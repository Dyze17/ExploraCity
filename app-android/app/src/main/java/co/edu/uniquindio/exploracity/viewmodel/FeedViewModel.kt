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
import co.edu.uniquindio.exploracity.data.repository.FeedPage
import co.edu.uniquindio.exploracity.data.repository.FeedQuery
import co.edu.uniquindio.exploracity.data.repository.ModerationRepository
import co.edu.uniquindio.exploracity.data.repository.ModerationSummary
import co.edu.uniquindio.exploracity.data.repository.PoiRepository
import co.edu.uniquindio.exploracity.data.repository.SavedPlaces
import co.edu.uniquindio.exploracity.domain.model.Category
import co.edu.uniquindio.exploracity.domain.model.FeedFilters
import co.edu.uniquindio.exploracity.domain.model.LocationScope
import co.edu.uniquindio.exploracity.domain.model.Poi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Contenido del feed: cada estado del diseño (7, 10.a, 10.b, 11, 12.b). */
sealed interface FeedContent {
    /** 11: skeletons. */
    data object Loading : FeedContent

    /** 7: lista con carga al desplazar. */
    data class Loaded(
        val items: List<Poi>,
        val total: Int,
        val canLoadMore: Boolean,
        val loadingMore: Boolean = false,
    ) : FeedContent

    /** 10.a: hay criterios y nada coincide. */
    data class NoResults(val query: FeedQuery) : FeedContent

    /** 10.b: la zona no tiene publicaciones. */
    data object EmptyArea : FeedContent

    /** 12.b: fallo o más de 8 s cargando. Con [hasSaved] se ofrece «Ver mis lugares guardados». */
    data class Error(val hasSaved: Boolean = false) : FeedContent

    /** 12.a: lo guardado para ver sin conexión; [places] null si no hay nada guardado. */
    data class Saved(val places: SavedPlaces?, val reason: SavedReason) : FeedContent
}

enum class SavedReason {
    /** El teléfono no tiene internet. */
    OFFLINE,

    /** Hay red pero el servidor no respondió, y la persona pidió ver lo guardado (12.b). */
    SERVER_ERROR,
}

/**
 * 9 · Hoja de filtros abierta. Nada se aplica hasta «Ver N lugares»: el feed sigue con los filtros de antes.
 * [count] es el último conteo del borrador; null mientras no llega el primero o si falló.
 */
data class FilterSheetState(val draft: FeedFilters, val count: Int? = null)

/** Avisos de una sola vez (snackbar). */
enum class FeedMessage {
    /** Se aplicó con «Cercanos» pero no hay permiso de ubicación: se muestra toda la ciudad. */
    LOCATION_DENIED,
}

data class FeedUiState(
    val areaName: String,
    val query: String = "",
    val filters: FeedFilters = FeedFilters.DEFAULT,
    /**
     * Orden de los chips de categoría del feed. Solo cambia al aplicar la hoja (las activas primero, para que
     * se vean): los chips que se tocan en la fila no se mueven bajo el dedo.
     */
    val categoryOrder: List<Category> = Category.entries,
    val content: FeedContent = FeedContent.Loading,
    val moderation: ModerationSummary? = null,
    val filterSheet: FilterSheetState? = null,
    val message: FeedMessage? = null,
)

/**
 * Lo que el feed recupera si el sistema cierra la app en segundo plano. Pasa, por ejemplo, al negar el
 * permiso de ubicación: Android mata el proceso y el resultado llega a uno nuevo, que debe seguir teniendo
 * la hoja abierta con su borrador para aplicarlo.
 */
@Serializable
private data class SavedFeed(
    val query: String,
    val filters: FeedFilters,
    val categoryOrder: List<Category>,
    val draft: FeedFilters?,
)

