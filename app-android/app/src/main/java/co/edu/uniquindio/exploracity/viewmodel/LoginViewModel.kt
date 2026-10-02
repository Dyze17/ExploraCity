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
import co.edu.uniquindio.exploracity.data.local.SessionStore
import co.edu.uniquindio.exploracity.data.repository.AuthRepository
import co.edu.uniquindio.exploracity.domain.model.AuthRules
import co.edu.uniquindio.exploracity.domain.model.DemoAccount
import co.edu.uniquindio.exploracity.domain.model.InvalidCredentialsException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

enum class LoginField { EMAIL, PASSWORD }

/** 3.c · El aviso del fallo: queda hasta que la persona lo cierra (README, snackbar de error). */
enum class LoginError { CREDENTIALS, FAILED }

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
    val demoAccounts: List<DemoAccount> = emptyList(),
    val signedIn: Boolean = false,
) {
    val emailValid: Boolean get() = AuthRules.isValidEmail(email)

    val passwordValid: Boolean get() = AuthRules.isValidPassword(password)

    val showEmailError: Boolean get() = emailTouched && !emailValid

    val showPasswordError: Boolean get() = passwordTouched && !passwordValid

    /** README 3: se habilita al pasar las dos validaciones; sin conexión, no. */
    val canSubmit: Boolean get() = emailValid && passwordValid && !offline && !submitting
}

/**
 * 3 · Correo y contraseña con su validación, el aviso sin conexión y el del fallo. Al entrar abre la sesión en el
 * teléfono y marca el onboarding como visto: al volver a abrir la app se entra directo (1).
 */
class LoginViewModel(
    private val auth: AuthRepository,
    private val sessions: SessionStore,
    private val preferences: AppPreferences,
    private val connectivity: ConnectivityObserver,
    private val savedStateHandle: SavedStateHandle,
    demoAccounts: List<DemoAccount> = emptyList(),
) : ViewModel() {

    private val _state = MutableStateFlow(
        LoginUiState(email = savedStateHandle[EMAIL_KEY] ?: "", offline = !connectivity.isOnline.value, demoAccounts = demoAccounts),
    )
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            connectivity.isOnline.collect { online -> _state.update { it.copy(offline = !online) } }
        }
    }

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

    /** Solo en desarrollo: rellena una cuenta de prueba con una contraseña inventada al momento (el servidor falso la acepta). */
    fun onDemoAccount(account: DemoAccount) {
        savedStateHandle[EMAIL_KEY] = account.email
        val password = UUID.randomUUID().toString().take(12)
        _state.update { it.copy(email = account.email, password = password, emailTouched = true, passwordTouched = true, error = null) }
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
            val result = catchingNonCancellation { auth.signIn(state.email.trim(), state.password) }
            val role = result.getOrNull()
            if (role != null) {
                sessions.open(role)
                preferences.setOnboardingSeen()
                _state.update { it.copy(submitting = false, signedIn = true) }
                return@launch
            }
            when (result.exceptionOrNull()) {
                is OfflineException -> _state.update { it.copy(submitting = false, offline = true) }
                is InvalidCredentialsException -> _state.update { it.copy(submitting = false, error = LoginError.CREDENTIALS) }
                else -> _state.update { it.copy(submitting = false, error = LoginError.FAILED) }
            }
        }
    }

    fun onErrorDismissed() = _state.update { it.copy(error = null) }

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
                    demoAccounts = container.demoAccounts,
                )
            }
        }
    }
}
