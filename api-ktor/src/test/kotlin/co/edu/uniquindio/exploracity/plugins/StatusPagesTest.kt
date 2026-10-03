package co.edu.uniquindio.exploracity.plugins

import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.Serializable
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals

/** Todos los errores llegan como {"code": "…"}, sin trazas: la app traduce el código a su texto. */
class StatusPagesTest {

    @Serializable
    private data class Body(val title: String)

    private fun ApplicationTestBuilder.app() {
        environment { config = MapApplicationConfig() }
        application {
            configureSerialization()
            configureStatusPages()
            routing {
                post("/eco") { call.respond(call.receive<Body>()) }
                get("/ocupado") { throw ApiException.conflict("email_taken") }
                get("/lugar") { throw ApiException.notFound("place_not_found") }
                get("/falla") { error("algo se rompió") }
                get("/vencido") { throw ApiException(HttpStatusCode.Gone, "link_expired", email = "ana@correo.com") }
                get("/espera") {
                    throw ApiException(HttpStatusCode.TooManyRequests, "too_many_attempts", retryAfter = Duration.ofMillis(90_001))
                }
            }
        }
    }

    private suspend fun HttpResponse.assertError(status: HttpStatusCode, code: String) {
        assertEquals(status, this.status)
        assertEquals("""{"code":"$code"}""", bodyAsText())
    }

    @Test
    fun `una ruta que no existe`() = testApplication {
        app()
        client.get("/no-existe").assertError(HttpStatusCode.NotFound, "not_found")
    }

    @Test
    fun `un método que la ruta no acepta`() = testApplication {
        app()
        client.post("/ocupado").assertError(HttpStatusCode.MethodNotAllowed, "method_not_allowed")
    }

    @Test
    fun `los errores de la API conservan su código, también los 404`() = testApplication {
        app()
        client.get("/ocupado").assertError(HttpStatusCode.Conflict, "email_taken")
        client.get("/lugar").assertError(HttpStatusCode.NotFound, "place_not_found")
    }

    @Test
    fun `un cuerpo que no se entiende`() = testApplication {
        app()

        client.post("/eco") {
            contentType(ContentType.Application.Json)
            setBody("""{"titulo": 1}""")
        }.assertError(HttpStatusCode.BadRequest, "bad_request")
        client.post("/eco") {
            contentType(ContentType.Text.Plain)
            setBody("hola")
        }.assertError(HttpStatusCode.UnsupportedMediaType, "unsupported_media_type")
    }

    @Test
    fun `un enlace vencido trae el correo y una espera dice cuántos segundos`() = testApplication {
        app()

        val expired = client.get("/vencido")
        assertEquals(HttpStatusCode.Gone, expired.status)
        assertEquals("""{"code":"link_expired","email":"ana@correo.com"}""", expired.bodyAsText())
        val wait = client.get("/espera")
        wait.assertError(HttpStatusCode.TooManyRequests, "too_many_attempts")
        // Redondea hacia arriba: esperar 90 s daría otra vez el error.
        assertEquals("91", wait.headers[HttpHeaders.RetryAfter])
    }

    @Test
    fun `un fallo inesperado no muestra la traza`() = testApplication {
        app()
        client.get("/falla").assertError(HttpStatusCode.InternalServerError, "internal_error")
    }
}
