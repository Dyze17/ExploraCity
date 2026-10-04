package co.edu.uniquindio.exploracity.support

import co.edu.uniquindio.exploracity.config.AppConfig
import co.edu.uniquindio.exploracity.config.JwtConfig
import co.edu.uniquindio.exploracity.config.UserPrincipal
import co.edu.uniquindio.exploracity.exploraModule
import co.edu.uniquindio.exploracity.integration.CategoryClassifier
import co.edu.uniquindio.exploracity.integration.DevMailbox
import co.edu.uniquindio.exploracity.integration.ImageType
import co.edu.uniquindio.exploracity.integration.Integrations
import co.edu.uniquindio.exploracity.integration.KeywordClassifier
import co.edu.uniquindio.exploracity.integration.MailClient
import co.edu.uniquindio.exploracity.integration.MailDeliveryException
import co.edu.uniquindio.exploracity.integration.MailMessage
import co.edu.uniquindio.exploracity.integration.MediaStore
import co.edu.uniquindio.exploracity.integration.MediaStoreException
import co.edu.uniquindio.exploracity.integration.StoredMedia
import co.edu.uniquindio.exploracity.model.LinkPurpose
import co.edu.uniquindio.exploracity.model.Role
import co.edu.uniquindio.exploracity.model.SessionResponse
import co.edu.uniquindio.exploracity.service.AccountMail
import co.edu.uniquindio.exploracity.service.PasswordHasher
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.v1.jdbc.Database
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals

/** Correo de prueba: guarda lo enviado y puede fallar como un SendGrid caído. */
class TestMail : MailClient {
    val sent = CopyOnWriteArrayList<MailMessage>()

    @Volatile
    var failing = false

    override suspend fun send(message: MailMessage) {
        if (failing) throw MailDeliveryException("Correo caído (prueba)")
        sent += message
    }
}

/** Almacén de fotos en memoria; puede fallar como un Cloudinary caído. */
class TestMedia : MediaStore {
    val stored = ConcurrentHashMap<String, ByteArray>()

    @Volatile
    var failing = false

    override suspend fun upload(bytes: ByteArray, type: ImageType, folder: String): StoredMedia {
        if (failing) throw MediaStoreException("Almacén caído (prueba)")
        val publicId = "$folder/${UUID.randomUUID()}.${type.extension}"
        stored[publicId] = bytes
        return StoredMedia("https://medios.test/$publicId", publicId)
    }

    override suspend fun delete(publicId: String) {
        if (failing) throw MediaStoreException("Almacén caído (prueba)")
        stored.remove(publicId)
    }
}

/** Una sesión abierta en las pruebas, con la contraseña con que se creó la cuenta. */
data class TestSession(val userId: UUID, val email: String, val password: String, val accessToken: String, val refreshToken: String)

/** Pruebas de la API completa contra PostGIS: reloj que se mueve a mano, correo y fotos en memoria, BCrypt rápido. */
abstract class ApiTest : DatabaseTest() {

    protected fun apiTest(
        moderators: Set<String> = emptySet(),
        devMailbox: Boolean = false,
        classifier: CategoryClassifier = KeywordClassifier(),
        block: suspend TestApi.() -> Unit,
    ) = testApplication {
        val settings = testConfig(moderators, devMailbox)
        val clock = MutableClock(NOW)
        val mail: MailClient = if (devMailbox) DevMailbox() else TestMail()
        val media = TestMedia()
        environment { config = MapApplicationConfig() }
        application { exploraModule(settings, database, clock, Integrations(mail, media, classifier), PasswordHasher(cost = 4)) }
        TestApi(client, database, settings, clock, mail, media).block()
    }

    companion object {
        val NOW: Instant = Instant.parse("2026-10-03T15:00:00Z")
    }
}

