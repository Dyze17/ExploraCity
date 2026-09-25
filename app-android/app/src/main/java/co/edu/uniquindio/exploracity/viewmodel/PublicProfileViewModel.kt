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
import co.edu.uniquindio.exploracity.data.repository.UserRepository
import co.edu.uniquindio.exploracity.domain.model.Author
import co.edu.uniquindio.exploracity.domain.model.PublicProfile
import co.edu.uniquindio.exploracity.domain.model.ReportReason
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** 31 · Los estados de carga no los dibuja el diseño; siguen a 11 y 12.b, como el detalle. */
sealed interface ProfileContent {
    data object Loading : ProfileContent

    data class Loaded(val profile: PublicProfile) : ProfileContent

    /** La cuenta se eliminó. */
    data object NotFound : ProfileContent

    data object Error : ProfileContent

    /** Sin internet: los perfiles no se guardan para ver sin conexión. */
    data object Offline : ProfileContent
}

enum class ReportError {
    FAILED,

    /** Sin red no se encola: el reporte es una acción deliberada y el perfil mismo necesita conexión. */
    OFFLINE,
}

/** 31A abierto: el motivo es obligatorio; si falla, el diálogo sigue abierto con el motivo y el aviso. */
data class ReportDialogState(val reason: ReportReason? = null, val sending: Boolean = false, val error: ReportError? = null)

data class PublicProfileUiState(
    val content: ProfileContent = ProfileContent.Loading,
    /** El propio perfil: sin la bandera de reportar. */
    val isOwn: Boolean = false,
    val report: ReportDialogState? = null,
    /** «Gracias. Un moderador revisará este perfil» tras enviar el reporte (snackbar, una vez). */
    val reportSent: Boolean = false,
) {
    val profile: PublicProfile? get() = (content as? ProfileContent.Loaded)?.profile
}

class PublicProfileViewModel(
    private val users: UserRepository,
    private val connectivity: ConnectivityObserver,
    currentUser: Author,
    private val savedStateHandle: SavedStateHandle,
    private val timeout: Duration = LOAD_TIMEOUT,
) : ViewModel() {

    /** Argumento de la ruta PublicProfile(userId). */
    val userId: String = checkNotNull(savedStateHandle[USER_ID_KEY]) { "Falta el id de la persona" }

    private val _state = MutableStateFlow(PublicProfileUiState(isOwn = userId == currentUser.id, report = restoredReport()))
    val state: StateFlow<PublicProfileUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        load()
        viewModelScope.launch {
            // Al volver la red se carga solo si no se pudo traer.
            connectivity.isOnline.drop(1).collect { online ->
                val content = _state.value.content
                if (online && (content is ProfileContent.Offline || content is ProfileContent.Error)) load()
            }
        }
    }

    fun onRetry() = load()

    /** La bandera: abre 31A sin motivo elegido. */
    fun onOpenReport() {
        val state = _state.value
        if (state.isOwn || state.profile == null) return
        updateReport(ReportDialogState())
    }

    fun onReasonChange(reason: ReportReason) {
        val report = _state.value.report ?: return
        if (report.sending) return
        updateReport(report.copy(reason = reason, error = null))
    }

    /** «Cancelar», atrás o tocar fuera. */
    fun onDismissReport() {
        if (_state.value.report?.sending == true) return
        updateReport(null)
    }

    /** «Reportar»: solo con un motivo. Al enviarse, el diálogo se cierra y se avisa en el perfil. */
    fun onConfirmReport() {
        val report = _state.value.report ?: return
        val reason = report.reason ?: return
        if (report.sending) return
        updateReport(report.copy(sending = true, error = null))
        viewModelScope.launch {
            val result = catchingNonCancellation { users.reportUser(userId, reason) }
            if (result.isSuccess) {
                updateReport(null)
                _state.update { it.copy(reportSent = true) }
            } else {
                val error = if (result.exceptionOrNull() is OfflineException) ReportError.OFFLINE else ReportError.FAILED
                updateReport(report.copy(sending = false, error = error))
            }
        }
    }

    fun onReportSentShown() = _state.update { it.copy(reportSent = false) }

    private fun load() {
        loadJob?.cancel()
        _state.update { it.copy(content = ProfileContent.Loading) }
        loadJob = viewModelScope.launch {
            val content = try {
                // Envuelto para distinguir el tiempo agotado (null) de una persona que ya no existe (Found(null)).
                val found = withTimeoutOrNull(timeout) { Found(users.publicProfile(userId)) }
                when {
                    found == null -> ProfileContent.Error
                    found.profile == null -> ProfileContent.NotFound
                    else -> ProfileContent.Loaded(found.profile)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: OfflineException) {
                ProfileContent.Offline
            } catch (e: Exception) {
                ProfileContent.Error
            }
            _state.update { it.copy(content = content) }
        }
    }

    private class Found(val profile: PublicProfile?)

    /** El diálogo abierto y su motivo sobreviven si Android cierra la app. */
    private fun updateReport(report: ReportDialogState?) {
        _state.update { it.copy(report = report) }
        savedStateHandle[REPORT_KEY] = report?.let { it.reason?.name ?: NO_REASON }
    }

    private fun restoredReport(): ReportDialogState? = savedStateHandle.get<String>(REPORT_KEY)?.let { saved ->
        ReportDialogState(reason = ReportReason.entries.firstOrNull { it.name == saved })
    }

    companion object {
        /** Como en el feed: más de 8 s cargando → error recuperable. */
        val LOAD_TIMEOUT = 8.seconds

        const val USER_ID_KEY = "userId"
        private const val REPORT_KEY = "report"
        private const val NO_REASON = ""

        val factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as ExploraApplication).container
                PublicProfileViewModel(container.userRepository, container.connectivity, container.currentUser, createSavedStateHandle())
            }
        }
    }
}
