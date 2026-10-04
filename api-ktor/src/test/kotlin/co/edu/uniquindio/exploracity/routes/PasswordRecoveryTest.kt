package co.edu.uniquindio.exploracity.routes

import co.edu.uniquindio.exploracity.model.AccountLinks
import co.edu.uniquindio.exploracity.model.LinkPurpose
import co.edu.uniquindio.exploracity.service.SecretTokens
import co.edu.uniquindio.exploracity.support.ApiTest
import co.edu.uniquindio.exploracity.support.TestApi
import co.edu.uniquindio.exploracity.support.assertMatchesContract
import co.edu.uniquindio.exploracity.support.json
import co.edu.uniquindio.exploracity.support.newPassword
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 5, 6 y 6C · Recuperar la contraseña con un enlace de 30 minutos, sin revelar qué correos tienen cuenta. */
class PasswordRecoveryTest : ApiTest() {

    @Test
    fun `pedir el enlace responde 202 con o sin cuenta, pero solo llega si la hay`() = apiTest {
        val ana = register()

        val known = post("/v1/auth/password/forgot", """{"email":" Ana.Rios@correo.com "}""")
        val unknown = post("/v1/auth/password/forgot", """{"email":"nadie@correo.com"}""")

        assertEquals(HttpStatusCode.Accepted, known.status)
        assertEquals(HttpStatusCode.Accepted, unknown.status)
        assertEquals("", known.bodyAsText())
        assertEquals("", unknown.bodyAsText())
        val mail = sentTo(ana.email).first()
        assertEquals("Crea una contraseña nueva para ExploraCity", mail.subject)
        assertTrue("exploracity://enlace/restablecer?token=" in mail.text)
        assertTrue(sentTo("nadie@correo.com").isEmpty())
    }

    @Test
    fun `antes de 60 segundos no se envía otro enlace, y responde igual`() = apiTest {
        val ana = register()
        post("/v1/auth/password/forgot", """{"email":"${ana.email}"}""")

        clock.advance(Duration.ofSeconds(59))
        assertEquals(HttpStatusCode.Accepted, post("/v1/auth/password/forgot", """{"email":"${ana.email}"}""").status)
        assertEquals(1, resetMails(ana.email))

        clock.advance(Duration.ofSeconds(1))
        post("/v1/auth/password/forgot", """{"email":"${ana.email}"}""")
        assertEquals(2, resetMails(ana.email))
    }

    @Test
    fun `si el correo de una cuenta no sale lo dice, y sin cuenta responde igual que siempre`() = apiTest {
        val ana = register()
        testMail.failing = true

        val known = post("/v1/auth/password/forgot", """{"email":"${ana.email}"}""")
        val unknown = post("/v1/auth/password/forgot", """{"email":"nadie@correo.com"}""")

        assertEquals(HttpStatusCode.ServiceUnavailable, known.status)
        assertEquals("""{"code":"email_delivery_failed"}""", known.bodyAsText())
        assertEquals(HttpStatusCode.Accepted, unknown.status)

        // El fallo no cuenta para la espera de 60 s.
        testMail.failing = false
        assertEquals(HttpStatusCode.Accepted, post("/v1/auth/password/forgot", """{"email":"${ana.email}"}""").status)
        assertEquals(1, resetMails(ana.email))
    }

    @Test
    fun `un correo mal escrito no se acepta`() = apiTest {
        val response = post("/v1/auth/password/forgot", """{"email":"ana@correo"}""")

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("""{"code":"invalid_email"}""", response.bodyAsText())
    }

    @Test
    fun `abrir el enlace dice para quién es y hasta cuándo vale`() = apiTest {
        val ana = register()
        val token = requestLink(ana.email)

        val response = post("/v1/auth/password/link", """{"token":"$token"}""")

        assertEquals(HttpStatusCode.OK, response.status)
        assertMatchesContract("reset-link.json", response.json())
    }

