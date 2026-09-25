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
import co.edu.uniquindio.exploracity.data.repository.NotificationList
import co.edu.uniquindio.exploracity.data.repository.NotificationRepository
import co.edu.uniquindio.exploracity.domain.model.Notification
import co.edu.uniquindio.exploracity.domain.model.markedRead
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** 25 · Estados de la lista de avisos (25.a, 25.b, 25.c y sin conexión). */
sealed interface NotificationsContent {
    /** 25.b: siluetas. */
    data object Loading : NotificationsContent

    /** 25.a (o 25.c si está vacía). Con [savedAt] es lo guardado: sin red o sin respuesta del servidor. */
    data class Loaded(val items: List<Notification>, val savedAt: Instant? = null) : NotificationsContent {
        val unread: List<Notification> get() = items.filter { !it.read }
        val earlier: List<Notification> get() = items.filter { it.read }
    }

    data object Error : NotificationsContent

    /** Sin red y sin nada guardado todavía. */
    data object Offline : NotificationsContent
}

data class NotificationsUiState(
    val content: NotificationsContent = NotificationsContent.Loading,
    val offline: Boolean = false,
    /** «Marcamos todo como leído» (snackbar, una vez). */
    val allReadShown: Boolean = false,
)

class NotificationsViewModel(
    private val notifications: NotificationRepository,
    private val connectivity: ConnectivityObserver,
    private val timeout: Duration = LOAD_TIMEOUT,
) : ViewModel() {

    private val _state = MutableStateFlow(NotificationsUiState(offline = !connectivity.isOnline.value))
    val state: StateFlow<NotificationsUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        load()
        viewModelScope.launch {
            // Al volver la red se pone al día sola; lo guardado se reemplaza sin la silueta.
            connectivity.isOnline.drop(1).collect { online ->
                _state.update { it.copy(offline = !online) }
                if (online) refresh()
            }
        }
    }

    fun onRetry() = load()

    /** Al volver a la pestaña (de un destino, por ejemplo) se pone al día sin la silueta. */
    fun onResumed() = refresh()

    /** Tocar un aviso (o su enlace): queda leído al instante y la pantalla abre su destino. */
    fun onOpen(notification: Notification) {
        if (notification.read) return
        updateItems { items -> items.map { if (it.id == notification.id) it.markedRead() else it } }
        viewModelScope.launch { runCatchingNonCancellation { notifications.markRead(notification.id) } }
    }

    /** «Marcar leídas». */
    fun onMarkAllRead() {
        val loaded = _state.value.content as? NotificationsContent.Loaded ?: return
        if (loaded.unread.isEmpty()) return
        updateItems { items -> items.map { it.markedRead() } }
        _state.update { it.copy(allReadShown = true) }
        viewModelScope.launch { runCatchingNonCancellation { notifications.markAllRead() } }
    }

    fun onAllReadShown() = _state.update { it.copy(allReadShown = false) }

    private fun updateItems(change: (List<Notification>) -> List<Notification>) = _state.update { state ->
        val loaded = state.content as? NotificationsContent.Loaded ?: return@update state
        state.copy(content = loaded.copy(items = change(loaded.items)))
    }

    private fun load() {
        loadJob?.cancel()
        _state.update { it.copy(content = NotificationsContent.Loading) }
        loadJob = viewModelScope.launch {
            val content = fetch() ?: NotificationsContent.Error
            _state.update { it.copy(content = content) }
        }
    }

    /** Sin silueta: si falla, se queda lo que había (salvo que no hubiera nada que mostrar). */
    private fun refresh() {
        if (loadJob?.isActive == true) return
        if (_state.value.content !is NotificationsContent.Loaded) return load()
        loadJob = viewModelScope.launch {
            val content = fetch() as? NotificationsContent.Loaded ?: return@launch
            _state.update { it.copy(content = content) }
        }
    }

    /** null si falla o tarda más de 8 s. */
    private suspend fun fetch(): NotificationsContent? = try {
        withTimeoutOrNull(timeout) { notifications.notifications() }?.toContent()
    } catch (e: CancellationException) {
        throw e
    } catch (e: OfflineException) {
        NotificationsContent.Offline
    } catch (e: Exception) {
        null
    }

    private fun NotificationList.toContent() = NotificationsContent.Loaded(items, savedAt)

    companion object {
        /** Como en el feed: más de 8 s cargando → error recuperable. */
        val LOAD_TIMEOUT = 8.seconds

        val factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as ExploraApplication).container
                NotificationsViewModel(container.notificationRepository, container.connectivity)
            }
        }
    }
}
