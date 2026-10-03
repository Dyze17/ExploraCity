package co.edu.uniquindio.exploracity.support

import co.edu.uniquindio.exploracity.config.AppConfig
import co.edu.uniquindio.exploracity.config.CitySettings
import co.edu.uniquindio.exploracity.config.DatabaseSettings
import co.edu.uniquindio.exploracity.config.JwtSettings
import co.edu.uniquindio.exploracity.model.GeoPoint
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.io.File
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

/** Configuración de prueba: la clave del JWT se genera en cada ejecución, nunca se escribe en el repositorio. */
fun testConfig(moderators: Set<String> = emptySet()) = AppConfig(
    database = DatabaseSettings(url = "", user = "", password = ""),
    jwt = JwtSettings(
        secret = randomSecret(),
        issuer = "exploracity-api",
        audience = "exploracity-app",
        accessTtl = Duration.ofMinutes(15),
        refreshTtl = Duration.ofDays(30),
    ),
    moderators = moderators,
    city = CitySettings(
        name = "Armenia",
        center = GeoPoint(4.5339, -75.6811),
        southwest = GeoPoint(4.47, -75.76),
        northeast = GeoPoint(4.6, -75.62),
        timeZone = ZoneId.of("America/Bogota"),
    ),
)

fun randomSecret(): String = UUID.randomUUID().toString() + UUID.randomUUID().toString()

/** Reloj que las pruebas mueven a mano. */
class MutableClock(var now: Instant) : Clock() {
    override fun instant(): Instant = now

    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId): Clock = this

    fun advance(duration: Duration) {
        now += duration
    }
}

/**
 * Ejemplo del contrato (docs/api/ejemplos), el mismo que leen las pruebas de la app: si la respuesta cambia, una de
 * las dos partes se entera.
 */
fun contractExample(name: String): JsonElement {
    val dir = System.getProperty("exploracity.contract") ?: error("Falta la propiedad exploracity.contract")
    return Json.parseToJsonElement(File(dir, name).readText())
}
