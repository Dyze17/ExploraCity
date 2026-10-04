package co.edu.uniquindio.exploracity.data.repository

import co.edu.uniquindio.exploracity.data.local.AuthTokens
import co.edu.uniquindio.exploracity.data.remote.ApiException
import co.edu.uniquindio.exploracity.data.remote.FakeApi
import co.edu.uniquindio.exploracity.data.remote.ModerationApi
import co.edu.uniquindio.exploracity.data.remote.SentRequest
import co.edu.uniquindio.exploracity.data.remote.TestSession
import co.edu.uniquindio.exploracity.data.remote.contractExample
import co.edu.uniquindio.exploracity.data.remote.empty
import co.edu.uniquindio.exploracity.data.remote.error
import co.edu.uniquindio.exploracity.data.remote.example
import co.edu.uniquindio.exploracity.data.remote.failure
import co.edu.uniquindio.exploracity.data.remote.json
import co.edu.uniquindio.exploracity.data.remote.randomSecret
import co.edu.uniquindio.exploracity.domain.model.AlreadyReviewedException
import co.edu.uniquindio.exploracity.domain.model.Author
import co.edu.uniquindio.exploracity.domain.model.Category
import co.edu.uniquindio.exploracity.domain.model.CategoryOrigin
import co.edu.uniquindio.exploracity.domain.model.FinalizeReason
import co.edu.uniquindio.exploracity.domain.model.GeoPoint
import co.edu.uniquindio.exploracity.domain.model.ModerationWork
import co.edu.uniquindio.exploracity.domain.model.PublicationStatus
import co.edu.uniquindio.exploracity.domain.model.RejectDecision
import co.edu.uniquindio.exploracity.domain.model.RejectionReason
import co.edu.uniquindio.exploracity.domain.model.ReviewAuthor
import co.edu.uniquindio.exploracity.domain.model.StateChangedException
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** 32–37 con la API: la cola, las decisiones, «Resueltas» y las decisiones que llegan tarde. */
class ApiModerationRepositoryTest {

    private val stores = TestSession()
    private val accessToken = randomSecret()
    private lateinit var api: FakeApi

    private suspend fun moderation(handler: MockRequestHandleScope.(SentRequest) -> HttpResponseData): ApiModerationRepository {
        stores.tokens.save(AuthTokens(accessToken, randomSecret()))
        api = FakeApi(handler)
        return ApiModerationRepository(ModerationApi(api.sessionClient(stores)))
    }

    private val cafe = "d2b3c4d5-e6f7-4a81-9bac-1d2e3f4a5b6c"
    private val mirador = "c1a2b3c4-d5e6-4f70-8a9b-0c1d2e3f4a5b"

    /** La pendiente del contrato, marcada como posible duplicado del Mirador. */
    private fun suspectedItem(): String {
        val item = Json.parseToJsonElement(contractExample("review-queue.json")).jsonObject.getValue("items").jsonArray[0].jsonObject
        val place = Json.parseToJsonElement(contractExample("feed-page.json")).jsonObject.getValue("items").jsonArray[1]
        val duplicate = Json.parseToJsonElement(
            """{"candidates":[{"place":$place,"distanceMeters":12,"publishedAt":"2026-09-01T14:00:00Z","photoCount":1}],"authorNote":"Es el de abajo."}""",
        )
        return JsonObject(item + mapOf("address" to JsonPrimitive("Cra. 14 #12-30, Armenia"), "duplicate" to duplicate)).toString()
    }

    @Test
    fun `la cola llega como dice el contrato y deja el badge y el orden al día`() = runTest {
        val repository = moderation { example("review-queue.json") }

        val item = repository.queue().items.single()

        assertEquals(cafe, item.id)
        assertEquals("Café Las Acacias", item.title)
        assertEquals(Category.GASTRONOMY, item.category)
        assertEquals(CategoryOrigin.CHOSEN, item.categoryOrigin)
        assertEquals(GeoPoint(4.5402, -75.6721), item.location)
        assertEquals(Instant.parse("2026-09-20T16:30:00Z"), item.submittedAt)
        assertEquals(ReviewAuthor(Author("3f6c1e2a-8b4d-4c7e-9a1f-2d5b7e9c0a14", "Ana Ríos", 40), verified = 1, rejected = 0), item.author)
        assertNull(item.address)
        assertNull(item.reportReason)
        assertNull(item.duplicate)
        assertEquals(listOf(cafe), repository.queueIds())
        assertEquals(1, repository.pendingCount.value)
        assertEquals("/v1/moderation/queue", api.requests.single().path)
        assertEquals("Bearer $accessToken", api.requests.single().authorization)
    }

