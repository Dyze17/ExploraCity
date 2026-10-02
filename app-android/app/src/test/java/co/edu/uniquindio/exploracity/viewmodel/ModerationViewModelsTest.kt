package co.edu.uniquindio.exploracity.viewmodel

import androidx.lifecycle.SavedStateHandle
import co.edu.uniquindio.exploracity.data.connectivity.FakeConnectivity
import co.edu.uniquindio.exploracity.data.connectivity.OfflineException
import co.edu.uniquindio.exploracity.data.repository.ModerationRepository
import co.edu.uniquindio.exploracity.data.repository.ModerationSummary
import co.edu.uniquindio.exploracity.domain.model.AlreadyReviewedException
import co.edu.uniquindio.exploracity.domain.model.Author
import co.edu.uniquindio.exploracity.domain.model.Category
import co.edu.uniquindio.exploracity.domain.model.CategoryOrigin
import co.edu.uniquindio.exploracity.domain.model.DuplicateSuspicion
import co.edu.uniquindio.exploracity.domain.model.GeoPoint
import co.edu.uniquindio.exploracity.domain.model.ModerationWork
import co.edu.uniquindio.exploracity.domain.model.ReviewAuthor
import co.edu.uniquindio.exploracity.domain.model.ReviewItem
import co.edu.uniquindio.exploracity.domain.model.ReviewQueue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
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
import java.time.Instant
import kotlin.time.Duration.Companion.seconds

/** 32, 33, 34 y 37 · La cola con sus filtros y estados, la revisión, verificar y pasar a la siguiente (C1). */
@OptIn(ExperimentalCoroutinesApi::class)
class ModerationViewModelsTest {

    private val dispatcher = StandardTestDispatcher()
    private val connectivity = FakeConnectivity()
    private val moderation = MemoryModeration(listOf(review("a"), review("b", duplicate = true), review("c")))

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun queue(savedState: SavedStateHandle = SavedStateHandle()) = ModerationQueueViewModel(moderation, connectivity, savedState)

    private fun detail(id: String) = ReviewDetailViewModel(moderation, connectivity, SavedStateHandle(mapOf("publicationId" to id)))

    // 32 · Cola

    @Test
    fun `carga la cola y filtra los posibles duplicados sin perder el orden`() = runTest(dispatcher) {
        val savedState = SavedStateHandle()
        val vm = queue(savedState)
        assertEquals(QueueContent.Loading, vm.state.value.content)
        advanceUntilIdle()

        assertEquals(listOf("a", "b", "c"), vm.state.value.visible.map { it.id })
        assertEquals(1, vm.state.value.duplicates)
        vm.onFilterChange(QueueFilter.DUPLICATES)
        assertEquals(listOf("b"), vm.state.value.visible.map { it.id })
        assertEquals(QueueFilter.DUPLICATES, queue(savedState).state.value.filter)
    }

    @Test
    fun `sin red y sin nada guardado se puede reintentar`() = runTest(dispatcher) {
        moderation.queueError = OfflineException()
        val vm = queue()
        advanceUntilIdle()
        assertEquals(QueueContent.Offline, vm.state.value.content)

        moderation.queueError = null
        vm.onRetry()
        advanceUntilIdle()
        assertEquals(3, vm.state.value.items.size)
    }

    @Test
    fun `lo guardado sin conexión es de solo lectura`() = runTest(dispatcher) {
        moderation.savedAt = Instant.parse("2026-10-02T13:00:00Z")
        val vm = queue()
        advanceUntilIdle()

        assertTrue(vm.state.value.readOnly)
    }

    @Test
    fun `al volver la red se refresca y avisa cuántas son nuevas`() = runTest(dispatcher) {
        val vm = queue()
        advanceUntilIdle()

        connectivity.online = false
        advanceUntilIdle()
        moderation.items += review("d")
        connectivity.online = true
        advanceUntilIdle()

        assertEquals(QueueMessage.Updated(1), vm.state.value.message)
        assertEquals(4, vm.state.value.items.size)
        vm.onMessageShown()
        assertNull(vm.state.value.message)
    }

    @Test
    fun `al volver de una revisión se pone al día sin la carga a pantalla completa`() = runTest(dispatcher) {
        val vm = queue()
        advanceUntilIdle()
        moderation.items.removeAt(0)

        vm.onResume()
        runCurrent()
        assertTrue(vm.state.value.content is QueueContent.Loaded)
        advanceUntilIdle()

        assertEquals(listOf("b", "c"), vm.state.value.items.map { it.id })
    }

    @Test
    fun `vacía muestra el trabajo de hoy`() = runTest(dispatcher) {
        moderation.items.clear()
        moderation.work = ModerationWork(verified = 9, rejected = 2, finalized = 1)
        val vm = queue()
        advanceUntilIdle()

        assertTrue(vm.state.value.empty)
        assertEquals(ModerationWork(9, 2, 1), vm.state.value.work)
        vm.onAllReviewed()
        assertEquals(QueueMessage.AllReviewed, vm.state.value.message)
    }

    // 33 · Revisión

    @Test
    fun `abre la pendiente con su posición en la cola`() = runTest(dispatcher) {
        val vm = detail("b")
        advanceUntilIdle()

        assertEquals("b", vm.state.value.item?.id)
        assertEquals(2, vm.state.value.position)
        assertEquals(3, vm.state.value.total)
    }

