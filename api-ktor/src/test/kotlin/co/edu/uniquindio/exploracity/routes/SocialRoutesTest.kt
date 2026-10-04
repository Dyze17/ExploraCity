package co.edu.uniquindio.exploracity.routes

import co.edu.uniquindio.exploracity.model.Comments
import co.edu.uniquindio.exploracity.model.NotificationType
import co.edu.uniquindio.exploracity.model.Notifications
import co.edu.uniquindio.exploracity.model.Users
import co.edu.uniquindio.exploracity.model.Visits
import co.edu.uniquindio.exploracity.support.ApiTest
import co.edu.uniquindio.exploracity.support.Fixtures
import co.edu.uniquindio.exploracity.support.TestApi
import co.edu.uniquindio.exploracity.support.assertMatchesContract
import co.edu.uniquindio.exploracity.support.json
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.v1.core.and
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

/** 13, 14 y 14.b · El voto «Es importante», «Marcar como visitado» y los comentarios. */
class SocialRoutesTest : ApiTest() {

    private suspend fun TestApi.badgeProgress(userId: UUID, badgeId: String): Int =
        get("/v1/profile", accessToken(userId)).json().jsonObject["badges"]!!.jsonArray
            .map { it.jsonObject }
            .single { it["id"]!!.jsonPrimitive.content == badgeId }["progress"]!!.jsonPrimitive.int

    private suspend fun TestApi.points(userId: UUID): Int =
        get("/v1/profile", accessToken(userId)).json().jsonObject["author"]!!.jsonObject["points"]!!.jsonPrimitive.int

    @Test
    fun `votar suma una vez por persona y quitar el voto lo resta`() = apiTest {
        Fixtures.anaWithActivity(database)
        val pedro = register("pedro@correo.com", name = "Pedro Pérez")

        val voted = put("/v1/places/${Fixtures.PLAZA}/vote", token = pedro.accessToken)
        val again = put("/v1/places/${Fixtures.PLAZA}/vote", token = pedro.accessToken)

        assertEquals(HttpStatusCode.OK, voted.status)
        assertMatchesContract("vote.json", voted.json())
        assertEquals("""{"votes":2}""", again.bodyAsText())
        assertEquals("""{"votes":1}""", delete("/v1/places/${Fixtures.PLAZA}/vote", pedro.accessToken).bodyAsText())
        assertEquals("""{"votes":1}""", delete("/v1/places/${Fixtures.PLAZA}/vote", pedro.accessToken).bodyAsText())
        assertEquals(HttpStatusCode.NotFound, put("/v1/places/${Fixtures.CAFE}/vote", token = pedro.accessToken).status)
    }

    @Test
    fun `el voto propio cuenta en el total pero no para la insignia de quien publicó`() = apiTest {
        Fixtures.anaWithActivity(database)
        val before = badgeProgress(Fixtures.LAURA, "voz-de-la-comunidad")

        val own = put("/v1/places/${Fixtures.PLAZA}/vote", token = accessToken(Fixtures.LAURA))

        assertEquals("""{"votes":2}""", own.bodyAsText())
        assertEquals(before, badgeProgress(Fixtures.LAURA, "voz-de-la-comunidad"))
        val pedro = register("pedro@correo.com", name = "Pedro Pérez")
        put("/v1/places/${Fixtures.PLAZA}/vote", token = pedro.accessToken)
        assertEquals(before + 1, badgeProgress(Fixtures.LAURA, "voz-de-la-comunidad"))
    }

    @Test
    fun `la primera visita a un lugar ajeno da 5 puntos y volver a marcarla solo cambia la experiencia`() = apiTest {
        Fixtures.anaWithActivity(database)
        val pedro = register("pedro@correo.com", name = "Pedro Pérez")

        val first = put("/v1/places/${Fixtures.MIRADOR}/visit", """{"recommends":true,"text":" Subí al atardecer. ","showName":true}""", pedro.accessToken)
        val second = put("/v1/places/${Fixtures.MIRADOR}/visit", """{"recommends":false}""", pedro.accessToken)

        assertEquals(HttpStatusCode.OK, first.status)
        assertMatchesContract("visit.json", first.json())
        assertEquals("""{"pointsAwarded":0}""", second.bodyAsText())
        assertEquals(5, points(pedro.userId))
        assertEquals(1, badgeProgress(pedro.userId, "caminante"))
        val visit = transaction(database) {
            Visits.selectAll().where { (Visits.userId eq pedro.userId) and (Visits.placeId eq Fixtures.MIRADOR) }.single()
        }
        assertEquals(false, visit[Visits.recommends])
        assertNull(visit[Visits.text])
        assertEquals(true, (get("/v1/places/${Fixtures.MIRADOR}", pedro.accessToken).json() as JsonObject)["visited"]!!.jsonPrimitive.content.toBoolean())
    }

    @Test
    fun `visitar un lugar propio no suma puntos ni cuenta para Caminante`() = apiTest {
        Fixtures.anaWithActivity(database)
        val before = points(Fixtures.ANA)

        val own = put("/v1/places/${Fixtures.MIRADOR}/visit", """{}""", accessToken(Fixtures.ANA))

        assertEquals("""{"pointsAwarded":0}""", own.bodyAsText())
        assertEquals(before, points(Fixtures.ANA))
        // La visita de Ana a la Plaza de Laura sí cuenta.
        assertEquals(1, badgeProgress(Fixtures.ANA, "caminante"))
    }

