package co.edu.uniquindio.exploracity.data.repository

import co.edu.uniquindio.exploracity.data.location.SIMULATED_LOCATION
import co.edu.uniquindio.exploracity.domain.model.Category
import co.edu.uniquindio.exploracity.domain.model.Category.CULTURE
import co.edu.uniquindio.exploracity.domain.model.Category.ENTERTAINMENT
import co.edu.uniquindio.exploracity.domain.model.Category.GASTRONOMY
import co.edu.uniquindio.exploracity.domain.model.Category.HISTORY
import co.edu.uniquindio.exploracity.domain.model.Category.NATURE
import co.edu.uniquindio.exploracity.domain.model.FeedFilters
import co.edu.uniquindio.exploracity.domain.model.GeoBounds
import co.edu.uniquindio.exploracity.domain.model.GeoPoint
import co.edu.uniquindio.exploracity.domain.model.LocationScope
import co.edu.uniquindio.exploracity.domain.model.Poi
import co.edu.uniquindio.exploracity.domain.model.PoiDetails
import co.edu.uniquindio.exploracity.domain.model.PriceRange
import co.edu.uniquindio.exploracity.domain.model.PriceRange.FREE
import co.edu.uniquindio.exploracity.domain.model.PriceRange.HIGH
import co.edu.uniquindio.exploracity.domain.model.PriceRange.LOW
import co.edu.uniquindio.exploracity.domain.model.PriceRange.MEDIUM
import co.edu.uniquindio.exploracity.domain.model.PublicationStatus.FINALIZED
import co.edu.uniquindio.exploracity.domain.model.PublicationStatus.VERIFIED
import co.edu.uniquindio.exploracity.domain.model.VisitExperience
import co.edu.uniquindio.exploracity.domain.model.VisitResult
import kotlinx.coroutines.delay
import java.text.Normalizer
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Temporal hasta que exista la API (api-ktor): lugares de Bogotá en memoria, ordenados por cercanía a la
 * ubicación simulada. Simula la latencia de red; la búsqueda ignora mayúsculas y tildes.
 */
