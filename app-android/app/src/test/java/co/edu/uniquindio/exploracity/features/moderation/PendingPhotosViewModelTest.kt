package co.edu.uniquindio.exploracity.features.moderation

import co.edu.uniquindio.exploracity.core.data.connectivity.FakeConnectivity
import co.edu.uniquindio.exploracity.domain.model.AlreadyReviewedException
import co.edu.uniquindio.exploracity.domain.model.PhotoRejectionReason
import co.edu.uniquindio.exploracity.domain.model.PlaceFullException
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
import org.junit.Before
import org.junit.Test

/** v1.4.0 · «Fotos por revisar»: publicar, rechazar con motivo y lo que llega tarde. */
@OptIn(ExperimentalCoroutinesApi::class)
class PendingPhotosViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val moderation = MemoryModeration(emptyList()).apply { photos += listOf(pendingPhoto("a"), pendingPhoto("b")) }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun PendingPhotosViewModel.ids() = (state.value.content as PendingPhotosContent.Loaded).items.map { it.id }

    @Test
    fun `publicar la saca de la lista y avisa`() = runTest(dispatcher) {
        val vm = PendingPhotosViewModel(moderation, FakeConnectivity())
        advanceUntilIdle()

        vm.onApprove(moderation.photos.first())
        runCurrent()
        assertEquals("a", vm.state.value.deciding)
        // Mientras se decide una, las demás esperan.
        vm.onApprove(moderation.photos.last())
        advanceUntilIdle()

        assertEquals(listOf("a"), moderation.approved)
        assertEquals(listOf("b"), vm.ids())
        assertEquals(PhotoReviewMessage.APPROVED, vm.state.value.message)
    }

    @Test
    fun `rechazar pide un motivo y lo manda`() = runTest(dispatcher) {
        val vm = PendingPhotosViewModel(moderation, FakeConnectivity())
        advanceUntilIdle()

        vm.onAskReject(moderation.photos.first())
        vm.onConfirmReject()
        assertEquals(emptyList<Any>(), moderation.rejectedPhotos)
        vm.onRejectReasonChange(PhotoRejectionReason.LOW_QUALITY)
        vm.onConfirmReject()
        advanceUntilIdle()

        assertEquals(listOf("a" to PhotoRejectionReason.LOW_QUALITY), moderation.rejectedPhotos)
        assertNull(vm.state.value.rejecting)
        assertEquals(listOf("b"), vm.ids())
        assertEquals(PhotoReviewMessage.REJECTED, vm.state.value.message)
    }

    @Test
    fun `ya decidida sale de la lista y con el lugar lleno se queda para rechazarla`() = runTest(dispatcher) {
        val vm = PendingPhotosViewModel(moderation, FakeConnectivity())
        advanceUntilIdle()

        moderation.photoError = AlreadyReviewedException()
        vm.onApprove(moderation.photos.first())
        advanceUntilIdle()
        assertEquals(listOf("b"), vm.ids())
        assertEquals(PhotoReviewMessage.ALREADY_REVIEWED, vm.state.value.message)

        moderation.photoError = PlaceFullException()
        vm.onApprove(moderation.photos.last())
        advanceUntilIdle()
        assertEquals(listOf("b"), vm.ids())
        assertEquals(PhotoReviewMessage.PLACE_FULL, vm.state.value.message)
    }
}
