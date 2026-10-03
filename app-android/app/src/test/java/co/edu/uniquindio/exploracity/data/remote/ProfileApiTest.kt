package co.edu.uniquindio.exploracity.data.remote

import co.edu.uniquindio.exploracity.data.local.AuthTokens
import co.edu.uniquindio.exploracity.domain.model.Author
import co.edu.uniquindio.exploracity.domain.model.BadgeMetric
import co.edu.uniquindio.exploracity.domain.model.Category
import co.edu.uniquindio.exploracity.domain.model.PublicationCounts
import co.edu.uniquindio.exploracity.domain.model.ReportReason
import co.edu.uniquindio.exploracity.domain.model.Residency
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpResponseData
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.YearMonth

/** 26, 28 y 31A con la API: el perfil del contrato, su edición con la foto y el reporte. */
class ProfileApiTest {

    private val stores = TestSession()
    private lateinit var api: FakeApi

    private suspend fun profiles(handler: MockRequestHandleScope.(SentRequest) -> HttpResponseData): ProfileApi {
        stores.tokens.save(AuthTokens("token-de-acceso", "token-de-renovacion"))
        api = FakeApi(handler)
        return ProfileApi(api.sessionClient(stores))
    }

    @Test
    fun `lee el perfil propio tal como lo describe el contrato`() = runTest {
        val profile = profiles { example("profile.json") }.ownProfile()

        assertEquals(Author("3f6c1e2a-8b4d-4c7e-9a1f-2d5b7e9c0a14", "Ana Ríos", 40), profile.author)
        assertEquals(Residency.RESIDENT, profile.residency)
        assertEquals("Armenia", profile.city)
        assertEquals(YearMonth.of(2026, 3), profile.memberSince)
        assertEquals(PublicationCounts(pending = 1, verified = 1), profile.publications)
        assertEquals("Me encanta caminar por los miradores del Quindío.", profile.bio)
        assertTrue(profile.photo!!.startsWith("https://"))
        assertNull(profile.savedAt)
        assertEquals(9, profile.badges.size)
        assertEquals(2, profile.unlockedBadges)
        val green = profile.badges[1]
        assertEquals("amigo-del-verde", green.id)
        assertEquals(BadgeMetric.CATEGORY_PLACES, green.metric)
        assertEquals(Category.NATURE, green.category)
        assertNull(profile.badges[0].tip)
        assertFalse(profile.badges[2].unlocked)
        assertEquals("Bearer token-de-acceso", api.requests.single().authorization)
    }

    @Test
    fun `editar envía nombre, sobre mí y cómo se presenta`() = runTest {
        profiles { example("profile.json") }.updateProfile("Ana Ríos", null, Residency.VISITOR)

        val sent = api.requests.single()
        assertEquals("PUT", sent.method)
        assertEquals("/v1/profile", sent.path)
        assertEquals("""{"name":"Ana Ríos","residency":"VISITOR"}""", sent.body)
    }

    @Test
    fun `la foto va como multipart con su formato`() = runTest {
        val file = File.createTempFile("perfil", ".png").apply {
            writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2))
            deleteOnExit()
        }

        profiles { example("profile.json") }.uploadPhoto(file)

        val sent = api.requests.single()
        assertEquals("PUT", sent.method)
        assertEquals("/v1/profile/photo", sent.path)
        assertTrue(sent.contentType!!.startsWith("multipart/form-data"))
        assertTrue("name=photo" in sent.body || "name=\"photo\"" in sent.body)
        assertTrue("image/png" in sent.body)
    }

    @Test
    fun `quitar la foto y reportar un perfil`() = runTest {
        val profileApi = profiles { sent -> if (sent.method == "DELETE") example("profile.json") else empty() }

        profileApi.removePhoto()
        profileApi.reportUser("9b2d4f6a-1c3e-4a5b-8d7f-0e2c4a6b8d10", ReportReason.SPAM)

        assertEquals(listOf("DELETE", "POST"), api.requests.map { it.method })
        assertEquals("/v1/users/9b2d4f6a-1c3e-4a5b-8d7f-0e2c4a6b8d10/reports", api.requests[1].path)
        assertEquals("""{"reason":"SPAM"}""", api.requests[1].body)
    }
}
