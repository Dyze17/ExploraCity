package co.edu.uniquindio.exploracity.routes

import co.edu.uniquindio.exploracity.model.ProfileUpdateRequest
import co.edu.uniquindio.exploracity.model.ReportRequest
import co.edu.uniquindio.exploracity.plugins.ACCESS
import co.edu.uniquindio.exploracity.plugins.user
import co.edu.uniquindio.exploracity.service.ProfileService
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

/** /v1/profile y /v1/users · El perfil propio (26 y 28), el perfil público (31) y su reporte (31A). */
fun Route.profileRoutes(profiles: ProfileService) {
    authenticate(ACCESS) {
        route("/profile") {
            get {
                call.respond(profiles.profile(call.user().id))
            }
            put {
                call.respond(profiles.update(call.user().id, call.receive<ProfileUpdateRequest>()))
            }
            route("/photo") {
                put {
                    call.respond(profiles.replacePhoto(call.user().id, call.receivePhoto()))
                }
                delete {
                    call.respond(profiles.removePhoto(call.user().id))
                }
            }
        }
        get("/users/{id}") {
            call.respond(profiles.publicProfile(call.parameters["id"].orEmpty(), call.request.queryParameters.near()))
        }
        post("/users/{id}/reports") {
            profiles.report(call.user().id, call.parameters["id"].orEmpty(), call.receive<ReportRequest>().reason)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
