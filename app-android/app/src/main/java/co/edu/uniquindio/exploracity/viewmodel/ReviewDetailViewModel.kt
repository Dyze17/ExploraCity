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
import co.edu.uniquindio.exploracity.domain.model.ReviewItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ReviewContent {
    data object Loading : ReviewContent

    data class Loaded(val item: ReviewItem) : ReviewContent

    /** Ya no está en la cola: otra persona la decidió. */
    data object Gone : ReviewContent

    data object Offline : ReviewContent

    data object Error : ReviewContent
}

/** 34 · La hoja de verificar abierta, con la nota interna (opcional). */
data class VerifySheet(val note: String = "", val sending: Boolean = false)

/** Después de decidir (C1): la siguiente pendiente, con cuántas quedan, o la cola si ya no queda ninguna. */
sealed interface ReviewDone {
    data class Next(val id: String, val remaining: Int) : ReviewDone

    data object QueueEmpty : ReviewDone
}

/** Lo que se decidió sobre una pendiente: «Verificada. …» o «Rechazada. …» (C1). */
enum class Decision { VERIFIED, REJECTED }

/** El aviso con que se llega a la siguiente pendiente: «Rechazada. Quedan 5 por revisar». */
data class DecisionNotice(val decision: Decision, val remaining: Int)

/** La que venía después de [id] en [order] (la cola al abrirla); si era la última, la primera que quede. */
internal fun nextReview(order: List<String>, id: String, remaining: List<String>): ReviewDone {
    if (remaining.isEmpty()) return ReviewDone.QueueEmpty
    val after = order.dropWhile { it != id }.drop(1).firstOrNull { it in remaining }
    return ReviewDone.Next(after ?: remaining.first(), remaining.size)
}

data class ReviewDetailUiState(
    val content: ReviewContent = ReviewContent.Loading,
    /** «1 de 7»: la posición en la cola y su largo; null si no está en la última cola cargada. */
    val position: Int? = null,
    val total: Int = 0,
    val offline: Boolean = false,
    /** Se abrió 33A: el diálogo de verificar ya no repite el aviso de duplicado. */
    val compared: Boolean = false,
    val verify: VerifySheet? = null,
    /** Se está enviando la verificación (desde la hoja o desde «Reintentar»). */
    val sending: Boolean = false,
    /** «No pudimos guardar la decisión. La publicación sigue pendiente», con «Reintentar»: la nota se conserva. */
    val verifyFailed: Boolean = false,
    val retryNote: String? = null,
    val done: ReviewDone? = null,
) {
    val item: ReviewItem? get() = (content as? ReviewContent.Loaded)?.item

    /** Sin conexión se lee, pero no se decide (32.c). */
    val canDecide: Boolean get() = item != null && !offline && !sending
}

/**
 * 33 y 34 · Lo que hace falta para decidir sobre una pendiente y la verificación con su nota interna. Al verificar
 * abre la siguiente de la cola (C1: «avanza automáticamente»); si era la última, vuelve a la cola, que ya está vacía.
 */
class ReviewDetailViewModel(
    private val moderation: ModerationRepository,
    private val connectivity: ConnectivityObserver,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val id: String = savedStateHandle[ID_KEY] ?: ""

    private val _state = MutableStateFlow(ReviewDetailUiState(offline = !connectivity.isOnline.value))
    val state: StateFlow<ReviewDetailUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    /** El orden de la cola al abrir: la siguiente es la que venía después de esta. */
    private var order: List<String> = emptyList()

    init {
        viewModelScope.launch {
            connectivity.isOnline.collect { online -> _state.update { it.copy(offline = !online) } }
        }
        load()
    }

    private fun load() {
        loadJob?.cancel()
        _state.update { it.copy(content = ReviewContent.Loading) }
        loadJob = viewModelScope.launch {
            order = catchingNonCancellation { moderation.queueIds() }.getOrNull().orEmpty()
            val index = order.indexOf(id)
            _state.update { it.copy(position = if (index >= 0) index + 1 else null, total = order.size) }
            val result = catchingNonCancellation { moderation.item(id) }
            val content = when {
                result.isSuccess -> result.getOrNull()?.let { ReviewContent.Loaded(it) } ?: ReviewContent.Gone
                result.exceptionOrNull() is OfflineException -> ReviewContent.Offline
                else -> ReviewContent.Error
            }
            _state.update { it.copy(content = content) }
        }
    }

    fun onRetry() = load()

    /** Se abrió «Comparar lugares» (33A). */
    fun onCompared() = _state.update { it.copy(compared = true) }

    /** 33A · «Verificar como lugar distinto»: se abre 34 sin el aviso de duplicado (se retira la marca). */
    fun onVerifyDistinct() {
        _state.update { it.copy(compared = true) }
        onVerifyClick()
    }

    fun onVerifyClick() {
        if (!_state.value.canDecide) return
        _state.update { it.copy(verify = VerifySheet(), verifyFailed = false) }
    }

    fun onNoteChange(note: String) = _state.update { state ->
        val sheet = state.verify ?: return@update state
        if (sheet.sending) state else state.copy(verify = sheet.copy(note = note.take(NOTE_MAX)))
    }

    fun onDismissVerify() = _state.update { if (it.verify?.sending == true) it else it.copy(verify = null) }

    fun onConfirmVerify() {
        val sheet = _state.value.verify ?: return
        if (sheet.sending || _state.value.offline) return
        _state.update { it.copy(verify = sheet.copy(sending = true)) }
        send(sheet.note)
    }

    /** «Reintentar» del aviso de fallo: vuelve a enviar con la misma nota, sin abrir otra vez la hoja. */
    fun onRetryVerify() {
        val note = _state.value.retryNote ?: return
        if (_state.value.sending || _state.value.offline) return
        send(note)
    }

    private fun send(note: String) {
        _state.update { it.copy(sending = true, verifyFailed = false) }
        viewModelScope.launch {
            val result = catchingNonCancellation { moderation.verify(id, note.trim().ifEmpty { null }) }
            when (result.exceptionOrNull()) {
                null -> {
                    val remaining = catchingNonCancellation { moderation.queueIds() }.getOrNull().orEmpty()
                    _state.update { it.copy(verify = null, sending = false, retryNote = null, done = nextReview(order, id, remaining)) }
                }
                is AlreadyReviewedException -> _state.update { it.copy(verify = null, sending = false, retryNote = null, content = ReviewContent.Gone) }
                // La hoja se cierra para que el aviso se vea; la nota queda para el reintento.
                is OfflineException -> _state.update { it.copy(verify = null, sending = false, offline = true, retryNote = note, verifyFailed = true) }
                else -> _state.update { it.copy(verify = null, sending = false, retryNote = note, verifyFailed = true) }
            }
        }
    }

    fun onVerifyFailureShown() = _state.update { it.copy(verifyFailed = false) }

    fun onDoneHandled() = _state.update { it.copy(done = null) }

    companion object {
        /** El nombre del argumento de la ruta (ReviewDetail.publicationId). */
        private const val ID_KEY = "publicationId"

        /** La nota interna (34) es breve, como un comentario. */
        const val NOTE_MAX = 300

        val factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as ExploraApplication).container
                ReviewDetailViewModel(
                    moderation = container.moderationRepository,
                    connectivity = container.connectivity,
                    savedStateHandle = createSavedStateHandle(),
                )
            }
        }
    }
}
