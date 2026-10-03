package co.edu.uniquindio.exploracity.routes

import co.edu.uniquindio.exploracity.model.DevLinkRequest
import co.edu.uniquindio.exploracity.model.DevLinkResponse
import co.edu.uniquindio.exploracity.plugins.ApiException
import co.edu.uniquindio.exploracity.service.DevMailboxService
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route

/**
 * /v1/dev/mailbox · Solo existen con DEV_MAILBOX=true y sin SendGrid: los botones de prueba de 6.a y «Cambiar correo»
 * abren el enlace que llegó al buzón o uno ya vencido. El correo va en el cuerpo, no en la dirección.
 */
fun Route.devRoutes(dev: DevMailboxService) {
    route("/dev/mailbox") {
        post("/latest-link") {
            val request = call.receive<DevLinkRequest>()
            val token = dev.latestLink(request.email, request.purpose) ?: throw ApiException.notFound("link_not_found")
            call.respond(DevLinkResponse(token))
        }
        post("/expired-link") {
            val request = call.receive<DevLinkRequest>()
            val token = dev.expiredLink(request.email, request.purpose) ?: throw ApiException.notFound("account_not_found")
            call.respond(DevLinkResponse(token))
        }
    }
}
