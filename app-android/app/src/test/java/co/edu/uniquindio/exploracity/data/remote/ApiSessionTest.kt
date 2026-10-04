package co.edu.uniquindio.exploracity.data.remote

import co.edu.uniquindio.exploracity.data.local.AuthTokens
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** D1 · El cliente con la sesión: lleva el token de acceso y, con un 401, lo renueva una vez y repite la petición. */
class ApiSessionTest {

    private val stores = TestSession()

    /** El 401 de un token de acceso vencido, con la señal para renovarlo. */
    private val expired = headersOf(
        HttpHeaders.WWWAuthenticate to listOf("""Bearer realm="exploracity""""),
        HttpHeaders.ContentType to listOf(ContentType.Application.Json.toString()),
    )

    @Test
    fun `cada petición lleva el token de acceso guardado`() = runTest {
        stores.tokens.save(AuthTokens("acceso", "renovacion"))
        val api = FakeApi { example("account.json") }

        AccountApi(api.sessionClient(stores)).account()

        assertEquals("Bearer acceso", api.requests.single().authorization)
    }

    @Test
    fun `con el token vencido lo renueva, guarda el par nuevo y repite la petición`() = runTest {
        stores.tokens.save(AuthTokens("acceso-vencido", "renovacion-vieja"))
        val api = FakeApi { sent ->
            when {
                sent.path == "/v1/auth/refresh" -> example("session.json")
                sent.authorization == "Bearer acceso-vencido" -> respond("""{"code":"unauthorized"}""", HttpStatusCode.Unauthorized, expired)
                else -> example("account.json")
            }
        }

        val account = AccountApi(api.sessionClient(stores)).account()

        assertEquals("ana.nueva@correo.com", account.pendingEmail)
        assertEquals(listOf("/v1/account", "/v1/auth/refresh", "/v1/account"), api.requests.map { it.path })
        assertEquals("""{"refreshToken":"renovacion-vieja"}""", api.requests[1].body)
        assertEquals("Bearer token-de-acceso", api.requests[2].authorization)
        assertEquals(AuthTokens("token-de-acceso", "token-de-renovacion"), stores.tokens.tokens())
        assertEquals("ana.rios@correo.com", stores.accounts.account.first()?.account?.email)
    }

    @Test
    fun `si la API ya no acepta la renovación, la sesión termina y la petición falla con su 401`() = runTest {
        stores.session.start(Json.decodeFromString(contractExample("session.json")))
        var ended = false
        val api = FakeApi { sent ->
            if (sent.path == "/v1/auth/refresh") {
                error(HttpStatusCode.Unauthorized, "invalid_refresh_token")
            } else {
                respond("""{"code":"unauthorized"}""", HttpStatusCode.Unauthorized, expired)
            }
        }

        val error = failure { AccountApi(api.sessionClient(stores, onSessionEnded = { ended = true })).account() }

        assertEquals(401, (error as ApiException).status)
        assertEquals("unauthorized", error.code)
        assertTrue(ended)
        assertNull(stores.tokens.tokens())
        assertNull(stores.accounts.account.first())
    }

    @Test
    fun `al abrir otra sesión el cliente deja el token anterior`() = runTest {
        stores.tokens.save(AuthTokens("acceso-anterior", "renovacion-anterior"))
        val api = FakeApi { example("account.json") }
        val accounts = AccountApi(api.sessionClient(stores))
        accounts.account()

        stores.session.start(Json.decodeFromString(contractExample("session.json")))
        accounts.account()
        stores.session.end()
        accounts.account()

        assertEquals(listOf("Bearer acceso-anterior", "Bearer token-de-acceso", null), api.requests.map { it.authorization })
    }
}
