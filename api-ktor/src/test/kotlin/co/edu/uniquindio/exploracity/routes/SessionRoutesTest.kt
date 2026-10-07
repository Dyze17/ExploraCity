package co.edu.uniquindio.exploracity.routes

import co.edu.uniquindio.exploracity.support.ApiTest
import co.edu.uniquindio.exploracity.support.assertMatchesContract
import co.edu.uniquindio.exploracity.support.json
import co.edu.uniquindio.exploracity.support.newPassword
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Duration
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/** 3 y 4 · Registro, inicio de sesión con el límite de intentos (A1), renovación (D1) y cierre de sesión. */
class SessionRoutesTest : ApiTest() {

    private val sessionFields = setOf("accessToken", "refreshToken", "userId")

    @Test
    fun `registrarse crea la cuenta, envía la bienvenida y abre la sesión como dice el contrato`() = apiTest {
        val password = newPassword()

        val response = post(
            "/v1/auth/register",
            """{"name":"  Ana Ríos ","email":" Ana.Rios@Correo.com ","password":"$password","residency":"RESIDENT"}""",
        )

        assertEquals(HttpStatusCode.Created, response.status)
        assertMatchesContract("register.json", response.json(), sessionFields)
        assertEquals(listOf("Tu cuenta de ExploraCity está lista"), sentTo("ana.rios@correo.com").map { it.subject })
        assertEquals(HttpStatusCode.OK, login("ana.rios@correo.com", password).status)
    }

    @Test
    fun `el rol de una cuenta nueva sale de la lista de moderadores`() = apiTest(moderators = setOf("laura@correo.com")) {
        val laura = post(
            "/v1/auth/register",
            """{"name":"Laura","email":"Laura@correo.com","password":"${newPassword()}","residency":"VISITOR"}""",
        )
        val ana = register()

        assertEquals("MODERATOR", laura.json().jsonObject["role"]?.jsonPrimitive?.content)
        assertEquals("USER", login(ana.email, ana.password).json().jsonObject["role"]?.jsonPrimitive?.content)
    }

    @Test
    fun `si la bienvenida no sale, la cuenta se crea igual y lo dice`() = apiTest {
        testMail.failing = true
        val password = newPassword()

        val response = post(
            "/v1/auth/register",
            """{"name":"Ana Ríos","email":"ana.rios@correo.com","password":"$password","residency":"RESIDENT"}""",
        )

        assertEquals(HttpStatusCode.Created, response.status)
        assertEquals("false", response.json().jsonObject["welcomeEmailSent"]?.jsonPrimitive?.content)
        assertEquals(HttpStatusCode.OK, login("ana.rios@correo.com", password).status)
    }

    @Test
    fun `un correo con cuenta no se registra otra vez`() = apiTest {
        register("ana.rios@correo.com")

        val again = post(
            "/v1/auth/register",
            """{"name":"Otra Ana","email":"ANA.RIOS@correo.com","password":"${newPassword()}","residency":"VISITOR"}""",
        )

        assertEquals(HttpStatusCode.Conflict, again.status)
        assertEquals("""{"code":"email_taken"}""", again.bodyAsText())
    }

    /** El registro de Ana Ríos con [clientId], como lo manda la app. */
    private fun registration(password: String, clientId: String?, email: String = "ana.rios@correo.com"): String =
        """{"name":"Ana Ríos","email":"$email","password":"$password","residency":"RESIDENT"""" +
            clientId?.let { ""","clientId":"$it"""" }.orEmpty() + "}"

    @Test
    fun `repetir un registro cuya respuesta se perdió abre otra sesión en la misma cuenta`() = apiTest {
        val password = newPassword()
        val clientId = UUID.randomUUID().toString()
        val first = post("/v1/auth/register", registration(password, clientId))

        // La app se rindió antes de recibir la respuesta y lo intenta de nuevo, igual.
        val again = post("/v1/auth/register", registration(password, clientId, email = " Ana.Rios@Correo.com "))

        assertEquals(HttpStatusCode.Created, first.status)
        assertEquals(HttpStatusCode.OK, again.status)
        val created = first.session()
        val reopened = again.session()
        assertEquals(created.userId, reopened.userId)
        assertNotEquals(created.refreshToken, reopened.refreshToken)
        // La bienvenida la envió el primero: no se repite ni se informa.
        assertFalse("welcomeEmailSent" in again.json().jsonObject)
        assertEquals(1, sentTo("ana.rios@correo.com").size)
        assertEquals(HttpStatusCode.OK, get("/v1/account", reopened.accessToken).status)
    }

