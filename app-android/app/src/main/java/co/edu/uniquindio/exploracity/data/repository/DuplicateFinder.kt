package co.edu.uniquindio.exploracity.data.repository

import co.edu.uniquindio.exploracity.data.connectivity.ConnectivityObserver
import co.edu.uniquindio.exploracity.data.connectivity.OfflineException
import co.edu.uniquindio.exploracity.domain.model.DuplicateRules
import co.edu.uniquindio.exploracity.domain.model.GeoPoint
import co.edu.uniquindio.exploracity.domain.model.SimilarPlace
import kotlinx.coroutines.delay
import java.text.Normalizer
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** 17 · Busca lugares que podrían ser el mismo que se está publicando (SAD: Componente de Detección de Duplicados). */
interface DuplicateFinder {
    /**
     * Lugares a menos de 50 m de [location] con un nombre parecido a [title], del más cercano al más lejano (como máximo
     * 3). Lanza excepción si falla la red.
     */
    suspend fun similarPlaces(title: String, location: GeoPoint): List<SimilarPlace>
}

/**
 * Temporal hasta que exista la API, que lo hará con PostGIS y pg_trgm (ADR-13): compara con los lugares del feed y con
 * las publicaciones pendientes. El nombre se compara por trigramas, como pg_trgm, con su umbral por defecto.
 */
class FakeDuplicateFinder(
    private val pois: FakePoiRepository,
    private val publications: FakePublicationRepository,
    private val latency: Duration = 350.milliseconds,
) : DuplicateFinder {

    override suspend fun similarPlaces(title: String, location: GeoPoint): List<SimilarPlace> {
        delay(latency)
        val public = pois.places().map { SimilarPlace(it.id, it.title, it.category, it.status, it.location, 0, it.photoUrl) }
        val pending = publications.pendingOnes().map { SimilarPlace(it.id, it.title, it.category, it.status, it.location, 0, it.photoUrl) }
        return (public + pending)
            .map { it.copy(distanceMeters = location.distanceTo(it.location)) }
            .filter { it.distanceMeters <= DuplicateRules.RADIUS_METERS && titleSimilarity(title, it.title) >= SIMILARITY_THRESHOLD }
            .sortedBy { it.distanceMeters }
            .take(DuplicateRules.MAX_RESULTS)
    }

    private companion object {
        /** El de pg_trgm por defecto (pg_trgm.similarity_threshold). */
        const val SIMILARITY_THRESHOLD = 0.3
    }
}

/** Sin red no se busca: el formulario sigue al paso 4 y el servidor repite la búsqueda al recibir la publicación. */
class OnlineOnlyDuplicateFinder(
    private val remote: DuplicateFinder,
    private val connectivity: ConnectivityObserver,
) : DuplicateFinder {
    override suspend fun similarPlaces(title: String, location: GeoPoint): List<SimilarPlace> {
        if (!connectivity.isOnline.value) throw OfflineException()
        return remote.similarPlaces(title, location)
    }
}

/**
 * Parecido entre dos nombres de 0 a 1, como similarity() de pg_trgm: trigramas de cada palabra (con dos espacios
 * delante y uno detrás), sin mayúsculas ni tildes, y la proporción de los que comparten.
 */
internal fun titleSimilarity(a: String, b: String): Double {
    val first = trigrams(a)
    val second = trigrams(b)
    if (first.isEmpty() || second.isEmpty()) return 0.0
    return (first intersect second).size.toDouble() / (first union second).size
}

private val diacritics = Regex("\\p{Mn}+")
private val separators = Regex("[^\\p{L}\\p{N}]+")

private fun trigrams(text: String): Set<String> {
    val plain = Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD).replace(diacritics, "")
    return plain.split(separators).filter { it.isNotEmpty() }.flatMap { word ->
        val padded = "  $word "
        (0..padded.length - 3).map { padded.substring(it, it + 3) }
    }.toSet()
}
