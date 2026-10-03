package co.edu.uniquindio.exploracity.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.auth.AuthCircuitBreaker
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * La API respondió con un error (docs/api): [status] HTTP y [code] estable, que cada repositorio traduce a su
 * excepción del dominio. [email] solo lo trae un enlace vencido (6C). Sin red no llega aquí: eso es una IOException.
 */
class ApiException(val status: Int, val code: String, val email: String? = null) : Exception("La API respondió $status ($code)")

/** Cuerpo de error de la API. */
@Serializable
internal data class ErrorBody(val code: String, val email: String? = null)

/** JSON de la API: lo que la app no conoce se ignora, así un campo nuevo no la rompe. */
internal val ApiJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

/**
 * Cliente de la API (Ktor Client, SAD: data/remote). [baseUrl] termina en «/» y las rutas se piden sin «/» al inicio
 * («v1/city»). Una respuesta de error se convierte en [ApiException]. [configure] suma plugins, como la sesión.
 */
fun apiHttpClient(
    baseUrl: String,
    engine: HttpClientEngine,
    configure: HttpClientConfig<*>.() -> Unit = {},
): HttpClient = HttpClient(engine) {
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
            val body = runCatching { response.body<ErrorBody>() }.getOrNull()
            throw ApiException(response.status.value, body?.code ?: "http_${response.status.value}", body?.email)
        }
    }
    configure()
}

/**
 * Una ruta pública (entrar, registrarse, recuperar la contraseña): no lleva el token de la sesión, y su 401 es una
 * respuesta, no la señal de renovarlo.
 */
internal fun HttpRequestBuilder.withoutSession() {
    attributes.put(AuthCircuitBreaker, Unit)
}

/** Un cuerpo JSON. */
internal inline fun <reified T> HttpRequestBuilder.jsonBody(body: T) {
    contentType(ContentType.Application.Json)
    setBody(body)
}

private const val CONNECT_TIMEOUT_MS = 10_000L
private const val REQUEST_TIMEOUT_MS = 20_000L
