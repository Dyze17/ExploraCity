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
import co.edu.uniquindio.exploracity.data.local.AppPreferences
import co.edu.uniquindio.exploracity.data.local.DocumentWriter
import co.edu.uniquindio.exploracity.data.local.SessionManager
import co.edu.uniquindio.exploracity.data.repository.AccountRepository
import co.edu.uniquindio.exploracity.domain.model.DataExport
import co.edu.uniquindio.exploracity.domain.model.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 29 · «Descargar mis datos»: el servidor arma el archivo y después Android pregunta dónde guardarlo. */
sealed interface DataDownload {
    data object Idle : DataDownload

    data object Preparing : DataDownload

    /** Listo: la pantalla abre el diálogo «Guardar» de Android con el nombre propuesto. */
    data class Ready(val export: DataExport) : DataDownload

    /** El diálogo de Android está abierto. */
    data class ChoosingDestination(val export: DataExport) : DataDownload

    data object Saving : DataDownload

    val busy: Boolean get() = this != Idle
}

enum class SettingsNoticeKind { DOWNLOAD_OFFLINE, DOWNLOAD_FAILED, DOWNLOAD_SAVED, SAVE_FAILED, EMAIL_CHANGED }

/**
 * Aviso breve de Ajustes. [detail] es el archivo en DOWNLOAD_SAVED («Guardamos tus datos en «…»») y el correo nuevo
 * en EMAIL_CHANGED («Listo, tu correo ahora es …»).
 */
data class SettingsNotice(val kind: SettingsNoticeKind, val detail: String? = null)

/** 29A · Abierto; [pendingSends] son los envíos sin conexión que se perderían. */
data class LogoutDialog(val pendingSends: Int = 0, val signingOut: Boolean = false)

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val email: String = "",
    /** «Cambiar correo» · El correo nuevo que espera confirmación (B1: la fila lo dice). */
    val pendingEmail: String? = null,
    /** ADR-15, D1 · Sin contraseña (entra solo con Google), la fila del correo no abre «Cambiar correo». */
    val hasPassword: Boolean = true,
    val download: DataDownload = DataDownload.Idle,
    val notice: SettingsNotice? = null,
    val logout: LogoutDialog? = null,
    /** Se cerró la sesión: la pantalla lleva al inicio de sesión (3). */
    val signedOut: Boolean = false,
)

