package co.edu.uniquindio.exploracity.viewmodel

import androidx.lifecycle.SavedStateHandle
import co.edu.uniquindio.exploracity.data.connectivity.FakeConnectivity
import co.edu.uniquindio.exploracity.data.connectivity.OfflineException
import co.edu.uniquindio.exploracity.domain.model.AlreadyReviewedException
import co.edu.uniquindio.exploracity.domain.model.FinalizeReason
import co.edu.uniquindio.exploracity.domain.model.PublicationStatus
import co.edu.uniquindio.exploracity.domain.model.RejectDecision
import co.edu.uniquindio.exploracity.domain.model.RejectionReason
import co.edu.uniquindio.exploracity.domain.model.StateChangedException
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

/** 33A, 35, 36 y «Resueltas» · Comparar, rechazar con motivo (y C1), cambiar de estado y la lista de lo decidido. */
@OptIn(ExperimentalCoroutinesApi::class)
class ModerationDecisionsViewModelsTest {

    private val dispatcher = StandardTestDispatcher()
    private val connectivity = FakeConnectivity()
    private val one = candidate("museo", 23)
    private val two = candidate("biblioteca", 41)
    private val moderation = MemoryModeration(
        listOf(review("a"), review("b", duplicate = true, candidates = listOf(one)), review("c", duplicate = true, candidates = listOf(one, two))),
    )

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun reject(id: String, duplicate: Boolean = false, originalId: String? = null) = RejectPublicationViewModel(
        moderation,
        connectivity,
        SavedStateHandle(mapOf("publicationId" to id, "duplicate" to duplicate, "originalId" to originalId)),
    )

    private fun compare(id: String, savedState: SavedStateHandle = SavedStateHandle(mapOf("publicationId" to id))) =
        CompareDuplicatesViewModel(moderation, connectivity, savedState)

    private fun changeState(id: String) = ChangeStateViewModel(moderation, connectivity, SavedStateHandle(mapOf("publicationId" to id)))

    // 35 · Rechazar

    @Test
    fun `sin motivo no rechaza y dice qué falta`() = runTest(dispatcher) {
        val vm = reject("a")
        advanceUntilIdle()

        vm.onReject()

        assertTrue(vm.state.value.showErrors)
        assertEquals(1, vm.state.value.attempts)
        assertEquals(setOf(RejectProblem.NO_REASON), vm.state.value.problems)
        assertTrue(moderation.rejected.isEmpty())
    }

    @Test
    fun `desde 33 con duplicado llega elegido y, con un solo parecido, enlazado`() = runTest(dispatcher) {
        val vm = reject("b", duplicate = true)
        advanceUntilIdle()

        assertEquals(RejectionReason.DUPLICATE, vm.state.value.reason)
        assertEquals("museo", vm.state.value.originalId)
        assertTrue(vm.state.value.problems.isEmpty())
    }

    @Test
    fun `con varios parecidos hay que elegir el original, salvo que llegue de 33A`() = runTest(dispatcher) {
        val vm = reject("c", duplicate = true)
        advanceUntilIdle()
        assertEquals(setOf(RejectProblem.NO_ORIGINAL), vm.state.value.problems)

        vm.onChooseOriginal()
        assertTrue(vm.state.value.choosingOriginal)
        vm.onOriginalChange("biblioteca")
        assertFalse(vm.state.value.choosingOriginal)
        assertEquals(two, vm.state.value.original)

        val fromCompare = reject("c", duplicate = true, originalId = "biblioteca")
        advanceUntilIdle()
        assertEquals("biblioteca", fromCompare.state.value.originalId)
    }

    @Test
    fun `sin parecidos marcados ofrece los lugares cercanos, sin elegir ninguno`() = runTest(dispatcher) {
        moderation.nearby = listOf(candidate("plaza", 120))
        val vm = reject("a")
        advanceUntilIdle()

        vm.onReasonChange(RejectionReason.DUPLICATE)

        assertEquals(listOf("plaza"), vm.state.value.options.map { it.poi.id })
        assertNull(vm.state.value.originalId)
    }

