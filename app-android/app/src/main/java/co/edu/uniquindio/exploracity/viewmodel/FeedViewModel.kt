package co.edu.uniquindio.exploracity.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import co.edu.uniquindio.exploracity.ExploraApplication
import co.edu.uniquindio.exploracity.data.repository.FeedPage
import co.edu.uniquindio.exploracity.data.repository.FeedQuery
import co.edu.uniquindio.exploracity.data.repository.ModerationRepository
import co.edu.uniquindio.exploracity.data.repository.ModerationSummary
import co.edu.uniquindio.exploracity.data.repository.PoiRepository
import co.edu.uniquindio.exploracity.domain.model.Category
import co.edu.uniquindio.exploracity.domain.model.Poi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
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

    /** 12.b: fallo o más de 8 s cargando. */
    data object Error : FeedContent
}

data class FeedUiState(
    val areaName: String,
    val query: String = "",
    val selectedCategories: Set<Category> = emptySet(),
    val content: FeedContent = FeedContent.Loading,
    val moderation: ModerationSummary? = null,
)

class FeedViewModel(
    private val poiRepository: PoiRepository,
    private val moderationRepository: ModerationRepository,
    areaName: String,
    isModerator: Boolean,
    private val firstPageTimeout: Duration = FIRST_PAGE_TIMEOUT,
) : ViewModel() {

    private val _state = MutableStateFlow(FeedUiState(areaName = areaName))
    val state: StateFlow<FeedUiState> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var searchJob: Job? = null
    private var nextPage = 0

    init {
        reload()
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

    fun onToggleCategory(category: Category) {
        _state.update {
            val categories = if (category in it.selectedCategories) it.selectedCategories - category else it.selectedCategories + category
            it.copy(selectedCategories = categories)
        }
        reload()
    }

    fun onClearFilters() {
        searchJob?.cancel()
        _state.update { it.copy(query = "", selectedCategories = emptySet()) }
        reload()
    }

    fun onRetry() = reload()

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

    private fun reload() {
        loadJob?.cancel()
        nextPage = 0
        _state.update { it.copy(content = FeedContent.Loading) }
        val query = currentQuery()
        loadJob = viewModelScope.launch {
            val page = runCatchingNonCancellation { withTimeoutOrNull(firstPageTimeout) { poiRepository.feedPage(query, 0) } }
            if (page != null) nextPage = 1
            _state.update { it.copy(content = page.toContent(query)) }
        }
    }

    private fun currentQuery() = _state.value.let { FeedQuery(categories = it.selectedCategories, text = it.query.trim()) }

    private fun FeedPage?.toContent(query: FeedQuery): FeedContent = when {
        this == null -> FeedContent.Error
        total == 0 && query.hasCriteria -> FeedContent.NoResults(query)
        total == 0 -> FeedContent.EmptyArea
        else -> FeedContent.Loaded(items = items, total = total, canLoadMore = hasMore)
    }

    companion object {
        /** README: skeleton > 8 s → error recuperable. */
        val FIRST_PAGE_TIMEOUT = 8.seconds
        val SEARCH_DEBOUNCE = 300.milliseconds

        fun factory(isModerator: Boolean): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as ExploraApplication).container
                FeedViewModel(container.poiRepository, container.moderationRepository, container.areaName, isModerator)
            }
        }
    }
}

/** Como runCatching, pero deja pasar la cancelación de la corrutina. */
private suspend fun <T> runCatchingNonCancellation(block: suspend () -> T): T? = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    null
}