    @Test
    fun `la experiencia tiene hasta 300 caracteres`() = apiTest {
        Fixtures.anaWithActivity(database)

        val long = put("/v1/places/${Fixtures.PLAZA}/visit", """{"text":"${"a".repeat(301)}"}""", accessToken(Fixtures.ANA))

        assertEquals(HttpStatusCode.BadRequest, long.status)
        assertEquals("""{"code":"invalid_experience"}""", long.bodyAsText())
    }

    @Test
    fun `los comentarios llegan del más reciente al más antiguo, como dice el contrato`() = apiTest {
        Fixtures.anaWithActivity(database)

        val response = get("/v1/places/${Fixtures.PLAZA}/comments", accessToken(Fixtures.LAURA))

        assertEquals(HttpStatusCode.OK, response.status)
        assertMatchesContract("comments-page.json", response.json(), volatile = setOf("id"))
    }

    @Test
    fun `los comentarios se piden de a página con un cursor, también si llegan al mismo tiempo`() = apiTest {
        Fixtures.anaWithActivity(database)
        val pedro = register("pedro@correo.com", name = "Pedro Pérez")
        repeat(24) { i ->
            if (i % 5 == 0) clock.advance(Duration.ofMinutes(1))
            post("/v1/places/${Fixtures.PLAZA}/comments", """{"text":"Comentario ${i + 1}"}""", pedro.accessToken)
        }

        val first = get("/v1/places/${Fixtures.PLAZA}/comments", pedro.accessToken).json().jsonObject
        val cursor = first["nextCursor"]!!.jsonPrimitive.content
        val second = get("/v1/places/${Fixtures.PLAZA}/comments?cursor=$cursor", pedro.accessToken).json().jsonObject

        assertEquals(25, first["total"]!!.jsonPrimitive.int)
        val texts = (first["items"]!!.jsonArray + second["items"]!!.jsonArray).map { it.jsonObject["text"]!!.jsonPrimitive.content }
        assertEquals(20, first["items"]!!.jsonArray.size)
        assertEquals(25, texts.size)
        assertEquals(25, texts.toSet().size)
        assertEquals("Muy bonita de noche, con la catedral iluminada.", texts.last())
        assertNull(second["nextCursor"])
        val malformed = get("/v1/places/${Fixtures.PLAZA}/comments?cursor=roto", pedro.accessToken)
        assertEquals("""{"code":"invalid_cursor"}""", malformed.bodyAsText())
    }

    @Test
    fun `comentar avisa a quien publicó el lugar y un reenvío con el mismo clientId no lo duplica`() = apiTest {
        Fixtures.anaWithActivity(database)
        val pedro = register("pedro@correo.com", name = "Pedro Pérez")
        val clientId = UUID.randomUUID()

        val created = post("/v1/places/${Fixtures.PLAZA}/comments", """{"text":"  Fui con mi familia.  ","clientId":"$clientId"}""", pedro.accessToken)
        val resent = post("/v1/places/${Fixtures.PLAZA}/comments", """{"text":"Fui con mi familia.","clientId":"$clientId"}""", pedro.accessToken)

        assertEquals(HttpStatusCode.Created, created.status)
        assertMatchesContract("comment.json", created.json(), volatile = setOf("id"))
        assertEquals(HttpStatusCode.OK, resent.status)
        assertEquals(created.json().jsonObject["id"], resent.json().jsonObject["id"])
        transaction(database) {
            assertEquals(2, Comments.selectAll().where { Comments.placeId eq Fixtures.PLAZA }.count())
            val notice = Notifications.selectAll().where { Notifications.type eq NotificationType.COMMENTED }.single()
            assertEquals(Fixtures.LAURA, notice[Notifications.userId])
            assertEquals("Plaza de Bolívar", notice[Notifications.placeTitle])
        }
    }

    @Test
    fun `comentar en lo propio no se avisa a uno mismo`() = apiTest {
        Fixtures.anaWithActivity(database)

        post("/v1/places/${Fixtures.PLAZA}/comments", """{"text":"Gracias por pasar."}""", accessToken(Fixtures.LAURA))

        assertEquals(0, transaction(database) { Notifications.selectAll().where { Notifications.type eq NotificationType.COMMENTED }.count() })
    }

    @Test
    fun `un comentario tiene de 1 a 300 caracteres`() = apiTest {
        Fixtures.anaWithActivity(database)
        val token = accessToken(Fixtures.LAURA)

        for (text in listOf("   ", "a".repeat(301))) {
            val response = post("/v1/places/${Fixtures.PLAZA}/comments", """{"text":"$text"}""", token)
            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertEquals("""{"code":"invalid_comment"}""", response.bodyAsText())
        }
        assertEquals(HttpStatusCode.Created, post("/v1/places/${Fixtures.PLAZA}/comments", """{"text":"${"a".repeat(300)}"}""", token).status)
    }

    @Test
    fun `el comentario de una cuenta eliminada queda sin autor`() = apiTest {
        Fixtures.anaWithActivity(database)
        transaction(database) { Users.deleteWhere { Users.id eq Fixtures.ANA } }

        val comment = get("/v1/places/${Fixtures.PLAZA}/comments", accessToken(Fixtures.LAURA)).json().jsonObject["items"]!!
            .jsonArray.single().jsonObject

        assertNull(comment["author"])
        assertEquals("Muy bonita de noche, con la catedral iluminada.", comment["text"]!!.jsonPrimitive.content)
        assertTrue(comment["mine"]!!.jsonPrimitive.content == "false")
    }
}
