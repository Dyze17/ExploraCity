package co.edu.uniquindio.exploracity.data.remote

import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders

/**
 * La foto como cuerpo multipart, en la parte «photo»: la API toma la primera parte con archivo (JPEG, PNG o WebP de
 * hasta 8 MB). [name] es el nombre del archivo sin la extensión.
 */
internal fun photoForm(bytes: ByteArray, name: String): MultiPartFormDataContent {
    val type = PhotoType.of(bytes)
    return MultiPartFormDataContent(
        formData {
            append(
                "photo",
                bytes,
                Headers.build {
                    append(HttpHeaders.ContentType, type.mime)
                    append(HttpHeaders.ContentDisposition, "filename=\"$name.${type.extension}\"")
                },
            )
        },
    )
}

/** El formato de la foto por sus primeros bytes; la API también lo revisa así. Las del teléfono se guardan en JPEG. */
private enum class PhotoType(val mime: String, val extension: String) {
    JPEG("image/jpeg", "jpg"),
    PNG("image/png", "png"),
    WEBP("image/webp", "webp"),
    ;

    companion object {
        fun of(bytes: ByteArray): PhotoType = when {
            bytes.size >= 4 && bytes[0] == 0x89.toByte() && String(bytes, 1, 3, Charsets.US_ASCII) == "PNG" -> PNG
            bytes.size >= 12 && String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP" -> WEBP
            else -> JPEG
        }
    }
}