    @Test
    fun `una posible duplicada trae los lugares con que se compara y la nota del autor`() = runTest {
        val item = moderation { json(suspectedItem()) }.item(cafe)!!

        val duplicate = item.duplicate!!
        val candidate = duplicate.candidates.single()
        assertEquals("Es el de abajo.", duplicate.authorNote)
        assertEquals(mirador, candidate.poi.id)
        assertEquals(12, candidate.distanceMeters)
        // La cuenta que publicó el Mirador se eliminó.
        assertNull(candidate.author)
        assertEquals(Instant.parse("2026-09-01T14:00:00Z"), candidate.publishedAt)
        assertEquals(1, candidate.photoCount)
        assertEquals("Cra. 14 #12-30, Armenia", item.address)
        assertEquals("/v1/moderation/queue/$cafe", api.requests.single().path)
    }

    @Test
    fun `una pendiente que ya decidió otra persona o que se borró no está, y otro error sí se dice`() = runTest {
        var answer: MockRequestHandleScope.() -> HttpResponseData = { error(HttpStatusCode.Conflict, "already_reviewed") }
        val repository = moderation { answer() }

        assertNull(repository.item(cafe))
        answer = { error(HttpStatusCode.NotFound, "publication_not_found") }
        assertNull(repository.item(cafe))
        answer = { error(HttpStatusCode.Forbidden, "own_publication") }
        assertEquals("own_publication", (failure { repository.item(cafe) } as ApiException).code)
    }

    @Test
    fun `el resumen del feed y el trabajo de hoy`() = runTest {
        val repository = moderation { sent -> if (sent.path.endsWith("/summary")) example("moderation-summary.json") else example("moderation-today.json") }

        assertEquals(ModerationSummary(pending = 1, oldestWaitingDays = 12), repository.summary())
        assertEquals(1, repository.pendingCount.value)
        assertEquals(ModerationWork(verified = 1, rejected = 1, finalized = 1), repository.todayWork())
        assertEquals(listOf("/v1/moderation/summary", "/v1/moderation/today"), api.requests.map { it.path })
    }

    @Test
    fun `verificar manda la nota interna y la saca de la cola`() = runTest {
        val repository = moderation { sent -> if (sent.method == "GET") example("review-queue.json") else empty() }
        repository.queue()

        repository.verify(cafe, "  Fotos claras y pin en la entrada. ")

        val sent = api.requests.last()
        assertEquals("POST", sent.method)
        assertEquals("/v1/moderation/queue/$cafe/verify", sent.path)
        assertEquals("""{"note":"Fotos claras y pin en la entrada."}""", sent.body)
        assertTrue(repository.queueIds().isEmpty())
        assertEquals(0, repository.pendingCount.value)
        repository.verify(cafe, " ")
        assertEquals("{}", api.requests.last().body)
    }

    @Test
    fun `si otra persona ya la decidió, avisa y la saca de la cola igual`() = runTest {
        val repository = moderation { sent -> if (sent.method == "GET") example("review-queue.json") else error(HttpStatusCode.Conflict, "already_reviewed") }
        repository.queue()

        val error = failure { repository.reject(cafe, RejectDecision(RejectionReason.PHOTO, "", canResubmit = true)) }

        assertTrue(error is AlreadyReviewedException)
        assertTrue(repository.queueIds().isEmpty())
        assertEquals(0, repository.pendingCount.value)
    }

