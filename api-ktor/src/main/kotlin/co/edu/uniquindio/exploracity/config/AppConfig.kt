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
    val ai: AiSettings = AiSettings(),
    val android: AndroidAppSettings = AndroidAppSettings(),
    /** En Cloud Run (APP_ENV=production): lo que en desarrollo tiene una versión local aquí es obligatorio. */
    val production: Boolean = false,
) {
    init {
        if (production) {
            check(mail.usesSendGrid) { "En producción hace falta SENDGRID_API_KEY: sin ella los enlaces del correo no le llegan a nadie." }
            check(!mail.devMailbox) { "DEV_MAILBOX no va en producción: dejaría leer los enlaces de cualquier cuenta." }
            check(media.cloudinary != null) {
                "En producción hacen falta CLOUDINARY_CLOUD_NAME, CLOUDINARY_API_KEY y CLOUDINARY_API_SECRET: Cloud Run borra su disco al apagarse."
            }
            check(mail.linkBaseUrl.startsWith("https://")) {
                "En producción APP_LINK_BASE_URL tiene que empezar por https://: Gmail no deja abrir los enlaces exploracity://."
            }
        }
    }

    companion object {
        fun load(config: ApplicationConfig): AppConfig {
            val root = config.config("exploracity")
            val database = root.config("database")
            val jwt = root.config("jwt")
            val city = root.config("city")
            val mail = root.config("mail")
            val media = root.config("media")
            val cloudinary = media.config("cloudinary")
            val ai = root.config("ai")
            val android = root.config("android")
            return AppConfig(
                database = DatabaseSettings(
                    url = database.property("url").getString(),
                    user = database.property("user").getString(),
                    password = database.property("password").getString(),
                    maxPoolSize = database.property("maxPoolSize").getString().toInt(),
                    connectionTimeout = Duration.ofSeconds(database.property("connectionTimeoutSeconds").getString().toLong()),
                    statementTimeout = Duration.ofSeconds(database.property("statementTimeoutSeconds").getString().toLong()),
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
                ai = AiSettings(
                    openRouterApiKey = ai.text("openRouterApiKey"),
                    model = ai.text("model").ifEmpty { AiSettings.DEFAULT_MODEL },
                ),
                android = AndroidAppSettings(
                    packageName = android.text("packageName").ifEmpty { AndroidAppSettings.DEFAULT_PACKAGE },
                    certFingerprints = AndroidAppSettings.parseFingerprints(android.text("certFingerprints")),
                ),
                production = when (val environment = root.text("environment").lowercase()) {
                    "", "development" -> false
                    "production" -> true
                    else -> error("APP_ENV no reconoce «$environment»: va vacío, development o production.")
                },
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

/**
 * La base de datos (ADR-04). [connectionTimeout] es lo más que una petición espera una conexión del pool y
 * [statementTimeout] lo más que dura cada consulta: así, con la base caída o lenta, la API responde con un error antes
 * de que la app se rinda, en vez de terminar el trabajo cuando ya nadie espera la respuesta.
 */
data class DatabaseSettings(
    val url: String,
    val user: String,
    val password: String,
    val maxPoolSize: Int = 10,
    val connectionTimeout: Duration = DEFAULT_TIMEOUT,
    val statementTimeout: Duration = DEFAULT_TIMEOUT,
) {
    init {
        // Hikari y PostgreSQL toman 0 como «sin límite»: justo lo que se quiere evitar.
        check(connectionTimeout >= MIN_TIMEOUT) { "DATABASE_CONNECTION_TIMEOUT_SECONDS debe ser de al menos 1 segundo." }
        check(statementTimeout >= MIN_TIMEOUT) { "DATABASE_STATEMENT_TIMEOUT_SECONDS debe ser de al menos 1 segundo." }
    }

    companion object {
        val DEFAULT_TIMEOUT: Duration = Duration.ofSeconds(5)
        private val MIN_TIMEOUT: Duration = Duration.ofSeconds(1)
    }
}

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

/**
 * C1 · Sugerencia de categoría (16, ADR-11). Con [openRouterApiKey] la hace [model] por OpenRouter; sin ella, una versión
 * de desarrollo por palabras clave.
 */
data class AiSettings(val openRouterApiKey: String = "", val model: String = DEFAULT_MODEL) {
    val usesOpenRouter: Boolean get() = openRouterApiKey.isNotEmpty()

    companion object {
        /**
         * ADR-11: DeepSeek por OpenRouter. V4.1 Flash en vez de V3 (deepseek-chat): con el razonamiento apagado responde
         * la categoría igual de bien en menos de 1 s, cuando V3 tardaba de 1 a 2,5 s del límite de 4 s.
         */
        const val DEFAULT_MODEL = "deepseek/deepseek-v4.1-flash"
    }
}

/**
 * C1 · App Links: la app [packageName], firmada con alguna de [certFingerprints] (SHA-256), abre los enlaces https del
 * correo sin pasar por el navegador. Sin huellas no se publica /.well-known/assetlinks.json, y esos enlaces abren la
 * página con el botón «Abrir en ExploraCity» (routes/AppLinkRoutes.kt).
 */
data class AndroidAppSettings(
    val packageName: String = DEFAULT_PACKAGE,
    val certFingerprints: List<String> = emptyList(),
) {
    companion object {
        const val DEFAULT_PACKAGE = "co.edu.uniquindio.exploracity"

        private val FINGERPRINT = Regex("([0-9A-F]{2}:){31}[0-9A-F]{2}")

        /** «14:d4:…, AA:BB:…» → las huellas en mayúscula, como las pide assetlinks.json. Una mal copiada no arranca. */
        fun parseFingerprints(text: String): List<String> =
            text.split(',').map { it.trim().uppercase() }.filter { it.isNotEmpty() }.onEach { fingerprint ->
                check(FINGERPRINT.matches(fingerprint)) {
                    "ANDROID_CERT_SHA256 tiene una huella que no es SHA-256 (32 pares hexadecimales separados por «:»)."
                }
            }
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
