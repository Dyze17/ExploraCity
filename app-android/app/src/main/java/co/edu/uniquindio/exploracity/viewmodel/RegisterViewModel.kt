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
import co.edu.uniquindio.exploracity.domain.model.EmailTakenException
import co.edu.uniquindio.exploracity.domain.model.NewAccount
import co.edu.uniquindio.exploracity.domain.model.ProfileLimits
import co.edu.uniquindio.exploracity.domain.model.Registration
import co.edu.uniquindio.exploracity.domain.model.Residency
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class RegisterField { NAME, EMAIL, PASSWORD }

data class RegisterUiState(
    val name: String = "",
    val email: String = "",
    val password: String = "",
    /** «¿Cómo te presentas?»: «De visita» viene elegida, como «Turista» en 4.a. */
    val residency: Residency = Residency.VISITOR,
    /** Ley 1581: la autorización nunca viene marcada. */
    val consent: Boolean = false,
    val nameTouched: Boolean = false,
    val emailTouched: Boolean = false,
    val passwordTouched: Boolean = false,
    /** El correo que el servidor dijo que ya tiene cuenta; el aviso se va al cambiarlo. */
    val takenEmail: String? = null,
    val submitting: Boolean = false,
    val offline: Boolean = false,
    /** Otro fallo: el aviso queda hasta que la persona lo cierra, y lo escrito sigue ahí. */
    val failed: Boolean = false,
    val focusField: RegisterField? = null,
    val focusRequest: Int = 0,
    val registered: Registration? = null,
) {
    private val nameLength: Int get() = name.trim().length

    /** Faltan caracteres para el mínimo de 28 (2); 0 si alcanza. */
    val nameMissing: Int get() = (ProfileLimits.NAME_MIN - nameLength).coerceAtLeast(0)

    /** Caracteres de más sobre el máximo de 28 (40); 0 si cabe. */
    val nameExcess: Int get() = (nameLength - ProfileLimits.NAME_MAX).coerceAtLeast(0)

    val nameValid: Boolean get() = nameMissing == 0 && nameExcess == 0

    val emailValid: Boolean get() = AuthRules.isValidEmail(email)

    val emailTaken: Boolean get() = takenEmail != null && takenEmail == email.trim().lowercase()

    val passwordValid: Boolean get() = AuthRules.isValidNewPassword(password)

    val showNameError: Boolean get() = nameTouched && !nameValid

    val showEmailError: Boolean get() = emailTouched && !emailValid

    val showPasswordError: Boolean get() = passwordTouched && !passwordValid

    val fieldsValid: Boolean get() = nameValid && emailValid && passwordValid && !emailTaken

    /** README 4: sin la autorización el botón está deshabilitado; sin conexión, también. */
    val canSubmit: Boolean get() = fieldsValid && consent && !offline && !submitting
}

/**
 * 4 · Registro con autorización de datos. Al crear la cuenta abre la sesión y marca el onboarding como visto, como el
 * inicio de sesión (3). Si el correo ya tiene cuenta lo dice junto al campo; si falla el correo de bienvenida la cuenta
 * queda igual (lo dice el feed). La contraseña no se guarda al recrear la pantalla; lo demás sí.
 */
class RegisterViewModel(
    private val auth: AuthRepository,
    private val sessions: SessionStore,
    private val preferences: AppPreferences,
    private val connectivity: ConnectivityObserver,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _state = MutableStateFlow(
        RegisterUiState(
            name = savedStateHandle[NAME_KEY] ?: "",
            email = savedStateHandle[EMAIL_KEY] ?: "",
            residency = savedStateHandle.get<String>(RESIDENCY_KEY)?.let { saved -> Residency.entries.firstOrNull { it.name == saved } }
                ?: Residency.VISITOR,
            consent = savedStateHandle[CONSENT_KEY] ?: false,
            offline = !connectivity.isOnline.value,
        ),
    )
    val state: StateFlow<RegisterUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            connectivity.isOnline.collect { online -> _state.update { it.copy(offline = !online) } }
        }
    }

    private inline fun edit(block: (RegisterUiState) -> RegisterUiState) {
        if (_state.value.submitting) return
        _state.update(block)
    }

    fun onNameChange(name: String) = edit {
        savedStateHandle[NAME_KEY] = name
        it.copy(name = name)
    }

    fun onEmailChange(email: String) = edit {
        savedStateHandle[EMAIL_KEY] = email
        it.copy(email = email)
    }

    fun onPasswordChange(password: String) = edit { it.copy(password = password) }

    fun onResidencyChange(residency: Residency) = edit {
        savedStateHandle[RESIDENCY_KEY] = residency.name
        it.copy(residency = residency)
    }

    fun onConsentChange(consent: Boolean) = edit {
        savedStateHandle[CONSENT_KEY] = consent
        it.copy(consent = consent)
    }

    fun onNameBlur() = _state.update { it.copy(nameTouched = it.name.isNotEmpty() || it.nameTouched) }

    fun onEmailBlur() = _state.update { it.copy(emailTouched = it.email.isNotEmpty() || it.emailTouched) }

    fun onPasswordBlur() = _state.update { it.copy(passwordTouched = it.password.isNotEmpty() || it.passwordTouched) }

    fun onSubmit() {
        val state = _state.value
        if (state.submitting || state.offline) return
        if (!state.fieldsValid) {
            val first = when {
                !state.nameValid -> RegisterField.NAME
                !state.emailValid || state.emailTaken -> RegisterField.EMAIL
                else -> RegisterField.PASSWORD
            }
            _state.update {
                it.copy(nameTouched = true, emailTouched = true, passwordTouched = true, focusField = first, focusRequest = it.focusRequest + 1)
            }
            return
        }
        // El texto bajo el botón ya dice que falta la autorización.
        if (!state.consent) return
        _state.update { it.copy(submitting = true, failed = false) }
        viewModelScope.launch {
            val account = NewAccount(state.name.trim(), state.email.trim(), state.password, state.residency)
            val result = catchingNonCancellation { auth.register(account) }
            val registration = result.getOrNull()
            if (registration != null) {
                sessions.open(registration.role)
                preferences.setOnboardingSeen()
                _state.update { it.copy(submitting = false, registered = registration) }
                return@launch
            }
            when (result.exceptionOrNull()) {
                is OfflineException -> _state.update { it.copy(submitting = false, offline = true) }
                is EmailTakenException -> _state.update {
                    it.copy(
                        submitting = false,
                        takenEmail = state.email.trim().lowercase(),
                        focusField = RegisterField.EMAIL,
                        focusRequest = it.focusRequest + 1,
                    )
                }
                else -> _state.update { it.copy(submitting = false, failed = true) }
            }
        }
    }

    fun onFailureDismissed() = _state.update { it.copy(failed = false) }

    companion object {
        private const val NAME_KEY = "nombre"
        private const val EMAIL_KEY = "correo"
        private const val RESIDENCY_KEY = "residencia"
        private const val CONSENT_KEY = "autorizacion"

        val factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as ExploraApplication).container
                RegisterViewModel(
                    auth = container.authRepository,
                    sessions = container.sessionStore,
                    preferences = container.preferences,
                    connectivity = container.connectivity,
                    savedStateHandle = createSavedStateHandle(),
                )
            }
        }
    }
}