    @Test
    fun `el mensaje sugerido sigue al original hasta que se edita, y se va con otro motivo`() = runTest(dispatcher) {
        val vm = reject("b", duplicate = true)
        advanceUntilIdle()

        vm.onSuggestion("Ya está publicado como «Museo».")
        vm.onMessageChange("Ya está publicado como «Museo».")
        assertTrue(vm.state.value.suggested)
        vm.onReasonChange(RejectionReason.PHOTO)
        assertEquals("", vm.state.value.message)

        vm.onReasonChange(RejectionReason.DUPLICATE)
        vm.onSuggestion("Ya está publicado como «Museo».")
        vm.onMessageChange("Es el mismo museo, ya publicado.")
        assertFalse(vm.state.value.suggested)
        vm.onSuggestion("Otra sugerencia")
        assertEquals("Es el mismo museo, ya publicado.", vm.state.value.message)
    }

    @Test
    fun `con otro motivo el detalle pide 20 caracteres`() = runTest(dispatcher) {
        val vm = reject("a")
        advanceUntilIdle()
        vm.onReasonChange(RejectionReason.OTHER)
        vm.onMessageChange("Muy corto")
        assertEquals(setOf(RejectProblem.SHORT_DETAIL), vm.state.value.problems)

        vm.onMessageChange("El horario y el precio no coinciden con el lugar.")

        assertTrue(vm.state.value.problems.isEmpty())
    }

    @Test
    fun `rechazar envía el motivo y abre la siguiente con cuántas quedan`() = runTest(dispatcher) {
        val vm = reject("a")
        advanceUntilIdle()
        vm.onReasonChange(RejectionReason.PHOTO)
        vm.onMessageChange("  La foto está muy oscura.  ")
        vm.onCanResubmitChange(false)

        vm.onReject()
        vm.onReject()
        runCurrent()
        assertTrue(vm.state.value.sending)
        advanceUntilIdle()

        assertEquals(listOf("a" to RejectDecision(RejectionReason.PHOTO, "La foto está muy oscura.", canResubmit = false)), moderation.rejected)
        assertEquals(ReviewDone.Next("b", remaining = 2), vm.state.value.done)
    }

    @Test
    fun `un duplicado va con su original y no se puede reenviar`() = runTest(dispatcher) {
        val vm = reject("b", duplicate = true)
        advanceUntilIdle()

        vm.onReject()
        advanceUntilIdle()

        val decision = moderation.rejected.single().second
        assertEquals("museo", decision.originalId)
        assertFalse(decision.canResubmit)
    }

    @Test
    fun `rechazar la última vuelve a la cola vacía`() = runTest(dispatcher) {
        moderation.items.retainAll { it.id == "a" }
        val vm = reject("a")
        advanceUntilIdle()
        vm.onReasonChange(RejectionReason.INAPPROPRIATE)

        vm.onReject()
        advanceUntilIdle()

        assertEquals(ReviewDone.QueueEmpty, vm.state.value.done)
    }

    @Test
    fun `si falla se avisa y se puede reintentar, y si ya la decidió otra persona lo dice`() = runTest(dispatcher) {
        moderation.rejectError = IOException("500")
        val vm = reject("a")
        advanceUntilIdle()
        vm.onReasonChange(RejectionReason.LOCATION)

        vm.onReject()
        advanceUntilIdle()
        assertTrue(vm.state.value.failed)
        assertNull(vm.state.value.done)

        moderation.rejectError = AlreadyReviewedException()
        vm.onFailureShown()
        vm.onReject()
        advanceUntilIdle()
        assertEquals(ReviewContent.Gone, vm.state.value.content)
    }

    @Test
    fun `sin red se puede escribir pero no rechazar`() = runTest(dispatcher) {
        val vm = reject("a")
        advanceUntilIdle()
        connectivity.online = false
        advanceUntilIdle()
        vm.onReasonChange(RejectionReason.PHOTO)

        vm.onReject()
        advanceUntilIdle()

        assertFalse(vm.state.value.canSend)
        assertTrue(moderation.rejected.isEmpty())
    }

    // 33A · Comparar

    @Test
    fun `compara con el primer parecido y se puede cambiar, también al volver`() = runTest(dispatcher) {
        val savedState = SavedStateHandle(mapOf("publicationId" to "c"))
        val vm = compare("c", savedState)
        advanceUntilIdle()
        assertEquals(one, vm.state.value.candidate)

        vm.onSelect(1)
        vm.onSelect(5)

        assertEquals(two, vm.state.value.candidate)
        assertEquals(1, compare("c", savedState).state.value.selected)
    }

