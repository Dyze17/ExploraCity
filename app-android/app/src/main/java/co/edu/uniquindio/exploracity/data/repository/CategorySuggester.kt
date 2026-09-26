package co.edu.uniquindio.exploracity.data.repository

import co.edu.uniquindio.exploracity.data.connectivity.ConnectivityObserver
import co.edu.uniquindio.exploracity.data.connectivity.OfflineException
import co.edu.uniquindio.exploracity.domain.model.Category
import kotlinx.coroutines.delay
import java.text.Normalizer
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * 16 · Sugerencia de categoría a partir del título y la descripción. La hace el backend (SAD: Componente de
 * Clasificación IA con OpenRouter); la clave del modelo nunca está en la app.
 */
interface CategorySuggester {
    /** La categoría más probable, o null si no hay una clara. Lanza excepción si falla la red. */
    suspend fun suggest(title: String, description: String): Category?
}

/**
 * Temporal hasta que exista la API: cuenta palabras clave de cada categoría. Empates o ninguna coincidencia: sin
 * sugerencia. Tarda un poco, como el servicio real (el indicador de 16.a se alcanza a ver).
 */
class FakeCategorySuggester(private val latency: Duration = 1200.milliseconds) : CategorySuggester {

    override suspend fun suggest(title: String, description: String): Category? {
        delay(latency)
        val words = "$title $description".normalized().split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }.toSet()
        val scores = keywords.mapValues { (_, list) -> list.count { it in words } }.filterValues { it > 0 }
        val best = scores.maxOfOrNull { it.value } ?: return null
        return scores.filterValues { it == best }.keys.singleOrNull()
    }

    private companion object {
        val keywords: Map<Category, List<String>> = mapOf(
            Category.GASTRONOMY to listOf(
                "cafe", "restaurante", "panaderia", "pan", "tamales", "tamal", "comida", "almuerzo", "chocolate", "arepa",
                "ajiaco", "postres", "cocina", "sabor", "tostion", "cerveza", "fruta", "frutas", "almojabanas",
            ),
            Category.CULTURE to listOf("galeria", "mural", "murales", "teatro", "arte", "biblioteca", "cine", "exposicion", "artesanias", "libros", "musica"),
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

        /** Sin mayúsculas ni tildes («Montaña» → «montana»), como la búsqueda del feed. */
        fun String.normalized(): String = Normalizer.normalize(lowercase(Locale.ROOT), Normalizer.Form.NFD).replace(diacritics, "")
    }
}

/** Sin red no se intenta: el formulario sigue con la elección manual (16.c). */
class OnlineOnlyCategorySuggester(
    private val remote: CategorySuggester,
    private val connectivity: ConnectivityObserver,
) : CategorySuggester {
    override suspend fun suggest(title: String, description: String): Category? {
        if (!connectivity.isOnline.value) throw OfflineException()
        return remote.suggest(title, description)
    }
}
