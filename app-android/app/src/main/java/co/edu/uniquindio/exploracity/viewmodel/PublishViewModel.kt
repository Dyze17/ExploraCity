package co.edu.uniquindio.exploracity.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import co.edu.uniquindio.exploracity.ExploraApplication
import co.edu.uniquindio.exploracity.data.connectivity.ConnectivityObserver
import co.edu.uniquindio.exploracity.data.connectivity.OfflineException
import co.edu.uniquindio.exploracity.data.local.DraftKey
import co.edu.uniquindio.exploracity.data.local.DraftRepository
import co.edu.uniquindio.exploracity.data.location.AddressResolver
import co.edu.uniquindio.exploracity.data.location.ApproximateAddress
import co.edu.uniquindio.exploracity.data.location.LocationProvider
import co.edu.uniquindio.exploracity.data.photos.PhotoStore
import co.edu.uniquindio.exploracity.data.photos.PhotoUploader
import co.edu.uniquindio.exploracity.data.photos.UploadProgress
import co.edu.uniquindio.exploracity.data.repository.CategorySuggester
import co.edu.uniquindio.exploracity.data.repository.DuplicateFinder
import co.edu.uniquindio.exploracity.data.repository.PublicationRepository
import co.edu.uniquindio.exploracity.data.sync.PublicationOutbox
import co.edu.uniquindio.exploracity.domain.model.Category
import co.edu.uniquindio.exploracity.domain.model.CategoryOrigin
import co.edu.uniquindio.exploracity.domain.model.DraftPhoto
import co.edu.uniquindio.exploracity.domain.model.DuplicateCheck
import co.edu.uniquindio.exploracity.domain.model.GeoBounds
import co.edu.uniquindio.exploracity.domain.model.GeoPoint
import co.edu.uniquindio.exploracity.domain.model.PhotoRules
import co.edu.uniquindio.exploracity.domain.model.PriceRange
import co.edu.uniquindio.exploracity.domain.model.PublicationDraft
import co.edu.uniquindio.exploracity.domain.model.PublicationSubmission
import co.edu.uniquindio.exploracity.domain.model.PublishStep
import co.edu.uniquindio.exploracity.domain.model.SentSummary
import co.edu.uniquindio.exploracity.domain.model.SimilarPlace
import co.edu.uniquindio.exploracity.navigation.PublishForm
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.DayOfWeek
import java.time.LocalTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** 15–19 · Qué muestra el formulario. */
sealed interface PublishContent {
    /** Leyendo el borrador (o la publicación a corregir). */
    data object Loading : PublishContent

    data object Editing : PublishContent

    /** No se pudo traer la publicación rechazada que se iba a corregir (24). */
    data object Error : PublishContent
}

/** 16 · La sugerencia automática de categoría. */
sealed interface Suggestion {
    /** No aplica: aún no se pidió o la categoría la eligió la persona. */
    data object Idle : Suggestion

    /** 16.a · «Buscando una categoría para ti…». */
    data object Searching : Suggestion

    /** 16.b · Llegó y quedó preseleccionada. */
    data class Ready(val category: Category) : Suggestion

    /** 16.b · Se eligió otra: «Cambiaste la categoría sugerida». */
    data class Replaced(val suggested: Category) : Suggestion

    /** 16.c · Sin respuesta a los 5 s (o sin red): se sigue a mano, con reintento opcional. */
    data object NoAnswer : Suggestion

    /** Respondió, pero ninguna categoría encaja con claridad. */
    data object NotFound : Suggestion
}

/** Un campo que impide continuar (21). */
enum class DraftField { TITLE, DESCRIPTION, CATEGORY, LOCATION, DAYS, OPENS, CLOSES, PHOTOS }

/** 19 · Cómo va la subida de una foto que aún no tiene dirección en el servidor. */
sealed interface PhotoUpload {
    /** 0 a 100. */
    data class Uploading(val percent: Int) : PhotoUpload

    /** Falló (sin red o error): la foto y el formulario siguen guardados; se reintenta sola al volver la red. */
    data object Failed : PhotoUpload
}

/** 19 · Un aviso sobre las fotos que no es de una en particular. */
enum class PhotoProblem {
    /** No se pudo leer o comprimir la foto elegida. */
    UNREADABLE,

    /** El teléfono no tiene app de cámara: queda la galería. */
    NO_CAMERA,
}

/** 19 → 20 · El envío no salió; la publicación sigue en el formulario. */
enum class SendError {
    /** Con red, ninguna foto terminó de subir: se reintentan antes de enviar. */
    NO_PHOTO_UPLOADED,

    /** El servidor no la recibió. */
    FAILED,
}