    @Test
    fun `rechazar manda el motivo, el mensaje, si puede reenviar y el original de un duplicado`() = runTest {
        val repository = moderation { empty() }

        repository.reject(cafe, RejectDecision(RejectionReason.DUPLICATE, "", canResubmit = false, originalId = mirador))
        repository.reject(cafe, RejectDecision(RejectionReason.OTHER, " El horario no corresponde al del letrero. ", canResubmit = true))

        assertEquals("/v1/moderation/queue/$cafe/reject", api.requests.first().path)
        assertEquals("""{"reason":"DUPLICATE","message":"","canResubmit":false,"originalId":"$mirador"}""", api.requests.first().body)
        assertEquals("""{"reason":"OTHER","message":"El horario no corresponde al del letrero.","canResubmit":true}""", api.requests.last().body)
    }

    @Test
    fun `las opciones para enlazar el original de un duplicado`() = runTest {
        val place = Json.parseToJsonElement(contractExample("feed-page.json")).jsonObject.getValue("items").jsonArray[1]
        val author = """{"id":"3f6c1e2a-8b4d-4c7e-9a1f-2d5b7e9c0a14","name":"Ana Ríos","points":40}"""
        val repository = moderation { json("""[{"place":$place,"distanceMeters":120,"author":$author,"photoCount":1}]""") }

        val option = repository.duplicateOptions(cafe).single()

        assertEquals(mirador, option.poi.id)
        assertEquals(120, option.distanceMeters)
        assertEquals("Ana Ríos", option.author?.name)
        assertNull(option.publishedAt)
        assertEquals("/v1/moderation/queue/$cafe/duplicate-options", api.requests.single().path)
    }

    @Test
    fun `resueltas llegan como dice el contrato, y sin autor si la cuenta se eliminó`() = runTest {
        val withoutAuthor = Json.parseToJsonElement(contractExample("resolved.json")).jsonArray[0].jsonObject - "authorName"
        val repository = moderation { sent -> if (sent.path.endsWith(cafe)) json(JsonObject(withoutAuthor).toString()) else example("resolved.json") }

        val resolved = repository.resolved().single()
        val orphan = repository.resolvedItem(cafe)!!

        assertEquals(cafe, resolved.id)
        assertEquals(PublicationStatus.VERIFIED, resolved.status)
        assertEquals("Ana Ríos", resolved.authorName)
        assertEquals(Instant.parse("2026-10-03T15:00:00Z"), resolved.decidedAt)
        assertEquals("Laura Gómez", resolved.decidedBy)
        assertEquals("Fotos claras y pin en la entrada.", resolved.note)
        assertNull(resolved.rejectionReason)
        assertTrue(resolved.canChangeState)
        assertNull(orphan.authorName)
        assertEquals(listOf("/v1/moderation/resolved", "/v1/moderation/resolved/$cafe"), api.requests.map { it.path })
    }

    @Test
    fun `una resuelta que volvió a pendiente ya no está`() = runTest {
        assertNull(moderation { error(HttpStatusCode.NotFound, "publication_not_found") }.resolvedItem(cafe))
    }

    @Test
    fun `finalizar y volver a pendiente mandan su motivo`() = runTest {
        val repository = moderation { empty() }

        repository.finalize(mirador, FinalizeReason.CLOSED)
        repository.reopen(mirador, "  La foto nueva no muestra la entrada del lugar. ")

        assertEquals("/v1/moderation/resolved/$mirador/finalize", api.requests.first().path)
        assertEquals("""{"reason":"CLOSED"}""", api.requests.first().body)
        assertEquals("/v1/moderation/resolved/$mirador/reopen", api.requests.last().path)
        assertEquals("""{"reason":"La foto nueva no muestra la entrada del lugar."}""", api.requests.last().body)
    }

    @Test
    fun `si cambió de estado o la borraron, avisa, y otro conflicto sí se dice`() = runTest {
        var answer: MockRequestHandleScope.() -> HttpResponseData = { error(HttpStatusCode.Conflict, "state_changed") }
        val repository = moderation { answer() }

        assertTrue(failure { repository.finalize(mirador, FinalizeReason.MERGED) } is StateChangedException)
        answer = { error(HttpStatusCode.NotFound, "publication_not_found") }
        assertTrue(failure { repository.reopen(mirador, "La foto nueva no muestra la entrada.") } is StateChangedException)
        answer = { error(HttpStatusCode.Conflict, "cannot_reopen") }
        assertEquals("cannot_reopen", (failure { repository.reopen(mirador, "La foto nueva no muestra la entrada.") } as ApiException).code)
    }
}
