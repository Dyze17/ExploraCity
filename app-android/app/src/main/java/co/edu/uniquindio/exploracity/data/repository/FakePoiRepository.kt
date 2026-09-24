package co.edu.uniquindio.exploracity.data.repository

import co.edu.uniquindio.exploracity.domain.model.Category
import co.edu.uniquindio.exploracity.domain.model.Category.CULTURE
import co.edu.uniquindio.exploracity.domain.model.Category.ENTERTAINMENT
import co.edu.uniquindio.exploracity.domain.model.Category.GASTRONOMY
import co.edu.uniquindio.exploracity.domain.model.Category.HISTORY
import co.edu.uniquindio.exploracity.domain.model.Category.NATURE
import co.edu.uniquindio.exploracity.domain.model.FeedFilters
import co.edu.uniquindio.exploracity.domain.model.LocationScope
import co.edu.uniquindio.exploracity.domain.model.Poi
import co.edu.uniquindio.exploracity.domain.model.PublicationStatus.FINALIZED
import co.edu.uniquindio.exploracity.domain.model.PublicationStatus.VERIFIED
import kotlinx.coroutines.delay
import java.text.Normalizer
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Temporal hasta que exista la API (api-ktor): feed en memoria con lugares de Bogotá, ya ordenado por
 * cercanía. Simula la latencia de red; la búsqueda ignora mayúsculas y tildes. «Cercanos» usa la distancia
 * guardada en cada lugar, porque todavía no se lee la ubicación del dispositivo.
 */
class FakePoiRepository(
    private val pois: List<Poi> = samplePois,
    private val latency: Duration = 700.milliseconds,
    private val countLatency: Duration = 150.milliseconds,
) : PoiRepository {

    override suspend fun feedPage(query: FeedQuery, page: Int, pageSize: Int): FeedPage {
        delay(latency)
        val matches = matching(query)
        val from = page * pageSize
        val items = matches.drop(from).take(pageSize)
        return FeedPage(items = items, total = matches.size, hasMore = from + items.size < matches.size)
    }

    override suspend fun count(query: FeedQuery): Int {
        delay(countLatency)
        return matching(query).size
    }

    private fun matching(query: FeedQuery): List<Poi> {
        val text = query.text.normalizedForSearch()
        val filters = query.filters
        return pois.filter { poi ->
            (filters.categories.isEmpty() || poi.category in filters.categories) &&
                (filters.scope == LocationScope.CITY || poi.distanceMeters <= FeedFilters.NEARBY_RADIUS_METERS) &&
                (!filters.verifiedOnly || poi.status == VERIFIED) &&
                (text.isEmpty() || poi.title.normalizedForSearch().contains(text))
        }
    }
}

private val diacritics = Regex("\\p{Mn}+")

private fun String.normalizedForSearch(): String =
    Normalizer.normalize(trim().lowercase(Locale.ROOT), Normalizer.Form.NFD).replace(diacritics, "")

private fun poi(id: String, title: String, category: Category, meters: Int, votes: Int, comments: Int, finalized: Boolean = false) =
    Poi(id, title, category, if (finalized) FINALIZED else VERIFIED, meters, votes, comments)

/** Los primeros coinciden con los lienzos del diseño (Café Las Acacias, Museo del Barrio Egipto…). */
val samplePois = listOf(
    poi("cafe-las-acacias", "Café Las Acacias", GASTRONOMY, 1_200, 48, 12),
    poi("museo-barrio-egipto", "Museo del Barrio Egipto", CULTURE, 2_400, 132, 31),
    poi("sendero-la-vieja", "Sendero Quebrada La Vieja", NATURE, 3_400, 87, 19),
    poi("chorro-de-quevedo", "Chorro de Quevedo", HISTORY, 3_600, 210, 44),
    poi("la-puerta-falsa", "La Puerta Falsa", GASTRONOMY, 3_900, 164, 52),
    poi("casa-independencia", "Casa de la Independencia", HISTORY, 4_100, 58, 9, finalized = true),
    poi("teatro-colon", "Teatro Colón", ENTERTAINMENT, 4_300, 96, 17),
    poi("museo-del-oro", "Museo del Oro", CULTURE, 4_700, 301, 88),
    poi("museo-botero", "Museo Botero", CULTURE, 4_900, 187, 40),
    poi("plaza-de-bolivar", "Plaza de Bolívar", HISTORY, 5_200, 143, 26),
    poi("biblioteca-luis-angel", "Biblioteca Luis Ángel Arango", CULTURE, 5_400, 77, 11),
    poi("mercado-paloquemao", "Mercado de Paloquemao", GASTRONOMY, 5_800, 119, 35),
    poi("quinta-de-bolivar", "Quinta de Bolívar", HISTORY, 6_100, 64, 8),
    poi("cerro-monserrate", "Cerro de Monserrate", NATURE, 6_500, 256, 73),
    poi("parque-de-los-novios", "Parque de los Novios", NATURE, 7_200, 45, 6),
    poi("taller-de-mascaras", "Taller de máscaras", CULTURE, 7_600, 22, 4),
    poi("galeria-santa-fe", "Galería Santa Fe", CULTURE, 8_100, 18, 3),
    poi("parque-de-la-93", "Parque de la 93", ENTERTAINMENT, 9_300, 91, 20),
    poi("mirador-alto-la-cruz", "Mirador del Alto de la Cruz", NATURE, 9_800, 37, 5),
    poi("tienda-don-alvaro", "Tienda de don Álvaro", GASTRONOMY, 10_400, 29, 7),
    poi("jardin-botanico", "Jardín Botánico José Celestino Mutis", NATURE, 11_200, 178, 39),
    poi("parque-simon-bolivar", "Parque Simón Bolívar", ENTERTAINMENT, 11_900, 205, 48),
    poi("humedal-cordoba", "Humedal Córdoba", NATURE, 13_500, 41, 9),
    poi("pulgas-usaquen", "Mercado de las pulgas de Usaquén", GASTRONOMY, 15_800, 88, 21),
    poi("plaza-de-toros", "Plaza Cultural La Santamaría", ENTERTAINMENT, 16_200, 33, 6, finalized = true),
    poi("hacienda-santa-barbara", "Hacienda Santa Bárbara", HISTORY, 17_400, 26, 4),
)
