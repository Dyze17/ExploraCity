package co.edu.uniquindio.exploracity.routes

import co.edu.uniquindio.exploracity.model.ReportReason
import co.edu.uniquindio.exploracity.model.UserReports
import co.edu.uniquindio.exploracity.support.ApiTest
import co.edu.uniquindio.exploracity.support.Fixtures
import co.edu.uniquindio.exploracity.support.TestApi
import co.edu.uniquindio.exploracity.support.assertMatchesContract
import co.edu.uniquindio.exploracity.support.json
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 26, 28 y 31A · El perfil propio, su edición con la foto y el reporte de otro perfil. */
class ProfileRoutesTest : ApiTest() {

    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2, 3)
    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 4, 5)
    private val webp = "RIFF".toByteArray() + byteArrayOf(0, 0, 0, 0) + "WEBPVP8 ".toByteArray()

    @Test
    fun `el perfil propio responde como dice el contrato`() = apiTest {
        Fixtures.anaWithActivity(database)

        val response = get("/v1/profile", accessToken(Fixtures.ANA))

        assertEquals(HttpStatusCode.OK, response.status)
        assertMatchesContract("profile.json", response.json())
    }

    @Test
    fun `una cuenta nueva empieza sin puntos y con las insignias en cero`() = apiTest {
        val ana = register()

        val profile = get("/v1/profile", ana.accessToken).json().jsonObject

        assertEquals(0, profile["author"]!!.jsonObject["points"]!!.jsonPrimitive.int)
        assertEquals("Armenia", profile["city"]!!.jsonPrimitive.content)
        // 3 de octubre a las 10 de la mañana en Armenia.
        assertEquals("2026-10", profile["memberSince"]!!.jsonPrimitive.content)
        val badges = profile["badges"]!!.jsonArray.map { it.jsonObject }
        assertEquals(9, badges.size)
        assertTrue(badges.all { it["progress"]!!.jsonPrimitive.int == 0 })
        assertNull(profile["photo"])
    }

    @Test
    fun `editar el perfil guarda el nombre, sobre mí y cómo se presenta`() = apiTest {
        val ana = register()

        val response = put("/v1/profile", """{"name":"  Ana María Ríos ","bio":"   ","residency":"VISITOR"}""", ana.accessToken)

        assertEquals(HttpStatusCode.OK, response.status)
        val profile = response.json().jsonObject
        assertEquals("Ana María Ríos", profile["author"]!!.jsonObject["name"]!!.jsonPrimitive.content)
        assertEquals("VISITOR", profile["residency"]!!.jsonPrimitive.content)
        // «Sobre mí» vacío se guarda como null.
        assertNull(profile["bio"])
    }

    @Test
    fun `editar el perfil valida el nombre y sobre mí`() = apiTest {
        val ana = register()

        suspend fun code(name: String, bio: String) =
            put("/v1/profile", """{"name":"$name","bio":"$bio","residency":"RESIDENT"}""", ana.accessToken).json().jsonObject["code"]
                ?.jsonPrimitive?.content

        assertEquals("invalid_name", code(" A ", ""))
        assertEquals("invalid_name", code("A".repeat(41), ""))
        assertEquals("bio_too_long", code("Ana", "b".repeat(151)))
        assertNull(code("Ana", "b".repeat(150)))
    }

    @Test
    fun `subir una foto la guarda y reemplaza la anterior en el almacén`() = apiTest {
        val ana = register()

        val first = upload(ana.accessToken, jpeg)
        val second = upload(ana.accessToken, png)

        assertEquals(HttpStatusCode.OK, first.status)
        assertEquals(HttpStatusCode.OK, second.status)
        val photo = second.json().jsonObject["photo"]!!.jsonPrimitive.content
        assertTrue(photo.startsWith("https://medios.test/perfil/") && photo.endsWith(".png"), photo)
        assertEquals(listOf(png.toList()), media.stored.values.map { it.toList() })
    }

    @Test
    fun `se aceptan JPEG, PNG y WebP según su contenido, no según lo que diga el archivo`() = apiTest {
        val ana = register()

        assertEquals(HttpStatusCode.OK, upload(ana.accessToken, webp).status)
        val text = upload(ana.accessToken, "no soy una foto".toByteArray())

        assertEquals(HttpStatusCode.UnsupportedMediaType, text.status)
        assertEquals("""{"code":"unsupported_photo_type"}""", text.bodyAsText())
    }

    @Test
    fun `una foto de más de 8 MB no se sube`() = apiTest {
        val ana = register()
        val limit = 8 * 1024 * 1024

        val tooLarge = upload(ana.accessToken, jpeg + ByteArray(limit + 1 - jpeg.size))
        val justRight = upload(ana.accessToken, jpeg + ByteArray(limit - jpeg.size))

        assertEquals(HttpStatusCode.PayloadTooLarge, tooLarge.status)
        assertEquals("""{"code":"photo_too_large"}""", tooLarge.bodyAsText())
        assertEquals(HttpStatusCode.OK, justRight.status)
    }

    @Test
    fun `sin foto en el cuerpo no hay nada que subir`() = apiTest {
        val ana = register()

        val response = client.put("/v1/profile/photo") {
            bearerAuth(ana.accessToken)
            setBody(MultiPartFormDataContent(formData { append("nota", "sin foto") }))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("""{"code":"photo_missing"}""", response.bodyAsText())
    }

    @Test
    fun `quitar la foto vuelve a las iniciales y la borra del almacén`() = apiTest {
        val ana = register()
        upload(ana.accessToken, jpeg)

        val response = delete("/v1/profile/photo", ana.accessToken)

        assertEquals(HttpStatusCode.OK, response.status)
        assertNull(response.json().jsonObject["photo"])
        assertTrue(media.stored.isEmpty())
    }

    @Test
    fun `si el almacén falla, la foto no cambia`() = apiTest {
        val ana = register()
        upload(ana.accessToken, jpeg)
        media.failing = true

        val response = upload(ana.accessToken, png)

        assertEquals(HttpStatusCode.BadGateway, response.status)
        assertEquals("""{"code":"photo_upload_failed"}""", response.bodyAsText())
        media.failing = false
        val photo = (get("/v1/profile", ana.accessToken).json() as JsonObject)["photo"]!!.jsonPrimitive.content
        assertTrue(photo.endsWith(".jpg"))
    }

    @Test
    fun `reportar un perfil queda para la moderación`() = apiTest {
        val ana = register()
        val laura = register("laura@correo.com")

        val response = post("/v1/users/${laura.userId}/reports", """{"reason":"IMPERSONATION"}""", ana.accessToken)

        assertEquals(HttpStatusCode.NoContent, response.status)
        val report = transaction(database) { UserReports.selectAll().single() }
        assertEquals(laura.userId, report[UserReports.reportedId])
        assertEquals(ana.userId, report[UserReports.reporterId])
        assertEquals(ReportReason.IMPERSONATION, report[UserReports.reason])
    }

    @Test
    fun `no se puede reportar el propio perfil ni uno que no existe`() = apiTest {
        val ana = register()

        val own = post("/v1/users/${ana.userId}/reports", """{"reason":"SPAM"}""", ana.accessToken)
        val missing = post("/v1/users/${UUID.randomUUID()}/reports", """{"reason":"SPAM"}""", ana.accessToken)
        val malformed = post("/v1/users/no-es-un-id/reports", """{"reason":"SPAM"}""", ana.accessToken)
        val badReason = post("/v1/users/${UUID.randomUUID()}/reports", """{"reason":"OTRO"}""", ana.accessToken)

        assertEquals(HttpStatusCode.BadRequest, own.status)
        assertEquals("""{"code":"cannot_report_self"}""", own.bodyAsText())
        assertEquals(HttpStatusCode.NotFound, missing.status)
        assertEquals("""{"code":"user_not_found"}""", missing.bodyAsText())
        assertEquals(HttpStatusCode.NotFound, malformed.status)
        assertEquals(HttpStatusCode.BadRequest, badReason.status)
    }

    private suspend fun TestApi.upload(token: String, bytes: ByteArray): HttpResponse = client.put("/v1/profile/photo") {
        bearerAuth(token)
        setBody(
            MultiPartFormDataContent(
                formData {
                    append(
                        "photo",
                        bytes,
                        Headers.build {
                            // Lo que diga el cliente no cuenta: el formato sale del contenido.
                            append(HttpHeaders.ContentType, "image/jpeg")
                            append(HttpHeaders.ContentDisposition, "filename=\"foto.jpg\"")
                        },
                    )
                },
            ),
        )
    }
}
