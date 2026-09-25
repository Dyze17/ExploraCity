package co.edu.uniquindio.exploracity.viewmodel

import androidx.lifecycle.SavedStateHandle
import co.edu.uniquindio.exploracity.data.connectivity.FakeConnectivity
import co.edu.uniquindio.exploracity.data.repository.FakePoiRepository
import co.edu.uniquindio.exploracity.data.repository.FakePublicationRepository
import co.edu.uniquindio.exploracity.data.repository.OnlineOnlyPublicationRepository
import co.edu.uniquindio.exploracity.data.repository.PublicationRepository
import co.edu.uniquindio.exploracity.domain.model.OwnPublication
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class RejectedPublicationViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val connectivity = FakeConnectivity()
    private val server = FakePublicationRepository(FakePoiRepository())

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun savedState(id: String = "mirador-de-la-pena") = SavedStateHandle(mapOf(RejectedPublicationViewModel.PUBLICATION_ID_KEY to id))

    private fun viewModel(
        publications: PublicationRepository = OnlineOnlyPublicationRepository(server, connectivity),
        savedState: SavedStateHandle = savedState(),
    ) = RejectedPublicationViewModel(publications, connectivity, savedState)

    private val RejectedPublicationViewModel.publication: OwnPublication
        get() = requireNotNull(state.value.publication) { "La publicación debería estar cargada: ${state.value.content}" }

    @Test
    fun `carga la publicación rechazada`() = runTest(dispatcher) {
        val vm = viewModel()
        assertEquals(RejectedContent.Loading, vm.state.value.content)

        advanceUntilIdle()

        assertEquals("Mirador de La Peña", vm.publication.title)
        assertNotNull(vm.publication.rejection)
    }

    @Test
    fun `una publicación que ya no existe lo dice`() = runTest(dispatcher) {
        val vm = viewModel(savedState = savedState("no-existe"))
        advanceUntilIdle()

        assertEquals(RejectedContent.NotFound, vm.state.value.content)
    }

    @Test
    fun `más de 8 segundos cargando pasa a error recuperable`() = runTest(dispatcher) {
        val vm = viewModel(FakePublicationRepository(FakePoiRepository(), latency = 10.seconds))

        advanceTimeBy(7.9.seconds)
        assertEquals(RejectedContent.Loading, vm.state.value.content)
        advanceTimeBy(0.2.seconds)
        assertEquals(RejectedContent.Error, vm.state.value.content)
    }

    @Test
    fun `un fallo muestra error recuperable y reintentar lo resuelve`() = runTest(dispatcher) {
        val repository = FlakyRepository()
        val vm = viewModel(repository)
        advanceUntilIdle()
        assertEquals(RejectedContent.Error, vm.state.value.content)

        repository.failing = false
        vm.onRetry()
        advanceUntilIdle()
        assertEquals("Mirador de La Peña", vm.publication.title)
    }

    @Test
    fun `sin red lo dice y al volver la red carga sola`() = runTest(dispatcher) {
        connectivity.online = false
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(RejectedContent.Offline, vm.state.value.content)

        connectivity.online = true
        advanceUntilIdle()
        assertEquals("Mirador de La Peña", vm.publication.title)
    }

    @Test
    fun `eliminar confirma, borra y avisa una vez para volver a mis publicaciones`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onOpenDelete()
        assertNotNull(vm.state.value.delete)
        vm.onConfirmDelete()
        assertTrue(vm.state.value.delete?.deleting == true)
        vm.onDismissDelete()
        assertNotNull("Mientras borra no se puede cerrar", vm.state.value.delete)
        advanceUntilIdle()

        assertNull(vm.state.value.delete)
        assertTrue(vm.state.value.deleted)
        assertNull(server.publication("mirador-de-la-pena"))
        vm.onDeletedHandled()
        assertFalse(vm.state.value.deleted)
    }

    @Test
    fun `sin red eliminar no se encola, el diálogo sigue abierto con el aviso`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        connectivity.online = false

        vm.onOpenDelete()
        vm.onConfirmDelete()
        advanceUntilIdle()

        assertEquals(DeleteDialogState(error = DeleteError.OFFLINE), vm.state.value.delete)
        assertFalse(vm.state.value.deleted)
        assertNotNull(server.publication("mirador-de-la-pena"))
    }

    @Test
    fun `si falla al eliminar se puede reintentar desde el mismo diálogo`() = runTest(dispatcher) {
        val repository = FlakyRepository(failing = false, deleteFailing = true)
        val vm = viewModel(repository)
        advanceUntilIdle()

        vm.onOpenDelete()
        vm.onConfirmDelete()
        advanceUntilIdle()
        assertEquals(DeleteError.FAILED, vm.state.value.delete?.error)

        repository.deleteFailing = false
        vm.onConfirmDelete()
        advanceUntilIdle()
        assertTrue(vm.state.value.deleted)
    }

    @Test
    fun `el diálogo abierto sobrevive si Android cierra la app`() = runTest(dispatcher) {
        val savedState = savedState()
        val vm = viewModel(savedState = savedState)
        advanceUntilIdle()
        vm.onOpenDelete()

        val recreated = viewModel(savedState = savedState)
        advanceUntilIdle()

        assertEquals(DeleteDialogState(), recreated.state.value.delete)
    }

    private inner class FlakyRepository(var failing: Boolean = true, var deleteFailing: Boolean = false) : PublicationRepository by server {
        override suspend fun publication(id: String): OwnPublication? {
            if (failing) throw IOException("sin respuesta")
            return server.publication(id)
        }

        override suspend fun delete(id: String) {
            if (deleteFailing) throw IOException("sin respuesta")
            server.delete(id)
        }
    }
}
