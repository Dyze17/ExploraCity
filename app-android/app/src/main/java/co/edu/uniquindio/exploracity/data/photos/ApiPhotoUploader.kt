package co.edu.uniquindio.exploracity.data.photos

import co.edu.uniquindio.exploracity.data.remote.PublicationApi
import co.edu.uniquindio.exploracity.domain.model.DraftPhoto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import java.io.File

/**
 * Sube las fotos del formulario a la API, que las guarda en Cloudinary (SAD: Media Store). El progreso es el de los
 * bytes enviados: al 100 % falta solo la respuesta, con la dirección de la foto.
 */
class ApiPhotoUploader(private val api: PublicationApi) : PhotoUploader {
    // channelFlow: Ktor avisa el progreso desde otra corrutina, y un flow simple no puede emitir desde ahí.
    override fun upload(photo: DraftPhoto): Flow<UploadProgress> = channelFlow {
        send(UploadProgress.Sending(0))
        var last = 0
        val uploaded = api.uploadPhoto(File(photo.path)) { percent ->
            // Ktor avisa con cada bloque: solo cuando sube el porcentaje.
            if (percent > last) {
                last = percent
                send(UploadProgress.Sending(percent))
            }
        }
        send(UploadProgress.Done(uploaded.url))
    }
}
