package co.edu.uniquindio.exploracity.service

import co.edu.uniquindio.exploracity.config.query
import co.edu.uniquindio.exploracity.model.NotificationResponse
import co.edu.uniquindio.exploracity.model.NotificationType
import co.edu.uniquindio.exploracity.model.NotificationsResponse
import co.edu.uniquindio.exploracity.model.iso
import co.edu.uniquindio.exploracity.plugins.ApiException
import co.edu.uniquindio.exploracity.repository.NotificationRepository
import co.edu.uniquindio.exploracity.repository.NotificationRow
import org.jetbrains.exposed.v1.jdbc.Database
import java.time.Clock
import java.util.UUID

/** Componente de Notificaciones (SAD, ADR-09) · Los avisos de la persona (25): la app los consulta, no hay push. */
class NotificationService(
    private val database: Database,
    private val notifications: NotificationRepository,
    private val clock: Clock,
) {
    /** Los últimos [LIMIT], del más reciente al más antiguo, y cuántos hay sin leer. */
    suspend fun list(userId: UUID): NotificationsResponse = database.query {
        NotificationsResponse(notifications.list(userId, LIMIT).map { it.toResponse() }, notifications.unread(userId))
    }

    /** Leer uno ya leído no cambia nada. 404 si no existe o es de otra persona. */
    suspend fun markRead(userId: UUID, notificationId: String) {
        val id = runCatching { UUID.fromString(notificationId) }.getOrNull() ?: throw notFound()
        val found = database.query { notifications.markRead(userId, id, clock.instant()) }
        if (!found) throw notFound()
    }

    suspend fun markAllRead(userId: UUID) {
        database.query { notifications.markAllRead(userId, clock.instant()) }
    }

    /** Cada tipo lleva solo lo que su frase necesita. */
    private fun NotificationRow.toResponse(): NotificationResponse {
        val base = NotificationResponse(id = id.toString(), type = type, createdAt = createdAt.iso(), read = read)
        val place = placeId?.toString()
        return when (type) {
            NotificationType.VERIFIED -> base.copy(placeId = place, placeTitle = placeTitle, points = points ?: 0)
            NotificationType.FINALIZED -> base.copy(placeId = place, placeTitle = placeTitle, reason = reason)
            NotificationType.REJECTED -> base.copy(placeId = place, placeTitle = placeTitle, reason = reason)
            NotificationType.DUPLICATE_REJECTED -> base.copy(
                placeId = place,
                placeTitle = placeTitle,
                existingPlaceId = existingPlaceId?.toString(),
                existingTitle = existingTitle,
            )
            NotificationType.COMMENTED -> base.copy(
                placeId = place,
                placeTitle = placeTitle,
                authorName = commentAuthor,
                excerpt = commentText,
            )
            NotificationType.ACHIEVEMENT -> base.copy(achievement = achievement, nextBadge = nextBadge, remaining = remaining)
        }
    }

    private fun notFound() = ApiException.notFound("notification_not_found")

    private companion object {
        /** La lista no se pagina: los avisos viejos dejan de verse. */
        const val LIMIT = 100
    }
}
