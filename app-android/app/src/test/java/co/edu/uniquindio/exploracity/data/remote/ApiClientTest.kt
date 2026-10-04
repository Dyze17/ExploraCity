package co.edu.uniquindio.exploracity.data.remote

import co.edu.uniquindio.exploracity.domain.model.City
import co.edu.uniquindio.exploracity.domain.model.GeoBounds
import co.edu.uniquindio.exploracity.domain.model.GeoPoint
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** El cliente de la API: la ciudad según el contrato y los errores como [ApiException]. */
class ApiClientTest {

    private val requested = mutableListOf<String>()

    private fun api(handler: MockRequestHandler) = CityApi(
        apiHttpClient(
            "http://localhost:8080/",
            MockEngine { request ->
                requested += request.url.toString()
                handler(request)
            },
        ),
    )

    private val json = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    @Test
    fun `lee la ciudad tal como la describe el contrato`() = runTest {
        val city = api { respond(contractExample("city.json"), HttpStatusCode.OK, json) }.city()

        assertEquals(
            City("Armenia", GeoPoint(4.5339, -75.6811), GeoBounds(GeoPoint(4.47, -75.76), GeoPoint(4.6, -75.62))),
            city,
        )
        assertEquals(listOf("http://localhost:8080/v1/city"), requested)
    }

    @Test
    fun `un error de la API llega con su código`() = runTest {
        val error = failure { api { respond("""{"code":"forbidden"}""", HttpStatusCode.Forbidden, json) }.city() }

        assertEquals(403, error.status)
        assertEquals("forbidden", error.code)
    }

    @Test
    fun `una respuesta de error sin el cuerpo de la API queda con el código HTTP`() = runTest {
        val error = failure { api { respond("Bad Gateway", HttpStatusCode.BadGateway) }.city() }

        assertEquals(502, error.status)
        assertEquals("http_502", error.code)
    }

    private suspend fun failure(block: suspend () -> Unit): ApiException =
        runCatching { block() }.exceptionOrNull() as? ApiException ?: throw AssertionError("Se esperaba ApiException")
}
