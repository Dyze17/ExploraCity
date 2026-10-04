package co.edu.uniquindio.exploracity.viewmodel

import androidx.lifecycle.SavedStateHandle
import co.edu.uniquindio.exploracity.data.connectivity.FakeConnectivity
import co.edu.uniquindio.exploracity.data.location.SimulatedLocationProvider
import co.edu.uniquindio.exploracity.data.repository.FakePoiRepository
import co.edu.uniquindio.exploracity.data.repository.FakePublicationRepository
import co.edu.uniquindio.exploracity.data.repository.OnlineOnlyPublicationRepository
import co.edu.uniquindio.exploracity.data.repository.PublicationRepository
import co.edu.uniquindio.exploracity.domain.model.Category
import co.edu.uniquindio.exploracity.domain.model.GeoBounds
import co.edu.uniquindio.exploracity.domain.model.GeoPoint
import co.edu.uniquindio.exploracity.domain.model.OpeningHours
import co.edu.uniquindio.exploracity.domain.model.OwnPublication
import co.edu.uniquindio.exploracity.domain.model.PriceRange
import co.edu.uniquindio.exploracity.domain.model.PublicationChanges
import co.edu.uniquindio.exploracity.domain.model.PublicationStatus
import co.edu.uniquindio.exploracity.domain.model.SimilarPlace
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
import java.time.DayOfWeek
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class EditPublicationViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val connectivity = FakeConnectivity()
    private val server = FakePublicationRepository(FakePoiRepository())
    private val finder = FakeFinder()
    private val addresses = FakeAddresses()
    private val photos = FakePhotos()
    private val uploads = FakeUploads(connectivity)
    private val outbox = MemoryOutbox()

    private val center = GeoPoint(4.6097, -74.0817)
    private val bounds = GeoBounds(GeoPoint(4.46, -74.23), GeoPoint(4.84, -73.99))

    /** Donde está «Murales de la calle 26» y un punto a unos 20 m. */
    private val murales = GeoPoint(4.6155, -74.0790)
    private val nearby = GeoPoint(4.61568, -74.0790)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun savedState(id: String = "murales-calle-26") = SavedStateHandle(mapOf(EditPublicationViewModel.PUBLICATION_ID_KEY to id))

    private fun viewModel(
        publications: PublicationRepository = OnlineOnlyPublicationRepository(server, connectivity),
        savedState: SavedStateHandle = savedState(),
    ) = EditPublicationViewModel(
        publications = publications,
        connectivity = connectivity,
        savedStateHandle = savedState,
        places = PublishPlaces(finder, addresses, SimulatedLocationProvider(center), connectivity, center, bounds),
        delivery = PublishDelivery(photos, uploads, outbox),
    )

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

    // 23 · Ubicación, horario y precio, y fotos

    @Test
    fun `carga también ubicación, horario, precio y fotos, y la dirección para la tarjeta`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(murales, vm.form.location)
        assertTrue("Murales no tiene horario", vm.form.hoursUnknown)
        assertEquals(PriceRange.FREE, vm.form.price)
        assertEquals(4, vm.form.photos.size)
        assertTrue(vm.form.photos.all { it.uploaded })
        assertEquals(PinAddress.Found(addresses.address), vm.state.value.pin.address)
    }

    @Test
    fun `cambiar el horario y el precio se guarda con la publicación`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onHoursUnknownChange(false)
        assertTrue("Sin días ni horas no se puede guardar", !vm.state.value.canSave)
        assertTrue(vm.state.value.showScheduleError(DraftField.DAYS))
        vm.onDayToggle(DayOfWeek.SATURDAY)
        vm.onDayToggle(DayOfWeek.SUNDAY)
        vm.onOpensChange(LocalTime.of(9, 0))
        vm.onClosesChange(LocalTime.of(17, 0))
        vm.onPriceChange(PriceRange.LOW)
        assertTrue(vm.state.value.canSave)

        vm.onSave()
        advanceUntilIdle()

        val saved = requireNotNull(server.publication("murales-calle-26"))
        assertEquals(OpeningHours(setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), LocalTime.of(9, 0), LocalTime.of(17, 0)), saved.hours)
        assertEquals(PriceRange.LOW, saved.price)
    }

    @Test
    fun `debe quedar al menos una foto`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.form.photos.map { it.id }.forEach(vm::onRemovePhoto)

        assertTrue(vm.state.value.changed)
        assertFalse(vm.state.value.canSave)
        assertTrue("Las publicadas no tienen archivo en el teléfono", photos.deleted.isEmpty())
    }

    @Test
    fun `una foto nueva se sube y se guarda junto a las publicadas`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onRemovePhoto(vm.form.photos.first().id)

        vm.onGalleryPicked(listOf("content://galeria/mural.jpg"))
        runCurrent()
        // Aún subiendo: «Guardar» la espera.
        vm.onSave()
        assertTrue(vm.state.value.saving)
        advanceUntilIdle()

        assertEquals(Done.WithMessage(PublicationMessage.SAVED), vm.state.value.done)
        val saved = requireNotNull(server.publication("murales-calle-26"))
        assertEquals(4, saved.photos.size)
        assertEquals("fake://foto-1", saved.photos.last().url)
        assertEquals("Ya está en el servidor", listOf("foto-1"), photos.deleted)
    }

    @Test
    fun `si la foto nueva no pudo subir, lo dice y no guarda`() = runTest(dispatcher) {
        uploads.failing += "foto-1"
        val vm = viewModel()
        advanceUntilIdle()
        vm.onGalleryPicked(listOf("content://galeria/mural.jpg"))
        advanceUntilIdle()

        vm.onSave()
        advanceUntilIdle()

        assertEquals(SaveError.PHOTOS, vm.state.value.saveError)
        assertNull(vm.state.value.done)
        assertEquals(4, server.publication("murales-calle-26")?.photos?.size)
    }

    @Test
    fun `sin red y con una foto nueva por subir, dice que no hay conexión`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        connectivity.online = false
        vm.onGalleryPicked(listOf("content://galeria/mural.jpg"))
        advanceUntilIdle()

        vm.onSave()
        advanceUntilIdle()

        assertEquals(SaveError.OFFLINE, vm.state.value.saveError)
        assertEquals(5, vm.form.photos.size)
    }

    @Test
    fun `mover el pin a un lugar sin parecidos cambia la ubicación al confirmar`() = runTest(dispatcher) {
        val recording = RecordingUpdates(OnlineOnlyPublicationRepository(server, connectivity))
        val vm = viewModel(publications = recording)
        advanceUntilIdle()

        vm.onOpenLocationEditor()
        assertEquals(LocationEditor(murales), vm.state.value.locationEditor)
        vm.onPinMoved(nearby)
        assertEquals("El formulario no cambia hasta confirmar", murales, vm.form.location)
        vm.onConfirmLocation()
        assertTrue(vm.state.value.pin.searchingNearby)
        advanceUntilIdle()

        assertNull(vm.state.value.locationEditor)
        assertEquals(nearby, vm.form.location)
        assertEquals(1, finder.calls)
        assertEquals("No es parecida a sí misma", "murales-calle-26", finder.excluded)
        vm.onSave()
        advanceUntilIdle()
        assertEquals(nearby, server.publication("murales-calle-26")?.location)
        assertEquals(false, server.publication("murales-calle-26")?.possibleDuplicate)
        assertEquals("La dirección del lugar nuevo viaja", "Cl. 45 #19-32, Chapinero", recording.updates.single().address)
    }

    @Test
    fun `con parecidos cerca pide confirmar que es otro lugar y guarda la marca (17A y 17B)`() = runTest(dispatcher) {
        finder.result = listOf(SimilarPlace("mercado-paloquemao", "Murales de la 26", Category.CULTURE, PublicationStatus.VERIFIED, murales, 20))
        val vm = viewModel()
        advanceUntilIdle()
        vm.onOpenLocationEditor()
        vm.onPinMoved(nearby)

        vm.onConfirmLocation()
        advanceUntilIdle()
        assertTrue(vm.state.value.pin.duplicates != null)
        assertEquals(LocationEditor(nearby), vm.state.value.locationEditor)
        vm.onNotSamePlace()
        vm.onDuplicateNoteChange("Es el tramo de la carrera 13.")
        vm.onConfirmDifferent()

        assertNull(vm.state.value.locationEditor)
        assertEquals(true, vm.form.duplicateCheck?.possibleDuplicate)
        vm.onSave()
        advanceUntilIdle()
        assertEquals(true, server.publication("murales-calle-26")?.possibleDuplicate)
    }

    @Test
    fun `atrás en el mapa no cambia la ubicación y la tarjeta vuelve a su dirección`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onOpenLocationEditor()
        vm.onPinMoved(nearby)
        advanceUntilIdle()

        vm.onBack()
        advanceUntilIdle()

        assertNull(vm.state.value.locationEditor)
        assertEquals(murales, vm.form.location)
        assertFalse(vm.state.value.changed)
        assertEquals(PinAddress.Found(addresses.address), vm.state.value.pin.address)
    }

    @Test
    fun `al volver a abrir el mapa la búsqueda por dirección empieza vacía`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onOpenLocationEditor()
        vm.onLocationDenied()
        vm.onAddressQueryChange("Calle 26")

        vm.onBack()
        vm.onOpenLocationEditor()

        assertEquals("", vm.state.value.pin.addressQuery)
        assertTrue("El aviso sin permiso sigue", vm.state.value.pin.locationDenied)
    }

    @Test
    fun `volver a dejar el pin donde estaba no busca parecidos`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onOpenLocationEditor()
        vm.onPinMoved(nearby)
        vm.onPinMoved(murales)

        vm.onConfirmLocation()
        advanceUntilIdle()

        assertEquals(0, finder.calls)
        assertNull(vm.state.value.locationEditor)
        assertFalse(vm.state.value.changed)
    }

    @Test
    fun `descartar borra las fotos nuevas del teléfono`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onGalleryPicked(listOf("content://galeria/mural.jpg"))
        advanceUntilIdle()

        vm.onBack()
        vm.onDiscard()
        advanceUntilIdle()

        assertEquals(listOf("foto-1"), photos.deleted)
    }

    @Test
    fun `el horario, el precio y las fotos también sobreviven si Android cierra la app`() = runTest(dispatcher) {
        val savedState = savedState()
        val vm = viewModel(savedState = savedState)
        advanceUntilIdle()
        vm.onPriceChange(PriceRange.HIGH)
        vm.onRemovePhoto(vm.form.photos.first().id)

        val recreated = viewModel(savedState = savedState)
        advanceUntilIdle()

        assertEquals(PriceRange.HIGH, recreated.form.price)
        assertEquals(3, recreated.form.photos.size)
    }
}

/** El servidor de publicaciones, que anota los cambios que recibe. */
private class RecordingUpdates(private val delegate: PublicationRepository) : PublicationRepository by delegate {
    val updates = mutableListOf<PublicationChanges>()

    override suspend fun update(id: String, changes: PublicationChanges): OwnPublication {
        updates += changes
        return delegate.update(id, changes)
    }
}
