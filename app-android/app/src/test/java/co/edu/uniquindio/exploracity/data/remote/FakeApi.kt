package co.edu.uniquindio.exploracity.data.remote

import co.edu.uniquindio.exploracity.data.local.DataStoreAccountStore
import co.edu.uniquindio.exploracity.data.local.DataStoreTokenStore
import co.edu.uniquindio.exploracity.data.local.MemoryDataStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.io.File
import java.net.URLDecoder
import java.util.UUID

/** Ejemplo del contrato (docs/api/ejemplos), el mismo que comprueban las pruebas de api-ktor. */
internal fun contractExample(name: String): String {
    val dir = System.getProperty("exploracity.contract") ?: error("Falta la propiedad exploracity.contract")
    return File(dir, name).readText()
}

/** Una petición que llegó a la API de prueba. */
internal data class SentRequest(
    val method: String,
    val path: String,
    /** La dirección completa, con la consulta. */
    val fullUrl: String,
    val body: String,
    val authorization: String?,
    val contentType: String?,
)

/** La consulta de la URL como mapa, sin codificar. */
internal val SentRequest.query: Map<String, String>
    get() = fullUrl.substringAfter('?', "").split('&').filter { it.isNotEmpty() }.associate { pair ->
        val (name, value) = pair.split('=', limit = 2)
        URLDecoder.decode(name, Charsets.UTF_8) to URLDecoder.decode(value, Charsets.UTF_8)
    }

/** Una API de prueba: responde con [handler] y anota cada petición. */
internal class FakeApi(private val handler: MockRequestHandleScope.(SentRequest) -> HttpResponseData) {
    val requests = mutableListOf<SentRequest>()

    val engine = MockEngine { request ->
        val sent = SentRequest(
            method = request.method.value,
            path = request.url.encodedPath,
            fullUrl = request.url.toString(),
            body = String(request.body.toByteArray()),
            authorization = request.headers[HttpHeaders.Authorization],
            contentType = request.body.contentType?.toString(),
        )
        requests += sent
        handler(sent)
    }

    /** El cliente sin sesión (como el de la ciudad). */
    fun client(): HttpClient = apiHttpClient(BASE_URL, engine)

    /** El cliente con la sesión de [stores]. */
    fun sessionClient(stores: TestSession, onSessionEnded: suspend () -> Unit = {}): HttpClient =
        sessionHttpClient(BASE_URL, engine, stores.session, onSessionEnded)

    companion object {
        const val BASE_URL = "http://localhost:8080/"
    }
}

/** La sesión de prueba, con su DataStore en memoria. */
internal class TestSession {
    val tokens = DataStoreTokenStore(MemoryDataStore())
    val accounts = DataStoreAccountStore(MemoryDataStore())
    val session = ApiSession(tokens, accounts)
}

private val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

internal fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK): HttpResponseData =
    respond(body, status, jsonHeaders)

internal fun MockRequestHandleScope.example(name: String, status: HttpStatusCode = HttpStatusCode.OK): HttpResponseData =
    json(contractExample(name), status)

internal fun MockRequestHandleScope.error(status: HttpStatusCode, code: String): HttpResponseData =
    json("""{"code":"$code"}""", status)

internal fun MockRequestHandleScope.empty(status: HttpStatusCode = HttpStatusCode.NoContent): HttpResponseData =
    respond("", status)

/** Un token de prueba, creado al ejecutar: las pruebas no llevan literales que parezcan secretos. */
internal fun randomSecret(): String = UUID.randomUUID().toString()

/** Lo que lanza [block]; falla si no lanza nada. */
internal suspend fun failure(block: suspend () -> Unit): Throwable =
    runCatching { block() }.exceptionOrNull() ?: throw AssertionError("Se esperaba una excepción")
