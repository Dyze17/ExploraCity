package co.edu.uniquindio.exploracity.core.data.repository

import co.edu.uniquindio.exploracity.core.data.connectivity.FakeConnectivity
import co.edu.uniquindio.exploracity.core.data.connectivity.OfflineException
import co.edu.uniquindio.exploracity.core.data.local.AuthTokens
import co.edu.uniquindio.exploracity.core.data.remote.FakeApi
import co.edu.uniquindio.exploracity.core.data.remote.ModerationApi
import co.edu.uniquindio.exploracity.core.data.remote.PoiApi
import co.edu.uniquindio.exploracity.core.data.remote.SentRequest
import co.edu.uniquindio.exploracity.core.data.remote.TestSession
import co.edu.uniquindio.exploracity.core.data.remote.empty
import co.edu.uniquindio.exploracity.core.data.remote.error
import co.edu.uniquindio.exploracity.core.data.remote.example
import co.edu.uniquindio.exploracity.core.data.remote.failure
import co.edu.uniquindio.exploracity.core.data.remote.json
import co.edu.uniquindio.exploracity.core.data.remote.randomSecret
import co.edu.uniquindio.exploracity.domain.model.AlreadyReviewedException
import co.edu.uniquindio.exploracity.domain.model.Category
import co.edu.uniquindio.exploracity.domain.model.PlaceReport
import co.edu.uniquindio.exploracity.domain.model.PlaceReportReason
import co.edu.uniquindio.exploracity.domain.model.PublicationStatus
import co.edu.uniquindio.exploracity.domain.model.ReportedPlace
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** v1.4.0 · Reportar un lugar (13) y «Reportados» con la API. */
class PlaceReportsApiTest {

    private val stores = TestSession()
    private lateinit var api: FakeApi
    private val mirador = "c1a2b3c4-d5e6-4f70-8a9b-0c1d2e3f4a5b"

    private suspend fun client(handler: MockRequestHandleScope.(SentRequest) -> HttpResponseData) = run {
        stores.tokens.save(AuthTokens(randomSecret(), randomSecret()))
        api = FakeApi(handler)
        api.sessionClient(stores)
    }

    @Test
    fun `el reporte va con el motivo y el detalle`() = runTest {
        val reports = ApiContributionRepository(PoiApi(client { empty() }))

        reports.reportPlace(mirador, PlaceReportReason.OUTDATED_INFO, "Ahora abre a las 9:00.")

        val sent = api.requests.single()
        assertEquals("POST", sent.method)
        assertEquals("/v1/places/$mirador/reports", sent.path)
        assertEquals(
            JsonObject(mapOf("reason" to JsonPrimitive("OUTDATED_INFO"), "detail" to JsonPrimitive("Ahora abre a las 9:00."))),
            Json.parseToJsonElement(sent.body),
        )
    }

    @Test
    fun `sin red no se intenta`() = runTest {
        val reports = OnlineOnlyContributionRepository(ApiContributionRepository(PoiApi(client { empty() })), FakeConnectivity(online = false))

        assertTrue(failure { reports.reportPlace(mirador, PlaceReportReason.CLOSED, null) } is OfflineException)
        assertEquals(emptyList<SentRequest>(), api.requests)
    }

    @Test
    fun `Reportados se lee del contrato`() = runTest {
        val moderation = ApiModerationRepository(ModerationApi(client { example("reported-places.json") }))

        val reported = moderation.reported()

        val created = Instant.parse("2026-10-03T15:00:00Z")
        assertEquals(
            listOf(
                ReportedPlace(
                    id = mirador,
                    title = "Mirador de la Secreta",
                    category = Category.NATURE,
                    status = PublicationStatus.VERIFIED,
                    photoUrl = "https://res.cloudinary.com/exploracity/image/upload/lugares/mirador-1.jpg",
                    authorName = "Ana Ríos",
                    reasons = listOf(PlaceReportReason.OUTDATED_INFO),
                    reports = listOf(PlaceReport(PlaceReportReason.OUTDATED_INFO, "Ahora abre a las 9:00, no a las 8:00.", created)),
                    firstReportedAt = created,
                ),
            ),
            reported,
        )
        assertEquals("/v1/moderation/reports", api.requests.single().path)
    }

    @Test
    fun `descartar reportes que ya cerró otro moderador lo dice como ya revisado`() = runTest {
        var answer: MockRequestHandleScope.() -> HttpResponseData = { empty() }
        val moderation = ApiModerationRepository(ModerationApi(client { answer() }))

        moderation.dismissReports(mirador)
        assertEquals("/v1/moderation/reports/$mirador/dismiss", api.requests.last().path)
        answer = { error(HttpStatusCode.Conflict, "already_reviewed") }
        assertTrue(failure { moderation.dismissReports(mirador) } is AlreadyReviewedException)
        answer = { error(HttpStatusCode.NotFound, "publication_not_found") }
        assertTrue(failure { moderation.dismissReports(mirador) } is AlreadyReviewedException)
    }

    @Test
    fun `la revisión trae los reportes abiertos`() = runTest {
        val body = """
            {"id":"$mirador","title":"Mirador de la Secreta","category":"NATURE","categoryOrigin":"SUGGESTED",
             "description":"Un mirador con vista a toda la ciudad, ideal al atardecer.","photos":[],
             "location":{"latitude":4.5339,"longitude":-75.6811},"submittedAt":"2026-10-03T15:00:00Z",
             "author":{"author":{"id":"a","name":"Ana Ríos","points":40},"verified":1,"rejected":0},
             "reportReason":"cerró o ya no existe",
             "reports":[{"reason":"CLOSED","detail":"Ya no está.","createdAt":"2026-10-02T15:00:00Z"}]}
        """.trimIndent()
        val moderation = ApiModerationRepository(ModerationApi(client { json(body) }))

        val item = checkNotNull(moderation.item(mirador))

        assertEquals(listOf(PlaceReport(PlaceReportReason.CLOSED, "Ya no está.", Instant.parse("2026-10-02T15:00:00Z"))), item.reports)
    }
}