    @Test
    fun `sin el mismo clientId y la misma contraseña, el correo sigue teniendo cuenta`() = apiTest {
        val password = newPassword()
        val clientId = UUID.randomUUID().toString()
        assertEquals(HttpStatusCode.Created, post("/v1/auth/register", registration(password, clientId)).status)
        val laura = register("laura@correo.com")

        val attempts = listOf(
            registration(password, null),
            registration(password, UUID.randomUUID().toString()),
            registration(newPassword(), clientId),
            // Una cuenta creada sin clientId no se abre con ninguno.
            registration(laura.password, clientId, email = laura.email),
        )

        for (attempt in attempts) {
            val response = post("/v1/auth/register", attempt)
            assertEquals(HttpStatusCode.Conflict, response.status, attempt)
            assertEquals("""{"code":"email_taken"}""", response.bodyAsText())
        }
        assertEquals(HttpStatusCode.BadRequest, post("/v1/auth/register", registration(password, "no-es-un-uuid")).status)
    }

    @Test
    fun `dos envíos a la vez del mismo registro crean una sola cuenta y los dos abren sesión`() = apiTest {
        val body = registration(newPassword(), UUID.randomUUID().toString())

        val responses = coroutineScope { List(2) { async { post("/v1/auth/register", body) } }.awaitAll() }

        assertEquals(listOf(HttpStatusCode.OK, HttpStatusCode.Created), responses.map { it.status }.sortedBy { it.value })
        assertEquals(1, responses.map { it.session().userId }.distinct().size)
        assertEquals(1, sentTo("ana.rios@correo.com").size)
    }

    @Test
    fun `el registro valida el nombre, el correo y la contraseña`() = apiTest {
        suspend fun attempt(name: String, email: String, password: String): String {
            val response = post("/v1/auth/register", """{"name":"$name","email":"$email","password":"$password","residency":"VISITOR"}""")
            assertEquals(HttpStatusCode.BadRequest, response.status)
            return response.json().jsonObject["code"]!!.jsonPrimitive.content
        }
        val good = newPassword()

        assertEquals("invalid_name", attempt(" A ", "ana@correo.com", good))
        assertEquals("invalid_name", attempt("A".repeat(41), "ana@correo.com", good))
        assertEquals("invalid_email", attempt("Ana", "ana@correo", good))
        assertEquals("weak_password", attempt("Ana", "ana@correo.com", "a".repeat(10)))
        assertEquals("weak_password", attempt("Ana", "ana@correo.com", "a1"))
        assertEquals(HttpStatusCode.BadRequest, post("/v1/auth/register", """{"name":"Ana"}""").status)
    }

    @Test
    fun `iniciar sesión devuelve los tokens y el rol como dice el contrato`() = apiTest {
        val ana = register()

        val response = login(" Ana.Rios@correo.com ", ana.password)

        assertEquals(HttpStatusCode.OK, response.status)
        assertMatchesContract("session.json", response.json(), sessionFields)
        val session = response.session()
        assertEquals(HttpStatusCode.OK, get("/v1/account", session.accessToken).status)
    }

    @Test
    fun `un correo sin cuenta y una contraseña equivocada dan el mismo error`() = apiTest {
        val ana = register()

        val unknown = login("nadie@correo.com", ana.password)
        val wrong = login(ana.email, newPassword())

        for (response in listOf(unknown, wrong)) {
            assertEquals(HttpStatusCode.Unauthorized, response.status)
            assertEquals("""{"code":"invalid_credentials"}""", response.bodyAsText())
            // No es la señal de renovar el token de acceso.
            assertNull(response.headers[HttpHeaders.WWWAuthenticate])
        }
    }

    @Test
    fun `tras 5 fallos en 15 minutos el correo queda bloqueado 15 minutos, también con la contraseña correcta`() = apiTest {
        val ana = register()
        repeat(5) { assertEquals(HttpStatusCode.Unauthorized, login(ana.email, newPassword()).status) }

        val locked = login(ana.email, ana.password)
        assertEquals(HttpStatusCode.TooManyRequests, locked.status)
        assertEquals("""{"code":"too_many_attempts"}""", locked.bodyAsText())
        assertEquals("900", locked.headers[HttpHeaders.RetryAfter])

        clock.advance(Duration.ofMinutes(14))
        val stillLocked = login(ana.email, ana.password)
        assertEquals(HttpStatusCode.TooManyRequests, stillLocked.status)
        assertEquals("60", stillLocked.headers[HttpHeaders.RetryAfter])

        clock.advance(Duration.ofMinutes(1))
        assertEquals(HttpStatusCode.OK, login(ana.email, ana.password).status)
    }

