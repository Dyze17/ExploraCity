package co.edu.uniquindio.exploracity.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import co.edu.uniquindio.exploracity.ExploraApplication
import co.edu.uniquindio.exploracity.data.connectivity.ConnectivityObserver
import co.edu.uniquindio.exploracity.data.connectivity.OfflineException
import co.edu.uniquindio.exploracity.data.local.DraftJson
import co.edu.uniquindio.exploracity.data.repository.PublicationRepository
import co.edu.uniquindio.exploracity.domain.model.Category
import co.edu.uniquindio.exploracity.domain.model.CategoryOrigin
import co.edu.uniquindio.exploracity.domain.model.GeoPoint
import co.edu.uniquindio.exploracity.domain.model.OwnPublication
import co.edu.uniquindio.exploracity.domain.model.PriceRange
import co.edu.uniquindio.exploracity.domain.model.PublicationChanges
import co.edu.uniquindio.exploracity.domain.model.PublicationDraft
import co.edu.uniquindio.exploracity.domain.model.PublicationStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.DayOfWeek
import java.time.LocalTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** 23 · Estados de carga; siguen a los de la publicación rechazada (24). */
sealed interface EditContent {
    data object Loading : EditContent

    data class Loaded(val original: OwnPublication) : EditContent

    /** Ya no existe (se eliminó desde otro dispositivo, por ejemplo). */
    data object NotFound : EditContent

    /** Rechazada o finalizada: se corrige desde 24 o ya la cerró el moderador. */
    data object NotEditable : EditContent

    data object Error : EditContent

    /** Sin internet: la publicación no se guarda para ver sin conexión, como en 22 y 24. */
    data object Offline : EditContent
}

enum class SaveError {
    FAILED,

    /** Sin red no se encola: una edición en cola podría chocar con la revisión del moderador. */
    OFFLINE,

    /** Una foto nueva no terminó de subir: se reintenta o se quita antes de guardar. */
    PHOTOS,
}

/** 23 · «Cambiar ubicación» abierto: el mapa a pantalla completa con el pin donde quedaría. */
data class LocationEditor(val point: GeoPoint)

data class EditPublicationUiState(
    val content: EditContent = EditContent.Loading,
    /** Lo que se está editando; null hasta que carga. */
    val form: PublicationChanges? = null,
    /** El error de un campo se muestra al salir de él, no mientras se escribe (15.b). */
    val titleTouched: Boolean = false,
    val descriptionTouched: Boolean = false,
    val saving: Boolean = false,
    /** Snackbar, una vez; los cambios siguen en los campos. */
    val saveError: SaveError? = null,
    /** «¿Descartar los cambios?» abierto. */
    val discardDialog: Boolean = false,
    val delete: DeleteDialogState? = null,
    /** 23 · «Cambiar ubicación» abierto. */
    val locationEditor: LocationEditor? = null,
    /** 17 · El mapa del editor de ubicación, y la dirección que muestra la tarjeta de 23. */
    val pin: PinState = PinState(),
    /** 19 · Cómo van las fotos nuevas que aún no están en el servidor. */
    val photoStatus: PhotoStatus = PhotoStatus(),
    /** Terminó: se guardó o se eliminó (la pantalla vuelve a 22 con el aviso) o se salió sin cambios (null). */
    val done: Done? = null,
) {
    val original: OwnPublication? get() = (content as? EditContent.Loaded)?.original

    /** Hay algo distinto de lo publicado (sin contar espacios de los extremos ni un horario a medias sin usar). */
    val changed: Boolean
        get() {
            val form = form ?: return false
            val original = original ?: return false
            return form.trimmed() != PublicationChanges.of(original).trimmed()
        }

    /** Con fotos aún subiendo también se puede: «Guardar» las espera. */
    val canSave: Boolean get() = changed && form?.isValid == true && !saving

    val showTitleError: Boolean get() = titleTouched && (form?.titleMissing ?: 0) > 0

    val showDescriptionError: Boolean get() = descriptionTouched && (form?.descriptionMissing ?: 0) > 0

    /** 18 · En 23 no hay «Continuar»: sin la casilla, lo que falta del horario se marca en cuanto se ve. */
    fun showScheduleError(field: DraftField): Boolean {
        val form = form ?: return false
        if (form.hoursUnknown) return false
        return when (field) {
            DraftField.DAYS -> form.hours.days.isEmpty()
            DraftField.OPENS -> form.hours.opens == null
            DraftField.CLOSES -> form.hours.closes == null
            else -> false
        }
    }

    val closesBeforeOpens: Boolean get() = form?.let { !it.hoursUnknown && !it.hours.closesAfterOpens } == true
}

