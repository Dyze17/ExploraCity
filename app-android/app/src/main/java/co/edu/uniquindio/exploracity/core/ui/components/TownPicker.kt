package co.edu.uniquindio.exploracity.core.ui.components

import co.edu.uniquindio.exploracity.core.data.connectivity.OfflineException
import co.edu.uniquindio.exploracity.core.util.catchingNonCancellation
import co.edu.uniquindio.exploracity.domain.model.TownOption
import co.edu.uniquindio.exploracity.domain.repository.TownRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/** v1.3.0 · Lo que encontró la búsqueda de «Elegir ciudad». */
sealed interface TownSearch {
    data object Idle : TownSearch

    data object Searching : TownSearch

    data class Found(val options: List<TownOption>) : TownSearch

    data class NotFound(val query: String) : TownSearch

    data object Offline : TownSearch

    data object Failed : TownSearch
}

/** v1.3.0 · «Elegir ciudad»: lo que se escribe, lo que encontró y las ciudades con lugares ([withPlaces] null mientras cargan). */
data class TownPickerState(
    val query: String = "",
    val search: TownSearch = TownSearch.Idle,
    val withPlaces: List<TownOption>? = null,
    val withPlacesFailed: Boolean = false,
)

/**
 * v1.3.0 · La hoja «Elegir ciudad» del feed (7) y de Editar perfil (28): al abrirla trae las ciudades con lugares, y
 * busca cualquier ciudad mientras se escribe. [state] es null con la hoja cerrada.
 */
class TownPicker(private val scope: CoroutineScope, private val towns: TownRepository) {

    private val _state = MutableStateFlow<TownPickerState?>(null)
    val state: StateFlow<TownPickerState?> = _state.asStateFlow()

    private var searchJob: Job? = null

    fun open() {
        if (_state.value != null) return
        _state.value = TownPickerState()
        scope.launch {
            val result = catchingNonCancellation { towns.withPlaces() }
            _state.update { it?.copy(withPlaces = result.getOrNull().orEmpty(), withPlacesFailed = result.isFailure) }
        }
    }

    fun close() {
        searchJob?.cancel()
        _state.value = null
    }

    fun onQueryChange(query: String) {
        val text = query.trim()
        _state.update { it?.copy(query = query, search = if (text.isEmpty()) TownSearch.Idle else TownSearch.Searching) }
        searchJob?.cancel()
        if (text.isEmpty()) return
        searchJob = scope.launch {
            delay(SEARCH_DEBOUNCE)
            val result = catchingNonCancellation { towns.search(text) }
            val found = result.getOrNull()
            val search = when {
                result.exceptionOrNull() is OfflineException -> TownSearch.Offline
                found == null -> TownSearch.Failed
                found.isEmpty() -> TownSearch.NotFound(text)
                else -> TownSearch.Found(found)
            }
            _state.update { it?.copy(search = search) }
        }
    }

    companion object {
        /** El Geocoder responde por red: se espera a que la persona deje de escribir. */
        val SEARCH_DEBOUNCE = 400.milliseconds
    }
}