    @Test
    fun `si ya no está en la cola lo dice, y sin red y sin guardar también`() = runTest(dispatcher) {
        val gone = detail("z")
        advanceUntilIdle()
        assertEquals(ReviewContent.Gone, gone.state.value.content)

        moderation.itemError = OfflineException()
        val offline = detail("a")
        advanceUntilIdle()
        assertEquals(ReviewContent.Offline, offline.state.value.content)
    }

    @Test
    fun `verificar con nota abre la siguiente con cuántas quedan`() = runTest(dispatcher) {
        val vm = detail("a")
        advanceUntilIdle()

        vm.onVerifyClick()
        vm.onNoteChange("  Coincide con la foto  ")
        vm.onConfirmVerify()
        vm.onConfirmVerify()
        runCurrent()
        assertTrue(vm.state.value.sending)
        advanceUntilIdle()

        assertEquals(listOf("a" to "Coincide con la foto"), moderation.verified)
        assertEquals(ReviewDone.Next("b", remaining = 2), vm.state.value.done)
        assertNull(vm.state.value.verify)
    }

    @Test
    fun `la siguiente es la que venía después, aunque se haya abierto la última`() = runTest(dispatcher) {
        val vm = detail("c")
        advanceUntilIdle()

        vm.onVerifyClick()
        vm.onConfirmVerify()
        advanceUntilIdle()

        assertEquals(ReviewDone.Next("a", remaining = 2), vm.state.value.done)
    }

    @Test
    fun `verificar la última vuelve a la cola vacía`() = runTest(dispatcher) {
        moderation.items.retainAll { it.id == "a" }
        val vm = detail("a")
        advanceUntilIdle()

        vm.onVerifyClick()
        vm.onConfirmVerify()
        advanceUntilIdle()

        assertEquals(ReviewDone.QueueEmpty, vm.state.value.done)
    }

    @Test
    fun `si falla se avisa y reintentar envía la misma nota`() = runTest(dispatcher) {
        moderation.verifyError = IOException("500")
        val vm = detail("a")
        advanceUntilIdle()
        vm.onVerifyClick()
        vm.onNoteChange("Revisada")

        vm.onConfirmVerify()
        advanceUntilIdle()
        assertTrue(vm.state.value.verifyFailed)
        assertNull(vm.state.value.verify)
        assertNull(vm.state.value.done)

        moderation.verifyError = null
        vm.onVerifyFailureShown()
        vm.onRetryVerify()
        advanceUntilIdle()
        assertEquals(listOf("a" to "Revisada", "a" to "Revisada"), moderation.verified)
        assertTrue(vm.state.value.done is ReviewDone.Next)
    }

    @Test
    fun `si otra persona ya la decidió lo dice`() = runTest(dispatcher) {
        moderation.verifyError = AlreadyReviewedException()
        val vm = detail("a")
        advanceUntilIdle()
        vm.onVerifyClick()

        vm.onConfirmVerify()
        advanceUntilIdle()

        assertEquals(ReviewContent.Gone, vm.state.value.content)
    }

    @Test
    fun `sin red se lee pero no se decide`() = runTest(dispatcher) {
        val vm = detail("a")
        advanceUntilIdle()
        connectivity.online = false
        advanceUntilIdle()

        assertFalse(vm.state.value.canDecide)
        vm.onVerifyClick()
        assertNull(vm.state.value.verify)
    }

    @Test
    fun `después de comparar, verificar ya no repite el aviso de duplicado`() = runTest(dispatcher) {
        val vm = detail("b")
        advanceUntilIdle()
        assertFalse(vm.state.value.compared)

        vm.onCompared()

        assertTrue(vm.state.value.compared)
    }

    private fun review(id: String, duplicate: Boolean = false) = ReviewItem(
        id = id,
        title = "Lugar $id",
        category = Category.CULTURE,
        categoryOrigin = CategoryOrigin.CHOSEN,
        description = "Descripción de prueba del lugar $id.",
        photos = emptyList(),
        hours = null,
        price = null,
        address = null,
        location = GeoPoint(4.6, -74.07),
        submittedAt = Instant.parse("2026-10-01T10:00:00Z"),
        author = ReviewAuthor(Author("autor", "Autor", 100), verified = 1, rejected = 0),
        duplicate = if (duplicate) DuplicateSuspicion(emptyList()) else null,
    )
}

/** Una cola en memoria: cargarla y verificar tardan 1 s; abrir una, medio. */
private class MemoryModeration(initial: List<ReviewItem>) : ModerationRepository {
    val items = initial.toMutableList()
    var savedAt: Instant? = null
    var queueError: Exception? = null
    var itemError: Exception? = null
    var verifyError: Exception? = null
    val verified = mutableListOf<Pair<String, String?>>()
    var work = ModerationWork(0, 0, 0)

    override val pendingCount = MutableStateFlow(initial.size)

    override suspend fun summary() = ModerationSummary(items.size, 0)

    override suspend fun queue(): ReviewQueue {
        delay(1.seconds)
        queueError?.let { throw it }
        return ReviewQueue(items.toList(), savedAt)
    }

    override suspend fun item(id: String): ReviewItem? {
        delay(0.5.seconds)
        itemError?.let { throw it }
        return items.firstOrNull { it.id == id }
    }

    override suspend fun queueIds(): List<String> = items.map { it.id }

    override suspend fun verify(id: String, note: String?) {
        verified += id to note
        delay(1.seconds)
        verifyError?.let { throw it }
        items.removeAll { it.id == id }
    }

    override suspend fun todayWork(): ModerationWork = work
}
