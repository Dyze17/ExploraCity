package co.edu.uniquindio.exploracity.routes

import co.edu.uniquindio.exploracity.support.ApiTest
import co.edu.uniquindio.exploracity.support.assertMatchesContract
import co.edu.uniquindio.exploracity.support.json
import co.edu.uniquindio.exploracity.support.newPassword
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

/** Solo en desarrollo · El buzón que leen los botones de prueba de 6.a y «Cambiar correo». */
class DevRoutesTest : ApiTest() {

    @Test
    fun `sin DEV_MAILBOX el buzón no existe`() = apiTest {
        val ana = register()

        val response = post("/v1/dev/mailbox/latest-link", """{"email":"${ana.email}","purpose":"PASSWORD_RESET"}""")

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals("""{"code":"not_found"}""", response.bodyAsText())
    }

    @Test
    fun `el buzón da el último enlace que llegó y aún sirve`() = apiTest(devMailbox = true) {
        val ana = register()
        post("/v1/auth/password/forgot", """{"email":"${ana.email}"}""")

        val latest = post("/v1/dev/mailbox/latest-link", """{"email":"Ana.Rios@correo.com","purpose":"PASSWORD_RESET"}""")

        assertEquals(HttpStatusCode.OK, latest.status)
        assertMatchesContract("dev-link.json", latest.json(), volatile = setOf("token"))
        val token = latest.token()
        assertEquals(HttpStatusCode.OK, post("/v1/auth/password/link", """{"token":"$token"}""").status)

        post("/v1/auth/password/reset", """{"token":"$token","password":"${newPassword()}"}""")
        val used = post("/v1/dev/mailbox/latest-link", """{"email":"${ana.email}","purpose":"PASSWORD_RESET"}""")
        assertEquals(HttpStatusCode.NotFound, used.status)
        assertEquals("""{"code":"link_not_found"}""", used.bodyAsText())
    }

    @Test
    fun `el buzón emite un enlace ya vencido para la cuenta`() = apiTest(devMailbox = true) {
        val ana = register()

        val expired = post("/v1/dev/mailbox/expired-link", """{"email":"${ana.email}","purpose":"PASSWORD_RESET"}""")
        val opened = post("/v1/auth/password/link", """{"token":"${expired.token()}"}""")

        assertEquals(HttpStatusCode.Gone, opened.status)
        assertMatchesContract("link-expired.json", opened.json())
        // Ese enlace no frena el siguiente pedido.
        post("/v1/auth/password/forgot", """{"email":"${ana.email}"}""")
        val latest = post("/v1/dev/mailbox/latest-link", """{"email":"${ana.email}","purpose":"PASSWORD_RESET"}""")
        assertEquals(HttpStatusCode.OK, latest.status)
        val unknown = post("/v1/dev/mailbox/expired-link", """{"email":"nadie@correo.com","purpose":"PASSWORD_RESET"}""")
        assertEquals(HttpStatusCode.NotFound, unknown.status)
        assertEquals("""{"code":"account_not_found"}""", unknown.bodyAsText())
    }

    @Test
    fun `para cambiar el correo da el enlace del correo pendiente, vigente o vencido`() = apiTest(devMailbox = true) {
        val ana = register()
        post("/v1/account/email", """{"newEmail":"ana.nueva@correo.com","password":"${ana.password}"}""", ana.accessToken)

        val expired = post("/v1/dev/mailbox/expired-link", """{"email":"ana.nueva@correo.com","purpose":"EMAIL_CHANGE"}""").token()
        val latest = post("/v1/dev/mailbox/latest-link", """{"email":"ana.nueva@correo.com","purpose":"EMAIL_CHANGE"}""").token()

        val late = post("/v1/account/email/confirm", """{"token":"$expired"}""", ana.accessToken)
        assertEquals(HttpStatusCode.Gone, late.status)
        assertEquals("""{"code":"link_expired","email":"ana.nueva@correo.com"}""", late.bodyAsText())
        assertEquals(HttpStatusCode.OK, post("/v1/account/email/confirm", """{"token":"$latest"}""", ana.accessToken).status)
    }

    private suspend fun HttpResponse.token(): String {
        assertEquals(HttpStatusCode.OK, status, bodyAsText())
        return json().jsonObject["token"]!!.jsonPrimitive.content
    }
}
