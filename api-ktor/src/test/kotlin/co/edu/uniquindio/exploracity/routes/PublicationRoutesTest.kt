package co.edu.uniquindio.exploracity.routes

import co.edu.uniquindio.exploracity.integration.CategoryClassifier
import co.edu.uniquindio.exploracity.integration.ClassifierException
import co.edu.uniquindio.exploracity.model.Category
import co.edu.uniquindio.exploracity.model.NotificationType
import co.edu.uniquindio.exploracity.model.Notifications
import co.edu.uniquindio.exploracity.model.Photos
import co.edu.uniquindio.exploracity.model.Places
import co.edu.uniquindio.exploracity.model.PublicationStatus
import co.edu.uniquindio.exploracity.support.ApiTest
import co.edu.uniquindio.exploracity.support.Fixtures
import co.edu.uniquindio.exploracity.support.JPEG
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
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Duration
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 15–24 · El formulario de publicación y las publicaciones propias. */
class PublicationRoutesTest : ApiTest() {

    private suspend fun TestApi.points(token: String): Int =
        get("/v1/profile", token).json().jsonObject["author"]!!.jsonObject["points"]!!.jsonPrimitive.int

    @Test
    fun `subir una foto del formulario la deja lista para el envío`() = apiTest {
        val pedro = register("pedro@correo.com", name = "Pedro Pérez")

        val uploaded = uploadPhotoResponse(pedro.accessToken, JPEG)

        assertEquals(HttpStatusCode.Created, uploaded.status)
        assertMatchesContract("photo.json", uploaded.json(), volatile = setOf("id", "url"))
        val url = uploaded.json().jsonObject["url"]!!.jsonPrimitive.content
        assertTrue(url.startsWith("https://medios.test/lugares/"), url)
        transaction(database) { assertNull(Photos.selectAll().single()[Photos.placeId]) }
        val text = uploadPhotoResponse(pedro.accessToken, "no soy una foto".toByteArray())
        assertEquals("""{"code":"unsupported_photo_type"}""", text.bodyAsText())
    }

    @Test
    fun `la sugerencia de categoría sale del título y la descripción`() = apiTest {
        val pedro = register("pedro@correo.com")

        val café = post("/v1/places/suggest-category", """{"title":"Café de la Estación","description":"Tostión propia y postres."}""", pedro.accessToken)
        val unclear = post("/v1/places/suggest-category", """{"title":"Lugar bonito","description":"Hay que ir."}""", pedro.accessToken)

        assertEquals(HttpStatusCode.OK, café.status)
        assertMatchesContract("suggestion.json", café.json())
        assertEquals("{}", unclear.bodyAsText())
    }

    @Test
    fun `si la IA falla o tarda, la API lo dice para que la app siga a mano`() = apiTest(classifier = FailingClassifier) {
        val pedro = register("pedro@correo.com")

        val response = post("/v1/places/suggest-category", """{"title":"Café","description":"Tostión propia."}""", pedro.accessToken)

        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertEquals("""{"code":"suggestion_unavailable"}""", response.bodyAsText())
    }

    @Test
    fun `los parecidos son los publicados a 50 metros con un título parecido, y los pendientes propios`() = apiTest {
        Fixtures.anaWithActivity(database)

        val near = get("/v1/places/similar?title=Mirador%20Secreto&near=4.55212,-75.65892", accessToken(Fixtures.LAURA))
        val far = get("/v1/places/similar?title=Mirador%20Secreto&near=4.5539,-75.6589", accessToken(Fixtures.LAURA))
        // El Café está pendiente: solo lo ve Ana, su autora.
        val othersPending = get("/v1/places/similar?title=Cafe%20Acacias&near=4.5402,-75.6721", accessToken(Fixtures.LAURA))
        val ownPending = get("/v1/places/similar?title=Cafe%20Acacias&near=4.5402,-75.6721", accessToken(Fixtures.ANA))

        assertEquals(HttpStatusCode.OK, near.status)
        assertMatchesContract("similar-places.json", near.json())
        assertEquals("[]", far.bodyAsText())
        assertEquals("[]", othersPending.bodyAsText())
        assertEquals(listOf("Café Las Acacias"), ownPending.json().jsonArray.map { it.jsonObject["title"]!!.jsonPrimitive.content })
        val excluded = get("/v1/places/similar?title=Cafe%20Acacias&near=4.5402,-75.6721&excludeId=${Fixtures.CAFE}", accessToken(Fixtures.ANA))
        assertEquals("[]", excluded.bodyAsText())
    }