class SettingsViewModel(
    private val preferences: AppPreferences,
    private val accounts: AccountRepository,
    private val session: SessionManager,
    private val documents: DocumentWriter,
    private val connectivity: ConnectivityObserver,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _state = MutableStateFlow(
        accounts.account.value.let { SettingsUiState(email = it.email, pendingEmail = it.pendingEmail, hasPassword = it.hasPassword) },
    )
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            preferences.themeMode.collect { mode -> _state.update { it.copy(themeMode = mode) } }
        }
        viewModelScope.launch {
            accounts.account.collect { account ->
                _state.update { it.copy(email = account.email, pendingEmail = account.pendingEmail, hasPassword = account.hasPassword) }
            }
        }
        // Tras la rotación o si Android cerró la app, 29A sigue abierto (con la cuenta de envíos al día).
        if (savedStateHandle.get<Boolean>(LOGOUT_OPEN_KEY) == true) onLogoutClick()
    }

    /** El tema cambia al tocarlo: la app entera se vuelve a pintar con él. */
    fun onThemeChange(mode: ThemeMode) {
        viewModelScope.launch { preferences.setThemeMode(mode) }
    }

    fun onDownloadData() {
        if (_state.value.download.busy) return
        if (!connectivity.isOnline.value) return notify(SettingsNoticeKind.DOWNLOAD_OFFLINE)
        _state.update { it.copy(download = DataDownload.Preparing) }
        viewModelScope.launch {
            val result = catchingNonCancellation { accounts.exportData() }
            val export = result.getOrNull()
            if (export != null) {
                _state.update { it.copy(download = DataDownload.Ready(export)) }
            } else {
                val kind = if (result.exceptionOrNull() is OfflineException) SettingsNoticeKind.DOWNLOAD_OFFLINE else SettingsNoticeKind.DOWNLOAD_FAILED
                _state.update { it.copy(download = DataDownload.Idle, notice = SettingsNotice(kind)) }
            }
        }
    }

    /** La pantalla ya abrió el diálogo «Guardar»: no se vuelve a abrir al recomponer. */
    fun onSaveDialogShown() = _state.update { state ->
        val ready = state.download as? DataDownload.Ready ?: return@update state
        state.copy(download = DataDownload.ChoosingDestination(ready.export))
    }

    /** Sin el diálogo «Guardar» de Android (no debería pasar): no hay dónde dejar el archivo. */
    fun onSaveDialogUnavailable() = _state.update { it.copy(download = DataDownload.Idle, notice = SettingsNotice(SettingsNoticeKind.SAVE_FAILED)) }

    /** [uri] es null si la persona cerró el diálogo sin guardar: no pasa nada. */
    fun onDestinationChosen(uri: String?) {
        val export = (_state.value.download as? DataDownload.ChoosingDestination)?.export
        if (uri == null) return _state.update { it.copy(download = DataDownload.Idle) }
        // Si Android cerró la app con el diálogo abierto, el archivo ya no está en memoria: hay que pedirlo otra vez.
        if (export == null) return _state.update { it.copy(download = DataDownload.Idle, notice = SettingsNotice(SettingsNoticeKind.SAVE_FAILED)) }
        _state.update { it.copy(download = DataDownload.Saving) }
        viewModelScope.launch {
            val saved = catchingNonCancellation { documents.write(uri, export.content) }.isSuccess
            val notice = if (saved) SettingsNotice(SettingsNoticeKind.DOWNLOAD_SAVED, export.fileName) else SettingsNotice(SettingsNoticeKind.SAVE_FAILED)
            _state.update { it.copy(download = DataDownload.Idle, notice = notice) }
        }
    }

    fun onNoticeShown() = _state.update { it.copy(notice = null) }

    /** Se confirmó el correo nuevo (al volver del enlace): Ajustes lo dice una vez. */
    fun onEmailChanged(email: String) = _state.update { it.copy(notice = SettingsNotice(SettingsNoticeKind.EMAIL_CHANGED, email)) }

    /** «Cerrar sesión» abre 29A, que avisa si hay envíos sin conexión que se perderían. */
    fun onLogoutClick() {
        savedStateHandle[LOGOUT_OPEN_KEY] = true
        viewModelScope.launch {
            val pending = runCatchingNonCancellation { session.pendingSends() } ?: 0
            if (savedStateHandle.get<Boolean>(LOGOUT_OPEN_KEY) == true) _state.update { it.copy(logout = LogoutDialog(pending)) }
        }
    }

    fun onDismissLogout() {
        if (_state.value.logout?.signingOut == true) return
        savedStateHandle[LOGOUT_OPEN_KEY] = false
        _state.update { it.copy(logout = null) }
    }

    /** Borrar lo guardado es local: aunque algo falle, la sesión se cierra igual para no dejar a la persona atrapada. */
    fun onConfirmLogout() {
        val dialog = _state.value.logout ?: return
        if (dialog.signingOut) return
        _state.update { it.copy(logout = dialog.copy(signingOut = true)) }
        viewModelScope.launch {
            runCatchingNonCancellation { session.signOut() }
            savedStateHandle[LOGOUT_OPEN_KEY] = false
            _state.update { it.copy(logout = null, signedOut = true) }
        }
    }

    private fun notify(kind: SettingsNoticeKind) = _state.update { it.copy(notice = SettingsNotice(kind)) }

    companion object {
        private const val LOGOUT_OPEN_KEY = "cerrar_sesion_abierto"

        val factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as ExploraApplication).container
                SettingsViewModel(
                    preferences = container.preferences,
                    accounts = container.accountRepository,
                    session = container.sessionManager,
                    documents = container.documentWriter,
                    connectivity = container.connectivity,
                    savedStateHandle = createSavedStateHandle(),
                )
            }
        }
    }
}
