package co.edu.uniquindio.exploracity.data.repository

import co.edu.uniquindio.exploracity.data.connectivity.FakeConnectivity
import co.edu.uniquindio.exploracity.data.connectivity.OfflineException
import co.edu.uniquindio.exploracity.domain.model.GeoPoint
import co.edu.uniquindio.exploracity.domain.model.PublicationStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration

/** 17 · La búsqueda de parecidos de prueba, como la hará el backend (ADR-13): menos de 50 m y nombre parecido. */
class FakeDuplicateFinderTest {

    private val pois = FakePoiRepository(latency = Duration.ZERO)
    private val finder = FakeDuplicateFinder(pois, FakePublicationRepository(pois), latency = Duration.ZERO)

    /** Unos 20 m al sur de La Puerta Falsa (4.5977, -74.0746). */
    private val nearPuertaFalsa = GeoPoint(4.59752, -74.0746)

    @Test
    fun `encuentra el lugar cercano con nombre parecido, con su distancia`() = runTest {
        val found = finder.similarPlaces("Tamales La Puerta Falsa", nearPuertaFalsa)

        assertEquals(listOf("la-puerta-falsa"), found.map { it.id })
        assertEquals(20, found.single().distanceMeters)
        assertEquals(PublicationStatus.VERIFIED, found.single().status)
    }

    @Test
    fun `un nombre distinto o un lugar a más de 50 m no cuentan`() = runTest {
        assertTrue(finder.similarPlaces("Museo de la Esmeralda", nearPuertaFalsa).isEmpty())
        // Unos 110 m al norte.
        assertTrue(finder.similarPlaces("La Puerta Falsa", GeoPoint(4.5987, -74.0746)).isEmpty())
    }

    @Test
    fun `también compara con las publicaciones pendientes, no con las rechazadas`() = runTest {
        // Panadería La Candelaria (pendiente) está en 4.5966, -74.0718; Puerta Falsa, tamales (rechazada), junto a La Puerta Falsa.
        val bakery = finder.similarPlaces("Panadería Candelaria", GeoPoint(4.5967, -74.0718))
        assertEquals(listOf("panaderia-la-candelaria" to PublicationStatus.PENDING), bakery.map { it.id to it.status })

        val tamales = finder.similarPlaces("Puerta Falsa, tamales", nearPuertaFalsa)
        assertEquals(listOf("la-puerta-falsa"), tamales.map { it.id })
    }

    @Test
    fun `sin red no se busca`() = runTest {
        val offline = OnlineOnlyDuplicateFinder(finder, FakeConnectivity(online = false))

        assertTrue(runCatching { offline.similarPlaces("La Puerta Falsa", nearPuertaFalsa) }.exceptionOrNull() is OfflineException)
    }

    @Test
    fun `el parecido de nombres ignora mayúsculas, tildes y signos, como pg_trgm`() {
        assertEquals(1.0, titleSimilarity("Café Las Acacias", "cafe las acacias!"), 0.0)
        assertTrue(titleSimilarity("Tamales La Puerta Falsa", "La Puerta Falsa") > 0.6)
        assertTrue(titleSimilarity("Museo del Oro", "La Puerta Falsa") < 0.1)
        assertEquals(0.0, titleSimilarity("", "La Puerta Falsa"), 0.0)
    }
}
