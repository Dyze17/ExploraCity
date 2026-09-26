package co.edu.uniquindio.exploracity.viewmodel

import androidx.lifecycle.SavedStateHandle
import co.edu.uniquindio.exploracity.data.connectivity.FakeConnectivity
import co.edu.uniquindio.exploracity.data.connectivity.OfflineException
import co.edu.uniquindio.exploracity.data.local.DraftKey
import co.edu.uniquindio.exploracity.data.local.DraftRepository
import co.edu.uniquindio.exploracity.data.location.AddressResolver
import co.edu.uniquindio.exploracity.data.location.ApproximateAddress
import co.edu.uniquindio.exploracity.data.location.SimulatedLocationProvider
import co.edu.uniquindio.exploracity.data.photos.PhotoStore
import co.edu.uniquindio.exploracity.data.photos.PhotoUploader
import co.edu.uniquindio.exploracity.data.photos.UploadProgress
import co.edu.uniquindio.exploracity.data.repository.CategorySuggester
import co.edu.uniquindio.exploracity.data.repository.DuplicateFinder
import co.edu.uniquindio.exploracity.data.repository.FakeCategorySuggester
import co.edu.uniquindio.exploracity.data.repository.FakePoiRepository
import co.edu.uniquindio.exploracity.data.repository.FakePublicationRepository
import co.edu.uniquindio.exploracity.data.repository.OnlineOnlyPublicationRepository
import co.edu.uniquindio.exploracity.data.sync.PublicationOutbox
import co.edu.uniquindio.exploracity.domain.model.Category
import co.edu.uniquindio.exploracity.domain.model.CategoryOrigin
import co.edu.uniquindio.exploracity.domain.model.DraftPhoto
import co.edu.uniquindio.exploracity.domain.model.DuplicateCheck
import co.edu.uniquindio.exploracity.domain.model.GeoBounds
import co.edu.uniquindio.exploracity.domain.model.GeoPoint
import co.edu.uniquindio.exploracity.domain.model.PriceRange
import co.edu.uniquindio.exploracity.domain.model.PublicationDraft
import co.edu.uniquindio.exploracity.domain.model.PublicationStatus
import co.edu.uniquindio.exploracity.domain.model.PublicationSubmission
import co.edu.uniquindio.exploracity.domain.model.PublishStep
import co.edu.uniquindio.exploracity.domain.model.SentSummary
import co.edu.uniquindio.exploracity.domain.model.SimilarPlace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
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
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class PublishViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val drafts = MemoryDrafts()
    private val publications = FakePublicationRepository(FakePoiRepository())
    private val finder = FakeFinder()
    private val addresses = FakeAddresses()
    private val connectivity = FakeConnectivity()
    private val photos = FakePhotos()
    private val uploads = FakeUploads(connectivity)
    private val outbox = MemoryOutbox()
    private val savedState = SavedStateHandle()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        suggester: CategorySuggester = FakeCategorySuggester(latency = 1.seconds),
        resubmitId: String? = null,
        step: PublishStep = PublishStep.BASICS,
    ) = PublishViewModel(
        drafts = drafts,
        suggester = suggester,
        publications = OnlineOnlyPublicationRepository(publications, connectivity),
        places = PublishPlaces(finder, addresses, SimulatedLocationProvider(here), connectivity, cityCenter, cityBounds),
        delivery = PublishDelivery(photos, uploads, outbox),
        resubmitId = resubmitId,
        initialStep = step,
        savedState = savedState,
    )

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
        assertEquals(PublishExit.Closed, vm.state.value.exit)
    }

    @Test
    fun `guardar conserva el borrador y descartar lo borra`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onTitleChange("Café Las Acacias")
        vm.onClose()

        vm.onSaveAndClose()
        advanceUntilIdle()
        assertEquals(PublishExit.Closed, vm.state.value.exit)
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
        assertEquals("El pin empieza donde estaba", GeoPoint(4.5905, -74.0590), vm.state.value.draft.location)
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

    // 17 · Paso 3, ubicación

    /** Punto que entrega «Usar mi ubicación», el centro de la ciudad y sus límites. */
    private val here = GeoPoint(4.5975, -74.0745)
    private val cityCenter = GeoPoint(4.6097, -74.0817)
    private val cityBounds = GeoBounds(GeoPoint(4.46, -74.23), GeoPoint(4.84, -73.99))
    private val entrance = GeoPoint(4.5976, -74.0747)

    private val similar = SimilarPlace("la-puerta-falsa", "La Puerta Falsa", Category.GASTRONOMY, PublicationStatus.VERIFIED, GeoPoint(4.5977, -74.0746), 16)

    /** Un ViewModel en el paso 3 con los pasos 1 y 2 hechos y, si se pasa, el pin ya puesto. */
    private fun TestScope.onLocationStep(location: GeoPoint? = null): PublishViewModel {
        drafts.saved[DraftKey.New] = basics.copy(category = Category.GASTRONOMY, step = PublishStep.LOCATION, location = location)
        val vm = viewModel()
        advanceUntilIdle()
        return vm
    }

    @Test
    fun `sin pin confirmar muestra el resumen y no busca parecidos (21)`() = runTest(dispatcher) {
        val vm = onLocationStep()

        vm.onContinue()

        assertEquals(listOf(DraftField.LOCATION), vm.state.value.stepErrors)
        assertTrue(vm.state.value.showLocationError)
        assertEquals(1, vm.state.value.errorFocusRequest)
        assertEquals(0, finder.calls)
    }

    @Test
    fun `mover el mapa pone el pin, lo guarda con el borrador y busca su dirección`() = runTest(dispatcher) {
        val vm = onLocationStep()

        vm.onPinMoved(entrance)

        assertEquals(entrance, vm.state.value.draft.location)
        assertEquals(PinAddress.Loading, vm.state.value.address)
        assertNull("Arrastrar ya movió el mapa", vm.state.value.pinTarget)
        advanceUntilIdle()
        assertEquals(PinAddress.Found(addresses.address), vm.state.value.address)
        assertEquals(entrance, drafts.saved[DraftKey.New]?.location)
    }

    @Test
    fun `al retomar el paso 3 con el pin puesto busca su dirección`() = runTest(dispatcher) {
        val vm = onLocationStep(entrance)

        assertEquals(PinAddress.Found(addresses.address), vm.state.value.address)
    }

    @Test
    fun `sin parecidos pasa al paso 4 y anota la búsqueda hecha`() = runTest(dispatcher) {
        val vm = onLocationStep(entrance)

        vm.onContinue()
        assertTrue(vm.state.value.searchingNearby)
        advanceUntilIdle()

        assertFalse(vm.state.value.searchingNearby)
        assertEquals(PublishStep.SCHEDULE, vm.state.value.step)
        assertEquals(DuplicateCheck(entrance), vm.state.value.draft.duplicateCheck)
        assertFalse(vm.state.value.draft.possibleDuplicate)
    }

    @Test
    fun `si la búsqueda tarda más de 500 ms sigue al paso 4 sin aviso y lo anota para el servidor`() = runTest(dispatcher) {
        finder.latency = 2.seconds
        finder.result = listOf(similar)
        val vm = onLocationStep(entrance)

        vm.onContinue()
        advanceTimeBy(499.milliseconds)
        assertEquals(PublishStep.LOCATION, vm.state.value.step)
        advanceTimeBy(2.milliseconds)

        assertEquals(PublishStep.SCHEDULE, vm.state.value.step)
        assertNull(vm.state.value.duplicates)
        assertTrue(vm.state.value.draft.duplicateCheck?.failed == true)
    }

    @Test
    fun `si la búsqueda falla también sigue al paso 4 sin aviso`() = runTest(dispatcher) {
        finder.error = OfflineException()
        val vm = onLocationStep(entrance)

        vm.onContinue()
        advanceUntilIdle()

        assertEquals(PublishStep.SCHEDULE, vm.state.value.step)
        assertTrue(vm.state.value.draft.duplicateCheck?.failed == true)
    }

    @Test
    fun `con parecidos abre la hoja y el segundo toque no busca otra vez (17A)`() = runTest(dispatcher) {
        finder.result = listOf(similar)
        val vm = onLocationStep(entrance)

        vm.onContinue()
        vm.onContinue()
        advanceUntilIdle()

        assertEquals(1, finder.calls)
        assertEquals(PublishStep.LOCATION, vm.state.value.step)
        assertEquals(DuplicateReview(listOf(similar)), vm.state.value.duplicates)
    }

    @Test
    fun `es otro lugar guarda la marca y la nota y sigue al paso 4 (17B)`() = runTest(dispatcher) {
        finder.result = listOf(similar)
        val vm = onLocationStep(entrance)
        vm.onContinue()
        advanceUntilIdle()

        vm.onNotSamePlace()
        assertTrue(vm.state.value.duplicates?.different == true)
        vm.onDuplicateNoteChange("  Es el local del segundo piso.  ")
        vm.onConfirmDifferent()
        advanceUntilIdle()

        assertNull(vm.state.value.duplicates)
        assertEquals(PublishStep.SCHEDULE, vm.state.value.step)
        val check = drafts.saved[DraftKey.New]?.duplicateCheck
        assertEquals(DuplicateCheck(entrance, listOf("la-puerta-falsa"), "Es el local del segundo piso."), check)
        assertTrue(vm.state.value.draft.possibleDuplicate)
    }

    @Test
    fun `volver desde 17B regresa a los parecidos y cerrar la hoja deja el pin donde estaba`() = runTest(dispatcher) {
        finder.result = listOf(similar)
        val vm = onLocationStep(entrance)
        vm.onContinue()
        advanceUntilIdle()

        vm.onNotSamePlace()
        vm.onBackToSimilar()
        assertEquals(false, vm.state.value.duplicates?.different)
        vm.onDuplicatesDismiss()

        assertNull(vm.state.value.duplicates)
        assertEquals(PublishStep.LOCATION, vm.state.value.step)
        assertEquals(entrance, vm.state.value.draft.location)
        assertNull(vm.state.value.draft.duplicateCheck)
    }

    @Test
    fun `confirmar otra vez sin mover el pin no repite la búsqueda, y moverlo sí`() = runTest(dispatcher) {
        finder.result = listOf(similar)
        val vm = onLocationStep(entrance)
        vm.onContinue()
        advanceUntilIdle()
        vm.onNotSamePlace()
        vm.onDuplicateNoteChange("Es otro local")
        vm.onConfirmDifferent()
        advanceUntilIdle()

        vm.onBack()
        vm.onContinue()
        advanceUntilIdle()
        assertEquals(PublishStep.SCHEDULE, vm.state.value.step)
        assertEquals(1, finder.calls)

        vm.onBack()
        vm.onPinMoved(here)
        vm.onContinue()
        advanceUntilIdle()
        assertEquals(2, finder.calls)
        assertEquals(PublishStep.LOCATION, vm.state.value.step)
        assertEquals("Conserva la nota que ya había escrito", "Es otro local", vm.state.value.duplicates?.note)
    }

    @Test
    fun `atrás mientras busca parecidos cancela la búsqueda`() = runTest(dispatcher) {
        finder.result = listOf(similar)
        val vm = onLocationStep(entrance)

        vm.onContinue()
        vm.onBack()
        advanceUntilIdle()

        assertEquals(PublishStep.CATEGORY, vm.state.value.step)
        assertFalse(vm.state.value.searchingNearby)
        assertNull(vm.state.value.duplicates)
    }

    @Test
    fun `descartar mientras busca parecidos no vuelve a guardar el borrador`() = runTest(dispatcher) {
        val vm = onLocationStep(entrance)

        vm.onContinue()
        vm.onClose()
        vm.onDiscard()
        advanceUntilIdle()

        assertNull(drafts.saved[DraftKey.New])
    }

    @Test
    fun `usar mi ubicación lleva el pin y el mapa donde está la persona`() = runTest(dispatcher) {
        val vm = onLocationStep()

        vm.onUseMyLocation()
        advanceUntilIdle()

        assertEquals(here, vm.state.value.draft.location)
        assertEquals(here, vm.state.value.pinTarget)
        vm.onPinTargetShown(entrance)
        assertEquals("Solo se da por mostrado el destino que llegó", here, vm.state.value.pinTarget)
        vm.onPinTargetShown(here)
        assertNull(vm.state.value.pinTarget)
    }

    @Test
    fun `sin permiso la búsqueda por dirección lleva el pin al resultado (17 b)`() = runTest(dispatcher) {
        addresses.places["Calle 85 con 15"] = entrance
        val vm = onLocationStep()

        vm.onLocationDenied()
        assertTrue(vm.state.value.locationDenied)
        vm.onAddressQueryChange("Calle 85 con 15")
        vm.onSearchAddress()
        assertEquals(AddressSearch.Searching, vm.state.value.addressSearch)
        advanceUntilIdle()

        assertEquals(AddressSearch.Idle, vm.state.value.addressSearch)
        assertEquals(entrance, vm.state.value.draft.location)
        assertEquals(entrance, vm.state.value.pinTarget)

        vm.onAddressQueryChange("Calle que no existe")
        vm.onSearchAddress()
        advanceUntilIdle()
        assertEquals(AddressSearch.NotFound("Calle que no existe"), vm.state.value.addressSearch)
        assertEquals(entrance, vm.state.value.draft.location)
    }

    @Test
    fun `sin red la dirección espera y llega sola al volver la red`() = runTest(dispatcher) {
        connectivity.online = false
        addresses.offline = true
        val vm = onLocationStep()

        vm.onPinMoved(entrance)
        advanceUntilIdle()
        assertEquals(PinAddress.Offline, vm.state.value.address)

        addresses.offline = false
        connectivity.online = true
        advanceUntilIdle()
        assertEquals(PinAddress.Found(addresses.address), vm.state.value.address)
    }

    // 18 · Paso 4, horario y precio

    /** Un ViewModel en el paso 4 con los pasos 1 a 3 hechos. */
    private fun TestScope.onScheduleStep(): PublishViewModel {
        drafts.saved[DraftKey.New] = basics.copy(category = Category.GASTRONOMY, location = entrance, duplicateCheck = DuplicateCheck(entrance), step = PublishStep.SCHEDULE)
        val vm = viewModel()
        advanceUntilIdle()
        return vm
    }

    @Test
    fun `sin horario ni la casilla, continuar pide días y horas (21)`() = runTest(dispatcher) {
        val vm = onScheduleStep()

        vm.onContinue()

        assertEquals(listOf(DraftField.DAYS, DraftField.OPENS, DraftField.CLOSES), vm.state.value.stepErrors)
        assertTrue(vm.state.value.showScheduleError(DraftField.DAYS))
        assertEquals(PublishStep.SCHEDULE, vm.state.value.step)
    }

    @Test
    fun `no tengo el horario exacto libera el paso`() = runTest(dispatcher) {
        val vm = onScheduleStep()

        vm.onHoursUnknownChange(true)
        vm.onContinue()

        assertEquals(PublishStep.PHOTOS, vm.state.value.step)
        assertNull(vm.state.value.draft.openingHours)
    }

    @Test
    fun `el cierre debe ser posterior a la apertura, y se avisa sin esperar a continuar`() = runTest(dispatcher) {
        val vm = onScheduleStep()
        vm.onDayToggle(DayOfWeek.MONDAY)
        vm.onDayToggle(DayOfWeek.SATURDAY)
        vm.onOpensChange(LocalTime.of(19, 0))
        vm.onClosesChange(LocalTime.of(7, 0))

        assertTrue(vm.state.value.closesBeforeOpens)
        vm.onContinue()
        assertEquals(listOf(DraftField.CLOSES), vm.state.value.stepErrors)

        vm.onOpensChange(LocalTime.of(7, 0))
        vm.onClosesChange(LocalTime.of(19, 0))
        vm.onContinue()
        advanceUntilIdle()
        assertEquals(PublishStep.PHOTOS, vm.state.value.step)
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.SATURDAY), drafts.saved[DraftKey.New]?.openingHours?.days)
    }

    @Test
    fun `tocar un día otra vez lo desmarca, y el precio elegido se quita igual`() = runTest(dispatcher) {
        val vm = onScheduleStep()

        vm.onDayToggle(DayOfWeek.SUNDAY)
        vm.onDayToggle(DayOfWeek.SUNDAY)
        vm.onPriceChange(PriceRange.LOW)
        assertEquals(PriceRange.LOW, vm.state.value.draft.price)
        vm.onPriceChange(PriceRange.LOW)

        assertTrue(vm.state.value.draft.hours.days.isEmpty())
        assertNull(vm.state.value.draft.price)
    }

    // 19 · Paso 5, fotos, y el envío (20)

    private val complete = basics.copy(
        category = Category.GASTRONOMY,
        location = entrance,
        duplicateCheck = DuplicateCheck(entrance),
        hoursUnknown = true,
        step = PublishStep.PHOTOS,
    )

    private fun TestScope.onPhotosStep(draft: PublicationDraft = complete, resubmitId: String? = null): PublishViewModel {
        drafts.saved[resubmitId?.let { DraftKey.Resubmit(it) } ?: DraftKey.New] = draft
        val vm = viewModel(resubmitId = resubmitId)
        advanceUntilIdle()
        return vm
    }

    @Test
    fun `las fotos de la galería se suben y guardan su dirección en el borrador`() = runTest(dispatcher) {
        val vm = onPhotosStep()

        vm.onGalleryPicked(listOf("content://galeria/patio.jpg", "content://galeria/barra.jpg"))
        advanceTimeBy(600.milliseconds)
        assertEquals(PhotoUpload.Uploading(50), vm.state.value.uploads["foto-1"])
        advanceUntilIdle()

        assertTrue(vm.state.value.uploads.isEmpty())
        assertEquals(listOf("fake://foto-1", "fake://foto-2"), drafts.saved[DraftKey.New]?.photos?.map { it.remoteUrl })
        assertEquals("patio.jpg", vm.state.value.draft.photos.first().name)
    }

    @Test
    fun `como máximo 5 fotos, y una ilegible se avisa sin agregarla`() = runTest(dispatcher) {
        photos.unreadable = setOf("content://galeria/rota.jpg")
        val vm = onPhotosStep()

        vm.onGalleryPicked(listOf("content://galeria/rota.jpg") + (1..7).map { "content://galeria/$it.jpg" })
        advanceUntilIdle()

        assertEquals(5, vm.state.value.draft.photos.size)
        assertEquals(0, vm.state.value.draft.photosLeft)
        assertEquals(PhotoProblem.UNREADABLE, vm.state.value.photoProblem)
    }

    @Test
    fun `si la subida falla la foto queda con error y se reintenta sola al volver la red`() = runTest(dispatcher) {
        val vm = onPhotosStep()
        vm.onGalleryPicked(listOf("content://galeria/patio.jpg"))
        advanceTimeBy(300.milliseconds)

        connectivity.online = false
        advanceUntilIdle()
        assertEquals(PhotoUpload.Failed, vm.state.value.uploads["foto-1"])
        assertEquals(1, vm.state.value.draft.photos.size)

        connectivity.online = true
        advanceUntilIdle()
        assertTrue(vm.state.value.draft.photos.single().uploaded)
    }

    @Test
    fun `quitar una foto cancela su subida y borra el archivo`() = runTest(dispatcher) {
        val vm = onPhotosStep()
        vm.onGalleryPicked(listOf("content://galeria/patio.jpg"))
        advanceTimeBy(300.milliseconds)

        vm.onRemovePhoto("foto-1")
        advanceUntilIdle()

        assertTrue(vm.state.value.draft.photos.isEmpty())
        assertTrue(vm.state.value.uploads.isEmpty())
        assertEquals(listOf("foto-1"), photos.deleted)
    }

    @Test
    fun `la foto de la cámara llega aunque Android haya cerrado la app mientras tanto`() = runTest(dispatcher) {
        val vm = onPhotosStep()
        val shot = vm.onCameraShot()
        assertEquals("content://camara/foto-1.jpg", shot)

        // Android cierra el proceso con la cámara abierta: el ViewModel nuevo recibe la respuesta.
        val again = viewModel()
        again.onCameraResult(taken = true)
        advanceUntilIdle()

        assertEquals("foto-1.jpg", again.state.value.draft.photos.single().name)
    }

    @Test
    fun `sin fotos, enviar muestra el resumen y no envía (21)`() = runTest(dispatcher) {
        val vm = onPhotosStep()

        vm.onContinue()

        assertEquals(listOf(DraftField.PHOTOS), vm.state.value.stepErrors)
        assertTrue(vm.state.value.showPhotosError)
        assertNull(vm.state.value.exit)
    }

    @Test
    fun `enviar con red la deja pendiente en mis publicaciones y borra el borrador`() = runTest(dispatcher) {
        val vm = onPhotosStep()
        vm.onGalleryPicked(listOf("content://galeria/patio.jpg"))
        advanceUntilIdle()

        vm.onContinue()
        assertTrue(vm.state.value.sending)
        advanceUntilIdle()

        val exit = vm.state.value.exit as PublishExit.Sent
        assertEquals(SentSummary("Café Las Acacias", possibleDuplicate = false, queued = false), exit.summary)
        assertNull(drafts.saved[DraftKey.New])
        val sent = publications.myPublications().first()
        assertEquals("Café Las Acacias", sent.title)
        assertEquals(PublicationStatus.PENDING, sent.status)
        assertEquals(listOf("foto-1"), photos.deleted)
    }

    @Test
    fun `con una foto subida ya se envía y las demás siguen en segundo plano`() = runTest(dispatcher) {
        uploads.slow["foto-2"] = 10.seconds
        val vm = onPhotosStep()
        vm.onGalleryPicked(listOf("content://galeria/patio.jpg", "content://galeria/barra.jpg"))
        runCurrent()

        // Aún subiendo las dos: espera a la primera y envía.
        assertEquals(PhotoUpload.Uploading(0), vm.state.value.uploads["foto-1"])
        vm.onContinue()
        advanceTimeBy(1500.milliseconds)
        advanceTimeBy(400.milliseconds)

        assertTrue(vm.state.value.exit is PublishExit.Sent)
        val sent = publications.myPublications().first()
        assertEquals(1, sent.photos)
        assertEquals(listOf("foto-2"), outbox.photos[sent.id]?.map { it.id })
        assertTrue("La foto que falta conserva su archivo", "foto-2" !in photos.deleted)
    }

    @Test
    fun `enviar mientras la foto se prepara la espera en vez de pedir fotos`() = runTest(dispatcher) {
        val vm = onPhotosStep()

        vm.onGalleryPicked(listOf("content://galeria/patio.jpg"))
        vm.onContinue()

        assertTrue(vm.state.value.sending)
        assertFalse(vm.state.value.showPhotosError)
        advanceUntilIdle()
        assertTrue(vm.state.value.exit is PublishExit.Sent)
    }

    @Test
    fun `sin red la publicación completa queda en la cola y se confirma como pendiente de envío`() = runTest(dispatcher) {
        connectivity.online = false
        val vm = onPhotosStep(complete.copy(duplicateCheck = DuplicateCheck(entrance, listOf("la-puerta-falsa"), "Es otro local.")))
        vm.onGalleryPicked(listOf("content://galeria/patio.jpg"))
        advanceUntilIdle()

        vm.onContinue()
        advanceUntilIdle()

        val exit = vm.state.value.exit as PublishExit.Sent
        assertEquals(SentSummary("Café Las Acacias", possibleDuplicate = true, queued = true), exit.summary)
        val queued = outbox.publications.single()
        assertEquals(listOf("foto-1"), queued.photos.map { it.id })
        assertEquals("Es otro local.", queued.duplicateCheck?.note)
        assertNull("El borrador ya está a salvo en la cola", drafts.saved[DraftKey.New])
        assertTrue(photos.deleted.isEmpty())
    }

    @Test
    fun `con red pero ninguna foto subida, avisa y no envía`() = runTest(dispatcher) {
        uploads.failing += "foto-1"
        val vm = onPhotosStep()
        vm.onGalleryPicked(listOf("content://galeria/patio.jpg"))
        advanceUntilIdle()

        vm.onContinue()
        advanceUntilIdle()

        assertEquals(SendError.NO_PHOTO_UPLOADED, vm.state.value.sendError)
        assertFalse(vm.state.value.sending)
        assertNull(vm.state.value.exit)
    }

    @Test
    fun `reenviar una rechazada la deja otra vez pendiente`() = runTest(dispatcher) {
        val rejected = PublicationDraft(
            title = "Mirador de La Peña",
            description = "Mirador en la subida de La Peña con vista al centro. Se llega por un sendero corto.",
            category = Category.NATURE,
            location = GeoPoint(4.5906, -74.0591),
            hoursUnknown = true,
            step = PublishStep.PHOTOS,
        )
        val vm = onPhotosStep(rejected, resubmitId = "mirador-de-la-pena")
        vm.onGalleryPicked(listOf("content://galeria/mirador.jpg"))
        advanceUntilIdle()

        vm.onContinue()
        advanceUntilIdle()

        assertTrue(vm.state.value.exit is PublishExit.Sent)
        val resent = publications.publication("mirador-de-la-pena")
        assertEquals(PublicationStatus.PENDING, resent?.status)
        assertNull(resent?.rejection)
    }

    @Test
    fun `descartar borra también las fotos del teléfono`() = runTest(dispatcher) {
        val vm = onPhotosStep()
        vm.onGalleryPicked(listOf("content://galeria/patio.jpg"))
        advanceUntilIdle()

        vm.onClose()
        vm.onDiscard()
        advanceUntilIdle()

        assertEquals(listOf("foto-1"), photos.deleted)
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

    /** Búsqueda de parecidos con respuesta, demora y fallo a mano; cuenta las búsquedas. */
    private class FakeFinder : DuplicateFinder {
        var result: List<SimilarPlace> = emptyList()
        var latency: Duration = 100.milliseconds
        var error: Exception? = null
        var calls = 0

        override suspend fun similarPlaces(title: String, location: GeoPoint): List<SimilarPlace> {
            calls++
            delay(latency)
            error?.let { throw it }
            return result
        }
    }

    /** Direcciones de prueba: todo punto está en Chapinero; la búsqueda encuentra lo que haya en [places]. */
    private class FakeAddresses : AddressResolver {
        val address = ApproximateAddress("Cl. 45 #19-32", "Chapinero", "Bogotá")
        val places = mutableMapOf<String, GeoPoint>()
        var offline = false

        override suspend fun addressOf(point: GeoPoint): ApproximateAddress {
            delay(200.milliseconds)
            if (offline) throw OfflineException()
            return address
        }

        override suspend fun search(query: String, bounds: GeoBounds): GeoPoint? {
            delay(200.milliseconds)
            return places[query]
        }
    }

    /** Fotos de prueba: cualquier dirección se «comprime» al instante; guarda lo que se borra. */
    private class FakePhotos : PhotoStore {
        val deleted = mutableListOf<String>()
        var unreadable = setOf<String>()
        private var next = 0

        override suspend fun import(uri: String, fallbackName: String?): DraftPhoto? {
            if (uri in unreadable) return null
            next++
            return DraftPhoto("foto-$next", "/fotos/foto-$next.jpg", uri.substringAfterLast('/'))
        }

        override fun newCameraShot(): String = "content://camara/foto-${next + 1}.jpg"

        override suspend fun delete(photo: DraftPhoto) {
            deleted += photo.id
        }
    }

    /** Subida de prueba: 1 s por foto (o lo de [slow]) de 25 en 25 %; sin red, o si está en [failing], falla. */
    private class FakeUploads(private val connectivity: FakeConnectivity) : PhotoUploader {
        val slow = mutableMapOf<String, Duration>()
        val failing = mutableSetOf<String>()

        override fun upload(photo: DraftPhoto): Flow<UploadProgress> = flow {
            val total = slow[photo.id] ?: 1.seconds
            for (percent in 0..100 step 25) {
                if (!connectivity.online || photo.id in failing) throw OfflineException()
                emit(UploadProgress.Sending(percent))
                if (percent < 100) delay(total / 4)
            }
            emit(UploadProgress.Done("fake://${photo.id}"))
        }
    }

    private class MemoryOutbox : PublicationOutbox {
        val publications = mutableListOf<PublicationSubmission>()
        val photos = mutableMapOf<String, List<DraftPhoto>>()

        override suspend fun enqueue(submission: PublicationSubmission) {
            publications += submission
        }

        override suspend fun enqueuePhotos(publicationId: String, photos: List<DraftPhoto>) {
            if (photos.isNotEmpty()) this.photos[publicationId] = photos
        }
    }
}
