package co.edu.uniquindio.exploracity.routes

import co.edu.uniquindio.exploracity.model.ChangesRequest
import co.edu.uniquindio.exploracity.model.PhotoUrlRequest
import co.edu.uniquindio.exploracity.model.SubmissionRequest
import co.edu.uniquindio.exploracity.model.SuggestionRequest
import co.edu.uniquindio.exploracity.plugins.ACCESS
import co.edu.uniquindio.exploracity.plugins.ApiException
import co.edu.uniquindio.exploracity.plugins.user
import co.edu.uniquindio.exploracity.service.PublicationService
import io.ktor.http.HttpStatusCode
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
 * /v1/photos, /v1/places/suggest-category, /v1/places/similar y /v1/publications · El formulario de publicación
 * (15–20) y las publicaciones propias (22–24).
 */
fun Route.publicationRoutes(publications: PublicationService) {
    authenticate(ACCESS) {
        post("/photos") {
            call.respond(HttpStatusCode.Created, publications.uploadPhoto(call.user().id, call.receivePhoto()))
        }
        post("/places/suggest-category") {
            val request = call.receive<SuggestionRequest>()
            call.respond(publications.suggestCategory(request.title, request.description))
        }
        get("/places/similar") {
            val query = call.request.queryParameters
            val near = query.near() ?: throw ApiException.badRequest("invalid_query")
            call.respond(publications.similar(call.user().id, query["title"].orEmpty(), near, query["excludeId"]))
        }
        route("/publications") {
            get {
                call.respond(publications.mine(call.user().id))
            }
            post {
                val (result, created) = publications.submit(call.user().id, call.receive<SubmissionRequest>())
                call.respond(if (created) HttpStatusCode.Created else HttpStatusCode.OK, result)
            }
            route("/{id}") {
                get {
                    call.respond(publications.one(call.user().id, call.parameters["id"].orEmpty()))
                }
                put {
                    call.respond(publications.update(call.user().id, call.parameters["id"].orEmpty(), call.receive<ChangesRequest>()))
                }
                delete {
                    publications.delete(call.user().id, call.parameters["id"].orEmpty())
                    call.respond(HttpStatusCode.NoContent)
                }
                post("/photos") {
                    publications.addPhoto(call.user().id, call.parameters["id"].orEmpty(), call.receive<PhotoUrlRequest>().url)
                    call.respond(HttpStatusCode.NoContent)
                }
            }
        }
    }
}
