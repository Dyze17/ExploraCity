package co.edu.uniquindio.exploracity.routes

import co.edu.uniquindio.exploracity.model.ProfileUpdateRequest
import co.edu.uniquindio.exploracity.model.ReportRequest
import co.edu.uniquindio.exploracity.plugins.ACCESS
import co.edu.uniquindio.exploracity.plugins.ApiException
import co.edu.uniquindio.exploracity.plugins.user
import co.edu.uniquindio.exploracity.service.ProfileService
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.request.contentLength
import io.ktor.server.request.receive
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.utils.io.readRemaining
import kotlinx.io.IOException
import kotlinx.io.readByteArray

/** /v1/profile y /v1/users · El perfil propio (26 y 28) y el reporte de un perfil (31A). */
fun Route.profileRoutes(profiles: ProfileService) {
    authenticate(ACCESS) {
        route("/profile") {
            get {
                call.respond(profiles.profile(call.user().id))
            }
            put {
                call.respond(profiles.update(call.user().id, call.receive<ProfileUpdateRequest>()))
            }
            route("/photo") {
                put {
                    call.respond(profiles.replacePhoto(call.user().id, call.receivePhoto()))
                }
                delete {
                    call.respond(profiles.removePhoto(call.user().id))
                }
            }
        }
        post("/users/{id}/reports") {
            profiles.report(call.user().id, call.parameters["id"].orEmpty(), call.receive<ReportRequest>().reason)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

/** La foto del cuerpo multipart (la primera parte con archivo), hasta 8 MB. */
private suspend fun ApplicationCall.receivePhoto(): ByteArray {
    val max = ProfileService.MAX_PHOTO_BYTES
    // El cuerpo entero trae, además de la foto, los separadores del multipart.
    request.contentLength()?.let { if (it > max + MULTIPART_OVERHEAD) throw photoTooLarge() }
    var photo: ByteArray? = null
    try {
        receiveMultipart(formFieldLimit = max + 1).forEachPart { part ->
            if (photo == null && part is PartData.FileItem) photo = part.provider().readRemaining(max + 1).readByteArray()
            part.dispose()
        }
    } catch (e: IOException) {
        // Ktor corta la lectura al pasar del límite.
        throw photoTooLarge()
    }
    val bytes = photo ?: throw ApiException.badRequest("photo_missing")
    if (bytes.size > max) throw photoTooLarge()
    return bytes
}

private fun photoTooLarge() = ApiException(HttpStatusCode.PayloadTooLarge, "photo_too_large")

private const val MULTIPART_OVERHEAD = 64L * 1024
