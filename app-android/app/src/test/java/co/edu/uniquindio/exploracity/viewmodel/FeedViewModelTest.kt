package co.edu.uniquindio.exploracity.viewmodel

import androidx.lifecycle.SavedStateHandle
import co.edu.uniquindio.exploracity.data.connectivity.FakeConnectivity
import co.edu.uniquindio.exploracity.data.repository.FakeModerationRepository
import co.edu.uniquindio.exploracity.data.repository.FakeOfflineRepository
import co.edu.uniquindio.exploracity.data.repository.FakePoiRepository
import co.edu.uniquindio.exploracity.data.repository.FeedPage
import co.edu.uniquindio.exploracity.data.repository.FeedQuery
import co.edu.uniquindio.exploracity.data.repository.ModerationSummary
import co.edu.uniquindio.exploracity.data.repository.PoiRepository
import co.edu.uniquindio.exploracity.data.repository.SavedPlaces
import co.edu.uniquindio.exploracity.data.repository.samplePois
import co.edu.uniquindio.exploracity.domain.model.Category
import co.edu.uniquindio.exploracity.domain.model.FeedFilters
import co.edu.uniquindio.exploracity.domain.model.LocationScope
import co.edu.uniquindio.exploracity.domain.model.PublicationStatus
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
class FeedViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val connectivity = FakeConnectivity()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(repository: PoiRepository = FakePoiRepository(), moderator: Boolean = false) =
        FeedViewModel(repository, FakeModerationRepository(), connectivity, areaName = "Bogotá", isModerator = moderator, savedStateHandle = SavedStateHandle())

    private val FeedViewModel.loaded get() = state.value.content as FeedContent.Loaded

    @Test
    fun `empieza cargando y trae la primera página de 20`() = runTest(dispatcher) {
        val vm = viewModel()
        assertEquals(FeedContent.Loading, vm.state.value.content)

        advanceUntilIdle()

        assertEquals(20, vm.loaded.items.size)
        assertEquals(samplePois.size, vm.loaded.total)
        assertTrue(vm.loaded.canLoadMore)
    }

    @Test
    fun `al llegar al final carga la página siguiente`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onLoadMore()
        assertTrue(vm.loaded.loadingMore)
        advanceUntilIdle()

        assertEquals(samplePois.size, vm.loaded.items.size)
        assertFalse(vm.loaded.canLoadMore)
        assertFalse(vm.loaded.loadingMore)
    }

    @Test
    fun `los chips filtran por categoría`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onToggleCategory(Category.NATURE)
        advanceUntilIdle()

        assertTrue(vm.loaded.items.isNotEmpty())
        assertTrue(vm.loaded.items.all { it.category == Category.NATURE })
    }

    @Test
    fun `la búsqueda ignora mayúsculas y tildes`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onQueryChange("BOLIVAR")
        advanceUntilIdle()

        assertTrue(vm.loaded.items.isNotEmpty())
        assertTrue(vm.loaded.items.all { "Bolívar" in it.title })
    }

    @Test
    fun `sin coincidencias muestra sin resultados con los criterios aplicados`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onToggleCategory(Category.NATURE)
        vm.onQueryChange("teatro al aire libre")
        advanceUntilIdle()

        assertEquals(
            FeedContent.NoResults(FeedQuery(FeedFilters(categories = setOf(Category.NATURE)), "teatro al aire libre")),
            vm.state.value.content,
        )

        vm.onClearFilters()
        advanceUntilIdle()
        assertEquals(20, vm.loaded.items.size)
    }

    @Test
    fun `zona sin publicaciones`() = runTest(dispatcher) {
        val vm = viewModel(FakePoiRepository(pois = emptyList()))
        advanceUntilIdle()

        assertEquals(FeedContent.EmptyArea, vm.state.value.content)
    }

    @Test
    fun `un fallo muestra error recuperable y reintentar lo resuelve`() = runTest(dispatcher) {
        val repository = FailingOnceRepository()
        val vm = viewModel(repository)
        advanceUntilIdle()
        assertEquals(FeedContent.Error(), vm.state.value.content)

        vm.onRetry()
        advanceUntilIdle()
        assertEquals(20, vm.loaded.items.size)
    }

    @Test
    fun `más de 8 segundos cargando pasa a error recuperable`() = runTest(dispatcher) {
        val vm = viewModel(FakePoiRepository(latency = 10.seconds))

        advanceTimeBy(7.9.seconds)
        assertEquals(FeedContent.Loading, vm.state.value.content)

        advanceTimeBy(0.2.seconds)
        assertEquals(FeedContent.Error(), vm.state.value.content)
    }

    @Test
    fun `el moderador recibe el resumen de la cola`() = runTest(dispatcher) {
        val vm = viewModel(moderator = true)
        advanceUntilIdle()

        assertEquals(ModerationSummary(pending = 7, oldestWaitingDays = 3), vm.state.value.moderation)
    }

    private val saved = SavedPlaces(samplePois.take(3), Instant.parse("2026-09-24T13:00:00Z"), withDetails = setOf("cafe-las-acacias"))

    @Test
    fun `sin conexión muestra lo guardado sin pedir nada al servidor`() = runTest(dispatcher) {
        connectivity.online = false
        val repository = FakeOfflineRepository(connectivity, saved = saved)
        val vm = viewModel(repository)
        advanceUntilIdle()

        assertEquals(FeedContent.Saved(saved, SavedReason.OFFLINE), vm.state.value.content)
        assertEquals(0, repository.feedCalls)
    }

    @Test
    fun `sin conexión y sin nada guardado lo dice`() = runTest(dispatcher) {
        connectivity.online = false
        val vm = viewModel(FakeOfflineRepository(connectivity))
        advanceUntilIdle()

        assertEquals(FeedContent.Saved(null, SavedReason.OFFLINE), vm.state.value.content)
    }

    @Test
    fun `al perder la red pasa a lo guardado y al volver recarga sola`() = runTest(dispatcher) {
        val vm = viewModel(FakeOfflineRepository(connectivity, saved = saved))
        advanceUntilIdle()
        assertTrue(vm.state.value.content is FeedContent.Loaded)

        connectivity.online = false
        advanceUntilIdle()
        assertEquals(FeedContent.Saved(saved, SavedReason.OFFLINE), vm.state.value.content)

        connectivity.online = true
        advanceUntilIdle()
        assertEquals(20, vm.loaded.items.size)
    }

    @Test
    fun `si el servidor no responde se ofrece ver lo guardado`() = runTest(dispatcher) {
        val repository = FakeOfflineRepository(connectivity, saved = saved).apply { serverDown = true }
        val vm = viewModel(repository)
        advanceUntilIdle()
        assertEquals(FeedContent.Error(hasSaved = true), vm.state.value.content)

        vm.onShowSaved()
        advanceUntilIdle()
        assertEquals(FeedContent.Saved(saved, SavedReason.SERVER_ERROR), vm.state.value.content)
    }

    @Test
    fun `sin nada guardado el error no ofrece verlo`() = runTest(dispatcher) {
        val vm = viewModel(FakeOfflineRepository(connectivity).apply { serverDown = true })
        advanceUntilIdle()

        assertEquals(FeedContent.Error(hasSaved = false), vm.state.value.content)
    }

    private class FailingOnceRepository(private val delegate: FakePoiRepository = FakePoiRepository()) : PoiRepository by delegate {
        private var failed = false

        override suspend fun feedPage(query: FeedQuery, page: Int, pageSize: Int): FeedPage {
            if (!failed) {
                failed = true
                throw IOException("sin red")
            }
            return delegate.feedPage(query, page, pageSize)
        }
    }

    // ── 9 · Hoja de filtros ──

    private val FeedViewModel.sheet get() = requireNotNull(state.value.filterSheet) { "La hoja debería estar abierta" }

    private val nearbyPois = samplePois.filter { it.distanceMeters <= FeedFilters.NEARBY_RADIUS_METERS }

    @Test
    fun `los filtros empiezan en toda la ciudad sin categorías ni solo verificados`() = runTest(dispatcher) {
        val vm = viewModel()

        assertEquals(FeedFilters(emptySet(), LocationScope.CITY, verifiedOnly = false), vm.state.value.filters)
        assertTrue(vm.state.value.filters.isDefault)
    }

    @Test
    fun `abrir la hoja copia los filtros aplicados y ya sabe el conteo`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onOpenFilters()

        assertEquals(FilterSheetState(draft = FeedFilters.DEFAULT, count = samplePois.size), vm.state.value.filterSheet)
    }

    @Test
    fun `cambiar el borrador recuenta en vivo sin tocar el feed`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        val feedBefore = vm.state.value.content
        vm.onOpenFilters()

        vm.onDraftChange(FeedFilters(scope = LocationScope.NEARBY))
        advanceUntilIdle()

        assertEquals(nearbyPois.size, vm.sheet.count)
        assertEquals(FeedFilters.DEFAULT, vm.state.value.filters)
        assertEquals(feedBefore, vm.state.value.content)
    }

    @Test
    fun `varios cambios seguidos piden un solo conteo, el del último borrador`() = runTest(dispatcher) {
        val repository = CountingRepository()
        val vm = viewModel(repository)
        advanceUntilIdle()
        vm.onOpenFilters()

        vm.onDraftChange(FeedFilters(categories = setOf(Category.CULTURE)))
        vm.onDraftChange(FeedFilters(categories = setOf(Category.CULTURE, Category.HISTORY)))
        advanceUntilIdle()

        assertEquals(1, repository.counts)
        assertEquals(samplePois.count { it.category == Category.CULTURE || it.category == Category.HISTORY }, vm.sheet.count)
    }

    @Test
    fun `limpiar vuelve al valor inicial sin cerrar la hoja`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onOpenFilters()
        vm.onDraftChange(FeedFilters(setOf(Category.NATURE), LocationScope.NEARBY, verifiedOnly = true))
        advanceUntilIdle()

        vm.onClearDraft()
        advanceUntilIdle()

        assertEquals(FeedFilters.DEFAULT, vm.sheet.draft)
        assertEquals(samplePois.size, vm.sheet.count)
    }

    @Test
    fun `aplicar cierra la hoja, filtra el feed y pone primero las categorías activas`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onOpenFilters()
        val draft = FeedFilters(setOf(Category.HISTORY), LocationScope.NEARBY, verifiedOnly = true)
        vm.onDraftChange(draft)

        vm.onApplyFilters()
        advanceUntilIdle()

        assertEquals(null, vm.state.value.filterSheet)
        assertEquals(draft, vm.state.value.filters)
        assertEquals(Category.HISTORY, vm.state.value.categoryOrder.first())
        val expected = nearbyPois.filter { it.category == Category.HISTORY && it.status == PublicationStatus.VERIFIED }
        assertEquals(expected, vm.loaded.items)
    }

    @Test
    fun `cerrar sin aplicar descarta el borrador`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onOpenFilters()
        vm.onDraftChange(FeedFilters(verifiedOnly = true))

        vm.onDismissFilters()
        advanceUntilIdle()

        assertEquals(null, vm.state.value.filterSheet)
        assertEquals(FeedFilters.DEFAULT, vm.state.value.filters)
        assertEquals(samplePois.size, vm.loaded.total)
    }

    @Test
    fun `los chips de la fila no reordenan las categorías`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onToggleCategory(Category.HISTORY)
        advanceUntilIdle()

        assertEquals(Category.entries, vm.state.value.categoryOrder)
    }

    @Test
    fun `sin permiso de ubicación aplica el resto con toda la ciudad y lo avisa`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onOpenFilters()
        vm.onDraftChange(FeedFilters(setOf(Category.NATURE), LocationScope.NEARBY))

        vm.onLocationDenied()
        advanceUntilIdle()

        assertEquals(FeedFilters(setOf(Category.NATURE), LocationScope.CITY), vm.state.value.filters)
        assertEquals(FeedMessage.LOCATION_DENIED, vm.state.value.message)
        assertEquals(null, vm.state.value.filterSheet)

        vm.onMessageShown()
        vm.onLocationGranted()
        advanceUntilIdle()
        assertEquals(null, vm.state.value.message)
        assertEquals(LocationScope.NEARBY, vm.state.value.filters.scope)
    }

    @Test
    fun `buscar en toda la ciudad quita solo «Cercanos»`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onOpenFilters()
        vm.onDraftChange(FeedFilters(setOf(Category.NATURE), LocationScope.NEARBY, verifiedOnly = true))
        vm.onApplyFilters()
        advanceUntilIdle()

        vm.onSearchWholeCity()
        advanceUntilIdle()

        assertEquals(FeedFilters(setOf(Category.NATURE), LocationScope.CITY, verifiedOnly = true), vm.state.value.filters)
    }

    @Test
    fun `quitar todos los filtros desde 10a borra también la búsqueda`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onQueryChange("teatro al aire libre")
        vm.onOpenFilters()
        vm.onDraftChange(FeedFilters(scope = LocationScope.NEARBY))
        vm.onApplyFilters()
        advanceUntilIdle()
        assertTrue(vm.state.value.content is FeedContent.NoResults)

        vm.onClearFilters()
        advanceUntilIdle()

        assertEquals("", vm.state.value.query)
        assertEquals(FeedFilters.DEFAULT, vm.state.value.filters)
        assertEquals(samplePois.size, vm.loaded.total)
    }

    @Test
    fun `si el sistema cierra la app con la hoja abierta, el borrador sigue ahí para aplicarlo`() = runTest(dispatcher) {
        // Pasa al negar el permiso de ubicación: Android mata el proceso y el resultado llega a uno nuevo.
        val savedState = SavedStateHandle()
        val before = FeedViewModel(FakePoiRepository(), FakeModerationRepository(), FakeConnectivity(), "Bogotá", isModerator = false, savedStateHandle = savedState)
        advanceUntilIdle()
        before.onQueryChange("parque")
        before.onToggleCategory(Category.ENTERTAINMENT)
        before.onOpenFilters()
        val draft = FeedFilters(setOf(Category.NATURE, Category.ENTERTAINMENT), LocationScope.NEARBY)
        before.onDraftChange(draft)
        advanceUntilIdle()

        val after = FeedViewModel(FakePoiRepository(), FakeModerationRepository(), FakeConnectivity(), "Bogotá", isModerator = false, savedStateHandle = savedState)
        advanceUntilIdle()

        assertEquals("parque", after.state.value.query)
        assertEquals(FeedFilters(setOf(Category.ENTERTAINMENT)), after.state.value.filters)
        assertEquals(draft, after.sheet.draft)
        // El conteo se recalcula en el proceso nuevo con el borrador y la búsqueda restaurados.
        val expected = nearbyPois.count { it.category in draft.categories && "parque" in it.title.lowercase() }
        assertEquals(expected, after.sheet.count)

        after.onLocationDenied()
        advanceUntilIdle()
        assertEquals(FeedFilters(draft.categories, LocationScope.CITY), after.state.value.filters)
        assertEquals(FeedMessage.LOCATION_DENIED, after.state.value.message)
    }

    @Test
    fun `si el conteo falla el botón queda sin cifra`() = runTest(dispatcher) {
        val vm = viewModel(CountFailingRepository())
        advanceUntilIdle()
        vm.onOpenFilters()

        vm.onDraftChange(FeedFilters(verifiedOnly = true))
        advanceUntilIdle()

        assertEquals(null, vm.sheet.count)
    }

    private class CountingRepository(private val delegate: FakePoiRepository = FakePoiRepository()) : PoiRepository by delegate {
        var counts = 0

        override suspend fun count(query: FeedQuery): Int {
            counts++
            return delegate.count(query)
        }
    }

    private class CountFailingRepository : PoiRepository by FakePoiRepository() {
        override suspend fun count(query: FeedQuery): Int = throw IOException("sin red")
    }
}
