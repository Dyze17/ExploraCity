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
import co.edu.uniquindio.exploracity.data.repository.ModerationRepository
import co.edu.uniquindio.exploracity.domain.model.DuplicateCandidate
import co.edu.uniquindio.exploracity.domain.model.ReviewItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CompareDuplicatesUiState(
    val content: ReviewContent = ReviewContent.Loading,
    /** «Comparar con: 1 · 2»: el parecido que se ve al lado. */
    val selected: Int = 0,
    val offline: Boolean = false,
) {
    val item: ReviewItem? get() = (content as? ReviewContent.Loaded)?.item

    val candidates: List<DuplicateCandidate> get() = item?.duplicate?.candidates.orEmpty()

    val candidate: DuplicateCandidate? get() = candidates.getOrNull(selected) ?: candidates.firstOrNull()

    /** Verificar o rechazar desde aquí necesita red, como en 33. */
    val canDecide: Boolean get() = candidate != null && !offline
}

/**
 * 33A · La pendiente y uno de sus parecidos lado a lado. Con 2 o 3 parecidos se elige con cuál comparar. Se lee también
 * sin conexión (con lo guardado), pero las decisiones esperan a la red.
 */
class CompareDuplicatesViewModel(
    private val moderation: ModerationRepository,
    private val connectivity: ConnectivityObserver,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val id: String = savedStateHandle[ID_KEY] ?: ""

    private val _state = MutableStateFlow(
        CompareDuplicatesUiState(selected = savedStateHandle[SELECTED_KEY] ?: 0, offline = !connectivity.isOnline.value),
    )
    val state: StateFlow<CompareDuplicatesUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            connectivity.isOnline.collect { online -> _state.update { it.copy(offline = !online) } }
        }
        load()
    }

    private fun load() {
        _state.update { it.copy(content = ReviewContent.Loading) }
        viewModelScope.launch {
            val result = catchingNonCancellation { moderation.item(id) }
            val item = result.getOrNull()
            val content = when {
                item?.duplicate != null -> ReviewContent.Loaded(item)
                // Ya no está, o ya no tiene parecidos con qué comparar.
                result.isSuccess -> ReviewContent.Gone
                result.exceptionOrNull() is OfflineException -> ReviewContent.Offline
                else -> ReviewContent.Error
            }
            _state.update { it.copy(content = content) }
        }
    }

    fun onRetry() = load()

    fun onSelect(index: Int) {
        if (index !in _state.value.candidates.indices) return
        savedStateHandle[SELECTED_KEY] = index
        _state.update { it.copy(selected = index) }
    }

    companion object {
        private const val ID_KEY = "publicationId"
        private const val SELECTED_KEY = "comparar_con"

        val factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as ExploraApplication).container
                CompareDuplicatesViewModel(
                    moderation = container.moderationRepository,
                    connectivity = container.connectivity,
                    savedStateHandle = createSavedStateHandle(),
                )
            }
        }
    }
}