class FeedViewModel(
    private val poiRepository: PoiRepository,
    private val moderationRepository: ModerationRepository,
    private val connectivity: ConnectivityObserver,
    areaName: String,
    isModerator: Boolean,
    private val savedStateHandle: SavedStateHandle,
    private val firstPageTimeout: Duration = FIRST_PAGE_TIMEOUT,
) : ViewModel() {

    private val _state = MutableStateFlow(restoredState(areaName))
    val state: StateFlow<FeedUiState> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var searchJob: Job? = null
    private var countJob: Job? = null
    private var nextPage = 0

    init {
        viewModelScope.launch {
            state.map { SavedFeed(it.query, it.filters, it.categoryOrder, it.filterSheet?.draft) }
                .distinctUntilChanged()
                .collect { savedStateHandle[SAVED_FEED_KEY] = Json.encodeToString(it) }
        }
        reload()
        viewModelScope.launch {
            // Al perder la red se pasa a lo guardado; al volver se recarga sola (README 8.c).
            connectivity.isOnline.drop(1).collect { online -> if (online) reload() else showSaved(SavedReason.OFFLINE) }
        }
        if (_state.value.filterSheet != null) recount(debounce = false)
        if (isModerator) {
            viewModelScope.launch {
                val summary = runCatchingNonCancellation { moderationRepository.summary() }
                _state.update { it.copy(moderation = summary) }
            }
        }
    }

    fun onQueryChange(text: String) {
        _state.update { it.copy(query = text) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE)
            reload()
        }
    }

    /** Chip de categoría de la fila del feed: se aplica al instante. */
    fun onToggleCategory(category: Category) = applyFilters(_state.value.filters.let { it.copy(categories = it.categories.toggle(category)) })

    /** 10.a «Buscar en toda la ciudad» y el chip «Cercanos» del feed: amplía sin tocar el resto de filtros. */
    fun onSearchWholeCity() = applyFilters(_state.value.filters.copy(scope = LocationScope.CITY))

    /** Chip «Solo verificados» del feed. */
    fun onRemoveVerifiedOnly() = applyFilters(_state.value.filters.copy(verifiedOnly = false))

    /** 10.a «Quitar todos los filtros»: también borra la búsqueda. */
    fun onClearFilters() {
        searchJob?.cancel()
        _state.update { it.copy(query = "", filters = FeedFilters.DEFAULT) }
        reload()
    }

    fun onOpenFilters() {
        val state = _state.value
        // Si el borrador es lo ya aplicado, el feed ya sabe el total: el botón lo muestra sin esperar.
        val known = when (val content = state.content) {
            is FeedContent.Loaded -> content.total
            is FeedContent.NoResults -> 0
            else -> null
        }
        _state.update { it.copy(filterSheet = FilterSheetState(draft = state.filters, count = known)) }
        if (known == null) recount(debounce = false)
    }

    fun onDraftChange(draft: FeedFilters) {
        val sheet = _state.value.filterSheet ?: return
        if (sheet.draft == draft) return
        _state.update { it.copy(filterSheet = sheet.copy(draft = draft)) }
        recount(debounce = true)
    }

    /** «Limpiar»: vuelve al valor inicial sin cerrar la hoja. */
    fun onClearDraft() = onDraftChange(FeedFilters.DEFAULT)

    /** «Ver N lugares». La pantalla pide antes el permiso de ubicación si el borrador usa «Cercanos». */
    fun onApplyFilters() {
        val sheet = _state.value.filterSheet ?: return
        closeSheet()
        _state.update { it.copy(categoryOrder = activeFirst(sheet.draft.categories)) }
        applyFilters(sheet.draft)
    }

    /** Se negó el permiso al aplicar: se aplica el resto con toda la ciudad y se explica por qué. */
    fun onLocationDenied() {
        val sheet = _state.value.filterSheet ?: return
        _state.update { it.copy(filterSheet = sheet.copy(draft = sheet.draft.copy(scope = LocationScope.CITY)), message = FeedMessage.LOCATION_DENIED) }
        onApplyFilters()
    }

    /** Se concedió el permiso desde el aviso: se activa «Cercanos», que era lo que la persona pidió. */
    fun onLocationGranted() = applyFilters(_state.value.filters.copy(scope = LocationScope.NEARBY))

    /** Cerrar (x), deslizar, atrás o tocar fuera: se descarta el borrador. */
    fun onDismissFilters() = closeSheet()

    fun onMessageShown() = _state.update { it.copy(message = null) }

    fun onRetry() = reload()

    /** 12.b «Ver mis lugares guardados». */
    fun onShowSaved() = showSaved(SavedReason.SERVER_ERROR)

    fun onLoadMore() {
        val content = _state.value.content as? FeedContent.Loaded ?: return
        if (!content.canLoadMore || content.loadingMore) return
        _state.update { it.copy(content = content.copy(loadingMore = true)) }
        val query = currentQuery()
        loadJob = viewModelScope.launch {
            val page = runCatchingNonCancellation { poiRepository.feedPage(query, nextPage) }
            _state.update { state ->
                val current = state.content as? FeedContent.Loaded ?: return@update state
                if (page == null) {
                    // Sin romper la lista: al volver a desplazarse se reintenta.
                    state.copy(content = current.copy(loadingMore = false))
                } else {
                    nextPage++
                    state.copy(content = current.copy(items = current.items + page.items, total = page.total, canLoadMore = page.hasMore, loadingMore = false))
                }
            }
        }
    }

    private fun restoredState(areaName: String): FeedUiState {
        val saved = savedStateHandle.get<String>(SAVED_FEED_KEY)
            ?.let { runCatching { Json.decodeFromString<SavedFeed>(it) }.getOrNull() }
            ?: return FeedUiState(areaName = areaName)
        return FeedUiState(
            areaName = areaName,
            query = saved.query,
            filters = saved.filters,
            categoryOrder = saved.categoryOrder,
            filterSheet = saved.draft?.let { FilterSheetState(draft = it) },
        )
    }

    private fun applyFilters(filters: FeedFilters) {
        if (filters == _state.value.filters) return
        _state.update { it.copy(filters = filters) }
        reload()
    }

    private fun closeSheet() {
        countJob?.cancel()
        _state.update { it.copy(filterSheet = null) }
    }

    /** Recalcula el conteo del borrador con la búsqueda actual, para que coincida con lo que se verá al aplicar. */
    private fun recount(debounce: Boolean) {
        countJob?.cancel()
        val state = _state.value
        val sheet = state.filterSheet ?: return
        val query = FeedQuery(sheet.draft, state.query.trim())
        countJob = viewModelScope.launch {
            if (debounce) delay(COUNT_DEBOUNCE)
            val count = runCatchingNonCancellation { poiRepository.count(query) }
            _state.update { current -> current.copy(filterSheet = current.filterSheet?.copy(count = count)) }
        }
    }

    private fun reload() {
        nextPage = 0
        if (!connectivity.isOnline.value) {
            showSaved(SavedReason.OFFLINE)
            return
        }
        loadJob?.cancel()
        _state.update { it.copy(content = FeedContent.Loading) }
        val query = currentQuery()
        loadJob = viewModelScope.launch {
            val result = catchingNonCancellation { withTimeoutOrNull(firstPageTimeout) { poiRepository.feedPage(query, 0) } }
            val page = result.getOrNull()
            val content = when {
                // La red se fue justo mientras cargaba.
                result.exceptionOrNull() is OfflineException -> FeedContent.Saved(savedPlaces(), SavedReason.OFFLINE)
                page == null -> FeedContent.Error(hasSaved = savedPlaces() != null)
                else -> {
                    nextPage = 1
                    page.toContent(query)
                }
            }
            _state.update { it.copy(content = content) }
        }
    }

    /** Lo guardado se lee del teléfono: es inmediato, así que no pasa por la silueta de carga. */
    private fun showSaved(reason: SavedReason) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val saved = savedPlaces()
            _state.update { it.copy(content = FeedContent.Saved(saved, reason)) }
        }
    }

    private suspend fun savedPlaces(): SavedPlaces? = runCatchingNonCancellation { poiRepository.savedPlaces() }

    private fun currentQuery() = _state.value.let { FeedQuery(filters = it.filters, text = it.query.trim()) }

    private fun FeedPage.toContent(query: FeedQuery): FeedContent = when {
        total == 0 && query.hasCriteria -> FeedContent.NoResults(query)
        total == 0 -> FeedContent.EmptyArea
        else -> FeedContent.Loaded(items = items, total = total, canLoadMore = hasMore)
    }

    companion object {
        /** README: skeleton > 8 s → error recuperable. */
        val FIRST_PAGE_TIMEOUT = 8.seconds
        val SEARCH_DEBOUNCE = 300.milliseconds

        /** Pausa antes de recontar: tocar varios chips seguidos pide un solo conteo. */
        val COUNT_DEBOUNCE = 150.milliseconds

        private const val SAVED_FEED_KEY = "feed"

        fun factory(isModerator: Boolean): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as ExploraApplication).container
                FeedViewModel(
                    container.poiRepository,
                    container.moderationRepository,
                    container.connectivity,
                    container.areaName,
                    isModerator,
                    savedStateHandle = createSavedStateHandle(),
                )
            }
        }
    }
}

private fun <T> Set<T>.toggle(item: T): Set<T> = if (item in this) this - item else this + item

/** Categorías activas primero, cada grupo en el orden habitual (sortedBy es estable). */
private fun activeFirst(active: Set<Category>): List<Category> = Category.entries.sortedBy { it !in active }
