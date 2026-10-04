package co.edu.uniquindio.exploracity.support

import co.edu.uniquindio.exploracity.config.AppConfig
import co.edu.uniquindio.exploracity.config.CitySettings
import co.edu.uniquindio.exploracity.config.DatabaseSettings
import co.edu.uniquindio.exploracity.config.JwtSettings
import co.edu.uniquindio.exploracity.config.MailSettings
import co.edu.uniquindio.exploracity.model.GeoPoint
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals

/** Configuración de prueba: la clave del JWT se genera en cada ejecución, nunca se escribe en el repositorio. */
fun testConfig(moderators: Set<String> = emptySet(), devMailbox: Boolean = false) = AppConfig(
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
    mail = MailSettings(devMailbox = devMailbox),
)

fun randomSecret(): String = UUID.randomUUID().toString() + UUID.randomUUID().toString()

/** Una contraseña que cumple las reglas (letra y número), generada en cada ejecución: ninguna queda escrita. */
fun newPassword(): String = "a" + randomSecret().take(14) + "7"

/**
 * Compara con el ejemplo del contrato. Los campos de [volatile] (tokens, ids que genera la base de datos) solo tienen
 * que estar, con el mismo tipo: su valor cambia en cada ejecución.
 */
fun assertMatchesContract(example: String, actual: JsonElement, volatile: Set<String> = emptySet()) {
    val expected = contractExample(example)
    assertEquals(expected, normalize(actual, expected, volatile), "La respuesta no coincide con docs/api/ejemplos/$example")
}

private fun normalize(actual: JsonElement, expected: JsonElement, volatile: Set<String>): JsonElement = when {
    actual is JsonObject && expected is JsonObject -> JsonObject(
        actual.mapValues { (key, value) ->
            val model = expected[key]
            when {
                model == null -> value
                key in volatile && value is JsonPrimitive && model is JsonPrimitive && value.isString == model.isString -> model
                else -> normalize(value, model, volatile)
            }
        },
    )
    actual is JsonArray && expected is JsonArray && actual.size == expected.size ->
        JsonArray(actual.zip(expected) { value, model -> normalize(value, model, volatile) })
    else -> actual
}

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