/** 17 · La dirección aproximada del pin. */
sealed interface PinAddress {
    data object Loading : PinAddress

    data class Found(val address: ApproximateAddress) : PinAddress

    /** Respondió, pero ese punto no tiene dirección: bastan las coordenadas. */
    data object NotFound : PinAddress

    /** Sin red: llega sola al volver la red. */
    data object Offline : PinAddress

    /** Falló o no respondió: bastan las coordenadas. */
    data object Unavailable : PinAddress
}

/** 17.b · «Buscar una dirección o barrio». */
sealed interface AddressSearch {
    data object Idle : AddressSearch

    data object Searching : AddressSearch

    data class NotFound(val query: String) : AddressSearch

    data object Offline : AddressSearch

    data object Failed : AddressSearch
}

/** 17A · La hoja «¿Ya existe este lugar?» abierta con sus parecidos; [different] es su segundo estado (17B). */
data class DuplicateReview(
    val places: List<SimilarPlace>,
    val different: Boolean = false,
    /** 17B · «¿En qué se diferencia?». */
    val note: String = "",
)

/** Cómo sale del formulario. */
sealed interface PublishExit {
    /** Cerrar o «Guardar»: el borrador queda guardado (o no había nada que guardar). */
    data object Closed : PublishExit

    /** «Enviar a verificación»: a la confirmación (20), enviada o en la cola sin red. */
    data class Sent(val summary: SentSummary) : PublishExit
}

data class PublishUiState(
    val content: PublishContent = PublishContent.Loading,
    val draft: PublicationDraft = PublicationDraft(),
    /** Corrige una rechazada (24): no toca el borrador de la publicación nueva. */
    val resubmit: Boolean = false,
    /** El error de un campo se ve al salir de él (15.b) o al tocar «Continuar» con algo pendiente (21). */
    val titleTouched: Boolean = false,
    val descriptionTouched: Boolean = false,
    /** 21 · Se tocó «Continuar» con errores en este paso: resumen arriba y error junto a cada campo. */
    val showErrors: Boolean = false,
    /** Cambia cada vez que hay que llevar el foco al primer campo con error (21). */
    val errorFocusRequest: Int = 0,
    val suggestion: Suggestion = Suggestion.Idle,
    /** 17 · Dirección del pin; null mientras no esté puesto. */
    val address: PinAddress? = null,
    /** 17 · El mapa debe llevar el pin aquí («Usar mi ubicación», búsqueda, ajuste fino). */
    val pinTarget: GeoPoint? = null,
    /** 17.b · Se negó el permiso de ubicación: aviso, búsqueda por dirección y pin a mano. */
    val locationDenied: Boolean = false,
    val addressQuery: String = "",
    val addressSearch: AddressSearch = AddressSearch.Idle,
    /** 17 · «Buscando lugares cercanos…» dentro del botón. */
    val searchingNearby: Boolean = false,
    val duplicates: DuplicateReview? = null,
    /** 17A · Se abrió «Ver este lugar»: la hoja se esconde mientras tanto y vuelve al regresar. */
    val awayForPlace: Boolean = false,
    /** 19 · Subidas en curso o fallidas, por id de foto; las subidas no están aquí. */
    val uploads: Map<String, PhotoUpload> = emptyMap(),
    /** 19 · Fotos elegidas que aún se están comprimiendo («Preparando la foto…»). */
    val preparingPhotos: Int = 0,
    val photoProblem: PhotoProblem? = null,
    /** 19 → 20 · «Enviar a verificación» en curso (espera la primera foto si hace falta). */
    val sending: Boolean = false,
    val sendError: SendError? = null,
    /** 15A · «¿Guardar el borrador?» abierto. */
    val closeDialog: Boolean = false,
    val exit: PublishExit? = null,
) {
    val step: PublishStep get() = draft.step

    /** Lo que falta en el paso actual, en el orden de la pantalla. */
    val stepErrors: List<DraftField>
        get() = when (step) {
            PublishStep.BASICS -> listOfNotNull(
                DraftField.TITLE.takeIf { draft.titleMissing > 0 },
                DraftField.DESCRIPTION.takeIf { draft.descriptionMissing > 0 },
            )
            PublishStep.CATEGORY -> listOfNotNull(DraftField.CATEGORY.takeIf { draft.category == null })
            PublishStep.LOCATION -> listOfNotNull(DraftField.LOCATION.takeIf { draft.location == null })
            // 18 · «No tengo el horario exacto» libera el paso; si no, días y horas, con el cierre después de la apertura.
            PublishStep.SCHEDULE -> if (draft.hoursUnknown) {
                emptyList()
            } else {
                listOfNotNull(
                    DraftField.DAYS.takeIf { draft.hours.days.isEmpty() },
                    DraftField.OPENS.takeIf { draft.hours.opens == null },
                    DraftField.CLOSES.takeIf { draft.hours.closes == null || !draft.hours.closesAfterOpens },
                )
            }
            // Una foto que se está preparando ya cuenta: «Enviar» la espera.
            PublishStep.PHOTOS -> listOfNotNull(DraftField.PHOTOS.takeIf { draft.photos.size < PhotoRules.MIN && preparingPhotos == 0 })
        }

    val showTitleError: Boolean get() = (titleTouched || showErrors) && draft.titleMissing > 0

    val showDescriptionError: Boolean get() = (descriptionTouched || showErrors) && draft.descriptionMissing > 0

    val showCategoryError: Boolean get() = showErrors && step == PublishStep.CATEGORY && draft.category == null

    val showLocationError: Boolean get() = showErrors && step == PublishStep.LOCATION && draft.location == null

    /** 18 · Los errores de días y horas junto a cada campo, tras tocar «Continuar». */
    fun showScheduleError(field: DraftField): Boolean = showErrors && step == PublishStep.SCHEDULE && field in stepErrors

    /** 18 · «La hora de cierre debe ser posterior»: se ve en cuanto las dos horas están, sin esperar a «Continuar». */
    val closesBeforeOpens: Boolean get() = !draft.hoursUnknown && !draft.hours.closesAfterOpens

    val showPhotosError: Boolean get() = showErrors && step == PublishStep.PHOTOS && draft.photos.size < PhotoRules.MIN
}

