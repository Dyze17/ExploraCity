package co.edu.uniquindio.exploracity.routes

import co.edu.uniquindio.exploracity.plugins.ApiException
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.contentLength
import io.ktor.server.request.receiveMultipart
import io.ktor.utils.io.readBuffer
import kotlinx.io.IOException
import kotlinx.io.readByteArray

/** 19 y 28 · Hasta 8 MB por foto. */
const val MAX_PHOTO_BYTES = 8L * 1024 * 1024

/** La foto del cuerpo multipart (la primera parte con archivo), hasta 8 MB. */
internal suspend fun ApplicationCall.receivePhoto(): ByteArray {
    val max = MAX_PHOTO_BYTES
    // El cuerpo entero trae, además de la foto, los separadores del multipart.
    request.contentLength()?.let { if (it > max + MULTIPART_OVERHEAD) throw photoTooLarge() }
    var photo: ByteArray? = null
    try {
        receiveMultipart(formFieldLimit = max + 1).forEachPart { part ->
            if (photo == null && part is PartData.FileItem) photo = part.provider().readBuffer(max + 1).readByteArray()
            part.release()
        }
    } catch (e: IOException) {
        // Ktor corta la lectura al pasar del límite.
        throw photoTooLarge()
    }
    val bytes = photo ?: throw ApiException.badRequest("photo_missing")
    if (bytes.size > max) throw photoTooLarge()
    return bytes
}

private fun photoTooLarge() = ApiException(HttpStatusCode.PayloadTooLarge, "photo_too_large")

private const val MULTIPART_OVERHEAD = 64L * 1024
