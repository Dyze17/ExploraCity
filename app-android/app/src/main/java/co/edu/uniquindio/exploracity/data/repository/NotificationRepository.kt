package co.edu.uniquindio.exploracity.data.repository

import co.edu.uniquindio.exploracity.domain.model.Notification
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant

/** Los avisos, de la más reciente a la más antigua. [savedAt] no es null si vienen de lo guardado (sin conexión). */
data class NotificationList(val items: List<Notification>, val savedAt: Instant? = null)
/** 25 · Avisos dentro de la app (SAD: sin push). */
interface NotificationRepository {
    /** Sin leer: el badge de la pestaña «Avisos». */
    val unreadCount: StateFlow<Int>

    /** Lanza excepción si falla la red y no hay nada guardado. */
    suspend fun notifications(): NotificationList

    suspend fun markRead(id: String)

    suspend fun markAllRead()
}
