package co.edu.uniquindio.exploracity.viewmodel

import co.edu.uniquindio.exploracity.data.connectivity.OfflineException
import co.edu.uniquindio.exploracity.data.google.GoogleCredentialResult
import co.edu.uniquindio.exploracity.data.repository.GoogleAuthRepository
import co.edu.uniquindio.exploracity.domain.model.EmailTakenException
import co.edu.uniquindio.exploracity.domain.model.GoogleSignInOutcome
import co.edu.uniquindio.exploracity.domain.model.GoogleSignInUnavailableException
import co.edu.uniquindio.exploracity.domain.model.GoogleTokenRejectedException
import co.edu.uniquindio.exploracity.domain.model.InvalidCredentialsException
import co.edu.uniquindio.exploracity.domain.model.TooManyAttemptsException
import co.edu.uniquindio.exploracity.domain.model.UserRole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Por qué no se pudo entrar con Google; el aviso queda hasta que la persona lo cierra. */
enum class GoogleError {
    /** El teléfono no tiene cuentas de Google. */
    NO_ACCOUNT,

    /** Credential Manager o la API fallaron: se puede intentar otra vez o entrar con el correo. */
    FAILED,

    /** Google no confirmó la cuenta (el token venció): hay que elegirla de nuevo. */
    REJECTED,

    /** La API no tiene entrar con Google. */
    UNAVAILABLE,

    /** El correo ya tiene otra cuenta de Google vinculada. */
    EMAIL_TAKEN,
}

/** B1 · Por qué no se vinculó con la contraseña. */
enum class LinkError { CREDENTIALS, TOO_MANY_ATTEMPTS, FAILED }

/** B1 · La contraseña de la cuenta de ese correo, para vincular Google. Solo en memoria: nunca se guarda. */
data class LinkPrompt(
    val email: String,
    val password: String = "",
    val submitting: Boolean = false,
    val error: LinkError? = null,
)

/** C1 · La cuenta de Google es nueva: el registro (4) en modo Google empieza con esto. */
data class GoogleRegistrationStart(val idToken: String, val email: String, val name: String?)

data class GoogleUiState(
    /** Esperando la respuesta de la API después de elegir la cuenta. */
    val busy: Boolean = false,
    val error: GoogleError? = null,
    val link: LinkPrompt? = null,
)

/**
 * ADR-15 · «Continuar con Google», el mismo en el inicio de sesión (3) y en el registro (4). La pantalla pide la cuenta
 * a Credential Manager (necesita la Activity) y pasa aquí el resultado; esto lo lleva a la API:
 * - Cuenta vinculada: [onSignedIn], después de dejar lista la app ([prepare]).
 * - Cuenta nueva: [onRegistrationRequired] con el token, el correo y el nombre de Google (C1).
 * - El correo ya tiene cuenta con contraseña: pide esa contraseña ([LinkPrompt], B1) y, al vincular, [onSignedIn].
 */
class GoogleAccess(
    private val google: GoogleAuthRepository,
    private val scope: CoroutineScope,
    private val prepare: suspend () -> Unit,
    private val onSignedIn: suspend (UserRole) -> Unit,
    private val onRegistrationRequired: (idToken: String, email: String, name: String?) -> Unit,
    /** Sin red: la pantalla ya tiene su aviso arriba (3.c). */
    private val onOffline: () -> Unit,
) {
    private val _state = MutableStateFlow(GoogleUiState())
    val state: StateFlow<GoogleUiState> = _state.asStateFlow()

    /** El token de la cuenta elegida, mientras se pide la contraseña para vincular. */
    private var linkToken: String? = null

    fun onCredential(result: GoogleCredentialResult) {
        when (result) {
            GoogleCredentialResult.Cancelled -> Unit
            GoogleCredentialResult.NoAccount -> _state.update { it.copy(error = GoogleError.NO_ACCOUNT) }
            GoogleCredentialResult.Failed -> _state.update { it.copy(error = GoogleError.FAILED) }
            is GoogleCredentialResult.Token -> signIn(result.idToken)
        }
    }

    private fun signIn(idToken: String) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null) }
        scope.launch {
            val result = catchingNonCancellation {
                google.signInWithGoogle(idToken).also { if (it is GoogleSignInOutcome.SignedIn) prepare() }
            }
            _state.update { it.copy(busy = false) }
            when (val outcome = result.getOrNull()) {
                is GoogleSignInOutcome.SignedIn -> onSignedIn(outcome.role)
                is GoogleSignInOutcome.RegistrationRequired -> onRegistrationRequired(idToken, outcome.email, outcome.name)
                is GoogleSignInOutcome.LinkRequired -> {
                    linkToken = idToken
                    _state.update { it.copy(link = LinkPrompt(outcome.email)) }
                }
                null -> fail(result.exceptionOrNull())
            }
        }
    }

    /** El aviso se va al escribir; el mismo texto (el campo que se vuelve a pintar) no lo quita. */
    fun onLinkPasswordChange(password: String) = _state.update { state ->
        val link = state.link?.takeUnless { it.submitting || it.password == password } ?: return@update state
        state.copy(link = link.copy(password = password, error = null))
    }

    fun onLinkSubmit() {
        val link = _state.value.link ?: return
        val token = linkToken ?: return
        if (link.submitting || link.password.isEmpty()) return
        _state.update { it.copy(link = link.copy(submitting = true, error = null)) }
        scope.launch {
            val result = catchingNonCancellation { google.linkGoogle(token, link.password).also { prepare() } }
            val role = result.getOrNull()
            if (role != null) {
                linkToken = null
                _state.update { it.copy(link = null) }
                onSignedIn(role)
                return@launch
            }
            val error = when (result.exceptionOrNull()) {
                is InvalidCredentialsException -> LinkError.CREDENTIALS
                is TooManyAttemptsException -> LinkError.TOO_MANY_ATTEMPTS
                is GoogleTokenRejectedException, is OfflineException -> {
                    // El token venció mientras escribía, o se fue la red: se cierra y se vuelve a empezar.
                    linkToken = null
                    _state.update { it.copy(link = null) }
                    fail(result.exceptionOrNull())
                    return@launch
                }
                else -> LinkError.FAILED
            }
            // La contraseña queda escrita, como en el inicio de sesión (3.c): con el ojo se ve qué no coincide.
            _state.update { state -> state.copy(link = state.link?.copy(submitting = false, error = error)) }
        }
    }

    fun onLinkDismissed() {
        if (_state.value.link?.submitting == true) return
        linkToken = null
        _state.update { it.copy(link = null) }
    }

    fun onErrorDismissed() = _state.update { it.copy(error = null) }

    /** Un fallo que descubre la pantalla: el registro en modo Google con el token ya vencido. */
    fun showError(error: GoogleError) = _state.update { it.copy(error = error) }

    private fun fail(error: Throwable?) {
        val shown = when (error) {
            is OfflineException -> {
                onOffline()
                return
            }
            is GoogleTokenRejectedException -> GoogleError.REJECTED
            is GoogleSignInUnavailableException -> GoogleError.UNAVAILABLE
            is EmailTakenException -> GoogleError.EMAIL_TAKEN
            else -> GoogleError.FAILED
        }
        _state.update { it.copy(error = shown) }
    }
}
