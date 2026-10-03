package co.edu.uniquindio.exploracity.config

import co.edu.uniquindio.exploracity.model.GeoPoint
import io.ktor.server.config.ApplicationConfig
import java.time.Duration
import java.time.ZoneId

/** Configuración de la API: application.conf con lo secreto desde variables de entorno. */
data class AppConfig(
    val database: DatabaseSettings,
    val jwt: JwtSettings,
    /** Correos (en minúscula) de las cuentas con rol de moderador. */
    val moderators: Set<String>,
    val city: CitySettings,
    val mail: MailSettings = MailSettings(),
    val media: MediaSettings = MediaSettings(),
) {
    companion object {
        fun load(config: ApplicationConfig): AppConfig {
            val root = config.config("exploracity")
            val database = root.config("database")
            val jwt = root.config("jwt")
            val city = root.config("city")
            val mail = root.config("mail")
            val media = root.config("media")
            val cloudinary = media.config("cloudinary")
            return AppConfig(
                database = DatabaseSettings(
                    url = database.property("url").getString(),
                    user = database.property("user").getString(),
                    password = database.property("password").getString(),
                    maxPoolSize = database.property("maxPoolSize").getString().toInt(),
                ),
                jwt = JwtSettings(
                    secret = jwt.property("secret").getString(),
                    issuer = jwt.property("issuer").getString(),
                    audience = jwt.property("audience").getString(),
                    accessTtl = Duration.ofMinutes(jwt.property("accessMinutes").getString().toLong()),
                    refreshTtl = Duration.ofDays(jwt.property("refreshDays").getString().toLong()),
                ),
                moderators = parseEmails(root.property("moderators").getString()),
                city = CitySettings(
                    name = city.property("name").getString(),
                    center = city.point("center"),
                    southwest = city.point("southwest"),
                    northeast = city.point("northeast"),
                    timeZone = ZoneId.of(city.property("timeZone").getString()),
                ),
                mail = MailSettings(
                    sendGridApiKey = mail.text("sendGridApiKey"),
                    from = mail.text("from"),
                    fromName = mail.text("fromName").ifEmpty { MailSettings.DEFAULT_FROM_NAME },
                    devMailbox = mail.text("devMailbox").equals("true", ignoreCase = true),
                    linkBaseUrl = mail.text("linkBaseUrl").ifEmpty { MailSettings.DEFAULT_LINK_BASE_URL }.trimEnd('/'),
                ),
                media = MediaSettings(
                    cloudinary = CloudinarySettings.of(
                        cloudName = cloudinary.text("cloudName"),
                        apiKey = cloudinary.text("apiKey"),
                        apiSecret = cloudinary.text("apiSecret"),
                    ),
                    localDir = media.text("localDir").ifEmpty { MediaSettings.DEFAULT_DIR },
                    publicBaseUrl = media.text("publicBaseUrl").ifEmpty { MediaSettings.DEFAULT_BASE_URL }.trimEnd('/'),
                ),
            )
        }

        /** «ana@x.co, Laura@x.co» → los dos correos en minúscula, sin espacios ni vacíos. */
        fun parseEmails(text: String): Set<String> =
            text.split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()

        private fun ApplicationConfig.point(name: String): GeoPoint {
            val point = config(name)
            return GeoPoint(point.property("latitude").getString().toDouble(), point.property("longitude").getString().toDouble())
        }

        private fun ApplicationConfig.text(name: String): String = property(name).getString().trim()
    }
}

data class DatabaseSettings(val url: String, val user: String, val password: String, val maxPoolSize: Int = 10)

data class JwtSettings(
    val secret: String,
    val issuer: String,
    val audience: String,
    val accessTtl: Duration,
    val refreshTtl: Duration,
) {
    init {
        check(secret.length >= MIN_SECRET_LENGTH) {
            "Falta JWT_SECRET o es muy corto: necesita al menos $MIN_SECRET_LENGTH caracteres aleatorios."
        }
    }

    companion object {
        /** HMAC-SHA256 pide una clave de al menos 256 bits. */
        const val MIN_SECRET_LENGTH = 32
    }
}

/** La ciudad que atiende la app (F2) y la zona horaria con la que se cuentan sus días. */
data class CitySettings(
    val name: String,
    val center: GeoPoint,
    val southwest: GeoPoint,
    val northeast: GeoPoint,
    val timeZone: ZoneId,
)

/**
 * C1 · Correo. Con [sendGridApiKey] se envía de verdad desde [from]; sin ella, los correos quedan en el buzón de
 * desarrollo, que solo se consulta en /v1/dev con [devMailbox]. [linkBaseUrl] es donde abren los enlaces del correo.
 */
data class MailSettings(
    val sendGridApiKey: String = "",
    val from: String = "",
    val fromName: String = DEFAULT_FROM_NAME,
    val devMailbox: Boolean = false,
    val linkBaseUrl: String = DEFAULT_LINK_BASE_URL,
) {
    val usesSendGrid: Boolean get() = sendGridApiKey.isNotEmpty()

    init {
        check(!usesSendGrid || from.isNotEmpty()) { "Con SENDGRID_API_KEY también hace falta MAIL_FROM, el remitente verificado." }
    }

    companion object {
        const val DEFAULT_FROM_NAME = "ExploraCity"

        /** Hasta tener el dominio verificado de los App Links, los enlaces abren la app por su esquema propio. */
        const val DEFAULT_LINK_BASE_URL = "exploracity://enlace"
    }
}

/**
 * C1 · Fotos (ADR-07). Con [cloudinary] van a Cloudinary; sin él, a la carpeta [localDir] (fuera de git), que la API
 * sirve en /media con direcciones que empiezan por [publicBaseUrl].
 */
data class MediaSettings(
    val cloudinary: CloudinarySettings? = null,
    val localDir: String = DEFAULT_DIR,
    val publicBaseUrl: String = DEFAULT_BASE_URL,
) {
    companion object {
        const val DEFAULT_DIR = "media"
        const val DEFAULT_BASE_URL = "http://localhost:8080"
    }
}

data class CloudinarySettings(val cloudName: String, val apiKey: String, val apiSecret: String) {
    companion object {
        /** null si falta todo; si falta solo una parte, la API no arranca, para no subir fotos a medias. */
        fun of(cloudName: String, apiKey: String, apiSecret: String): CloudinarySettings? {
            val parts = listOf(cloudName, apiKey, apiSecret)
            if (parts.all { it.isEmpty() }) return null
            check(parts.none { it.isEmpty() }) {
                "Cloudinary necesita CLOUDINARY_CLOUD_NAME, CLOUDINARY_API_KEY y CLOUDINARY_API_SECRET juntas."
            }
            return CloudinarySettings(cloudName, apiKey, apiSecret)
        }
    }
}
