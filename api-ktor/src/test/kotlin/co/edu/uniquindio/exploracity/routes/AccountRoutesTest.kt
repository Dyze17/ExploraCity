package co.edu.uniquindio.exploracity.routes

import co.edu.uniquindio.exploracity.model.Comments
import co.edu.uniquindio.exploracity.model.LinkPurpose
import co.edu.uniquindio.exploracity.model.Photos
import co.edu.uniquindio.exploracity.model.Places
import co.edu.uniquindio.exploracity.model.RefreshTokens
import co.edu.uniquindio.exploracity.model.Users
import co.edu.uniquindio.exploracity.model.Visits
import co.edu.uniquindio.exploracity.model.Votes
import co.edu.uniquindio.exploracity.service.PasswordHasher
import co.edu.uniquindio.exploracity.support.ApiTest
import co.edu.uniquindio.exploracity.support.Fixtures
import co.edu.uniquindio.exploracity.support.TestApi
import co.edu.uniquindio.exploracity.support.TestSession
import co.edu.uniquindio.exploracity.support.assertMatchesContract
import co.edu.uniquindio.exploracity.support.json
import co.edu.uniquindio.exploracity.support.newPassword
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 28, 29, 30 y «Cambiar correo» · La cuenta de la sesión. */
class AccountRoutesTest : ApiTest() {

    @Test
    fun `la cuenta trae el correo y el cambio pendiente como dice el contrato`() = apiTest {
        val ana = register()
        requestChange(ana, "Ana.Nueva@correo.com")

        val response = get("/v1/account", ana.accessToken)

        assertEquals(HttpStatusCode.OK, response.status)
        assertMatchesContract("account.json", response.json())
    }

    @Test
    fun `sin token la cuenta no responde`() = apiTest {
        assertEquals(HttpStatusCode.Unauthorized, get("/v1/account").status)
    }

    @Test
    fun `cambiar el correo pide la contraseña actual`() = apiTest {
        val ana = register()

        val response = post("/v1/account/email", """{"newEmail":"ana.nueva@correo.com","password":"${newPassword()}"}""", ana.accessToken)

        // 403 y no 401: la sesión sigue valiendo, lo que no coincide es la contraseña.
        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals("""{"code":"invalid_credentials"}""", response.bodyAsText())
        assertTrue(sentTo("ana.nueva@correo.com").isEmpty())
        assertEquals("""{"email":"ana.rios@correo.com"}""", get("/v1/account", ana.accessToken).bodyAsText())
    }

    @Test
    fun `el correo nuevo no puede ser el mismo ni uno que ya tiene cuenta`() = apiTest {
        val ana = register()
        register("laura@correo.com")

        val same = post("/v1/account/email", """{"newEmail":"ANA.RIOS@correo.com","password":"${ana.password}"}""", ana.accessToken)
        val taken = post("/v1/account/email", """{"newEmail":"laura@correo.com","password":"${ana.password}"}""", ana.accessToken)
        val invalid = post("/v1/account/email", """{"newEmail":"laura@","password":"${ana.password}"}""", ana.accessToken)

        assertEquals(HttpStatusCode.BadRequest, same.status)
        assertEquals("""{"code":"same_email"}""", same.bodyAsText())
        assertEquals(HttpStatusCode.Conflict, taken.status)
        assertEquals("""{"code":"email_taken"}""", taken.bodyAsText())
        assertEquals("""{"code":"invalid_email"}""", invalid.bodyAsText())
    }

    @Test
    fun `si el correo no sale, nada queda pendiente`() = apiTest {
        val ana = register()
        testMail.failing = true

        val response = post("/v1/account/email", """{"newEmail":"ana.nueva@correo.com","password":"${ana.password}"}""", ana.accessToken)

        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertEquals("""{"code":"email_delivery_failed"}""", response.bodyAsText())
        assertEquals("""{"email":"ana.rios@correo.com"}""", get("/v1/account", ana.accessToken).bodyAsText())
    }

