package co.edu.uniquindio.exploracity.viewmodel

import co.edu.uniquindio.exploracity.data.repository.FakeModerationRepository
import co.edu.uniquindio.exploracity.data.repository.FakePoiRepository
import co.edu.uniquindio.exploracity.data.repository.FeedPage
import co.edu.uniquindio.exploracity.data.repository.FeedQuery
import co.edu.uniquindio.exploracity.data.repository.ModerationSummary
import co.edu.uniquindio.exploracity.data.repository.PoiRepository
import co.edu.uniquindio.exploracity.data.repository.samplePois
import co.edu.uniquindio.exploracity.domain.model.Category
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
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class FeedViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(repository: PoiRepository = FakePoiRepository(), moderator: Boolean = false) =
        FeedViewModel(repository, FakeModerationRepository(), areaName = "Bogotá", isModerator = moderator)

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
            FeedContent.NoResults(FeedQuery(setOf(Category.NATURE), "teatro al aire libre")),
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
        assertEquals(FeedContent.Error, vm.state.value.content)

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
        assertEquals(FeedContent.Error, vm.state.value.content)
    }

    @Test
    fun `el moderador recibe el resumen de la cola`() = runTest(dispatcher) {
        val vm = viewModel(moderator = true)
        advanceUntilIdle()

        assertEquals(ModerationSummary(pending = 7, oldestWaitingDays = 3), vm.state.value.moderation)
    }

    private class FailingOnceRepository : PoiRepository {
        private val delegate = FakePoiRepository()
        private var failed = false

        override suspend fun feedPage(query: FeedQuery, page: Int, pageSize: Int): FeedPage {
            if (!failed) {
                failed = true
                throw IOException("sin red")
            }
            return delegate.feedPage(query, page, pageSize)
        }
    }
}
