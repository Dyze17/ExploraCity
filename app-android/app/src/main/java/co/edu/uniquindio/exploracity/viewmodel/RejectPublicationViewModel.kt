package co.edu.uniquindio.exploracity.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import co.edu.uniquindio.exploracity.ExploraApplication
import co.edu.uniquindio.exploracity.data.connectivity.ConnectivityObserver
import co.edu.uniquindio.exploracity.data.connectivity.OfflineException
import co.edu.uniquindio.exploracity.data.repository.ModerationRepository
import co.edu.uniquindio.exploracity.domain.model.AlreadyReviewedException
import co.edu.uniquindio.exploracity.domain.model.DuplicateCandidate
import co.edu.uniquindio.exploracity.domain.model.RejectDecision
import co.edu.uniquindio.exploracity.domain.model.RejectionReason
import co.edu.uniquindio.exploracity.domain.model.ReviewItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Lo que falta para poder rechazar (35.b): se muestra después del primer intento. */
enum class RejectProblem {
    /** «Elige un motivo para continuar». */
    NO_REASON,

    /** Con «Duplicado»: «Elige el lugar original». */
    NO_ORIGINAL,

    /** Con «Otro motivo»: el detalle tiene menos de 20 caracteres. */
    SHORT_DETAIL,
}

data class RejectPublicationUiState(
    /** La pendiente, como en 33: sin ella no hay nada que rechazar. */
    val content: ReviewContent = ReviewContent.Loading,
    val reason: RejectionReason? = null,
    /** Los lugares que se pueden enlazar como original: los parecidos o, sin ellos, los más cercanos. */
    val options: List<DuplicateCandidate> = emptyList(),
    val optionsLoaded: Boolean = false,
    val originalId: String? = null,
    /** El selector del original está abierto. */
    val choosingOriginal: Boolean = false,
    val message: String = "",
    /** El mensaje es el sugerido para el duplicado y no se ha tocado: cambia con el original. */
    val suggested: Boolean = false,
    /** «Permitir que corrija y la reenvíe»: no aplica a un duplicado. */
    val canResubmit: Boolean = true,
    val showErrors: Boolean = false,
    /** Cuántas veces se intentó rechazar sin poder: cada intento lleva el foco al primer problema. */
    val attempts: Int = 0,
    val offline: Boolean = false,
    val sending: Boolean = false,
    /** «No pudimos guardar la decisión. La publicación sigue pendiente», con «Reintentar». */
    val failed: Boolean = false,
    val done: ReviewDone? = null,
) {
    val item: ReviewItem? get() = (content as? ReviewContent.Loaded)?.item

    val original: DuplicateCandidate? get() = options.firstOrNull { it.poi.id == originalId }

    val problems: Set<RejectProblem>
        get() = buildSet {
            when (reason) {
                null -> add(RejectProblem.NO_REASON)
                RejectionReason.DUPLICATE -> if (original == null) add(RejectProblem.NO_ORIGINAL)
                RejectionReason.OTHER -> if (message.trim().length < RejectPublicationViewModel.DETAIL_MIN) add(RejectProblem.SHORT_DETAIL)
                else -> Unit
            }
        }

    /** Sin conexión se lee, pero no se decide (32.c). */
    val canSend: Boolean get() = item != null && problems.isEmpty() && !offline && !sending
}

/**
 * 35 · Rechazar con motivo. El motivo es obligatorio; con «Duplicado» también el original (si hay un solo parecido, va
 * elegido)
 * y un mensaje sugerido que se puede editar; con «Otro motivo», un detalle de al menos 20 caracteres. Intentar rechazar
 * sin cumplirlo muestra qué falta (35.b). Al rechazar abre la siguiente de la cola (C1), como al verificar.
 */