    @Test
    fun `la primera publicación gana 20 puntos al enviarla y el logro`() = apiTest {
        val pedro = register("pedro@correo.com", name = "Pedro Pérez")
        val photos = listOf(uploadPhoto(pedro.accessToken), uploadPhoto(pedro.accessToken))

        val response = post(
            "/v1/publications",
            """{"clientId":"${UUID.randomUUID()}","title":"  Café de la Estación ","description":"Un café pequeño frente a la vieja estación del tren, con tostión propia.",""" +
                """"category":"GASTRONOMY","categoryOrigin":"SUGGESTED","location":{"latitude":4.545,"longitude":-75.675},"address":"Calle 21 # 18-30, Armenia",""" +
                """"hours":{"days":["SATURDAY","MONDAY"],"opens":"07:00","closes":"19:00"},"price":"LOW","photos":["${photos[1]}","${photos[0]}"]}""",
            pedro.accessToken,
        )

        assertEquals(HttpStatusCode.Created, response.status, response.bodyAsText())
        assertMatchesContract("submit.json", response.json(), volatile = setOf("publicationId"))
        assertEquals(20, points(pedro.accessToken))
        val id = UUID.fromString(response.json().jsonObject["publicationId"]!!.jsonPrimitive.content)
        transaction(database) {
            val place = Places.selectAll().where { Places.id eq id }.single()
            assertEquals("Café de la Estación", place[Places.title])
            assertEquals(PublicationStatus.PENDING, place[Places.status])
            // Lunes (1) y sábado (32).
            assertEquals(33, place[Places.hoursDays]?.toInt())
            val ordered = Photos.selectAll().where { Photos.placeId eq id }.orderBy(Photos.position).map { it[Photos.url] }
            assertEquals(listOf(photos[1], photos[0]), ordered)
            val achievement = Notifications.selectAll().where { Notifications.type eq NotificationType.ACHIEVEMENT }.single()
            assertEquals("Primera publicación", achievement[Notifications.achievement])
        }
        val second = post(
            "/v1/publications",
            """{"title":"Mirador del Tren","description":"Desde aquí se ve toda la línea del tren y la cordillera al fondo.",""" +
                """"category":"NATURE","categoryOrigin":"CHOSEN","location":{"latitude":4.546,"longitude":-75.676},"photos":["${uploadPhoto(pedro.accessToken)}"]}""",
            pedro.accessToken,
        )
        assertNull(second.json().jsonObject["firstPublicationPoints"])
        assertEquals(20, points(pedro.accessToken))
    }

    @Test
    fun `un reenvío de la cola con el mismo clientId no duplica la publicación`() = apiTest {
        val pedro = register("pedro@correo.com")
        val clientId = UUID.randomUUID()
        val photo = uploadPhoto(pedro.accessToken)
        val body = """{"clientId":"$clientId","title":"Café de la Estación","description":"Un café pequeño frente a la vieja estación del tren.",""" +
            """"category":"GASTRONOMY","categoryOrigin":"CHOSEN","location":{"latitude":4.545,"longitude":-75.675},"photos":["$photo"]}"""

        val first = post("/v1/publications", body, pedro.accessToken)
        val again = post("/v1/publications", body, pedro.accessToken)

        assertEquals(HttpStatusCode.Created, first.status)
        assertEquals(HttpStatusCode.OK, again.status)
        assertEquals(first.bodyAsText(), again.bodyAsText())
        assertEquals(1, transaction(database) { Places.selectAll().count() })
    }

    @Test
    fun `el envío valida los pasos 1 a 5`() = apiTest {
        val pedro = register("pedro@correo.com")
        val laura = register("laura@correo.com")
        val photo = uploadPhoto(pedro.accessToken)
        val other = uploadPhoto(laura.accessToken)

        suspend fun code(title: String = "Café de la Estación", photos: String = "\"$photo\"", extra: String = ""): String {
            val response = post(
                "/v1/publications",
                """{"title":"$title","description":"Un café pequeño frente a la vieja estación del tren.","category":"GASTRONOMY",""" +
                    """"categoryOrigin":"CHOSEN","location":{"latitude":4.545,"longitude":-75.675},"photos":[$photos]$extra}""",
                pedro.accessToken,
            )
            assertEquals(HttpStatusCode.BadRequest, response.status, response.bodyAsText())
            return response.json().jsonObject["code"]!!.jsonPrimitive.content
        }

        assertEquals("invalid_publication", code(title = "Café"))
        assertEquals("invalid_photos", code(photos = ""))
        assertEquals("invalid_photos", code(photos = "\"$other\""))
        assertEquals("invalid_photos", code(photos = "\"https://medios.test/lugares/no-existe.jpg\""))
        assertEquals("invalid_photos", code(photos = "\"$photo\",\"$photo\""))
        assertEquals("invalid_publication", code(extra = ""","hours":{"days":["MONDAY"],"opens":"19:00","closes":"07:00"}"""))
        assertEquals("invalid_publication", code(extra = ""","hours":{"days":[],"opens":"07:00","closes":"19:00"}"""))
        assertEquals("invalid_publication", code(extra = ""","duplicateCheck":{"location":{"latitude":4.545,"longitude":-75.675},"note":"${"a".repeat(201)}"}"""))
    }

