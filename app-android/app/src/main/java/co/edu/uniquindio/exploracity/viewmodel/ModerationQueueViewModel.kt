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
import co.edu.uniquindio.exploracity.domain.model.ModerationWork
import co.edu.uniquindio.exploracity.domain.model.ReviewItem
import co.edu.uniquindio.exploracity.domain.model.ReviewQueue
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 32 · «Pendientes» o solo «Posibles duplicados»; las dos conservan el orden por antigüedad. */
enum class QueueFilter { ALL, DUPLICATES }

sealed interface QueueContent {
    data object Loading : QueueContent

    data class Loaded(val queue: ReviewQueue) : QueueContent

    /** Sin conexión y sin nada guardado. */
    data object Offline : QueueContent

    /** Con red, el servidor no respondió y no había nada guardado. */
    data object Error : QueueContent
}

/** Avisos de una sola vez de 32. */
sealed interface QueueMessage {
    /** Volvió la red y se refrescó: «La cola se actualizó: 2 nuevas». */
    data class Updated(val newOnes: Int) : QueueMessage

    /** Se verificó la última (C1): «Verificada. Revisaste toda la cola». */
    data object AllReviewed : QueueMessage
}

data class ModerationQueueUiState(
    val content: QueueContent = QueueContent.Loading,
    val filter: QueueFilter = QueueFilter.ALL,
    /** 37 · «Tu trabajo de hoy»; null mientras no llega o si no se pudo pedir. */
    val work: ModerationWork? = null,
    val message: QueueMessage? = null,
) {
    private val queue: ReviewQueue? get() = (content as? QueueContent.Loaded)?.queue

    val items: List<ReviewItem> get() = queue?.items.orEmpty()

    val duplicates: Int get() = items.count { it.duplicate != null }

    val visible: List<ReviewItem> get() = if (filter == QueueFilter.DUPLICATES) items.filter { it.duplicate != null } else items

    /** 32.c · Lo guardado sin conexión: se lee, pero no se decide. */
    val readOnly: Boolean get() = queue?.savedAt != null

    /** 37 · La cola llegó vacía (con red o guardada). */
    val empty: Boolean get() = queue != null && items.isEmpty()
}

/**
 * 32 y 37 · La cola de moderación, de la más antigua a la más reciente. Sin conexión muestra lo guardado; al volver la
 * red se refresca y avisa cuántas llegaron. Vacía, dice lo decidido hoy. Al volver de una revisión se pone al día sin
 * mostrar otra vez la carga.
 */
class ModerationQueueViewModel(
    private val moderation: ModerationRepository,
    private val connectivity: ConnectivityObserver,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _state = MutableStateFlow(
        ModerationQueueUiState(filter = if (savedStateHandle.get<Boolean>(DUPLICATES_KEY) == true) QueueFilter.DUPLICATES else QueueFilter.ALL),
    )
    val state: StateFlow<ModerationQueueUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        load(showLoading = true)
        viewModelScope.launch {
            // Al volver la red: se refresca y se avisa cuántas son nuevas.
            connectivity.isOnline.drop(1).collect { online -> if (online) load(showLoading = false, announce = true) }
        }
    }

    private fun load(showLoading: Boolean, announce: Boolean = false) {
        loadJob?.cancel()
        val before = _state.value.items.map { it.id }.toSet()
        val hadQueue = _state.value.content is QueueContent.Loaded
        if (showLoading || !hadQueue) _state.update { it.copy(content = QueueContent.Loading) }
        loadJob = viewModelScope.launch {
            val result = catchingNonCancellation { moderation.queue() }
            val queue = result.getOrNull()
            if (queue == null) {
                // Si ya había una cola a la vista, se queda: el fallo de un refresco no la borra.
                if (!hadQueue || showLoading) {
                    val content = if (result.exceptionOrNull() is OfflineException) QueueContent.Offline else QueueContent.Error
                    _state.update { it.copy(content = content) }
                }
                return@launch
            }
            val newOnes = queue.items.count { it.id !in before }
            _state.update {
                it.copy(
                    content = QueueContent.Loaded(queue),
                    message = if (announce && hadQueue && queue.savedAt == null) QueueMessage.Updated(newOnes) else it.message,
                )
            }
            if (queue.items.isEmpty() && queue.savedAt == null) loadWork()
        }
    }

    private suspend fun loadWork() {
        val work = catchingNonCancellation { moderation.todayWork() }.getOrNull()
        _state.update { it.copy(work = work) }
    }

    fun onFilterChange(filter: QueueFilter) {
        savedStateHandle[DUPLICATES_KEY] = filter == QueueFilter.DUPLICATES
        _state.update { it.copy(filter = filter) }
    }

    fun onRetry() = load(showLoading = true)

    /** Al volver de una revisión (33): la cola cambió, pero no hace falta la carga a pantalla completa. */
    fun onResume() {
        if (_state.value.content is QueueContent.Loaded) load(showLoading = false)
    }

    /** La revisión verificó la última pendiente (C1) y volvió aquí. */
    fun onAllReviewed() = _state.update { it.copy(message = QueueMessage.AllReviewed) }

    fun onMessageShown() = _state.update { it.copy(message = null) }

    companion object {
        private const val DUPLICATES_KEY = "solo_duplicados"

        val factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as ExploraApplication).container
                ModerationQueueViewModel(
                    moderation = container.moderationRepository,
                    connectivity = container.connectivity,
                    savedStateHandle = createSavedStateHandle(),
                )
            }
        }
    }
}
