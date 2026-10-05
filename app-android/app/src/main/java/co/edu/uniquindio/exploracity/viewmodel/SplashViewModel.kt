package co.edu.uniquindio.exploracity.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import co.edu.uniquindio.exploracity.ExploraApplication
import co.edu.uniquindio.exploracity.data.connectivity.ConnectivityObserver
import co.edu.uniquindio.exploracity.data.local.AppPreferences
import co.edu.uniquindio.exploracity.data.local.SessionStore
import co.edu.uniquindio.exploracity.data.repository.AuthRepository
import co.edu.uniquindio.exploracity.domain.model.SessionEndedException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** A dónde sigue el arranque. [SESSION_ENDED]: al inicio de sesión, diciendo que la sesión terminó. */
enum class SplashDestination { ONBOARDING, LOGIN, SESSION_ENDED, FEED }

sealed interface SplashState {
    /** 1.a · «Preparando tu ciudad…». */
    data object Loading : SplashState

    /** 1.c · Hay sesión pero no conexión: «Reintentar» o «Continuar sin conexión». */
    data object Offline : SplashState

    data class Done(val destination: SplashDestination) : SplashState
}

/**
 * 1 · Decide por dónde entra la persona. Sin sesión no hace falta red: onboarding (2) la primera vez, si no inicio de
 * sesión (3). Con sesión, el servidor la confirma como mucho 2 s (README); si tarda más se sigue al feed, que trae lo
 * suyo, y sin red se ofrece seguir con lo guardado (1.c). Si la API ya no la acepta, se borra y se pide entrar de
 * nuevo. Al feed solo se entra con la ciudad lista (B1). La marca se ve al menos [minimum], aunque la decisión sea
 * inmediata: un destello de medio segundo parece un error.
 */
class SplashViewModel(
    private val preferences: AppPreferences,
    private val sessions: SessionStore,
    private val auth: AuthRepository,
    private val connectivity: ConnectivityObserver,
    /** Hay tokens de la API guardados. Sin ellos, la sesión es de antes de conectar la app con la API. */
    private val hasTokens: suspend () -> Boolean = { true },
    /** La ciudad está guardada o se pudo traer. */
    private val cityReady: suspend () -> Boolean = { true },
    /** Borra lo de la cuenta en el teléfono: la API ya no acepta la sesión. */
    private val endSession: suspend () -> Unit = {},
    private val timeout: Duration = MAX_SPLASH,
    private val minimum: Duration = MIN_SPLASH,
) : ViewModel() {

    private val _state = MutableStateFlow<SplashState>(SplashState.Loading)
    val state: StateFlow<SplashState> = _state.asStateFlow()

    private var startJob: Job? = null

    init {
        start()
    }

    fun onRetry() = start()

    /** 1.c · Al feed con lo guardado; el feed muestra su aviso de sin conexión (12). Sin la ciudad guardada, no se puede. */
    fun onContinueOffline() {
        viewModelScope.launch {
            if (cityReady()) _state.value = SplashState.Done(SplashDestination.FEED)
        }
    }

    private fun start() {
        startJob?.cancel()
        _state.value = SplashState.Loading
        startJob = viewModelScope.launch {
            // El mínimo corre a la vez que la decisión: si esta tarda más, no se suma.
            val shown = launch { delay(minimum) }
            val next = decide()
            shown.join()
            _state.value = next
        }
    }

    private suspend fun decide(): SplashState {
        if (sessions.role.first() == null) {
            val seen = preferences.onboardingSeen.first()
            return SplashState.Done(if (seen) SplashDestination.LOGIN else SplashDestination.ONBOARDING)
        }
        if (!hasTokens()) {
            endSession()
            return SplashState.Done(SplashDestination.LOGIN)
        }
        if (!connectivity.isOnline.value) return SplashState.Offline
        // Si el servidor no responde a tiempo se sigue igual: la sesión ya estaba abierta en el teléfono.
        val result = withTimeoutOrNull(timeout) { catchingNonCancellation { auth.resumeSession() } }
        if (result?.exceptionOrNull() is SessionEndedException) {
            endSession()
            return SplashState.Done(SplashDestination.SESSION_ENDED)
        }
        if (result != null && result.isFailure) return SplashState.Offline
        return if (cityReady()) SplashState.Done(SplashDestination.FEED) else SplashState.Offline
    }

    companion object {
        val MAX_SPLASH = 2.seconds

        /** Lo mínimo que se ve la marca; queda dentro de los 2 s del README. */
        val MIN_SPLASH = 1.5.seconds

        val factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as ExploraApplication).container
                SplashViewModel(
                    preferences = container.preferences,
                    sessions = container.sessionStore,
                    auth = container.authRepository,
                    connectivity = container.connectivity,
                    hasTokens = container::hasApiSession,
                    cityReady = container::cityReady,
                    endSession = container.sessionManager::sessionEnded,
                )
            }
        }
    }
}
