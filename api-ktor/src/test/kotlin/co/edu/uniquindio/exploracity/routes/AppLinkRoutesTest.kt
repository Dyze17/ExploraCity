package co.edu.uniquindio.exploracity.routes

import co.edu.uniquindio.exploracity.config.AndroidAppSettings
import co.edu.uniquindio.exploracity.plugins.configureRouting
import co.edu.uniquindio.exploracity.plugins.configureSerialization
import co.edu.uniquindio.exploracity.plugins.configureStatusPages
import co.edu.uniquindio.exploracity.support.randomSecret
import co.edu.uniquindio.exploracity.support.testConfig
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** C1 · Los enlaces https del correo: assetlinks.json para los App Links y la página «Abrir en ExploraCity». */
class AppLinkRoutesTest {

    private fun ApplicationTestBuilder.start(android: AndroidAppSettings = AndroidAppSettings()) {
        environment { config = MapApplicationConfig() }
        application {
            configureSerialization()
            configureStatusPages()
            configureRouting(testConfig().copy(android = android))
        }
    }

    /** Una huella SHA-256 inventada en cada ejecución. */
    private fun randomFingerprint(): String = (1..32).joinToString(":") { "%02X".format(Random.nextInt(256)) }

    @Test
    fun `assetlinks declara la app y las huellas de su firma`() = testApplication {
        val fingerprints = listOf(randomFingerprint(), randomFingerprint())
        start(AndroidAppSettings(certFingerprints = fingerprints))

        val response = client.get("/.well-known/assetlinks.json")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.contentType()!!.match(ContentType.Application.Json))
        val expected = JsonArray(
            listOf(
                JsonObject(
                    mapOf(
                        "relation" to JsonArray(listOf(JsonPrimitive("delegate_permission/common.handle_all_urls"))),
                        "target" to JsonObject(
                            mapOf(
                                "namespace" to JsonPrimitive("android_app"),
                                "package_name" to JsonPrimitive("co.edu.uniquindio.exploracity"),
                                "sha256_cert_fingerprints" to JsonArray(fingerprints.map(::JsonPrimitive)),
                            ),
                        ),
                    ),
                ),
            ),
        )
        assertEquals(expected, Json.parseToJsonElement(response.bodyAsText()))
    }

    @Test
    fun `sin huellas no hay assetlinks y los enlaces quedan con la página`() = testApplication {
        start()

        assertEquals(HttpStatusCode.NotFound, client.get("/.well-known/assetlinks.json").status)
        assertEquals(HttpStatusCode.OK, client.get("/enlace/restablecer?token=${randomSecret()}").status)
    }

    @Test
    fun `el enlace de recuperación abierto en el navegador ofrece abrir la app con su token`() = testApplication {
        start()
        val token = randomSecret()

        val response = client.get("/enlace/restablecer?token=$token")
        val page = response.bodyAsText()

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.contentType()!!.match(ContentType.Text.Html))
        assertTrue("Crea tu contraseña nueva" in page)
        assertTrue("intent://enlace/restablecer?token=$token#Intent;scheme=exploracity;package=co.edu.uniquindio.exploracity;end" in page)
        assertPrivate(response)
    }

    @Test
    fun `el enlace del correo nuevo abierto en el navegador ofrece confirmarlo en la app`() = testApplication {
        start()
        val token = randomSecret()

        val page = client.get("/enlace/confirmar-correo?token=$token").bodyAsText()

        assertTrue("Confirma tu correo nuevo" in page)
        assertTrue("intent://enlace/confirmar-correo?token=$token#Intent;scheme=exploracity;" in page)
    }

    @Test
    fun `un token con caracteres raros se codifica y no se mete en la página`() = testApplication {
        start()

        val page = client.get("/enlace/restablecer?token=%22%3E%3Cscript%3E").bodyAsText()

        assertFalse("<script>" in page)
        assertTrue("token=%22%3E%3Cscript%3E#Intent" in page)
    }

    @Test
    fun `sin token la página dice que el enlace está incompleto`() = testApplication {
        start()

        val response = client.get("/enlace/restablecer?token=")

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue("Este enlace está incompleto" in response.bodyAsText())
        assertFalse("intent://" in response.bodyAsText())
        assertEquals(HttpStatusCode.NotFound, client.get("/enlace/otra-cosa?token=${randomSecret()}").status)
    }

    /** El token va en la dirección: ni en cachés, ni en el Referer de otra página, ni en un buscador. */
    private fun assertPrivate(response: HttpResponse) {
        assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
        assertEquals("no-referrer", response.headers["Referrer-Policy"])
        assertEquals("noindex", response.headers["X-Robots-Tag"])
    }
}
