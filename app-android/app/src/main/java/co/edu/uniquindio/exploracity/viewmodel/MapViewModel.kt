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
import co.edu.uniquindio.exploracity.data.location.LocationProvider
import co.edu.uniquindio.exploracity.data.repository.FeedQuery
import co.edu.uniquindio.exploracity.data.repository.PoiRepository
import co.edu.uniquindio.exploracity.domain.model.GeoBounds
import co.edu.uniquindio.exploracity.domain.model.GeoPoint
import co.edu.uniquindio.exploracity.domain.model.Poi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * 8 · Feed en modo mapa. [pois] son los marcadores del área visible (los más cercanos, como máximo 200) y
 * [totalInArea], todos los del área. Mientras se consulta un área nueva se conservan los marcadores anteriores:
 * el mapa sigue navegable (8.b).
 */
data class MapUiState(
    val areaCenter: GeoPoint,
    val pois: List<Poi> = emptyList(),
    val totalInArea: Int? = null,
    val loading: Boolean = true,
    val error: Boolean = false,
    val selectedId: String? = null,
    /** Solo se conoce tras pedirla con el permiso concedido («Mi ubicación»). */
    val userLocation: GeoPoint? = null,
    /** Abierto desde el detalle (13): la cámara debe centrarse aquí una vez. */
    val focusTarget: GeoPoint? = null,
) {
    val selected: Poi? get() = pois.firstOrNull { it.id == selectedId }
}

class MapViewModel(
    private val poiRepository: PoiRepository,
    private val locationProvider: LocationProvider,
    areaCenter: GeoPoint,
    private val savedStateHandle: SavedStateHandle,
    private val timeout: Duration = AREA_TIMEOUT,
) : ViewModel() {

    private val _state = MutableStateFlow(MapUiState(areaCenter = areaCenter, selectedId = savedStateHandle[SELECTED_KEY]))
    val state: StateFlow<MapUiState> = _state.asStateFlow()

    private var criteria = FeedQuery()
    private var area: GeoBounds? = null
    private var loadJob: Job? = null

    /** Argumento de la ruta FeedMap(focusPoiId): el lugar que se quiere ver en el mapa. */
    private val focusPoiId: String? = savedStateHandle[FOCUS_POI_ID_KEY]

    /** Si el mapa se abrió enfocado en un lugar; entonces no se centra en la persona al abrir. */
    val hasFocus: Boolean get() = focusPoiId != null

    init {
        // Una sola vez: al volver al mapa (o si Android lo recrea) manda la cámara guardada.
        if (focusPoiId != null && savedStateHandle.get<Boolean>(FOCUS_SHOWN_KEY) != true) {
            viewModelScope.launch {
                val poi = runCatchingNonCancellation { poiRepository.poiDetails(focusPoiId) }?.poi ?: return@launch
                select(poi.id)
                _state.update { it.copy(focusTarget = poi.location) }
            }
        }
    }

    /** La cámara ya se centró en el lugar del detalle. */
    fun onFocusShown() {
        _state.update { it.copy(focusTarget = null) }
        savedStateHandle[FOCUS_SHOWN_KEY] = true
    }

    /** La cámara se detuvo: consulta el área visible. */
    fun onAreaChange(bounds: GeoBounds) {
        if (bounds == area) return
        area = bounds
        reload()
    }

    /** Búsqueda o filtros compartidos con la lista cambiaron. */
    fun onCriteriaChange(query: FeedQuery) {
        if (query == criteria) return
        criteria = query
        if (area != null) reload()
    }

    fun onSelect(poiId: String) = select(poiId)

    fun onRetry() = reload()

    /** «Mi ubicación» con el permiso concedido: devuelve dónde centrar la cámara y muestra el marcador propio. */
    suspend fun locate(): GeoPoint {
        val location = locationProvider.currentLocation()
        _state.update { it.copy(userLocation = location) }
        return location
    }

    private fun reload() {
        val bounds = area ?: return
        val query = criteria
        loadJob?.cancel()
        _state.update { it.copy(loading = true, error = false) }
        loadJob = viewModelScope.launch {
            val result = try {
                withTimeoutOrNull(timeout) { poiRepository.mapArea(query, bounds) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            if (result == null) {
                _state.update { it.copy(loading = false, error = true) }
                return@launch
            }
            _state.update { state ->
                // Se conserva la selección si sigue en el área; si no, la tarjeta muestra el más cercano.
                val selected = state.selectedId?.takeIf { id -> result.items.any { it.id == id } } ?: result.items.firstOrNull()?.id
                state.copy(pois = result.items, totalInArea = result.total, loading = false, error = false, selectedId = selected)
            }
            savedStateHandle[SELECTED_KEY] = _state.value.selectedId
        }
    }

    private fun select(poiId: String) {
        _state.update { it.copy(selectedId = poiId) }
        savedStateHandle[SELECTED_KEY] = poiId
    }

    companion object {
        /** Como en el feed: más de 8 s buscando → error recuperable. */
        val AREA_TIMEOUT = 8.seconds

        private const val SELECTED_KEY = "selected"
        private const val FOCUS_SHOWN_KEY = "focusShown"
        const val FOCUS_POI_ID_KEY = "focusPoiId"

        val factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as ExploraApplication).container
                MapViewModel(container.poiRepository, container.locationProvider, container.areaCenter, createSavedStateHandle())
            }
        }
    }
}
