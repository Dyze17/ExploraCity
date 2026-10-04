package co.edu.uniquindio.exploracity.data.repository

import co.edu.uniquindio.exploracity.data.local.AuthTokens
import co.edu.uniquindio.exploracity.data.location.LocationProvider
import co.edu.uniquindio.exploracity.data.location.SimulatedLocationProvider
import co.edu.uniquindio.exploracity.data.remote.FakeApi
import co.edu.uniquindio.exploracity.data.remote.PoiApi
import co.edu.uniquindio.exploracity.data.remote.SentRequest
import co.edu.uniquindio.exploracity.data.remote.TestSession
import co.edu.uniquindio.exploracity.data.remote.contractExample
import co.edu.uniquindio.exploracity.data.remote.error
import co.edu.uniquindio.exploracity.data.remote.example
import co.edu.uniquindio.exploracity.data.remote.json
import co.edu.uniquindio.exploracity.data.remote.query
import co.edu.uniquindio.exploracity.domain.model.Author
import co.edu.uniquindio.exploracity.domain.model.Category
import co.edu.uniquindio.exploracity.domain.model.FeedFilters
import co.edu.uniquindio.exploracity.domain.model.GeoBounds
import co.edu.uniquindio.exploracity.domain.model.GeoPoint
import co.edu.uniquindio.exploracity.domain.model.LocationScope
import co.edu.uniquindio.exploracity.domain.model.OpeningHours
import co.edu.uniquindio.exploracity.domain.model.PoiPhoto
import co.edu.uniquindio.exploracity.domain.model.PriceRange
import co.edu.uniquindio.exploracity.domain.model.PublicationStatus
import co.edu.uniquindio.exploracity.domain.model.VisitExperience
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime

/** 7, 8, 9, 13, 14 y 14.b con la API: los criterios en la consulta y lo que llega según el contrato. */
class ApiPoiRepositoryTest {

    private val stores = TestSession()
    private lateinit var api: FakeApi

    /** Desde el Café Las Acacias, como en los ejemplos del contrato. */
    private val here = GeoPoint(4.5402, -75.6721)

    private suspend fun places(
        location: LocationProvider = SimulatedLocationProvider(here),
        handler: MockRequestHandleScope.(SentRequest) -> HttpResponseData,
    ): ApiPoiRepository {
        stores.tokens.save(AuthTokens("token-de-acceso", "token-de-renovacion"))
        api = FakeApi(handler)
        return ApiPoiRepository(PoiApi(api.sessionClient(stores)), location)
    }

    /** La consulta de la última petición, ya decodificada. */
    private fun query(): Map<String, String> = api.requests.last().query

    @Test
    fun `el feed llega como dice el contrato, medido desde la persona`() = runTest {
        val page = places { example("feed-page.json") }.feedPage(FeedQuery(), page = 1, pageSize = 20)

        assertEquals(2, page.total)
        assertEquals(false, page.hasMore)
        val (plaza, mirador) = page.items
        assertEquals("Plaza de Bolívar", plaza.title)
        assertEquals(Category.HISTORY, plaza.category)
        assertEquals(PublicationStatus.VERIFIED, plaza.status)
        assertEquals(1218, plaza.distanceMeters)
        assertNull(plaza.photoUrl)
        assertNull(plaza.openNow)
        assertEquals(PriceRange.FREE, mirador.price)
        assertEquals(false, mirador.openNow)
        assertEquals("https://res.cloudinary.com/exploracity/image/upload/lugares/mirador-1.jpg", mirador.photoUrl)
        assertEquals("Un mirador con vista a toda la ciudad, ideal al atardecer.", mirador.summary)
        assertEquals("/v1/places", api.requests.single().path)
        assertEquals(mapOf("near" to "4.5402,-75.6721", "page" to "1", "pageSize" to "20"), query())
        assertEquals("Bearer token-de-acceso", api.requests.single().authorization)
    }

    @Test
    fun `los filtros de la hoja 9 y la búsqueda van en la consulta`() = runTest {
        val filters = FeedFilters(categories = setOf(Category.NATURE, Category.CULTURE), scope = LocationScope.NEARBY, verifiedOnly = true)
        val repository = places { example("count.json") }

        val count = repository.count(FeedQuery(filters, text = "  café "))

        assertEquals(2, count)
        assertEquals("/v1/places/count", api.requests.single().path)
        assertEquals(
            mapOf("categories" to "CULTURE,NATURE", "scope" to "NEARBY", "verifiedOnly" to "true", "q" to "café", "near" to "4.5402,-75.6721"),
            query(),
        )
    }

    @Test
    fun `sin ubicación la API mide desde el centro de la ciudad`() = runTest {
        val noLocation = object : LocationProvider {
            override suspend fun currentLocation(): GeoPoint = throw IllegalStateException("sin permiso")
        }

        places(noLocation) { example("feed-page.json") }.feedPage(FeedQuery(), 0)

        assertEquals(mapOf("page" to "0", "pageSize" to "20"), query())
    }

