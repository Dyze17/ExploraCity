package co.edu.uniquindio.exploracity.routes

import co.edu.uniquindio.exploracity.model.PlaceReportReason
import co.edu.uniquindio.exploracity.model.PlaceReports
import co.edu.uniquindio.exploracity.model.Places
import co.edu.uniquindio.exploracity.model.PublicationStatus
import co.edu.uniquindio.exploracity.model.ReportResolution
import co.edu.uniquindio.exploracity.model.Residency
import co.edu.uniquindio.exploracity.model.Role
import co.edu.uniquindio.exploracity.model.Users
import co.edu.uniquindio.exploracity.support.ApiTest
import co.edu.uniquindio.exploracity.support.Fixtures
import co.edu.uniquindio.exploracity.support.TestApi
import co.edu.uniquindio.exploracity.support.assertMatchesContract
import co.edu.uniquindio.exploracity.support.json
import co.edu.uniquindio.exploracity.support.randomSecret
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * v1.4.0 · Reportar un lugar (13) y «Reportados» en la moderación. Laura modera; el Mirador es de Ana y la Plaza, de
 * Laura.
 */
class PlaceReportsTest : ApiTest() {

    private val outdated = """{"reason":"OUTDATED_INFO","detail":" Ahora abre a las 9:00, no a las 8:00. "}"""

    private fun TestApi.laura() = accessToken(Fixtures.LAURA, Role.MODERATOR)

    /** Mateo, otro moderador, que puede reportar y ver lo de Laura. */
    private fun TestApi.moderator(): String {
        val id = transaction(database) {
            Users.insert {
                it[email] = "mateo.salazar@correo.com"
                it[passwordHash] = randomSecret()
                it[name] = "Mateo Salazar"
                it[residency] = Residency.RESIDENT
            }[Users.id]
        }
        return accessToken(id, Role.MODERATOR)
    }

    private fun TestApi.reports() = transaction(database) { PlaceReports.selectAll().toList() }

    private suspend fun TestApi.reportedIds(token: String) =
        get("/v1/moderation/reports", token).json().jsonObject["items"]!!.jsonArray.map { it.jsonObject["id"]!!.jsonPrimitive.content }

    private suspend fun TestApi.reportedCount(token: String) =
        get("/v1/moderation/summary", token).json().jsonObject["reported"]!!.jsonPrimitive.int

    @Test
    fun `un reporte llega a Reportados con el motivo y el detalle, como dice el contrato`() = apiTest {
        Fixtures.anaWithActivity(database)

        val response = post("/v1/places/${Fixtures.MIRADOR}/reports", outdated, laura())

        assertEquals(HttpStatusCode.NoContent, response.status)
        assertMatchesContract("reported-places.json", get("/v1/moderation/reports", laura()).json())
        assertEquals(1, reportedCount(laura()))
        val item = get("/v1/moderation/reports/${Fixtures.MIRADOR}", laura())
        assertEquals(HttpStatusCode.OK, item.status)
        assertEquals("OUTDATED_INFO", item.json().jsonObject["reasons"]!!.jsonArray.single().jsonPrimitive.content)
    }

    @Test
    fun `reportar otra vez el mismo lugar cambia el reporte abierto`() = apiTest {
        Fixtures.anaWithActivity(database)

        post("/v1/places/${Fixtures.MIRADOR}/reports", outdated, laura())
        clock.advance(Duration.ofMinutes(5))
        post("/v1/places/${Fixtures.MIRADOR}/reports", """{"reason":"CLOSED"}""", laura())

        val report = reports().single()
        assertEquals(PlaceReportReason.CLOSED, report[PlaceReports.reason])
        assertNull(report[PlaceReports.detail])
        assertEquals(NOW.plus(Duration.ofMinutes(5)), report[PlaceReports.createdAt].toInstant())
    }

    @Test
    fun `los más reportados van primero y cada persona cuenta una vez`() = apiTest {
        Fixtures.anaWithActivity(database)
        val mateo = moderator()
        val sendero = Fixtures.place(database, Fixtures.LAURA, "Sendero de Laura")
        val ana = accessToken(Fixtures.ANA)

        post("/v1/places/${Fixtures.MIRADOR}/reports", """{"reason":"WRONG_LOCATION"}""", laura())
        clock.advance(Duration.ofMinutes(1))
        post("/v1/places/${Fixtures.PLAZA}/reports", """{"reason":"BAD_PHOTOS"}""", ana)
        post("/v1/places/${Fixtures.PLAZA}/reports", """{"reason":"INAPPROPRIATE"}""", ana)
        clock.advance(Duration.ofMinutes(1))
        post("/v1/places/$sendero/reports", """{"reason":"CLOSED"}""", ana)
        post("/v1/places/$sendero/reports", """{"reason":"CLOSED"}""", mateo)

        // Entre los de un reporte, el que espera desde hace más; Laura no ve lo suyo (F1).
        assertEquals(listOf(sendero, Fixtures.MIRADOR, Fixtures.PLAZA).map { it.toString() }, reportedIds(mateo))
        assertEquals(listOf(Fixtures.MIRADOR.toString()), reportedIds(laura()))
        assertEquals(3, reportedCount(mateo))
    }

