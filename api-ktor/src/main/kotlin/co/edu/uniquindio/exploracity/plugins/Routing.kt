package co.edu.uniquindio.exploracity.plugins

import co.edu.uniquindio.exploracity.config.AppConfig
import co.edu.uniquindio.exploracity.routes.cityRoutes
import io.ktor.server.application.Application
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(val status: String)

fun Application.configureRouting(config: AppConfig) {
    routing {
        // Comprobación de vida para Cloud Run (ADR-10).
        get("/health") {
            call.respond(HealthResponse("ok"))
        }
        // ADR-02: la versión va en la ruta; un cambio que rompa la app irá a /v2.
        route("/v1") {
            cityRoutes(config.city)
            // Aquí se suman las rutas de routes/: Auth, Place, Moderation y Social.
        }
    }
}