    @Test
    fun `el mapa pide el área visible`() = runTest {
        val area = places { example("map-area.json") }
            .mapArea(FeedQuery(), GeoBounds(GeoPoint(4.50, -75.70), GeoPoint(4.56, -75.65)), limit = 200)

        assertEquals(listOf("Plaza de Bolívar"), area.items.map { it.title })
        assertEquals(2, area.total)
        assertEquals("/v1/places/map", api.requests.single().path)
        assertEquals("4.5,-75.7,4.56,-75.65", query()["bounds"])
        assertEquals("200", query()["limit"])
    }

    @Test
    fun `el detalle trae fotos con su descripción, horario, autor y lo que hizo la persona`() = runTest {
        val details = places { example("place.json") }.poiDetails("c1a2b3c4-d5e6-4f70-8a9b-0c1d2e3f4a5b")!!

        assertEquals("Mirador de la Secreta", details.poi.title)
        assertEquals(listOf(PoiPhoto("https://res.cloudinary.com/exploracity/image/upload/lugares/mirador-1.jpg", "Mirador de la Secreta")), details.photos)
        assertEquals("Vía al Mirador, Armenia", details.address)
        assertEquals(
            OpeningHours(setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY), LocalTime.of(8, 0), LocalTime.of(18, 0)),
            details.hours,
        )
        assertEquals(Author("3f6c1e2a-8b4d-4c7e-9a1f-2d5b7e9c0a14", "Ana Ríos", 40), details.author)
        assertTrue(details.voted)
        assertEquals(false, details.visited)
        assertNull(details.savedAt)
        assertEquals("/v1/places/c1a2b3c4-d5e6-4f70-8a9b-0c1d2e3f4a5b", api.requests.single().path)
    }

    @Test
    fun `un lugar sin autor es de una cuenta eliminada, y uno que ya no es público no tiene detalle`() = runTest {
        val withoutAuthor = contractExample("place.json").replace(Regex("\"author\": \\{[^}]*\\},"), "")

        assertNull(places { json(withoutAuthor) }.poiDetails("mirador")!!.author)
        assertNull(places { error(HttpStatusCode.NotFound, "place_not_found") }.poiDetails("pendiente"))
    }

    @Test
    fun `votar, quitar el voto y marcar la visita`() = runTest {
        val repository = places { sent ->
            when {
                sent.path.endsWith("/vote") -> example("vote.json")
                else -> example("visit.json")
            }
        }

        assertEquals(2, repository.setVote("plaza", voted = true).votes)
        repository.setVote("plaza", voted = false)
        val visit = repository.markVisited("plaza", VisitExperience(recommends = true, text = "  Subí al atardecer. ", showName = true))

        assertEquals(5, visit.pointsAwarded)
        assertEquals(false, visit.queued)
        assertEquals(listOf("PUT", "DELETE", "PUT"), api.requests.map { it.method })
        assertEquals("""{"recommends":true,"text":"Subí al atardecer.","showName":true}""", api.requests.last().body)
    }

    @Test
    fun `una visita sin experiencia no manda texto`() = runTest {
        places { example("visit.json") }.markVisited("plaza", VisitExperience())

        assertEquals("""{"showName":false}""", api.requests.single().body)
    }

    @Test
    fun `los comentarios llegan del más reciente al más antiguo y se piden con el cursor`() = runTest {
        val page = places { example("comments-page.json") }.comments("plaza", cursor = "siguiente", pageSize = 20)!!

        assertEquals("Plaza de Bolívar", page.poiTitle)
        assertEquals(1, page.total)
        assertNull(page.nextCursor)
        val comment = page.items.single()
        assertEquals(Author("3f6c1e2a-8b4d-4c7e-9a1f-2d5b7e9c0a14", "Ana Ríos", 40), comment.author)
        assertEquals(Instant.parse("2026-09-25T22:10:00Z"), comment.createdAt)
        assertEquals(false, comment.mine)
        assertEquals(mapOf("cursor" to "siguiente", "pageSize" to "20"), query())
        assertNull(places { error(HttpStatusCode.NotFound, "place_not_found") }.comments("pendiente", null, 20))
    }

    @Test
    fun `comentar manda el texto con su clientId`() = runTest {
        val comment = places { example("comment.json", HttpStatusCode.Created) }.addComment("plaza", "Fui con mi familia.", clientId = "id-del-telefono")

        assertEquals("Pedro Pérez", comment.author?.name)
        assertTrue(comment.mine)
        assertEquals(false, comment.pending)
        assertEquals("""{"text":"Fui con mi familia.","clientId":"id-del-telefono"}""", api.requests.single().body)
    }

    @Test
    fun `la API no guarda nada para ver sin conexión ni tiene cola`() = runTest {
        val repository = places { error(HttpStatusCode.InternalServerError, "internal_error") }

        assertNull(repository.savedPlaces())
        assertTrue(api.requests.isEmpty())
    }
}
