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
import co.edu.uniquindio.exploracity.domain.model.EmailTakenException
import co.edu.uniquindio.exploracity.domain.model.GoogleTokenRejectedException
import co.edu.uniquindio.exploracity.domain.model.NewAccount
import co.edu.uniquindio.exploracity.domain.model.ProfileLimits
import co.edu.uniquindio.exploracity.domain.model.Registration
import co.edu.uniquindio.exploracity.domain.model.Residency
import co.edu.uniquindio.exploracity.domain.model.UnconfirmedRegistrationException
import co.edu.uniquindio.exploracity.domain.model.UserRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

enum class RegisterField { NAME, EMAIL, PASSWORD }

/** C1 · El registro con la cuenta de Google: el correo es el de Google y no hay contraseña. */
data class GoogleMode(val email: String)

/** Por qué no se entró: la API respondió que no creó la cuenta, o no respondió y puede que sí la haya creado. */
enum class RegisterFailure { NOT_CREATED, UNCONFIRMED }

data class RegisterUiState(
    val name: String = "",
    val email: String = "",
    val password: String = "",
    /** «¿Cómo te presentas?»: «De visita» viene elegida, como en 4.a. */
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
    val failure: RegisterFailure? = null,
    val focusField: RegisterField? = null,
    val focusRequest: Int = 0,
    val registered: Registration? = null,
    /** ADR-15 · Modo Google (C1): sin correo ni contraseña que escribir. */
    val google: GoogleMode? = null,
    /** «Continuar con Google» encontró la cuenta ya vinculada: se entra sin «Tu cuenta quedó lista». */
    val signedIn: Boolean = false,
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

    /** En modo Google solo falta el nombre: el correo es el de Google y no hay contraseña. */
    val fieldsValid: Boolean get() = if (google != null) nameValid else nameValid && emailValid && passwordValid && !emailTaken

    /** README 4: sin la autorización el botón está deshabilitado; sin conexión, también. */
    val canSubmit: Boolean get() = fieldsValid && consent && !offline && !submitting
}

/**
 * 4 · Registro con autorización de datos. Al crear la cuenta abre la sesión y marca el onboarding como visto, como el
 * inicio de sesión (3). Si el correo ya tiene cuenta lo dice junto al campo; si falla el correo de bienvenida la cuenta
 * queda igual (lo dice el feed). Si el servidor no respondió, no dice que la cuenta no se creó: puede que sí, y el
 * reintento entra con ella. La contraseña no se guarda al recrear la pantalla; lo demás sí.
 */
