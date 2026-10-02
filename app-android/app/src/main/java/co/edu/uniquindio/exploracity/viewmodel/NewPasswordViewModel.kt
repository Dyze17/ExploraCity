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
import co.edu.uniquindio.exploracity.domain.model.ExpiredLinkException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import kotlin.time.Duration.Companion.milliseconds

enum class NewPasswordContent { LOADING, READY, OFFLINE, ERROR }

enum class NewPasswordField { PASSWORD, CONFIRM }

/** Cómo termina 6.b: guardada (a 3 con su aviso) o con el enlace vencido (a 6C, que pide otro para [Expired.email]). */
sealed interface NewPasswordDone {
    data object Saved : NewPasswordDone

    data class Expired(val email: String) : NewPasswordDone
}

data class NewPasswordUiState(
    val content: NewPasswordContent = NewPasswordContent.LOADING,
    val email: String = "",
    /** Minutos que le quedan al enlace, redondeados hacia arriba («vence en 12 minutos»). */
    val minutesLeft: Int = 0,
    val password: String = "",
    val confirm: String = "",
    val passwordTouched: Boolean = false,
    val confirmTouched: Boolean = false,
    val saving: Boolean = false,
    val offline: Boolean = false,
    /** Otro fallo al guardar: el aviso queda hasta que la persona lo cierra. */
    val saveFailed: Boolean = false,
    val focusField: NewPasswordField? = null,
    val focusRequest: Int = 0,
    val done: NewPasswordDone? = null,
) {
    val hasLength: Boolean get() = AuthRules.isValidPassword(password)

    val hasLetterAndDigit: Boolean get() = AuthRules.hasLetterAndDigit(password)

    val passwordValid: Boolean get() = hasLength && hasLetterAndDigit

    val matches: Boolean get() = confirm == password

    /** Los requisitos no cumplidos se marcan como error después de dejar el campo. */
    val showRequirementErrors: Boolean get() = passwordTouched && !passwordValid

    val showMismatch: Boolean get() = confirmTouched && confirm.isNotEmpty() && !matches

    val canSubmit: Boolean get() = content == NewPasswordContent.READY && passwordValid && confirm.isNotEmpty() && matches && !offline && !saving
}

/**
 * 6.b · Nueva contraseña desde el enlace del correo. Primero el servidor dice para qué cuenta es y hasta cuándo vale;
 * si ya venció, o vence con la pantalla abierta, o al guardar, sigue a 6C. Al guardar vuelve al inicio de sesión (3).
 * Las contraseñas no se guardan al recrear la pantalla.
 */
class NewPasswordViewModel(
    private val auth: AuthRepository,
    private val connectivity: ConnectivityObserver,
    private val clock: Clock,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val token: String = savedStateHandle[TOKEN_KEY] ?: ""

    private val _state = MutableStateFlow(NewPasswordUiState(offline = !connectivity.isOnline.value))
    val state: StateFlow<NewPasswordUiState> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var expiryJob: Job? = null

    init {
        viewModelScope.launch {
            connectivity.isOnline.collect { online -> _state.update { it.copy(offline = !online) } }
        }
        load()
    }

    private fun load() {
        loadJob?.cancel()
        _state.update { it.copy(content = NewPasswordContent.LOADING) }
        loadJob = viewModelScope.launch {
            val result = catchingNonCancellation { auth.openResetLink(token) }
            val link = result.getOrNull()
            if (link != null) {
                _state.update { it.copy(content = NewPasswordContent.READY, email = link.email) }
                watchExpiry(link.expiresAt)
                return@launch
            }
            when (val error = result.exceptionOrNull()) {
                is ExpiredLinkException -> _state.update { it.copy(done = NewPasswordDone.Expired(error.email)) }
                is OfflineException -> _state.update { it.copy(content = NewPasswordContent.OFFLINE, offline = true) }
                else -> _state.update { it.copy(content = NewPasswordContent.ERROR) }
            }
        }
    }

    /** El aviso cuenta los minutos que quedan; al llegar a cero el enlace ya no sirve y se explica en 6C. */
    private fun watchExpiry(expiresAt: Instant) {
        expiryJob?.cancel()
        expiryJob = viewModelScope.launch {
            while (true) {
                val msLeft = expiresAt.toEpochMilli() - clock.millis()
                if (msLeft <= 0) {
                    _state.update { if (it.done == null && !it.saving) it.copy(done = NewPasswordDone.Expired(it.email)) else it }
                    break
                }
                val minutes = ((msLeft + MINUTE - 1) / MINUTE).toInt()
                _state.update { it.copy(minutesLeft = minutes) }
                delay((msLeft - (minutes - 1) * MINUTE).coerceAtLeast(1).milliseconds)
            }
        }
    }

    fun onRetry() = load()

    private inline fun edit(block: (NewPasswordUiState) -> NewPasswordUiState) {
        if (_state.value.saving) return
        _state.update(block)
    }

    fun onPasswordChange(password: String) = edit { it.copy(password = password) }

    fun onConfirmChange(confirm: String) = edit { it.copy(confirm = confirm) }

    fun onPasswordBlur() = _state.update { it.copy(passwordTouched = it.password.isNotEmpty() || it.passwordTouched) }

    fun onConfirmBlur() = _state.update { it.copy(confirmTouched = it.confirm.isNotEmpty() || it.confirmTouched) }

    fun onSubmit() {
        val state = _state.value
        if (state.saving || state.offline || state.content != NewPasswordContent.READY) return
        if (!state.passwordValid || state.confirm.isEmpty() || !state.matches) {
            val first = if (!state.passwordValid) NewPasswordField.PASSWORD else NewPasswordField.CONFIRM
            _state.update { it.copy(passwordTouched = true, confirmTouched = true, focusField = first, focusRequest = it.focusRequest + 1) }
            return
        }
        _state.update { it.copy(saving = true, saveFailed = false) }
        viewModelScope.launch {
            val result = catchingNonCancellation { auth.resetPassword(token, state.password) }
            when (val error = result.exceptionOrNull()) {
                null -> {
                    expiryJob?.cancel()
                    _state.update { it.copy(saving = false, done = NewPasswordDone.Saved) }
                }
                is ExpiredLinkException -> _state.update { it.copy(saving = false, done = NewPasswordDone.Expired(error.email.ifEmpty { it.email })) }
                is OfflineException -> _state.update { it.copy(saving = false, offline = true) }
                else -> _state.update { it.copy(saving = false, saveFailed = true) }
            }
        }
    }

    fun onSaveFailureDismissed() = _state.update { it.copy(saveFailed = false) }

    companion object {
        /** El nombre del argumento de la ruta (NewPassword.token). */
        private const val TOKEN_KEY = "token"
        private const val MINUTE = 60_000L

        val factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as ExploraApplication).container
                NewPasswordViewModel(
                    auth = container.authRepository,
                    connectivity = container.connectivity,
                    clock = Clock.systemUTC(),
                    savedStateHandle = createSavedStateHandle(),
                )
            }
        }
    }
}
