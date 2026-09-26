package co.edu.uniquindio.exploracity.data.photos

import co.edu.uniquindio.exploracity.data.connectivity.ConnectivityObserver
import co.edu.uniquindio.exploracity.data.connectivity.OfflineException
import co.edu.uniquindio.exploracity.domain.model.DraftPhoto
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.last
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

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

/**
 * Temporal hasta que exista la API: tarda [duration] por foto y avanza de 5 en 5 %. Sin red no empieza, y si la red se
 * corta a mitad de camino falla ahí, como una subida real.
 */
class FakePhotoUploader(
    private val connectivity: ConnectivityObserver,
    private val duration: Duration = 2.seconds,
) : PhotoUploader {
    override fun upload(photo: DraftPhoto): Flow<UploadProgress> = flow {
        for (percent in 0..100 step STEP) {
            if (!connectivity.isOnline.value) throw OfflineException()
            emit(UploadProgress.Sending(percent))
            if (percent < 100) delay(duration / (100 / STEP))
        }
        emit(UploadProgress.Done("fake://fotos/${photo.id}.jpg"))
    }

    private companion object {
        const val STEP = 5
    }
}
