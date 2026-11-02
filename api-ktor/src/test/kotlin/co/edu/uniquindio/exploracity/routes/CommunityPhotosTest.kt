package co.edu.uniquindio.exploracity.routes

import co.edu.uniquindio.exploracity.model.NotificationType
import co.edu.uniquindio.exploracity.model.Notifications
import co.edu.uniquindio.exploracity.model.PendingPhotos
import co.edu.uniquindio.exploracity.model.Photos
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * v1.4.0 · Fotos que la comunidad agrega a un lugar público (C1) y su revisión (D1). El Mirador es de Ana y tiene una
 * foto; Laura agrega; Mateo modera las fotos de Laura (Laura también modera, pero no lo suyo: F1).
 */
class CommunityPhotosTest : ApiTest() {

    private fun TestApi.laura() = accessToken(Fixtures.LAURA, Role.MODERATOR)

    private fun TestApi.mateo(): String {
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

    private suspend fun TestApi.contribute(token: String, vararg urls: String, place: UUID = Fixtures.MIRADOR) =
        post("/v1/places/$place/photos", """{"urls":[${urls.joinToString(",") { "\"$it\"" }}]}""", token)

    private suspend fun TestApi.details(token: String) = get("/v1/places/${Fixtures.MIRADOR}", token).json().jsonObject

    private suspend fun TestApi.pendingIds(token: String) =
        get("/v1/moderation/photos", token).json().jsonObject["items"]!!.jsonArray.map { it.jsonObject["id"]!!.jsonPrimitive.content }

    private fun TestApi.notices(type: NotificationType) = transaction(database) {
        Notifications.selectAll().where { Notifications.type eq type }.toList()
    }

    private fun TestApi.photoIdOf(url: String): UUID = transaction(database) { Photos.selectAll().where { Photos.url eq url }.single()[Photos.id] }

    @Test
    fun `una foto agregada espera revisión y se ve al aprobarla, con quién la agregó`() = apiTest {
        Fixtures.anaWithActivity(database)
        val mateo = mateo()
        val url = uploadPhoto(laura())

        val response = contribute(laura(), url)

        assertEquals(HttpStatusCode.NoContent, response.status)
        val waiting = details(accessToken(Fixtures.ANA))
        assertEquals(1, waiting["photos"]!!.jsonArray.size)
        assertEquals(3, waiting["photoSlots"]!!.jsonPrimitive.int)
        assertEquals(0, waiting["myPendingPhotos"]!!.jsonPrimitive.int)
        assertEquals(1, details(laura())["myPendingPhotos"]!!.jsonPrimitive.int)
        assertMatchesContract("pending-photos.json", get("/v1/moderation/photos", mateo).json(), volatile = setOf("id", "url"))
        assertEquals(1, get("/v1/moderation/summary", mateo).json().jsonObject["pendingPhotos"]!!.jsonPrimitive.int)

        val approve = post("/v1/moderation/photos/${photoIdOf(url)}/approve", null, mateo)

        assertEquals(HttpStatusCode.NoContent, approve.status)
        val gallery = details(accessToken(Fixtures.ANA))["gallery"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf(null, "Laura Gómez"), gallery.map { it["contributor"]?.jsonPrimitive?.content })
        assertEquals(url, gallery.last()["url"]!!.jsonPrimitive.content)
        assertEquals(Fixtures.LAURA, notices(NotificationType.PHOTO_APPROVED).single()[Notifications.userId])
        assertEquals(emptyList(), pendingIds(mateo))
        val again = post("/v1/moderation/photos/${photoIdOf(url)}/approve", null, mateo)
        assertEquals(HttpStatusCode.Conflict, again.status)
        assertEquals("""{"code":"already_reviewed"}""", again.bodyAsText())
    }

    @Test
    fun `rechazarla la borra del almacén y avisa con el motivo`() = apiTest {
        Fixtures.anaWithActivity(database)
        val mateo = mateo()
        val url = uploadPhoto(laura())
        contribute(laura(), url)
        val id = photoIdOf(url)

        val response = post("/v1/moderation/photos/$id/reject", """{"reason":"NOT_THE_PLACE"}""", mateo)

        assertEquals(HttpStatusCode.NoContent, response.status)
        assertTrue(transaction(database) { Photos.selectAll().where { Photos.id eq id }.empty() })
        assertTrue(media.stored.keys.none { url.endsWith(it) })
        assertEquals("no muestra el lugar", notices(NotificationType.PHOTO_REJECTED).single()[Notifications.reason])
        val notice = get("/v1/notifications", laura()).json().jsonObject["items"]!!.jsonArray
            .single { it.jsonObject["type"]!!.jsonPrimitive.content == "PHOTO_REJECTED" }.jsonObject
        assertEquals("NOT_THE_PLACE", notice["photoRejectionReason"]!!.jsonPrimitive.content)
        assertEquals(4, details(laura())["photoSlots"]!!.jsonPrimitive.int)
        assertEquals(HttpStatusCode.NotFound, post("/v1/moderation/photos/$id/reject", """{"reason":"LOW_QUALITY"}""", mateo).status)
    }

    @Test
    fun `caben 5 entre las publicadas y las que esperan`() = apiTest {
        Fixtures.anaWithActivity(database)
        val urls = List(5) { uploadPhoto(laura()) }

        val tooMany = contribute(laura(), *urls.toTypedArray())
        val four = contribute(laura(), *urls.take(4).toTypedArray())
        val fifth = contribute(laura(), urls.last())

        assertEquals(HttpStatusCode.Conflict, tooMany.status)
        assertEquals("""{"code":"too_many_photos"}""", tooMany.bodyAsText())
        assertEquals(HttpStatusCode.NoContent, four.status)
        assertEquals(HttpStatusCode.Conflict, fifth.status)
        assertEquals(0, details(accessToken(Fixtures.ANA))["photoSlots"]!!.jsonPrimitive.int)
    }

    @Test
    fun `no se agregan a lo que no se ve, ni fotos ajenas, repetidas o que ya esperan`() = apiTest {
        Fixtures.anaWithActivity(database)
        val url = uploadPhoto(laura())
        val anaPhoto = uploadPhoto(accessToken(Fixtures.ANA))

        assertEquals("""{"code":"place_not_found"}""", contribute(laura(), url, place = Fixtures.CAFE).bodyAsText())
        assertEquals("""{"code":"invalid_photos"}""", contribute(laura(), anaPhoto).bodyAsText())
        assertEquals("""{"code":"invalid_photos"}""", contribute(laura(), url, url).bodyAsText())
        assertEquals(HttpStatusCode.NoContent, contribute(laura(), url).status)
        // Ya espera en el Mirador: no va a otro lugar ni a otra publicación.
        assertEquals("""{"code":"invalid_photos"}""", contribute(laura(), url, place = Fixtures.PLAZA).bodyAsText())
    }

    @Test
    fun `un moderador no revisa las fotos que agregó`() = apiTest {
        Fixtures.anaWithActivity(database)
        val url = uploadPhoto(laura())
        contribute(laura(), url)

        val own = post("/v1/moderation/photos/${photoIdOf(url)}/approve", null, laura())

        assertEquals(emptyList(), pendingIds(laura()))
        assertEquals(HttpStatusCode.Forbidden, own.status)
        assertEquals("""{"code":"own_photo"}""", own.bodyAsText())
        assertEquals(HttpStatusCode.Forbidden, get("/v1/moderation/photos", accessToken(Fixtures.ANA)).status)
    }

    @Test
    fun `quien publicó edita su lugar con la foto que agregó otra persona`() = apiTest {
        Fixtures.anaWithActivity(database)
        val url = uploadPhoto(laura())
        contribute(laura(), url)
        post("/v1/moderation/photos/${photoIdOf(url)}/approve", null, mateo())
        val mirador = "https://res.cloudinary.com/exploracity/image/upload/lugares/mirador-1.jpg"
        val body = """{"title":"Mirador de la Secreta","category":"NATURE","description":"Un mirador con vista a toda la ciudad, ideal al atardecer.",""" +
            """"location":{"latitude":4.5521,"longitude":-75.6589},"photos":["$url","$mirador"]}"""

        val response = put("/v1/publications/${Fixtures.MIRADOR}", body, accessToken(Fixtures.ANA))

        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        val photos = response.json().jsonObject["photos"]!!.jsonArray.map { it.jsonObject["url"]!!.jsonPrimitive.content }
        assertEquals(listOf(url, mirador), photos)
    }

    @Test
    fun `borrar el lugar se lleva las fotos que esperaban, y el archivo de datos las cuenta`() = apiTest {
        Fixtures.anaWithActivity(database)
        val url = uploadPhoto(laura())
        contribute(laura(), url)
        val exported = Json.parseToJsonElement(get("/v1/account/export", laura()).bodyAsText()).jsonObject
        assertEquals(listOf(url), exported["fotosAportadas"]!!.jsonArray.map { it.jsonPrimitive.content })
        val id = photoIdOf(url)

        val response = delete("/v1/publications/${Fixtures.MIRADOR}", accessToken(Fixtures.ANA))

        assertEquals(HttpStatusCode.NoContent, response.status)
        assertTrue(transaction(database) { Photos.selectAll().where { Photos.id eq id }.empty() })
        assertTrue(transaction(database) { PendingPhotos.selectAll().empty() })
        assertFalse(media.stored.keys.any { url.endsWith(it) })
        assertNull(Json.parseToJsonElement(get("/v1/account/export", laura()).bodyAsText()).jsonObject["fotosAportadas"])
    }
}
