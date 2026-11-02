package co.edu.uniquindio.exploracity.features.moderation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import co.edu.uniquindio.exploracity.ExploraApplication
import co.edu.uniquindio.exploracity.core.data.connectivity.ConnectivityObserver
import co.edu.uniquindio.exploracity.core.data.connectivity.OfflineException
import co.edu.uniquindio.exploracity.core.util.catchingNonCancellation
import co.edu.uniquindio.exploracity.domain.model.AlreadyReviewedException
import co.edu.uniquindio.exploracity.domain.model.PendingPhoto
import co.edu.uniquindio.exploracity.domain.model.PhotoRejectionReason
import co.edu.uniquindio.exploracity.domain.model.PlaceFullException
import co.edu.uniquindio.exploracity.domain.model.StateChangedException
import co.edu.uniquindio.exploracity.domain.repository.ModerationRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface PendingPhotosContent {
    data object Loading : PendingPhotosContent

    data class Loaded(val items: List<PendingPhoto>) : PendingPhotosContent

    /** «Fotos por revisar» no se guarda: sin conexión no hay qué mostrar. */
    data object Offline : PendingPhotosContent

    data object Error : PendingPhotosContent
}

/** Avisos de una sola vez (snackbar) de cada decisión. */
enum class PhotoReviewMessage { APPROVED, REJECTED, ALREADY_REVIEWED, PLACE_FULL, FAILED, OFFLINE }

/** «¿Por qué no se publica?» abierto para [photo]: el motivo elegido, ninguno al abrir. */
data class PhotoRejectState(val photo: PendingPhoto, val reason: PhotoRejectionReason? = null)

data class PendingPhotosUiState(
    val content: PendingPhotosContent = PendingPhotosContent.Loading,
    /** La foto que se está publicando o rechazando: sus botones cargan y los demás esperan. */
    val deciding: String? = null,
    val rejecting: PhotoRejectState? = null,
    val message: PhotoReviewMessage? = null,
)

/**
 * v1.4.0 · «Fotos por revisar» (D1): las fotos agregadas a lugares públicos, de la más antigua a la más reciente.
 * «Publicar» la pone en el lugar; «Rechazar» pide el motivo. Ya decidida por otro moderador, sale de la lista igual.
 */
class PendingPhotosViewModel(
    private val moderation: ModerationRepository,
    private val connectivity: ConnectivityObserver,
) : ViewModel() {

    private val _state = MutableStateFlow(PendingPhotosUiState())
    val state: StateFlow<PendingPhotosUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        load(showLoading = true)
        viewModelScope.launch {
            connectivity.isOnline.drop(1).collect { online -> if (online) load(showLoading = false) }
        }
    }

    private fun load(showLoading: Boolean) {
        loadJob?.cancel()
        val hadList = _state.value.content is PendingPhotosContent.Loaded
        if (showLoading || !hadList) _state.update { it.copy(content = PendingPhotosContent.Loading) }
        loadJob = viewModelScope.launch {
            val result = catchingNonCancellation { moderation.pendingPhotos() }
            val items = result.getOrNull()
            when {
                items != null -> _state.update { it.copy(content = PendingPhotosContent.Loaded(items)) }
                hadList && !showLoading -> Unit
                result.exceptionOrNull() is OfflineException -> _state.update { it.copy(content = PendingPhotosContent.Offline) }
                else -> _state.update { it.copy(content = PendingPhotosContent.Error) }
            }
        }
    }

    fun onRetry() = load(showLoading = true)

    fun onApprove(photo: PendingPhoto) = decide(photo.id, PhotoReviewMessage.APPROVED) { moderation.approvePhoto(photo.id) }

    fun onAskReject(photo: PendingPhoto) {
        if (_state.value.deciding != null) return
        _state.update { it.copy(rejecting = PhotoRejectState(photo)) }
    }

    fun onRejectReasonChange(reason: PhotoRejectionReason) = _state.update { it.copy(rejecting = it.rejecting?.copy(reason = reason)) }

    fun onCancelReject() = _state.update { it.copy(rejecting = null) }

    fun onConfirmReject() {
        val rejecting = _state.value.rejecting ?: return
        val reason = rejecting.reason ?: return
        _state.update { it.copy(rejecting = null) }
        decide(rejecting.photo.id, PhotoReviewMessage.REJECTED) { moderation.rejectPhoto(rejecting.photo.id, reason) }
    }

    fun onMessageShown() = _state.update { it.copy(message = null) }

    /** Decidida (o ya decidida por otra persona, o el lugar dejó de ser público), sale de la lista; si falla, queda. */
    private fun decide(id: String, done: PhotoReviewMessage, send: suspend () -> Unit) {
        if (_state.value.deciding != null) return
        _state.update { it.copy(deciding = id) }
        viewModelScope.launch {
            val error = catchingNonCancellation { send() }.exceptionOrNull()
            val message = when (error) {
                null -> done
                is AlreadyReviewedException, is StateChangedException -> PhotoReviewMessage.ALREADY_REVIEWED
                is PlaceFullException -> PhotoReviewMessage.PLACE_FULL
                is OfflineException -> PhotoReviewMessage.OFFLINE
                else -> PhotoReviewMessage.FAILED
            }
            val gone = error == null || message == PhotoReviewMessage.ALREADY_REVIEWED
            _state.update { current ->
                val content = current.content
                current.copy(
                    content = if (gone && content is PendingPhotosContent.Loaded) PendingPhotosContent.Loaded(content.items.filter { it.id != id }) else content,
                    deciding = null,
                    message = message,
                )
            }
        }
    }

    companion object {
        val factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as ExploraApplication).container
                PendingPhotosViewModel(container.moderationRepository, container.connectivity)
            }
        }
    }
}
