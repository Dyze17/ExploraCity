package co.edu.uniquindio.exploracity.data.repository

import co.edu.uniquindio.exploracity.data.local.AuthTokens
import co.edu.uniquindio.exploracity.data.location.SimulatedLocationProvider
import co.edu.uniquindio.exploracity.data.remote.FakeApi
import co.edu.uniquindio.exploracity.data.remote.ProfileApi
import co.edu.uniquindio.exploracity.data.remote.SentRequest
import co.edu.uniquindio.exploracity.data.remote.TestSession
import co.edu.uniquindio.exploracity.data.remote.empty
import co.edu.uniquindio.exploracity.data.remote.error
import co.edu.uniquindio.exploracity.data.remote.example
import co.edu.uniquindio.exploracity.domain.model.Author
import co.edu.uniquindio.exploracity.domain.model.GeoPoint
import co.edu.uniquindio.exploracity.domain.model.PhotoChange
import co.edu.uniquindio.exploracity.domain.model.ProfileUpdate
import co.edu.uniquindio.exploracity.domain.model.ReportReason
import co.edu.uniquindio.exploracity.domain.model.Residency
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/** 26, 28, 31 y 31A con la API: el perfil propio, su edición con la foto, el perfil público y el reporte. */
class ApiUserRepositoryTest {

    private val stores = TestSession()
    private lateinit var api: FakeApi

    private suspend fun users(handler: MockRequestHandleScope.(SentRequest) -> HttpResponseData): ApiUserRepository {
        stores.tokens.save(AuthTokens("token-de-acceso", "token-de-renovacion"))
        api = FakeApi(handler)
        return ApiUserRepository(ProfileApi(api.sessionClient(stores)), SimulatedLocationProvider(GeoPoint(4.5402, -75.6721)))
    }

    private val update = ProfileUpdate(name = "Ana Ríos", bio = null, residency = Residency.RESIDENT, photo = PhotoChange.Keep)

    @Test
    fun `el perfil público llega como dice el contrato, con distancias desde la persona`() = runTest {
        val profile = users { example("public-profile.json") }.publicProfile("9b2d4f6a-1c3e-4a5b-8d7f-0e2c4a6b8d10")!!

        assertEquals(Author("9b2d4f6a-1c3e-4a5b-8d7f-0e2c4a6b8d10", "Laura Gómez", 35), profile.author)
        assertEquals(Residency.VISITOR, profile.residency)
        assertEquals("Armenia", profile.city)
        assertNull(profile.bio)
        assertEquals(1, profile.badges)
        assertEquals(listOf("Plaza de Bolívar"), profile.places.map { it.title })
        assertEquals(1, profile.verifiedCount)
        assertEquals("/v1/users/9b2d4f6a-1c3e-4a5b-8d7f-0e2c4a6b8d10", api.requests.single().path)
        assertEquals(true, api.requests.single().fullUrl.endsWith("?near=4.5402%2C-75.6721"))
    }

    @Test
    fun `una persona que ya no existe no tiene perfil`() = runTest {
        assertNull(users { error(HttpStatusCode.NotFound, "user_not_found") }.publicProfile("eliminada"))
    }

    @Test
    fun `editar sin tocar la foto solo guarda los datos`() = runTest {
        users { example("profile.json") }.updateProfile(update)

        assertEquals(listOf("PUT /v1/profile"), api.requests.map { "${it.method} ${it.path}" })
    }

    @Test
    fun `una foto nueva se sube antes de guardar los datos`() = runTest {
        val file = File.createTempFile("perfil", ".jpg").apply {
            writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 1))
            deleteOnExit()
        }

        users { example("profile.json") }.updateProfile(update.copy(photo = PhotoChange.Replace(file.path)))

        assertEquals(listOf("PUT /v1/profile/photo", "PUT /v1/profile"), api.requests.map { "${it.method} ${it.path}" })
    }

    @Test
    fun `quitar la foto y luego guardar los datos`() = runTest {
        users { example("profile.json") }.updateProfile(update.copy(photo = PhotoChange.Remove))

        assertEquals(listOf("DELETE /v1/profile/photo", "PUT /v1/profile"), api.requests.map { "${it.method} ${it.path}" })
    }

    @Test
    fun `si la foto no se sube, los datos no se guardan`() = runTest {
        val file = File.createTempFile("perfil", ".jpg").apply { deleteOnExit() }

        val repository = users { error(HttpStatusCode.BadGateway, "photo_upload_failed") }
        runCatching { repository.updateProfile(update.copy(photo = PhotoChange.Replace(file.path))) }

        assertEquals(listOf("PUT /v1/profile/photo"), api.requests.map { "${it.method} ${it.path}" })
    }

    @Test
    fun `el perfil propio y el reporte`() = runTest {
        val repository = users { sent -> if (sent.method == "GET") example("profile.json") else empty() }

        assertEquals(40, repository.ownProfile().author.points)
        repository.reportUser("9b2d4f6a-1c3e-4a5b-8d7f-0e2c4a6b8d10", ReportReason.IMPERSONATION)

        assertEquals("""{"reason":"IMPERSONATION"}""", api.requests.last().body)
    }
}