    @Test
    fun `los fallos de una ventana que ya pasó no suman`() = apiTest {
        val ana = register()
        repeat(4) { login(ana.email, newPassword()) }

        clock.advance(Duration.ofMinutes(15))
        login(ana.email, newPassword())

        assertEquals(HttpStatusCode.OK, login(ana.email, ana.password).status)
    }

    @Test
    fun `entrar bien vuelve a contar desde cero`() = apiTest {
        val ana = register()
        repeat(4) { login(ana.email, newPassword()) }
        assertEquals(HttpStatusCode.OK, login(ana.email, ana.password).status)

        repeat(4) { login(ana.email, newPassword()) }

        assertEquals(HttpStatusCode.OK, login(ana.email, ana.password).status)
    }

    @Test
    fun `un correo sin cuenta también se bloquea, así el bloqueo no revela qué correos existen`() = apiTest {
        repeat(5) { login("nadie@correo.com", newPassword()) }

        assertEquals(HttpStatusCode.TooManyRequests, login("Nadie@correo.com", newPassword()).status)
    }

    @Test
    fun `renovar entrega otro par y el token usado deja de servir`() = apiTest {
        val ana = register()

        val renewed = post("/v1/auth/refresh", """{"refreshToken":"${ana.refreshToken}"}""")

        assertEquals(HttpStatusCode.OK, renewed.status)
        assertMatchesContract("session.json", renewed.json(), sessionFields)
        val next = renewed.session()
        assertFalse(next.refreshToken == ana.refreshToken)
        assertEquals(HttpStatusCode.OK, get("/v1/account", next.accessToken).status)
        assertEquals(HttpStatusCode.OK, post("/v1/auth/refresh", """{"refreshToken":"${next.refreshToken}"}""").status)
    }

    @Test
    fun `un token de renovación ya usado cierra todas las sesiones de la cuenta`() = apiTest {
        val phone = register()
        val tablet = login(phone.email, phone.password).session()
        val renewed = post("/v1/auth/refresh", """{"refreshToken":"${phone.refreshToken}"}""").session()

        val reused = post("/v1/auth/refresh", """{"refreshToken":"${phone.refreshToken}"}""")

        assertEquals(HttpStatusCode.Unauthorized, reused.status)
        assertEquals("""{"code":"invalid_refresh_token"}""", reused.bodyAsText())
        assertEquals(HttpStatusCode.Unauthorized, post("/v1/auth/refresh", """{"refreshToken":"${renewed.refreshToken}"}""").status)
        assertEquals(HttpStatusCode.Unauthorized, post("/v1/auth/refresh", """{"refreshToken":"${tablet.refreshToken}"}""").status)
    }

    @Test
    fun `el token de renovación vence a los 30 días`() = apiTest {
        val ana = register()

        clock.advance(Duration.ofDays(30))

        assertEquals(HttpStatusCode.Unauthorized, post("/v1/auth/refresh", """{"refreshToken":"${ana.refreshToken}"}""").status)
    }

    @Test
    fun `cerrar sesión revoca solo ese token de renovación`() = apiTest {
        val phone = register()
        val tablet = login(phone.email, phone.password).session()

        assertEquals(HttpStatusCode.NoContent, post("/v1/auth/logout", """{"refreshToken":"${phone.refreshToken}"}""").status)

        assertEquals(HttpStatusCode.Unauthorized, post("/v1/auth/refresh", """{"refreshToken":"${phone.refreshToken}"}""").status)
        // Un token cerrado no es uno copiado: las demás sesiones siguen.
        assertEquals(HttpStatusCode.OK, post("/v1/auth/refresh", """{"refreshToken":"${tablet.refreshToken}"}""").status)
        assertEquals(HttpStatusCode.NoContent, post("/v1/auth/logout", """{"refreshToken":"${phone.refreshToken}"}""").status)
        assertEquals(HttpStatusCode.NoContent, post("/v1/auth/logout", """{"refreshToken":"desconocido"}""").status)
    }
}
