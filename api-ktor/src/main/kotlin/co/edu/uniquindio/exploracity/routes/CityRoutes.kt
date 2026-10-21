package co.edu.uniquindio.exploracity.routes

import co.edu.uniquindio.exploracity.config.CitySettings
import co.edu.uniquindio.exploracity.model.GeoBounds
import co.edu.uniquindio.exploracity.model.GeoPoint
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.Serializable

/**
 * La ciudad que atiende la app: dónde abre el mapa (8) y hasta dónde llega la búsqueda por dirección (17.b). Es la que
 * se explora mientras el teléfono no sabe en qué municipio está (ADR-16); [region] es su departamento.
 */
@Serializable
data class CityResponse(val name: String, val region: String? = null, val center: GeoPoint, val bounds: GeoBounds)

/** GET /v1/city · Pública: la app la pide al entrar, también antes de iniciar sesión. */
fun Route.cityRoutes(city: CitySettings) {
    val response = CityResponse(city.name, city.region, city.center, GeoBounds(city.southwest, city.northeast))
    get("/city") {
        call.respond(response)
    }
}
