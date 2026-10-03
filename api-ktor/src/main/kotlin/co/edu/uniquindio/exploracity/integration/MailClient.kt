package co.edu.uniquindio.exploracity.integration

import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentLinkedDeque

/** Un correo listo para enviar: en español, con texto plano y HTML simple. */
data class MailMessage(val to: String, val subject: String, val text: String, val html: String)

/** El correo no salió: el servicio falló o no respondió. */
class MailDeliveryException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** C1 · Envío de correos: SendGrid con su clave; sin ella, el buzón de desarrollo ([DevMailbox]). */
interface MailClient {
    /** Lanza [MailDeliveryException] si el correo no sale. */
    suspend fun send(message: MailMessage)
}

/** SendGrid (API v3). Sin seguimiento de clics: los enlaces llevan un token y no deben pasar por otro servidor. */
class SendGridMailClient(
    private val http: HttpClient,
    private val apiKey: String,
    private val from: String,
    private val fromName: String,
    private val endpoint: String = ENDPOINT,
) : MailClient {

    override suspend fun send(message: MailMessage) {
        val body = SendGridRequest(
            personalizations = listOf(SendGridPersonalization(listOf(SendGridAddress(message.to)))),
            from = SendGridAddress(from, fromName),
            subject = message.subject,
            content = listOf(SendGridContent("text/plain", message.text), SendGridContent("text/html", message.html)),
            trackingSettings = SendGridTracking(SendGridClickTracking(enable = false, enableText = false)),
        )
        val response = try {
            http.post(endpoint) {
                bearerAuth(apiKey)
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw MailDeliveryException("SendGrid no respondió", e)
        }
        if (!response.status.isSuccess()) throw MailDeliveryException("SendGrid respondió ${response.status.value}")
    }

    private companion object {
        const val ENDPOINT = "https://api.sendgrid.com/v3/mail/send"
    }
}

/**
 * C1 · Sin SendGrid los correos no salen: quedan aquí (los últimos [capacity]) y se anotan en el registro. Solo se leen
 * en /v1/dev con DEV_MAILBOX=true, para los botones de prueba de 6.a y «Cambiar correo».
 */
class DevMailbox(private val capacity: Int = 200) : MailClient {
    private val log = LoggerFactory.getLogger(DevMailbox::class.java)
    private val messages = ConcurrentLinkedDeque<MailMessage>()

    override suspend fun send(message: MailMessage) {
        messages.addFirst(message)
        while (messages.size > capacity) messages.pollLast()
        log.info("Correo de desarrollo (no sale) para {}: «{}»", message.to, message.subject)
    }

    /** Los correos que llegaron a [email], del más reciente al más antiguo. */
    fun inbox(email: String): List<MailMessage> = messages.filter { it.to.equals(email.trim(), ignoreCase = true) }
}

@Serializable
private data class SendGridRequest(
    val personalizations: List<SendGridPersonalization>,
    val from: SendGridAddress,
    val subject: String,
    val content: List<SendGridContent>,
    @SerialName("tracking_settings") val trackingSettings: SendGridTracking,
)

@Serializable
private data class SendGridPersonalization(val to: List<SendGridAddress>)

@Serializable
private data class SendGridAddress(val email: String, val name: String? = null)

@Serializable
private data class SendGridContent(val type: String, val value: String)

@Serializable
private data class SendGridTracking(@SerialName("click_tracking") val clickTracking: SendGridClickTracking)

@Serializable
private data class SendGridClickTracking(val enable: Boolean, @SerialName("enable_text") val enableText: Boolean)
