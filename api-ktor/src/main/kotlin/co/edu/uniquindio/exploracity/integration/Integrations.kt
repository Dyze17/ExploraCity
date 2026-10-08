package co.edu.uniquindio.exploracity.integration

import co.edu.uniquindio.exploracity.config.AppConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.io.File
import java.time.Clock

/**
 * C1 · Las integraciones de la API: la real cuando su clave está en la configuración y la de desarrollo cuando no. Las
 * pruebas arman las suyas.
 */
class Integrations(
    val mail: MailClient,
    val media: MediaStore,
    val classifier: CategoryClassifier = KeywordClassifier(),
    private val http: HttpClient? = null,
    /** ADR-15 · null sin GOOGLE_WEB_CLIENT_ID: no se puede entrar con Google. */
    val google: GoogleTokenVerifier? = null,
) : Closeable {

    override fun close() {
        http?.close()
    }

    companion object {
        private val log = LoggerFactory.getLogger(Integrations::class.java)

        fun create(config: AppConfig, clock: Clock): Integrations {
            val http = HttpClient(CIO) {
                install(ContentNegotiation) {
                    json(
                        Json {
                            ignoreUnknownKeys = true
                            explicitNulls = false
                        },
                    )
                }
                install(HttpTimeout) {
                    connectTimeoutMillis = 10_000
                    requestTimeoutMillis = 30_000
                }
            }
            val mail = if (config.mail.usesSendGrid) {
                log.info("Correo: SendGrid, desde {}.", config.mail.from)
                SendGridMailClient(http, config.mail.sendGridApiKey, config.mail.from, config.mail.fromName)
            } else {
                log.warn("Correo: sin SENDGRID_API_KEY los correos no salen; quedan en el buzón de desarrollo.")
                DevMailbox()
            }
            val media = config.media.cloudinary?.let { cloudinary ->
                log.info("Fotos: Cloudinary ({}).", cloudinary.cloudName)
                CloudinaryMediaStore(http, cloudinary, clock)
            } ?: run {
                val directory = File(config.media.localDir).absoluteFile
                log.warn("Fotos: sin Cloudinary van a la carpeta {}.", directory)
                LocalMediaStore(directory, config.media.publicBaseUrl)
            }
            val classifier = if (config.ai.usesOpenRouter) {
                log.info("Sugerencia de categoría: OpenRouter ({}).", config.ai.model)
                OpenRouterClassifier(http, config.ai.openRouterApiKey, config.ai.model)
            } else {
                log.warn("Sugerencia de categoría: sin OPENROUTER_API_KEY sale de palabras clave.")
                KeywordClassifier()
            }
            val google = if (config.google.enabled) {
                log.info("Entrar con Google: activado.")
                JwksGoogleTokenVerifier.google(config.google.webClientId, clock)
            } else {
                log.warn("Entrar con Google: sin GOOGLE_WEB_CLIENT_ID no está disponible.")
                null
            }
            return Integrations(mail, media, classifier, http, google)
        }
    }
}
