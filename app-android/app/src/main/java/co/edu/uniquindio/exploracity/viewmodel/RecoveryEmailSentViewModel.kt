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
import co.edu.uniquindio.exploracity.data.repository.DemoMailbox
import co.edu.uniquindio.exploracity.domain.model.AuthRules
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import kotlin.time.Duration.Companion.milliseconds

/** Avisos de una sola vez de 6.a. */
enum class RecoveryMessage {
    /** «Te enviamos otro enlace.» */
    RESENT,

    /** «No pudimos enviar el correo. Intenta en unos minutos», con «Reintentar». */
    SEND_FAILED,
}

data class RecoveryEmailSentUiState(
    val email: String,
    /** Segundos para poder reenviar; 0 = disponible. */
    val resendIn: Int,
    val resending: Boolean = false,
    val offline: Boolean = false,
    val message: RecoveryMessage? = null,
    /** Solo en desarrollo: los enlaces que abren 6.b y 6C sin un buzón real. */
    val demoLinks: Boolean = false,
    /** Demostración: a ese correo no llegó nada (no tiene cuenta). */
    val demoNoMail: Boolean = false,
    /** Demostración: el enlace que se abre ahora (6.b, o 6C si venció). */
    val openLink: String? = null,
) {
    val canResend: Boolean get() = resendIn == 0 && !resending && !offline
}

/**
 * 6.a · Revisa tu correo. El reenvío espera 60 s desde el último envío; la cuenta se hace contra la hora del envío
 * (guardada), así que sobrevive a rotar la pantalla y a que Android cierre la app.
 */
class RecoveryEmailSentViewModel(
    private val auth: AuthRepository,
    private val connectivity: ConnectivityObserver,
    private val clock: Clock,
    private val savedStateHandle: SavedStateHandle,
    private val mailbox: DemoMailbox? = null,
) : ViewModel() {

    private val email: String = savedStateHandle[EMAIL_KEY] ?: ""

    private var sentAt: Long
        get() = savedStateHandle[SENT_AT_KEY] ?: 0L
        set(value) {
            savedStateHandle[SENT_AT_KEY] = value
        }

    private val _state = MutableStateFlow(
        RecoveryEmailSentUiState(email, resendIn = secondsLeft(), offline = !connectivity.isOnline.value, demoLinks = mailbox != null),
    )
    val state: StateFlow<RecoveryEmailSentUiState> = _state.asStateFlow()

    private var countdown: Job? = null

    init {
        viewModelScope.launch {
            connectivity.isOnline.collect { online -> _state.update { it.copy(offline = !online) } }
        }
        startCountdown()
    }

    private fun msLeft(): Long = sentAt + AuthRules.RESEND_WAIT.inWholeMilliseconds - clock.millis()

    private fun secondsLeft(): Int = ((msLeft() + 999) / 1000).toInt().coerceAtLeast(0)

    private fun startCountdown() {
        countdown?.cancel()
        countdown = viewModelScope.launch {
            while (true) {
                val left = secondsLeft()
                _state.update { it.copy(resendIn = left) }
                if (left == 0) break
                // Hasta que el número baje uno: la cuenta sigue al reloj, no a cuántas veces se esperó.
                delay((msLeft() - (left - 1) * 1000L).coerceAtLeast(1).milliseconds)
            }
        }
    }

    fun onResend() {
        if (!_state.value.canResend) return
        _state.update { it.copy(resending = true, message = null) }
        viewModelScope.launch {
            val result = catchingNonCancellation { auth.requestPasswordReset(email) }
            when (result.exceptionOrNull()) {
                null -> {
                    sentAt = clock.millis()
                    _state.update { it.copy(resending = false, message = RecoveryMessage.RESENT, demoNoMail = false) }
                    startCountdown()
                }
                is OfflineException -> _state.update { it.copy(resending = false, offline = true) }
                else -> _state.update { it.copy(resending = false, message = RecoveryMessage.SEND_FAILED) }
            }
        }
    }

    fun onMessageShown() = _state.update { it.copy(message = null) }

    /** Demostración: abre el último enlace que llegó, o uno vencido para ver 6C. */
    fun onDemoLink(expired: Boolean) {
        val box = mailbox ?: return
        val token = if (expired) box.expiredResetLink(email) else box.latestResetLink(email)
        _state.update { it.copy(openLink = token, demoNoMail = token == null) }
    }

    fun onLinkOpened() = _state.update { it.copy(openLink = null) }

    companion object {
        // Los nombres de los argumentos de la ruta (RecoveryEmailSent): el reenvío pisa la hora del envío.
        private const val EMAIL_KEY = "email"
        private const val SENT_AT_KEY = "sentAtMillis"

        val factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as ExploraApplication).container
                RecoveryEmailSentViewModel(
                    auth = container.authRepository,
                    connectivity = container.connectivity,
                    clock = Clock.systemUTC(),
                    savedStateHandle = createSavedStateHandle(),
                    mailbox = container.demoMailbox,
                )
            }
        }
    }
}
