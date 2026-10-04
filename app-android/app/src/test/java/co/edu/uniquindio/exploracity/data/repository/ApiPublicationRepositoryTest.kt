package co.edu.uniquindio.exploracity.data.repository

import co.edu.uniquindio.exploracity.data.local.AuthTokens
import co.edu.uniquindio.exploracity.data.remote.ApiException
import co.edu.uniquindio.exploracity.data.remote.FakeApi
import co.edu.uniquindio.exploracity.data.remote.PublicationApi
import co.edu.uniquindio.exploracity.data.remote.SentRequest
import co.edu.uniquindio.exploracity.data.remote.TestSession
import co.edu.uniquindio.exploracity.data.remote.contractExample
import co.edu.uniquindio.exploracity.data.remote.empty
import co.edu.uniquindio.exploracity.data.remote.error
import co.edu.uniquindio.exploracity.data.remote.example
import co.edu.uniquindio.exploracity.data.remote.failure
import co.edu.uniquindio.exploracity.data.remote.json
import co.edu.uniquindio.exploracity.data.remote.query
import co.edu.uniquindio.exploracity.data.remote.randomSecret
import co.edu.uniquindio.exploracity.domain.model.Category
import co.edu.uniquindio.exploracity.domain.model.CategoryOrigin
import co.edu.uniquindio.exploracity.domain.model.DraftHours
import co.edu.uniquindio.exploracity.domain.model.DraftPhoto
import co.edu.uniquindio.exploracity.domain.model.DuplicateCheck
import co.edu.uniquindio.exploracity.domain.model.FixKind
import co.edu.uniquindio.exploracity.domain.model.GeoPoint
import co.edu.uniquindio.exploracity.domain.model.OpeningHours
import co.edu.uniquindio.exploracity.domain.model.PriceRange
import co.edu.uniquindio.exploracity.domain.model.PublicationChanges
import co.edu.uniquindio.exploracity.domain.model.PublicationStatus
import co.edu.uniquindio.exploracity.domain.model.PublicationSubmission
import co.edu.uniquindio.exploracity.domain.model.PublishedPhoto
import co.edu.uniquindio.exploracity.domain.model.RejectionReason
import co.edu.uniquindio.exploracity.domain.model.RequiredFix
import co.edu.uniquindio.exploracity.domain.model.SimilarPlace
import co.edu.uniquindio.exploracity.domain.model.SubmitResult
import co.edu.uniquindio.exploracity.domain.model.toDraftPhoto
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime

/** 15–24 con la API: el formulario, las publicaciones propias y lo que llega según el contrato. */
class ApiPublicationRepositoryTest {

    private val stores = TestSession()
    private val accessToken = randomSecret()
    private lateinit var api: FakeApi

    private suspend fun publicationApi(handler: MockRequestHandleScope.(SentRequest) -> HttpResponseData): PublicationApi {
        stores.tokens.save(AuthTokens(accessToken, randomSecret()))
        api = FakeApi(handler)
        return PublicationApi(api.sessionClient(stores))
    }

    private suspend fun publications(handler: MockRequestHandleScope.(SentRequest) -> HttpResponseData) =
        ApiPublicationRepository(publicationApi(handler))

    /** El JSON enviado, para compararlo sin depender del orden de los campos. */
    private fun sentJson() = Json.parseToJsonElement(api.requests.last().body)

    private val mirador = "c1a2b3c4-d5e6-4f70-8a9b-0c1d2e3f4a5b"
    private val miradorPhoto = "https://res.cloudinary.com/exploracity/image/upload/lugares/mirador-1.jpg"

    @Test
    fun `mis publicaciones llegan como dice el contrato`() = runTest {
        val (cafe, verified) = publications { example("publications.json") }.myPublications()

        assertEquals(PublicationStatus.PENDING, cafe.status)
        assertEquals("Café Las Acacias", cafe.title)
        assertEquals(Instant.parse("2026-09-20T16:30:00Z"), cafe.submittedAt)
        assertTrue(cafe.photos.isEmpty())
        assertNull(cafe.rejection)
        assertEquals(mirador, verified.id)
        assertEquals(GeoPoint(4.5521, -75.6589), verified.location)
        assertEquals(listOf(PublishedPhoto("b4c5d6e7-f8a9-4bc0-9d1e-3f4a5b6c7d8e", miradorPhoto)), verified.photos)
        assertEquals(miradorPhoto, verified.photoUrl)
        assertEquals(OpeningHours((1..5).map(DayOfWeek::of).toSet(), LocalTime.of(8, 0), LocalTime.of(18, 0)), verified.hours)
        assertEquals(PriceRange.FREE, verified.price)
        assertEquals(1, verified.votes)
        assertEquals(35, verified.pointsEarned)
        assertEquals("/v1/publications", api.requests.single().path)
        assertEquals("Bearer $accessToken", api.requests.single().authorization)
    }

