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

/** A dónde sigue el arranque. */
enum class SplashDestination { ONBOARDING, LOGIN, FEED }

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
 * suyo, y sin red se ofrece seguir con lo guardado (1.c). La marca se ve al menos [minimum], aunque la decisión sea
 * inmediata: un destello de medio segundo parece un error.
 */
class SplashViewModel(
    private val preferences: AppPreferences,
    private val sessions: SessionStore,
    private val auth: AuthRepository,
    private val connectivity: ConnectivityObserver,
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

    /** 1.c · Al feed con lo guardado; el feed muestra su aviso de sin conexión (12). */
    fun onContinueOffline() {
        _state.value = SplashState.Done(SplashDestination.FEED)
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
        if (!connectivity.isOnline.value) return SplashState.Offline
        // Si el servidor no responde a tiempo se sigue igual: la sesión ya estaba abierta en el teléfono.
        val result = withTimeoutOrNull(timeout) { catchingNonCancellation { auth.resumeSession() } }
        return if (result != null && result.isFailure) SplashState.Offline else SplashState.Done(SplashDestination.FEED)
    }

    companion object {
        val MAX_SPLASH = 2.seconds

        /** Lo mínimo que se ve la marca; queda dentro de los 2 s del README. */
        val MIN_SPLASH = 1.5.seconds

        val factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as ExploraApplication).container
                SplashViewModel(container.preferences, container.sessionStore, container.authRepository, container.connectivity)
            }
        }
    }
}
