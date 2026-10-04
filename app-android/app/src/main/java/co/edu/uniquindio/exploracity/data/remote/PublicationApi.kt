package co.edu.uniquindio.exploracity.data.remote

import co.edu.uniquindio.exploracity.data.remote.dto.ChangesRequest
import co.edu.uniquindio.exploracity.data.remote.dto.PhotoDto
import co.edu.uniquindio.exploracity.data.remote.dto.PhotoUrlRequest
import co.edu.uniquindio.exploracity.data.remote.dto.PublicationDto
import co.edu.uniquindio.exploracity.data.remote.dto.SimilarPlaceDto
import co.edu.uniquindio.exploracity.data.remote.dto.SubmissionRequest
import co.edu.uniquindio.exploracity.data.remote.dto.SubmitDto
import co.edu.uniquindio.exploracity.data.remote.dto.SuggestionDto
import co.edu.uniquindio.exploracity.data.remote.dto.SuggestionRequest
import co.edu.uniquindio.exploracity.domain.model.Category
import co.edu.uniquindio.exploracity.domain.model.GeoPoint
import co.edu.uniquindio.exploracity.domain.model.OwnPublication
import co.edu.uniquindio.exploracity.domain.model.PublicationChanges
import co.edu.uniquindio.exploracity.domain.model.PublicationSubmission
import co.edu.uniquindio.exploracity.domain.model.PublishedPhoto
import co.edu.uniquindio.exploracity.domain.model.SimilarPlace
import co.edu.uniquindio.exploracity.domain.model.SubmitResult
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.onUpload
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

/** /v1/photos, /v1/places y /v1/publications · El formulario de publicación y las publicaciones propias (15–24). */
class PublicationApi(private val client: HttpClient) {

    /**
     * 19 · Sube una foto del formulario (JPEG, PNG o WebP de hasta 8 MB). [onProgress] recibe de 0 a 100 mientras se
     * envía. La foto queda sin publicación hasta que se envía o se edita una con su dirección.
     */
    suspend fun uploadPhoto(file: File, onProgress: suspend (Int) -> Unit): PublishedPhoto {
        val bytes = withContext(Dispatchers.IO) { file.readBytes() }
        return client.post("v1/photos") {
            setBody(photoForm(bytes, file.nameWithoutExtension))
            onUpload { sent, total -> if (total != null && total > 0) onProgress((sent * 100 / total).toInt()) }
        }.body<PhotoDto>().toDomain()
    }

    /** 16 · La categoría más probable, o null si no hay una clara. Si la IA no respondió, ApiException 503. */
    suspend fun suggestCategory(title: String, description: String): Category? =
        client.post("v1/places/suggest-category") { jsonBody(SuggestionRequest(title, description)) }.body<SuggestionDto>().category

    /** 17 · Hasta 3 lugares a 50 m de [near] con un título parecido, sin contar [excludeId]. */
    suspend fun similar(title: String, near: GeoPoint, excludeId: String?): List<SimilarPlace> = client.get("v1/places/similar") {
        parameter("title", title)
        parameter("near", "${near.latitude},${near.longitude}")
        excludeId?.let { parameter("excludeId", it) }
    }.body<List<SimilarPlaceDto>>().map { it.toDomain() }

    /** 20 · Envía a verificación, o reenvía una rechazada. Con un clientId que ya llegó responde la que estaba. */
    suspend fun submit(submission: PublicationSubmission): SubmitResult =
        client.post("v1/publications") { jsonBody(SubmissionRequest.of(submission)) }.body<SubmitDto>().toDomain()

    /** 22 · Las propias en cualquier estado, de la más reciente a la más antigua. */
    suspend fun mine(): List<OwnPublication> = client.get("v1/publications").body<List<PublicationDto>>().map { it.toDomain() }

    /** 24 · Una propia; null si ya no existe. */
    suspend fun one(id: String): OwnPublication? = try {
        client.get(publication(id)).body<PublicationDto>().toDomain()
    } catch (e: ApiException) {
        if (e.status == HttpStatusCode.NotFound.value) null else throw e
    }

    /** 23 · Vuelve a verificación. */
    suspend fun update(id: String, changes: PublicationChanges): OwnPublication =
        client.put(publication(id)) { jsonBody(ChangesRequest.of(changes)) }.body<PublicationDto>().toDomain()

    suspend fun delete(id: String) {
        client.delete(publication(id))
    }

    /** 19 · Una foto que terminó de subir después del envío va al final. */
    suspend fun addPhoto(id: String, url: String) {
        client.post("${publication(id)}/photos") { jsonBody(PhotoUrlRequest(url)) }
    }

    private fun publication(id: String) = "v1/publications/${id.encodeURLPathPart()}"
}
