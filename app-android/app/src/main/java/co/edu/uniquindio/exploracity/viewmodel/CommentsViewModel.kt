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
import co.edu.uniquindio.exploracity.data.repository.CommentsPage
import co.edu.uniquindio.exploracity.data.repository.PoiRepository
import co.edu.uniquindio.exploracity.domain.model.Author
import co.edu.uniquindio.exploracity.domain.model.Comment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Clock
import java.time.Instant
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** 14 · Comentarios: los estados de carga no los dibuja el diseño; siguen a 11 y 12.b, como el detalle. */
sealed interface CommentsContent {
    data object Loading : CommentsContent

    /** [comments] del más reciente al más antiguo; [nextCursor] null si no hay más páginas. */
    data class Loaded(
        val poiTitle: String,
        val comments: List<Comment>,
        val total: Int,
        val nextCursor: String?,
        val loadingMore: Boolean = false,
        val loadMoreFailed: Boolean = false,
    ) : CommentsContent

    /** El lugar se eliminó o dejó de ser público. */
    data object NotFound : CommentsContent

    data object Error : CommentsContent

    /** Sin internet: los comentarios no se guardan para ver sin conexión. */
    data object Offline : CommentsContent
}

enum class SendStatus { SENDING, FAILED, SENT }

/**
 * Comentario escrito en esta pantalla (envío optimista): aparece al instante arriba de la lista con «Enviando…» y
 * conserva su lugar al publicarse, para que el lector anuncie el cambio sobre el mismo elemento. Si falla, el
 * texto sigue ahí para reintentar.
 */
data class OwnComment(
    val localId: String,
    val text: String,
    val createdAt: Instant,
    val status: SendStatus,
    /** Id del servidor una vez publicado. */
    val sentId: String? = null,
)

data class CommentsUiState(
    val currentUser: Author,
    val content: CommentsContent = CommentsContent.Loading,
    /** Del más reciente al más antiguo; se muestran antes que los comentarios cargados. */
    val own: List<OwnComment> = emptyList(),
) {
    val isEmpty: Boolean get() = content is CommentsContent.Loaded && content.comments.isEmpty() && own.isEmpty()
}

/** Comentario sin publicar que sobrevive si Android cierra la app (el texto no se pierde). */
@Serializable
private data class SavedOwnComment(val localId: String, val text: String, val createdAtMillis: Long)

