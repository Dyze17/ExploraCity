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
import co.edu.uniquindio.exploracity.domain.model.ReportedPlace
import co.edu.uniquindio.exploracity.domain.repository.ModerationRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ReportedContent {
    data object Loading : ReportedContent

    data class Loaded(val items: List<ReportedPlace>) : ReportedContent

    /** «Reportados» no se guarda: sin conexión no hay qué mostrar. */
    data object Offline : ReportedContent

    data object Error : ReportedContent
}

/** Avisos de una sola vez (snackbar) de «Descartar». */
enum class ReportedMessage { DISMISSED, ALREADY_REVIEWED, FAILED, OFFLINE }

data class ReportedPlacesUiState(
    val content: ReportedContent = ReportedContent.Loading,
    /** El lugar cuyo «¿Descartar los reportes?» está abierto. */
    val confirming: ReportedPlace? = null,
    /** Se está descartando: el diálogo sigue abierto con «Descartar» cargando. */
    val dismissing: Boolean = false,
    val message: ReportedMessage? = null,
)

/**
 * v1.4.0 · «Reportados»: los lugares públicos con reportes abiertos. «Descartar» los cierra aquí mismo; «Cambiar
 * estado» va a 36, y al volver la lista se pone al día sin la carga a pantalla completa. Al volver la red, también.
 */
class ReportedPlacesViewModel(
    private val moderation: ModerationRepository,
    private val connectivity: ConnectivityObserver,
) : ViewModel() {

    private val _state = MutableStateFlow(ReportedPlacesUiState())
    val state: StateFlow<ReportedPlacesUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        load(showLoading = true)
        viewModelScope.launch {
            connectivity.isOnline.drop(1).collect { online -> if (online) load(showLoading = false) }
        }
    }

    private fun load(showLoading: Boolean) {
        loadJob?.cancel()
        val hadList = _state.value.content is ReportedContent.Loaded
        if (showLoading || !hadList) _state.update { it.copy(content = ReportedContent.Loading) }
        loadJob = viewModelScope.launch {
            val result = catchingNonCancellation { moderation.reported() }
            val items = result.getOrNull()
            when {
                items != null -> _state.update { it.copy(content = ReportedContent.Loaded(items)) }
                // El fallo de un refresco no borra la lista que ya se veía.
                hadList && !showLoading -> Unit
                result.exceptionOrNull() is OfflineException -> _state.update { it.copy(content = ReportedContent.Offline) }
                else -> _state.update { it.copy(content = ReportedContent.Error) }
            }
        }
    }

    fun onRetry() = load(showLoading = true)

    /** Al volver de 36 o del detalle: alguno pudo cambiar. */
    fun onResume() {
        if (_state.value.content is ReportedContent.Loaded) load(showLoading = false)
    }

    fun onAskDismiss(place: ReportedPlace) = _state.update { it.copy(confirming = place) }

    fun onCancelDismiss() {
        if (_state.value.dismissing) return
        _state.update { it.copy(confirming = null) }
    }

    /** Se descartó (o ya lo había hecho otro moderador): sale de la lista. Si falla, la lista queda como estaba. */
    fun onConfirmDismiss() {
        val place = _state.value.confirming ?: return
        if (_state.value.dismissing) return
        _state.update { it.copy(dismissing = true) }
        viewModelScope.launch {
            val error = catchingNonCancellation { moderation.dismissReports(place.id) }.exceptionOrNull()
            val message = when (error) {
                null -> ReportedMessage.DISMISSED
                is AlreadyReviewedException -> ReportedMessage.ALREADY_REVIEWED
                is OfflineException -> ReportedMessage.OFFLINE
                else -> ReportedMessage.FAILED
            }
            val gone = message == ReportedMessage.DISMISSED || message == ReportedMessage.ALREADY_REVIEWED
            _state.update { current ->
                val content = current.content
                current.copy(
                    content = if (gone && content is ReportedContent.Loaded) ReportedContent.Loaded(content.items.filter { it.id != place.id }) else content,
                    confirming = null,
                    dismissing = false,
                    message = message,
                )
            }
        }
    }

    fun onMessageShown() = _state.update { it.copy(message = null) }

    companion object {
        val factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as ExploraApplication).container
                ReportedPlacesViewModel(container.moderationRepository, container.connectivity)
            }
        }
    }
}
