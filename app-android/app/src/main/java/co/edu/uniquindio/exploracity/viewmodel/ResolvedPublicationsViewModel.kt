package co.edu.uniquindio.exploracity.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import co.edu.uniquindio.exploracity.ExploraApplication
import co.edu.uniquindio.exploracity.data.connectivity.ConnectivityObserver
import co.edu.uniquindio.exploracity.data.connectivity.OfflineException
import co.edu.uniquindio.exploracity.data.repository.ModerationRepository
import co.edu.uniquindio.exploracity.domain.model.ResolvedPublication
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ResolvedContent {
    data object Loading : ResolvedContent

    data class Loaded(val items: List<ResolvedPublication>) : ResolvedContent

    /** «Resueltas» no se guarda: sin conexión no hay qué mostrar. */
    data object Offline : ResolvedContent

    data object Error : ResolvedContent
}

data class ResolvedPublicationsUiState(val content: ResolvedContent = ResolvedContent.Loading)

/**
 * «Resueltas» (E1) · Lo ya decidido, de lo más reciente a lo más antiguo. Al volver de 36 se pone al día sin la carga a
 * pantalla completa; al volver la red, también.
 */
class ResolvedPublicationsViewModel(
    private val moderation: ModerationRepository,
    private val connectivity: ConnectivityObserver,
) : ViewModel() {

    private val _state = MutableStateFlow(ResolvedPublicationsUiState())
    val state: StateFlow<ResolvedPublicationsUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        load(showLoading = true)
        viewModelScope.launch {
            connectivity.isOnline.drop(1).collect { online -> if (online) load(showLoading = false) }
        }
    }

    private fun load(showLoading: Boolean) {
        loadJob?.cancel()
        val hadList = _state.value.content is ResolvedContent.Loaded
        if (showLoading || !hadList) _state.update { it.copy(content = ResolvedContent.Loading) }
        loadJob = viewModelScope.launch {
            val result = catchingNonCancellation { moderation.resolved() }
            val items = result.getOrNull()
            when {
                items != null -> _state.update { it.copy(content = ResolvedContent.Loaded(items)) }
                // El fallo de un refresco no borra la lista que ya se veía.
                hadList && !showLoading -> Unit
                result.exceptionOrNull() is OfflineException -> _state.update { it.copy(content = ResolvedContent.Offline) }
                else -> _state.update { it.copy(content = ResolvedContent.Error) }
            }
        }
    }

    fun onRetry() = load(showLoading = true)

    /** Al volver de 36: el estado de alguna cambió. */
    fun onResume() {
        if (_state.value.content is ResolvedContent.Loaded) load(showLoading = false)
    }

    companion object {
        val factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as ExploraApplication).container
                ResolvedPublicationsViewModel(container.moderationRepository, container.connectivity)
            }
        }
    }
}
