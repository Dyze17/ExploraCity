package co.edu.uniquindio.exploracity.data.repository

import co.edu.uniquindio.exploracity.data.local.AuthTokens
import co.edu.uniquindio.exploracity.data.local.SessionAccount
import co.edu.uniquindio.exploracity.data.remote.ApiException
import co.edu.uniquindio.exploracity.data.remote.AuthApi
import co.edu.uniquindio.exploracity.data.remote.FakeApi
import co.edu.uniquindio.exploracity.data.remote.SentRequest
import co.edu.uniquindio.exploracity.data.remote.TestSession
import co.edu.uniquindio.exploracity.data.remote.contractExample
import co.edu.uniquindio.exploracity.data.remote.empty
import co.edu.uniquindio.exploracity.data.remote.error
import co.edu.uniquindio.exploracity.data.remote.example
import co.edu.uniquindio.exploracity.data.remote.failure
import co.edu.uniquindio.exploracity.data.remote.json
import co.edu.uniquindio.exploracity.domain.model.Account
import co.edu.uniquindio.exploracity.domain.model.EmailDeliveryException
import co.edu.uniquindio.exploracity.domain.model.EmailTakenException
import co.edu.uniquindio.exploracity.domain.model.ExpiredLinkException
import co.edu.uniquindio.exploracity.domain.model.InvalidCredentialsException
import co.edu.uniquindio.exploracity.domain.model.NewAccount
import co.edu.uniquindio.exploracity.domain.model.Registration
import co.edu.uniquindio.exploracity.domain.model.ResetLink
import co.edu.uniquindio.exploracity.domain.model.Residency
import co.edu.uniquindio.exploracity.domain.model.SessionEndedException
import co.edu.uniquindio.exploracity.domain.model.TooManyAttemptsException
import co.edu.uniquindio.exploracity.domain.model.UnconfirmedRegistrationException
import co.edu.uniquindio.exploracity.domain.model.UserRole
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.Instant
import java.util.UUID

/** 1, 3, 4, 5, 6 y 6C con la API: la sesión que se guarda y los errores que ya explican las pantallas. */
class ApiAuthRepositoryTest {

    private val stores = TestSession()

    // Sin contraseñas escritas en el código (GitGuardian): una cualquiera que cumpla la regla.
    private val password = "a".repeat(7) + "1"

    private lateinit var api: FakeApi

    private fun auth(handler: MockRequestHandleScope.(SentRequest) -> HttpResponseData): ApiAuthRepository {
        api = FakeApi(handler)
        return ApiAuthRepository(AuthApi(api.sessionClient(stores)), stores.session)
    }

    private val clientId = UUID.randomUUID().toString()

    private val ana = SessionAccount("3f6c1e2a-8b4d-4c7e-9a1f-2d5b7e9c0a14", Account("ana.rios@correo.com"))

    @Test
    fun `entrar guarda los tokens y la cuenta, y devuelve el rol`() = runTest {
        val repository = auth { example("session.json") }

        val role = repository.signIn("  ana.rios@correo.com ", password)

        assertEquals(UserRole.USER, role)
        assertEquals(AuthTokens("token-de-acceso", "token-de-renovacion"), stores.tokens.tokens())
        assertEquals(ana, stores.accounts.account.first())
        val sent = api.requests.single()
        assertEquals("POST", sent.method)
        assertEquals("/v1/auth/login", sent.path)
        assertEquals("""{"email":"ana.rios@correo.com","password":"$password"}""", sent.body)
        // Una ruta pública no lleva el token de otra sesión.
        assertNull(sent.authorization)
    }

    @Test
    fun `credenciales que no coinciden y demasiados intentos se explican sin renovar nada`() = runTest {
        stores.tokens.save(AuthTokens("acceso-viejo", "renovacion-vieja"))

        val wrong = failure { auth { error(HttpStatusCode.Unauthorized, "invalid_credentials") }.signIn("ana@correo.com", password) }
        val locked = failure { auth { error(HttpStatusCode.TooManyRequests, "too_many_attempts") }.signIn("ana@correo.com", password) }

        assertTrue(wrong is InvalidCredentialsException)
        assertTrue(locked is TooManyAttemptsException)
        // El 401 de entrar no es la señal de renovar el token: una sola petición.
        assertEquals(1, api.requests.size)
    }

    @Test
    fun `registrarse abre la sesión y dice si salió la bienvenida`() = runTest {
        val repository = auth { example("register.json", HttpStatusCode.Created) }

        val registration = repository.register(NewAccount(" Ana Ríos ", " ana.rios@correo.com", password, Residency.RESIDENT, clientId))

        assertEquals(Registration(UserRole.USER, welcomeEmailSent = true), registration)
        assertEquals(ana, stores.accounts.account.first())
        assertEquals(
            """{"name":"Ana Ríos","email":"ana.rios@correo.com","password":"$password","residency":"RESIDENT","clientId":"$clientId"}""",
            api.requests.single().body,
        )
    }

    @Test
    fun `repetir un registro que ya llegó abre la sesión en esa cuenta`() = runTest {
        // La API responde 200 con la sesión, sin welcomeEmailSent: la bienvenida la envió el primer intento.
        val repository = auth { example("session.json") }

        val registration = repository.register(NewAccount("Ana Ríos", "ana.rios@correo.com", password, Residency.RESIDENT, clientId))

        assertEquals(Registration(UserRole.USER, welcomeEmailSent = true), registration)
        assertEquals(AuthTokens("token-de-acceso", "token-de-renovacion"), stores.tokens.tokens())
    }

