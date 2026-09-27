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
    /** 17 · El mapa del paso 3: dirección, ubicación, búsqueda por dirección y parecidos (17A/17B). */
    val pin: PinState = PinState(),
    /** 19 · Cómo van las fotos que aún no están en el servidor. */
    val photoStatus: PhotoStatus = PhotoStatus(),
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
            PublishStep.PHOTOS -> listOfNotNull(DraftField.PHOTOS.takeIf { draft.photos.size < PhotoRules.MIN && photoStatus.preparing == 0 })
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
    private var sendJob: Job? = null

    /** 17 · El pin del paso 3, guardado en el borrador. */
    private val pin = PinPicker(
        scope = viewModelScope,
        places = places,
        read = { _state.value.pin },
        write = { change -> _state.update { it.copy(pin = change(it.pin)) } },
        location = { _state.value.draft.location },
        onLocationChange = { point -> updateDraft { it.copy(location = point) } },
        enabled = { _state.value.content == PublishContent.Editing },
    )

    /** 19 · Las fotos del paso 5, guardadas en el borrador. */
    private val photos = FormPhotos(
        scope = viewModelScope,
        store = delivery.photos,
        uploader = delivery.uploader,
        connectivity = places.connectivity,
        savedState = savedState,
        read = { _state.value.photoStatus },
        write = { change -> _state.update { it.copy(photoStatus = change(it.photoStatus)) } },
        photos = { _state.value.draft.photos },
        updatePhotos = { change -> updateDraft { it.copy(photos = change(it.photos)) } },
        awaitReady = { _state.first { it.content == PublishContent.Editing } },
        onChanged = { _state.update { it.copy(sendError = null) } },
    )

    init {
        load()
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
        if (state.content != PublishContent.Editing || state.pin.searchingNearby || state.sending) return
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
        pin.cancelNearby()
        moveTo(previous)
    }

    /** 17 · El pin quedó en [point] (arrastrar, ajuste fino o un resultado); con [moveMap] el mapa lo sigue. */
    fun onPinMoved(point: GeoPoint, moveMap: Boolean = false) = pin.onPinMoved(point, moveMap)

    fun onPinTargetShown(target: GeoPoint) = pin.onPinTargetShown(target)

    fun onUseMyLocation() = pin.onUseMyLocation()

    fun onLocationDenied() = pin.onLocationDenied()

    fun onAddressQueryChange(query: String) = pin.onAddressQueryChange(query)

    fun onSearchAddress() = pin.onSearchAddress()

    fun onDuplicatesDismiss() = pin.onDuplicatesDismiss()

    fun onNotSamePlace() = pin.onNotSamePlace()

    fun onBackToSimilar() = pin.onBackToSimilar()

    fun onDuplicateNoteChange(note: String) = pin.onDuplicateNoteChange(note)

    /** 17B · «Continuar»: paso 4 con la marca «posible duplicado» y la nota en el borrador. */
    fun onConfirmDifferent() = pin.onConfirmDifferent()

    fun onLeaveForPlace() = pin.onLeaveForPlace()

    fun onBackFromPlace() = pin.onBackFromPlace()

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

    /** 19 · «Cámara»: dónde escribirá la foto la app de cámara; null si ya hay 5. */
    fun onCameraShot(): String? = photos.onCameraShot()

    fun onCameraResult(taken: Boolean) = photos.onCameraResult(taken)

    fun onCameraUnavailable() = photos.onCameraUnavailable()

    fun onGalleryPicked(uris: List<String>) = photos.onGalleryPicked(uris)

    fun onRetryPhoto(id: String) = photos.onRetry(id)

    fun onRemovePhoto(id: String) = photos.onRemove(id)

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
            pin.cancelNearby()
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
            // Una búsqueda o una subida que terminara después guardaría otra vez el borrador.
            pin.cancelAll()
            photos.stopAll()
            drafts.clear(key)
            // Las fotos comprimidas del borrador tampoco se necesitan ya.
            photos.deleteFiles(_state.value.draft.photos)
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
        if (step == PublishStep.LOCATION && location != null && _state.value.pin.address == null) pin.resolveAddress(location)
    }

    /**
     * 17 · Busca parecidos del pin: con parecidos abre 17A; sin ellos, o si falla o tarda, pasa al paso 4 sin aviso (el
     * fallo queda en el borrador para que el servidor repita la búsqueda). Si ya había una nota para este lugar, se conserva.
     */
    private fun searchNearby() {
        val draft = _state.value.draft
        pin.searchNearby(draft.title, draft.duplicateCheck?.note.orEmpty(), excludeId = resubmitId) { check ->
            updateDraft { it.copy(duplicateCheck = check) }
            moveTo(PublishStep.SCHEDULE)
        }
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
                state.photoStatus.preparing == 0 &&
                    (state.draft.photos.any(DraftPhoto::uploaded) || state.draft.photos.none { state.photoStatus.uploads[it.id] is PhotoUpload.Uploading })
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
            photos.stop(later.map(DraftPhoto::id))
            delivery.outbox.enqueuePhotos(result.publicationId, later)
            finish(SentSummary(submission.title, submission.possibleDuplicate, queued = false, result.firstPublicationPoints))
            photos.deleteFiles(uploaded)
        }
    }

    /** Sin red: la publicación completa, con sus fotos del teléfono, pasa a la cola de envío (B1 de Daniel). */
    private suspend fun queue(submission: PublicationSubmission) {
        photos.stopAll()
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
            if (draft.step == PublishStep.LOCATION && location != null) pin.resolveAddress(location)
            // Las fotos que no alcanzaron a subir (se cerró la app) siguen donde quedaron.
            photos.uploadPending()
        }
    }

    companion object {
        /** README · «tiempo límite 5 s»: después se sigue con la elección manual (16.c). */
        val SUGGESTION_TIMEOUT = 5.seconds

        /** Espera tras el último cambio antes de guardar el borrador: no escribe en disco con cada tecla. */
        val SAVE_DELAY = 400.milliseconds

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