    @Test
    fun `el enlace confirma el correo nuevo y desde ahí se entra con él`() = apiTest {
        val ana = register()
        val token = requestChange(ana, "ana.nueva@correo.com")

        val confirmed = post("/v1/account/email/confirm", """{"token":"$token"}""", ana.accessToken)

        assertEquals(HttpStatusCode.OK, confirmed.status)
        assertEquals("""{"email":"ana.nueva@correo.com"}""", confirmed.bodyAsText())
        assertEquals("""{"email":"ana.nueva@correo.com"}""", get("/v1/account", ana.accessToken).bodyAsText())
        assertEquals(HttpStatusCode.OK, login("ana.nueva@correo.com", ana.password).status)
        assertEquals(HttpStatusCode.Unauthorized, login("ana.rios@correo.com", ana.password).status)
    }

    @Test
    fun `otro pedido reemplaza al anterior y su enlace deja de servir`() = apiTest {
        val ana = register()
        val first = requestChange(ana, "ana.nueva@correo.com")
        requestChange(ana, "ana.otra@correo.com")

        val old = post("/v1/account/email/confirm", """{"token":"$first"}""", ana.accessToken)

        assertEquals(HttpStatusCode.Gone, old.status)
        assertEquals("""{"code":"link_expired","email":"ana.nueva@correo.com"}""", old.bodyAsText())
        assertEquals("""{"email":"ana.rios@correo.com","pendingEmail":"ana.otra@correo.com"}""", get("/v1/account", ana.accessToken).bodyAsText())
    }

    @Test
    fun `reenviar manda otro enlace y el anterior deja de servir`() = apiTest {
        val ana = register()
        val first = requestChange(ana, "ana.nueva@correo.com")
        clock.advance(Duration.ofSeconds(60))

        assertEquals(HttpStatusCode.Accepted, post("/v1/account/email/resend", token = ana.accessToken).status)

        val second = checkNotNull(linkSentTo("ana.nueva@correo.com", LinkPurpose.EMAIL_CHANGE))
        assertEquals(2, sentTo("ana.nueva@correo.com").size)
        assertEquals(HttpStatusCode.Gone, post("/v1/account/email/confirm", """{"token":"$first"}""", ana.accessToken).status)
        assertEquals(HttpStatusCode.OK, post("/v1/account/email/confirm", """{"token":"$second"}""", ana.accessToken).status)
    }

    @Test
    fun `reenviar antes de 60 segundos no envía otro y el anterior sigue sirviendo`() = apiTest {
        val ana = register()
        val first = requestChange(ana, "ana.nueva@correo.com")

        assertEquals(HttpStatusCode.Accepted, post("/v1/account/email/resend", token = ana.accessToken).status)

        assertEquals(1, sentTo("ana.nueva@correo.com").size)
        assertEquals(HttpStatusCode.OK, post("/v1/account/email/confirm", """{"token":"$first"}""", ana.accessToken).status)
    }

    @Test
    fun `reenviar sin un cambio pendiente es un conflicto`() = apiTest {
        val ana = register()

        val response = post("/v1/account/email/resend", token = ana.accessToken)

        assertEquals(HttpStatusCode.Conflict, response.status)
        assertEquals("""{"code":"no_pending_email"}""", response.bodyAsText())
    }

    @Test
    fun `el enlace del correo nuevo vence a los 30 minutos`() = apiTest {
        val ana = register()
        val token = requestChange(ana, "ana.nueva@correo.com")

        clock.advance(Duration.ofMinutes(30))
        // El token de acceso de antes ya venció (15 minutos): la app lo habría renovado.
        val response = post("/v1/account/email/confirm", """{"token":"$token"}""", accessToken(ana.userId))

        assertEquals(HttpStatusCode.Gone, response.status)
        assertEquals("""{"code":"link_expired","email":"ana.nueva@correo.com"}""", response.bodyAsText())
    }

    @Test
    fun `si otra persona se registró con ese correo mientras tanto, no se confirma`() = apiTest {
        val ana = register()
        val token = requestChange(ana, "laura@correo.com")
        register("laura@correo.com")

        val response = post("/v1/account/email/confirm", """{"token":"$token"}""", ana.accessToken)

        assertEquals(HttpStatusCode.Conflict, response.status)
        assertEquals("""{"code":"email_taken"}""", response.bodyAsText())
    }

    @Test
    fun `el enlace de otra cuenta no sirve`() = apiTest {
        val ana = register()
        val laura = register("laura@correo.com")
        val token = requestChange(ana, "ana.nueva@correo.com")

        val response = post("/v1/account/email/confirm", """{"token":"$token"}""", laura.accessToken)

        assertEquals(HttpStatusCode.Gone, response.status)
        assertEquals("""{"code":"link_expired"}""", response.bodyAsText())
    }