    @Test
    fun `si la bienvenida no salió, la cuenta igual queda lista`() = runTest {
        val body = contractExample("register.json").replace("\"welcomeEmailSent\": true", "\"welcomeEmailSent\": false")
        val repository = auth { json(body, HttpStatusCode.Created) }

        val registration = repository.register(NewAccount("Ana Ríos", "ana.rios@correo.com", password, Residency.RESIDENT))

        assertEquals(Registration(UserRole.USER, welcomeEmailSent = false), registration)
    }

    @Test
    fun `si la API no respondió, no se sabe si la cuenta quedó creada`() = runTest {
        val account = NewAccount("Ana Ríos", "ana.rios@correo.com", password, Residency.RESIDENT)
        val noAnswer = listOf(
            HttpRequestTimeoutException("http://localhost:8080/v1/auth/register", 20_000),
            SocketTimeoutException("timeout"),
            IOException("unexpected end of stream"),
        )
        val neverArrived = listOf(ConnectException("Failed to connect"), ConnectTimeoutException("connect timeout"), UnknownHostException("api"))

        for (cause in noAnswer) {
            val error = failure { auth { throw cause }.register(account) }
            assertTrue("$cause", error is UnconfirmedRegistrationException)
        }
        // Un pedido que ni siquiera conectó nunca llegó: la cuenta no se creó.
        for (cause in neverArrived) {
            val error = failure { auth { throw cause }.register(account) }
            assertTrue("$cause", error !is UnconfirmedRegistrationException && error is IOException)
        }
        assertNull(stores.tokens.tokens())
    }

    @Test
    fun `un correo con cuenta no se registra`() = runTest {
        val error = failure {
            auth { error(HttpStatusCode.Conflict, "email_taken") }
                .register(NewAccount("Ana Ríos", "ana.rios@correo.com", password, Residency.RESIDENT))
        }

        assertTrue(error is EmailTakenException)
        assertNull(stores.tokens.tokens())
    }

    @Test
    fun `pedir el enlace y la caída del correo`() = runTest {
        auth { empty(HttpStatusCode.Accepted) }.requestPasswordReset(" ana.rios@correo.com ")
        assertEquals("""{"email":"ana.rios@correo.com"}""", api.requests.single().body)

        val down = failure { auth { error(HttpStatusCode.ServiceUnavailable, "email_delivery_failed") }.requestPasswordReset("ana@correo.com") }
        assertTrue(down is EmailDeliveryException)
    }

    @Test
    fun `abrir el enlace dice para quién es, y uno vencido trae el correo para pedir otro`() = runTest {
        val link = auth { example("reset-link.json") }.openResetLink("token-del-enlace")
        assertEquals(ResetLink("ana.rios@correo.com", Instant.parse("2026-10-03T15:30:00Z")), link)
        assertEquals("""{"token":"token-del-enlace"}""", api.requests.single().body)

        val expired = failure { auth { example("link-expired.json", HttpStatusCode.Gone) }.openResetLink("vencido") }
        assertEquals("ana.rios@correo.com", (expired as ExpiredLinkException).email)

        val unknown = failure { auth { error(HttpStatusCode.Gone, "link_expired") }.resetPassword("desconocido", password) }
        assertEquals("", (unknown as ExpiredLinkException).email)
    }

    @Test
    fun `guardar la contraseña nueva`() = runTest {
        auth { empty() }.resetPassword("token-del-enlace", password)

        assertEquals("/v1/auth/password/reset", api.requests.single().path)
        assertEquals("""{"token":"token-del-enlace","password":"$password"}""", api.requests.single().body)
    }

    @Test
    fun `al abrir la app la sesión se renueva y se guarda el par nuevo`() = runTest {
        stores.tokens.save(AuthTokens("acceso-viejo", "renovacion-vieja"))
        val repository = auth { example("session.json") }

        repository.resumeSession()

        assertEquals("""{"refreshToken":"renovacion-vieja"}""", api.requests.single().body)
        assertEquals(AuthTokens("token-de-acceso", "token-de-renovacion"), stores.tokens.tokens())
    }

    @Test
    fun `una sesión que la API ya no acepta termina y se borra`() = runTest {
        stores.session.start(Json.decodeFromString(contractExample("session.json")))

        val error = failure { auth { error(HttpStatusCode.Unauthorized, "invalid_refresh_token") }.resumeSession() }

        assertTrue(error is SessionEndedException)
        assertNull(stores.tokens.tokens())
        assertNull(stores.accounts.account.first())
        assertTrue(failure { auth { example("session.json") }.resumeSession() } is SessionEndedException)
    }

    @Test
    fun `sin red la sesión guardada se conserva`() = runTest {
        stores.tokens.save(AuthTokens("acceso-viejo", "renovacion-vieja"))

        val error = failure { auth { throw IOException("sin red") }.resumeSession() }

        assertTrue(error is IOException)
        assertEquals(AuthTokens("acceso-viejo", "renovacion-vieja"), stores.tokens.tokens())
    }

    @Test
    fun `cerrar sesión avisa a la API y borra la sesión, también si la API falla`() = runTest {
        stores.session.start(Json.decodeFromString(contractExample("session.json")))
        auth { empty() }.signOut()
        assertEquals("""{"refreshToken":"token-de-renovacion"}""", api.requests.single().body)
        assertNull(stores.tokens.tokens())

        stores.session.start(Json.decodeFromString(contractExample("session.json")))
        auth { error(HttpStatusCode.InternalServerError, "internal_error") }.signOut()
        assertNull(stores.tokens.tokens())
    }

    @Test
    fun `los códigos que la app no explica llegan tal cual`() = runTest {
        val error = failure { auth { error(HttpStatusCode.BadRequest, "weak_password") }.resetPassword("token", "corta") }

        assertEquals("weak_password", (error as ApiException).code)
    }
}
