package co.edu.uniquindio.exploracity.plugins

import io.ktor.server.application.Application
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(val status: String)

fun Application.configureRouting() {
    routing {
        // Comprobación de vida para Cloud Run (ADR-10).
        get("/health") {
            call.respond(HealthResponse("ok"))
        }
        // Aquí se registran las rutas de routes/: Auth, Place, Moderation y Social.
    }
}