class RegisterViewModel(
    private val auth: AuthRepository,
    private val sessions: SessionStore,
    private val preferences: AppPreferences,
    private val connectivity: ConnectivityObserver,
    private val savedStateHandle: SavedStateHandle,
    private val googleAuth: GoogleAuthRepository,
    private val googleCredentials: GoogleCredentials? = null,
    /** Lo que la app necesita antes de entrar: la ciudad y el perfil. */
    private val prepare: suspend () -> Unit = {},
) : ViewModel() {

    /**
     * El mismo en cada intento de este formulario, también si Android cierra la app: si un intento creó la cuenta y su
     * respuesta no llegó, el siguiente entra con ella.
     */
    private val clientId: String = savedStateHandle[CLIENT_ID_KEY] ?: UUID.randomUUID().toString().also { savedStateHandle[CLIENT_ID_KEY] = it }

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

    /**
     * C1 · El ID token de la cuenta de Google nueva: llega en la ruta desde el inicio de sesión (3) o lo pone «Continuar
     * con Google» de esta pantalla. Vale una hora; si venció, la API lo rechaza y se vuelve a elegir la cuenta.
     */
    private val googleToken: String? get() = savedStateHandle[GOOGLE_TOKEN_KEY]

    val google = GoogleAccess(
        google = googleAuth,
        scope = viewModelScope,
        prepare = prepare,
        onSignedIn = ::enter,
        onRegistrationRequired = ::enterGoogleMode,
        onOffline = { _state.update { it.copy(offline = true) } },
    )

    init {
        val token = googleToken
        val email = savedStateHandle.get<String>(GOOGLE_EMAIL_KEY)
        if (token != null && email != null) enterGoogleMode(token, email, savedStateHandle[GOOGLE_NAME_KEY])
        viewModelScope.launch {
            connectivity.isOnline.collect { online -> _state.update { it.copy(offline = !online) } }
        }
    }

    suspend fun requestGoogleCredential(activity: Context): GoogleCredentialResult =
        googleCredentials?.request(activity) ?: GoogleCredentialResult.Failed

    /** El nombre de Google queda escrito si la persona aún no puso uno; lo puede cambiar. */
    private fun enterGoogleMode(token: String, email: String, name: String?) {
        savedStateHandle[GOOGLE_TOKEN_KEY] = token
        savedStateHandle[GOOGLE_EMAIL_KEY] = email
        savedStateHandle[GOOGLE_NAME_KEY] = name
        _state.update { state ->
            val prefilled = state.name.ifBlank { name.orEmpty() }
            savedStateHandle[NAME_KEY] = prefilled
            state.copy(google = GoogleMode(email), name = prefilled, takenEmail = null, focusField = null)
        }
    }

    /** «Usar otro correo y una contraseña»: vuelve al registro de siempre, con el nombre que ya está escrito. */
    fun onUseEmailInstead() = edit {
        clearGoogleMode()
        it.copy(google = null)
    }

    private fun clearGoogleMode() {
        savedStateHandle.remove<String>(GOOGLE_TOKEN_KEY)
        savedStateHandle.remove<String>(GOOGLE_EMAIL_KEY)
        savedStateHandle.remove<String>(GOOGLE_NAME_KEY)
    }

    private suspend fun enter(role: UserRole) {
        sessions.open(role)
        preferences.setOnboardingSeen()
        _state.update { it.copy(submitting = false, signedIn = true) }
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
        _state.update { it.copy(submitting = true, failure = null) }
        val token = googleToken
        if (state.google != null && token != null) {
            submitWithGoogle(token, state)
            return
        }
        viewModelScope.launch {
            val account = NewAccount(state.name.trim(), state.email.trim(), state.password, state.residency, clientId)
            val result = catchingNonCancellation { auth.register(account).also { prepare() } }
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
                is UnconfirmedRegistrationException -> _state.update { it.copy(submitting = false, failure = RegisterFailure.UNCONFIRMED) }
                else -> _state.update { it.copy(submitting = false, failure = RegisterFailure.NOT_CREATED) }
            }
        }
    }

    /** C1 · Crea la cuenta sin contraseña con la cuenta de Google elegida. */
    private fun submitWithGoogle(token: String, state: RegisterUiState) {
        val email = checkNotNull(state.google).email
        viewModelScope.launch {
            val result = catchingNonCancellation { googleAuth.registerWithGoogle(token, state.name.trim(), state.residency).also { prepare() } }
            val registration = result.getOrNull()
            if (registration != null) {
                sessions.open(registration.role)
                preferences.setOnboardingSeen()
                _state.update { it.copy(submitting = false, registered = registration) }
                return@launch
            }
            when (result.exceptionOrNull()) {
                is OfflineException -> _state.update { it.copy(submitting = false, offline = true) }
                // Mientras tanto ese correo tomó una cuenta: el registro de siempre lo dice junto al correo.
                is EmailTakenException -> {
                    clearGoogleMode()
                    savedStateHandle[EMAIL_KEY] = email
                    _state.update { it.copy(submitting = false, google = null, email = email, takenEmail = email.lowercase()) }
                }
                // El token venció (dura una hora): se vuelve a elegir la cuenta, con lo escrito intacto.
                is GoogleTokenRejectedException -> {
                    clearGoogleMode()
                    _state.update { it.copy(submitting = false, google = null) }
                    google.showError(GoogleError.REJECTED)
                }
                is UnconfirmedRegistrationException -> _state.update { it.copy(submitting = false, failure = RegisterFailure.UNCONFIRMED) }
                else -> _state.update { it.copy(submitting = false, failure = RegisterFailure.NOT_CREATED) }
            }
        }
    }

    fun onFailureDismissed() = _state.update { it.copy(failure = null) }

    companion object {
        private const val NAME_KEY = "nombre"
        private const val EMAIL_KEY = "correo"
        private const val RESIDENCY_KEY = "residencia"
        private const val CONSENT_KEY = "autorizacion"
        private const val CLIENT_ID_KEY = "id_de_registro"

        // Los nombres de los argumentos de la ruta Register (navigation/Routes.kt): ahí llegan desde 3.
        private const val GOOGLE_TOKEN_KEY = "googleToken"
        private const val GOOGLE_EMAIL_KEY = "googleEmail"
        private const val GOOGLE_NAME_KEY = "googleName"

        val factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as ExploraApplication).container
                RegisterViewModel(
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
