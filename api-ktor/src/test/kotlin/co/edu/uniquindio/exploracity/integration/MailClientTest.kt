package co.edu.uniquindio.exploracity.integration

import co.edu.uniquindio.exploracity.model.LinkPurpose
import co.edu.uniquindio.exploracity.service.AccountMail
import co.edu.uniquindio.exploracity.support.randomSecret
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** C1 · El correo: SendGrid de verdad, el buzón de desarrollo y los textos de la cuenta. */
class MailClientTest {

    private val apiKey = randomSecret()
    private val message = MailMessage("ana@correo.com", "Asunto", "Texto plano", "<p>HTML</p>")

    private fun sendGrid(handler: MockRequestHandler): SendGridMailClient {
        val http = HttpClient(MockEngine(handler)) {
            install(ContentNegotiation) { json(Json { explicitNulls = false }) }
        }
        return SendGridMailClient(http, apiKey, "hola@exploracity.co", "ExploraCity")
    }

    @Test
    fun `SendGrid recibe el correo con texto y HTML, sin seguimiento de clics`() = runBlocking {
        var body = ""
        var auth: String? = null
        var url = ""
        val client = sendGrid { request ->
            url = request.url.toString()
            auth = request.headers[HttpHeaders.Authorization]
            body = String(request.body.toByteArray())
            respond("", HttpStatusCode.Accepted)
        }

        client.send(message)

        assertEquals("https://api.sendgrid.com/v3/mail/send", url)
        assertEquals("Bearer $apiKey", auth)
        val json = Json.parseToJsonElement(body).jsonObject
        assertEquals("ana@correo.com", json["personalizations"]!!.jsonArray[0].jsonObject["to"]!!.jsonArray[0].jsonObject["email"]!!.jsonPrimitive.content)
        assertEquals("hola@exploracity.co", json["from"]!!.jsonObject["email"]!!.jsonPrimitive.content)
        assertEquals("ExploraCity", json["from"]!!.jsonObject["name"]!!.jsonPrimitive.content)
        assertEquals("Asunto", json["subject"]!!.jsonPrimitive.content)
        assertEquals(listOf("text/plain", "text/html"), json["content"]!!.jsonArray.map { it.jsonObject["type"]!!.jsonPrimitive.content })
        assertEquals("false", json["tracking_settings"]!!.jsonObject["click_tracking"]!!.jsonObject["enable"]!!.jsonPrimitive.content)
    }

    @Test
    fun `si SendGrid responde con error o no responde, el correo no salió`() = runBlocking<Unit> {
        assertFailsWith<MailDeliveryException> { sendGrid { respond("", HttpStatusCode.Unauthorized) }.send(message) }
        assertFailsWith<MailDeliveryException> { sendGrid { throw IOException("sin red") }.send(message) }
    }

    @Test
    fun `el buzón de desarrollo guarda los últimos correos de cada dirección`() = runBlocking {
        val mailbox = DevMailbox(capacity = 2)

        mailbox.send(message.copy(subject = "Primero"))
        mailbox.send(message.copy(to = "laura@correo.com", subject = "Para Laura"))
        mailbox.send(message.copy(subject = "Segundo"))

        assertEquals(listOf("Segundo"), mailbox.inbox(" Ana@Correo.com ").map { it.subject })
        assertEquals(listOf("Para Laura"), mailbox.inbox("laura@correo.com").map { it.subject })
    }

    @Test
    fun `los correos de la cuenta llevan el enlace con su token y escapan el nombre en el HTML`() = runBlocking {
        val mailbox = DevMailbox()
        val mail = AccountMail(mailbox, "exploracity://enlace")
        val token = "abc_DEF-123"

        mail.passwordReset("ana@correo.com", "<Ana>", token)
        mail.emailChange("nueva@correo.com", "Ana", "otro")

        val reset = mailbox.inbox("ana@correo.com").single()
        assertTrue("exploracity://enlace/restablecer?token=$token" in reset.text)
        assertTrue("30 minutos" in reset.text)
        assertTrue("&lt;Ana&gt;" in reset.html && "<Ana>" !in reset.html)
        assertEquals(token, mail.tokenIn(reset, LinkPurpose.PASSWORD_RESET))
        assertNull(mail.tokenIn(reset, LinkPurpose.EMAIL_CHANGE))
        val change = mailbox.inbox("nueva@correo.com").single()
        assertEquals("exploracity://enlace/confirmar-correo?token=otro", mail.link(LinkPurpose.EMAIL_CHANGE, "otro"))
        assertEquals("otro", mail.tokenIn(change, LinkPurpose.EMAIL_CHANGE))
    }
}
