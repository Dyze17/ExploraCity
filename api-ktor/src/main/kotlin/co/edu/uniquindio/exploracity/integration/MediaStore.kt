package co.edu.uniquindio.exploracity.integration

import co.edu.uniquindio.exploracity.config.CloudinarySettings
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.statement.HttpResponse
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.http.parameters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.File
import java.security.MessageDigest
import java.time.Clock
import java.util.UUID

/** Una imagen guardada: la dirección pública y el id para borrarla. */
data class StoredMedia(val url: String, val publicId: String)

/** Formatos de imagen que se aceptan (19 y 28), reconocidos por sus primeros bytes y no por lo que diga el cliente. */
enum class ImageType(val extension: String, val mime: String) {
    JPEG("jpg", "image/jpeg"),
    PNG("png", "image/png"),
    WEBP("webp", "image/webp"),
    ;

    companion object {
        private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

        fun detect(bytes: ByteArray): ImageType? = when {
            bytes.startsWith(0xFF, 0xD8, 0xFF) -> JPEG
            bytes.size >= PNG_SIGNATURE.size && bytes.copyOfRange(0, PNG_SIGNATURE.size).contentEquals(PNG_SIGNATURE) -> PNG
            // RIFF, cuatro bytes de largo y WEBP.
            bytes.size >= 12 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" &&
                String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP" -> WEBP
            else -> null
        }

        private fun ByteArray.startsWith(vararg prefix: Int): Boolean =
            size >= prefix.size && prefix.indices.all { this[it] == prefix[it].toByte() }
    }
}

/** El almacén de medios falló o no respondió. */
class MediaStoreException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** C1 · Almacén de fotos (ADR-07): Cloudinary con sus claves; sin ellas, una carpeta local. */
interface MediaStore {
    /** Guarda la imagen en [folder] («perfil», «lugares»). Lanza [MediaStoreException] si falla. */
    suspend fun upload(bytes: ByteArray, type: ImageType, folder: String): StoredMedia

    /** Borra la imagen; si ya no estaba, no pasa nada. Lanza [MediaStoreException] si el almacén falla. */
    suspend fun delete(publicId: String)
}

/**
 * Cloudinary por su API REST, con las peticiones firmadas: SHA-1 de los parámetros en orden alfabético seguidos del
 * secreto. La firma vence a la hora, por eso lleva la hora del [clock].
 */
class CloudinaryMediaStore(
    private val http: HttpClient,
    private val settings: CloudinarySettings,
    private val clock: Clock,
    private val baseUrl: String = "https://api.cloudinary.com/v1_1",
) : MediaStore {

    override suspend fun upload(bytes: ByteArray, type: ImageType, folder: String): StoredMedia {
        val timestamp = clock.instant().epochSecond.toString()
        val signed = mapOf("folder" to folder, "timestamp" to timestamp)
        val response = call {
            http.submitFormWithBinaryData(
                url = "$baseUrl/${settings.cloudName}/image/upload",
                formData = formData {
                    signed.forEach { (name, value) -> append(name, value) }
                    append("api_key", settings.apiKey)
                    append("signature", sign(signed, settings.apiSecret))
                    append(
                        "file",
                        bytes,
                        Headers.build {
                            append(HttpHeaders.ContentType, type.mime)
                            append(HttpHeaders.ContentDisposition, "filename=\"foto.${type.extension}\"")
                        },
                    )
                },
            )
        }
        val uploaded = response.body<CloudinaryUpload>()
        return StoredMedia(uploaded.secureUrl, uploaded.publicId)
    }

    override suspend fun delete(publicId: String) {
        val timestamp = clock.instant().epochSecond.toString()
        val signed = mapOf("public_id" to publicId, "timestamp" to timestamp)
        call {
            http.submitForm(
                url = "$baseUrl/${settings.cloudName}/image/destroy",
                formParameters = parameters {
                    signed.forEach { (name, value) -> append(name, value) }
                    append("api_key", settings.apiKey)
                    append("signature", sign(signed, settings.apiSecret))
                },
            )
        }
    }

    private suspend fun call(request: suspend () -> HttpResponse): HttpResponse {
        val response = try {
            request()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw MediaStoreException("Cloudinary no respondió", e)
        }
        if (!response.status.isSuccess()) throw MediaStoreException("Cloudinary respondió ${response.status.value}")
        return response
    }

    companion object {
        /** La firma de Cloudinary: «a=1&b=2» en orden alfabético más el secreto, en SHA-1 hexadecimal. */
        fun sign(params: Map<String, String>, secret: String): String {
            val text = params.toSortedMap().entries.joinToString("&") { (name, value) -> "$name=$value" } + secret
            return MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
        }
    }
}

@Serializable
private data class CloudinaryUpload(
    @SerialName("public_id") val publicId: String,
    @SerialName("secure_url") val secureUrl: String,
)

/**
 * Sin Cloudinary: las fotos van a [directory] (fuera de git) y la API las sirve en /media. Las direcciones empiezan
 * por [publicBaseUrl]; desde el teléfono, la IP del equipo en la red.
 */
class LocalMediaStore(val directory: File, private val publicBaseUrl: String) : MediaStore {

    override suspend fun upload(bytes: ByteArray, type: ImageType, folder: String): StoredMedia = withContext(Dispatchers.IO) {
        val publicId = "$folder/${UUID.randomUUID()}.${type.extension}"
        val file = fileOf(publicId)
        try {
            file.parentFile.mkdirs()
            file.writeBytes(bytes)
        } catch (e: Exception) {
            throw MediaStoreException("No se pudo guardar la foto en ${directory.path}", e)
        }
        StoredMedia("$publicBaseUrl/media/$publicId", publicId)
    }

    override suspend fun delete(publicId: String) {
        withContext(Dispatchers.IO) { fileOf(publicId).delete() }
    }

    /** El archivo de [publicId], sin salir nunca de [directory]. */
    private fun fileOf(publicId: String): File {
        val root = directory.canonicalFile
        val file = File(root, publicId).canonicalFile
        require(file.toPath().startsWith(root.toPath())) { "Id de foto fuera de la carpeta: $publicId" }
        return file
    }
}
