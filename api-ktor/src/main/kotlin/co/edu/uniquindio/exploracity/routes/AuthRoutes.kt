package co.edu.uniquindio.exploracity.routes

import co.edu.uniquindio.exploracity.model.EmailRequest
import co.edu.uniquindio.exploracity.model.LoginRequest
import co.edu.uniquindio.exploracity.model.RefreshRequest
import co.edu.uniquindio.exploracity.model.RegisterRequest
import co.edu.uniquindio.exploracity.model.ResetPasswordRequest
import co.edu.uniquindio.exploracity.model.TokenRequest
import co.edu.uniquindio.exploracity.service.AuthService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route

/**
 * /v1/auth · Públicas: sesión (3 y 4) y recuperación de la contraseña (5, 6 y 6C). Cerrar sesión no pide el token de
 * acceso: basta el de renovación, aunque el de acceso ya haya vencido.
 */
fun Route.authRoutes(auth: AuthService) {
    route("/auth") {
        post("/register") {
            call.respond(HttpStatusCode.Created, auth.register(call.receive<RegisterRequest>()))
        }
        post("/login") {
            call.respond(auth.login(call.receive<LoginRequest>()))
        }
        post("/refresh") {
            call.respond(auth.refresh(call.receive<RefreshRequest>().refreshToken))
        }
        post("/logout") {
            auth.logout(call.receive<RefreshRequest>().refreshToken)
            call.respond(HttpStatusCode.NoContent)
        }
        route("/password") {
            post("/forgot") {
                auth.requestPasswordReset(call.receive<EmailRequest>().email)
                call.respond(HttpStatusCode.Accepted)
            }
            post("/link") {
                call.respond(auth.openResetLink(call.receive<TokenRequest>().token))
            }
            post("/reset") {
                val request = call.receive<ResetPasswordRequest>()
                auth.resetPassword(request.token, request.password)
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}
