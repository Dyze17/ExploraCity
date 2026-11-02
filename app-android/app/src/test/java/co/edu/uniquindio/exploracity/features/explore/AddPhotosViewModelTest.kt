package co.edu.uniquindio.exploracity.features.explore

import androidx.lifecycle.SavedStateHandle
import co.edu.uniquindio.exploracity.core.data.connectivity.FakeConnectivity
import co.edu.uniquindio.exploracity.core.data.connectivity.OfflineException
import co.edu.uniquindio.exploracity.domain.model.PlaceFullException
import co.edu.uniquindio.exploracity.domain.model.PlaceReportReason
import co.edu.uniquindio.exploracity.domain.repository.ContributionRepository
import co.edu.uniquindio.exploracity.features.publish.FakePhotos
import co.edu.uniquindio.exploracity.features.publish.FakeUploads
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
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

/** v1.4.0 · Agregar fotos a un lugar publicado: elegir hasta las que caben, subirlas y enviarlas a revisión. */
@OptIn(ExperimentalCoroutinesApi::class)
class AddPhotosViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val connectivity = FakeConnectivity()
    private val store = FakePhotos()
    private val uploads = FakeUploads(connectivity)
    private val contributions = MemoryContributions()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(slots: Int = 2, saved: SavedStateHandle = handle(slots)) =
        AddPhotosViewModel(contributions, store, uploads, connectivity, saved)

    private fun handle(slots: Int) = SavedStateHandle(
        mapOf(AddPhotosViewModel.PLACE_ID_KEY to "mirador", AddPhotosViewModel.PLACE_TITLE_KEY to "Mirador", AddPhotosViewModel.SLOTS_KEY to slots),
    )

    @Test
    fun `las fotos suben y al enviarlas van al lugar y se borran del teléfono`() = runTest(dispatcher) {
        val vm = viewModel()
        assertFalse(vm.state.value.canSend)

        vm.onGalleryPicked(listOf("content://galeria/atardecer.jpg"))
        advanceUntilIdle()
        assertTrue(vm.state.value.canSend)
        vm.onSend()
        advanceUntilIdle()

        assertEquals(listOf("mirador" to listOf("fake://foto-1")), contributions.added)
        assertEquals(true, vm.state.value.done)
        assertEquals(listOf("foto-1"), store.deleted)
    }

    @Test
    fun `no se eligen más de las que caben`() = runTest(dispatcher) {
        val vm = viewModel(slots = 1)

        vm.onGalleryPicked(listOf("content://galeria/a.jpg", "content://galeria/b.jpg"))
        advanceUntilIdle()

        assertEquals(1, vm.state.value.photos.size)
        assertEquals(0, vm.photosLeft)
        assertNull(vm.onCameraShot())
    }

    @Test
    fun `si ya no caben o no hay red, las fotos quedan para intentar de nuevo`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onGalleryPicked(listOf("content://galeria/a.jpg"))
        advanceUntilIdle()

        contributions.error = PlaceFullException()
        vm.onSend()
        advanceUntilIdle()
        assertEquals(AddPhotosError.FULL, vm.state.value.error)
        assertEquals(1, vm.state.value.photos.size)

        contributions.error = OfflineException()
        vm.onSend()
        advanceUntilIdle()
        assertEquals(AddPhotosError.OFFLINE, vm.state.value.error)
        assertNull(vm.state.value.done)
    }

    @Test
    fun `atrás con fotos pregunta y descartarlas las borra`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onGalleryPicked(listOf("content://galeria/a.jpg"))
        advanceUntilIdle()

        vm.onBack()
        assertTrue(vm.state.value.confirmDiscard)
        vm.onDiscard()
        advanceUntilIdle()

        assertEquals(false, vm.state.value.done)
        assertEquals(listOf("foto-1"), store.deleted)
        assertEquals(emptyList<Any>(), contributions.added)
    }

    @Test
    fun `las fotos elegidas vuelven si Android cierra la app`() = runTest(dispatcher) {
        val saved = handle(2)
        val first = viewModel(saved = saved)
        first.onGalleryPicked(listOf("content://galeria/a.jpg"))
        advanceUntilIdle()

        val restored = viewModel(saved = saved)

        assertEquals(first.state.value.photos, restored.state.value.photos)
        assertTrue(restored.state.value.canSend)
    }

    private class MemoryContributions : ContributionRepository {
        val added = mutableListOf<Pair<String, List<String>>>()
        var error: Exception? = null

        override suspend fun addPhotos(placeId: String, urls: List<String>) {
            delay(500)
            error?.let { throw it }
            added += placeId to urls
        }

        override suspend fun reportPlace(placeId: String, reason: PlaceReportReason, detail: String?) = Unit
    }
}