class RejectPublicationViewModel(
    private val moderation: ModerationRepository,
    private val connectivity: ConnectivityObserver,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val id: String = savedStateHandle[ID_KEY] ?: ""

    private val _state = MutableStateFlow(
        RejectPublicationUiState(
            reason = if (savedStateHandle.get<Boolean>(DUPLICATE_KEY) == true) RejectionReason.DUPLICATE else null,
            originalId = savedStateHandle[ORIGINAL_KEY],
            offline = !connectivity.isOnline.value,
        ),
    )
    val state: StateFlow<RejectPublicationUiState> = _state.asStateFlow()

    /** El orden de la cola al abrir: la siguiente es la que venía después de esta. */
    private var order: List<String> = emptyList()

    init {
        viewModelScope.launch {
            connectivity.isOnline.collect { online -> _state.update { it.copy(offline = !online) } }
        }
        load()
    }

    private fun load() {
        _state.update { it.copy(content = ReviewContent.Loading) }
        viewModelScope.launch {
            order = catchingNonCancellation { moderation.queueIds() }.getOrNull().orEmpty()
            val result = catchingNonCancellation { moderation.item(id) }
            val item = result.getOrNull()
            val content = when {
                item != null -> ReviewContent.Loaded(item)
                result.isSuccess -> ReviewContent.Gone
                result.exceptionOrNull() is OfflineException -> ReviewContent.Offline
                else -> ReviewContent.Error
            }
            _state.update { it.copy(content = content) }
            if (item != null) loadOptions(item)
        }
    }

    /** Los parecidos marcados ya vienen con la pendiente; si no hay, se piden los lugares cercanos. */
    private suspend fun loadOptions(item: ReviewItem) {
        val flagged = item.duplicate?.candidates.orEmpty()
        val options = flagged.ifEmpty { catchingNonCancellation { moderation.duplicateOptions(id) }.getOrNull().orEmpty() }
        _state.update { state ->
            val keep = state.originalId?.takeIf { chosen -> options.any { it.poi.id == chosen } }
            // Un solo parecido marcado va elegido; un lugar que solo está cerca lo elige quien modera.
            val single = if (flagged.isNotEmpty()) options.singleOrNull()?.poi?.id else null
            state.copy(options = options, optionsLoaded = true, originalId = keep ?: single)
        }
    }

    fun onRetry() = load()

    fun onReasonChange(reason: RejectionReason) = _state.update { state ->
        if (state.sending) return@update state
        // El mensaje sugerido es del duplicado: con otro motivo se quita, si nadie lo tocó.
        val leaveDuplicate = state.reason == RejectionReason.DUPLICATE && reason != RejectionReason.DUPLICATE && state.suggested
        state.copy(
            reason = reason,
            message = if (leaveDuplicate) "" else state.message,
            suggested = state.suggested && !leaveDuplicate,
        )
    }

    fun onChooseOriginal() = _state.update { if (it.sending || it.options.isEmpty()) it else it.copy(choosingOriginal = true) }

    fun onOriginalChange(poiId: String) = _state.update { state ->
        if (state.options.none { it.poi.id == poiId }) state else state.copy(originalId = poiId, choosingOriginal = false)
    }

    fun onDismissOriginal() = _state.update { it.copy(choosingOriginal = false) }

    /**
     * El mensaje sugerido para el duplicado («Este lugar ya está publicado como «…»…»): la pantalla lo arma con el
     * original y lo pone mientras el campo esté vacío o siga siendo el sugerido.
     */
    fun onSuggestion(text: String) = _state.update { state ->
        if (state.reason != RejectionReason.DUPLICATE || !(state.suggested || state.message.isBlank())) state
        else state.copy(message = text, suggested = true)
    }

    fun onMessageChange(text: String) = _state.update { state ->
        // El eco del sugerido que baja al campo no cuenta como edición.
        if (state.sending || text == state.message) state else state.copy(message = text.take(MESSAGE_MAX), suggested = false)
    }

    fun onCanResubmitChange(allowed: Boolean) = _state.update { it.copy(canResubmit = allowed) }

    /** «Rechazar»: si falta algo se dice qué (35.b) sin diálogos; si no, se envía. */
    fun onReject() {
        val state = _state.value
        if (state.sending || state.item == null) return
        if (state.problems.isNotEmpty()) {
            _state.update { it.copy(showErrors = true, attempts = it.attempts + 1) }
            return
        }
        if (state.offline) return
        send()
    }

    fun onFailureShown() = _state.update { it.copy(failed = false) }

    fun onDoneHandled() = _state.update { it.copy(done = null) }

    private fun send() {
        val state = _state.value
        val reason = state.reason ?: return
        val decision = RejectDecision(
            reason = reason,
            message = state.message.trim(),
            canResubmit = state.canResubmit && reason != RejectionReason.DUPLICATE,
            originalId = state.originalId.takeIf { reason == RejectionReason.DUPLICATE },
        )
        _state.update { it.copy(sending = true, failed = false) }
        viewModelScope.launch {
            val result = catchingNonCancellation { moderation.reject(id, decision) }
            when (result.exceptionOrNull()) {
                null -> {
                    val remaining = catchingNonCancellation { moderation.queueIds() }.getOrNull().orEmpty()
                    _state.update { it.copy(sending = false, done = nextReview(order, id, remaining)) }
                }
                is AlreadyReviewedException -> _state.update { it.copy(sending = false, content = ReviewContent.Gone) }
                is OfflineException -> _state.update { it.copy(sending = false, offline = true, failed = true) }
                else -> _state.update { it.copy(sending = false, failed = true) }
            }
        }
    }

    companion object {
        private const val ID_KEY = "publicationId"
        private const val DUPLICATE_KEY = "duplicate"
        private const val ORIGINAL_KEY = "originalId"

        /** «Detalle para el autor» (35.a): «Mínimo 20 caracteres · 118/400». */
        const val DETAIL_MIN = 20
        const val MESSAGE_MAX = 400

        val factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as ExploraApplication).container
                RejectPublicationViewModel(
                    moderation = container.moderationRepository,
                    connectivity = container.connectivity,
                    savedStateHandle = createSavedStateHandle(),
                )
            }
        }
    }
}
