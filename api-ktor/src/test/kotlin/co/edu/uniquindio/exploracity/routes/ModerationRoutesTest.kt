package co.edu.uniquindio.exploracity.routes

import co.edu.uniquindio.exploracity.model.NotificationType
import co.edu.uniquindio.exploracity.model.Notifications
import co.edu.uniquindio.exploracity.model.Places
import co.edu.uniquindio.exploracity.model.PublicationStatus
import co.edu.uniquindio.exploracity.model.Role
import co.edu.uniquindio.exploracity.model.Users
import co.edu.uniquindio.exploracity.support.ApiTest
import co.edu.uniquindio.exploracity.support.Fixtures
import co.edu.uniquindio.exploracity.support.TestApi
import co.edu.uniquindio.exploracity.support.assertMatchesContract
import co.edu.uniquindio.exploracity.support.json
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.Duration
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 32–37 · La cola, las decisiones del moderador y «Resueltas». Laura modera; el Café pendiente es de Ana. */
class ModerationRoutesTest : ApiTest() {

    private val reopenReason = "La dirección cambió y hay que revisarla de nuevo."

    private fun TestApi.laura() = accessToken(Fixtures.LAURA, Role.MODERATOR)

    private suspend fun TestApi.points(userId: UUID): Int =
        get("/v1/profile", accessToken(userId)).json().jsonObject["author"]!!.jsonObject["points"]!!.jsonPrimitive.int

    private fun TestApi.notices(type: NotificationType) = transaction(database) {
        Notifications.selectAll().where { Notifications.type eq type }.toList()
    }

    private fun TestApi.status(id: UUID) = transaction(database) { Places.selectAll().where { Places.id eq id }.single()[Places.status] }

    @Test
    fun `solo con el rol de moderador`() = apiTest {
        Fixtures.anaWithActivity(database)

        val response = get("/v1/moderation/queue", accessToken(Fixtures.ANA))

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals("""{"code":"forbidden"}""", response.bodyAsText())
    }

    @Test
    fun `la cola trae las pendientes con lo necesario para decidir, como dice el contrato`() = apiTest {
        Fixtures.anaWithActivity(database)

        val queue = get("/v1/moderation/queue", laura())
        val summary = get("/v1/moderation/summary", laura())

        assertEquals(HttpStatusCode.OK, queue.status)
        assertMatchesContract("review-queue.json", queue.json())
        assertMatchesContract("moderation-summary.json", summary.json())
        assertEquals(HttpStatusCode.OK, get("/v1/moderation/queue/${Fixtures.CAFE}", laura()).status)
    }

    @Test
    fun `un moderador no ve ni decide sus propias publicaciones`() = apiTest {
        Fixtures.anaWithActivity(database)
        val own = Fixtures.place(database, Fixtures.LAURA, "Sendero de Laura", status = PublicationStatus.PENDING)

        val ids = get("/v1/moderation/queue", laura()).json().jsonObject["items"]!!.jsonArray.map { it.jsonObject["id"]!!.jsonPrimitive.content }
        val verify = post("/v1/moderation/queue/$own/verify", "{}", laura())

        assertEquals(listOf(Fixtures.CAFE.toString()), ids)
        assertEquals(HttpStatusCode.Forbidden, verify.status)
        assertEquals("""{"code":"own_publication"}""", verify.bodyAsText())
        assertEquals(1, get("/v1/moderation/summary", laura()).json().jsonObject["pending"]!!.jsonPrimitive.int)
    }

    @Test
    fun `verificar la publica, avisa a quien la publicó y le da 15 puntos la primera vez`() = apiTest {
        Fixtures.anaWithActivity(database)
        val before = points(Fixtures.ANA)

        val response = post("/v1/moderation/queue/${Fixtures.CAFE}/verify", """{"note":" Fotos claras. "}""", laura())

        assertEquals(HttpStatusCode.NoContent, response.status)
        assertEquals(PublicationStatus.VERIFIED, status(Fixtures.CAFE))
        assertEquals(before + 15, points(Fixtures.ANA))
        val notice = notices(NotificationType.VERIFIED).single()
        assertEquals(Fixtures.ANA, notice[Notifications.userId])
        assertEquals(15, notice[Notifications.points])
        assertEquals(HttpStatusCode.OK, get("/v1/places/${Fixtures.CAFE}", accessToken(Fixtures.ANA)).status)
        val again = post("/v1/moderation/queue/${Fixtures.CAFE}/verify", "{}", laura())
        assertEquals(HttpStatusCode.Conflict, again.status)
        assertEquals("""{"code":"already_reviewed"}""", again.bodyAsText())

        // Vuelve a pendiente y se verifica otra vez: los puntos no se repiten.
        post("/v1/moderation/resolved/${Fixtures.CAFE}/reopen", """{"reason":"$reopenReason"}""", laura())
        post("/v1/moderation/queue/${Fixtures.CAFE}/verify", "{}", laura())
        assertEquals(before + 15, points(Fixtures.ANA))
        assertEquals(listOf(15, 0), notices(NotificationType.VERIFIED).sortedBy { it[Notifications.createdAt] }.map { it[Notifications.points] })
    }