    @Test
    fun `una rechazada trae el motivo y qué corregir, y una que ya no existe es null`() = runTest {
        val repository = publications { sent ->
            if (sent.path.endsWith("/no-existe")) error(HttpStatusCode.NotFound, "publication_not_found") else example("publication-rejected.json")
        }

        val rejection = repository.publication("d2b3c4d5-e6f7-4a81-9bac-1d2e3f4a5b6c")!!.rejection!!

        assertEquals(RejectionReason.PHOTO, rejection.reason)
        assertEquals("La foto no permite reconocer el lugar.", rejection.message)
        assertEquals("Laura Gómez", rejection.reviewerName)
        assertEquals(Instant.parse("2026-10-03T15:00:00Z"), rejection.rejectedAt)
        assertTrue(rejection.canResubmit)
        assertEquals(listOf(RequiredFix(FixKind.PHOTOS, "Una foto donde se reconozca el lugar")), rejection.fixes)
        assertEquals(5, rejection.firstStepToFix)
        assertNull(rejection.duplicateOf)
        assertNull(repository.publication("no-existe"))
    }

    @Test
    fun `un rechazo por duplicado trae el lugar que ya existía`() = runTest {
        val rejected = Json.parseToJsonElement(contractExample("publication-rejected.json")).jsonObject
        val original = Json.parseToJsonElement(contractExample("feed-page.json")).jsonObject.getValue("items").jsonArray[1]
        val rejection = JsonObject(
            rejected.getValue("rejection").jsonObject + mapOf(
                "reason" to JsonPrimitive("DUPLICATE"),
                "canResubmit" to JsonPrimitive(false),
                "fixes" to JsonArray(emptyList()),
                "duplicateOf" to original,
            ),
        )
        val body = JsonObject(rejected + ("rejection" to rejection)).toString()

        val duplicate = publications { json(body) }.publication("d2b3c4d5-e6f7-4a81-9bac-1d2e3f4a5b6c")!!.rejection!!

        assertEquals(RejectionReason.DUPLICATE, duplicate.reason)
        assertEquals("Mirador de la Secreta", duplicate.duplicateOf?.title)
        assertEquals(1, duplicate.firstStepToFix)
    }

    @Test
    fun `enviar manda el borrador con su dirección, su clientId y solo las fotos ya subidas`() = runTest {
        val submission = PublicationSubmission(
            title = "Café de la Estación",
            description = "Un café pequeño frente a la vieja estación del tren, con tostión propia.",
            category = Category.GASTRONOMY,
            categoryOrigin = CategoryOrigin.SUGGESTED,
            location = GeoPoint(4.545, -75.675),
            hours = OpeningHours(setOf(DayOfWeek.SATURDAY, DayOfWeek.MONDAY), LocalTime.of(7, 0), LocalTime.of(19, 0)),
            price = PriceRange.LOW,
            photos = listOf(
                DraftPhoto("f1", "/fotos/f1.jpg", "patio.jpg", remoteUrl = "https://medios.test/lugares/f1.jpg"),
                DraftPhoto("f2", "/fotos/f2.jpg", "barra.jpg"),
            ),
            duplicateCheck = DuplicateCheck(GeoPoint(4.545, -75.675), listOf(mirador), "  Es el de arriba. "),
            address = "Calle 21 # 18-30, Armenia",
            clientId = "envio-1",
        )

        val result = publications { example("submit.json", HttpStatusCode.Created) }.submit(submission)

        assertEquals(SubmitResult("7a8b9c0d-1e2f-4a3b-8c4d-5e6f7a8b9c0d", firstPublicationPoints = 20), result)
        val sent = api.requests.single()
        assertEquals("POST", sent.method)
        assertEquals("/v1/publications", sent.path)
        val expected = """
            {"clientId":"envio-1","title":"Café de la Estación",
             "description":"Un café pequeño frente a la vieja estación del tren, con tostión propia.",
             "category":"GASTRONOMY","categoryOrigin":"SUGGESTED","location":{"latitude":4.545,"longitude":-75.675},
             "address":"Calle 21 # 18-30, Armenia","hours":{"days":["MONDAY","SATURDAY"],"opens":"07:00","closes":"19:00"},
             "price":"LOW","photos":["https://medios.test/lugares/f1.jpg"],
             "duplicateCheck":{"location":{"latitude":4.545,"longitude":-75.675},"similarIds":["$mirador"],"note":"Es el de arriba.","failed":false}}
        """
        assertEquals(Json.parseToJsonElement(expected), sentJson())
    }

    @Test
    fun `reenviar una rechazada sin horario ni búsqueda de parecidos no manda lo que falta`() = runTest {
        val submission = PublicationSubmission(
            title = "Café Las Acacias",
            description = "Café de origen con tostión propia y vista al parque principal.",
            category = Category.GASTRONOMY,
            categoryOrigin = CategoryOrigin.CHOSEN,
            location = GeoPoint(4.5402, -75.6721),
            hours = null,
            price = null,
            photos = listOf(DraftPhoto("f1", "", "", remoteUrl = "https://medios.test/lugares/f1.jpg")),
            duplicateCheck = null,
            resubmitId = "d2b3c4d5-e6f7-4a81-9bac-1d2e3f4a5b6c",
            clientId = "envio-2",
        )

        publications { json("""{"publicationId":"d2b3c4d5-e6f7-4a81-9bac-1d2e3f4a5b6c"}""") }.submit(submission)

        val sent = sentJson().jsonObject
        assertEquals(
            setOf("clientId", "title", "description", "category", "categoryOrigin", "location", "photos", "resubmitId"),
            sent.keys,
        )
        assertEquals(JsonPrimitive("d2b3c4d5-e6f7-4a81-9bac-1d2e3f4a5b6c"), sent["resubmitId"])
    }

