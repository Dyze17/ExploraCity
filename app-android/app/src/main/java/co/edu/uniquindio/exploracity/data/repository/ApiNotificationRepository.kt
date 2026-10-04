package co.edu.uniquindio.exploracity.data.repository

import co.edu.uniquindio.exploracity.data.remote.NotificationApi
import co.edu.uniquindio.exploracity.domain.model.Notification
import co.edu.uniquindio.exploracity.domain.model.markedRead
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Los avisos con la API (ADR-09: la app los consulta). [unreadCount] se pone al día con cada consulta y con cada aviso
 * que se marca leído. Lo de ver sin conexión lo hace OfflineNotificationRepository, que envuelve a este.
 */
class ApiNotificationRepository(private val api: NotificationApi) : NotificationRepository {

    private val unread = MutableStateFlow(0)
    override val unreadCount: StateFlow<Int> = unread.asStateFlow()

    /** Lo último que llegó, para restar del contador sin volver a pedir la lista. */
    private var latest: List<Notification> = emptyList()

    override suspend fun notifications(): NotificationList {
        val result = api.notifications()
        latest = result.items
        unread.value = result.unread
        return NotificationList(result.items)
    }

    override suspend fun markRead(id: String) {
        api.markRead(id)
        if (latest.any { it.id == id && !it.read }) {
            latest = latest.map { if (it.id == id) it.markedRead() else it }
            unread.value = (unread.value - 1).coerceAtLeast(0)
        }
    }

    override suspend fun markAllRead() {
        api.markAllRead()
        latest = latest.map { it.markedRead() }
        unread.value = 0
    }
}