class TestApi(
    val client: HttpClient,
    val database: Database,
    val config: AppConfig,
    val clock: MutableClock,
    val mail: MailClient,
    val media: TestMedia,
) {
    private val accountMail = AccountMail(mail, config.mail.linkBaseUrl)
    private val jwt = JwtConfig(config.jwt, clock)

    val testMail: TestMail get() = mail as TestMail

    suspend fun get(path: String, token: String? = null): HttpResponse = client.get(path) { auth(token) }

    suspend fun post(path: String, json: String? = null, token: String? = null): HttpResponse =
        client.post(path) { send(json, token) }

    suspend fun put(path: String, json: String? = null, token: String? = null): HttpResponse =
        client.put(path) { send(json, token) }

    suspend fun delete(path: String, token: String? = null): HttpResponse = client.delete(path) { auth(token) }

    /** Un token de acceso para una cuenta guardada directo en la base de datos. */
    fun accessToken(userId: UUID, role: Role = Role.USER): String = jwt.accessToken(UserPrincipal(userId, role))

    /** Los correos que llegaron a [email], del más reciente al más antiguo. */
    fun sentTo(email: String): List<MailMessage> = testMail.sent.filter { it.to == email }.reversed()

    /** El token del último enlace de [purpose] que llegó a [email]. */
    fun linkSentTo(email: String, purpose: LinkPurpose): String? =
        sentTo(email).firstNotNullOfOrNull { accountMail.tokenIn(it, purpose) }

    /** Crea una cuenta por la API y devuelve su sesión. */
    suspend fun register(
        email: String = "ana.rios@correo.com",
        password: String = newPassword(),
        name: String = "Ana Ríos",
        residency: String = "RESIDENT",
    ): TestSession {
        val response = post(
            "/v1/auth/register",
            """{"name":"$name","email":"$email","password":"$password","residency":"$residency"}""",
        )
        assertEquals(HttpStatusCode.Created, response.status, response.bodyAsText())
        return response.session(password)
    }

    /** 19 · Sube una foto JPEG de prueba y devuelve su dirección. */
    suspend fun uploadPhoto(token: String, bytes: ByteArray = JPEG): String {
        val response = uploadPhotoResponse(token, bytes)
        assertEquals(HttpStatusCode.Created, response.status, response.bodyAsText())
        return Json.parseToJsonElement(response.bodyAsText()).jsonObject["url"]!!.jsonPrimitive.content
    }

    suspend fun uploadPhotoResponse(token: String, bytes: ByteArray): HttpResponse =
        client.post("/v1/photos") {
            bearerAuth(token)
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append(
                            "photo",
                            bytes,
                            Headers.build {
                                append(HttpHeaders.ContentType, "image/jpeg")
                                append(HttpHeaders.ContentDisposition, "filename=\"foto.jpg\"")
                            },
                        )
                    },
                ),
            )
        }

    /** 20 · Publica un lugar con una foto y devuelve su id. [extra] se agrega al JSON del envío. */
    suspend fun publish(
        token: String,
        title: String = "Café de la Estación",
        latitude: Double = 4.5450,
        longitude: Double = -75.6750,
        extra: String = "",
    ): String {
        val photo = uploadPhoto(token)
        val response = post(
            "/v1/publications",
            """{"title":"$title","description":"Un café pequeño frente a la vieja estación del tren, con tostión propia.",""" +
                """"category":"GASTRONOMY","categoryOrigin":"SUGGESTED","location":{"latitude":$latitude,"longitude":$longitude},""" +
                """"photos":["$photo"]$extra}""",
            token,
        )
        assertEquals(HttpStatusCode.Created, response.status, response.bodyAsText())
        return Json.parseToJsonElement(response.bodyAsText()).jsonObject["publicationId"]!!.jsonPrimitive.content
    }

    suspend fun login(email: String, password: String): HttpResponse =
        post("/v1/auth/login", """{"email":"$email","password":"$password"}""")

    suspend fun HttpResponse.session(password: String = ""): TestSession {
        val body = Json.decodeFromString<SessionResponse>(bodyAsText())
        return TestSession(UUID.fromString(body.userId), body.email, password, body.accessToken, body.refreshToken)
    }

    private fun HttpRequestBuilder.auth(token: String?) {
        token?.let { bearerAuth(it) }
    }

    private fun HttpRequestBuilder.send(json: String?, token: String?) {
        auth(token)
        if (json != null) {
            contentType(ContentType.Application.Json)
            setBody(json)
        }
    }
}

suspend fun HttpResponse.json(): JsonElement = Json.parseToJsonElement(bodyAsText())

/** Los primeros bytes de un JPEG: lo que el almacén reconoce como foto. */
val JPEG = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2, 3)
