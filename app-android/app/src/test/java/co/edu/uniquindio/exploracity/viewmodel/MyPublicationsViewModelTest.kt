package co.edu.uniquindio.exploracity.viewmodel

import androidx.lifecycle.SavedStateHandle
import co.edu.uniquindio.exploracity.data.connectivity.FakeConnectivity
import co.edu.uniquindio.exploracity.data.repository.FakePoiRepository
import co.edu.uniquindio.exploracity.data.repository.FakePublicationRepository
import co.edu.uniquindio.exploracity.data.repository.OnlineOnlyPublicationRepository
import co.edu.uniquindio.exploracity.data.repository.PublicationRepository
import co.edu.uniquindio.exploracity.domain.model.OwnPublication
import co.edu.uniquindio.exploracity.domain.model.PublicationStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class MyPublicationsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val connectivity = FakeConnectivity()
    private val server = FakePublicationRepository(FakePoiRepository())

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        publications: PublicationRepository = OnlineOnlyPublicationRepository(server, connectivity),
        savedState: SavedStateHandle = SavedStateHandle(),
        initialFilter: PublicationStatus? = null,
    ) = MyPublicationsViewModel(publications, connectivity, savedState, initialFilter)

    private val MyPublicationsViewModel.items: List<OwnPublication>
        get() = requireNotNull(state.value.loaded) { "La lista debería estar cargada: ${state.value.content}" }.items

    @Test
    fun `carga todas y cuenta cada estado`() = runTest(dispatcher) {
        val vm = viewModel()
        assertEquals(MyPublicationsContent.Loading, vm.state.value.content)

        advanceUntilIdle()

        assertEquals(7, vm.items.size)
        val counts = requireNotNull(vm.state.value.loaded).counts
        assertEquals(7, counts.total)
        assertEquals(2, counts[PublicationStatus.PENDING])
        assertEquals(vm.items, vm.state.value.visible)
    }

    @Test
    fun `llega filtrada desde las cifras del perfil y el filtro sobrevive si Android cierra la app`() = runTest(dispatcher) {
        val savedState = SavedStateHandle()
        val vm = viewModel(savedState = savedState, initialFilter = PublicationStatus.PENDING)
        advanceUntilIdle()
        assertTrue(vm.state.value.visible.all { it.status == PublicationStatus.PENDING })

        vm.onFilter(null)
        val recreated = viewModel(savedState = savedState, initialFilter = PublicationStatus.PENDING)
        advanceUntilIdle()

        assertNull("«Todas» elegida a mano gana al filtro de la ruta", recreated.state.value.filter)
        assertEquals(7, recreated.state.value.visible.size)
    }

    @Test
    fun `eliminar desde el menú la quita de la lista y avisa una vez`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        val pending = vm.items.first { it.status == PublicationStatus.PENDING }

        vm.onOpenDelete(pending)
        assertEquals(pending, vm.state.value.deleting)
        vm.onConfirmDelete()
        advanceUntilIdle()

        assertNull(vm.state.value.delete)
        assertTrue(vm.items.none { it.id == pending.id })
        assertEquals(PublicationMessage.DELETED, vm.state.value.message)
        vm.onMessageShown()
        assertNull(vm.state.value.message)
    }

    @Test
    fun `sin red eliminar no se encola y el diálogo sigue abierto con el aviso`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        val verified = vm.items.first { it.status == PublicationStatus.VERIFIED }
        connectivity.online = false

        vm.onOpenDelete(verified)
        vm.onConfirmDelete()
        advanceUntilIdle()

        assertEquals(DeleteError.OFFLINE, vm.state.value.delete?.dialog?.error)
        assertTrue(vm.items.any { it.id == verified.id })
    }

    @Test
    fun `lo eliminado en la publicación rechazada se avisa aquí y la lista se pone al día`() = runTest(dispatcher) {
        val savedState = SavedStateHandle()
        val vm = viewModel(savedState = savedState)
        advanceUntilIdle()

        server.delete("mirador-de-la-pena")
        vm.onMessageFromElsewhere(PublicationMessage.DELETED)
        advanceUntilIdle()

        assertEquals(PublicationMessage.DELETED, vm.state.value.message)
        assertTrue(vm.items.none { it.id == "mirador-de-la-pena" })
    }

    @Test
    fun `sin red lo dice y al volver la red carga sola`() = runTest(dispatcher) {
        connectivity.online = false
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(MyPublicationsContent.Offline, vm.state.value.content)

        connectivity.online = true
        advanceUntilIdle()
        assertEquals(7, vm.items.size)
    }

    @Test
    fun `más de 8 segundos cargando pasa a error recuperable, y reintentar lo resuelve`() = runTest(dispatcher) {
        val repository = SlowRepository()
        val vm = viewModel(repository)

        advanceTimeBy(8.1.seconds)
        assertEquals(MyPublicationsContent.Error, vm.state.value.content)

        repository.slow = false
        vm.onRetry()
        advanceUntilIdle()
        assertEquals(7, vm.items.size)
    }

    @Test
    fun `al volver a la pantalla se pone al día sin silueta, y si falla se queda lo que había`() = runTest(dispatcher) {
        val repository = SlowRepository(slow = false)
        val vm = viewModel(repository)
        advanceUntilIdle()

        repository.failing = true
        vm.onResumed()
        assertNotNull(vm.state.value.loaded)
        advanceUntilIdle()
        assertEquals(7, vm.items.size)
    }

    private inner class SlowRepository(var slow: Boolean = true, var failing: Boolean = false) : PublicationRepository by server {
        override suspend fun myPublications(): List<OwnPublication> {
            if (failing) throw IOException("sin respuesta")
            if (slow) delay(10.seconds)
            return server.myPublications()
        }
    }
}
