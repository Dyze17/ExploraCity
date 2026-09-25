package co.edu.uniquindio.exploracity.data.repository

import co.edu.uniquindio.exploracity.data.connectivity.ConnectivityObserver
import co.edu.uniquindio.exploracity.data.connectivity.OfflineException
import co.edu.uniquindio.exploracity.data.local.NOTIFICATIONS_TARGET
import co.edu.uniquindio.exploracity.data.local.NotificationsDao
import co.edu.uniquindio.exploracity.data.local.PendingActionEntity
import co.edu.uniquindio.exploracity.data.local.PendingActionsDao
import co.edu.uniquindio.exploracity.data.local.PendingType
import co.edu.uniquindio.exploracity.data.local.QueuedRead
import co.edu.uniquindio.exploracity.data.local.payloadAs
import co.edu.uniquindio.exploracity.data.local.toDomain
import co.edu.uniquindio.exploracity.data.local.toEntity
import co.edu.uniquindio.exploracity.data.sync.PendingScheduler
import co.edu.uniquindio.exploracity.domain.model.Notification
import co.edu.uniquindio.exploracity.domain.model.markedRead
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.Json
import java.io.IOException
import java.time.Clock
import java.time.Instant

/**
 * Envuelve al servidor de avisos ([remote]) y guarda en Room la última lista (25: sin conexión, la lista queda en su
 * último estado conocido con su antigüedad). Leer funciona siempre: cambia en el teléfono al instante y, sin red, la
 * lectura va a la cola de envío como el voto y la visita.
 */
class OfflineNotificationRepository(
    private val remote: NotificationRepository,
    private val dao: NotificationsDao,
    private val pending: PendingActionsDao,
    private val scheduler: PendingScheduler,
    private val connectivity: ConnectivityObserver,
    scope: CoroutineScope,
    private val clock: Clock = Clock.systemUTC(),
) : NotificationRepository {

    /** Sale de lo guardado: el badge responde al instante al leer, con red o sin ella. */
    override val unreadCount: StateFlow<Int> = dao.unreadCount().stateIn(scope, SharingStarted.Eagerly, 0)

    override suspend fun notifications(): NotificationList {
        if (!connectivity.isOnline.value) return saved() ?: throw OfflineException()
        val fresh = try {
            remote.notifications().items
        } catch (e: IOException) {
            // Hay red pero el servidor no respondió: mejor lo guardado que un error.
            return saved() ?: throw e
        }
        // Lo leído sin red que aún espera en la cola no vuelve a aparecer como nuevo.
        val items = withPendingReads(fresh)
        dao.replace(items.mapIndexed { i, notification -> notification.toEntity(i) }, clock.millis())
        return NotificationList(items)
    }

    override suspend fun markRead(id: String) {
        dao.markRead(id)
        send(QueuedRead(id)) { remote.markRead(id) }
    }

    override suspend fun markAllRead() {
        dao.markAllRead()
        send(QueuedRead(null)) { remote.markAllRead() }
    }

    /** Con red se envía ya; si no hay red (o se cae al enviar), queda en la cola. */
    private suspend fun send(read: QueuedRead, call: suspend () -> Unit) {
        if (connectivity.isOnline.value) {
            try {
                call()
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                // Se encola abajo.
            }
        }
        pending.insert(
            PendingActionEntity(
                type = PendingType.NOTIFICATION_READ,
                poiId = NOTIFICATIONS_TARGET,
                payload = Json.encodeToString(read),
                createdAtMillis = clock.millis(),
            ),
        )
        scheduler.schedule()
    }

    private suspend fun withPendingReads(items: List<Notification>): List<Notification> {
        val reads = pending.ofType(PendingType.NOTIFICATION_READ).map { it.payloadAs<QueuedRead>().id }
        if (reads.isEmpty()) return items
        if (null in reads) return items.map { it.markedRead() }
        return items.map { if (it.id in reads) it.markedRead() else it }
    }

    private suspend fun saved(): NotificationList? {
        val savedAt = dao.savedAt() ?: return null
        return NotificationList(dao.all().map { it.toDomain() }, Instant.ofEpochMilli(savedAt))
    }
}
