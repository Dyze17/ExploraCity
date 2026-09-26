package co.edu.uniquindio.exploracity.data.photos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import co.edu.uniquindio.exploracity.domain.model.DraftPhoto
import co.edu.uniquindio.exploracity.domain.model.PhotoRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

/** Las fotos del formulario en el teléfono (19): comprimidas antes de subirlas y guardadas hasta que se envían. */
interface PhotoStore {
    /** Comprime la foto de [uri] (de la galería o de la cámara) y la guarda; null si no se pudo leer. */
    suspend fun import(uri: String, fallbackName: String? = null): DraftPhoto?

    /** Dónde escribe la app de cámara la foto que se va a tomar; se importa después con [import]. */
    fun newCameraShot(): String

    /** Borra el archivo local: al quitar la foto, al descartar el borrador o cuando el servidor ya la tiene. */
    suspend fun delete(photo: DraftPhoto)
}

/**
 * Con ImageDecoder (respeta la orientación de la cámara y lee HEIC): lado mayor de 2048 px y JPEG al 85 %, que deja
 * cada foto muy por debajo de los 8 MB. Si aun así pasara del límite, se baja la calidad.
 */
class AndroidPhotoStore(context: Context) : PhotoStore {

    private val context = context.applicationContext
    private val folder get() = File(context.filesDir, "fotos-borrador").apply { mkdirs() }
    private val cameraFolder get() = File(context.cacheDir, "camara").apply { mkdirs() }

    override suspend fun import(uri: String, fallbackName: String?): DraftPhoto? = withContext(Dispatchers.IO) {
        val source = uri.toUri()
        try {
            val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, source)) { decoder, info, _ ->
                val scale = PhotoRules.MAX_SIDE_PX.toFloat() / max(info.size.width, info.size.height)
                if (scale < 1f) decoder.setTargetSize((info.size.width * scale).roundToInt(), (info.size.height * scale).roundToInt())
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            val id = UUID.randomUUID().toString()
            val file = File(folder, "$id.jpg")
            var quality = 85
            do {
                FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it) }
                quality -= 15
            } while (file.length() > PhotoRules.MAX_BYTES && quality > 20)
            bitmap.recycle()
            if (file.length() > PhotoRules.MAX_BYTES) {
                file.delete()
                return@withContext null
            }
            DraftPhoto(id = id, path = file.absolutePath, name = displayName(source) ?: fallbackName ?: file.name)
        } catch (e: IOException) {
            null
        } catch (e: SecurityException) {
            null
        } finally {
            // La foto de la cámara ya quedó copiada y comprimida.
            if (source.authority == authority) cameraFile(source)?.delete()
        }
    }

    override fun newCameraShot(): String {
        val name = "foto-${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))}.jpg"
        return FileProvider.getUriForFile(context, authority, File(cameraFolder, name)).toString()
    }

    override suspend fun delete(photo: DraftPhoto) {
        withContext(Dispatchers.IO) { File(photo.path).delete() }
    }

    private val authority get() = "${context.packageName}.fotos"

    private fun cameraFile(uri: Uri): File? = uri.lastPathSegment?.let { File(cameraFolder, it) }

    private fun displayName(uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
}