    @Test
    fun `a los 30 minutos el enlace vence y responde con el correo para pedir otro`() = apiTest {
        val ana = register()
        val token = requestLink(ana.email)

        clock.advance(Duration.ofMinutes(29))
        assertEquals(HttpStatusCode.OK, post("/v1/auth/password/link", """{"token":"$token"}""").status)
        clock.advance(Duration.ofMinutes(1))
        val expired = post("/v1/auth/password/link", """{"token":"$token"}""")

        assertEquals(HttpStatusCode.Gone, expired.status)
        assertMatchesContract("link-expired.json", expired.json())
    }

    @Test
    fun `un enlace desconocido vence sin decir de quién es`() = apiTest {
        val response = post("/v1/auth/password/link", """{"token":"${SecretTokens.generate()}"}""")

        assertEquals(HttpStatusCode.Gone, response.status)
        assertEquals("""{"code":"link_expired"}""", response.bodyAsText())
    }

    @Test
    fun `la contraseña nueva reemplaza la anterior, cierra las sesiones y el enlace ya no sirve`() = apiTest {
        val ana = register()
        val token = requestLink(ana.email)
        val fresh = newPassword()

        val reset = post("/v1/auth/password/reset", """{"token":"$token","password":"$fresh"}""")

        assertEquals(HttpStatusCode.NoContent, reset.status)
        assertEquals(HttpStatusCode.Unauthorized, login(ana.email, ana.password).status)
        assertEquals(HttpStatusCode.OK, login(ana.email, fresh).status)
        assertEquals(HttpStatusCode.Unauthorized, post("/v1/auth/refresh", """{"refreshToken":"${ana.refreshToken}"}""").status)
        val again = post("/v1/auth/password/reset", """{"token":"$token","password":"${newPassword()}"}""")
        assertEquals(HttpStatusCode.Gone, again.status)
        assertMatchesContract("link-expired.json", again.json())
    }

    @Test
    fun `al usar un enlace, los demás de recuperación dejan de servir`() = apiTest {
        val ana = register()
        val first = requestLink(ana.email)
        clock.advance(Duration.ofMinutes(2))
        val second = requestLink(ana.email)

        post("/v1/auth/password/reset", """{"token":"$second","password":"${newPassword()}"}""")

        assertEquals(HttpStatusCode.Gone, post("/v1/auth/password/link", """{"token":"$first"}""").status)
    }

    @Test
    fun `la contraseña nueva cumple las reglas y el enlace sigue sirviendo si no`() = apiTest {
        val ana = register()
        val token = requestLink(ana.email)

        val weak = post("/v1/auth/password/reset", """{"token":"$token","password":"${"a".repeat(12)}"}""")

        assertEquals(HttpStatusCode.BadRequest, weak.status)
        assertEquals("""{"code":"weak_password"}""", weak.bodyAsText())
        assertEquals(HttpStatusCode.OK, post("/v1/auth/password/link", """{"token":"$token"}""").status)
    }

    @Test
    fun `crear una contraseña nueva saca al correo del bloqueo de intentos`() = apiTest {
        val ana = register()
        repeat(5) { login(ana.email, newPassword()) }
        val token = requestLink(ana.email)
        val fresh = newPassword()

        post("/v1/auth/password/reset", """{"token":"$token","password":"$fresh"}""")

        assertEquals(HttpStatusCode.OK, login(ana.email, fresh).status)
    }

    @Test
    fun `del enlace solo se guarda el hash`() = apiTest {
        val ana = register()
        val token = requestLink(ana.email)

        val stored = transaction(database) { AccountLinks.selectAll().single()[AccountLinks.tokenHash] }

        assertEquals(SecretTokens.hash(token), stored)
        assertTrue(token !in stored)
    }

    private suspend fun TestApi.requestLink(email: String): String {
        assertEquals(HttpStatusCode.Accepted, post("/v1/auth/password/forgot", """{"email":"$email"}""").status)
        return checkNotNull(linkSentTo(email, LinkPurpose.PASSWORD_RESET))
    }

    private fun TestApi.resetMails(email: String): Int =
        sentTo(email).count { it.subject == "Crea una contraseña nueva para ExploraCity" }
}
