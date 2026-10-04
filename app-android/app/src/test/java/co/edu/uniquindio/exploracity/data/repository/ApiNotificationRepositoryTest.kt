package co.edu.uniquindio.exploracity.data.repository

import co.edu.uniquindio.exploracity.data.local.AuthTokens
import co.edu.uniquindio.exploracity.data.remote.FakeApi
import co.edu.uniquindio.exploracity.data.remote.NotificationApi
import co.edu.uniquindio.exploracity.data.remote.SentRequest
import co.edu.uniquindio.exploracity.data.remote.TestSession
import co.edu.uniquindio.exploracity.data.remote.empty
import co.edu.uniquindio.exploracity.data.remote.example
import co.edu.uniquindio.exploracity.data.remote.json
import co.edu.uniquindio.exploracity.domain.model.Notification
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpResponseData
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

/** 25 con la API: cada tipo con lo que pide su frase, y el contador de «Avisos» al día. */
class ApiNotificationRepositoryTest {

    private val stores = TestSession()
    private lateinit var api: FakeApi

    private suspend fun notifications(handler: MockRequestHandleScope.(SentRequest) -> HttpResponseData): ApiNotificationRepository {
        stores.tokens.save(AuthTokens("token-de-acceso", "token-de-renovacion"))
        api = FakeApi(handler)
        return ApiNotificationRepository(NotificationApi(api.sessionClient(stores)))
    }

    @Test
    fun `los avisos llegan como dice el contrato y el contador los cuenta`() = runTest {
        val repository = notifications { example("notifications.json") }

        val list = repository.notifications()

        assertNull(list.savedAt)
        val (commented, verified) = list.items
        assertEquals(
            Notification.Commented(
                id = "8e4a1c7f-3b2d-4e9a-a5c6-0f1d2e3b4a5c",
                createdAt = Instant.parse("2026-10-03T15:05:00Z"),
                read = false,
                poiId = "e3c4d5e6-f7a8-4b92-acbd-2e3f4a5b6c7d",
                poiTitle = "Plaza de Bolívar",
                authorName = "Ana Ríos",
                excerpt = "La catedral de noche es otra cosa.",
            ),
            commented,
        )
        assertEquals(35, (verified as Notification.Verified).points)
        assertEquals(2, repository.unreadCount.value)
    }

    @Test
    fun `cada tipo se lee con sus campos, y uno que la app no conoce no se muestra`() = runTest {
        val body = """
            {"items":[
              {"id":"1","type":"ACHIEVEMENT","createdAt":"2026-10-03T15:00:00Z","read":false,"achievement":"Caminante","nextBadge":"Primera publicación","remaining":1},
              {"id":"2","type":"DUPLICATE_REJECTED","createdAt":"2026-10-03T14:00:00Z","read":true,"placeId":"p","placeTitle":"Café La Fonda","existingPlaceId":"e","existingTitle":"Café Las Acacias"},
              {"id":"3","type":"REJECTED","createdAt":"2026-10-03T13:00:00Z","read":true,"placeId":"p","placeTitle":"Mirador","reason":"la foto no permite reconocer el lugar"},
              {"id":"4","type":"FINALIZED","createdAt":"2026-10-03T12:00:00Z","read":true,"placeId":"p","placeTitle":"Feria"},
              {"id":"5","type":"COMMENTED","createdAt":"2026-10-03T11:00:00Z","read":true,"placeId":"p","placeTitle":"Plaza","excerpt":"Hola"},
              {"id":"6","type":"SURVEY","createdAt":"2026-10-03T10:00:00Z","read":false}
            ],"unread":2}
        """.trimIndent()

        val items = notifications { json(body) }.notifications().items

        assertEquals(listOf("1", "2", "3", "4", "5"), items.map { it.id })
        assertEquals(Notification.Achievement("1", Instant.parse("2026-10-03T15:00:00Z"), false, "Caminante", "Primera publicación", 1), items[0])
        assertEquals("Café Las Acacias", (items[1] as Notification.DuplicateRejected).existingTitle)
        assertEquals("la foto no permite reconocer el lugar", (items[2] as Notification.Rejected).reason)
        assertNull((items[3] as Notification.Finalized).reason)
        // Sin nombre: la cuenta que comentó se eliminó.
        assertNull((items[4] as Notification.Commented).authorName)
    }

    @Test
    fun `marcar leído baja el contador y marcar todos lo deja en cero`() = runTest {
        val repository = notifications { sent -> if (sent.method == "GET") example("notifications.json") else empty() }
        repository.notifications()

        repository.markRead("8e4a1c7f-3b2d-4e9a-a5c6-0f1d2e3b4a5c")
        repository.markRead("8e4a1c7f-3b2d-4e9a-a5c6-0f1d2e3b4a5c")
        assertEquals(1, repository.unreadCount.value)
        repository.markAllRead()

        assertEquals(0, repository.unreadCount.value)
        assertEquals(
            listOf(
                "/v1/notifications",
                "/v1/notifications/8e4a1c7f-3b2d-4e9a-a5c6-0f1d2e3b4a5c/read",
                "/v1/notifications/8e4a1c7f-3b2d-4e9a-a5c6-0f1d2e3b4a5c/read",
                "/v1/notifications/read-all",
            ),
            api.requests.map { it.path },
        )
    }
}