    @Test
    fun `sin parecidos no hay qué comparar, y sin red no se decide desde aquí`() = runTest(dispatcher) {
        val plain = compare("a")
        advanceUntilIdle()
        assertEquals(ReviewContent.Gone, plain.state.value.content)

        val vm = compare("b")
        advanceUntilIdle()
        assertTrue(vm.state.value.canDecide)
        connectivity.online = false
        advanceUntilIdle()
        assertFalse(vm.state.value.canDecide)
    }

    // Resueltas

    @Test
    fun `resueltas carga, se pone al día al volver de 36 y sin red se puede reintentar`() = runTest(dispatcher) {
        moderation.resolvedItems += listOf(resolved("x"), resolved("y", PublicationStatus.REJECTED))
        val vm = ResolvedPublicationsViewModel(moderation, connectivity)
        advanceUntilIdle()
        assertEquals(listOf("x", "y"), (vm.state.value.content as ResolvedContent.Loaded).items.map { it.id })

        moderation.resolvedItems.removeAt(0)
        vm.onResume()
        runCurrent()
        assertTrue(vm.state.value.content is ResolvedContent.Loaded)
        advanceUntilIdle()
        assertEquals(listOf("y"), (vm.state.value.content as ResolvedContent.Loaded).items.map { it.id })

        moderation.resolvedError = OfflineException()
        val offline = ResolvedPublicationsViewModel(moderation, connectivity)
        advanceUntilIdle()
        assertEquals(ResolvedContent.Offline, offline.state.value.content)
        moderation.resolvedError = null
        offline.onRetry()
        advanceUntilIdle()
        assertTrue(offline.state.value.content is ResolvedContent.Loaded)
    }

    // 36 · Cambiar estado

    @Test
    fun `una verificada empieza en finalizar y pide el motivo`() = runTest(dispatcher) {
        moderation.resolvedItems += resolved("x")
        val vm = changeState("x")
        advanceUntilIdle()
        assertEquals(StateTarget.FINALIZED, vm.state.value.target)

        vm.onConfirm()
        assertEquals(setOf(ChangeStateProblem.NO_FINALIZE_REASON), vm.state.value.problems)
        assertTrue(vm.state.value.showErrors)
        assertTrue(moderation.finalized.isEmpty())

        vm.onFinalizeReasonChange(FinalizeReason.CLOSED)
        vm.onConfirm()
        advanceUntilIdle()

        assertEquals(listOf("x" to FinalizeReason.CLOSED), moderation.finalized)
        assertEquals(StateTarget.FINALIZED, vm.state.value.done)
    }

    @Test
    fun `una finalizada solo vuelve a pendiente, con un motivo de 20 caracteres`() = runTest(dispatcher) {
        moderation.resolvedItems += resolved("x", PublicationStatus.FINALIZED)
        val vm = changeState("x")
        advanceUntilIdle()
        assertEquals(StateTarget.PENDING, vm.state.value.target)
        vm.onTargetChange(StateTarget.FINALIZED)
        assertEquals(StateTarget.PENDING, vm.state.value.target)

        vm.onReopenReasonChange("Corto")
        vm.onConfirm()
        assertEquals(setOf(ChangeStateProblem.SHORT_REOPEN_REASON), vm.state.value.problems)

        vm.onReopenReasonChange("  Reabrió con otro horario y hay que revisarlo.  ")
        vm.onConfirm()
        advanceUntilIdle()

        assertEquals(listOf("x" to "Reabrió con otro horario y hay que revisarlo."), moderation.reopened)
        assertEquals(StateTarget.PENDING, vm.state.value.done)
    }

    @Test
    fun `si ya cambió de estado lo dice, y si falla se puede reintentar`() = runTest(dispatcher) {
        moderation.resolvedItems += listOf(resolved("x"), resolved("r", PublicationStatus.REJECTED))
        assertEquals(ChangeStateContent.Gone, changeState("r").also { advanceUntilIdle() }.state.value.content)
        assertEquals(ChangeStateContent.Gone, changeState("nada").also { advanceUntilIdle() }.state.value.content)

        val vm = changeState("x")
        advanceUntilIdle()
        vm.onFinalizeReasonChange(FinalizeReason.MERGED)
        moderation.changeError = IOException("500")
        vm.onConfirm()
        advanceUntilIdle()
        assertTrue(vm.state.value.failed)
        assertNull(vm.state.value.done)

        moderation.changeError = StateChangedException()
        vm.onFailureShown()
        vm.onConfirm()
        advanceUntilIdle()
        assertEquals(ChangeStateContent.Gone, vm.state.value.content)
    }
}