    @Test
    fun `quien publica junto a un lugar parecido lo deja marcado como posible duplicado`() = apiTest {
        Fixtures.anaWithActivity(database)
        val pedro = register("pedro@correo.com")

        // La persona vio el Mirador en 17A y dijo que es otro lugar.
        val confirmed = publish(
            pedro.accessToken,
            title = "Mirador Secreto",
            latitude = 4.55212,
            longitude = -75.65892,
            extra = ""","duplicateCheck":{"location":{"latitude":4.55212,"longitude":-75.65892},"similarIds":["${Fixtures.MIRADOR}"],"note":" Es el de arriba. "}""",
        )
        // Sin búsqueda en el teléfono (falló): la API la repite.
        val unchecked = publish(pedro.accessToken, title = "Mirador de Secreta", latitude = 4.55213, longitude = -75.65891)

        val mine = get("/v1/publications", pedro.accessToken).json().jsonArray.map { it.jsonObject }.associateBy { it["id"]!!.jsonPrimitive.content }
        for (id in listOf(confirmed, unchecked)) {
            assertEquals("true", mine.getValue(id)["possibleDuplicate"]!!.jsonPrimitive.content)
            assertEquals(listOf(Fixtures.MIRADOR.toString()), mine.getValue(id)["similarIds"]!!.jsonArray.map { it.jsonPrimitive.content })
        }
        assertEquals("Es el de arriba.", mine.getValue(confirmed)["duplicateNote"]!!.jsonPrimitive.content)
        assertNull(mine.getValue(unchecked)["duplicateNote"])
    }

    @Test
    fun `mis publicaciones traen todo, de la más reciente a la más antigua`() = apiTest {
        Fixtures.anaWithActivity(database)

        val response = get("/v1/publications", accessToken(Fixtures.ANA))

        assertEquals(HttpStatusCode.OK, response.status)
        assertMatchesContract("publications.json", response.json())
        assertEquals(HttpStatusCode.NotFound, get("/v1/publications/${Fixtures.PLAZA}", accessToken(Fixtures.ANA)).status)
        assertEquals(HttpStatusCode.OK, get("/v1/publications/${Fixtures.CAFE}", accessToken(Fixtures.ANA)).status)
    }

    @Test
    fun `editar una verificada la devuelve a verificación y sale del feed`() = apiTest {
        Fixtures.anaWithActivity(database)
        media.stored[Fixtures.MIRADOR_PHOTO_ID] = byteArrayOf(1)
        val newPhoto = uploadPhoto(accessToken(Fixtures.ANA))
        clock.advance(Duration.ofHours(1))
        // Después de una hora, con un token de acceso nuevo (dura 15 minutos).
        val token = accessToken(Fixtures.ANA)

        val response = put(
            "/v1/publications/${Fixtures.MIRADOR}",
            """{"title":"Mirador de la Secreta","category":"NATURE","description":"Un mirador con vista a toda la ciudad, mejor al atardecer.",""" +
                """"location":{"latitude":4.5521,"longitude":-75.6589},"price":"FREE","photos":["$newPhoto"]}""",
            token,
        )

        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        val edited = response.json().jsonObject
        assertEquals("PENDING", edited["status"]!!.jsonPrimitive.content)
        assertEquals("2026-10-03T16:00:00Z", edited["submittedAt"]!!.jsonPrimitive.content)
        assertNull(edited["hours"])
        assertEquals(listOf(newPhoto), edited["photos"]!!.jsonArray.map { it.jsonObject["url"]!!.jsonPrimitive.content })
        assertEquals(HttpStatusCode.NotFound, get("/v1/places/${Fixtures.MIRADOR}", token).status)
        // La foto que salió ya no está en el almacén.
        assertTrue(!media.stored.containsKey(Fixtures.MIRADOR_PHOTO_ID))
        // El pin no se movió: la dirección sigue.
        transaction(database) {
            assertEquals("Vía al Mirador, Armenia", Places.selectAll().where { Places.id eq Fixtures.MIRADOR }.single()[Places.address])
        }
    }

