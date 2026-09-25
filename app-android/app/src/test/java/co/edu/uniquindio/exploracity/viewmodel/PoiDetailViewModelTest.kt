package co.edu.uniquindio.exploracity.viewmodel

import androidx.lifecycle.SavedStateHandle
import co.edu.uniquindio.exploracity.data.connectivity.FakeConnectivity
import co.edu.uniquindio.exploracity.data.repository.FakeOfflineRepository
import co.edu.uniquindio.exploracity.data.repository.FakePoiRepository
import co.edu.uniquindio.exploracity.data.repository.PoiRepository
import co.edu.uniquindio.exploracity.data.repository.sampleDetails
import co.edu.uniquindio.exploracity.data.repository.samplePois
import co.edu.uniquindio.exploracity.domain.model.PoiDetails
import co.edu.uniquindio.exploracity.domain.model.VisitExperience
import co.edu.uniquindio.exploracity.domain.model.VisitResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.Instant
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class PoiDetailViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val connectivity = FakeConnectivity()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        repository: PoiRepository = FakePoiRepository(),
        poiId: String = "cafe-las-acacias",
        savedState: SavedStateHandle = SavedStateHandle(mapOf(PoiDetailViewModel.POI_ID_KEY to poiId)),
    ) = PoiDetailViewModel(repository, connectivity, savedState)

    private val PoiDetailViewModel.details: PoiDetails get() = requireNotNull(state.value.details) { "El detalle debería estar cargado" }

    @Test
    fun `carga el detalle del lugar de la ruta`() = runTest(dispatcher) {
        val vm = viewModel()
        assertEquals(DetailContent.Loading, vm.state.value.content)

        advanceUntilIdle()

        assertEquals("Café Las Acacias", vm.details.poi.title)
        assertEquals("Calle 45 # 19-32, Chapinero", vm.details.address)
        assertEquals(3, vm.details.photos.size)
        assertFalse(vm.details.voted)
    }

    @Test
    fun `un lugar que ya no existe muestra no encontrado`() = runTest(dispatcher) {
        val vm = viewModel(poiId = "no-existe")
        advanceUntilIdle()

        assertEquals(DetailContent.NotFound, vm.state.value.content)
    }

    @Test
    fun `un fallo muestra error recuperable y reintentar lo resuelve`() = runTest(dispatcher) {
        val repository = FailingRepository(failDetails = true)
        val vm = viewModel(repository)
        advanceUntilIdle()
        assertEquals(DetailContent.Error, vm.state.value.content)

        repository.failDetails = false
        vm.onRetry()
        advanceUntilIdle()
        assertEquals("Café Las Acacias", vm.details.poi.title)
    }

    @Test
    fun `más de 8 segundos cargando pasa a error recuperable`() = runTest(dispatcher) {
        val vm = viewModel(FakePoiRepository(latency = 10.seconds))

        advanceTimeBy(8.1.seconds)

        assertEquals(DetailContent.Error, vm.state.value.content)
    }

    @Test
    fun `el voto cambia al instante y queda el total del servidor`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        val before = vm.details.poi.votes

        vm.onToggleVote()
        assertTrue(vm.details.voted)
        assertEquals(before + 1, vm.details.poi.votes)
        assertTrue(vm.state.value.voting)

        advanceUntilIdle()
        assertFalse(vm.state.value.voting)
        assertEquals(before + 1, vm.details.poi.votes)

        vm.onToggleVote()
        advanceUntilIdle()
        assertFalse(vm.details.voted)
        assertEquals(before, vm.details.poi.votes)
    }

    @Test
    fun `si el voto falla se revierte y se avisa`() = runTest(dispatcher) {
        val vm = viewModel(FailingRepository(failVote = true))
        advanceUntilIdle()
        val before = vm.details.poi.votes

        vm.onToggleVote()
        advanceUntilIdle()

        assertFalse(vm.details.voted)
        assertEquals(before, vm.details.poi.votes)
        assertEquals(DetailMessage.VoteFailed, vm.state.value.message)
    }

    @Test
    fun `marcar visitado guarda la experiencia y avisa sin puntos`() = runTest(dispatcher) {
        val repository = FailingRepository()
        val vm = viewModel(repository)
        advanceUntilIdle()

        vm.onOpenVisit()
        vm.onVisitDraftChange(VisitExperience(recommends = true, text = "  Fui un martes a las 8.  ", showName = true))
        vm.onConfirmVisit()
        assertTrue(vm.state.value.visitSheet!!.sending)
        advanceUntilIdle()

        assertNull(vm.state.value.visitSheet)
        assertTrue(vm.details.visited)
        assertEquals(DetailMessage.VisitSaved(points = 0), vm.state.value.message)
        assertEquals(VisitExperience(recommends = true, text = "Fui un martes a las 8.", showName = true), repository.lastExperience)

        vm.onOpenVisit()
        assertNull(vm.state.value.visitSheet)
    }

    @Test
    fun `si marcar visitado falla la hoja sigue con lo escrito`() = runTest(dispatcher) {
        val vm = viewModel(FailingRepository(failVisit = true))
        advanceUntilIdle()
        vm.onOpenVisit()
        val draft = VisitExperience(recommends = false, text = "No abrieron a la hora.")
        vm.onVisitDraftChange(draft)

        vm.onConfirmVisit()
        advanceUntilIdle()

        assertEquals(VisitSheetState(draft = draft, sending = false), vm.state.value.visitSheet)
        assertFalse(vm.details.visited)
        assertEquals(DetailMessage.VisitFailed, vm.state.value.message)
    }

    @Test
    fun `la experiencia no pasa de 300 caracteres`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onOpenVisit()

        vm.onVisitDraftChange(VisitExperience(text = "a".repeat(400)))

        assertEquals(VisitExperience.MAX_LENGTH, vm.state.value.visitSheet!!.draft.text.length)
    }

    @Test
    fun `el borrador de la visita sobrevive si el sistema cierra la app`() = runTest(dispatcher) {
        val savedState = SavedStateHandle(mapOf(PoiDetailViewModel.POI_ID_KEY to "cafe-las-acacias"))
        val before = viewModel(savedState = savedState)
        advanceUntilIdle()
        before.onOpenVisit()
        val draft = VisitExperience(recommends = true, text = "Pan de queso a las 8.")
        before.onVisitDraftChange(draft)

        val after = viewModel(savedState = savedState)

        assertEquals(draft, after.state.value.visitSheet?.draft)
        after.onDismissVisit()
        assertNull(viewModel(savedState = savedState).state.value.visitSheet)
    }

    @Test
    fun `cancelar descarta el borrador`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onOpenVisit()
        vm.onVisitDraftChange(VisitExperience(text = "Algo"))

        vm.onDismissVisit()
        vm.onOpenVisit()

        assertNotNull(vm.state.value.visitSheet)
        assertEquals(VisitExperience(), vm.state.value.visitSheet!!.draft)
    }

    @Test
    fun `al volver al detalle se actualiza el número de comentarios sin mostrar la carga`() = runTest(dispatcher) {
        val repository = FakePoiRepository()
        val vm = viewModel(repository)
        vm.onResumed()
        advanceUntilIdle()
        val before = vm.details.poi.comments

        repository.addComment("cafe-las-acacias", "Muy buen pan de queso.")
        vm.onResumed()
        assertTrue(vm.state.value.content is DetailContent.Loaded)
        advanceUntilIdle()

        assertEquals(before + 1, vm.details.poi.comments)
    }

    private val savedCafe = sampleDetails(samplePois.first()).copy(savedAt = Instant.parse("2026-09-24T13:00:00Z"))

    @Test
    fun `sin conexión y sin guardar lo dice, y al volver la red carga solo`() = runTest(dispatcher) {
        connectivity.online = false
        val vm = viewModel(FakeOfflineRepository(connectivity))
        advanceUntilIdle()
        assertEquals(DetailContent.Offline, vm.state.value.content)
        assertTrue(vm.state.value.offline)

        connectivity.online = true
        advanceUntilIdle()
        assertEquals("Café Las Acacias", vm.details.poi.title)
        assertFalse(vm.state.value.offline)
    }

    @Test
    fun `sin conexión se ve lo guardado y votar o marcar la visita avisan que falta la red`() = runTest(dispatcher) {
        connectivity.online = false
        val vm = viewModel(FakeOfflineRepository(connectivity, savedDetails = mapOf("cafe-las-acacias" to savedCafe)))
        advanceUntilIdle()
        assertEquals(savedCafe, vm.details)

        vm.onToggleVote()
        advanceUntilIdle()
        assertFalse(vm.details.voted)
        assertEquals(savedCafe.poi.votes, vm.details.poi.votes)
        assertEquals(DetailMessage.VoteOffline, vm.state.value.message)
        vm.onMessageShown()

        vm.onOpenVisit()
        vm.onVisitDraftChange(VisitExperience(text = "Muy bueno"))
        vm.onConfirmVisit()
        advanceUntilIdle()
        assertEquals(DetailMessage.VisitOffline, vm.state.value.message)
        assertEquals("Muy bueno", vm.state.value.visitSheet?.draft?.text)
    }

    @Test
    fun `al volver la red lo guardado se pone al día sin la silueta`() = runTest(dispatcher) {
        connectivity.online = false
        val vm = viewModel(FakeOfflineRepository(connectivity, savedDetails = mapOf("cafe-las-acacias" to savedCafe)))
        advanceUntilIdle()

        connectivity.online = true
        runCurrent()
        assertEquals(savedCafe, vm.details)
        advanceUntilIdle()
        assertNull(vm.details.savedAt)
    }

    private class FailingRepository(
        var failDetails: Boolean = false,
        private val failVote: Boolean = false,
        private val failVisit: Boolean = false,
        private val delegate: FakePoiRepository = FakePoiRepository(),
    ) : PoiRepository by delegate {
        var lastExperience: VisitExperience? = null

        override suspend fun poiDetails(id: String): PoiDetails? {
            if (failDetails) throw IOException("sin red")
            return delegate.poiDetails(id)
        }

        override suspend fun setVote(id: String, voted: Boolean): Int {
            if (failVote) throw IOException("sin red")
            return delegate.setVote(id, voted)
        }

        override suspend fun markVisited(id: String, experience: VisitExperience): VisitResult {
            if (failVisit) throw IOException("sin red")
            lastExperience = experience
            return delegate.markVisited(id, experience)
        }
    }
}
