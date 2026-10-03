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
import co.edu.uniquindio.exploracity.domain.model.FinalizeReason
import co.edu.uniquindio.exploracity.domain.model.PublicationStatus
import co.edu.uniquindio.exploracity.domain.model.ResolvedPublication
import co.edu.uniquindio.exploracity.domain.model.StateChangedException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 36 · «Nuevo estado». */
enum class StateTarget {
    /** «Resuelta / finalizada». */
    FINALIZED,

    /** «Volver a pendiente». */
    PENDING,
}

sealed interface ChangeStateContent {
    data object Loading : ChangeStateContent

    data class Loaded(val item: ResolvedPublication) : ChangeStateContent

    /** Ya no está resuelta (volvió a pendiente o la borraron) o ya no puede cambiar de estado. */
    data object Gone : ChangeStateContent

    data object Offline : ChangeStateContent

    data object Error : ChangeStateContent
}

/** Lo que falta para poder cambiar el estado: se muestra después del primer intento. */
enum class ChangeStateProblem {
    /** «Elige un motivo para continuar». */
    NO_FINALIZE_REASON,

    /** El motivo para volver a pendiente tiene menos de 20 caracteres. */
    SHORT_REOPEN_REASON,
}

data class ChangeStateUiState(
    val content: ChangeStateContent = ChangeStateContent.Loading,
    val target: StateTarget? = null,
    val finalizeReason: FinalizeReason? = null,
    /** «¿Por qué vuelve a pendiente?»: queda como nota interna. */
    val reopenReason: String = "",
    val showErrors: Boolean = false,
    /** Cuántas veces se intentó sin poder: cada intento lleva el foco al primer problema. */
    val attempts: Int = 0,
    val offline: Boolean = false,
    val sending: Boolean = false,
    /** «No pudimos guardar el cambio. La publicación sigue igual», con «Reintentar». */
    val failed: Boolean = false,
    /** Se guardó: vuelve a «Resueltas» con el aviso de lo hecho. */
    val done: StateTarget? = null,
) {
    val item: ResolvedPublication? get() = (content as? ChangeStateContent.Loaded)?.item

    /** Una finalizada solo puede volver a pendiente: es como se deshace. */
    val canFinalize: Boolean get() = item?.status == PublicationStatus.VERIFIED

    val problems: Set<ChangeStateProblem>
        get() = buildSet {
            when (target) {
                StateTarget.FINALIZED -> if (finalizeReason == null) add(ChangeStateProblem.NO_FINALIZE_REASON)
                StateTarget.PENDING -> if (reopenReason.trim().length < ChangeStateViewModel.REASON_MIN) add(ChangeStateProblem.SHORT_REOPEN_REASON)
                null -> Unit
            }
        }

    val canSend: Boolean get() = item != null && target != null && problems.isEmpty() && !offline && !sending
}

/**
 * 36 · Cambiar el estado de una resuelta: pasarla a finalizada con uno de los tres motivos o devolverla a pendiente
 * con un motivo escrito. Una verificada empieza en «Resuelta / finalizada»; una finalizada, en «Volver a pendiente».
 */
class ChangeStateViewModel(
    private val moderation: ModerationRepository,
    private val connectivity: ConnectivityObserver,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val id: String = savedStateHandle[ID_KEY] ?: ""

    private val _state = MutableStateFlow(ChangeStateUiState(offline = !connectivity.isOnline.value))
    val state: StateFlow<ChangeStateUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            connectivity.isOnline.collect { online -> _state.update { it.copy(offline = !online) } }
        }
        load()
    }

    private fun load() {
        _state.update { it.copy(content = ChangeStateContent.Loading) }
        viewModelScope.launch {
            val result = catchingNonCancellation { moderation.resolvedItem(id) }
            val item = result.getOrNull()
            val content = when {
                item != null && item.canChangeState -> ChangeStateContent.Loaded(item)
                result.isSuccess -> ChangeStateContent.Gone
                result.exceptionOrNull() is OfflineException -> ChangeStateContent.Offline
                else -> ChangeStateContent.Error
            }
            _state.update { state ->
                val target = state.target ?: item?.let { if (it.status == PublicationStatus.VERIFIED) StateTarget.FINALIZED else StateTarget.PENDING }
                state.copy(content = content, target = target)
            }
        }
    }

    fun onRetry() = load()

    fun onTargetChange(target: StateTarget) = _state.update { state ->
        if (state.sending || (target == StateTarget.FINALIZED && !state.canFinalize)) state else state.copy(target = target)
    }

    fun onFinalizeReasonChange(reason: FinalizeReason) = _state.update { if (it.sending) it else it.copy(finalizeReason = reason) }

    fun onReopenReasonChange(text: String) = _state.update { if (it.sending) it else it.copy(reopenReason = text.take(REASON_MAX)) }

    /** «Pasar a finalizada» o «Volver a pendiente»: si falta algo se dice qué; si no, se guarda. */
    fun onConfirm() {
        val state = _state.value
        val target = state.target ?: return
        if (state.sending || state.item == null) return
        if (state.problems.isNotEmpty()) {
            _state.update { it.copy(showErrors = true, attempts = it.attempts + 1) }
            return
        }
        if (state.offline) return
        _state.update { it.copy(sending = true, failed = false) }
        viewModelScope.launch {
            val result = catchingNonCancellation {
                when (target) {
                    StateTarget.FINALIZED -> moderation.finalize(id, checkNotNull(state.finalizeReason))
                    StateTarget.PENDING -> moderation.reopen(id, state.reopenReason.trim())
                }
            }
            when (result.exceptionOrNull()) {
                null -> _state.update { it.copy(sending = false, done = target) }
                is StateChangedException -> _state.update { it.copy(sending = false, content = ChangeStateContent.Gone) }
                is OfflineException -> _state.update { it.copy(sending = false, offline = true, failed = true) }
                else -> _state.update { it.copy(sending = false, failed = true) }
            }
        }
    }

    fun onFailureShown() = _state.update { it.copy(failed = false) }

    fun onDoneHandled() = _state.update { it.copy(done = null) }

    companion object {
        private const val ID_KEY = "publicationId"

        /** El motivo para volver a pendiente, como el detalle de «Otro motivo» en 35. */
        const val REASON_MIN = 20
        const val REASON_MAX = 300

        val factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as ExploraApplication).container
                ChangeStateViewModel(
                    moderation = container.moderationRepository,
                    connectivity = container.connectivity,
                    savedStateHandle = createSavedStateHandle(),
                )
            }
        }
    }
}
