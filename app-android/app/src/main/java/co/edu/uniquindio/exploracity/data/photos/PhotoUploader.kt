package co.edu.uniquindio.exploracity.data.photos

import co.edu.uniquindio.exploracity.domain.model.DraftPhoto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.last

/** Cómo va la subida de una foto. */
sealed interface UploadProgress {
    /** 0 a 100. */
    data class Sending(val percent: Int) : UploadProgress

    /** Subida: [url] es su dirección en el servidor de imágenes. */
    data class Done(val url: String) : UploadProgress
}
/** Sube las fotos del formulario (SAD: Media Store en Cloudinary, a través de la API). */
interface PhotoUploader {
    /** Progreso de la subida de [photo], hasta [UploadProgress.Done]. Lanza excepción si falla la red. */
    fun upload(photo: DraftPhoto): Flow<UploadProgress>
}

/** Sube [photo] y devuelve su dirección, sin seguir el progreso (el envío en segundo plano). */
suspend fun PhotoUploader.uploadNow(photo: DraftPhoto): String = (upload(photo).last() as UploadProgress.Done).url
