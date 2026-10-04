package co.edu.uniquindio.exploracity.integration

import co.edu.uniquindio.exploracity.model.Category
import co.edu.uniquindio.exploracity.support.randomSecret
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** C1 · 16 · La sugerencia de categoría: OpenRouter con su clave y las palabras clave sin ella. */
class CategoryClassifierTest {

    private val apiKey = randomSecret()
    private val json = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    private fun openRouter(timeoutMillis: Long = 4_000, handler: MockRequestHandler): OpenRouterClassifier {
        val http = HttpClient(MockEngine(handler)) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            install(HttpTimeout)
        }
        return OpenRouterClassifier(http, apiKey, "deepseek/deepseek-chat", timeoutMillis)
    }

    private fun answer(content: String) = """{"choices":[{"message":{"role":"assistant","content":"$content"}}]}"""

    @Test
    fun `OpenRouter recibe el título y la descripción y responde una categoría`() = runBlocking {
        var auth: String? = null
        var body = ""
        val classifier = openRouter { request ->
            auth = request.headers[HttpHeaders.Authorization]
            body = String(request.body.toByteArray())
            respond(answer("NATURE"), HttpStatusCode.OK, json)
        }

        val category = classifier.classify("Mirador de la Secreta", "Vista a toda la ciudad.")

        assertEquals(Category.NATURE, category)
        assertEquals("Bearer $apiKey", auth)
        val request = Json.parseToJsonElement(body).jsonObject
        assertEquals("deepseek/deepseek-chat", request["model"]!!.jsonPrimitive.content)
        val messages = request["messages"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("system", "user"), messages.map { it["role"]!!.jsonPrimitive.content })
        assertTrue("Título: Mirador de la Secreta" in messages[1]["content"]!!.jsonPrimitive.content)
    }

    @Test
    fun `sin una categoría clara no sugiere nada`() = runBlocking {
        assertNull(openRouter { respond(answer("NONE"), HttpStatusCode.OK, json) }.classify("Lugar", "Bonito"))
        assertNull(openRouter { respond(answer("No sé"), HttpStatusCode.OK, json) }.classify("Lugar", "Bonito"))
        assertEquals(Category.HISTORY, OpenRouterClassifier.parse(" history.\n"))
    }

    @Test
    fun `si OpenRouter falla, tarda o responde algo raro, la app sigue a mano`() = runBlocking<Unit> {
        assertFailsWith<ClassifierException> { openRouter { respond("", HttpStatusCode.TooManyRequests) }.classify("a", "b") }
        assertFailsWith<ClassifierException> { openRouter { respond("no es json", HttpStatusCode.OK, json) }.classify("a", "b") }
        assertFailsWith<ClassifierException> {
            openRouter(timeoutMillis = 50) {
                delay(1_000)
                respond(answer("NATURE"), HttpStatusCode.OK, json)
            }.classify("a", "b")
        }
    }

    @Test
    fun `sin OpenRouter la sugerencia sale de palabras clave, sin tildes`() = runBlocking {
        val classifier = KeywordClassifier()

        assertEquals(Category.GASTRONOMY, classifier.classify("Café de la Estación", "Tostión propia y postres."))
        assertEquals(Category.NATURE, classifier.classify("Sendero a la MONTAÑA", "Caminata entre la niebla."))
        assertNull(classifier.classify("Lugar bonito", "Hay que ir."))
        // Empate: sin sugerencia.
        assertNull(classifier.classify("Museo y café", "Un museo con café."))
    }
}
