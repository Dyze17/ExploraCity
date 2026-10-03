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
class Integrations(val mail: MailClient, val media: MediaStore, private val http: HttpClient? = null) : Closeable {

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
            return Integrations(mail, media, http)
        }
    }
}
