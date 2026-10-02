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

/** Avisos de una sola vez de «enlace enviado». */
enum class LinkSentMessage {
    /** «Te enviamos otro enlace.» */
    RESENT,

    /** «No pudimos enviar el correo. Intenta en unos minutos», con «Reintentar». */
    SEND_FAILED,
}

data class LinkSentUiState(
    val email: String,
    /** Segundos para poder reenviar; 0 = disponible. */
    val resendIn: Int,
    val resending: Boolean = false,
    val offline: Boolean = false,
    val message: LinkSentMessage? = null,
    /** Solo en desarrollo: los enlaces que abren lo que abriría el del correo. */
    val demoLinks: Boolean = false,
    /** Demostración: a ese correo no llegó nada (no tiene cuenta). */
    val demoNoMail: Boolean = false,
    /** Demostración: el enlace que se abre ahora. */
    val openLink: String? = null,
) {
    val canResend: Boolean get() = resendIn == 0 && !resending && !offline
}

/**
 * Un enlace salió hacia [LinkSentUiState.email]: 6.a (contraseña nueva) y «Confirma tu correo nuevo». El reenvío
 * ([resend]) espera 60 s desde el último envío; la cuenta se hace contra la hora del envío (guardada), así que sobrevive
 * a rotar la pantalla y a que Android cierre la app. [demoLink] (solo en desarrollo) da el enlace que llegó, o uno
 * vencido; null si no llegó ninguno.
 */
class LinkSentViewModel(
    private val resend: suspend (email: String) -> Unit,
    private val connectivity: ConnectivityObserver,
    private val clock: Clock,
    private val savedStateHandle: SavedStateHandle,
    private val demoLink: ((email: String, expired: Boolean) -> String?)? = null,
) : ViewModel() {

    private val email: String = savedStateHandle[EMAIL_KEY] ?: ""

    private var sentAt: Long
        get() = savedStateHandle[SENT_AT_KEY] ?: 0L
        set(value) {
            savedStateHandle[SENT_AT_KEY] = value
        }

    private val _state = MutableStateFlow(
        LinkSentUiState(email, resendIn = secondsLeft(), offline = !connectivity.isOnline.value, demoLinks = demoLink != null),
    )
    val state: StateFlow<LinkSentUiState> = _state.asStateFlow()

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
            val result = catchingNonCancellation { resend(email) }
            when (result.exceptionOrNull()) {
                null -> {
                    sentAt = clock.millis()
                    _state.update { it.copy(resending = false, message = LinkSentMessage.RESENT, demoNoMail = false) }
                    startCountdown()
                }
                is OfflineException -> _state.update { it.copy(resending = false, offline = true) }
                else -> _state.update { it.copy(resending = false, message = LinkSentMessage.SEND_FAILED) }
            }
        }
    }

    fun onMessageShown() = _state.update { it.copy(message = null) }

    /** Demostración: abre el último enlace que llegó, o uno vencido. */
    fun onDemoLink(expired: Boolean) {
        val link = demoLink ?: return
        val token = link(email, expired)
        _state.update { it.copy(openLink = token, demoNoMail = token == null) }
    }

    fun onLinkOpened() = _state.update { it.copy(openLink = null) }

    companion object {
        // Los nombres de los argumentos de las rutas (6.a y «Confirma tu correo nuevo»): el reenvío pisa la hora del envío.
        private const val EMAIL_KEY = "email"
        private const val SENT_AT_KEY = "sentAtMillis"

        /** 6.a · Reenvía el enlace para crear una contraseña nueva. */
        val recoveryFactory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as ExploraApplication).container
                val mailbox = container.demoMailbox
                LinkSentViewModel(
                    resend = container.authRepository::requestPasswordReset,
                    connectivity = container.connectivity,
                    clock = Clock.systemUTC(),
                    savedStateHandle = createSavedStateHandle(),
                    demoLink = mailbox?.let { box ->
                        { email, expired -> if (expired) box.expiredResetLink(email) else box.latestResetLink(email) }
                    },
                )
            }
        }

        /** «Confirma tu correo nuevo» · Reenvía el enlace del cambio pendiente. */
        val emailChangeFactory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as ExploraApplication).container
                val mailbox = container.demoEmailChangeMailbox
                LinkSentViewModel(
                    resend = { container.accountRepository.resendEmailChange() },
                    connectivity = container.connectivity,
                    clock = Clock.systemUTC(),
                    savedStateHandle = createSavedStateHandle(),
                    demoLink = mailbox?.let { box ->
                        { _, expired -> if (expired) box.expiredEmailChangeLink() else box.latestEmailChangeLink() }
                    },
                )
            }
        }
    }
}
