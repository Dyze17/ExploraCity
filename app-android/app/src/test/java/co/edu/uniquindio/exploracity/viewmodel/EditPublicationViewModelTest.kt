package co.edu.uniquindio.exploracity.viewmodel

import androidx.lifecycle.SavedStateHandle
import co.edu.uniquindio.exploracity.data.connectivity.FakeConnectivity
import co.edu.uniquindio.exploracity.data.repository.FakePoiRepository
import co.edu.uniquindio.exploracity.data.repository.FakePublicationRepository
import co.edu.uniquindio.exploracity.data.repository.OnlineOnlyPublicationRepository
import co.edu.uniquindio.exploracity.data.repository.PublicationRepository
import co.edu.uniquindio.exploracity.domain.model.Category
import co.edu.uniquindio.exploracity.domain.model.PublicationChanges
import co.edu.uniquindio.exploracity.domain.model.PublicationStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

@OptIn(ExperimentalCoroutinesApi::class)
class EditPublicationViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val connectivity = FakeConnectivity()
    private val server = FakePublicationRepository(FakePoiRepository())

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun savedState(id: String = "murales-calle-26") = SavedStateHandle(mapOf(EditPublicationViewModel.PUBLICATION_ID_KEY to id))

    private fun viewModel(
        publications: PublicationRepository = OnlineOnlyPublicationRepository(server, connectivity),
        savedState: SavedStateHandle = savedState(),
    ) = EditPublicationViewModel(publications, connectivity, savedState)

    private val EditPublicationViewModel.form: PublicationChanges
        get() = requireNotNull(state.value.form) { "El formulario debería estar cargado: ${state.value.content}" }

    @Test
    fun `carga los campos de la publicación, sin cambios que guardar`() = runTest(dispatcher) {
        val vm = viewModel()
        assertEquals(EditContent.Loading, vm.state.value.content)

        advanceUntilIdle()

        assertEquals("Murales de la calle 26", vm.form.title)
        assertEquals(Category.CULTURE, vm.form.category)
        assertFalse(vm.state.value.changed)
        assertFalse(vm.state.value.canSave)
    }

    @Test
    fun `una rechazada no se edita aquí`() = runTest(dispatcher) {
        val vm = viewModel(savedState = savedState("mirador-de-la-pena"))
        advanceUntilIdle()

        assertEquals(EditContent.NotEditable, vm.state.value.content)
    }

    @Test
    fun `el error de un campo se ve al salir de él, no mientras se escribe`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onTitleChange("Mur")
        assertFalse(vm.state.value.showTitleError)
        assertFalse("Con un error no se guarda", vm.state.value.canSave)

        vm.onTitleBlur()
        assertTrue(vm.state.value.showTitleError)
        vm.onTitleChange("Murales del centro")
        assertFalse(vm.state.value.showTitleError)
        assertTrue(vm.state.value.canSave)
    }

    @Test
    fun `solo espacios de más no cuentan como cambio`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onTitleChange("  Murales de la calle 26  ")

        assertFalse(vm.state.value.changed)
    }

    @Test
    fun `guardar vuelve a mis publicaciones con el aviso y deja la publicación pendiente`() = runTest(dispatcher) {
        val vm = viewModel(savedState = savedState("sendero-la-vieja"))
        advanceUntilIdle()

        vm.onCategoryChange(Category.ENTERTAINMENT)
        vm.onSave()
        assertTrue(vm.state.value.saving)
        advanceUntilIdle()

        assertEquals(Done.WithMessage(PublicationMessage.SAVED), vm.state.value.done)
        val saved = requireNotNull(server.publication("sendero-la-vieja"))
        assertEquals(Category.ENTERTAINMENT, saved.category)
        assertEquals(PublicationStatus.PENDING, saved.status)
        vm.onDoneHandled()
        assertNull(vm.state.value.done)
    }

    @Test
    fun `sin red guardar avisa, no encola y los cambios siguen en los campos`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onTitleChange("Murales del centro")
        connectivity.online = false

        vm.onSave()
        advanceUntilIdle()

        assertEquals(SaveError.OFFLINE, vm.state.value.saveError)
        assertEquals("Murales del centro", vm.form.title)
        assertNull(vm.state.value.done)
        assertEquals("Murales de la calle 26", server.publication("murales-calle-26")?.title)
    }

    @Test
    fun `volver con cambios pregunta, y descartar sale sin guardar`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onTitleChange("Murales del centro")

        vm.onBack()
        assertTrue(vm.state.value.discardDialog)
        assertNull(vm.state.value.done)

        vm.onKeepEditing()
        assertFalse(vm.state.value.discardDialog)
        vm.onBack()
        vm.onDiscard()
        assertEquals(Done.Left, vm.state.value.done)
    }

    @Test
    fun `volver sin cambios sale directamente`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onBack()

        assertFalse(vm.state.value.discardDialog)
        assertEquals(Done.Left, vm.state.value.done)
    }

    @Test
    fun `eliminar vuelve a mis publicaciones con el aviso`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onOpenDelete()
        vm.onConfirmDelete()
        advanceUntilIdle()

        assertEquals(Done.WithMessage(PublicationMessage.DELETED), vm.state.value.done)
        assertNull(server.publication("murales-calle-26"))
    }

    @Test
    fun `lo escrito y el diálogo abierto sobreviven si Android cierra la app`() = runTest(dispatcher) {
        val savedState = savedState()
        val vm = viewModel(savedState = savedState)
        advanceUntilIdle()
        vm.onTitleChange("Murales del centro")
        vm.onTitleBlur()
        vm.onBack()

        val recreated = viewModel(savedState = savedState)
        advanceUntilIdle()

        assertEquals("Murales del centro", recreated.form.title)
        assertTrue(recreated.state.value.titleTouched)
        assertTrue(recreated.state.value.discardDialog)
    }
}
