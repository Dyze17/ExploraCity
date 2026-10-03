package co.edu.uniquindio.exploracity.plugins

import co.edu.uniquindio.exploracity.config.JwtConfig
import co.edu.uniquindio.exploracity.config.JwtSettings
import co.edu.uniquindio.exploracity.config.UserPrincipal
import co.edu.uniquindio.exploracity.model.Role
import co.edu.uniquindio.exploracity.support.MutableClock
import co.edu.uniquindio.exploracity.support.randomSecret
import co.edu.uniquindio.exploracity.support.testConfig
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

/** ADR-06 · El token de acceso y la autorización por rol. */
class SecurityTest {

    private val settings: JwtSettings = testConfig().jwt
    private val clock = MutableClock(Instant.parse("2026-10-03T15:00:00Z"))
    private val jwt = JwtConfig(settings, clock)

    private val user = UserPrincipal(UUID.randomUUID(), Role.USER)
    private val moderator = UserPrincipal(UUID.randomUUID(), Role.MODERATOR)

    private fun ApplicationTestBuilder.app() {
        environment { config = MapApplicationConfig() }
        application {
            configureSerialization()
            configureStatusPages()
            configureSecurity(jwt)
            routing {
                authenticate(ACCESS) {
                    get("/yo") { call.respond("${call.user().id} ${call.user().role}") }
                }
                moderatorOnly {
                    get("/cola") { call.respond("cola") }
                }
                // Otra ruta con token al mismo nivel: la comprobación del rol no la alcanza.
                authenticate(ACCESS) {
                    get("/avisos") { call.respond("avisos") }
                }
            }
        }
    }

    private suspend fun ApplicationTestBuilder.get(path: String, token: String? = null): HttpResponse =
        client.get(path) { token?.let { bearerAuth(it) } }

    @Test
    fun `sin token responde 401 con la señal para renovarlo`() = testApplication {
        app()

        val response = get("/yo")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals("""Bearer realm="exploracity"""", response.headers[HttpHeaders.WWWAuthenticate])
        assertEquals("""{"code":"unauthorized"}""", response.bodyAsText())
    }

    @Test
    fun `con un token vigente se sabe quién es y su rol`() = testApplication {
        app()

        val response = get("/yo", jwt.accessToken(user))

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("${user.id} USER", response.bodyAsText())
    }

    @Test
    fun `el token de acceso vence a los 15 minutos`() = testApplication {
        app()
        val token = jwt.accessToken(user)

        clock.advance(Duration.ofMinutes(14))
        assertEquals(HttpStatusCode.OK, get("/yo", token).status)
        clock.advance(Duration.ofMinutes(2))
        assertEquals(HttpStatusCode.Unauthorized, get("/yo", token).status)
    }

    @Test
    fun `un token firmado con otra clave no sirve`() = testApplication {
        app()
        val other = JwtConfig(settings.copy(secret = randomSecret()), clock)

        assertEquals(HttpStatusCode.Unauthorized, get("/yo", other.accessToken(user)).status)
    }

    @Test
    fun `moderación solo para el rol de moderador`() = testApplication {
        app()

        val asUser = get("/cola", jwt.accessToken(user))
        assertEquals(HttpStatusCode.Forbidden, asUser.status)
        assertEquals("""{"code":"forbidden"}""", asUser.bodyAsText())
        assertEquals(HttpStatusCode.OK, get("/cola", jwt.accessToken(moderator)).status)
        assertEquals(HttpStatusCode.Unauthorized, get("/cola").status)
    }

    @Test
    fun `el rol de moderador no se pide en las demás rutas con token`() = testApplication {
        app()

        assertEquals(HttpStatusCode.OK, get("/avisos", jwt.accessToken(user)).status)
    }
}
