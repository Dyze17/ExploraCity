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
import co.edu.uniquindio.exploracity.data.repository.AuthRepository
import co.edu.uniquindio.exploracity.domain.model.AuthRules
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock

/** 5 → 6.a · El enlace salió hacia [email] en [sentAtMillis] (para la cuenta regresiva del reenvío). */
data class RecoverySent(val email: String, val sentAtMillis: Long)

data class RecoverPasswordUiState(
    val email: String = "",
    val emailTouched: Boolean = false,
    val sending: Boolean = false,
    val offline: Boolean = false,
    /** El servicio de correo falló: aviso con «Reintentar», sin perder el correo escrito. */
    val failed: Boolean = false,
    val focusRequest: Int = 0,
    val sent: RecoverySent? = null,
) {
    val emailValid: Boolean get() = AuthRules.isValidEmail(email)

    val showEmailError: Boolean get() = emailTouched && !emailValid

    val canSubmit: Boolean get() = emailValid && !offline && !sending
}

/**
 * 5 · Recuperar contraseña. Valida el formato antes de enviar y, exista o no la cuenta, sigue a «Revisa tu correo»
 * (6.a): la respuesta del servidor es la misma. Abre con el correo que venía escrito (3 o 6C).
 */
class RecoverPasswordViewModel(
    private val auth: AuthRepository,
    private val connectivity: ConnectivityObserver,
    private val clock: Clock,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _state = MutableStateFlow(
        RecoverPasswordUiState(email = savedStateHandle[EMAIL_KEY] ?: "", offline = !connectivity.isOnline.value),
    )
    val state: StateFlow<RecoverPasswordUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            connectivity.isOnline.collect { online -> _state.update { it.copy(offline = !online) } }
        }
    }

    fun onEmailChange(email: String) {
        if (_state.value.sending) return
        savedStateHandle[EMAIL_KEY] = email
        _state.update { it.copy(email = email) }
    }

    fun onEmailBlur() = _state.update { it.copy(emailTouched = it.email.isNotEmpty() || it.emailTouched) }

    fun onSubmit() {
        val state = _state.value
        if (state.sending || state.offline) return
        if (!state.emailValid) {
            _state.update { it.copy(emailTouched = true, focusRequest = it.focusRequest + 1) }
            return
        }
        val email = state.email.trim()
        _state.update { it.copy(sending = true, failed = false) }
        viewModelScope.launch {
            val result = catchingNonCancellation { auth.requestPasswordReset(email) }
            when (result.exceptionOrNull()) {
                null -> _state.update { it.copy(sending = false, sent = RecoverySent(email, clock.millis())) }
                is OfflineException -> _state.update { it.copy(sending = false, offline = true) }
                else -> _state.update { it.copy(sending = false, failed = true) }
            }
        }
    }

    fun onFailureShown() = _state.update { it.copy(failed = false) }

    /** Ya se abrió 6.a: al volver con «atrás» no se abre otra vez. */
    fun onSentHandled() = _state.update { it.copy(sent = null) }

    companion object {
        /** Como el argumento de la ruta de 5: abre con el correo que venía escrito. */
        private const val EMAIL_KEY = "email"

        val factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as ExploraApplication).container
                RecoverPasswordViewModel(
                    auth = container.authRepository,
                    connectivity = container.connectivity,
                    clock = Clock.systemUTC(),
                    savedStateHandle = createSavedStateHandle(),
                )
            }
        }
    }
}
