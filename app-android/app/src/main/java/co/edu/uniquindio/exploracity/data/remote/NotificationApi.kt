package co.edu.uniquindio.exploracity.data.remote

import co.edu.uniquindio.exploracity.data.remote.dto.NotificationsDto
import co.edu.uniquindio.exploracity.domain.model.Notification
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.encodeURLPathPart

/** Los avisos de la API y cuántos hay sin leer. */
data class NotificationsResult(val items: List<Notification>, val unread: Int)

/** /v1/notifications · Los avisos de la persona (25). La app los consulta: no hay push (ADR-09). */
class NotificationApi(private val client: HttpClient) {
    /** Los últimos, del más reciente al más antiguo. Los de un tipo que la app no conoce no se muestran. */
    suspend fun notifications(): NotificationsResult {
        val body = client.get("v1/notifications").body<NotificationsDto>()
        return NotificationsResult(body.items.mapNotNull { it.toDomain() }, body.unread)
    }

    suspend fun markRead(id: String) {
        client.post("v1/notifications/${id.encodeURLPathPart()}/read")
    }

    suspend fun markAllRead() {
        client.post("v1/notifications/read-all")
    }
}
