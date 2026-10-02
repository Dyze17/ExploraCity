package co.edu.uniquindio.exploracity.data.photos

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Temporal hasta que exista la API: hace de servidor para la foto de perfil (28). Copia la foto comprimida a una
 * carpeta propia y devuelve su ruta, que las pantallas muestran como cualquier foto del teléfono. Con la API la foto
 * se sube a Cloudinary (SAD) y la dirección será una URL.
 */
class LocalProfilePhotoHost(context: Context) {
    private val filesDir = context.applicationContext.filesDir
    private val folder get() = File(filesDir, "servidor-falso/perfil").apply { mkdirs() }

    suspend fun host(path: String): String = withContext(Dispatchers.IO) {
        // Una sola foto de perfil: la anterior ya no sirve.
        folder.listFiles()?.forEach { it.delete() }
        val copy = File(folder, "${UUID.randomUUID()}.jpg")
        File(path).copyTo(copy)
        copy.absolutePath
    }

    /** Al quitar la foto o eliminar la cuenta (30): el «servidor» no se queda con ella. */
    suspend fun clear() {
        withContext(Dispatchers.IO) { folder.listFiles()?.forEach { it.delete() } }
    }
}
