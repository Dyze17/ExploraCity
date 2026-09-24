package co.edu.uniquindio.exploracity.plugins

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import kotlinx.serialization.Serializable

/** Cuerpo de error común: un código estable que la app traduce a su propio microcopy, sin trazas. */
@Serializable
data class ErrorResponse(val code: String)

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.application.log.error("Error no controlado en ${call.request.local.uri}", cause)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("internal_error"))
        }
    }
}
