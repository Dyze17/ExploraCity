package co.edu.uniquindio.exploracity.viewmodel

import androidx.lifecycle.SavedStateHandle
import co.edu.uniquindio.exploracity.data.connectivity.FakeConnectivity
import co.edu.uniquindio.exploracity.data.location.SIMULATED_LOCATION
import co.edu.uniquindio.exploracity.data.location.SimulatedLocationProvider
import co.edu.uniquindio.exploracity.data.repository.FakeOfflineRepository
import co.edu.uniquindio.exploracity.data.repository.FakePoiRepository
import co.edu.uniquindio.exploracity.data.repository.FeedQuery
import co.edu.uniquindio.exploracity.data.repository.MAP_MARKER_LIMIT
import co.edu.uniquindio.exploracity.data.repository.MapArea
import co.edu.uniquindio.exploracity.data.repository.PoiRepository
import co.edu.uniquindio.exploracity.data.repository.SavedPlaces
import co.edu.uniquindio.exploracity.data.repository.samplePois
import co.edu.uniquindio.exploracity.domain.model.Category
import co.edu.uniquindio.exploracity.domain.model.FeedFilters
import co.edu.uniquindio.exploracity.domain.model.GeoBounds
import co.edu.uniquindio.exploracity.domain.model.GeoPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.Instant
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val connectivity = FakeConnectivity()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** Toda Bogotá: contiene los 26 lugares de prueba. */
    private val city = GeoBounds(GeoPoint(4.55, -74.15), GeoPoint(4.75, -73.98))

    /** Centro histórico: solo los lugares de La Candelaria. */
    private val candelaria = GeoBounds(GeoPoint(4.590, -74.080), GeoPoint(4.605, -74.065))

    private fun viewModel(repository: PoiRepository = FakePoiRepository(), savedState: SavedStateHandle = SavedStateHandle()) =
        MapViewModel(repository, SimulatedLocationProvider(), connectivity, areaCenter = GeoPoint(4.6097, -74.0817), savedStateHandle = savedState)

    @Test
    fun `espera a conocer el área antes de buscar`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        assertTrue(vm.state.value.loading)
        assertEquals(null, vm.state.value.totalInArea)
    }

    @Test
    fun `trae los lugares del área visible y selecciona el más cercano`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.onAreaChange(candelaria)
        assertTrue(vm.state.value.loading)
        advanceUntilIdle()

        val state = vm.state.value
        val expected = samplePois.filter { it.location in candelaria }
        assertEquals(expected, state.pois)
        assertEquals(expected.size, state.totalInArea)
        assertEquals(expected.first(), state.selected)
        assertFalse(state.loading)
    }

    @Test
    fun `como máximo 200 marcadores, con el total real del área`() = runTest(dispatcher) {
        val many = (0 until 250).map { i -> samplePois[i % samplePois.size].copy(id = "poi-$i") }
        val vm = viewModel(FakePoiRepository(pois = many))

        vm.onAreaChange(city)
        advanceUntilIdle()

        assertEquals(MAP_MARKER_LIMIT, vm.state.value.pois.size)
        assertEquals(250, vm.state.value.totalInArea)
    }

    @Test
    fun `al mover el mapa conserva la selección si sigue en el área`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onAreaChange(city)
        advanceUntilIdle()
        val museo = samplePois.first { it.id == "museo-del-oro" }
        vm.onSelect(museo.id)

        vm.onAreaChange(candelaria)
        advanceUntilIdle()
        assertEquals(museo, vm.state.value.selected)

        vm.onAreaChange(GeoBounds(GeoPoint(4.64, -74.07), GeoPoint(4.66, -74.05)))
        advanceUntilIdle()
        assertEquals(vm.state.value.pois.first(), vm.state.value.selected)
    }

    @Test
    fun `los filtros compartidos con la lista vuelven a consultar el área`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onAreaChange(city)
        advanceUntilIdle()

        vm.onCriteriaChange(FeedQuery(FeedFilters(categories = setOf(Category.NATURE))))
        advanceUntilIdle()

        assertTrue(vm.state.value.pois.isNotEmpty())
        assertTrue(vm.state.value.pois.all { it.category == Category.NATURE })
        assertEquals(Category.NATURE, vm.state.value.selected?.category)
    }

    @Test
    fun `un fallo deja los marcadores anteriores y reintentar lo resuelve`() = runTest(dispatcher) {
        val repository = FailingAfterFirstRepository()
        val vm = viewModel(repository)
        vm.onAreaChange(city)
        advanceUntilIdle()
        val before = vm.state.value.pois

        vm.onAreaChange(candelaria)
        advanceUntilIdle()
        assertTrue(vm.state.value.error)
        assertEquals(before, vm.state.value.pois)

        repository.failing = false
        vm.onRetry()
        advanceUntilIdle()
        assertFalse(vm.state.value.error)
        assertEquals(samplePois.filter { it.location in candelaria }, vm.state.value.pois)
    }

    @Test
    fun `más de 8 segundos buscando pasa a error recuperable`() = runTest(dispatcher) {
        val vm = viewModel(FakePoiRepository(areaLatency = 10.seconds))
        vm.onAreaChange(city)

        advanceTimeBy(7.9.seconds)
        assertTrue(vm.state.value.loading)
        advanceTimeBy(0.2.seconds)
        assertTrue(vm.state.value.error)
        assertFalse(vm.state.value.loading)
    }

    @Test
    fun `mi ubicación devuelve la simulada y muestra su marcador`() = runTest(dispatcher) {
        val vm = viewModel()

        val here = vm.locate()

        assertEquals(SIMULATED_LOCATION, here)
        assertEquals(SIMULATED_LOCATION, vm.state.value.userLocation)
    }

    @Test
    fun `la selección se recupera si el sistema cierra la app`() = runTest(dispatcher) {
        val savedState = SavedStateHandle()
        val before = viewModel(savedState = savedState)
        before.onAreaChange(city)
        advanceUntilIdle()
        before.onSelect("museo-del-oro")

        val after = viewModel(savedState = savedState)
        after.onAreaChange(city)
        advanceUntilIdle()

        assertEquals("museo-del-oro", after.state.value.selected?.id)
    }

    @Test
    fun `abierto desde el detalle se centra una vez en el lugar y lo selecciona`() = runTest(dispatcher) {
        val savedState = SavedStateHandle(mapOf(MapViewModel.FOCUS_POI_ID_KEY to "museo-del-oro"))
        val museo = samplePois.first { it.id == "museo-del-oro" }
        val vm = viewModel(savedState = savedState)
        assertTrue(vm.hasFocus)
        advanceUntilIdle()

        assertEquals(museo.location, vm.state.value.focusTarget)
        vm.onAreaChange(candelaria)
        advanceUntilIdle()
        assertEquals(museo, vm.state.value.selected)

        vm.onFocusShown()
        assertEquals(null, vm.state.value.focusTarget)
        // Al volver al mapa (o si Android lo recrea) ya no se vuelve a centrar: manda la cámara guardada.
        val again = viewModel(savedState = savedState)
        advanceUntilIdle()
        assertEquals(null, again.state.value.focusTarget)
    }

    @Test
    fun `sin conexión no busca zonas y cuenta lo guardado`() = runTest(dispatcher) {
        connectivity.online = false
        val saved = SavedPlaces(samplePois.take(3), Instant.parse("2026-09-24T13:00:00Z"), withDetails = emptySet())
        val vm = viewModel(FakeOfflineRepository(connectivity, saved = saved))

        vm.onAreaChange(city)
        advanceUntilIdle()

        val state = vm.state.value
        assertTrue(state.offline)
        assertEquals(3, state.savedCount)
        assertFalse(state.loading)
        assertTrue(state.pois.isEmpty())
    }

    @Test
    fun `al volver la red busca otra vez el área visible`() = runTest(dispatcher) {
        val vm = viewModel(FakeOfflineRepository(connectivity))
        vm.onAreaChange(candelaria)
        advanceUntilIdle()

        connectivity.online = false
        advanceUntilIdle()
        assertTrue(vm.state.value.offline)

        connectivity.online = true
        advanceUntilIdle()
        assertFalse(vm.state.value.offline)
        assertEquals(samplePois.filter { it.location in candelaria }, vm.state.value.pois)
    }

    private class FailingAfterFirstRepository(private val delegate: FakePoiRepository = FakePoiRepository()) : PoiRepository by delegate {
        private var calls = 0
        var failing = true

        override suspend fun mapArea(query: FeedQuery, bounds: GeoBounds, limit: Int): MapArea {
            calls++
            if (calls > 1 && failing) throw IOException("sin red")
            return delegate.mapArea(query, bounds, limit)
        }
    }
}
