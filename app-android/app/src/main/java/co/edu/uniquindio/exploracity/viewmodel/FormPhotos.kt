package co.edu.uniquindio.exploracity.viewmodel

import androidx.lifecycle.SavedStateHandle
import co.edu.uniquindio.exploracity.data.connectivity.ConnectivityObserver
import co.edu.uniquindio.exploracity.data.photos.PhotoStore
import co.edu.uniquindio.exploracity.data.photos.PhotoUploader
import co.edu.uniquindio.exploracity.data.photos.UploadProgress
import co.edu.uniquindio.exploracity.domain.model.DraftPhoto
import co.edu.uniquindio.exploracity.domain.model.PhotoRules
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/** 19 · Cómo van las fotos que aún no están en el servidor. */
data class PhotoStatus(
    /** Subidas en curso o fallidas, por id de foto; las que ya subieron no están aquí. */
    val uploads: Map<String, PhotoUpload> = emptyMap(),
    /** Fotos elegidas que aún se están comprimiendo («Preparando la foto…»). */
    val preparing: Int = 0,
    val problem: PhotoProblem? = null,
) {
    /** Aún se comprime o se sube alguna: «Enviar» y «Guardar» esperan. */
    val busy: Boolean get() = preparing > 0 || uploads.values.any { it is PhotoUpload.Uploading }
}

/**
 * 19 · Las fotos de un formulario (publicar, 19, y editar, 23): cámara y galería, compresión, subida con progreso,
 * error recuperable y reintento al volver la red. Cada formulario guarda la lista donde le corresponde ([photos],
 * [updatePhotos]) y el estado de las subidas en el suyo ([read], [write]).
 */
class FormPhotos(
    private val scope: CoroutineScope,
    private val store: PhotoStore,
    private val uploader: PhotoUploader,
    connectivity: ConnectivityObserver,
    /** Guarda la foto que está tomando la cámara: Android puede cerrar la app mientras tanto. */
    private val savedState: SavedStateHandle,
    private val read: () -> PhotoStatus,
    private val write: ((PhotoStatus) -> PhotoStatus) -> Unit,
    private val photos: () -> List<DraftPhoto>,
    private val updatePhotos: ((List<DraftPhoto>) -> List<DraftPhoto>) -> Unit,
    /** Espera a que el formulario esté listo: la foto de la cámara puede llegar mientras se lee el borrador. */
    private val awaitReady: suspend () -> Unit,
    /** Se agregó o se quitó una foto (el formulario borra, por ejemplo, un aviso de envío fallido). */
    private val onChanged: () -> Unit = {},
) {
    private val jobs = mutableMapOf<String, Job>()

    init {
        // Al volver la red, las fotos que no pudieron subir se reintentan solas.
        scope.launch {
            connectivity.isOnline.drop(1).filter { it }.collect {
                val status = read()
                photos().filter { status.uploads[it.id] == PhotoUpload.Failed }.forEach(::startUpload)
            }
        }
    }

    private val left: Int get() = (PhotoRules.MAX - photos().size).coerceAtLeast(0)

    /** «Cámara»: dónde escribirá la foto la app de cámara; null si ya hay 5. */
    fun onCameraShot(): String? {
        if (left == 0) return null
        write { it.copy(problem = null) }
        return store.newCameraShot().also { savedState[CAMERA_SHOT_KEY] = it }
    }

    /** Volvió de la cámara: con foto, se comprime y se sube; si canceló, no pasa nada. */
    fun onCameraResult(taken: Boolean) {
        val shot = savedState.remove<String>(CAMERA_SHOT_KEY) ?: return
        if (taken) import(listOf(shot))
    }

    /** No hay app de cámara: se explica y queda la galería. */
    fun onCameraUnavailable() {
        savedState.remove<String>(CAMERA_SHOT_KEY)
        write { it.copy(problem = PhotoProblem.NO_CAMERA) }
    }

    /** Fotos elegidas en la galería (el selector ya limita cuántas). */
    fun onGalleryPicked(uris: List<String>) = import(uris)

    /** «Reintentar» de una foto que no pudo subir. */
    fun onRetry(id: String) {
        photos().firstOrNull { it.id == id }?.let(::startUpload)
    }

    /**
     * Quitar una foto (o cancelar su subida). El archivo del teléfono se borra; una foto ya publicada (23) no tiene
     * archivo y el servidor la quita al guardar.
     */
    fun onRemove(id: String) {
        val photo = photos().firstOrNull { it.id == id } ?: return
        jobs.remove(id)?.cancel()
        write { it.copy(uploads = it.uploads - id) }
        updatePhotos { list -> list.filterNot { it.id == id } }
        onChanged()
        if (photo.path.isNotEmpty()) scope.launch { store.delete(photo) }
    }

    /** Sube las que aún no tienen dirección en el servidor (se cerró la app a mitad de camino). */
    fun uploadPending() = photos().filterNot(DraftPhoto::uploaded).forEach(::startUpload)

    /** Deja de subir [ids]: siguen en segundo plano o ya no hacen falta. */
    fun stop(ids: Collection<String>) = ids.forEach { jobs.remove(it)?.cancel() }

    fun stopAll() {
        jobs.values.forEach(Job::cancel)
        jobs.clear()
    }

    /** Borra del teléfono los archivos de [list] (las fotos ya publicadas no tienen). */
    fun deleteFiles(list: List<DraftPhoto>) {
        val local = list.filter { it.path.isNotEmpty() }
        if (local.isNotEmpty()) scope.launch { local.forEach { store.delete(it) } }
    }

    /** Comprime y agrega las fotos (hasta 5) y empieza a subirlas. */
    private fun import(uris: List<String>) {
        if (uris.isEmpty()) return
        write { it.copy(problem = null, preparing = it.preparing + uris.size) }
        scope.launch {
            awaitReady()
            var unreadable = false
            for (uri in uris) {
                val photo = if (left > 0) store.import(uri) else null
                write { it.copy(preparing = it.preparing - 1) }
                if (photo == null) {
                    // Más de 5: el selector ya las limita; si pasa, sobran en silencio.
                    if (left > 0) unreadable = true
                    continue
                }
                updatePhotos { it + photo }
                onChanged()
                startUpload(photo)
            }
            if (unreadable) write { it.copy(problem = PhotoProblem.UNREADABLE) }
        }
    }

    /** Sube una foto informando el progreso; al terminar guarda su dirección en el formulario. */
    private fun startUpload(photo: DraftPhoto) {
        jobs.remove(photo.id)?.cancel()
        setUpload(photo.id, PhotoUpload.Uploading(0))
        jobs[photo.id] = scope.launch {
            try {
                uploader.upload(photo).collect { progress ->
                    when (progress) {
                        is UploadProgress.Sending -> setUpload(photo.id, PhotoUpload.Uploading(progress.percent))
                        is UploadProgress.Done -> {
                            setUpload(photo.id, null)
                            updatePhotos { list -> list.map { if (it.id == photo.id) it.copy(remoteUrl = progress.url) else it } }
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

    private fun setUpload(id: String, upload: PhotoUpload?) = write {
        it.copy(uploads = if (upload == null) it.uploads - id else it.uploads + (id to upload))
    }

    private companion object {
        const val CAMERA_SHOT_KEY = "foto_de_camara"
    }
}
