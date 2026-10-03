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
) {
    companion object {
        fun load(config: ApplicationConfig): AppConfig {
            val root = config.config("exploracity")
            val database = root.config("database")
            val jwt = root.config("jwt")
            val city = root.config("city")
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
            )
        }

        /** «ana@x.co, Laura@x.co» → los dos correos en minúscula, sin espacios ni vacíos. */
        fun parseEmails(text: String): Set<String> =
            text.split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()

        private fun ApplicationConfig.point(name: String): GeoPoint {
            val point = config(name)
            return GeoPoint(point.property("latitude").getString().toDouble(), point.property("longitude").getString().toDouble())
        }
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
