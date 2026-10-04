package co.edu.uniquindio.exploracity.data.remote

import co.edu.uniquindio.exploracity.data.remote.dto.OwnProfileDto
import co.edu.uniquindio.exploracity.data.remote.dto.ProfileUpdateRequest
import co.edu.uniquindio.exploracity.data.remote.dto.ReportRequest
import co.edu.uniquindio.exploracity.domain.model.OwnProfile
import co.edu.uniquindio.exploracity.domain.model.ReportReason
import co.edu.uniquindio.exploracity.domain.model.Residency
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.encodeURLPathPart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * /v1/profile y /v1/users · El perfil propio (26 y 28) y el reporte de un perfil (31A). Piden el token de acceso. El
 * perfil público (31) llega con el área de Explorar.
 */
class ProfileApi(private val client: HttpClient) {
    suspend fun ownProfile(): OwnProfile = client.get("v1/profile").body<OwnProfileDto>().toDomain()

    /** 28 · «Sobre mí» null o vacío se guarda como null. */
    suspend fun updateProfile(name: String, bio: String?, residency: Residency): OwnProfile = client.put("v1/profile") {
        jsonBody(ProfileUpdateRequest(name, bio, residency))
    }.body<OwnProfileDto>().toDomain()

    /** 28 · Sube la foto del teléfono (JPEG, PNG o WebP de hasta 8 MB) y reemplaza la anterior. */
    suspend fun uploadPhoto(file: File): OwnProfile {
        val bytes = withContext(Dispatchers.IO) { file.readBytes() }
        val type = PhotoType.of(bytes)
        return client.put("v1/profile/photo") {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append(
                            "photo",
                            bytes,
                            Headers.build {
                                append(HttpHeaders.ContentType, type.mime)
                                append(HttpHeaders.ContentDisposition, "filename=\"perfil.${type.extension}\"")
                            },
                        )
                    },
                ),
            )
        }.body<OwnProfileDto>().toDomain()
    }

    /** 28 · Sin foto, el perfil vuelve a las iniciales. */
    suspend fun removePhoto(): OwnProfile = client.delete("v1/profile/photo").body<OwnProfileDto>().toDomain()

    /** 31A · Reporte anónimo. */
    suspend fun reportUser(userId: String, reason: ReportReason) {
        client.post("v1/users/${userId.encodeURLPathPart()}/reports") { jsonBody(ReportRequest(reason)) }
    }
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