    @Test
    fun `editar manda lo editable y la dirección solo si se movió el pin`() = runTest {
        val verified = Json.parseToJsonElement(contractExample("publications.json")).jsonArray[1].toString()
        val repository = publications { json(verified) }
        val changes = PublicationChanges(
            title = "  Mirador de la Secreta ",
            category = Category.NATURE,
            description = "Un mirador con vista a toda la ciudad, mejor al atardecer.",
            location = GeoPoint(4.5521, -75.6589),
            // Con «No tengo el horario exacto», lo que se empezó a elegir no viaja.
            hours = DraftHours(opens = LocalTime.of(9, 0)),
            hoursUnknown = true,
            price = PriceRange.FREE,
            photos = listOf(PublishedPhoto("b4c5d6e7-f8a9-4bc0-9d1e-3f4a5b6c7d8e", miradorPhoto).toDraftPhoto()),
        )

        val updated = repository.update(mirador, changes)
        val moved = GeoPoint(4.5525, -75.6590)
        repository.update(mirador, changes.copy(location = moved, address = "Vía al Mirador, Armenia", duplicateCheck = DuplicateCheck(moved)))

        assertEquals(mirador, updated.id)
        assertEquals("PUT", api.requests.first().method)
        assertEquals("/v1/publications/$mirador", api.requests.first().path)
        val expected = """
            {"title":"Mirador de la Secreta","category":"NATURE","description":"Un mirador con vista a toda la ciudad, mejor al atardecer.",
             "location":{"latitude":4.5521,"longitude":-75.6589},"price":"FREE","photos":["$miradorPhoto"]}
        """
        assertEquals(Json.parseToJsonElement(expected), Json.parseToJsonElement(api.requests.first().body))
        val second = sentJson().jsonObject
        assertEquals(JsonPrimitive("Vía al Mirador, Armenia"), second["address"])
        assertEquals("""{"location":{"latitude":4.5525,"longitude":-75.659},"similarIds":[],"failed":false}""", second["duplicateCheck"].toString())
    }

    @Test
    fun `borrar una que ya no existía no es un error, pero otro fallo sí`() = runTest {
        var status = HttpStatusCode.NotFound
        val repository = publications { error(status, "publication_not_found") }

        repository.delete(mirador)
        status = HttpStatusCode.InternalServerError

        assertEquals(500, (failure { repository.delete(mirador) } as ApiException).status)
        assertEquals(listOf("DELETE", "DELETE"), api.requests.map { it.method })
    }

    @Test
    fun `una foto que termina de subir después del envío se agrega con su dirección`() = runTest {
        publications { empty() }.addPhoto(mirador, "https://medios.test/lugares/f2.jpg")

        val sent = api.requests.single()
        assertEquals("POST", sent.method)
        assertEquals("/v1/publications/$mirador/photos", sent.path)
        assertEquals("""{"url":"https://medios.test/lugares/f2.jpg"}""", sent.body)
    }

    @Test
    fun `la sugerencia de categoría la hace la API, y si la IA no responde es un error`() = runTest {
        var answer: MockRequestHandleScope.() -> HttpResponseData = { example("suggestion.json") }
        val suggester = ApiCategorySuggester(publicationApi { answer() })

        assertEquals(Category.GASTRONOMY, suggester.suggest("Café de la Estación", "Tostión propia y postres."))
        assertEquals("""{"title":"Café de la Estación","description":"Tostión propia y postres."}""", api.requests.single().body)
        answer = { json("{}") }
        assertNull(suggester.suggest("Lugar bonito", "Hay que ir."))
        answer = { error(HttpStatusCode.ServiceUnavailable, "suggestion_unavailable") }
        assertEquals("suggestion_unavailable", (failure { suggester.suggest("Café", "Tostión.") } as ApiException).code)
    }

    @Test
    fun `los parecidos se piden con el título, el pin y la publicación que se edita`() = runTest {
        val finder = ApiDuplicateFinder(publicationApi { example("similar-places.json") })

        val similar = finder.similarPlaces("Mirador Secreto", GeoPoint(4.55212, -75.65892), excludeId = "d2b3")

        assertEquals(
            listOf(SimilarPlace(mirador, "Mirador de la Secreta", Category.NATURE, PublicationStatus.VERIFIED, GeoPoint(4.5521, -75.6589), 3, miradorPhoto)),
            similar,
        )
        assertEquals("/v1/places/similar", api.requests.single().path)
        assertEquals(mapOf("title" to "Mirador Secreto", "near" to "4.55212,-75.65892", "excludeId" to "d2b3"), api.requests.single().query)
    }
}
