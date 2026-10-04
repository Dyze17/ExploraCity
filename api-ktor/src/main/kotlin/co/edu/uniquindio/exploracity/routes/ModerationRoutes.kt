package co.edu.uniquindio.exploracity.routes

import co.edu.uniquindio.exploracity.model.FinalizeRequest
import co.edu.uniquindio.exploracity.model.RejectRequest
import co.edu.uniquindio.exploracity.model.ReopenRequest
import co.edu.uniquindio.exploracity.model.VerifyRequest
import co.edu.uniquindio.exploracity.plugins.moderatorOnly
import co.edu.uniquindio.exploracity.plugins.user
import co.edu.uniquindio.exploracity.service.ModerationService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

/** /v1/moderation · Solo con el rol de moderador (403 con otro): la cola, las decisiones y «Resueltas» (32–37). */
fun Route.moderationRoutes(moderation: ModerationService) {
    moderatorOnly {
        route("/moderation") {
            get("/summary") {
                call.respond(moderation.summary(call.user().id))
            }
            get("/today") {
                call.respond(moderation.todayWork(call.user().id))
            }
            route("/queue") {
                get {
                    call.respond(moderation.queue(call.user().id))
                }
                route("/{id}") {
                    get {
                        call.respond(moderation.item(call.user().id, call.parameters["id"].orEmpty()))
                    }
                    post("/verify") {
                        moderation.verify(call.user().id, call.parameters["id"].orEmpty(), call.receive<VerifyRequest>().note)
                        call.respond(HttpStatusCode.NoContent)
                    }
                    get("/duplicate-options") {
                        call.respond(moderation.duplicateOptions(call.user().id, call.parameters["id"].orEmpty()))
                    }
                    post("/reject") {
                        moderation.reject(call.user().id, call.parameters["id"].orEmpty(), call.receive<RejectRequest>())
                        call.respond(HttpStatusCode.NoContent)
                    }
                }
            }
            route("/resolved") {
                get {
                    call.respond(moderation.resolved())
                }
                route("/{id}") {
                    get {
                        call.respond(moderation.resolvedItem(call.parameters["id"].orEmpty()))
                    }
                    post("/finalize") {
                        moderation.finalize(call.user().id, call.parameters["id"].orEmpty(), call.receive<FinalizeRequest>().reason)
                        call.respond(HttpStatusCode.NoContent)
                    }
                    post("/reopen") {
                        moderation.reopen(call.user().id, call.parameters["id"].orEmpty(), call.receive<ReopenRequest>().reason)
                        call.respond(HttpStatusCode.NoContent)
                    }
                }
            }
        }
    }
}
