package co.edu.uniquindio.exploracity.routes

import co.edu.uniquindio.exploracity.model.Category
import co.edu.uniquindio.exploracity.model.CommentRequest
import co.edu.uniquindio.exploracity.model.GeoBounds
import co.edu.uniquindio.exploracity.model.GeoPoint
import co.edu.uniquindio.exploracity.model.VisitRequest
import co.edu.uniquindio.exploracity.plugins.ACCESS
import co.edu.uniquindio.exploracity.plugins.ApiException
import co.edu.uniquindio.exploracity.plugins.user
import co.edu.uniquindio.exploracity.repository.PlaceFilter
import co.edu.uniquindio.exploracity.service.PlaceService
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route

/**
 * /v1/places · Explorar y Social (7, 8, 9, 10, 13, 14 y 14.b). Los criterios van en la consulta:
 * `categories=NATURE,CULTURE`, `scope=NEARBY|CITY`, `verifiedOnly=true`, `q=texto` y `near=lat,lng` (la ubicación de la
 * persona; sin ella, el centro de la ciudad).
 */
fun Route.placeRoutes(places: PlaceService) {
    authenticate(ACCESS) {
        route("/places") {
            get {
                val query = call.request.queryParameters
                val page = query.int("page", default = 0, range = 0..MAX_PAGE)
                val pageSize = query.int("pageSize", default = FEED_PAGE_SIZE, range = 1..MAX_PAGE_SIZE)
                call.respond(places.feed(query.filter(), query.near(), page, pageSize))
            }
            get("/count") {
                val query = call.request.queryParameters
                call.respond(places.count(query.filter(), query.near()))
            }
            get("/map") {
                val query = call.request.queryParameters
                val bounds = query.bounds() ?: throw invalidQuery()
                val limit = query.int("limit", default = MAP_LIMIT, range = 1..MAP_LIMIT)
                call.respond(places.map(query.filter().copy(bounds = bounds), query.near(), limit))
            }
            route("/{id}") {
                get {
                    call.respond(places.details(call.user().id, call.placeId(), call.request.queryParameters.near()))
                }
                put("/vote") {
                    call.respond(places.vote(call.user().id, call.placeId(), voted = true))
                }
                delete("/vote") {
                    call.respond(places.vote(call.user().id, call.placeId(), voted = false))
                }
                put("/visit") {
                    call.respond(places.visit(call.user().id, call.placeId(), call.receive<VisitRequest>()))
                }
                get("/comments") {
                    val query = call.request.queryParameters
                    val pageSize = query.int("pageSize", default = COMMENTS_PAGE_SIZE, range = 1..MAX_PAGE_SIZE)
                    call.respond(places.comments(call.user().id, call.placeId(), query["cursor"], pageSize))
                }
                post("/comments") {
                    val (comment, created) = places.addComment(call.user().id, call.placeId(), call.receive<CommentRequest>())
                    call.respond(if (created) HttpStatusCode.Created else HttpStatusCode.OK, comment)
                }
            }
        }
    }
}

private fun ApplicationCall.placeId(): String = parameters["id"].orEmpty()

/** 9 · Los criterios de la hoja de filtros y la búsqueda. */
private fun Parameters.filter(): PlaceFilter {
    val categories = this["categories"]?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.map { name ->
        Category.entries.firstOrNull { it.name == name } ?: throw invalidQuery()
    }.orEmpty().toSet()
    val nearby = when (this["scope"]) {
        null, "CITY" -> false
        "NEARBY" -> true
        else -> throw invalidQuery()
    }
    return PlaceFilter(
        categories = categories,
        nearbyOnly = nearby,
        verifiedOnly = this["verifiedOnly"]?.toBooleanStrictOrNull() ?: false,
        text = this["q"].orEmpty().take(MAX_TEXT),
    )
}

/** `near=4.5339,-75.6811` */
internal fun Parameters.near(): GeoPoint? = this["near"]?.let { text ->
    val parts = text.split(',').map { it.trim().toDoubleOrNull() ?: throw invalidQuery() }
    if (parts.size != 2) throw invalidQuery()
    point(parts[0], parts[1])
}

/** `bounds=surLat,surLng,norteLat,norteLng`: el área visible del mapa. */
private fun Parameters.bounds(): GeoBounds? = this["bounds"]?.let { text ->
    val parts = text.split(',').map { it.trim().toDoubleOrNull() ?: throw invalidQuery() }
    if (parts.size != 4) throw invalidQuery()
    val southwest = point(parts[0], parts[1])
    val northeast = point(parts[2], parts[3])
    if (southwest.latitude > northeast.latitude || southwest.longitude > northeast.longitude) throw invalidQuery()
    GeoBounds(southwest, northeast)
}

private fun point(latitude: Double, longitude: Double): GeoPoint {
    if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) throw invalidQuery()
    return GeoPoint(latitude, longitude)
}

private fun Parameters.int(name: String, default: Int, range: IntRange): Int {
    val value = this[name]?.let { it.toIntOrNull() ?: throw invalidQuery() } ?: return default
    if (value !in range) throw invalidQuery()
    return value
}

private fun invalidQuery() = ApiException.badRequest("invalid_query")

/** README 7: paginación de 20. */
private const val FEED_PAGE_SIZE = 20
private const val COMMENTS_PAGE_SIZE = 20
private const val MAX_PAGE_SIZE = 50
private const val MAX_PAGE = 10_000

/** README 8: hasta 200 marcadores. */
private const val MAP_LIMIT = 200

/** Una búsqueda más larga que un título (60) no encuentra nada. */
private const val MAX_TEXT = 100
