package co.edu.uniquindio.exploracity.data.remote

import co.edu.uniquindio.exploracity.data.remote.dto.OwnProfileDto
import co.edu.uniquindio.exploracity.data.remote.dto.ProfileUpdateRequest
import co.edu.uniquindio.exploracity.data.remote.dto.PublicProfileDto
import co.edu.uniquindio.exploracity.data.remote.dto.ReportRequest
import co.edu.uniquindio.exploracity.domain.model.GeoPoint
import co.edu.uniquindio.exploracity.domain.model.OwnProfile
import co.edu.uniquindio.exploracity.domain.model.PublicProfile
import co.edu.uniquindio.exploracity.domain.model.ReportReason
import co.edu.uniquindio.exploracity.domain.model.Residency
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodeURLPathPart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** /v1/profile y /v1/users · El perfil propio (26 y 28), el perfil público (31) y su reporte (31A). Piden el token de acceso. */
class ProfileApi(private val client: HttpClient) {
    suspend fun ownProfile(): OwnProfile = client.get("v1/profile").body<OwnProfileDto>().toDomain()

    /** 28 · «Sobre mí» null o vacío se guarda como null. */
    suspend fun updateProfile(name: String, bio: String?, residency: Residency): OwnProfile = client.put("v1/profile") {
        jsonBody(ProfileUpdateRequest(name, bio, residency))
    }.body<OwnProfileDto>().toDomain()

    /** 28 · Sube la foto del teléfono (JPEG, PNG o WebP de hasta 8 MB) y reemplaza la anterior. */
    suspend fun uploadPhoto(file: File): OwnProfile {
        val bytes = withContext(Dispatchers.IO) { file.readBytes() }
        return client.put("v1/profile/photo") { setBody(photoForm(bytes, "perfil")) }.body<OwnProfileDto>().toDomain()
    }

    /** 28 · Sin foto, el perfil vuelve a las iniciales. */
    suspend fun removePhoto(): OwnProfile = client.delete("v1/profile/photo").body<OwnProfileDto>().toDomain()

    /**
     * 31 · Lo público de otra persona, con sus lugares en el formato del feed (distancias desde [near]). null si ya no
     * existe.
     */
    suspend fun publicProfile(userId: String, near: GeoPoint?): PublicProfile? = try {
        client.get("v1/users/${userId.encodeURLPathPart()}") {
            near?.let { parameter("near", "${it.latitude},${it.longitude}") }
        }.body<PublicProfileDto>().toDomain()
    } catch (e: ApiException) {
        if (e.status == HttpStatusCode.NotFound.value) null else throw e
    }

    /** 31A · Reporte anónimo. */
    suspend fun reportUser(userId: String, reason: ReportReason) {
        client.post("v1/users/${userId.encodeURLPathPart()}/reports") { jsonBody(ReportRequest(reason)) }
    }
}
