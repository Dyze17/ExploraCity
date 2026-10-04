package co.edu.uniquindio.exploracity.routes

import co.edu.uniquindio.exploracity.plugins.ACCESS
import co.edu.uniquindio.exploracity.plugins.user
import co.edu.uniquindio.exploracity.service.NotificationService
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

/** /v1/notifications · Los avisos de la persona (25). La app los consulta (ADR-09). */
fun Route.notificationRoutes(notifications: NotificationService) {
    authenticate(ACCESS) {
        route("/notifications") {
            get {
                call.respond(notifications.list(call.user().id))
            }
            post("/read-all") {
                notifications.markAllRead(call.user().id)
                call.respond(HttpStatusCode.NoContent)
            }
            post("/{id}/read") {
                notifications.markRead(call.user().id, call.parameters["id"].orEmpty())
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}