    @Test
    fun `descargar mis datos entrega el archivo del contrato con su nombre`() = apiTest {
        Fixtures.anaWithActivity(database)

        val response = get("/v1/account/export", accessToken(Fixtures.ANA))

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.contentType()!!.match(ContentType.Application.Json))
        val disposition = ContentDisposition.parse(response.headers[HttpHeaders.ContentDisposition]!!)
        assertEquals(ContentDisposition.Attachment.disposition, disposition.disposition)
        assertEquals("exploracity-mis-datos-2026-10-03.json", disposition.parameter(ContentDisposition.Parameters.FileName))
        assertMatchesContract("export.json", Json.parseToJsonElement(response.bodyAsText()))
    }

    @Test
    fun `eliminar la cuenta deja lo publicado sin autor y borra lo demás, también las fotos`() = apiTest {
        val password = newPassword()
        Fixtures.anaWithActivity(database, anaPasswordHash = PasswordHasher(cost = 4).hash(password))
        media.stored[Fixtures.ANA_PHOTO_ID] = byteArrayOf(1)
        media.stored[Fixtures.MIRADOR_PHOTO_ID] = byteArrayOf(2)
        val session = login(Fixtures.ANA_EMAIL, password).session(password)

        val response = delete("/v1/account", session.accessToken)

        assertEquals(HttpStatusCode.NoContent, response.status)
        transaction(database) {
            // Lo verificado sigue publicado, sin autor y sin las fotos de Ana.
            assertNull(Places.selectAll().where { Places.id eq Fixtures.MIRADOR }.single()[Places.authorId])
            assertEquals(0, Photos.selectAll().count())
            // Lo pendiente se fue con la cuenta.
            assertEquals(0, Places.selectAll().where { Places.id eq Fixtures.CAFE }.count())
            // El comentario queda como «Usuario eliminado» y los votos, como cifras.
            assertNull(Comments.selectAll().single()[Comments.authorId])
            assertEquals(1, Votes.selectAll().where { Votes.placeId eq Fixtures.PLAZA }.count())
            assertEquals(1, Votes.selectAll().where { Votes.placeId eq Fixtures.MIRADOR }.count())
            assertEquals(0, Visits.selectAll().count())
            assertEquals(0, RefreshTokens.selectAll().count())
            assertEquals(listOf(Fixtures.LAURA), Users.selectAll().map { it[Users.id] })
        }
        assertTrue(media.stored.isEmpty())
        assertEquals(HttpStatusCode.Unauthorized, login(Fixtures.ANA_EMAIL, password).status)
        assertEquals(HttpStatusCode.Unauthorized, post("/v1/auth/refresh", """{"refreshToken":"${session.refreshToken}"}""").status)
    }

    @Test
    fun `con la cuenta eliminada su token de acceso ya no sirve`() = apiTest {
        val ana = register()
        delete("/v1/account", ana.accessToken)

        assertEquals(HttpStatusCode.Unauthorized, get("/v1/account", ana.accessToken).status)
        assertEquals(HttpStatusCode.Unauthorized, delete("/v1/account", ana.accessToken).status)
    }

    @Test
    fun `si el almacén falla al borrar las fotos, la cuenta igual queda eliminada`() = apiTest {
        Fixtures.anaWithActivity(database)
        media.failing = true

        val response = delete("/v1/account", accessToken(Fixtures.ANA))

        assertEquals(HttpStatusCode.NoContent, response.status)
        assertEquals(1, transaction(database) { Users.selectAll().count() })
    }

    /** Pide el cambio de correo y devuelve el token del enlace que llegó al correo nuevo. */
    private suspend fun TestApi.requestChange(session: TestSession, newEmail: String): String {
        val response: HttpResponse =
            post("/v1/account/email", """{"newEmail":"$newEmail","password":"${session.password}"}""", session.accessToken)
        assertEquals(HttpStatusCode.Accepted, response.status, response.bodyAsText())
        return checkNotNull(linkSentTo(newEmail.lowercase(), LinkPurpose.EMAIL_CHANGE))
    }
}
