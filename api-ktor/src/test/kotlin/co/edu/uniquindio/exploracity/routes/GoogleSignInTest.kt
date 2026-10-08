package co.edu.uniquindio.exploracity.routes

import co.edu.uniquindio.exploracity.model.LinkPurpose
import co.edu.uniquindio.exploracity.model.Users
import co.edu.uniquindio.exploracity.support.ApiTest
import co.edu.uniquindio.exploracity.support.TestApi
import co.edu.uniquindio.exploracity.support.assertMatchesContract
import co.edu.uniquindio.exploracity.support.json
import co.edu.uniquindio.exploracity.support.newPassword
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** ADR-15 · Entrar con Google: B1 (vincular con la contraseña), C1 (registro en modo Google) y D1 (sin contraseña). */
class GoogleSignInTest : ApiTest() {

    private suspend fun TestApi.signIn(token: String, registration: String? = null): HttpResponse =
        post("/v1/auth/google", """{"idToken":"$token"${registration?.let { ""","registration":$it""" } ?: ""}}""")

    private suspend fun TestApi.link(token: String, password: String): HttpResponse =
        post("/v1/auth/google/link", """{"idToken":"$token","password":"$password"}""")

    private val residente = """{"name":"Ana Ríos","residency":"RESIDENT"}"""

    private fun JsonElement.text(field: String) = jsonObject[field]?.jsonPrimitive?.content

    private fun TestApi.accounts(): Long = transaction(database) { Users.selectAll().count() }

    @Test
    fun `una cuenta de Google nueva pide el registro con su correo y su nombre`() = apiTest(googleSignIn = true) {
        val response = signIn(google.token(email = "ana.rios@gmail.com", name = "Ana Ríos"))

        assertEquals(HttpStatusCode.NotFound, response.status)
        val body = response.json()
        assertEquals("registration_required", body.text("code"))
        assertEquals("ana.rios@gmail.com", body.text("email"))
        assertEquals("Ana Ríos", body.text("name"))
        assertEquals(0, accounts())
    }

    @Test
    fun `con el registro crea la cuenta sin contraseña y la próxima vez entra directo`() = apiTest(googleSignIn = true) {
        val token = google.token(subject = "google-ana", email = "ana.rios@gmail.com")

        val created = signIn(token, residente)

        assertEquals(HttpStatusCode.Created, created.status, created.bodyAsText())
        assertMatchesContract("register.json", created.json(), setOf("accessToken", "refreshToken", "userId", "email"))
        val ana = created.session()
        assertEquals("ana.rios@gmail.com", ana.email)
        assertEquals(1, sentTo("ana.rios@gmail.com").size)
        val account = get("/v1/account", ana.accessToken).bodyAsText()
        assertEquals("""{"email":"ana.rios@gmail.com","hasPassword":false,"googleLinked":true}""", account)
        // La segunda vez, con otro token de la misma cuenta, entra sin registro y sin otra bienvenida.
        val again = signIn(google.token(subject = "google-ana", email = "ana.rios@gmail.com"))
        assertEquals(HttpStatusCode.OK, again.status)
        assertEquals(ana.userId, again.session().userId)
        assertEquals(1, sentTo("ana.rios@gmail.com").size)
        // Sin contraseña, ninguna sirve.
        assertEquals(HttpStatusCode.Unauthorized, login("ana.rios@gmail.com", newPassword()).status)
        assertEquals(1, accounts())
    }

    @Test
    fun `repetir el registro con Google cuya respuesta se perdió entra a la misma cuenta`() = apiTest(googleSignIn = true) {
        val first = signIn(google.token(), residente).session()

        val repeated = signIn(google.token(), residente)

        assertEquals(HttpStatusCode.OK, repeated.status)
        assertEquals(first.userId, repeated.session().userId)
        assertEquals(1, accounts())
    }

    @Test
    fun `el registro con Google valida el nombre y el correo de un moderador entra con su rol`() =
        apiTest(googleSignIn = true, moderators = setOf("laura@gmail.com")) {
            val invalid = signIn(google.token(subject = "google-laura", email = "laura@gmail.com"), """{"name":" ","residency":"VISITOR"}""")
            assertEquals("invalid_name", invalid.json().text("code"))

            val laura = signIn(google.token(subject = "google-laura", email = "laura@gmail.com"), """{"name":"Laura","residency":"VISITOR"}""")

            assertEquals(HttpStatusCode.Created, laura.status)
            assertEquals("MODERATOR", laura.json().text("role"))
        }

