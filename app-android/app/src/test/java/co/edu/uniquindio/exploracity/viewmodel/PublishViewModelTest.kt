package co.edu.uniquindio.exploracity.viewmodel

import co.edu.uniquindio.exploracity.data.connectivity.OfflineException
import co.edu.uniquindio.exploracity.data.local.DraftKey
import co.edu.uniquindio.exploracity.data.local.DraftRepository
import co.edu.uniquindio.exploracity.data.repository.CategorySuggester
import co.edu.uniquindio.exploracity.data.repository.FakeCategorySuggester
import co.edu.uniquindio.exploracity.data.repository.FakePoiRepository
import co.edu.uniquindio.exploracity.data.repository.FakePublicationRepository
import co.edu.uniquindio.exploracity.domain.model.Category
import co.edu.uniquindio.exploracity.domain.model.CategoryOrigin
import co.edu.uniquindio.exploracity.domain.model.PublicationDraft
import co.edu.uniquindio.exploracity.domain.model.PublishStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class PublishViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val drafts = MemoryDrafts()
    private val publications = FakePublicationRepository(FakePoiRepository())

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        suggester: CategorySuggester = FakeCategorySuggester(latency = 1.seconds),
        resubmitId: String? = null,
        step: PublishStep = PublishStep.BASICS,
    ) = PublishViewModel(drafts, suggester, publications, resubmitId, step)

    private val basics = PublicationDraft(
        title = "Café Las Acacias",
        description = "Café de barrio con tostión propia y un patio interior lleno de matas.",
    )

    /** Un ViewModel ya en el paso 2 con título y descripción válidos. */
    private fun TestScope.onCategoryStep(suggester: CategorySuggester = FakeCategorySuggester(latency = 1.seconds)): PublishViewModel {
        val vm = viewModel(suggester)
        advanceUntilIdle()
        vm.onTitleChange(basics.title)
        vm.onDescriptionChange(basics.description)
        vm.onContinue()
        return vm
    }

    @Test
    fun `empieza vacío y guarda solo lo que se escribe`() = runTest(dispatcher) {
        val vm = viewModel()
        assertEquals(PublishContent.Loading, vm.state.value.content)
        advanceUntilIdle()
        assertEquals(PublishContent.Editing, vm.state.value.content)

        vm.onTitleChange("Café Las Acacias")
        assertNull("Espera un momento antes de escribir en disco", drafts.saved[DraftKey.New])
        advanceUntilIdle()

        assertEquals("Café Las Acacias", drafts.saved[DraftKey.New]?.title)
    }

    @Test
    fun `retoma el borrador guardado en el paso en que quedó`() = runTest(dispatcher) {
        drafts.saved[DraftKey.New] = basics.copy(category = Category.GASTRONOMY, categoryOrigin = CategoryOrigin.CHOSEN, step = PublishStep.CATEGORY)

        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(PublishStep.CATEGORY, vm.state.value.step)
        assertEquals("Café Las Acacias", vm.state.value.draft.title)
        assertEquals(Suggestion.Idle, vm.state.value.suggestion)
    }

    @Test
    fun `al retomar una categoría sugerida conserva su aviso`() = runTest(dispatcher) {
        drafts.saved[DraftKey.New] = basics.copy(category = Category.GASTRONOMY, categoryOrigin = CategoryOrigin.SUGGESTED, step = PublishStep.CATEGORY)

        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(Suggestion.Ready(Category.GASTRONOMY), vm.state.value.suggestion)
    }

    @Test
    fun `continuar con algo pendiente muestra el resumen y lleva el foco al primer error (21)`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onTitleChange("Café")

        vm.onContinue()

        val state = vm.state.value
        assertEquals(PublishStep.BASICS, state.step)
        assertTrue(state.showErrors)
        assertEquals(listOf(DraftField.TITLE, DraftField.DESCRIPTION), state.stepErrors)
        assertTrue(state.showTitleError)
        assertTrue(state.showDescriptionError)
        assertEquals(1, state.errorFocusRequest)
    }

    @Test
    fun `el error de un campo se ve al salir de él, no mientras se escribe (15 · b)`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onTitleChange("Caf")
        assertFalse(vm.state.value.showTitleError)
        vm.onTitleBlur()
        assertTrue(vm.state.value.showTitleError)
        assertFalse(vm.state.value.showDescriptionError)
    }

    @Test
    fun `al llegar al paso 2 la sugerencia se busca y queda preseleccionada`() = runTest(dispatcher) {
        val vm = onCategoryStep()
        assertEquals(PublishStep.CATEGORY, vm.state.value.step)
        assertEquals(Suggestion.Searching, vm.state.value.suggestion)

        advanceUntilIdle()

        assertEquals(Suggestion.Ready(Category.GASTRONOMY), vm.state.value.suggestion)
        assertEquals(Category.GASTRONOMY, vm.state.value.draft.category)
        assertEquals(CategoryOrigin.SUGGESTED, vm.state.value.draft.categoryOrigin)
    }

    @Test
    fun `elegir otra reemplaza la sugerida y volver a ella la recupera`() = runTest(dispatcher) {
        val vm = onCategoryStep()
        advanceUntilIdle()

        vm.onCategoryChange(Category.CULTURE)
        assertEquals(Suggestion.Replaced(Category.GASTRONOMY), vm.state.value.suggestion)
        assertEquals(CategoryOrigin.CHOSEN, vm.state.value.draft.categoryOrigin)

        vm.onCategoryChange(Category.GASTRONOMY)
        assertEquals(Suggestion.Ready(Category.GASTRONOMY), vm.state.value.suggestion)
        assertEquals(CategoryOrigin.SUGGESTED, vm.state.value.draft.categoryOrigin)
    }

    @Test
    fun `elegir mientras busca cancela la sugerencia y no se pisa lo elegido`() = runTest(dispatcher) {
        val vm = onCategoryStep()
        advanceTimeBy(300.milliseconds)

        vm.onCategoryChange(Category.HISTORY)
        advanceUntilIdle()

        assertEquals(Suggestion.Idle, vm.state.value.suggestion)
        assertEquals(Category.HISTORY, vm.state.value.draft.category)
        assertEquals(CategoryOrigin.CHOSEN, vm.state.value.draft.categoryOrigin)
    }

    @Test
    fun `sin respuesta a los 5 segundos sigue a mano, y el reintento la aplica (16 · c)`() = runTest(dispatcher) {
        val slow = FakeCategorySuggester(latency = 10.seconds)
        val vm = onCategoryStep(slow)

        advanceTimeBy(4.9.seconds)
        assertEquals(Suggestion.Searching, vm.state.value.suggestion)
        advanceTimeBy(0.2.seconds)
        assertEquals(Suggestion.NoAnswer, vm.state.value.suggestion)
        assertNull(vm.state.value.draft.category)

        vm.onCategoryChange(Category.HISTORY)
        assertEquals(Suggestion.NoAnswer, vm.state.value.suggestion)
        assertEquals(CategoryOrigin.CHOSEN, vm.state.value.draft.categoryOrigin)
    }

    @Test
    fun `sin red la sugerencia no responde y el formulario sigue`() = runTest(dispatcher) {
        val vm = onCategoryStep(object : CategorySuggester {
            override suspend fun suggest(title: String, description: String): Category? = throw OfflineException()
        })
        advanceUntilIdle()

        assertEquals(Suggestion.NoAnswer, vm.state.value.suggestion)
        vm.onContinue()
        assertTrue("Sin categoría no avanza", vm.state.value.showCategoryError)
    }

    @Test
    fun `atrás retrocede un paso y en el primero pregunta antes de cerrar (15A)`() = runTest(dispatcher) {
        val vm = onCategoryStep()
        advanceUntilIdle()

        vm.onBack()
        assertEquals(PublishStep.BASICS, vm.state.value.step)
        vm.onBack()
        assertTrue(vm.state.value.closeDialog)
        assertNull(vm.state.value.exit)

        vm.onKeepEditing()
        assertFalse(vm.state.value.closeDialog)
    }

    @Test
    fun `sin nada escrito cerrar sale sin preguntar`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onClose()

        assertFalse(vm.state.value.closeDialog)
        assertEquals(PublishExit.CLOSED, vm.state.value.exit)
    }

    @Test
    fun `guardar conserva el borrador y descartar lo borra`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onTitleChange("Café Las Acacias")
        vm.onClose()

        vm.onSaveAndClose()
        advanceUntilIdle()
        assertEquals(PublishExit.CLOSED, vm.state.value.exit)
        assertEquals("Café Las Acacias", drafts.saved[DraftKey.New]?.title)

        val again = viewModel()
        advanceUntilIdle()
        again.onClose()
        again.onDiscard()
        advanceUntilIdle()
        assertNull(drafts.saved[DraftKey.New])
    }

    @Test
    fun `corregir una rechazada parte de lo enviado, en el paso indicado, sin tocar el borrador nuevo`() = runTest(dispatcher) {
        drafts.saved[DraftKey.New] = basics

        val vm = viewModel(resubmitId = "mirador-de-la-pena", step = PublishStep.LOCATION)
        advanceUntilIdle()

        assertTrue(vm.state.value.resubmit)
        assertEquals(PublishStep.LOCATION, vm.state.value.step)
        assertEquals("Mirador de La Peña", vm.state.value.draft.title)
        assertEquals(Category.NATURE, vm.state.value.draft.category)
        vm.onTitleChange("Mirador de La Peña, sendero")
        advanceUntilIdle()
        assertEquals(basics, drafts.saved[DraftKey.New])
        assertEquals("Mirador de La Peña, sendero", drafts.saved[DraftKey.Resubmit("mirador-de-la-pena")]?.title)
    }

    @Test
    fun `si la publicación a corregir no se puede traer lo dice`() = runTest(dispatcher) {
        val vm = viewModel(resubmitId = "no-existe")
        advanceUntilIdle()

        assertEquals(PublishContent.Error, vm.state.value.content)
    }

    @Test
    fun `en el último paso continuar sigue a la confirmación provisional`() = runTest(dispatcher) {
        drafts.saved[DraftKey.New] = basics.copy(category = Category.GASTRONOMY, step = PublishStep.PHOTOS)
        val vm = viewModel()
        advanceUntilIdle()

        vm.onContinue()

        assertEquals(PublishExit.SENT, vm.state.value.exit)
    }

    /** Borradores en memoria. */
    private class MemoryDrafts : DraftRepository {
        val saved = mutableMapOf<DraftKey, PublicationDraft>()

        override suspend fun load(key: DraftKey): PublicationDraft? = saved[key]

        override suspend fun save(key: DraftKey, draft: PublicationDraft) {
            saved[key] = draft
        }

        override suspend fun clear(key: DraftKey) {
            saved.remove(key)
        }
    }
}