    @Test
    fun `un rechazo por la foto lleva qué corregir y permite reenviar`() = apiTest {
        Fixtures.anaWithActivity(database)

        val response = post("/v1/moderation/queue/${Fixtures.CAFE}/reject", """{"reason":"PHOTO","message":"","canResubmit":true}""", laura())

        assertEquals(HttpStatusCode.NoContent, response.status)
        assertMatchesContract("publication-rejected.json", get("/v1/publications/${Fixtures.CAFE}", accessToken(Fixtures.ANA)).json())
        assertEquals("la foto no permite reconocer el lugar", notices(NotificationType.REJECTED).single()[Notifications.reason])
        val photo = uploadPhoto(accessToken(Fixtures.ANA))
        val body = """{"clientId":"${UUID.randomUUID()}","title":"Café Las Acacias","description":"Café de origen con tostión propia y vista al parque principal.",""" +
            """"category":"GASTRONOMY","categoryOrigin":"CHOSEN","location":{"latitude":4.5402,"longitude":-75.6721},"photos":["$photo"],"resubmitId":"${Fixtures.CAFE}"}"""
        val resubmit = post("/v1/publications", body, accessToken(Fixtures.ANA))
        assertEquals(HttpStatusCode.Created, resubmit.status, resubmit.bodyAsText())
        assertEquals(PublicationStatus.PENDING, status(Fixtures.CAFE))
        // La respuesta se perdió y la app lo repite: responde la misma, no cannot_resubmit.
        val again = post("/v1/publications", body, accessToken(Fixtures.ANA))
        assertEquals(HttpStatusCode.OK, again.status, again.bodyAsText())
        assertEquals(resubmit.bodyAsText(), again.bodyAsText())
    }

    @Test
    fun `un rechazo por duplicado enlaza el original y no se reenvía`() = apiTest {
        Fixtures.anaWithActivity(database)

        val missing = post("/v1/moderation/queue/${Fixtures.CAFE}/reject", """{"reason":"DUPLICATE"}""", laura())
        val response = post("/v1/moderation/queue/${Fixtures.CAFE}/reject", """{"reason":"DUPLICATE","originalId":"${Fixtures.PLAZA}","canResubmit":true}""", laura())

        assertEquals(HttpStatusCode.BadRequest, missing.status)
        assertEquals("""{"code":"invalid_decision"}""", missing.bodyAsText())
        assertEquals(HttpStatusCode.NoContent, response.status)
        val rejection = get("/v1/publications/${Fixtures.CAFE}", accessToken(Fixtures.ANA)).json().jsonObject["rejection"]!!.jsonObject
        assertEquals("false", rejection["canResubmit"]!!.jsonPrimitive.content)
        assertEquals("Plaza de Bolívar", rejection["duplicateOf"]!!.jsonObject["title"]!!.jsonPrimitive.content)
        val notice = notices(NotificationType.DUPLICATE_REJECTED).single()
        assertEquals(Fixtures.PLAZA, notice[Notifications.existingPlaceId])
        assertEquals("Plaza de Bolívar", notice[Notifications.existingTitle])
    }

    @Test
    fun `con otro motivo el detalle es obligatorio y llega en el aviso`() = apiTest {
        Fixtures.anaWithActivity(database)

        val short = post("/v1/moderation/queue/${Fixtures.CAFE}/reject", """{"reason":"OTHER","message":"Muy corto."}""", laura())
        val ok = post("/v1/moderation/queue/${Fixtures.CAFE}/reject", """{"reason":"OTHER","message":"El horario no coincide con el letrero del local."}""", laura())

        assertEquals(HttpStatusCode.BadRequest, short.status)
        assertEquals(HttpStatusCode.NoContent, ok.status)
        assertEquals("el horario no coincide con el letrero del local", notices(NotificationType.REJECTED).single()[Notifications.reason])
    }

    @Test
    fun `la primera publicación rechazada sin permiso de reenvío pierde sus 20 puntos`() = apiTest {
        Fixtures.anaWithActivity(database)
        val pedro = register("pedro@correo.com", name = "Pedro Pérez")
        val id = publish(pedro.accessToken)
        assertEquals(20, points(pedro.userId))

        post("/v1/moderation/queue/$id/reject", """{"reason":"INAPPROPRIATE","canResubmit":false}""", laura())

        assertEquals(0, points(pedro.userId))
    }

    @Test
    fun `el original de un duplicado se elige entre los parecidos o los publicados cercanos`() = apiTest {
        Fixtures.anaWithActivity(database)
        val near = Fixtures.place(database, Fixtures.LAURA, "Café del Parque", latitude = 4.5405, longitude = -75.6720)

        val options = get("/v1/moderation/queue/${Fixtures.CAFE}/duplicate-options", laura()).json().jsonArray.map { it.jsonObject }

        assertEquals(listOf(near.toString()), options.map { it["place"]!!.jsonObject["id"]!!.jsonPrimitive.content })
        assertEquals("Laura Gómez", options.single()["author"]!!.jsonObject["name"]!!.jsonPrimitive.content)
    }

