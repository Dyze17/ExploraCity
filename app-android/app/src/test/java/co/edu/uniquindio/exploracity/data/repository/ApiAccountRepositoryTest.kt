package co.edu.uniquindio.exploracity.data.repository

import co.edu.uniquindio.exploracity.data.remote.AccountApi
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
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 29, 30 y «Cambiar correo» con la API: el correo guardado con la sesión y lo que cambia al confirmarlo. */
class ApiAccountRepositoryTest {

    private val stores = TestSession()
    private val password = "a".repeat(7) + "1"
    private lateinit var api: FakeApi

    private suspend fun TestScope.accounts(
        scope: CoroutineScope = backgroundScope,
        handler: MockRequestHandleScope.(SentRequest) -> HttpResponseData,
    ): ApiAccountRepository {
        stores.session.start(Json.decodeFromString(contractExample("session.json")))
        api = FakeApi(handler)
        return ApiAccountRepository(AccountApi(api.sessionClient(stores)), stores.session, scope).also { runCurrent() }
    }

    @Test
    fun `el correo llega con la sesión y se ve sin pedirlo a la API`() = runTest {
        val repository = accounts { error(HttpStatusCode.InternalServerError, "internal_error") }

        assertEquals(Account("ana.rios@correo.com"), repository.account.value)
        assertTrue(api.requests.isEmpty())
    }

    @Test
    fun `pedir el cambio de correo lo deja pendiente`() = runTest {
        val repository = accounts { example("account.json", HttpStatusCode.Accepted) }

        repository.requestEmailChange(" ana.nueva@correo.com ", password)
        runCurrent()

        val sent = api.requests.single()
        assertEquals("/v1/account/email", sent.path)
        assertEquals("Bearer token-de-acceso", sent.authorization)
        assertEquals("""{"newEmail":"ana.nueva@correo.com","password":"$password"}""", sent.body)
        assertEquals(Account("ana.rios@correo.com", pendingEmail = "ana.nueva@correo.com"), repository.account.value)
    }

    @Test
    fun `los errores del cambio de correo son los de la pantalla`() = runTest {
        suspend fun attempt(status: HttpStatusCode, code: String) =
            failure { accounts { error(status, code) }.requestEmailChange("ana.nueva@correo.com", password) }

        assertTrue(attempt(HttpStatusCode.Forbidden, "invalid_credentials") is InvalidCredentialsException)
        assertTrue(attempt(HttpStatusCode.Conflict, "email_taken") is EmailTakenException)
        assertTrue(attempt(HttpStatusCode.ServiceUnavailable, "email_delivery_failed") is EmailDeliveryException)
        // Un 403 no es la señal de renovar el token: una sola petición.
        assertEquals(1, api.requests.size)
    }

    @Test
    fun `reenviar el enlace`() = runTest {
        accounts { empty(HttpStatusCode.Accepted) }.resendEmailChange()

        assertEquals("/v1/account/email/resend", api.requests.single().path)
    }

    @Test
    fun `confirmar el enlace cambia el correo de la sesión`() = runTest {
        val repository = accounts { json("""{"email":"ana.nueva@correo.com"}""") }

        val email = repository.confirmEmailChange("token-del-enlace")
        runCurrent()

        assertEquals("ana.nueva@correo.com", email)
        assertEquals(Account("ana.nueva@correo.com"), repository.account.value)
        assertEquals("""{"token":"token-del-enlace"}""", api.requests.single().body)
    }

    @Test
    fun `un enlace vencido trae el correo nuevo para pedir otro`() = runTest {
        val error = failure {
            accounts { json("""{"code":"link_expired","email":"ana.nueva@correo.com"}""", HttpStatusCode.Gone) }
                .confirmEmailChange("vencido")
        }

        assertEquals("ana.nueva@correo.com", (error as ExpiredLinkException).email)
    }

    @Test
    fun `descargar mis datos trae el archivo y el nombre que propone la API`() = runTest {
        val repository = accounts {
            respond(
                contractExample("export.json"),
                HttpStatusCode.OK,
                headersOf(
                    HttpHeaders.ContentType to listOf(ContentType.Application.Json.toString()),
                    HttpHeaders.ContentDisposition to listOf("attachment; filename=exploracity-mis-datos-2026-10-03.json"),
                ),
            )
        }

        val export = repository.exportData()

        assertEquals("exploracity-mis-datos-2026-10-03.json", export.fileName)
        assertEquals(contractExample("export.json"), export.content)
        assertEquals("/v1/account/export", api.requests.single().path)
    }

    @Test
    fun `eliminar la cuenta cierra la sesión en el teléfono`() = runTest {
        val repository = accounts { empty() }

        repository.deleteAccount()
        runCurrent()

        assertEquals("DELETE", api.requests.single().method)
        assertNull(stores.tokens.tokens())
        assertNull(stores.accounts.account.first())
        assertEquals("", repository.account.value.email)
    }

    @Test
    fun `si la eliminación falla, la sesión sigue`() = runTest {
        val repository = accounts { error(HttpStatusCode.InternalServerError, "internal_error") }

        failure { repository.deleteAccount() }

        assertEquals("token-de-acceso", stores.tokens.tokens()?.access)
    }
}
