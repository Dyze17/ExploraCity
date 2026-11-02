package co.edu.uniquindio.exploracity.features.explore

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import co.edu.uniquindio.exploracity.ExploraApplication
import co.edu.uniquindio.exploracity.core.data.connectivity.ConnectivityObserver
import co.edu.uniquindio.exploracity.core.data.connectivity.OfflineException
import co.edu.uniquindio.exploracity.core.data.photos.PhotoStore
import co.edu.uniquindio.exploracity.core.data.photos.PhotoUploader
import co.edu.uniquindio.exploracity.core.util.catchingNonCancellation
import co.edu.uniquindio.exploracity.domain.model.DraftPhoto
import co.edu.uniquindio.exploracity.domain.model.PlaceFullException
import co.edu.uniquindio.exploracity.domain.repository.ContributionRepository
import co.edu.uniquindio.exploracity.features.publish.FormPhotos
import co.edu.uniquindio.exploracity.features.publish.PhotoStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** v1.4.0 · Por qué no salieron las fotos; el aviso queda en la pantalla con las fotos. */
enum class AddPhotosError { FULL, OFFLINE, FAILED }

data class AddPhotosUiState(
    val photos: List<DraftPhoto> = emptyList(),
    val photoStatus: PhotoStatus = PhotoStatus(),
    val sending: Boolean = false,
    val error: AddPhotosError? = null,
    /** «Atrás» con fotos elegidas: pregunta antes de descartarlas. */
    val confirmDiscard: Boolean = false,
    /** Se enviaron (true) o se salió sin enviar (false); null mientras sigue abierta. */
    val done: Boolean? = null,
) {
    /** Con al menos una foto y todas en el servidor. */
    val canSend: Boolean get() = photos.isNotEmpty() && photos.all { it.uploaded } && !photoStatus.busy && !sending
}

/** Las fotos elegidas sobreviven si Android cierra la app; el archivo ya está en el teléfono y su dirección, si subió. */
@Serializable
private data class SavedPhoto(val id: String, val path: String, val name: String, val remoteUrl: String?)

/**
 * v1.4.0 · Agregar fotos a un lugar publicado (C1): las mismas cámara, galería, compresión y subida del formulario (19),
 * hasta las que quedan libres. Al enviarlas esperan a un moderador (D1). Necesita conexión.
 */
class AddPhotosViewModel(
    private val contributions: ContributionRepository,
    private val store: PhotoStore,
    uploader: PhotoUploader,
    connectivity: ConnectivityObserver,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /** Argumentos de la ruta AddPhotos(placeId, placeTitle, slots). */
    val placeId: String = checkNotNull(savedStateHandle[PLACE_ID_KEY]) { "Falta el id del lugar" }
    val placeTitle: String = savedStateHandle[PLACE_TITLE_KEY] ?: ""
    val slots: Int = savedStateHandle[SLOTS_KEY] ?: 0

    private val _state = MutableStateFlow(AddPhotosUiState(photos = restoredPhotos()))
    val state: StateFlow<AddPhotosUiState> = _state.asStateFlow()

    private val form = FormPhotos(
        scope = viewModelScope,
        store = store,
        uploader = uploader,
        connectivity = connectivity,
        savedState = savedStateHandle,
        read = { _state.value.photoStatus },
        write = { change -> _state.update { it.copy(photoStatus = change(it.photoStatus)) } },
        photos = { _state.value.photos },
        updatePhotos = { change -> updatePhotos(change(_state.value.photos)) },
        awaitReady = {},
        onChanged = { _state.update { it.copy(error = null) } },
        max = slots,
    )

    init {
        // Si Android cerró la app a mitad de camino, las que no alcanzaron a subir se suben ahora.
        form.uploadPending()
    }

    /** Cuántas más se pueden elegir. */
    val photosLeft: Int get() = (slots - _state.value.photos.size).coerceAtLeast(0)

    fun onCameraShot(): String? = form.onCameraShot()

    fun onCameraResult(taken: Boolean) = form.onCameraResult(taken)

    fun onCameraUnavailable() = form.onCameraUnavailable()

    fun onGalleryPicked(uris: List<String>) = form.onGalleryPicked(uris)

    fun onRetryPhoto(id: String) = form.onRetry(id)

    fun onRemovePhoto(id: String) = form.onRemove(id)

    /** «Enviar fotos»: van al lugar y esperan revisión. Si falla, las fotos quedan para intentar de nuevo. */
    fun onSend() {
        val state = _state.value
        if (!state.canSend) return
        _state.update { it.copy(sending = true, error = null) }
        viewModelScope.launch {
            val urls = state.photos.mapNotNull { it.remoteUrl }
            val error = catchingNonCancellation { contributions.addPhotos(placeId, urls) }.exceptionOrNull()
            if (error == null) {
                form.deleteFiles(state.photos)
                updatePhotos(emptyList())
                _state.update { it.copy(sending = false, done = true) }
                return@launch
            }
            val reason = when (error) {
                is PlaceFullException -> AddPhotosError.FULL
                is OfflineException -> AddPhotosError.OFFLINE
                else -> AddPhotosError.FAILED
            }
            _state.update { it.copy(sending = false, error = reason) }
        }
    }

    /** «Atrás»: sin fotos sale; con fotos, pregunta. Mientras se envían no se sale. */
    fun onBack() {
        val state = _state.value
        if (state.sending) return
        if (state.photos.isEmpty() && state.photoStatus.preparing == 0) _state.update { it.copy(done = false) } else _state.update { it.copy(confirmDiscard = true) }
    }

    fun onKeepEditing() = _state.update { it.copy(confirmDiscard = false) }

    /** «Descartar»: deja de subir y borra las fotos del teléfono. */
    fun onDiscard() {
        form.stopAll()
        form.deleteFiles(_state.value.photos)
        updatePhotos(emptyList())
        _state.update { it.copy(confirmDiscard = false, done = false) }
    }

    fun onErrorShown() = _state.update { it.copy(error = null) }

    private fun updatePhotos(photos: List<DraftPhoto>) {
        _state.update { it.copy(photos = photos) }
        savedStateHandle[PHOTOS_KEY] = Json.encodeToString(photos.map { SavedPhoto(it.id, it.path, it.name, it.remoteUrl) })
    }

    private fun restoredPhotos(): List<DraftPhoto> = savedStateHandle.get<String>(PHOTOS_KEY)
        ?.let { runCatching { Json.decodeFromString<List<SavedPhoto>>(it) }.getOrNull() }
        ?.map { DraftPhoto(it.id, it.path, it.name, it.remoteUrl) }
        .orEmpty()

    companion object {
        const val PLACE_ID_KEY = "placeId"
        const val PLACE_TITLE_KEY = "placeTitle"
        const val SLOTS_KEY = "slots"
        private const val PHOTOS_KEY = "fotos_agregadas"

        val factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as ExploraApplication).container
                AddPhotosViewModel(
                    container.contributionRepository,
                    container.photoStore,
                    container.photoUploader,
                    container.connectivity,
                    createSavedStateHandle(),
                )
            }
        }
    }
}