    @Test
    fun `solo se editan las pendientes y las verificadas propias`() = apiTest {
        Fixtures.anaWithActivity(database)
        transaction(database) { Places.update({ Places.id eq Fixtures.MIRADOR }) { it[status] = PublicationStatus.FINALIZED } }
        val token = accessToken(Fixtures.ANA)
        val photo = uploadPhoto(token)
        val body = """{"title":"Mirador de la Secreta","category":"NATURE","description":"Un mirador con vista a toda la ciudad, ideal al atardecer.",""" +
            """"location":{"latitude":4.5521,"longitude":-75.6589},"photos":["$photo"]}"""

        val finalized = put("/v1/publications/${Fixtures.MIRADOR}", body, token)
        val others = put("/v1/publications/${Fixtures.PLAZA}", body, token)

        assertEquals(HttpStatusCode.Conflict, finalized.status)
        assertEquals("""{"code":"not_editable"}""", finalized.bodyAsText())
        assertEquals(HttpStatusCode.NotFound, others.status)
        assertEquals("""{"code":"publication_not_found"}""", others.bodyAsText())
    }

    @Test
    fun `eliminar una publicación borra sus fotos y sus puntos dejan de contar`() = apiTest {
        Fixtures.anaWithActivity(database)
        media.stored[Fixtures.MIRADOR_PHOTO_ID] = byteArrayOf(1)
        val token = accessToken(Fixtures.ANA)
        val before = points(token)

        assertEquals(HttpStatusCode.NoContent, delete("/v1/publications/${Fixtures.MIRADOR}", token).status)

        assertEquals(before - 35, points(token))
        assertTrue(media.stored.isEmpty())
        assertEquals(HttpStatusCode.NotFound, delete("/v1/publications/${Fixtures.MIRADOR}", token).status)
        assertEquals(HttpStatusCode.NotFound, delete("/v1/publications/${Fixtures.PLAZA}", token).status)
    }

    @Test
    fun `una foto que termina de subir después del envío se agrega al final, hasta 5`() = apiTest {
        val pedro = register("pedro@correo.com")
        val id = publish(pedro.accessToken)

        val photos = (1..4).map { uploadPhoto(pedro.accessToken) }
        photos.forEach { assertEquals(HttpStatusCode.NoContent, post("/v1/publications/$id/photos", """{"url":"$it"}""", pedro.accessToken).status) }
        assertEquals(HttpStatusCode.NoContent, post("/v1/publications/$id/photos", """{"url":"${photos.last()}"}""", pedro.accessToken).status)
        val sixth = post("/v1/publications/$id/photos", """{"url":"${uploadPhoto(pedro.accessToken)}"}""", pedro.accessToken)

        assertEquals(HttpStatusCode.Conflict, sixth.status)
        assertEquals("""{"code":"too_many_photos"}""", sixth.bodyAsText())
        val urls = get("/v1/publications/$id", pedro.accessToken).json().jsonObject["photos"]!!.jsonArray.map { it.jsonObject["url"]!!.jsonPrimitive.content }
        assertEquals(5, urls.size)
        assertEquals(photos, urls.drop(1))
    }

    @Test
    fun `solo se reenvía una rechazada que el moderador permitió corregir`() = apiTest {
        Fixtures.anaWithActivity(database)
        val photo = uploadPhoto(accessToken(Fixtures.ANA))

        val response = post(
            "/v1/publications",
            """{"title":"Café Las Acacias","description":"Café de origen con tostión propia y vista al parque principal.","category":"GASTRONOMY",""" +
                """"categoryOrigin":"CHOSEN","location":{"latitude":4.5402,"longitude":-75.6721},"photos":["$photo"],"resubmitId":"${Fixtures.CAFE}"}""",
            accessToken(Fixtures.ANA),
        )

        // Pendiente, no rechazada.
        assertEquals(HttpStatusCode.Conflict, response.status)
        assertEquals("""{"code":"cannot_resubmit"}""", response.bodyAsText())
    }

    private object FailingClassifier : CategoryClassifier {
        override suspend fun classify(title: String, description: String): Category? = throw ClassifierException("sin respuesta")
    }
}
