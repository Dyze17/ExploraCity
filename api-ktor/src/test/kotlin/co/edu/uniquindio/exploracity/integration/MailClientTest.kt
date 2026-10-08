package co.edu.uniquindio.exploracity.integration

import co.edu.uniquindio.exploracity.model.LinkPurpose
import co.edu.uniquindio.exploracity.service.AccountMail
import co.edu.uniquindio.exploracity.service.MailContent
import co.edu.uniquindio.exploracity.service.MailLayout
import co.edu.uniquindio.exploracity.service.MailLink
import co.edu.uniquindio.exploracity.support.randomSecret
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.delay
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

    private fun sendGrid(timeoutMillis: Long = 5_000, handler: MockRequestHandler): SendGridMailClient {
        val http = HttpClient(MockEngine(handler)) {
            install(ContentNegotiation) { json(Json { explicitNulls = false }) }
            install(HttpTimeout)
        }
        return SendGridMailClient(http, apiKey, "hola@exploracity.co", "ExploraCity", timeoutMillis = timeoutMillis)
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
    fun `si SendGrid tarda, el correo no salió y la petición no lo sigue esperando`() = runBlocking<Unit> {
        assertFailsWith<MailDeliveryException> {
            sendGrid(timeoutMillis = 50) {
                delay(1_000)
                respond("", HttpStatusCode.Accepted)
            }.send(message)
        }
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

    @Test
    fun `el HTML lleva el enlace como botón, el enlace a la vista y por qué llegó el correo`() = runBlocking {
        val mailbox = DevMailbox()
        val mail = AccountMail(mailbox, "https://exploracity-api.run.app/enlace")
        val token = randomSecret()

        mail.passwordReset("ana@correo.com", "Ana", token)
        mail.emailChange("nueva@correo.com", "Ana", token)

        val reset = mailbox.inbox("ana@correo.com").single()
        val url = "https://exploracity-api.run.app/enlace/restablecer?token=$token"
        assertTrue(">Crear contraseña nueva</a>" in reset.html)
        // El botón y el enlace a la vista, para cuando el botón no se muestre.
        assertEquals(3, Regex(Regex.escape(url)).findAll(reset.html).count())
        assertTrue("se pidió una contraseña nueva para tu cuenta" in reset.html && "se pidió una contraseña nueva para tu cuenta" in reset.text)
        assertTrue("El enlace vence en 30 minutos y sirve una sola vez." in reset.html)
        assertTrue(">Confirmar correo nuevo</a>" in mailbox.inbox("nueva@correo.com").single().html)
    }

    @Test
    fun `la bienvenida no lleva botón, y ningún correo lleva imágenes ni estilos que Gmail quite`() = runBlocking {
        val mailbox = DevMailbox()
        val mail = AccountMail(mailbox, "exploracity://enlace")

        mail.welcome("ana@correo.com", "Ana")
        mail.passwordReset("ana@correo.com", "Ana", randomSecret())

        val inbox = mailbox.inbox("ana@correo.com")
        val welcome = inbox.single { it.subject.startsWith("Tu cuenta") }
        val reset = inbox.single { it.subject.startsWith("Crea una contraseña") }
        assertTrue("Tu cuenta de ExploraCity quedó lista." in welcome.html)
        assertTrue("<a " !in welcome.html)
        for (message in listOf(welcome, reset)) {
            assertTrue(message.html.startsWith("<!doctype html>") && message.html.endsWith("</html>"))
            assertTrue("<img" !in message.html && "<style" !in message.html && "<script" !in message.html)
            assertTrue("Universidad del Quindío" in message.html && "Universidad del Quindío" in message.text)
        }
    }

    @Test
    fun `el texto del correo se escapa también en el asunto, el resumen y el enlace`() {
        val html = MailLayout.html(
            MailContent(
                subject = "A & B",
                preview = "<b>",
                greeting = "Hola",
                paragraphs = emptyList(),
                link = MailLink("https://x.co/?a=1&b=\"2\"", "Abrir <ya>"),
                reason = "Porque sí",
            ),
        )

        assertTrue("<title>A &amp; B</title>" in html)
        assertTrue("&lt;b&gt;" in html && "<b>" !in html)
        assertTrue("href=\"https://x.co/?a=1&amp;b=&quot;2&quot;\"" in html)
        assertTrue(">Abrir &lt;ya&gt;</a>" in html)
    }
}
