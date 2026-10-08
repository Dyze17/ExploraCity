package co.edu.uniquindio.exploracity.data.repository

import co.edu.uniquindio.exploracity.data.local.AuthTokens
import co.edu.uniquindio.exploracity.data.local.SessionAccount
import co.edu.uniquindio.exploracity.data.remote.AuthApi
import co.edu.uniquindio.exploracity.data.remote.FakeApi
import co.edu.uniquindio.exploracity.data.remote.SentRequest
import co.edu.uniquindio.exploracity.data.remote.TestSession
import co.edu.uniquindio.exploracity.data.remote.contractExample
import co.edu.uniquindio.exploracity.data.remote.error
import co.edu.uniquindio.exploracity.data.remote.example
import co.edu.uniquindio.exploracity.data.remote.failure
import co.edu.uniquindio.exploracity.data.remote.json
import co.edu.uniquindio.exploracity.data.remote.randomSecret
import co.edu.uniquindio.exploracity.domain.model.Account
import co.edu.uniquindio.exploracity.domain.model.EmailTakenException
import co.edu.uniquindio.exploracity.domain.model.GoogleSignInOutcome
import co.edu.uniquindio.exploracity.domain.model.GoogleSignInUnavailableException
import co.edu.uniquindio.exploracity.domain.model.GoogleTokenRejectedException
import co.edu.uniquindio.exploracity.domain.model.InvalidCredentialsException
import co.edu.uniquindio.exploracity.domain.model.Registration
import co.edu.uniquindio.exploracity.domain.model.Residency
import co.edu.uniquindio.exploracity.domain.model.UnconfirmedRegistrationException
import co.edu.uniquindio.exploracity.domain.model.UserRole
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.SocketTimeoutException

/** ADR-15 · Entrar con Google con la API: qué sigue tras elegir la cuenta, la sesión que se guarda y sus errores. */
class ApiGoogleAuthRepositoryTest {

    private val stores = TestSession()

    // Sin tokens ni contraseñas escritos en el código (GitGuardian): se crean al ejecutar.
    private val idToken = randomSecret()
    private val password = randomSecret()

    private lateinit var api: FakeApi

    private fun auth(handler: MockRequestHandleScope.(SentRequest) -> HttpResponseData): ApiAuthRepository {
        api = FakeApi(handler)
        return ApiAuthRepository(AuthApi(api.sessionClient(stores)), stores.session)
    }

    @Test
    fun `con la cuenta de Google vinculada guarda la sesión y dice si la cuenta tiene contraseña`() = runTest {
        val body = contractExample("session.json").replace("\"hasPassword\": true", "\"hasPassword\": false")
        val repository = auth { json(body) }

        val outcome = repository.signInWithGoogle(idToken)

        assertEquals(GoogleSignInOutcome.SignedIn(UserRole.USER), outcome)
        assertEquals(AuthTokens("token-de-acceso", "token-de-renovacion"), stores.tokens.tokens())
        assertEquals(
            SessionAccount("3f6c1e2a-8b4d-4c7e-9a1f-2d5b7e9c0a14", Account("ana.rios@correo.com", hasPassword = false)),
            stores.accounts.account.first(),
        )
        val sent = api.requests.single()
        assertEquals("POST", sent.method)
        assertEquals("/v1/auth/google", sent.path)
        assertEquals("""{"idToken":"$idToken"}""", sent.body)
        assertNull(sent.authorization)
    }

    @Test
    fun `una cuenta nueva y un correo con contraseña no son fallos, dicen qué sigue`() = runTest {
        val new = auth {
            json("""{"code":"registration_required","email":"pedro@gmail.com","name":"Pedro Gómez"}""", HttpStatusCode.NotFound)
        }.signInWithGoogle(idToken)
        val withPassword = auth { json("""{"code":"link_required","email":"ana@correo.com"}""", HttpStatusCode.Conflict) }.signInWithGoogle(idToken)

        assertEquals(GoogleSignInOutcome.RegistrationRequired("pedro@gmail.com", "Pedro Gómez"), new)
        assertEquals(GoogleSignInOutcome.LinkRequired("ana@correo.com"), withPassword)
        assertNull(stores.tokens.tokens())
    }

    @Test
    fun `una cuenta de Google sin nombre llega al registro sin nombre escrito`() = runTest {
        val outcome = auth { json("""{"code":"registration_required","email":"pedro@gmail.com"}""", HttpStatusCode.NotFound) }.signInWithGoogle(idToken)

        assertEquals(GoogleSignInOutcome.RegistrationRequired("pedro@gmail.com", null), outcome)
    }

    @Test
    fun `registrarse con Google manda el nombre y la residencia y abre la sesión`() = runTest {
        val repository = auth { example("register.json", HttpStatusCode.Created) }

        val registration = repository.registerWithGoogle(idToken, " Pedro Gómez ", Residency.RESIDENT)

        assertEquals(Registration(UserRole.USER, welcomeEmailSent = true), registration)
        assertEquals(AuthTokens("token-de-acceso", "token-de-renovacion"), stores.tokens.tokens())
        assertEquals(
            """{"idToken":"$idToken","registration":{"name":"Pedro Gómez","residency":"RESIDENT"}}""",
            api.requests.single().body,
        )
    }

    @Test
    fun `al registrarse, un correo que tomó cuenta o una respuesta que no llegó se explican`() = runTest {
        val taken = failure {
            auth { json("""{"code":"link_required","email":"pedro@gmail.com"}""", HttpStatusCode.Conflict) }
                .registerWithGoogle(idToken, "Pedro", Residency.VISITOR)
        }
        val noAnswer = failure { auth { throw SocketTimeoutException("timeout") }.registerWithGoogle(idToken, "Pedro", Residency.VISITOR) }

        assertTrue(taken is EmailTakenException)
        assertTrue(noAnswer is UnconfirmedRegistrationException)
        assertNull(stores.tokens.tokens())
    }

    @Test
    fun `vincular manda la contraseña y abre la sesión, si no coincide lo dice`() = runTest {
        val role = auth { example("session.json") }.linkGoogle(idToken, password)

        assertEquals(UserRole.USER, role)
        val sent = api.requests.single()
        assertEquals("/v1/auth/google/link", sent.path)
        assertEquals("""{"idToken":"$idToken","password":"$password"}""", sent.body)
        assertTrue(stores.accounts.account.first()!!.account.hasPassword)

        val wrong = failure { auth { error(HttpStatusCode.Unauthorized, "invalid_credentials") }.linkGoogle(idToken, password) }
        assertTrue(wrong is InvalidCredentialsException)
        // El 401 de vincular no es la señal de renovar el token: una sola petición.
        assertEquals(1, api.requests.size)
    }

    @Test
    fun `un token vencido, Google sin configurar y una cuenta de Google en uso se explican`() = runTest {
        val rejected = failure { auth { error(HttpStatusCode.Unauthorized, "invalid_google_token") }.signInWithGoogle(idToken) }
        val unavailable = failure { auth { error(HttpStatusCode.ServiceUnavailable, "google_sign_in_unavailable") }.signInWithGoogle(idToken) }
        val inUse = failure { auth { error(HttpStatusCode.Conflict, "google_account_in_use") }.linkGoogle(idToken, password) }
        val taken = failure { auth { error(HttpStatusCode.Conflict, "email_taken") }.signInWithGoogle(idToken) }

        assertTrue(rejected is GoogleTokenRejectedException)
        assertTrue(unavailable is GoogleSignInUnavailableException)
        assertTrue(inUse is EmailTakenException)
        assertTrue(taken is EmailTakenException)
        assertNull(stores.tokens.tokens())
    }
}