    @Test
    fun `tu trabajo de hoy cuenta lo decidido desde la medianoche de Armenia`() = apiTest {
        Fixtures.anaWithActivity(database)
        val pedro = register("pedro@correo.com")
        val second = publish(pedro.accessToken)
        post("/v1/moderation/queue/${Fixtures.CAFE}/verify", "{}", laura())
        post("/v1/moderation/queue/$second/reject", """{"reason":"PHOTO"}""", laura())
        post("/v1/moderation/resolved/${Fixtures.CAFE}/finalize", """{"reason":"CLOSED"}""", laura())

        assertMatchesContract("moderation-today.json", get("/v1/moderation/today", laura()).json())

        clock.advance(Duration.ofDays(1))
        assertEquals("""{"verified":0,"rejected":0,"finalized":0}""", get("/v1/moderation/today", laura()).bodyAsText())
    }

    @Test
    fun `resueltas trae lo decidido con su última decisión`() = apiTest {
        Fixtures.anaWithActivity(database)
        post("/v1/moderation/queue/${Fixtures.CAFE}/verify", """{"note":"Fotos claras y pin en la entrada."}""", laura())

        val resolved = get("/v1/moderation/resolved", laura())

        assertEquals(HttpStatusCode.OK, resolved.status)
        assertMatchesContract("resolved.json", resolved.json())
        assertEquals(HttpStatusCode.OK, get("/v1/moderation/resolved/${Fixtures.CAFE}", laura()).status)
        assertEquals(HttpStatusCode.NotFound, get("/v1/moderation/resolved/${Fixtures.MIRADOR}", laura()).status)
    }

    @Test
    fun `finalizar una verificada avisa con el motivo y no descuenta puntos`() = apiTest {
        Fixtures.anaWithActivity(database)
        val before = points(Fixtures.ANA)

        val response = post("/v1/moderation/resolved/${Fixtures.MIRADOR}/finalize", """{"reason":"EVENT_ENDED"}""", laura())

        assertEquals(HttpStatusCode.NoContent, response.status)
        assertEquals(PublicationStatus.FINALIZED, status(Fixtures.MIRADOR))
        assertEquals("fue un evento temporal que ya pasó", notices(NotificationType.FINALIZED).single()[Notifications.reason])
        assertEquals(before, points(Fixtures.ANA))
        val again = post("/v1/moderation/resolved/${Fixtures.MIRADOR}/finalize", """{"reason":"CLOSED"}""", laura())
        assertEquals(HttpStatusCode.Conflict, again.status)
        assertEquals("""{"code":"state_changed"}""", again.bodyAsText())
        val resolved = get("/v1/moderation/resolved/${Fixtures.MIRADOR}", laura()).json().jsonObject
        assertEquals("EVENT_ENDED", resolved["finalizeReason"]!!.jsonPrimitive.content)
    }

    @Test
    fun `volver a pendiente la saca del feed y la pone al final de la cola`() = apiTest {
        Fixtures.anaWithActivity(database)
        clock.advance(Duration.ofMinutes(5))

        val short = post("/v1/moderation/resolved/${Fixtures.MIRADOR}/reopen", """{"reason":"Revisar"}""", laura())
        val ok = post("/v1/moderation/resolved/${Fixtures.MIRADOR}/reopen", """{"reason":"$reopenReason"}""", laura())

        assertEquals(HttpStatusCode.BadRequest, short.status)
        assertEquals(HttpStatusCode.NoContent, ok.status)
        assertEquals(HttpStatusCode.NotFound, get("/v1/places/${Fixtures.MIRADOR}", laura()).status)
        val queue = get("/v1/moderation/queue", laura()).json().jsonObject["items"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf(Fixtures.CAFE.toString(), Fixtures.MIRADOR.toString()), queue.map { it["id"]!!.jsonPrimitive.content })
        assertEquals("2026-10-03T15:05:00Z", queue.last()["submittedAt"]!!.jsonPrimitive.content)
    }

    @Test
    fun `un lugar sin autor no vuelve a pendiente`() = apiTest {
        Fixtures.anaWithActivity(database)
        transaction(database) { Users.deleteWhere { Users.id eq Fixtures.ANA } }

        val response = post("/v1/moderation/resolved/${Fixtures.MIRADOR}/reopen", """{"reason":"$reopenReason"}""", laura())

        assertEquals(HttpStatusCode.Conflict, response.status)
        assertEquals("""{"code":"cannot_reopen"}""", response.bodyAsText())
        assertTrue(get("/v1/moderation/queue", laura()).json().jsonObject["items"]!!.jsonArray.isEmpty())
        assertNull(transaction(database) { Places.selectAll().where { Places.id eq Fixtures.MIRADOR }.single()[Places.authorId] })
    }
}
