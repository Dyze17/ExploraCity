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
import co.edu.uniquindio.exploracity.data.repository.AccountRepository
import co.edu.uniquindio.exploracity.domain.model.AuthRules
import co.edu.uniquindio.exploracity.domain.model.EmailDeliveryException
import co.edu.uniquindio.exploracity.domain.model.EmailTakenException
import co.edu.uniquindio.exploracity.domain.model.InvalidCredentialsException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock

enum class ChangeEmailField { EMAIL, PASSWORD }

/** El aviso del fallo: [MAIL_FAILED] ofrece «Reintentar»; [FAILED] queda hasta «Cerrar». */
enum class ChangeEmailError { MAIL_FAILED, FAILED }

/** Salió el enlace hacia [email] en [sentAtMillis]; mientras tanto se sigue entrando con [currentEmail]. */
data class EmailChangeRequested(val email: String, val currentEmail: String, val sentAtMillis: Long)

data class ChangeEmailUiState(
    val currentEmail: String,
    /** El correo que espera confirmación de un pedido anterior (B1: se puede pedir otra vez). */
    val pendingEmail: String? = null,
    val newEmail: String = "",
    val password: String = "",
    val emailTouched: Boolean = false,
    val passwordTouched: Boolean = false,
    /** El correo que el servidor dijo que ya tiene cuenta; el aviso se va al cambiarlo. */
    val takenEmail: String? = null,
    /** La contraseña no coincidió; el aviso se va al cambiarla. */
    val wrongPassword: Boolean = false,
    val sending: Boolean = false,
    val offline: Boolean = false,
    val error: ChangeEmailError? = null,
    val focusField: ChangeEmailField? = null,
    val focusRequest: Int = 0,
    val sent: EmailChangeRequested? = null,
) {
    val emailValid: Boolean get() = AuthRules.isValidEmail(newEmail)

    val sameAsCurrent: Boolean get() = newEmail.trim().equals(currentEmail, ignoreCase = true)

    val emailTaken: Boolean get() = takenEmail != null && takenEmail == newEmail.trim().lowercase()

    val passwordValid: Boolean get() = AuthRules.isValidPassword(password)

    val emailOk: Boolean get() = emailValid && !sameAsCurrent && !emailTaken

    val passwordOk: Boolean get() = passwordValid && !wrongPassword

    val canSubmit: Boolean get() = emailOk && passwordOk && !offline && !sending
}

/**
 * «Cambiar correo» (Ajustes › Cuenta). Pide el correo nuevo y la contraseña (A1: quien tenga el teléfono desbloqueado
 * no puede quedarse con la cuenta) y envía un enlace al correo nuevo; el cambio se hace al abrirlo. La contraseña no se
 * guarda al recrear la pantalla y se borra al enviar.
 */
class ChangeEmailViewModel(
    private val accounts: AccountRepository,
    private val connectivity: ConnectivityObserver,
    private val clock: Clock,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _state = MutableStateFlow(
        accounts.account.value.let { account ->
            ChangeEmailUiState(
                currentEmail = account.email,
                pendingEmail = account.pendingEmail,
                newEmail = savedStateHandle[EMAIL_KEY] ?: "",
                offline = !connectivity.isOnline.value,
            )
        },
    )
    val state: StateFlow<ChangeEmailUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            connectivity.isOnline.collect { online -> _state.update { it.copy(offline = !online) } }
        }
        viewModelScope.launch {
            accounts.account.collect { account -> _state.update { it.copy(currentEmail = account.email, pendingEmail = account.pendingEmail) } }
        }
    }

    fun onEmailChange(email: String) {
        if (_state.value.sending) return
        savedStateHandle[EMAIL_KEY] = email
        _state.update { it.copy(newEmail = email) }
    }

    fun onPasswordChange(password: String) {
        if (_state.value.sending) return
        _state.update { it.copy(password = password, wrongPassword = false) }
    }

    fun onEmailBlur() = _state.update { it.copy(emailTouched = it.newEmail.isNotEmpty() || it.emailTouched) }

    fun onPasswordBlur() = _state.update { it.copy(passwordTouched = it.password.isNotEmpty() || it.passwordTouched) }

    fun onSubmit() {
        val state = _state.value
        if (state.sending || state.offline) return
        if (!state.emailOk || !state.passwordOk) {
            val first = if (!state.emailOk) ChangeEmailField.EMAIL else ChangeEmailField.PASSWORD
            _state.update { it.copy(emailTouched = true, passwordTouched = true, focusField = first, focusRequest = it.focusRequest + 1) }
            return
        }
        val email = state.newEmail.trim().lowercase()
        _state.update { it.copy(sending = true, error = null) }
        viewModelScope.launch {
            val result = catchingNonCancellation { accounts.requestEmailChange(email, state.password) }
            when (result.exceptionOrNull()) {
                null -> _state.update {
                    it.copy(sending = false, password = "", sent = EmailChangeRequested(email, state.currentEmail, clock.millis()))
                }
                is InvalidCredentialsException -> _state.update {
                    it.copy(sending = false, wrongPassword = true, focusField = ChangeEmailField.PASSWORD, focusRequest = it.focusRequest + 1)
                }
                is EmailTakenException -> _state.update {
                    it.copy(sending = false, takenEmail = email, focusField = ChangeEmailField.EMAIL, focusRequest = it.focusRequest + 1)
                }
                is EmailDeliveryException -> _state.update { it.copy(sending = false, error = ChangeEmailError.MAIL_FAILED) }
                is OfflineException -> _state.update { it.copy(sending = false, offline = true) }
                else -> _state.update { it.copy(sending = false, error = ChangeEmailError.FAILED) }
            }
        }
    }

    fun onErrorShown() = _state.update { it.copy(error = null) }

    /** Ya se abrió «Confirma tu correo nuevo»: al volver con «atrás» no se abre otra vez. */
    fun onSentHandled() = _state.update { it.copy(sent = null) }

    companion object {
        /** Como el argumento de la ruta: abre con el correo nuevo escrito (al pedir otro enlace desde el vencido). */
        private const val EMAIL_KEY = "newEmail"

        val factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as ExploraApplication).container
                ChangeEmailViewModel(
                    accounts = container.accountRepository,
                    connectivity = container.connectivity,
                    clock = Clock.systemUTC(),
                    savedStateHandle = createSavedStateHandle(),
                )
            }
        }
    }
}
