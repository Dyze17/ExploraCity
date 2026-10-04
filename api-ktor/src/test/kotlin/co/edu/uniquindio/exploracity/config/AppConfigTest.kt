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
import kotlin.test.assertFalse
import kotlin.test.assertNull
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
    fun `sin claves de integraciones, el correo queda en el buzón y las fotos en la carpeta local`() {
        val config = load("exploracity.jwt.secret" to randomSecret())

        assertFalse(config.mail.usesSendGrid)
        assertFalse(config.mail.devMailbox)
        assertEquals("exploracity://enlace", config.mail.linkBaseUrl)
        assertEquals("ExploraCity", config.mail.fromName)
        assertNull(config.media.cloudinary)
        assertEquals("media", config.media.localDir)
        assertEquals("http://localhost:8080", config.media.publicBaseUrl)
    }

    @Test
    fun `las variables vacías de api-ktor env toman el valor por omisión`() {
        val config = load(
            "exploracity.jwt.secret" to randomSecret(),
            "exploracity.mail.fromName" to "",
            "exploracity.mail.linkBaseUrl" to " ",
            "exploracity.mail.devMailbox" to "",
            "exploracity.media.localDir" to "",
            "exploracity.media.publicBaseUrl" to "",
        )

        assertEquals("ExploraCity", config.mail.fromName)
        assertEquals("exploracity://enlace", config.mail.linkBaseUrl)
        assertFalse(config.mail.devMailbox)
        assertEquals("media", config.media.localDir)
        assertEquals("http://localhost:8080", config.media.publicBaseUrl)
    }

    @Test
    fun `con sus claves se activan SendGrid, Cloudinary y el buzón de desarrollo`() {
        val apiKey = randomSecret()
        val apiSecret = randomSecret()
        val config = load(
            "exploracity.jwt.secret" to randomSecret(),
            "exploracity.mail.sendGridApiKey" to apiKey,
            "exploracity.mail.from" to "hola@exploracity.co",
            "exploracity.mail.devMailbox" to "TRUE",
            "exploracity.mail.linkBaseUrl" to "https://exploracity.co/enlace/",
            "exploracity.media.cloudinary.cloudName" to "exploracity",
            "exploracity.media.cloudinary.apiKey" to "llave-publica",
            "exploracity.media.cloudinary.apiSecret" to apiSecret,
            "exploracity.media.publicBaseUrl" to "http://192.168.1.20:8080/",
        )

        assertTrue(config.mail.usesSendGrid)
        assertTrue(config.mail.devMailbox)
        assertEquals("https://exploracity.co/enlace", config.mail.linkBaseUrl)
        assertEquals(CloudinarySettings("exploracity", "llave-publica", apiSecret), config.media.cloudinary)
        assertEquals("http://192.168.1.20:8080", config.media.publicBaseUrl)
    }

    @Test
    fun `la sugerencia de categoría usa OpenRouter solo con su clave`() {
        val without = load("exploracity.jwt.secret" to randomSecret())
        val apiKey = randomSecret()
        val with = load("exploracity.jwt.secret" to randomSecret(), "exploracity.ai.openRouterApiKey" to apiKey, "exploracity.ai.model" to "deepseek/deepseek-r1")

        assertFalse(without.ai.usesOpenRouter)
        assertEquals("deepseek/deepseek-chat", without.ai.model)
        assertTrue(with.ai.usesOpenRouter)
        assertEquals("deepseek/deepseek-r1", with.ai.model)
    }

    @Test
    fun `una integración a medias no arranca`() {
        val sendGrid = assertFailsWith<IllegalStateException> {
            load("exploracity.jwt.secret" to randomSecret(), "exploracity.mail.sendGridApiKey" to randomSecret())
        }
        assertTrue("MAIL_FROM" in sendGrid.message.orEmpty())
        val cloudinary = assertFailsWith<IllegalStateException> {
            load("exploracity.jwt.secret" to randomSecret(), "exploracity.media.cloudinary.cloudName" to "exploracity")
        }
        assertTrue("CLOUDINARY_API_SECRET" in cloudinary.message.orEmpty())
    }

    @Test
    fun `la lista de moderadores ignora mayúsculas, espacios y vacíos`() {
        assertEquals(setOf("laura@ejemplo.co", "ana@ejemplo.co"), AppConfig.parseEmails(" Laura@Ejemplo.co, ,ana@ejemplo.co,"))
    }
}
