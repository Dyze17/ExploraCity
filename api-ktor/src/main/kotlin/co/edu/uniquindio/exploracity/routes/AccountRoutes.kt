package co.edu.uniquindio.exploracity.routes

import co.edu.uniquindio.exploracity.model.EmailChangeRequest
import co.edu.uniquindio.exploracity.model.TokenRequest
import co.edu.uniquindio.exploracity.plugins.ACCESS
import co.edu.uniquindio.exploracity.plugins.user
import co.edu.uniquindio.exploracity.service.AccountService
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.withCharset
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

/** /v1/account · La cuenta de la sesión (28, 29, 30 y «Cambiar correo»). */
fun Route.accountRoutes(accounts: AccountService) {
    authenticate(ACCESS) {
        route("/account") {
            get {
                call.respond(accounts.account(call.user().id))
            }
            delete {
                accounts.delete(call.user().id)
                call.respond(HttpStatusCode.NoContent)
            }
            get("/export") {
                val file = accounts.export(call.user().id)
                call.response.header(
                    HttpHeaders.ContentDisposition,
                    ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, file.fileName).toString(),
                )
                call.respondText(file.content, ContentType.Application.Json.withCharset(Charsets.UTF_8))
            }
            route("/email") {
                post {
                    call.respond(HttpStatusCode.Accepted, accounts.requestEmailChange(call.user().id, call.receive<EmailChangeRequest>()))
                }
                post("/resend") {
                    accounts.resendEmailChange(call.user().id)
                    call.respond(HttpStatusCode.Accepted)
                }
                post("/confirm") {
                    call.respond(accounts.confirmEmailChange(call.user().id, call.receive<TokenRequest>().token))
                }
            }
        }
    }
}
