package co.edu.uniquindio.exploracity.config

import co.edu.uniquindio.exploracity.model.GeoPoint
import co.edu.uniquindio.exploracity.support.randomSecret
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.config.mergeWith
import java.time.Duration
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AppConfigTest {

    /** application.conf con lo que en producción llega por variables de entorno. */
    private fun load(vararg values: Pair<String, String>): AppConfig =
        AppConfig.load(ApplicationConfig("application.conf").mergeWith(MapApplicationConfig(*values)))

    @Test
    fun `la configuración por defecto atiende Armenia con sesiones de 15 minutos y 30 días`() {
        val config = load("exploracity.jwt.secret" to randomSecret(), "exploracity.moderators" to "")

        assertEquals("Armenia", config.city.name)
        assertEquals(GeoPoint(4.5339, -75.6811), config.city.center)
        assertEquals(ZoneId.of("America/Bogota"), config.city.timeZone)
        assertEquals(Duration.ofMinutes(15), config.jwt.accessTtl)
        assertEquals(Duration.ofDays(30), config.jwt.refreshTtl)
        assertTrue(config.moderators.isEmpty())
    }

    @Test
    fun `sin una clave del JWT larga la API no arranca`() {
        val error = assertFailsWith<IllegalStateException> { load("exploracity.jwt.secret" to "corta") }
        assertTrue("JWT_SECRET" in error.message.orEmpty())
    }

    @Test
    fun `la lista de moderadores ignora mayúsculas, espacios y vacíos`() {
        assertEquals(setOf("laura@ejemplo.co", "ana@ejemplo.co"), AppConfig.parseEmails(" Laura@Ejemplo.co, ,ana@ejemplo.co,"))
    }
}
