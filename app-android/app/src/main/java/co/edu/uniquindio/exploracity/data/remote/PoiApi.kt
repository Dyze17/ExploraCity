package co.edu.uniquindio.exploracity.data.remote

import co.edu.uniquindio.exploracity.data.remote.dto.CommentDto
import co.edu.uniquindio.exploracity.data.remote.dto.CommentRequest
import co.edu.uniquindio.exploracity.data.remote.dto.CommentsPageDto
import co.edu.uniquindio.exploracity.data.remote.dto.CountDto
import co.edu.uniquindio.exploracity.data.remote.dto.FeedPageDto
import co.edu.uniquindio.exploracity.data.remote.dto.MapAreaDto
import co.edu.uniquindio.exploracity.data.remote.dto.PlaceDetailsDto
import co.edu.uniquindio.exploracity.data.remote.dto.VisitDto
import co.edu.uniquindio.exploracity.data.remote.dto.VisitRequest
import co.edu.uniquindio.exploracity.data.remote.dto.VoteDto
import co.edu.uniquindio.exploracity.data.repository.CommentsPage
import co.edu.uniquindio.exploracity.data.repository.FeedPage
import co.edu.uniquindio.exploracity.data.repository.FeedQuery
import co.edu.uniquindio.exploracity.data.repository.MapArea
import co.edu.uniquindio.exploracity.domain.model.Comment
import co.edu.uniquindio.exploracity.domain.model.GeoBounds
import co.edu.uniquindio.exploracity.domain.model.GeoPoint
import co.edu.uniquindio.exploracity.domain.model.LocationScope
import co.edu.uniquindio.exploracity.domain.model.PoiDetails
import co.edu.uniquindio.exploracity.domain.model.VisitExperience
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodeURLPathPart

/**
 * /v1/places · Explorar y Social (7, 8, 9, 13, 14 y 14.b). Piden el token de acceso. [near] es la ubicación de la
 * persona: la API mide desde ahí la distancia y el orden; sin ella, desde el centro de la ciudad.
 */
class PoiApi(private val client: HttpClient) {
    suspend fun feed(query: FeedQuery, near: GeoPoint?, page: Int, pageSize: Int): FeedPage = client.get("v1/places") {
        criteria(query, near)
        parameter("page", page)
        parameter("pageSize", pageSize)
    }.body<FeedPageDto>().toDomain()

    suspend fun count(query: FeedQuery, near: GeoPoint?): Int =
        client.get("v1/places/count") { criteria(query, near) }.body<CountDto>().count

    suspend fun mapArea(query: FeedQuery, near: GeoPoint?, bounds: GeoBounds, limit: Int): MapArea = client.get("v1/places/map") {
        criteria(query, near)
        parameter("bounds", listOf(bounds.southwest.latitude, bounds.southwest.longitude, bounds.northeast.latitude, bounds.northeast.longitude).joinToString(","))
        parameter("limit", limit)
    }.body<MapAreaDto>().toDomain()

    /** null si el lugar ya no existe o dejó de ser público. */
    suspend fun details(id: String, near: GeoPoint?): PoiDetails? = orNullIfGone {
        client.get(place(id)) { near?.let { parameter("near", it.asParameter()) } }.body<PlaceDetailsDto>().toDomain()
    }

    /** El total de votos del lugar después del cambio. */
    suspend fun setVote(id: String, voted: Boolean): Int {
        val response = if (voted) client.put("${place(id)}/vote") else client.delete("${place(id)}/vote")
        return response.body<VoteDto>().votes
    }

    /** Los puntos que ganó con esta visita. */
    suspend fun visit(id: String, experience: VisitExperience): Int = client.put("${place(id)}/visit") {
        jsonBody(VisitRequest(experience.recommends, experience.text.trim().ifEmpty { null }, experience.showName))
    }.body<VisitDto>().pointsAwarded

    /** null si el lugar ya no existe. */
    suspend fun comments(id: String, cursor: String?, pageSize: Int): CommentsPage? = orNullIfGone {
        client.get("${place(id)}/comments") {
            cursor?.let { parameter("cursor", it) }
            parameter("pageSize", pageSize)
        }.body<CommentsPageDto>().toDomain()
    }

    suspend fun addComment(id: String, text: String, clientId: String): Comment = client.post("${place(id)}/comments") {
        jsonBody(CommentRequest(text, clientId))
    }.body<CommentDto>().toDomain()

    private fun place(id: String) = "v1/places/${id.encodeURLPathPart()}"

    /** 9 · Los criterios de la hoja de filtros y la búsqueda, en la consulta. */
    private fun HttpRequestBuilder.criteria(query: FeedQuery, near: GeoPoint?) {
        val filters = query.filters
        if (filters.categories.isNotEmpty()) parameter("categories", filters.categories.sorted().joinToString(",") { it.name })
        if (filters.scope == LocationScope.NEARBY) parameter("scope", "NEARBY")
        if (filters.verifiedOnly) parameter("verifiedOnly", true)
        query.text.trim().takeIf { it.isNotEmpty() }?.let { parameter("q", it) }
        near?.let { parameter("near", it.asParameter()) }
    }

    private fun GeoPoint.asParameter() = "$latitude,$longitude"

    private suspend fun <T> orNullIfGone(block: suspend () -> T): T? = try {
        block()
    } catch (e: ApiException) {
        if (e.status == HttpStatusCode.NotFound.value) null else throw e
    }
}