    @Test
    fun `un correo con cuenta y contraseña se vincula solo con esa contraseña`() = apiTest(googleSignIn = true) {
        val ana = register(email = "ana.rios@gmail.com")
        val token = google.token(subject = "google-ana", email = "ana.rios@gmail.com")

        val asked = signIn(token)
        assertEquals(HttpStatusCode.Conflict, asked.status)
        assertEquals("link_required", asked.json().text("code"))
        assertEquals("ana.rios@gmail.com", asked.json().text("email"))
        // Ni con el registro se crea otra cuenta con ese correo.
        assertEquals("link_required", signIn(token, residente).json().text("code"))

        val wrong = link(token, newPassword())
        assertEquals(HttpStatusCode.Unauthorized, wrong.status)
        assertEquals("invalid_credentials", wrong.json().text("code"))

        val linked = link(token, ana.password)
        assertEquals(HttpStatusCode.OK, linked.status, linked.bodyAsText())
        assertEquals(ana.userId, linked.session().userId)
        // Desde entonces entra con cualquiera de los dos.
        assertEquals(ana.userId, signIn(google.token(subject = "google-ana", email = "ana.rios@gmail.com")).session().userId)
        assertEquals(HttpStatusCode.OK, login("ana.rios@gmail.com", ana.password).status)
        val account = get("/v1/account", ana.accessToken).bodyAsText()
        assertEquals("""{"email":"ana.rios@gmail.com","hasPassword":true,"googleLinked":true}""", account)
        // Vincular otra vez, por ejemplo si la respuesta se perdió, solo abre la sesión.
        assertEquals(HttpStatusCode.OK, link(token, ana.password).status)
    }

    @Test
    fun `cinco contraseñas equivocadas al vincular bloquean el correo como el inicio de sesión`() = apiTest(googleSignIn = true) {
        val ana = register(email = "ana.rios@gmail.com")
        val token = google.token(email = "ana.rios@gmail.com")

        repeat(5) { assertEquals(HttpStatusCode.Unauthorized, link(token, newPassword()).status) }

        val locked = link(token, ana.password)
        assertEquals(HttpStatusCode.TooManyRequests, locked.status)
        assertEquals("too_many_attempts", locked.json().text("code"))
    }

    @Test
    fun `otra cuenta de Google con el correo de una ya vinculada no entra ni la reemplaza`() = apiTest(googleSignIn = true) {
        signIn(google.token(subject = "google-ana", email = "ana.rios@gmail.com"), residente).session()

        val other = google.token(subject = "otra-cuenta", email = "ana.rios@gmail.com")

        assertEquals("email_taken", signIn(other).json().text("code"))
        assertEquals(HttpStatusCode.Unauthorized, link(other, newPassword()).status)
    }

    @Test
    fun `un token que no sirve no entra`() = apiTest(googleSignIn = true) {
        for (token in listOf("esto no es un token", google.token(audience = "otra-app"), google.token(emailVerified = false))) {
            val response = signIn(token)
            assertEquals(HttpStatusCode.Unauthorized, response.status)
            assertEquals("invalid_google_token", response.json().text("code"))
        }
        assertEquals(HttpStatusCode.Unauthorized, link("esto no es un token", newPassword()).status)
    }

    @Test
    fun `sin GOOGLE_WEB_CLIENT_ID entrar con Google no está disponible`() = apiTest {
        val response = post("/v1/auth/google", """{"idToken":"cualquiera"}""")

        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertEquals("google_sign_in_unavailable", response.json().text("code"))
    }

    @Test
    fun `una cuenta solo con Google no cambia el correo, pero puede crear una contraseña`() = apiTest(googleSignIn = true) {
        val ana = signIn(google.token(email = "ana.rios@gmail.com"), residente).session()

        val change = post("/v1/account/email", """{"newEmail":"ana.nueva@correo.com","password":"${newPassword()}"}""", ana.accessToken)
        assertEquals(HttpStatusCode.Conflict, change.status)
        assertEquals("password_required", change.json().text("code"))

        // «¿Olvidaste tu contraseña?» le crea una: desde entonces también entra con ella.
        assertEquals(HttpStatusCode.Accepted, post("/v1/auth/password/forgot", """{"email":"ana.rios@gmail.com"}""").status)
        val link = checkNotNull(linkSentTo("ana.rios@gmail.com", LinkPurpose.PASSWORD_RESET))
        val password = newPassword()
        assertEquals(HttpStatusCode.NoContent, post("/v1/auth/password/reset", """{"token":"$link","password":"$password"}""").status)
        assertEquals(HttpStatusCode.OK, login("ana.rios@gmail.com", password).status)
        val session = login("ana.rios@gmail.com", password).session()
        assertTrue(""""hasPassword":true""" in get("/v1/account", session.accessToken).bodyAsText())
    }

    @Test
    fun `la exportación dice si la cuenta entra con Google`() = apiTest(googleSignIn = true) {
        val ana = signIn(google.token(email = "ana.rios@gmail.com"), residente).session()
        val laura = register(email = "laura@correo.com")

        val withGoogle = get("/v1/account/export", ana.accessToken).json().jsonObject["cuenta"]!!
        val withoutGoogle = get("/v1/account/export", laura.accessToken).json().jsonObject["cuenta"]!!

        assertEquals("true", withGoogle.text("entraConGoogle"))
        assertEquals("false", withoutGoogle.text("entraConGoogle"))
        assertFalse("google-ana" in get("/v1/account/export", ana.accessToken).bodyAsText())
    }
}
