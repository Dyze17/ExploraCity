package co.edu.uniquindio.exploracity.integration

import co.edu.uniquindio.exploracity.config.CloudinarySettings
import co.edu.uniquindio.exploracity.support.MutableClock
import co.edu.uniquindio.exploracity.support.randomSecret
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.net.URLDecoder
import java.nio.file.Files
import java.security.MessageDigest
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** C1 · El almacén de fotos: el formato por su contenido, Cloudinary firmado y la carpeta local. */
class MediaStoreTest {

    private val settings = CloudinarySettings("exploracity", "llave-publica", randomSecret())
    private val clock = MutableClock(Instant.parse("2026-10-03T15:00:00Z"))
    private val timestamp = clock.now.epochSecond.toString()

    private fun cloudinary(handler: MockRequestHandler): CloudinaryMediaStore {
        val http = HttpClient(MockEngine(handler)) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        return CloudinaryMediaStore(http, settings, clock)
    }

    private fun sha1(text: String) = MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    @Test
    fun `el formato se reconoce por los primeros bytes`() {
        assertEquals(ImageType.JPEG, ImageType.detect(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0)))
        assertEquals(ImageType.PNG, ImageType.detect(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)))
        assertEquals(ImageType.WEBP, ImageType.detect("RIFF".toByteArray() + byteArrayOf(1, 2, 3, 4) + "WEBP".toByteArray()))
        assertNull(ImageType.detect("GIF89a".toByteArray()))
        assertNull(ImageType.detect(ByteArray(0)))
    }

    @Test
    fun `Cloudinary recibe la foto con la petición firmada`() = runBlocking {
        var url = ""
        var body = ""
        val store = cloudinary { request ->
            url = request.url.toString()
            body = String(request.body.toByteArray())
            respond(
                """{"public_id":"perfil/abc","secure_url":"https://res.cloudinary.com/exploracity/image/upload/perfil/abc.jpg","bytes":3}""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        val stored = store.upload(byteArrayOf(1, 2, 3), ImageType.JPEG, "perfil")

        assertEquals(StoredMedia("https://res.cloudinary.com/exploracity/image/upload/perfil/abc.jpg", "perfil/abc"), stored)
        assertEquals("https://api.cloudinary.com/v1_1/exploracity/image/upload", url)
        val signature = sha1("folder=perfil&timestamp=$timestamp${settings.apiSecret}")
        for (field in listOf("folder" to "perfil", "timestamp" to timestamp, "api_key" to "llave-publica", "signature" to signature)) {
            assertTrue(Regex("name=\"?${field.first}\"?\\r\\n(.*\\r\\n)*\\r\\n${field.second}\\r\\n").containsMatchIn(body), field.first)
        }
        assertTrue("image/jpeg" in body)
        assertFalse(settings.apiSecret in body)
    }

    @Test
    fun `borrar en Cloudinary también va firmado`() = runBlocking {
        var url = ""
        var form = mapOf<String, String>()
        val store = cloudinary { request ->
            url = request.url.toString()
            form = String(request.body.toByteArray()).split('&').associate { pair ->
                val (name, value) = pair.split('=', limit = 2)
                name to URLDecoder.decode(value, Charsets.UTF_8)
            }
            respond("""{"result":"ok"}""", HttpStatusCode.OK)
        }

        store.delete("perfil/abc")

        assertEquals("https://api.cloudinary.com/v1_1/exploracity/image/destroy", url)
        assertEquals("perfil/abc", form["public_id"])
        assertEquals(sha1("public_id=perfil/abc&timestamp=$timestamp${settings.apiSecret}"), form["signature"])
    }

    @Test
    fun `si Cloudinary falla, el almacén lo dice`() = runBlocking<Unit> {
        val store = cloudinary { respond("""{"error":{"message":"Invalid Signature"}}""", HttpStatusCode.Unauthorized) }

        assertFailsWith<MediaStoreException> { store.upload(byteArrayOf(1), ImageType.PNG, "perfil") }
        assertFailsWith<MediaStoreException> { store.delete("perfil/abc") }
    }

    @Test
    fun `la firma ordena los parámetros y agrega el secreto`() {
        val secret = randomSecret()

        assertEquals(sha1("a=1&b=2$secret"), CloudinaryMediaStore.sign(mapOf("b" to "2", "a" to "1"), secret))
    }

    @Test
    fun `sin Cloudinary las fotos van a la carpeta local y la API las sirve en media`() = runBlocking<Unit> {
        val directory = Files.createTempDirectory("medios").toFile()
        try {
            val store = LocalMediaStore(directory, "http://192.168.1.20:8080")

            val stored = store.upload(byteArrayOf(9, 8, 7), ImageType.WEBP, "perfil")

            assertTrue(stored.url.startsWith("http://192.168.1.20:8080/media/perfil/") && stored.url.endsWith(".webp"), stored.url)
            val file = directory.resolve(stored.publicId)
            assertEquals(listOf<Byte>(9, 8, 7), file.readBytes().toList())
            store.delete(stored.publicId)
            assertFalse(file.exists())
            store.delete(stored.publicId)
            assertFailsWith<IllegalArgumentException> { store.delete("../fuera.txt") }
        } finally {
            directory.deleteRecursively()
        }
    }
}
