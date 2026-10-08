package co.edu.uniquindio.exploracity.plugins

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.http.content.HttpStatusCodeContent
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.CannotTransformContentToTypeException
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.plugins.UnsupportedMediaTypeException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.header
import io.ktor.server.response.respond
import kotlinx.serialization.Serializable
import java.time.Duration

/**
 * Cuerpo de error común: un código estable que la app traduce a su propio microcopy, sin trazas. [email] solo lo trae
 * un enlace vencido (6C), para pedir otro con el correo ya escrito.
 */
@Serializable
data class ErrorResponse(val code: String, val email: String? = null, val name: String? = null)

/**
 * Un error que la app sabe explicar: [status] HTTP y [code] estable (docs/api). Con [retryAfter], la respuesta dice en
 * `Retry-After` cuántos segundos esperar.
 */
open class ApiException(
    val status: HttpStatusCode,
    val code: String,
    val email: String? = null,
    val retryAfter: Duration? = null,
    /** registration_required (ADR-15): el nombre de la cuenta de Google, para empezar el registro con él. */
    val name: String? = null,
) : RuntimeException(code) {
    companion object {
        fun badRequest(code: String = "bad_request") = ApiException(HttpStatusCode.BadRequest, code)

        fun unauthorized(code: String = "unauthorized") = ApiException(HttpStatusCode.Unauthorized, code)

        fun forbidden(code: String = "forbidden") = ApiException(HttpStatusCode.Forbidden, code)

        fun notFound(code: String = "not_found") = ApiException(HttpStatusCode.NotFound, code)

        fun conflict(code: String) = ApiException(HttpStatusCode.Conflict, code)
    }
}

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<ApiException> { call, cause ->
            cause.retryAfter?.let { wait ->
                // En segundos enteros, redondeando hacia arriba: esperar menos daría otra vez el error.
                val seconds = (wait.toMillis() + 999) / 1000
                call.response.header(HttpHeaders.RetryAfter, seconds.coerceAtLeast(1).toString())
            }
            call.respond(cause.status, ErrorResponse(cause.code, cause.email, cause.name))
        }
        // JSON mal formado o que no corresponde al cuerpo esperado.
        exception<BadRequestException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request"))
        }
        // Un cuerpo que no es JSON.
        exception<UnsupportedMediaTypeException> { call, _ ->
            call.respond(HttpStatusCode.UnsupportedMediaType, ErrorResponse("unsupported_media_type"))
        }
        exception<CannotTransformContentToTypeException> { call, _ ->
            call.respond(HttpStatusCode.UnsupportedMediaType, ErrorResponse("unsupported_media_type"))
        }
        exception<NotFoundException> { call, _ ->
            call.respond(HttpStatusCode.NotFound, ErrorResponse("not_found"))
        }
        exception<Throwable> { call, cause ->
            call.application.log.error("Error no controlado en ${call.request.local.uri}", cause)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("internal_error"))
        }
        // Rutas que no existen o que no aceptan el método: el mismo cuerpo de error. Solo cuando la respuesta llega
        // vacía, para no tapar un 404 que ya trae su código.
        status(HttpStatusCode.NotFound, HttpStatusCode.MethodNotAllowed) { status ->
            if (content is HttpStatusCodeContent) {
                val code = if (status == HttpStatusCode.NotFound) "not_found" else "method_not_allowed"
                call.respond(status, ErrorResponse(code))
            }
        }
    }
}