/** Lo que el formulario necesita del mapa (17): el pin, su dirección, la ubicación y los parecidos. */
class PublishPlaces(
    val duplicateFinder: DuplicateFinder,
    val addresses: AddressResolver,
    val locationProvider: LocationProvider,
    val connectivity: ConnectivityObserver,
    /** Donde empieza el pin sin ubicación ni borrador («el centro de la ciudad», 17.b). */
    val cityCenter: GeoPoint,
    /** La búsqueda por dirección no sale de la ciudad. */
    val cityBounds: GeoBounds,
)

/** Lo que el formulario necesita para las fotos y el envío (19, 20). */
class PublishDelivery(
    val photos: PhotoStore,
    val uploader: PhotoUploader,
    val outbox: PublicationOutbox,
)

class PublishViewModel(
    private val drafts: DraftRepository,
    private val suggester: CategorySuggester,
    private val publications: PublicationRepository,
    private val places: PublishPlaces,
    private val delivery: PublishDelivery,
    resubmitId: String?,
    private val initialStep: PublishStep,
    /** Guarda la foto que está tomando la cámara: Android puede cerrar la app mientras tanto. */
    private val savedState: SavedStateHandle,
    private val suggestionTimeout: Duration = SUGGESTION_TIMEOUT,
    private val saveDelay: Duration = SAVE_DELAY,
) : ViewModel() {

    private val key: DraftKey = resubmitId?.let { DraftKey.Resubmit(it) } ?: DraftKey.New
    private val resubmitId: String? = resubmitId

    private val _state = MutableStateFlow(PublishUiState(resubmit = resubmitId != null))
    val state: StateFlow<PublishUiState> = _state.asStateFlow()

    /** Donde abre el mapa del paso 3 si el pin aún no está puesto. */
    val cityCenter: GeoPoint get() = places.cityCenter

    private var saveJob: Job? = null
    private var suggestionJob: Job? = null
    private var addressJob: Job? = null
    private var searchJob: Job? = null
    private var nearbyJob: Job? = null
    private var sendJob: Job? = null
    private val uploadJobs = mutableMapOf<String, Job>()

    init {
        load()
        // Al volver la red: la dirección que se prometió (17) y las fotos que no pudieron subir (19).
        viewModelScope.launch {
            places.connectivity.isOnline.drop(1).filter { it }.collect {
                val state = _state.value
                val location = state.draft.location
                if (state.address == PinAddress.Offline && location != null) resolveAddress(location)
                state.draft.photos.filter { state.uploads[it.id] == PhotoUpload.Failed }.forEach(::startUpload)
            }
        }
    }

    fun onRetry() = load()

    fun onTitleChange(title: String) = updateDraft { it.copy(title = title) }

    fun onDescriptionChange(description: String) = updateDraft { it.copy(description = description) }

    fun onTitleBlur() = _state.update { it.copy(titleTouched = true) }

    fun onDescriptionBlur() = _state.update { it.copy(descriptionTouched = true) }

    /**
     * Elegir una categoría a mano cancela la sugerencia en curso; elegir otra distinta de la sugerida la reemplaza
     * («Cambiaste la categoría sugerida») y volver a la sugerida la recupera.
     */
    fun onCategoryChange(category: Category) {
        suggestionJob?.cancel()
        val suggestion = when (val current = _state.value.suggestion) {
            is Suggestion.Ready -> if (category == current.category) current else Suggestion.Replaced(current.category)
            is Suggestion.Replaced -> if (category == current.suggested) Suggestion.Ready(category) else current
            Suggestion.Searching -> Suggestion.Idle
            Suggestion.Idle, Suggestion.NoAnswer, Suggestion.NotFound -> current
        }
        val origin = if (suggestion is Suggestion.Ready) CategoryOrigin.SUGGESTED else CategoryOrigin.CHOSEN
        _state.update { it.copy(suggestion = suggestion) }
        updateDraft { it.copy(category = category, categoryOrigin = origin) }
    }

    /** 16.c · «Intentar la sugerencia otra vez»: la persona la pidió, así que si llega, se aplica. */
    fun onRetrySuggestion() = startSuggestion(overrideChoice = true)

    /**
     * «Continuar» («Confirmar ubicación» en el paso 3): con algo pendiente muestra el resumen (21) y lleva el foco al
     * primer error; si no, avanza. En el paso 3 antes busca parecidos cerca del pin.
     */
    fun onContinue() {
        val state = _state.value
        if (state.content != PublishContent.Editing || state.searchingNearby || state.sending) return
        if (state.stepErrors.isNotEmpty()) {
            _state.update {
                it.copy(showErrors = true, titleTouched = true, descriptionTouched = true, errorFocusRequest = it.errorFocusRequest + 1)
            }
            return
        }
        val next = state.step.next
        when {
            next == null -> send()
            state.step == PublishStep.LOCATION && !state.draft.duplicatesChecked -> searchNearby()
            else -> moveTo(next)
        }
    }

    /** «Atrás»: al paso anterior (también mientras busca parecidos); en el primero equivale a cerrar. */
    fun onBack() {
        if (_state.value.sending) return
        val previous = _state.value.step.previous ?: return onClose()
        nearbyJob?.cancel()
        _state.update { it.copy(searchingNearby = false) }
        moveTo(previous)
    }

    /**
     * 17 · La persona puso el pin en [point]: al soltarlo tras arrastrar el mapa, con los botones de ajuste fino o al
     * elegir un resultado. Con [moveMap] el mapa lo sigue. Se guarda con el borrador y se busca su dirección.
     */
    fun onPinMoved(point: GeoPoint, moveMap: Boolean = false) {
        if (_state.value.content != PublishContent.Editing) return
        if (moveMap) _state.update { it.copy(pinTarget = point) }
        if (point == _state.value.draft.location) return
        // Los parecidos que se buscaban eran los del punto anterior.
        nearbyJob?.cancel()
        _state.update { it.copy(searchingNearby = false) }
        updateDraft { it.copy(location = point) }
        resolveAddress(point)
    }

    /** El mapa ya llevó el pin a [target]; si mientras tanto llegó otro destino, ese sigue pendiente. */
    fun onPinTargetShown(target: GeoPoint) = _state.update { if (it.pinTarget == target) it.copy(pinTarget = null) else it }

    /** «Usar mi ubicación» con el permiso concedido (o al entrar al paso 3 si ya lo estaba). */
    fun onUseMyLocation() {
        _state.update { it.copy(locationDenied = false) }
        viewModelScope.launch {
            val here = try {
                places.locationProvider.currentLocation()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Sin ubicación el pin sigue donde estaba y se puede mover a mano.
                null
            }
            if (here != null) onPinMoved(here, moveMap = true)
        }
    }

    /** 17.b · Se negó el permiso: el pin queda donde está y aparecen el aviso y la búsqueda por dirección. */
    fun onLocationDenied() = _state.update { it.copy(locationDenied = true) }

    fun onAddressQueryChange(query: String) = _state.update {
        it.copy(addressQuery = query, addressSearch = if (it.addressSearch == AddressSearch.Searching) it.addressSearch else AddressSearch.Idle)
    }

    /** 17.b · Busca lo escrito dentro de la ciudad y, si lo encuentra, lleva el pin allí. */
    fun onSearchAddress() {
        val query = _state.value.addressQuery.trim()
        if (query.isEmpty() || _state.value.addressSearch == AddressSearch.Searching) return
        searchJob?.cancel()
        _state.update { it.copy(addressSearch = AddressSearch.Searching) }
        searchJob = viewModelScope.launch {
            val outcome = try {
                withTimeoutOrNull(ADDRESS_TIMEOUT) { Located(places.addresses.search(query, places.cityBounds)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: OfflineException) {
                _state.update { it.copy(addressSearch = AddressSearch.Offline) }
                return@launch
            } catch (e: Exception) {
                null
            }
            val point = outcome?.point
            _state.update {
                it.copy(
                    addressSearch = when {
                        outcome == null -> AddressSearch.Failed
                        point == null -> AddressSearch.NotFound(query)
                        else -> AddressSearch.Idle
                    },
                )
            }
            if (point != null) onPinMoved(point, moveMap = true)
        }
    }

    /** 17A · Deslizar la hoja hacia abajo o «Atrás»: vuelve al paso 3 con el pin donde estaba. */
    fun onDuplicatesDismiss() = _state.update { it.copy(duplicates = null) }

    /** 17A · «Es otro lugar, continuar» → 17B en la misma hoja. */
    fun onNotSamePlace() = _state.update { state -> state.copy(duplicates = state.duplicates?.copy(different = true)) }

    /** 17B · «Volver a los lugares parecidos» o la flecha. */
    fun onBackToSimilar() = _state.update { state -> state.copy(duplicates = state.duplicates?.copy(different = false)) }

    fun onDuplicateNoteChange(note: String) = _state.update { state -> state.copy(duplicates = state.duplicates?.copy(note = note)) }

    /** 17B · «Continuar»: paso 4 con la marca «posible duplicado» y la nota en el borrador. */
    fun onConfirmDifferent() {
        val review = _state.value.duplicates ?: return
        val location = _state.value.draft.location ?: return
        _state.update { it.copy(duplicates = null) }
        updateDraft { it.copy(duplicateCheck = DuplicateCheck(location, review.places.map(SimilarPlace::id), review.note.trim())) }
        moveTo(PublishStep.SCHEDULE)
    }

    /** 18 · Un día de atención: tocarlo lo marca o lo desmarca. */
    fun onDayToggle(day: DayOfWeek) = updateDraft { draft ->
        val days = draft.hours.days
        draft.copy(hours = draft.hours.copy(days = if (day in days) days - day else days + day))
    }

    fun onOpensChange(time: LocalTime) = updateDraft { it.copy(hours = it.hours.copy(opens = time)) }

    fun onClosesChange(time: LocalTime) = updateDraft { it.copy(hours = it.hours.copy(closes = time)) }

    /** 18 · «No tengo el horario exacto»: días y horas dejan de hacer falta (se conservan por si la desmarca). */
    fun onHoursUnknownChange(unknown: Boolean) = updateDraft { it.copy(hoursUnknown = unknown) }

    /** 18 · El precio es opcional: tocar el elegido lo quita. */
    fun onPriceChange(price: PriceRange) = updateDraft { it.copy(price = if (it.price == price) null else price) }

    /**
     * 19 · «Cámara»: dónde escribirá la foto la app de cámara; null si ya hay 5. Se recuerda en [savedState] porque
     * Android puede cerrar la app mientras la cámara está abierta.
     */
    fun onCameraShot(): String? {
        if (_state.value.draft.photosLeft == 0) return null
        return delivery.photos.newCameraShot().also { savedState[CAMERA_SHOT_KEY] = it }
    }

    /** 19 · Volvió de la cámara: con foto, se comprime y se sube; si canceló, no pasa nada. */
    fun onCameraResult(taken: Boolean) {
        val shot = savedState.remove<String>(CAMERA_SHOT_KEY) ?: return
        if (taken) importPhotos(listOf(shot))
    }

    /** 19 · No hay app de cámara: se explica y queda la galería. */
    fun onCameraUnavailable() {
        savedState.remove<String>(CAMERA_SHOT_KEY)
        _state.update { it.copy(photoProblem = PhotoProblem.NO_CAMERA) }
    }

    /** 19 · Fotos elegidas en la galería (el selector ya limita cuántas). */
    fun onGalleryPicked(uris: List<String>) = importPhotos(uris)

    fun onPhotoProblemShown() = _state.update { it.copy(photoProblem = null) }

    /** 19 · «Reintentar» de una foto que no pudo subir. */
    fun onRetryPhoto(id: String) {
        _state.value.draft.photos.firstOrNull { it.id == id }?.let(::startUpload)
    }

    /** 19 · Quitar una foto (o cancelar su subida): se borra también del teléfono. */
    fun onRemovePhoto(id: String) {
        val photo = _state.value.draft.photos.firstOrNull { it.id == id } ?: return
        uploadJobs.remove(id)?.cancel()
        _state.update { it.copy(uploads = it.uploads - id, sendError = null) }
        updateDraft { draft -> draft.copy(photos = draft.photos.filterNot { it.id == id }) }
        viewModelScope.launch { delivery.photos.delete(photo) }
    }

    /** 17A · «Ver este lugar»: el formulario queda intacto y la hoja vuelve al regresar. */
    fun onLeaveForPlace() = _state.update { it.copy(awayForPlace = true) }

    fun onBackFromPlace() = _state.update { it.copy(awayForPlace = false) }

    /** Cerrar (X): con algo escrito pregunta (15A); si no, sale. */
    fun onClose() {
        if (_state.value.sending) return
        if (_state.value.draft.hasContent && _state.value.content == PublishContent.Editing) {
            _state.update { it.copy(closeDialog = true) }
        } else {
            _state.update { it.copy(exit = PublishExit.Closed) }
        }
    }

    /** «Guardar» (barra o 15A): el borrador queda para seguir después desde Publicar. */
    fun onSaveAndClose() {
        viewModelScope.launch {
            saveJob?.cancel()
            nearbyJob?.cancel()
            val draft = _state.value.draft
            if (draft.hasContent) drafts.save(key, draft) else drafts.clear(key)
            _state.update { it.copy(closeDialog = false, exit = PublishExit.Closed) }
        }
    }

    /** 15A · «Descartar»: se borra lo escrito. */
    fun onDiscard() {
        viewModelScope.launch {
            saveJob?.cancel()
            suggestionJob?.cancel()
            // Una búsqueda que terminara después guardaría otra vez el borrador.
            nearbyJob?.cancel()
            searchJob?.cancel()
            uploadJobs.values.forEach(Job::cancel)
            drafts.clear(key)
            // Las fotos comprimidas del borrador tampoco se necesitan ya.
            _state.value.draft.photos.forEach { delivery.photos.delete(it) }
            _state.update { it.copy(closeDialog = false, exit = PublishExit.Closed) }
        }
    }

    /** 15A · «Seguir editando». */
    fun onKeepEditing() = _state.update { it.copy(closeDialog = false) }

    fun onExitHandled() = _state.update { it.copy(exit = null) }

    private fun moveTo(step: PublishStep) {
        _state.update { it.copy(showErrors = false) }
        updateDraft { it.copy(step = step) }
        if (step == PublishStep.CATEGORY && _state.value.draft.category == null) startSuggestion(overrideChoice = false)
        val location = _state.value.draft.location
        if (step == PublishStep.LOCATION && location != null && _state.value.address == null) resolveAddress(location)
    }

    /** 17 · La dirección aproximada del pin; la anterior se descarta si el pin se movió. */
    private fun resolveAddress(point: GeoPoint) {
        addressJob?.cancel()
        _state.update { it.copy(address = PinAddress.Loading) }
        addressJob = viewModelScope.launch {
            val address = try {
                withTimeoutOrNull(ADDRESS_TIMEOUT) { places.addresses.addressOf(point)?.let { PinAddress.Found(it) } ?: PinAddress.NotFound }
            } catch (e: CancellationException) {
                throw e
            } catch (e: OfflineException) {
                PinAddress.Offline
            } catch (e: Exception) {
                null
            }
            _state.update { it.copy(address = address ?: PinAddress.Unavailable) }
        }
    }

    /**
     * 17 · Busca parecidos a menos de 50 m, como máximo 500 ms: con parecidos abre 17A; sin ellos, o si falla o tarda,
     * pasa al paso 4 sin aviso (el fallo queda en el borrador para que el servidor repita la búsqueda).
     */
    private fun searchNearby() {
        val draft = _state.value.draft
        val location = draft.location ?: return
        _state.update { it.copy(searchingNearby = true) }
        nearbyJob = viewModelScope.launch {
            val found = try {
                withTimeoutOrNull(NEARBY_TIMEOUT) { places.duplicateFinder.similarPlaces(draft.title.trim(), location) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            _state.update { it.copy(searchingNearby = false) }
            when {
                found.isNullOrEmpty() -> {
                    updateDraft { it.copy(duplicateCheck = DuplicateCheck(location, failed = found == null)) }
                    moveTo(PublishStep.SCHEDULE)
                }
                else -> {
                    // Si ya había escrito una nota para este lugar (volvió a moverlo), se conserva.
                    val note = draft.duplicateCheck?.note.orEmpty()
                    _state.update { it.copy(duplicates = DuplicateReview(found, note = note)) }
                }
            }
        }
    }

    /** 19 · Comprime y agrega las fotos (hasta 5) y empieza a subirlas; espera al borrador si aún se está leyendo. */
    private fun importPhotos(uris: List<String>) {
        if (uris.isEmpty()) return
        _state.update { it.copy(photoProblem = null, preparingPhotos = it.preparingPhotos + uris.size) }
        viewModelScope.launch {
            _state.first { it.content == PublishContent.Editing }
            var unreadable = false
            for (uri in uris) {
                val photo = if (_state.value.draft.photosLeft > 0) delivery.photos.import(uri) else null
                _state.update { it.copy(preparingPhotos = it.preparingPhotos - 1) }
                if (photo == null) {
                    // Más de 5: el selector ya las limita; si pasa, sobran en silencio.
                    if (_state.value.draft.photosLeft > 0) unreadable = true
                    continue
                }
                updateDraft { it.copy(photos = it.photos + photo) }
                _state.update { it.copy(sendError = null) }
                startUpload(photo)
            }
            if (unreadable) _state.update { it.copy(photoProblem = PhotoProblem.UNREADABLE) }
        }
    }

    /** 19 · Sube una foto informando el progreso; al terminar guarda su dirección en el borrador. */
    private fun startUpload(photo: DraftPhoto) {
        uploadJobs.remove(photo.id)?.cancel()
        setUpload(photo.id, PhotoUpload.Uploading(0))
        uploadJobs[photo.id] = viewModelScope.launch {
            try {
                delivery.uploader.upload(photo).collect { progress ->
                    when (progress) {
                        is UploadProgress.Sending -> setUpload(photo.id, PhotoUpload.Uploading(progress.percent))
                        is UploadProgress.Done -> {
                            setUpload(photo.id, null)
                            updateDraft { draft ->
                                draft.copy(photos = draft.photos.map { if (it.id == photo.id) it.copy(remoteUrl = progress.url) else it })
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                setUpload(photo.id, PhotoUpload.Failed)
            }
        }
    }

    private fun setUpload(id: String, upload: PhotoUpload?) = _state.update {
        it.copy(uploads = if (upload == null) it.uploads - id else it.uploads + (id to upload))
    }

    /**
     * 19 → 20 · «Enviar a verificación». Con una foto subida ya se puede enviar: si todas siguen subiendo, espera a la
     * primera; las que falten se suben después en segundo plano. Sin red, la publicación entera queda en la cola y se
     * envía al volver el internet. El borrador se borra cuando el servidor la recibe o cuando ya está a salvo en la cola.
     */
    private fun send() {
        _state.update { it.copy(sending = true, sendError = null) }
        sendJob = viewModelScope.launch {
            val ready = _state.first { state ->
                state.preparingPhotos == 0 &&
                    (state.draft.photos.any(DraftPhoto::uploaded) || state.draft.photos.none { state.uploads[it.id] is PhotoUpload.Uploading })
            }
            val draft = ready.draft
            val submission = PublicationSubmission.from(draft, resubmitId)
            if (submission == null) {
                // La foto que se esperaba no se pudo leer: queda el resumen de 21.
                _state.update { it.copy(sending = false, showErrors = true, errorFocusRequest = it.errorFocusRequest + 1) }
                return@launch
            }
            val uploaded = draft.photos.filter(DraftPhoto::uploaded)
            if (uploaded.isEmpty()) {
                if (places.connectivity.isOnline.value) {
                    _state.update { it.copy(sending = false, sendError = SendError.NO_PHOTO_UPLOADED) }
                } else {
                    queue(submission)
                }
                return@launch
            }
            val result = try {
                publications.submit(submission.copy(photos = uploaded))
            } catch (e: CancellationException) {
                throw e
            } catch (e: OfflineException) {
                queue(submission)
                return@launch
            } catch (e: Exception) {
                _state.update { it.copy(sending = false, sendError = SendError.FAILED) }
                return@launch
            }
            // Las que no alcanzaron a subir siguen en segundo plano, con su archivo.
            val later = draft.photos.filterNot(DraftPhoto::uploaded)
            later.forEach { uploadJobs.remove(it.id)?.cancel() }
            delivery.outbox.enqueuePhotos(result.publicationId, later)
            finish(SentSummary(submission.title, submission.possibleDuplicate, queued = false, result.firstPublicationPoints))
            uploaded.forEach { delivery.photos.delete(it) }
        }
    }

    /** Sin red: la publicación completa, con sus fotos del teléfono, pasa a la cola de envío (B1 de Daniel). */
    private suspend fun queue(submission: PublicationSubmission) {
        uploadJobs.values.forEach(Job::cancel)
        uploadJobs.clear()
        delivery.outbox.enqueue(submission)
        finish(SentSummary(submission.title, submission.possibleDuplicate, queued = true))
    }

    private suspend fun finish(summary: SentSummary) {
        saveJob?.cancel()
        drafts.clear(key)
        _state.update { it.copy(sending = false, exit = PublishExit.Sent(summary)) }
    }

    /** Cada cambio se guarda solo, un momento después de escribir («Guardamos tu borrador automáticamente»). */
    private fun updateDraft(change: (PublicationDraft) -> PublicationDraft) {
        if (_state.value.content != PublishContent.Editing) return
        val draft = change(_state.value.draft)
        _state.update { it.copy(draft = draft) }
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(saveDelay)
            drafts.save(key, draft)
        }
    }

    /**
     * 16 · Pide la sugerencia con tiempo límite de 5 s. La lista está activa desde el primer instante: si la persona
     * elige antes, la sugerencia se cancela ([onCategoryChange]); con [overrideChoice] (reintento) se aplica igual.
     */
    private fun startSuggestion(overrideChoice: Boolean) {
        suggestionJob?.cancel()
        _state.update { it.copy(suggestion = Suggestion.Searching) }
        val draft = _state.value.draft
        suggestionJob = viewModelScope.launch {
            val result = try {
                withTimeoutOrNull(suggestionTimeout) { Found(suggester.suggest(draft.title.trim(), draft.description.trim())) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            val category = result?.category
            when {
                result == null -> _state.update { it.copy(suggestion = Suggestion.NoAnswer) }
                category == null -> _state.update { it.copy(suggestion = Suggestion.NotFound) }
                !overrideChoice && _state.value.draft.categoryOrigin == CategoryOrigin.CHOSEN ->
                    _state.update { it.copy(suggestion = Suggestion.Idle) }
                else -> {
                    _state.update { it.copy(suggestion = Suggestion.Ready(category)) }
                    updateDraft { it.copy(category = category, categoryOrigin = CategoryOrigin.SUGGESTED) }
                }
            }
        }
    }

    private class Found(val category: Category?)

    private class Located(val point: GeoPoint?)

    private fun load() {
        _state.update { it.copy(content = PublishContent.Loading) }
        viewModelScope.launch {
            val saved = drafts.load(key)
            val draft = when {
                saved != null -> saved
                resubmitId == null -> PublicationDraft()
                else -> {
                    val publication = try {
                        publications.publication(resubmitId)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        null
                    }
                    if (publication == null) {
                        _state.update { it.copy(content = PublishContent.Error) }
                        return@launch
                    }
                    PublicationDraft.from(publication, initialStep)
                }
            }
            // Al retomar, una categoría sugerida conserva su aviso (16.b) aunque el estado de la sugerencia no se guarde.
            val category = draft.category
            val suggestion = if (category != null && draft.categoryOrigin == CategoryOrigin.SUGGESTED) Suggestion.Ready(category) else Suggestion.Idle
            _state.update { it.copy(content = PublishContent.Editing, draft = draft, suggestion = suggestion) }
            if (draft.step == PublishStep.CATEGORY && category == null) startSuggestion(overrideChoice = false)
            val location = draft.location
            if (draft.step == PublishStep.LOCATION && location != null) resolveAddress(location)
            // Las fotos que no alcanzaron a subir (se cerró la app) siguen donde quedaron.
            draft.photos.filterNot(DraftPhoto::uploaded).forEach(::startUpload)
        }
    }

    companion object {
        /** README · «tiempo límite 5 s»: después se sigue con la elección manual (16.c). */
        val SUGGESTION_TIMEOUT = 5.seconds

        /** README · Duplicados: «máx. 500 ms»; después se sigue al paso 4 sin aviso. */
        val NEARBY_TIMEOUT = 500.milliseconds

        /** La dirección del pin o la búsqueda por dirección: después se muestran solo las coordenadas. */
        val ADDRESS_TIMEOUT = 8.seconds

        /** Espera tras el último cambio antes de guardar el borrador: no escribe en disco con cada tecla. */
        val SAVE_DELAY = 400.milliseconds

        private const val CAMERA_SHOT_KEY = "foto_de_camara"

        val factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as ExploraApplication).container
                val savedState = createSavedStateHandle()
                val route = savedState.toRoute<PublishForm>()
                PublishViewModel(
                    drafts = container.draftRepository,
                    suggester = container.categorySuggester,
                    publications = container.publicationRepository,
                    places = PublishPlaces(
                        duplicateFinder = container.duplicateFinder,
                        addresses = container.addressResolver,
                        locationProvider = container.locationProvider,
                        connectivity = container.connectivity,
                        cityCenter = container.areaCenter,
                        cityBounds = container.areaBounds,
                    ),
                    delivery = PublishDelivery(container.photoStore, container.photoUploader, container.publicationOutbox),
                    resubmitId = route.resubmitId,
                    initialStep = PublishStep.of(route.step),
                    savedState = savedState,
                )
            }
        }
    }
}
