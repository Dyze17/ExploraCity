package co.edu.uniquindio.exploracity.routes

import co.edu.uniquindio.exploracity.model.NotificationType
import co.edu.uniquindio.exploracity.model.Notifications
import co.edu.uniquindio.exploracity.model.UserBadges
import co.edu.uniquindio.exploracity.support.ApiTest
import co.edu.uniquindio.exploracity.support.Fixtures
import co.edu.uniquindio.exploracity.support.assertMatchesContract
import co.edu.uniquindio.exploracity.support.json
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.Duration
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 25 y 27 · Los avisos de cada persona y los logros al desbloquear una insignia (B1). */
class NotificationRoutesTest : ApiTest() {

    @Test
    fun `los avisos llegan del más reciente al más antiguo, con lo que pide cada frase`() = apiTest {
        Fixtures.anaWithActivity(database)
        // Uno de Moderación (parte 4), guardado a mano para ver cómo se cuenta.
        transaction(database) {
            Notifications.insert {
                it[userId] = Fixtures.LAURA
                it[type] = NotificationType.VERIFIED
                it[placeId] = Fixtures.PLAZA
                it[placeTitle] = "Plaza de Bolívar"
                it[points] = 35
                it[createdAt] = NOW.minus(Duration.ofHours(2)).atOffset(ZoneOffset.UTC)
            }
        }
        clock.advance(Duration.ofMinutes(5))
        post("/v1/places/${Fixtures.PLAZA}/comments", """{"text":"La catedral de noche es otra cosa."}""", accessToken(Fixtures.ANA))

        val response = get("/v1/notifications", accessToken(Fixtures.LAURA))

        assertEquals(HttpStatusCode.OK, response.status)
        assertMatchesContract("notifications.json", response.json(), volatile = setOf("id", "placeId"))
    }

    @Test
    fun `marcar un aviso como leído, o todos`() = apiTest {
        Fixtures.anaWithActivity(database)
        val pedro = register("pedro@correo.com", name = "Pedro Pérez")
        post("/v1/places/${Fixtures.PLAZA}/comments", """{"text":"Primero"}""", pedro.accessToken)
        post("/v1/places/${Fixtures.PLAZA}/comments", """{"text":"Segundo"}""", pedro.accessToken)
        val laura = accessToken(Fixtures.LAURA)
        val first = get("/v1/notifications", laura).json().jsonObject["items"]!!.jsonArray.first().jsonObject["id"]!!.jsonPrimitive.content

        assertEquals(HttpStatusCode.NoContent, post("/v1/notifications/$first/read", token = laura).status)
        assertEquals(HttpStatusCode.NoContent, post("/v1/notifications/$first/read", token = laura).status)
        assertEquals(1, get("/v1/notifications", laura).json().jsonObject["unread"]!!.jsonPrimitive.int)
        assertEquals(HttpStatusCode.NotFound, post("/v1/notifications/$first/read", token = pedro.accessToken).status)
        assertEquals(HttpStatusCode.NotFound, post("/v1/notifications/${UUID.randomUUID()}/read", token = laura).status)
        assertEquals(HttpStatusCode.NoContent, post("/v1/notifications/read-all", token = laura).status)
        assertEquals(0, get("/v1/notifications", laura).json().jsonObject["unread"]!!.jsonPrimitive.int)
    }

    @Test
    fun `si quien comentó eliminó su cuenta, el aviso queda sin nombre`() = apiTest {
        Fixtures.anaWithActivity(database)
        val pedro = register("pedro@correo.com", name = "Pedro Pérez")
        post("/v1/places/${Fixtures.PLAZA}/comments", """{"text":"Hasta luego"}""", pedro.accessToken)
        delete("/v1/account", pedro.accessToken)

        val notice = get("/v1/notifications", accessToken(Fixtures.LAURA)).json().jsonObject["items"]!!.jsonArray.single().jsonObject

        assertEquals("COMMENTED", notice["type"]!!.jsonPrimitive.content)
        assertNull(notice["authorName"])
        assertEquals("Hasta luego", notice["excerpt"]!!.jsonPrimitive.content)
    }

    @Test
    fun `al desbloquear una insignia llega un aviso de logro, una sola vez`() = apiTest {
        val pedro = register("pedro@correo.com", name = "Pedro Pérez")
        val places = (1..20).map { i -> Fixtures.place(database, author = null, title = "Lugar para visitar $i") }

        places.take(19).forEach { put("/v1/places/$it/visit", "{}", pedro.accessToken) }
        assertEquals(0, get("/v1/notifications", pedro.accessToken).json().jsonObject["items"]!!.jsonArray.size)
        put("/v1/places/${places.last()}/visit", "{}", pedro.accessToken)
        put("/v1/places/${places.last()}/visit", """{"recommends":true}""", pedro.accessToken)

        val items = get("/v1/notifications", pedro.accessToken).json().jsonObject["items"]!!.jsonArray
        val notice = items.single().jsonObject
        assertEquals("ACHIEVEMENT", notice["type"]!!.jsonPrimitive.content)
        assertEquals("Caminante", notice["achievement"]!!.jsonPrimitive.content)
        // Las demás están en cero: la más cercana es la primera del catálogo.
        assertEquals("Primera publicación", notice["nextBadge"]!!.jsonPrimitive.content)
        assertEquals(1, notice["remaining"]!!.jsonPrimitive.int)
    }

    @Test
    fun `una insignia desbloqueada no se pierde aunque la cifra baje`() = apiTest {
        val pedro = register("pedro@correo.com", name = "Pedro Pérez")
        transaction(database) {
            UserBadges.insert {
                it[userId] = pedro.userId
                it[badgeId] = "voz-de-la-comunidad"
            }
        }

        val badge = get("/v1/profile", pedro.accessToken).json().jsonObject["badges"]!!.jsonArray.map { it.jsonObject }
            .single { it["id"]!!.jsonPrimitive.content == "voz-de-la-comunidad" }

        assertEquals(1000, badge["progress"]!!.jsonPrimitive.int)
    }

    @Test
    fun `cada persona ve solo sus avisos`() = apiTest {
        Fixtures.anaWithActivity(database)
        post("/v1/places/${Fixtures.PLAZA}/comments", """{"text":"Hola"}""", accessToken(Fixtures.ANA))

        assertEquals("""{"items":[],"unread":0}""", get("/v1/notifications", accessToken(UUID.randomUUID())).bodyAsText())
    }
}