    @Test
    fun `lo propio, lo que no se ve y un detalle de más no se reportan`() = apiTest {
        Fixtures.anaWithActivity(database)
        val ana = accessToken(Fixtures.ANA)

        val own = post("/v1/places/${Fixtures.MIRADOR}/reports", outdated, ana)
        val pending = post("/v1/places/${Fixtures.CAFE}/reports", outdated, laura())
        val otherWithout = post("/v1/places/${Fixtures.MIRADOR}/reports", """{"reason":"OTHER","detail":"   "}""", laura())
        val tooLong = post("/v1/places/${Fixtures.MIRADOR}/reports", """{"reason":"CLOSED","detail":"${"a".repeat(301)}"}""", laura())

        assertEquals(HttpStatusCode.BadRequest, own.status)
        assertEquals("""{"code":"cannot_report_own"}""", own.bodyAsText())
        assertEquals(HttpStatusCode.NotFound, pending.status)
        assertEquals("""{"code":"place_not_found"}""", pending.bodyAsText())
        assertEquals("""{"code":"invalid_report"}""", otherWithout.bodyAsText())
        assertEquals("""{"code":"invalid_report"}""", tooLong.bodyAsText())
        assertEquals(emptyList(), reports())
    }

    @Test
    fun `solo un moderador ve Reportados y nunca lo suyo`() = apiTest {
        Fixtures.anaWithActivity(database)
        post("/v1/places/${Fixtures.PLAZA}/reports", """{"reason":"DUPLICATE"}""", accessToken(Fixtures.ANA))

        assertEquals(HttpStatusCode.Forbidden, get("/v1/moderation/reports", accessToken(Fixtures.ANA)).status)
        assertEquals(emptyList(), reportedIds(laura()))
        assertEquals(0, reportedCount(laura()))
        val own = get("/v1/moderation/reports/${Fixtures.PLAZA}", laura())
        assertEquals(HttpStatusCode.Forbidden, own.status)
        assertEquals("""{"code":"own_publication"}""", own.bodyAsText())
    }

    @Test
    fun `descartar cierra los reportes y el lugar sigue como estaba`() = apiTest {
        Fixtures.anaWithActivity(database)
        post("/v1/places/${Fixtures.MIRADOR}/reports", outdated, laura())

        val response = post("/v1/moderation/reports/${Fixtures.MIRADOR}/dismiss", null, laura())

        assertEquals(HttpStatusCode.NoContent, response.status)
        assertEquals(ReportResolution.DISMISSED, reports().single()[PlaceReports.resolution])
        assertEquals(Fixtures.LAURA, reports().single()[PlaceReports.resolvedBy])
        assertEquals(emptyList(), reportedIds(laura()))
        assertEquals(HttpStatusCode.NotFound, get("/v1/moderation/reports/${Fixtures.MIRADOR}", laura()).status)
        assertEquals(HttpStatusCode.OK, get("/v1/places/${Fixtures.MIRADOR}", accessToken(Fixtures.ANA)).status)
        val again = post("/v1/moderation/reports/${Fixtures.MIRADOR}/dismiss", null, laura())
        assertEquals(HttpStatusCode.Conflict, again.status)
        assertEquals("""{"code":"already_reviewed"}""", again.bodyAsText())

        // Un reporte nuevo después de descartar vuelve a abrirse.
        post("/v1/places/${Fixtures.MIRADOR}/reports", """{"reason":"CLOSED"}""", laura())
        assertEquals(listOf(Fixtures.MIRADOR.toString()), reportedIds(laura()))
    }

    @Test
    fun `pasar a finalizada cierra los reportes`() = apiTest {
        Fixtures.anaWithActivity(database)
        post("/v1/places/${Fixtures.MIRADOR}/reports", """{"reason":"CLOSED"}""", laura())

        post("/v1/moderation/resolved/${Fixtures.MIRADOR}/finalize", """{"reason":"CLOSED"}""", laura())

        assertEquals(ReportResolution.FINALIZED, reports().single()[PlaceReports.resolution])
        assertEquals(emptyList(), reportedIds(laura()))
    }

    @Test
    fun `al volver a pendiente la revisión muestra los reportes y se cierran al decidir`() = apiTest {
        Fixtures.anaWithActivity(database)
        post("/v1/places/${Fixtures.MIRADOR}/reports", outdated, laura())
        post("/v1/places/${Fixtures.MIRADOR}/reports", """{"reason":"WRONG_LOCATION"}""", laura())

        post("/v1/moderation/resolved/${Fixtures.MIRADOR}/reopen", """{"reason":"Revisar el pin, dicen que quedó en otra calle."}""", laura())
        val item = get("/v1/moderation/queue/${Fixtures.MIRADOR}", laura()).json().jsonObject

        assertEquals(emptyList(), reportedIds(laura()))
        assertEquals("la ubicación en el mapa está mal", item["reportReason"]!!.jsonPrimitive.content)
        assertEquals("WRONG_LOCATION", item["reports"]!!.jsonArray.single().jsonObject["reason"]!!.jsonPrimitive.content)
        post("/v1/moderation/queue/${Fixtures.MIRADOR}/verify", "{}", laura())
        assertEquals(ReportResolution.REVIEWED, reports().single()[PlaceReports.resolution])
        assertEquals(PublicationStatus.VERIFIED, transaction(database) { Places.selectAll().where { Places.id eq Fixtures.MIRADOR }.single()[Places.status] })
    }
}
