package co.edu.uniquindio.exploracity.viewmodel

import androidx.lifecycle.SavedStateHandle
import co.edu.uniquindio.exploracity.data.connectivity.FakeConnectivity
import co.edu.uniquindio.exploracity.data.connectivity.OfflineException
import co.edu.uniquindio.exploracity.data.repository.FakePoiRepository
import co.edu.uniquindio.exploracity.data.repository.FakeUserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import kotlin.time.Duration

/** 30 · Doble confirmación, sin red, fallo sin estados a medias y borrado de lo del teléfono. */
@OptIn(ExperimentalCoroutinesApi::class)
class DeleteAccountViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val connectivity = FakeConnectivity()
    private val users = FakeUserRepository(FakePoiRepository(), latency = Duration.ZERO)
    private val accounts = FakeAccounts()
    private val session = FakeSession()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(savedState: SavedStateHandle = SavedStateHandle()) =
        DeleteAccountViewModel(users, accounts, session, connectivity, savedState)

    @Test
    fun `carga el perfil para nombrar puntos, nivel e insignias`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(340, vm.state.value.profile?.author?.points)
        assertEquals(2, vm.state.value.profile?.unlockedBadges)
    }

    @Test
    fun `hay que escribir ELIMINAR, sin importar mayúsculas ni espacios`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onContinue()

        vm.onTypedChange("ELIMINA")
        assertFalse(vm.state.value.confirmation!!.matches)
        vm.onConfirmDelete()
        advanceUntilIdle()
        assertFalse("Sin la palabra no se borra", accounts.deleted)

        vm.onTypedChange(" eliminar ")
        assertTrue(vm.state.value.confirmation!!.matches)
    }

    @Test
    fun `confirmar borra la cuenta, luego lo del teléfono, y lleva al inicio de sesión`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onContinue()
        vm.onTypedChange("ELIMINAR")

        vm.onConfirmDelete()
        runCurrent()
        assertEquals(true, vm.state.value.confirmation?.deleting)
        vm.onDismissConfirmation()
        assertTrue("Mientras borra no se puede cancelar", vm.state.value.confirmation != null)
        advanceUntilIdle()

        assertTrue(accounts.deleted)
        assertTrue(session.accountDataDeleted)
        assertTrue(vm.state.value.deleted)
    }

    @Test
    fun `si el servidor falla, la cuenta y lo del teléfono siguen como estaban`() = runTest(dispatcher) {
        accounts.deleteError = IOException("500")
        val vm = viewModel()
        advanceUntilIdle()
        vm.onContinue()
        vm.onTypedChange("ELIMINAR")

        vm.onConfirmDelete()
        advanceUntilIdle()

        assertEquals(DeleteAccountError.FAILED, vm.state.value.confirmation?.error)
        assertEquals(false, vm.state.value.confirmation?.deleting)
        assertFalse(session.accountDataDeleted)
        assertFalse(vm.state.value.deleted)

        vm.onTypedChange("ELIMINAR ")
        assertNull("Escribir de nuevo quita el aviso", vm.state.value.confirmation?.error)
    }

    @Test
    fun `sin red no se puede continuar, y si la red se cae con el diálogo abierto lo dice`() = runTest(dispatcher) {
        connectivity.online = false
        val vm = viewModel()
        advanceUntilIdle()
        assertTrue(vm.state.value.offline)
        vm.onContinue()
        assertNull(vm.state.value.confirmation)

        connectivity.online = true
        advanceUntilIdle()
        vm.onContinue()
        vm.onTypedChange("ELIMINAR")
        connectivity.online = false
        vm.onConfirmDelete()
        advanceUntilIdle()

        assertEquals(DeleteAccountError.OFFLINE, vm.state.value.confirmation?.error)
        assertFalse(accounts.deleted)
    }

    @Test
    fun `si la red se cae durante el borrado lo dice como sin conexión`() = runTest(dispatcher) {
        accounts.deleteError = OfflineException()
        val vm = viewModel()
        advanceUntilIdle()
        vm.onContinue()
        vm.onTypedChange("eliminar")

        vm.onConfirmDelete()
        advanceUntilIdle()

        assertEquals(DeleteAccountError.OFFLINE, vm.state.value.confirmation?.error)
    }

    @Test
    fun `el diálogo y lo escrito sobreviven si Android cierra la app`() = runTest(dispatcher) {
        val savedState = SavedStateHandle()
        val vm = viewModel(savedState)
        advanceUntilIdle()
        vm.onContinue()
        vm.onTypedChange("ELIMI")

        val recreated = viewModel(savedState)
        advanceUntilIdle()

        assertEquals(DeleteConfirmation("ELIMI"), recreated.state.value.confirmation)
        recreated.onDismissConfirmation()
        assertNull(viewModel(savedState).state.value.confirmation)
    }
}
