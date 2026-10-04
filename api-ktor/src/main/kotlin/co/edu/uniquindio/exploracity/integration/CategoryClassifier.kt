package co.edu.uniquindio.exploracity.integration

import co.edu.uniquindio.exploracity.model.Category
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.Normalizer
import java.util.Locale

/** El servicio de IA falló o no respondió a tiempo: la app sigue con la elección manual (ADR-12). */
class ClassifierException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** C1 · 16 · Sugerencia de categoría (Componente de Clasificación IA, ADR-11): OpenRouter con su clave; sin ella, palabras clave. */
interface CategoryClassifier {
    /** La categoría más probable o null si no hay una clara. Lanza [ClassifierException] si el servicio falla. */
    suspend fun classify(title: String, description: String): Category?
}

/**
 * DeepSeek (u otro [model]) por OpenRouter, con un tiempo límite menor que los 5 s que espera la app (16): así la app
 * recibe la respuesta, o el aviso de que no la hay, antes de rendirse.
 */
class OpenRouterClassifier(
    private val http: HttpClient,
    private val apiKey: String,
    private val model: String,
    private val timeoutMillis: Long = TIMEOUT_MILLIS,
    private val endpoint: String = ENDPOINT,
) : CategoryClassifier {

    override suspend fun classify(title: String, description: String): Category? {
        val request = ChatRequest(
            model = model,
            messages = listOf(
                ChatMessage("system", PROMPT),
                ChatMessage("user", "Título: ${title.trim()}\nDescripción: ${description.trim()}"),
            ),
            temperature = 0.0,
            maxTokens = 5,
        )
        val response = try {
            http.post(endpoint) {
                bearerAuth(apiKey)
                header("X-Title", "ExploraCity")
                contentType(ContentType.Application.Json)
                setBody(request)
                timeout { requestTimeoutMillis = timeoutMillis }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw ClassifierException("OpenRouter no respondió a tiempo", e)
        }
        if (!response.status.isSuccess()) throw ClassifierException("OpenRouter respondió ${response.status.value}")
        val answer = try {
            response.body<ChatResponse>().choices.firstOrNull()?.message?.content.orEmpty()
        } catch (e: Exception) {
            throw ClassifierException("La respuesta de OpenRouter no se entiende", e)
        }
        return parse(answer)
    }

    companion object {
        private const val ENDPOINT = "https://openrouter.ai/api/v1/chat/completions"

        /** La app espera 5 s (16.c); la API contesta antes. */
        const val TIMEOUT_MILLIS = 4_000L

        private val PROMPT = """
            Clasificas lugares turísticos de una ciudad colombiana en una de estas categorías:
            GASTRONOMY (restaurantes, cafés, panaderías, mercados de comida),
            CULTURE (museos de arte, galerías, teatros, bibliotecas, murales),
            NATURE (parques, senderos, miradores, humedales, jardines),
            ENTERTAINMENT (bares, conciertos, ferias, juegos, vida nocturna),
            HISTORY (plazas, iglesias, casas antiguas, monumentos, sitios históricos).
            Responde solo con el nombre de la categoría en inglés y en mayúsculas, o NONE si ninguna es clara.
        """.trimIndent()

        /** La primera palabra de la respuesta, si es una categoría; NONE o cualquier otra cosa, ninguna. */
        fun parse(answer: String): Category? {
            val word = answer.trim().uppercase(Locale.ROOT).takeWhile { it.isLetter() || it == '_' }
            return Category.entries.firstOrNull { it.name == word }
        }
    }
}

/**
 * Sin OpenRouter: cuenta palabras clave de cada categoría en el título y la descripción, como hacía la app con datos de
 * prueba. Empates o ninguna coincidencia: sin sugerencia.
 */
class KeywordClassifier : CategoryClassifier {

    override suspend fun classify(title: String, description: String): Category? {
        val words = "$title $description".normalized().split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }.toSet()
        val scores = KEYWORDS.mapValues { (_, list) -> list.count { it in words } }.filterValues { it > 0 }
        val best = scores.maxOfOrNull { it.value } ?: return null
        return scores.filterValues { it == best }.keys.singleOrNull()
    }

    private companion object {
        val KEYWORDS: Map<Category, List<String>> = mapOf(
            Category.GASTRONOMY to listOf(
                "cafe", "restaurante", "panaderia", "pan", "tamales", "tamal", "comida", "almuerzo", "chocolate", "arepa",
                "ajiaco", "postres", "cocina", "sabor", "tostion", "cerveza", "fruta", "frutas", "almojabanas",
            ),
            Category.CULTURE to listOf(
                "galeria", "mural", "murales", "teatro", "arte", "biblioteca", "cine", "exposicion", "artesanias", "libros", "musica",
            ),
            Category.NATURE to listOf(
                "sendero", "parque", "humedal", "cerro", "mirador", "jardin", "bosque", "quebrada", "montana", "aves", "lago",
                "caminata", "niebla", "rio", "naturaleza",
            ),
            Category.ENTERTAINMENT to listOf("bar", "discoteca", "concierto", "juegos", "feria", "karaoke", "baile", "fiesta", "bolos", "pulgas"),
            Category.HISTORY to listOf(
                "casa", "museo", "plaza", "iglesia", "catedral", "historico", "historica", "colonial", "quinta", "independencia",
                "historia", "monumento", "siglo", "fundacion",
            ),
        )

        val diacritics = Regex("\\p{Mn}+")

        fun String.normalized(): String = Normalizer.normalize(lowercase(Locale.ROOT), Normalizer.Form.NFD).replace(diacritics, "")
    }
}

@Serializable
private data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double,
    @SerialName("max_tokens") val maxTokens: Int,
)

@Serializable
private data class ChatMessage(val role: String, val content: String)

@Serializable
private data class ChatResponse(val choices: List<ChatChoice> = emptyList())

@Serializable
private data class ChatChoice(val message: ChatMessage? = null)
