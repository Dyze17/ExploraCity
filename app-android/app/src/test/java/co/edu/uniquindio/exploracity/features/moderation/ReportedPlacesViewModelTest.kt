package co.edu.uniquindio.exploracity.features.moderation

import co.edu.uniquindio.exploracity.core.data.connectivity.FakeConnectivity
import co.edu.uniquindio.exploracity.core.data.connectivity.OfflineException
import co.edu.uniquindio.exploracity.domain.model.AlreadyReviewedException
import co.edu.uniquindio.exploracity.domain.model.PlaceReportReason
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

/** v1.4.0 · «Reportados»: la lista, «Descartar» y lo que pasa sin red. */
@OptIn(ExperimentalCoroutinesApi::class)
class ReportedPlacesViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val connectivity = FakeConnectivity()
    private val moderation = MemoryModeration(emptyList()).apply {
        reportedPlaces += reportedPlace("mirador", PlaceReportReason.WRONG_LOCATION, PlaceReportReason.CLOSED)
        reportedPlaces += reportedPlace("plaza")
    }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun ReportedPlacesViewModel.ids() = (state.value.content as ReportedContent.Loaded).items.map { it.id }

    @Test
    fun `carga los lugares reportados`() = runTest(dispatcher) {
        val vm = ReportedPlacesViewModel(moderation, connectivity)
        assertEquals(ReportedContent.Loading, vm.state.value.content)

        advanceUntilIdle()

        assertEquals(listOf("mirador", "plaza"), vm.ids())
    }

    @Test
    fun `descartar pide confirmación, lo saca de la lista y avisa`() = runTest(dispatcher) {
        val vm = ReportedPlacesViewModel(moderation, connectivity)
        advanceUntilIdle()
        val mirador = moderation.reportedPlaces.first()

        vm.onAskDismiss(mirador)
        assertEquals(mirador, vm.state.value.confirming)
        vm.onConfirmDismiss()
        runCurrent()
        assertTrue(vm.state.value.dismissing)
        // Mientras se descarta, «Cancelar» no cierra el diálogo.
        vm.onCancelDismiss()
        assertEquals(mirador, vm.state.value.confirming)
        advanceUntilIdle()

        assertEquals(listOf("mirador"), moderation.dismissed)
        assertEquals(listOf("plaza"), vm.ids())
        assertNull(vm.state.value.confirming)
        assertEquals(ReportedMessage.DISMISSED, vm.state.value.message)
    }

    @Test
    fun `si otro moderador ya los cerró, sale igual de la lista`() = runTest(dispatcher) {
        moderation.dismissError = AlreadyReviewedException()
        val vm = ReportedPlacesViewModel(moderation, connectivity)
        advanceUntilIdle()

        vm.onAskDismiss(moderation.reportedPlaces.first())
        vm.onConfirmDismiss()
        advanceUntilIdle()

        assertEquals(listOf("plaza"), vm.ids())
        assertEquals(ReportedMessage.ALREADY_REVIEWED, vm.state.value.message)
    }

    @Test
    fun `si falla, la lista queda como estaba`() = runTest(dispatcher) {
        moderation.dismissError = IOException("500")
        val vm = ReportedPlacesViewModel(moderation, connectivity)
        advanceUntilIdle()

        vm.onAskDismiss(moderation.reportedPlaces.first())
        vm.onConfirmDismiss()
        advanceUntilIdle()

        assertEquals(listOf("mirador", "plaza"), vm.ids())
        assertEquals(ReportedMessage.FAILED, vm.state.value.message)
    }

    @Test
    fun `sin red dice que hace falta internet y carga al volver`() = runTest(dispatcher) {
        moderation.reportedError = OfflineException()
        val offline = FakeConnectivity(online = false)
        val vm = ReportedPlacesViewModel(moderation, offline)
        advanceUntilIdle()
        assertEquals(ReportedContent.Offline, vm.state.value.content)

        moderation.reportedError = null
        offline.online = true
        advanceUntilIdle()

        assertEquals(listOf("mirador", "plaza"), vm.ids())
    }
}
