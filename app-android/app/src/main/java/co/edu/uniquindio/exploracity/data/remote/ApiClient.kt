package co.edu.uniquindio.exploracity.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * La API respondió con un error (docs/api): [status] HTTP y [code] estable, que cada repositorio traduce a su
 * excepción del dominio. Sin red no llega aquí: eso es una IOException.
 */
class ApiException(val status: Int, val code: String) : Exception("La API respondió $status ($code)")

/** Cuerpo de error de la API. */
@Serializable
internal data class ErrorBody(val code: String)

/** JSON de la API: lo que la app no conoce se ignora, así un campo nuevo no la rompe. */
internal val ApiJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

/**
 * Cliente de la API (Ktor Client, SAD: data/remote). [baseUrl] termina en «/» y las rutas se piden sin «/» al inicio
 * («v1/city»). Una respuesta de error se convierte en [ApiException].
 */
fun apiHttpClient(baseUrl: String, engine: HttpClientEngine): HttpClient = HttpClient(engine) {
    expectSuccess = true
    install(ContentNegotiation) { json(ApiJson) }
    install(HttpTimeout) {
        connectTimeoutMillis = CONNECT_TIMEOUT_MS
        requestTimeoutMillis = REQUEST_TIMEOUT_MS
    }
    defaultRequest { url(baseUrl) }
    HttpResponseValidator {
        handleResponseExceptionWithRequest { cause, _ ->
            val response = (cause as? ResponseException)?.response ?: return@handleResponseExceptionWithRequest
            // Un proxy o la nube pueden responder sin el cuerpo de la API: queda el código HTTP.
            val code = runCatching { response.body<ErrorBody>().code }.getOrNull() ?: "http_${response.status.value}"
            throw ApiException(response.status.value, code)
        }
    }
}

private const val CONNECT_TIMEOUT_MS = 10_000L
private const val REQUEST_TIMEOUT_MS = 20_000L
