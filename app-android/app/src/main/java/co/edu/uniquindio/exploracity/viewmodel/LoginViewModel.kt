package co.edu.uniquindio.exploracity.viewmodel

import android.content.Context
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
import co.edu.uniquindio.exploracity.data.google.GoogleCredentialResult
import co.edu.uniquindio.exploracity.data.google.GoogleCredentials
import co.edu.uniquindio.exploracity.data.local.AppPreferences
import co.edu.uniquindio.exploracity.data.local.SessionStore
import co.edu.uniquindio.exploracity.data.repository.AuthRepository
import co.edu.uniquindio.exploracity.data.repository.GoogleAuthRepository
import co.edu.uniquindio.exploracity.domain.model.AuthRules
import co.edu.uniquindio.exploracity.domain.model.InvalidCredentialsException
import co.edu.uniquindio.exploracity.domain.model.TooManyAttemptsException
import co.edu.uniquindio.exploracity.domain.model.UserRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class LoginField { EMAIL, PASSWORD }

/**
 * 3.c · El aviso del fallo: queda hasta que la persona lo cierra (README, snackbar de error). [TOO_MANY_ATTEMPTS]: la
 * API bloquea el correo 15 minutos tras 5 intentos fallidos (A1).
 */
enum class LoginError { CREDENTIALS, TOO_MANY_ATTEMPTS, FAILED }

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val emailTouched: Boolean = false,
    val passwordTouched: Boolean = false,
    val submitting: Boolean = false,
    val offline: Boolean = false,
    val error: LoginError? = null,
    /** Campo al que va el foco tras un intento con errores; [focusRequest] cambia en cada intento. */
    val focusField: LoginField? = null,
    val focusRequest: Int = 0,
    val signedIn: Boolean = false,
    /** ADR-15 · La cuenta de Google es nueva: la pantalla abre el registro en modo Google (C1) y lo marca como visto. */
    val googleRegistration: GoogleRegistrationStart? = null,
) {
    val emailValid: Boolean get() = AuthRules.isValidEmail(email)

    val passwordValid: Boolean get() = AuthRules.isValidPassword(password)

    val showEmailError: Boolean get() = emailTouched && !emailValid

    val showPasswordError: Boolean get() = passwordTouched && !passwordValid

    /** README 3: se habilita al pasar las dos validaciones; sin conexión, no. */
    val canSubmit: Boolean get() = emailValid && passwordValid && !offline && !submitting
}

/**
 * 3 · Correo y contraseña con su validación, el aviso sin conexión y el del fallo, y «Continuar con Google» (ADR-15,
 * [google]). Al entrar deja lista la app ([prepare]: la ciudad y el perfil), abre la sesión en el teléfono y marca el
 * onboarding como visto: al volver a abrir la app se entra directo (1).
 */
class LoginViewModel(
    private val auth: AuthRepository,
    private val sessions: SessionStore,
    private val preferences: AppPreferences,
    private val connectivity: ConnectivityObserver,
    private val savedStateHandle: SavedStateHandle,
    googleAuth: GoogleAuthRepository,
    private val googleCredentials: GoogleCredentials? = null,
    private val prepare: suspend () -> Unit = {},
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState(email = savedStateHandle[EMAIL_KEY] ?: "", offline = !connectivity.isOnline.value))
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    val google = GoogleAccess(
        google = googleAuth,
        scope = viewModelScope,
        prepare = prepare,
        onSignedIn = ::enter,
        onRegistrationRequired = { token, email, name -> _state.update { it.copy(googleRegistration = GoogleRegistrationStart(token, email, name)) } },
        onOffline = { _state.update { it.copy(offline = true) } },
    )

    init {
        viewModelScope.launch {
            connectivity.isOnline.collect { online -> _state.update { it.copy(offline = !online) } }
        }
    }

    /** La hoja de Google para elegir la cuenta, sobre la Activity de la pantalla. */
    suspend fun requestGoogleCredential(activity: Context): GoogleCredentialResult =
        googleCredentials?.request(activity) ?: GoogleCredentialResult.Failed

    fun onGoogleRegistrationShown() = _state.update { it.copy(googleRegistration = null) }

    fun onEmailChange(email: String) {
        if (_state.value.submitting) return
        savedStateHandle[EMAIL_KEY] = email
        _state.update { it.copy(email = email) }
    }

    fun onPasswordChange(password: String) {
        if (_state.value.submitting) return
        _state.update { it.copy(password = password) }
    }

    fun onEmailBlur() = _state.update { it.copy(emailTouched = it.email.isNotEmpty() || it.emailTouched) }

    fun onPasswordBlur() = _state.update { it.copy(passwordTouched = it.password.isNotEmpty() || it.passwordTouched) }

    /** El registro (4) encontró que el correo ya tiene cuenta: llega escrito, sin la contraseña de antes. */
    fun onSuggestedEmail(email: String) {
        if (_state.value.submitting) return
        savedStateHandle[EMAIL_KEY] = email
        _state.update { it.copy(email = email, password = "", emailTouched = true, passwordTouched = false, error = null) }
    }

    fun onSubmit() {
        val state = _state.value
        if (state.submitting || state.offline) return
        if (!state.emailValid || !state.passwordValid) {
            val first = if (!state.emailValid) LoginField.EMAIL else LoginField.PASSWORD
            _state.update { it.copy(emailTouched = true, passwordTouched = true, focusField = first, focusRequest = it.focusRequest + 1) }
            return
        }
        _state.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            val result = catchingNonCancellation { auth.signIn(state.email.trim(), state.password).also { prepare() } }
            val role = result.getOrNull()
            if (role != null) {
                enter(role)
                return@launch
            }
            when (result.exceptionOrNull()) {
                is OfflineException -> _state.update { it.copy(submitting = false, offline = true) }
                is InvalidCredentialsException -> _state.update { it.copy(submitting = false, error = LoginError.CREDENTIALS) }
                is TooManyAttemptsException -> _state.update { it.copy(submitting = false, error = LoginError.TOO_MANY_ATTEMPTS) }
                else -> _state.update { it.copy(submitting = false, error = LoginError.FAILED) }
            }
        }
    }

    fun onErrorDismissed() = _state.update { it.copy(error = null) }

    private suspend fun enter(role: UserRole) {
        sessions.open(role)
        preferences.setOnboardingSeen()
        _state.update { it.copy(submitting = false, signedIn = true) }
    }

    companion object {
        private const val EMAIL_KEY = "correo"

        val factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as ExploraApplication).container
                LoginViewModel(
                    auth = container.authRepository,
                    sessions = container.sessionStore,
                    preferences = container.preferences,
                    connectivity = container.connectivity,
                    savedStateHandle = createSavedStateHandle(),
                    googleAuth = container.googleAuthRepository,
                    googleCredentials = container.googleCredentials,
                    prepare = container::prepareSession,
                )
            }
        }
    }
}
