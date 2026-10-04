package co.edu.uniquindio.exploracity.data.photos

import co.edu.uniquindio.exploracity.data.local.AuthTokens
import co.edu.uniquindio.exploracity.data.remote.ApiException
import co.edu.uniquindio.exploracity.data.remote.FakeApi
import co.edu.uniquindio.exploracity.data.remote.PublicationApi
import co.edu.uniquindio.exploracity.data.remote.SentRequest
import co.edu.uniquindio.exploracity.data.remote.TestSession
import co.edu.uniquindio.exploracity.data.remote.error
import co.edu.uniquindio.exploracity.data.remote.example
import co.edu.uniquindio.exploracity.data.remote.failure
import co.edu.uniquindio.exploracity.data.remote.randomSecret
import co.edu.uniquindio.exploracity.domain.model.DraftPhoto
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** 19 con la API: la foto del formulario sube con su progreso y vuelve con su dirección. */
class ApiPhotoUploaderTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val stores = TestSession()
    private val accessToken = randomSecret()
    private lateinit var api: FakeApi

    private suspend fun uploader(handler: MockRequestHandleScope.(SentRequest) -> HttpResponseData): ApiPhotoUploader {
        stores.tokens.save(AuthTokens(accessToken, randomSecret()))
        api = FakeApi(handler)
        return ApiPhotoUploader(PublicationApi(api.sessionClient(stores)))
    }

    /** Una foto ya comprimida en el teléfono: un JPEG de 200 KB, para que el progreso avance en varios pasos. */
    private fun photo(): DraftPhoto {
        val file = folder.newFile("patio.jpg")
        file.writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()) + ByteArray(200 * 1024))
        return DraftPhoto("f1", file.absolutePath, "patio.jpg")
    }

    @Test
    fun `la foto sube como multipart, avanza hasta el 100 y termina con su dirección`() = runTest {
        val steps = uploader { example("photo.json", HttpStatusCode.Created) }.upload(photo()).toList()

        assertEquals(UploadProgress.Sending(0), steps.first())
        assertEquals(UploadProgress.Done("https://res.cloudinary.com/exploracity/image/upload/lugares/0f1e2d3c.jpg"), steps.last())
        val percents = steps.filterIsInstance<UploadProgress.Sending>().map { it.percent }
        assertEquals(100, percents.last())
        assertTrue("Avanza en varios pasos: $percents", percents.size > 2)
        assertEquals(percents.sorted().distinct(), percents)
        val sent = api.requests.single()
        assertEquals("POST", sent.method)
        assertEquals("/v1/photos", sent.path)
        assertTrue(sent.contentType!!.startsWith("multipart/form-data"))
        assertTrue("image/jpeg" in sent.body)
        assertTrue("patio.jpg" in sent.body)
    }

    @Test
    fun `si la API la rechaza, la subida falla con su código`() = runTest {
        val upload = uploader { error(HttpStatusCode.PayloadTooLarge, "photo_too_large") }.upload(photo())

        assertEquals("photo_too_large", (failure { upload.toList() } as ApiException).code)
    }
}
