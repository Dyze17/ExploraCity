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
import co.edu.uniquindio.exploracity.data.repository.UserRepository
import co.edu.uniquindio.exploracity.domain.model.Badge
import co.edu.uniquindio.exploracity.domain.model.OwnProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** 26 y 27 · Los estados de carga no los dibuja el diseño; siguen a los de Avisos (25). */
sealed interface OwnProfileContent {
    data object Loading : OwnProfileContent

    /** Con [OwnProfile.savedAt] es lo guardado: sin red o sin respuesta del servidor. */
    data class Loaded(val profile: OwnProfile) : OwnProfileContent

    data object Error : OwnProfileContent

    /** Sin red y sin nada guardado todavía. */
    data object Offline : OwnProfileContent
}

data class OwnProfileUiState(
    val content: OwnProfileContent = OwnProfileContent.Loading,
    val offline: Boolean = false,
    /** 27A · «Cómo se obtiene» abierta con esta insignia. */
    val openBadgeId: String? = null,
) {
    val profile: OwnProfile? get() = (content as? OwnProfileContent.Loaded)?.profile

    val openBadge: Badge? get() = openBadgeId?.let { id -> profile?.badges?.firstOrNull { it.id == id } }
}

/** Lo comparten el perfil (26) y la pantalla de niveles e insignias (27): los dos muestran lo mismo y abren 27A. */
class OwnProfileViewModel(
    private val users: UserRepository,
    private val connectivity: ConnectivityObserver,
    private val savedStateHandle: SavedStateHandle,
    private val timeout: Duration = LOAD_TIMEOUT,
) : ViewModel() {

    private val _state = MutableStateFlow(
        OwnProfileUiState(offline = !connectivity.isOnline.value, openBadgeId = savedStateHandle[OPEN_BADGE_KEY]),
    )
    val state: StateFlow<OwnProfileUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        load()
        viewModelScope.launch {
            // Al volver la red se pone al día solo; lo guardado se reemplaza sin la silueta.
            connectivity.isOnline.drop(1).collect { online ->
                _state.update { it.copy(offline = !online) }
                if (online) refresh()
            }
        }
    }

    fun onRetry() = load()

    /** Al volver a la pestaña (tras ganar puntos en otra pantalla, por ejemplo) se pone al día sin la silueta. */
    fun onResumed() = refresh()

    /** Tocar una insignia abre su hoja (27A); sobrevive a la rotación. */
    fun onOpenBadge(id: String) = setOpenBadge(id)

    fun onDismissBadge() = setOpenBadge(null)

    private fun setOpenBadge(id: String?) {
        savedStateHandle[OPEN_BADGE_KEY] = id
        _state.update { it.copy(openBadgeId = id) }
    }

    private fun load() {
        loadJob?.cancel()
        _state.update { it.copy(content = OwnProfileContent.Loading) }
        loadJob = viewModelScope.launch {
            val content = fetch() ?: OwnProfileContent.Error
            _state.update { it.copy(content = content) }
        }
    }

    /** Sin silueta: si falla, se queda lo que había (salvo que no hubiera nada que mostrar). */
    private fun refresh() {
        if (loadJob?.isActive == true) return
        if (_state.value.content !is OwnProfileContent.Loaded) return load()
        loadJob = viewModelScope.launch {
            val content = fetch() as? OwnProfileContent.Loaded ?: return@launch
            _state.update { it.copy(content = content) }
        }
    }

    /** null si falla o tarda más de 8 s. */
    private suspend fun fetch(): OwnProfileContent? = try {
        withTimeoutOrNull(timeout) { users.ownProfile() }?.let { OwnProfileContent.Loaded(it) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: OfflineException) {
        OwnProfileContent.Offline
    } catch (e: Exception) {
        null
    }

    companion object {
        /** Como en el feed: más de 8 s cargando → error recuperable. */
        val LOAD_TIMEOUT = 8.seconds

        private const val OPEN_BADGE_KEY = "insignia"

        val factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as ExploraApplication).container
                OwnProfileViewModel(container.userRepository, container.connectivity, createSavedStateHandle())
            }
        }
    }
}