class CommentsViewModel(
    private val poiRepository: PoiRepository,
    private val connectivity: ConnectivityObserver,
    currentUser: Author,
    private val savedStateHandle: SavedStateHandle,
    private val clock: Clock = Clock.systemUTC(),
    private val timeout: Duration = LOAD_TIMEOUT,
) : ViewModel() {

    /** Argumentos de la ruta Comments(poiId, write). */
    val poiId: String = checkNotNull(savedStateHandle[POI_ID_KEY]) { "Falta el id del lugar" }

    /** «Agregar comentario» del detalle: la pantalla abre con el teclado listo. */
    val startWriting: Boolean = savedStateHandle[WRITE_KEY] ?: false

    private val _state = MutableStateFlow(CommentsUiState(currentUser, own = restoredOwn()))
    val state: StateFlow<CommentsUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        load()
        viewModelScope.launch {
            // Al volver la red se cargan solos si no se pudieron traer.
            connectivity.isOnline.drop(1).collect { online ->
                val content = _state.value.content
                if (online && (content is CommentsContent.Offline || content is CommentsContent.Error)) load()
            }
        }
    }

    fun onRetry() = load()

    /** Página siguiente al acercarse al final. Si falla, la lista queda como estaba y se ofrece reintentar. */
    fun onLoadMore() {
        val content = _state.value.content as? CommentsContent.Loaded ?: return
        val cursor = content.nextCursor ?: return
        if (content.loadingMore) return
        _state.update { it.copy(content = content.copy(loadingMore = true, loadMoreFailed = false)) }
        viewModelScope.launch {
            val page = runCatchingNonCancellation { poiRepository.comments(poiId, cursor) }
            _state.update { state ->
                val current = state.content as? CommentsContent.Loaded ?: return@update state
                if (page == null) {
                    state.copy(content = current.copy(loadingMore = false, loadMoreFailed = true))
                } else {
                    val seen = current.comments.mapTo(HashSet()) { it.id }
                    val more = page.items.filterNot { it.id in seen }
                    state.copy(content = current.copy(comments = current.comments + more, total = page.total, nextCursor = page.nextCursor, loadingMore = false))
                }
            }
        }
    }

    /**
     * Enviar: el comentario aparece al instante con «Enviando…». Devuelve false si no hay nada que enviar (en
     * blanco o la lista aún no cargó), y entonces la pantalla no borra el campo.
     */
    fun onSend(text: String): Boolean {
        val clean = text.trim().take(Comment.MAX_LENGTH)
        if (clean.isEmpty() || _state.value.content !is CommentsContent.Loaded) return false
        val own = OwnComment(UUID.randomUUID().toString(), clean, clock.instant(), SendStatus.SENDING)
        _state.update { it.copy(own = listOf(own) + it.own) }
        send(own)
        return true
    }

    /** «Reintentar» de un comentario que no se envió. */
    fun onRetrySend(localId: String) {
        val own = _state.value.own.firstOrNull { it.localId == localId } ?: return
        if (own.status != SendStatus.FAILED) return
        updateOwn(localId) { it.copy(status = SendStatus.SENDING) }
        send(own)
    }

    private fun send(own: OwnComment) {
        saveUnsent()
        viewModelScope.launch {
            val sent = runCatchingNonCancellation { poiRepository.addComment(poiId, own.text) }
            _state.update { state ->
                val content = state.content
                state.copy(
                    own = state.own.map { c ->
                        when {
                            c.localId != own.localId -> c
                            sent == null -> c.copy(status = SendStatus.FAILED)
                            else -> c.copy(status = SendStatus.SENT, createdAt = sent.createdAt, sentId = sent.id)
                        }
                    },
                    content = if (sent != null && content is CommentsContent.Loaded) content.copy(total = content.total + 1) else content,
                )
            }
            saveUnsent()
        }
    }

    private fun updateOwn(localId: String, change: (OwnComment) -> OwnComment) =
        _state.update { state -> state.copy(own = state.own.map { if (it.localId == localId) change(it) else it }) }

    private fun load() {
        loadJob?.cancel()
        _state.update { it.copy(content = CommentsContent.Loading) }
        loadJob = viewModelScope.launch {
            val content = try {
                // Envuelto para distinguir el tiempo agotado (null) de un lugar que ya no existe (Found(null)).
                val found = withTimeoutOrNull(timeout) { Found(poiRepository.comments(poiId)) }
                when {
                    found == null -> CommentsContent.Error
                    found.page == null -> CommentsContent.NotFound
                    else -> with(found.page) { CommentsContent.Loaded(poiTitle, items, total, nextCursor) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: OfflineException) {
                CommentsContent.Offline
            } catch (e: Exception) {
                CommentsContent.Error
            }
            _state.update { state ->
                // Un comentario propio que el servidor ya devuelve no se muestra dos veces.
                val loaded = (content as? CommentsContent.Loaded)?.comments.orEmpty().mapTo(HashSet()) { it.id }
                state.copy(content = content, own = state.own.filterNot { it.sentId in loaded })
            }
        }
    }

    private class Found(val page: CommentsPage?)

    /**
     * Si Android cerró la app con un comentario enviándose, no se sabe si llegó: vuelve como «No se envió» para que
     * la persona decida reintentar.
     */
    private fun restoredOwn(): List<OwnComment> = savedStateHandle.get<String>(UNSENT_KEY)
        ?.let { runCatching { Json.decodeFromString<List<SavedOwnComment>>(it) }.getOrNull() }
        .orEmpty()
        .map { OwnComment(it.localId, it.text, Instant.ofEpochMilli(it.createdAtMillis), SendStatus.FAILED) }

    private fun saveUnsent() {
        val unsent = _state.value.own.filter { it.status != SendStatus.SENT }
        savedStateHandle[UNSENT_KEY] = Json.encodeToString(unsent.map { SavedOwnComment(it.localId, it.text, it.createdAt.toEpochMilli()) })
    }

    companion object {
        /** Como en el feed: más de 8 s cargando → error recuperable. */
        val LOAD_TIMEOUT = 8.seconds

        const val POI_ID_KEY = "poiId"
        const val WRITE_KEY = "write"
        private const val UNSENT_KEY = "unsentComments"

        val factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as ExploraApplication).container
                CommentsViewModel(container.poiRepository, container.connectivity, container.currentUser, createSavedStateHandle())
            }
        }
    }
}
