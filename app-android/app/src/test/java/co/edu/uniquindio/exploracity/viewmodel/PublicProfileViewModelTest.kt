package co.edu.uniquindio.exploracity.viewmodel

import androidx.lifecycle.SavedStateHandle
import co.edu.uniquindio.exploracity.data.connectivity.FakeConnectivity
import co.edu.uniquindio.exploracity.data.repository.FakePoiRepository
import co.edu.uniquindio.exploracity.data.repository.FakeUserRepository
import co.edu.uniquindio.exploracity.data.repository.OnlineOnlyUserRepository
import co.edu.uniquindio.exploracity.data.repository.UserRepository
import co.edu.uniquindio.exploracity.data.repository.sampleCurrentUser
import co.edu.uniquindio.exploracity.domain.model.PublicProfile
import co.edu.uniquindio.exploracity.domain.model.ReportReason
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class PublicProfileViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val connectivity = FakeConnectivity()
    private val server = FakeUserRepository(FakePoiRepository())

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun savedState(userId: String = "camilo-r") = SavedStateHandle(mapOf(PublicProfileViewModel.USER_ID_KEY to userId))

    private fun viewModel(
        users: UserRepository = OnlineOnlyUserRepository(server, connectivity),
        savedState: SavedStateHandle = savedState(),
    ) = PublicProfileViewModel(users, connectivity, sampleCurrentUser, savedState)

    private val PublicProfileViewModel.profile: PublicProfile get() = requireNotNull(state.value.profile) { "El perfil debería estar cargado" }

    @Test
    fun `carga el perfil de la ruta con la bandera de reportar`() = runTest(dispatcher) {
        val vm = viewModel()
        assertEquals(ProfileContent.Loading, vm.state.value.content)

        advanceUntilIdle()

        assertEquals("Camilo R.", vm.profile.author.name)
        assertFalse(vm.state.value.isOwn)
    }

    @Test
    fun `el perfil propio no se puede reportar`() = runTest(dispatcher) {
        val vm = viewModel(savedState = savedState(sampleCurrentUser.id))
        advanceUntilIdle()

        assertTrue(vm.state.value.isOwn)
        vm.onOpenReport()
        assertNull(vm.state.value.report)
    }

    @Test
    fun `una persona que no existe muestra no encontrado`() = runTest(dispatcher) {
        val vm = viewModel(savedState = savedState("no-existe"))
        advanceUntilIdle()

        assertEquals(ProfileContent.NotFound, vm.state.value.content)
    }

    @Test
    fun `un fallo muestra error recuperable y reintentar lo resuelve`() = runTest(dispatcher) {
        val users = FlakyUsers()
        val vm = viewModel(users)
        advanceUntilIdle()
        assertEquals(ProfileContent.Error, vm.state.value.content)

        users.failing = false
        vm.onRetry()
        advanceUntilIdle()
        assertEquals("Camilo R.", vm.profile.author.name)
    }

    @Test
    fun `más de 8 segundos cargando pasa a error recuperable`() = runTest(dispatcher) {
        val vm = viewModel(FakeUserRepository(FakePoiRepository(), latency = 10.seconds))

        advanceTimeBy(7.9.seconds)
        assertEquals(ProfileContent.Loading, vm.state.value.content)
        advanceTimeBy(0.2.seconds)
        assertEquals(ProfileContent.Error, vm.state.value.content)
    }

    @Test
    fun `sin red lo dice y al volver la red carga solo`() = runTest(dispatcher) {
        connectivity.online = false
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(ProfileContent.Offline, vm.state.value.content)

        connectivity.online = true
        advanceUntilIdle()
        assertEquals("Camilo R.", vm.profile.author.name)
    }

    @Test
    fun `reportar pide un motivo y al enviarse cierra el diálogo y avisa`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onOpenReport()
        assertEquals(ReportDialogState(), vm.state.value.report)
        vm.onConfirmReport()
        advanceUntilIdle()
        assertTrue(server.reports.isEmpty())

        vm.onReasonChange(ReportReason.IMPERSONATION)
        vm.onConfirmReport()
        assertTrue(vm.state.value.report?.sending == true)
        advanceUntilIdle()

        assertEquals(listOf("camilo-r" to ReportReason.IMPERSONATION), server.reports)
        assertNull(vm.state.value.report)
        assertTrue(vm.state.value.reportSent)
        vm.onReportSentShown()
        assertFalse(vm.state.value.reportSent)
    }

    @Test
    fun `si el reporte falla el diálogo sigue con el motivo y lo dice`() = runTest(dispatcher) {
        val users = FlakyUsers(failing = false)
        val vm = viewModel(users)
        advanceUntilIdle()
        vm.onOpenReport()
        vm.onReasonChange(ReportReason.SPAM)

        users.failing = true
        vm.onConfirmReport()
        advanceUntilIdle()

        assertEquals(ReportDialogState(reason = ReportReason.SPAM, error = ReportError.FAILED), vm.state.value.report)
    }

    @Test
    fun `sin red el reporte no se envía y el diálogo dice que falta la conexión`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onOpenReport()
        vm.onReasonChange(ReportReason.SPAM)

        connectivity.online = false
        vm.onConfirmReport()
        advanceUntilIdle()

        assertEquals(ReportDialogState(reason = ReportReason.SPAM, error = ReportError.OFFLINE), vm.state.value.report)
        assertTrue(server.reports.isEmpty())
    }

    @Test
    fun `el diálogo abierto y su motivo sobreviven si el sistema cierra la app`() = runTest(dispatcher) {
        val savedState = savedState()
        val before = viewModel(savedState = savedState)
        advanceUntilIdle()
        before.onOpenReport()
        before.onReasonChange(ReportReason.INAPPROPRIATE_CONTENT)

        val after = viewModel(savedState = savedState)

        assertEquals(ReportDialogState(reason = ReportReason.INAPPROPRIATE_CONTENT), after.state.value.report)
    }

    private inner class FlakyUsers(var failing: Boolean = true) : UserRepository by server {
        override suspend fun publicProfile(userId: String): PublicProfile? {
            if (failing) throw IOException("sin red")
            return server.publicProfile(userId)
        }

        override suspend fun reportUser(userId: String, reason: ReportReason) {
            if (failing) throw IOException("sin red")
            server.reportUser(userId, reason)
        }
    }
}