/** Cómo termina la edición. */
sealed interface Done {
    data class WithMessage(val message: PublicationMessage) : Done

    data object Left : Done
}

class EditPublicationViewModel(
    private val publications: PublicationRepository,
    private val connectivity: ConnectivityObserver,
    private val savedStateHandle: SavedStateHandle,
    places: PublishPlaces,
    delivery: PublishDelivery,
    private val timeout: Duration = LOAD_TIMEOUT,
) : ViewModel() {

    /** Argumento de la ruta EditPublication(publicationId). */
    val publicationId: String = checkNotNull(savedStateHandle[PUBLICATION_ID_KEY]) { "Falta el id de la publicación" }

    private val _state = MutableStateFlow(
        EditPublicationUiState(
            titleTouched = savedStateHandle[TITLE_TOUCHED_KEY] ?: false,
            descriptionTouched = savedStateHandle[DESCRIPTION_TOUCHED_KEY] ?: false,
            discardDialog = savedStateHandle[DISCARD_KEY] ?: false,
            delete = if (savedStateHandle.get<Boolean>(DELETE_KEY) == true) DeleteDialogState() else null,
        ),
    )
    val state: StateFlow<EditPublicationUiState> = _state.asStateFlow()

    /** Donde abre el mapa si la publicación no tuviera ubicación (no pasa: todas tienen). */
    val cityCenter: GeoPoint = places.cityCenter

    private var loadJob: Job? = null
    private var saveJob: Job? = null

    /** 17 · El pin del editor de ubicación: se mueve sobre [LocationEditor.point] y no toca el formulario hasta confirmar. */
    private val pin = PinPicker(
        scope = viewModelScope,
        places = places,
        read = { _state.value.pin },
        write = { change -> _state.update { it.copy(pin = change(it.pin)) } },
        location = { _state.value.locationEditor?.point ?: _state.value.form?.location },
        onLocationChange = { point -> _state.update { it.copy(locationEditor = it.locationEditor?.copy(point = point)) } },
        enabled = { _state.value.locationEditor != null },
    )

    /** 19 · Las fotos del formulario: las ya publicadas y las nuevas. */
    private val photos = FormPhotos(
        scope = viewModelScope,
        store = delivery.photos,
        uploader = delivery.uploader,
        connectivity = connectivity,
        savedState = savedStateHandle,
        read = { _state.value.photoStatus },
        write = { change -> _state.update { it.copy(photoStatus = change(it.photoStatus)) } },
        photos = { _state.value.form?.photos.orEmpty() },
        updatePhotos = { change -> updateForm { it.copy(photos = change(it.photos)) } },
        awaitReady = { _state.first { it.form != null } },
        onChanged = { _state.update { it.copy(saveError = null) } },
    )

    init {
        load()
        viewModelScope.launch {
            // Al volver la red se carga sola si no se pudo traer.
            connectivity.isOnline.drop(1).collect { online ->
                val content = _state.value.content
                if (online && (content is EditContent.Offline || content is EditContent.Error)) load()
            }
        }
    }

    fun onRetry() = load()

    fun onTitleChange(title: String) = updateForm { it.copy(title = title) }

    fun onDescriptionChange(description: String) = updateForm { it.copy(description = description) }

    fun onCategoryChange(category: Category) = updateForm { it.copy(category = category) }

    /** Salió del campo: desde ahora su error se ve (y se actualiza mientras escribe). */
    fun onTitleBlur() {
        savedStateHandle[TITLE_TOUCHED_KEY] = true
        _state.update { it.copy(titleTouched = true) }
    }

    fun onDescriptionBlur() {
        savedStateHandle[DESCRIPTION_TOUCHED_KEY] = true
        _state.update { it.copy(descriptionTouched = true) }
    }

    // 18 · Horario y precio, como en el paso 4.

    fun onDayToggle(day: DayOfWeek) = updateForm { form ->
        val days = form.hours.days
        form.copy(hours = form.hours.copy(days = if (day in days) days - day else days + day))
    }

    fun onOpensChange(time: LocalTime) = updateForm { it.copy(hours = it.hours.copy(opens = time)) }

    fun onClosesChange(time: LocalTime) = updateForm { it.copy(hours = it.hours.copy(closes = time)) }

    fun onHoursUnknownChange(unknown: Boolean) = updateForm { it.copy(hoursUnknown = unknown) }

    /** El precio es opcional: tocar el elegido lo quita. */
    fun onPriceChange(price: PriceRange) = updateForm { it.copy(price = if (it.price == price) null else price) }

    // 19 · Fotos, como en el paso 5.

    fun onCameraShot(): String? = photos.onCameraShot()

    fun onCameraResult(taken: Boolean) = photos.onCameraResult(taken)

    fun onCameraUnavailable() = photos.onCameraUnavailable()

    fun onGalleryPicked(uris: List<String>) = photos.onGalleryPicked(uris)

    fun onRetryPhoto(id: String) = photos.onRetry(id)

    /** Quitar una foto: las publicadas se quitan del servidor al guardar; debe quedar al menos una. */
    fun onRemovePhoto(id: String) = photos.onRemove(id)

    // 17 · «Cambiar ubicación».

    /** Abre el mapa con el pin donde está la publicación. */
    fun onOpenLocationEditor() {
        val form = _state.value.form ?: return
        _state.update { it.copy(locationEditor = LocationEditor(form.location), pin = it.pin.copy(pinTarget = null, duplicates = null)) }
    }

    private fun closeLocationEditor() {
        pin.reset()
        _state.update { it.copy(locationEditor = null) }
    }

    /** «Atrás» en el mapa: vuelve a 23 sin cambiar la ubicación. */
    fun onCloseLocationEditor() {
        val form = _state.value.form ?: return
        val moved = _state.value.locationEditor?.point != form.location
        pin.reset()
        _state.update { it.copy(locationEditor = null) }
        // La tarjeta vuelve a mostrar la dirección de la ubicación que se queda.
        if (moved) pin.resolveAddress(form.location)
    }

    /**
     * «Usar esta ubicación». Si el pin no se movió de lo publicado, basta cerrar; si se movió, antes se buscan parecidos
     * a 50 m como al publicar (ADR-14): con parecidos aparecen 17A/17B y la marca queda con la edición.
     */
    fun onConfirmLocation() {
        val editor = _state.value.locationEditor ?: return
        val form = _state.value.form ?: return
        val original = _state.value.original ?: return
        if (_state.value.pin.searchingNearby) return
        when (editor.point) {
            form.location -> closeLocationEditor()
            original.location -> {
                updateForm { it.copy(location = original.location, duplicateCheck = null) }
                closeLocationEditor()
            }
            else -> pin.searchNearby(form.title, form.duplicateCheck?.note.orEmpty(), excludeId = publicationId) { check ->
                updateForm { it.copy(location = check.location, duplicateCheck = check) }
                closeLocationEditor()
            }
        }
    }

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

    fun onConfirmDifferent() = pin.onConfirmDifferent()

    fun onLeaveForPlace() = pin.onLeaveForPlace()

    fun onBackFromPlace() = pin.onBackFromPlace()

    /**
     * «Guardar»: solo con cambios válidos. Si alguna foto nueva sigue subiendo, espera; si una no pudo subir, lo dice y
     * no guarda. La publicación vuelve a verificación.
     */
    fun onSave() {
        val state = _state.value
        if (!state.canSave) return
        _state.update { it.copy(saving = true, saveError = null) }
        saveJob = viewModelScope.launch {
            val ready = _state.first { !it.photoStatus.busy }
            val form = ready.form ?: return@launch
            if (form.photos.any { !it.uploaded }) {
                // Sin red, la foto sube sola al volver: basta decir que no hay conexión.
                val error = if (connectivity.isOnline.value) SaveError.PHOTOS else SaveError.OFFLINE
                _state.update { it.copy(saving = false, saveError = error) }
                return@launch
            }
            val result = catchingNonCancellation { publications.update(publicationId, form.trimmed()) }
            if (result.isSuccess) {
                // Ya están en el servidor: los archivos del teléfono sobran.
                photos.deleteFiles(form.photos)
                _state.update { it.copy(saving = false, done = Done.WithMessage(PublicationMessage.SAVED)) }
            } else {
                val error = if (result.exceptionOrNull() is OfflineException) SaveError.OFFLINE else SaveError.FAILED
                _state.update { it.copy(saving = false, saveError = error) }
            }
        }
    }

    fun onSaveErrorShown() = _state.update { it.copy(saveError = null) }

    /** Volver (flecha o gesto): en el mapa lo cierra; con cambios pregunta; sin ellos, sale. */
    fun onBack() {
        if (_state.value.saving) return
        when {
            _state.value.locationEditor != null -> onCloseLocationEditor()
            _state.value.changed -> setDiscardDialog(true)
            else -> _state.update { it.copy(done = Done.Left) }
        }
    }

    /** «Descartar»: las fotos nuevas tampoco se necesitan. */
    fun onDiscard() {
        setDiscardDialog(false)
        photos.stopAll()
        photos.deleteFiles(_state.value.form?.photos.orEmpty())
        _state.update { it.copy(done = Done.Left) }
    }

    fun onKeepEditing() = setDiscardDialog(false)

    fun onOpenDelete() {
        if (_state.value.original == null) return
        updateDelete(DeleteDialogState())
    }

    fun onDismissDelete() {
        if (_state.value.delete?.deleting == true) return
        updateDelete(null)
    }

    fun onConfirmDelete() {
        val dialog = _state.value.delete ?: return
        if (dialog.deleting) return
        updateDelete(dialog.copy(deleting = true, error = null))
        viewModelScope.launch {
            val result = catchingNonCancellation { publications.delete(publicationId) }
            if (result.isSuccess) {
                updateDelete(null)
                photos.stopAll()
                photos.deleteFiles(_state.value.form?.photos.orEmpty())
                _state.update { it.copy(done = Done.WithMessage(PublicationMessage.DELETED)) }
            } else {
                val error = if (result.exceptionOrNull() is OfflineException) DeleteError.OFFLINE else DeleteError.FAILED
                updateDelete(DeleteDialogState(error = error))
            }
        }
    }

    fun onDoneHandled() = _state.update { it.copy(done = null) }

    private fun updateForm(change: (PublicationChanges) -> PublicationChanges) {
        val form = _state.value.form ?: return
        val updated = change(form)
        saveForm(updated)
        _state.update { it.copy(form = updated) }
    }

    /** Lo editado sobrevive si Android cierra la app: el formulario completo, como JSON. */
    private fun saveForm(form: PublicationChanges) {
        savedStateHandle[FORM_KEY] = DraftJson.encode(form.toDraft())
    }

    private fun restoredForm(original: OwnPublication): PublicationChanges =
        savedStateHandle.get<String>(FORM_KEY)?.let(DraftJson::decode)?.toChanges() ?: PublicationChanges.of(original)

    private fun setDiscardDialog(open: Boolean) {
        savedStateHandle[DISCARD_KEY] = open
        _state.update { it.copy(discardDialog = open) }
    }

    private fun updateDelete(delete: DeleteDialogState?) {
        savedStateHandle[DELETE_KEY] = delete != null
        _state.update { it.copy(delete = delete) }
    }

    private fun load() {
        loadJob?.cancel()
        _state.update { it.copy(content = EditContent.Loading) }
        loadJob = viewModelScope.launch {
            val content = try {
                // Envuelto para distinguir el tiempo agotado (null) de una publicación que ya no existe (Found(null)).
                val found = withTimeoutOrNull(timeout) { Found(publications.publication(publicationId)) }
                val publication = found?.publication
                when {
                    found == null -> EditContent.Error
                    publication == null -> EditContent.NotFound
                    publication.status !in editable -> EditContent.NotEditable
                    else -> EditContent.Loaded(publication)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: OfflineException) {
                EditContent.Offline
            } catch (e: Exception) {
                EditContent.Error
            }
            val firstLoad = _state.value.form == null
            _state.update { state ->
                val original = (content as? EditContent.Loaded)?.original
                // Lo que ya se estaba escribiendo no se pisa al reintentar ni al volver la red.
                val form = state.form ?: original?.let(::restoredForm)
                state.copy(content = content, form = form)
            }
            val form = _state.value.form
            if (firstLoad && form != null) {
                pin.resolveAddress(form.location)
                // Fotos nuevas que no alcanzaron a subir (Android cerró la app a mitad de camino).
                photos.uploadPending()
            }
        }
    }

    private class Found(val publication: OwnPublication?)

    companion object {
        /** Como en el feed: más de 8 s cargando → error recuperable. */
        val LOAD_TIMEOUT = 8.seconds

        const val PUBLICATION_ID_KEY = "publicationId"
        private const val FORM_KEY = "formulario"
        private const val TITLE_TOUCHED_KEY = "titulo_tocado"
        private const val DESCRIPTION_TOUCHED_KEY = "descripcion_tocada"
        private const val DISCARD_KEY = "descartar"
        private const val DELETE_KEY = "eliminar"

        /** Rechazadas se corrigen desde 24 (formulario) y las finalizadas las cerró el moderador. */
        private val editable = setOf(PublicationStatus.PENDING, PublicationStatus.VERIFIED)

        val factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as ExploraApplication).container
                EditPublicationViewModel(
                    publications = container.publicationRepository,
                    connectivity = container.connectivity,
                    savedStateHandle = createSavedStateHandle(),
                    places = PublishPlaces(
                        duplicateFinder = container.duplicateFinder,
                        addresses = container.addressResolver,
                        locationProvider = container.locationProvider,
                        connectivity = container.connectivity,
                        cityCenter = container.areaCenter,
                        cityBounds = container.areaBounds,
                    ),
                    delivery = PublishDelivery(container.photoStore, container.photoUploader, container.publicationOutbox),
                )
            }
        }
    }
}

/** El formulario de 23 en el formato del borrador, para guardarlo como JSON. */
private fun PublicationChanges.toDraft() = PublicationDraft(
    title = title,
    description = description,
    category = category,
    categoryOrigin = CategoryOrigin.CHOSEN,
    location = location,
    duplicateCheck = duplicateCheck,
    hours = hours,
    hoursUnknown = hoursUnknown,
    price = price,
    photos = photos,
)

private fun PublicationDraft.toChanges(): PublicationChanges? {
    val category = category ?: return null
    val location = location ?: return null
    return PublicationChanges(title, category, description, location, hours, hoursUnknown, price, photos, duplicateCheck)
}
