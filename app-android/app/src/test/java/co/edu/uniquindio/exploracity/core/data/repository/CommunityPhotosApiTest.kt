package co.edu.uniquindio.exploracity.core.data.repository

import co.edu.uniquindio.exploracity.core.data.local.AuthTokens
import co.edu.uniquindio.exploracity.core.data.remote.FakeApi
import co.edu.uniquindio.exploracity.core.data.remote.ModerationApi
import co.edu.uniquindio.exploracity.core.data.remote.PoiApi
import co.edu.uniquindio.exploracity.core.data.remote.SentRequest
import co.edu.uniquindio.exploracity.core.data.remote.TestSession
import co.edu.uniquindio.exploracity.core.data.remote.contractExample
import co.edu.uniquindio.exploracity.core.data.remote.empty
import co.edu.uniquindio.exploracity.core.data.remote.error
import co.edu.uniquindio.exploracity.core.data.remote.example
import co.edu.uniquindio.exploracity.core.data.remote.failure
import co.edu.uniquindio.exploracity.core.data.remote.json
import co.edu.uniquindio.exploracity.core.data.remote.randomSecret
import co.edu.uniquindio.exploracity.domain.model.AlreadyReviewedException
import co.edu.uniquindio.exploracity.domain.model.Notification
import co.edu.uniquindio.exploracity.domain.model.PhotoRejectionReason
import co.edu.uniquindio.exploracity.domain.model.PlaceFullException
import co.edu.uniquindio.exploracity.core.data.remote.dto.NotificationDto
import co.edu.uniquindio.exploracity.core.data.remote.dto.PlaceDetailsDto
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** v1.4.0 · Fotos de la comunidad con la API: agregarlas, el detalle con créditos, su revisión y los avisos. */
class CommunityPhotosApiTest {

    private val stores = TestSession()
    private lateinit var api: FakeApi
    private val mirador = "c1a2b3c4-d5e6-4f70-8a9b-0c1d2e3f4a5b"
    private val json = Json { ignoreUnknownKeys = true }

    private suspend fun client(handler: MockRequestHandleScope.(SentRequest) -> HttpResponseData) = run {
        stores.tokens.save(AuthTokens(randomSecret(), randomSecret()))
        api = FakeApi(handler)
        api.sessionClient(stores)
    }

    @Test
    fun `agregar fotos manda sus direcciones, y si no caben lo dice`() = runTest {
        var answer: MockRequestHandleScope.() -> HttpResponseData = { empty() }
        val contributions = ApiContributionRepository(PoiApi(client { answer() }))

        contributions.addPhotos(mirador, listOf("https://medios.test/a.jpg"))
        val sent = api.requests.single()
        assertEquals("/v1/places/$mirador/photos", sent.path)
        assertEquals(JsonObject(mapOf("urls" to JsonArray(listOf(JsonPrimitive("https://medios.test/a.jpg"))))), Json.parseToJsonElement(sent.body))

        answer = { error(HttpStatusCode.Conflict, "too_many_photos") }
        assertTrue(failure { contributions.addPhotos(mirador, listOf("https://medios.test/b.jpg")) } is PlaceFullException)
    }

    @Test
    fun `el detalle trae quién agregó cada foto y cuántas caben`() {
        val withContributor = contractExample("place.json").replace(
            """"gallery": [""",
            """"gallery": [{ "url": "https://medios.test/laura.jpg", "contributor": "Laura Gómez" },""",
        )

        val details = json.decodeFromString<PlaceDetailsDto>(withContributor).toDomain()

        assertEquals(listOf("Laura Gómez", null), details.photos.map { it.contributor })
        assertEquals(4, details.photoSlots)
        assertEquals(0, details.myPendingPhotos)
    }

    @Test
    fun `Fotos por revisar se lee del contrato y lo decidido tarde se dice`() = runTest {
        var answer: MockRequestHandleScope.(SentRequest) -> HttpResponseData = { example("pending-photos.json") }
        val moderation = ApiModerationRepository(ModerationApi(client { answer(it) }))

        val photo = moderation.pendingPhotos().single()

        assertEquals("Laura Gómez", photo.contributor?.name)
        assertEquals("Mirador de la Secreta", photo.place.title)
        assertEquals(listOf("https://res.cloudinary.com/exploracity/image/upload/lugares/mirador-1.jpg"), photo.place.photoUrls)
        assertEquals(Instant.parse("2026-10-03T15:00:00Z"), photo.submittedAt)

        answer = { empty() }
        moderation.rejectPhoto(photo.id, PhotoRejectionReason.NOT_THE_PLACE)
        assertEquals("/v1/moderation/photos/${photo.id}/reject", api.requests.last().path)
        assertEquals("""{"reason":"NOT_THE_PLACE"}""", api.requests.last().body)
        answer = { error(HttpStatusCode.Conflict, "already_reviewed") }
        assertTrue(failure { moderation.approvePhoto(photo.id) } is AlreadyReviewedException)
        answer = { error(HttpStatusCode.Conflict, "too_many_photos") }
        assertTrue(failure { moderation.approvePhoto(photo.id) } is PlaceFullException)
    }

    @Test
    fun `los avisos de una foto aprobada o rechazada`() {
        val approved = json.decodeFromString<NotificationDto>(
            """{"id":"n1","type":"PHOTO_APPROVED","createdAt":"2026-10-03T15:00:00Z","read":false,"placeId":"$mirador","placeTitle":"Mirador"}""",
        ).toDomain()
        val rejected = json.decodeFromString<NotificationDto>(
            """{"id":"n2","type":"PHOTO_REJECTED","createdAt":"2026-10-03T15:00:00Z","read":true,"placeId":"$mirador","placeTitle":"Mirador",""" +
                """"reason":"no muestra el lugar","photoRejectionReason":"NOT_THE_PLACE"}""",
        ).toDomain()

        assertEquals(Notification.PhotoApproved("n1", Instant.parse("2026-10-03T15:00:00Z"), false, mirador, "Mirador"), approved)
        assertEquals(PhotoRejectionReason.NOT_THE_PLACE, (rejected as Notification.PhotoRejected).photoRejectionReason)
    }
}