class FakePoiRepository(
    private val pois: List<Poi> = samplePois,
    private val latency: Duration = 700.milliseconds,
    private val countLatency: Duration = 150.milliseconds,
    private val areaLatency: Duration = 500.milliseconds,
    private val actionLatency: Duration = 300.milliseconds,
) : PoiRepository {

    // Votos y visitas de la persona en esta sesión: el feed y el detalle ven el mismo total.
    private val votes = pois.associate { it.id to it.votes }.toMutableMap()
    private val voted = mutableSetOf<String>()
    private val visited = mutableSetOf<String>()

    override suspend fun poiDetails(id: String): PoiDetails? {
        delay(latency)
        val poi = current().firstOrNull { it.id == id } ?: return null
        return sampleDetails(poi).copy(voted = id in voted, visited = id in visited)
    }

    override suspend fun setVote(id: String, voted: Boolean): Int {
        delay(actionLatency)
        val total = votes[id] ?: error("Lugar desconocido: $id")
        val changed = if (voted) this.voted.add(id) else this.voted.remove(id)
        if (changed) votes[id] = total + if (voted) 1 else -1
        return votes.getValue(id)
    }

    /** Opción A de Daniel (24/09/2026): los puntos por visitar los decide el backend; aquí no hay. */
    override suspend fun markVisited(id: String, experience: VisitExperience): VisitResult {
        delay(actionLatency)
        visited += id
        return VisitResult(pointsAwarded = 0)
    }

    private fun current(): List<Poi> = pois.map { poi -> votes[poi.id]?.let { poi.copy(votes = it) } ?: poi }

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

    override suspend fun mapArea(query: FeedQuery, bounds: GeoBounds, limit: Int): MapArea {
        delay(areaLatency)
        val inArea = matching(query).filter { it.location in bounds }
        return MapArea(items = inArea.take(limit), total = inArea.size)
    }

    private fun matching(query: FeedQuery): List<Poi> {
        val text = query.text.normalizedForSearch()
        val filters = query.filters
        return current().filter { poi ->
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

private fun poi(
    id: String,
    title: String,
    category: Category,
    latitude: Double,
    longitude: Double,
    votes: Int,
    comments: Int,
    price: PriceRange? = null,
    openNow: Boolean? = null,
    summary: String? = null,
    finalized: Boolean = false,
): Poi {
    val location = GeoPoint(latitude, longitude)
    return Poi(
        id = id,
        title = title,
        category = category,
        status = if (finalized) FINALIZED else VERIFIED,
        location = location,
        distanceMeters = SIMULATED_LOCATION.distanceTo(location),
        votes = votes,
        comments = comments,
        price = price,
        openNow = openNow,
        summary = summary,
    )
}

/**
 * Coordenadas reales aproximadas; los lugares inventados del diseño (Café Las Acacias, Taller de máscaras…)
 * están en barrios verosímiles. Café Las Acacias queda primero, a 1,2 km, como en los lienzos.
 */
val samplePois: List<Poi> = listOf(
    poi("cafe-las-acacias", "Café Las Acacias", GASTRONOMY, 4.6383, -74.0655, 48, 12, price = LOW, openNow = true, summary = "Tostión propia y patio interior, ideal en la mañana."),
    poi("museo-barrio-egipto", "Museo del Barrio Egipto", CULTURE, 4.5925, -74.0675, 132, 31, price = LOW, openNow = true, summary = "Memoria del barrio contada por sus vecinos."),
    poi("sendero-la-vieja", "Sendero Quebrada La Vieja", NATURE, 4.6515, -74.0535, 87, 19, openNow = false, summary = "Caminata entre bosque de niebla; se sube en la mañana."),
    poi("chorro-de-quevedo", "Chorro de Quevedo", HISTORY, 4.5966, -74.0697, 210, 44, summary = "Plazoleta donde, según la tradición, se fundó Bogotá."),
    poi("la-puerta-falsa", "La Puerta Falsa", GASTRONOMY, 4.5977, -74.0746, 164, 52, price = LOW, openNow = true, summary = "Tamales y chocolate con queso desde 1816."),
    poi("casa-independencia", "Casa de la Independencia", HISTORY, 4.5985, -74.0753, 58, 9, price = LOW, summary = "El florero de Llorente y el 20 de julio de 1810.", finalized = true),
    poi("teatro-colon", "Teatro Colón", ENTERTAINMENT, 4.5961, -74.0735, 96, 17, price = HIGH, openNow = false, summary = "Teatro nacional con temporada de ópera y danza."),
    poi("museo-del-oro", "Museo del Oro", CULTURE, 4.6019, -74.0721, 301, 88, price = LOW, openNow = true, summary = "La mayor colección de orfebrería prehispánica."),
    poi("museo-botero", "Museo Botero", CULTURE, 4.5967, -74.0730, 187, 40, price = FREE, openNow = true, summary = "Obras donadas por Fernando Botero; entrada libre."),
    poi("plaza-de-bolivar", "Plaza de Bolívar", HISTORY, 4.5981, -74.0760, 143, 26, summary = "Capitolio, catedral y alcaldía alrededor de la plaza."),
    poi("biblioteca-luis-angel", "Biblioteca Luis Ángel Arango", CULTURE, 4.5970, -74.0727, 77, 11, openNow = true, summary = "Sala de conciertos y colecciones abiertas al público."),
    poi("mercado-paloquemao", "Mercado de Paloquemao", GASTRONOMY, 4.6155, -74.0838, 119, 35, price = LOW, openNow = true, summary = "Frutas, flores y desayunos de plaza de mercado."),
    poi("quinta-de-bolivar", "Quinta de Bolívar", HISTORY, 4.6030, -74.0648, 64, 8, price = LOW, openNow = true, summary = "Casa y jardines donde vivió Simón Bolívar."),
    poi("cerro-monserrate", "Cerro de Monserrate", NATURE, 4.6058, -74.0556, 256, 73, price = MEDIUM, openNow = true, summary = "Vista de toda la ciudad; se sube a pie o en funicular."),
    poi("parque-de-los-novios", "Parque de los Novios", NATURE, 4.6577, -74.0703, 45, 6, openNow = true, summary = "Lago con botes y senderos para caminar."),
    poi("taller-de-mascaras", "Taller de máscaras", CULTURE, 4.6440, -74.0630, 22, 4, price = MEDIUM, openNow = false, summary = "Talleres de máscaras de carnaval los sábados."),
    poi("galeria-santa-fe", "Galería Santa Fe", CULTURE, 4.6123, -74.0689, 18, 3, openNow = true, summary = "Arte emergente en el Planetario."),
    poi("parque-de-la-93", "Parque de la 93", ENTERTAINMENT, 4.6766, -74.0485, 91, 20, price = HIGH, summary = "Restaurantes y eventos al aire libre."),
    poi("mirador-alto-la-cruz", "Mirador del Alto de la Cruz", NATURE, 4.5870, -74.0640, 37, 5, summary = "Mirador sobre el centro histórico."),
    poi("tienda-don-alvaro", "Tienda de don Álvaro", GASTRONOMY, 4.6300, -74.0790, 29, 7, price = LOW, openNow = true, summary = "Empanadas y tinto de barrio."),
    poi("jardin-botanico", "Jardín Botánico José Celestino Mutis", NATURE, 4.6687, -74.0999, 178, 39, price = LOW, openNow = true, summary = "Colecciones de flora andina y de páramo."),
    poi("parque-simon-bolivar", "Parque Simón Bolívar", ENTERTAINMENT, 4.6581, -74.0935, 205, 48, summary = "Conciertos al parque y el lago más grande de la ciudad."),
    poi("humedal-cordoba", "Humedal Córdoba", NATURE, 4.7050, -74.0700, 41, 9, openNow = true, summary = "Avistamiento de aves en senderos elevados."),
    poi("pulgas-usaquen", "Mercado de las pulgas de Usaquén", GASTRONOMY, 4.6948, -74.0307, 88, 21, price = MEDIUM, openNow = false, summary = "Artesanías y comida los domingos."),
    poi("plaza-de-toros", "Plaza Cultural La Santamaría", ENTERTAINMENT, 4.6127, -74.0680, 33, 6, summary = "Antigua plaza de toros, hoy escenario cultural.", finalized = true),
    poi("hacienda-santa-barbara", "Hacienda Santa Bárbara", HISTORY, 4.6970, -74.0400, 26, 4, summary = "Casa de hacienda colonial en Usaquén."),
).sortedBy { it.distanceMeters }
