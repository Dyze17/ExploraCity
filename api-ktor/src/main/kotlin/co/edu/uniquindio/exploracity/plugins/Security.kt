package co.edu.uniquindio.exploracity.plugins

import co.edu.uniquindio.exploracity.config.JwtConfig
import co.edu.uniquindio.exploracity.config.UserPrincipal
import co.edu.uniquindio.exploracity.model.Role
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.AuthenticationChecked
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.principal
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RouteSelector
import io.ktor.server.routing.RouteSelectorEvaluation
import io.ktor.server.routing.RoutingResolveContext

/** Nombre del proveedor de autenticación con el token de acceso. */
const val ACCESS = "access"

private const val REALM = "exploracity"

/** ADR-06 · Las rutas con `authenticate(ACCESS)` piden un token de acceso vigente. */
fun Application.configureSecurity(jwt: JwtConfig) {
    install(Authentication) {
        jwt(ACCESS) {
            realm = REALM
            verifier(jwt.verifier)
            validate { credential -> jwt.principal(credential.payload) }
            // Sin token o vencido: 401 con WWW-Authenticate, la señal para que la app lo renueve.
            challenge { _, _ ->
                call.response.header(HttpHeaders.WWWAuthenticate, "Bearer realm=\"$REALM\"")
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("unauthorized"))
            }
        }
    }
}

/** La persona del token; solo dentro de `authenticate(ACCESS)`. */
fun ApplicationCall.user(): UserPrincipal = principal<UserPrincipal>() ?: throw ApiException.unauthorized()

private val ModeratorOnly = createRouteScopedPlugin("ModeratorOnly") {
    on(AuthenticationChecked) { call ->
        val user = call.principal<UserPrincipal>()
        if (user != null && user.role != Role.MODERATOR) {
            call.respond(HttpStatusCode.Forbidden, ErrorResponse("forbidden"))
        }
    }
}

/**
 * Rutas de Moderación: token de acceso y rol de moderador; con otro rol, 403. Cuelgan de una rama propia para que la
 * comprobación del rol no alcance a las demás rutas con `authenticate(ACCESS)` del mismo nivel.
 */
fun Route.moderatorOnly(build: Route.() -> Unit): Route {
    val branch = createChild(ModeratorSelector)
    branch.authenticate(ACCESS) {
        install(ModeratorOnly)
        build()
    }
    return branch
}

/** Rama transparente: no consume nada de la ruta. */
private object ModeratorSelector : RouteSelector() {
    override suspend fun evaluate(context: RoutingResolveContext, segmentIndex: Int): RouteSelectorEvaluation =
        RouteSelectorEvaluation.Transparent

    override fun toString(): String = "(moderación)"
}
