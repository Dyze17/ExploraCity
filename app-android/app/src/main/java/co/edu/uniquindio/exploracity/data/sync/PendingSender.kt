package co.edu.uniquindio.exploracity.data.sync

import co.edu.uniquindio.exploracity.data.local.PendingActionEntity
import co.edu.uniquindio.exploracity.data.local.PendingActionsDao
import co.edu.uniquindio.exploracity.data.local.PendingType
import co.edu.uniquindio.exploracity.data.local.QueuedComment
import co.edu.uniquindio.exploracity.data.local.QueuedRead
import co.edu.uniquindio.exploracity.data.local.QueuedVisit
import co.edu.uniquindio.exploracity.data.local.QueuedVote
import co.edu.uniquindio.exploracity.data.local.SavedPlacesDao
import co.edu.uniquindio.exploracity.data.local.payloadAs
import co.edu.uniquindio.exploracity.data.repository.NotificationRepository
import co.edu.uniquindio.exploracity.data.repository.PoiRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

/**
 * Envía la cola al servidor ([remote]) en el orden en que se hizo cada cosa. Lo llama [SendPendingWorker] cuando
 * WorkManager ve red, también con la app cerrada.
 *
 * - Si sale bien, la acción se borra y lo guardado para ver sin conexión se pone al día.
 * - Si falla la red, se detiene y la cola queda como estaba: WorkManager reintenta después.
 * - Si el servidor la rechaza (p. ej. el lugar ya no existe), se descarta: reintentar no la arreglaría. Con la API
 *   real, un 4xx se descarta y un 5xx reintenta, como la red.
 */
class PendingSender(
    private val remote: PoiRepository,
    private val notifications: NotificationRepository,
    private val pending: PendingActionsDao,
    private val saved: SavedPlacesDao,
) {
    private val mutex = Mutex()

    /** true si la cola quedó vacía; false si falló la red y hay que reintentar más tarde. */
    suspend fun flush(): Boolean = mutex.withLock {
        var action = pending.next()
        while (action != null) {
            if (!trySend(action)) return@withLock false
            pending.delete(action.id)
            action = pending.next()
        }
        true
    }

    /** false si falló la red; true si se envió o si no tiene arreglo y se descarta. */
    private suspend fun trySend(action: PendingActionEntity): Boolean = try {
        send(action)
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        false
    } catch (e: Exception) {
        true
    }

    private suspend fun send(action: PendingActionEntity) {
        val poiId = action.poiId
        when (action.type) {
            PendingType.VISIT -> remote.markVisited(poiId, action.payloadAs<QueuedVisit>().toDomain())
            PendingType.VOTE -> {
                val voted = action.payloadAs<QueuedVote>().voted
                // El total real puede haber cambiado mientras tanto: lo guardado se queda con el del servidor.
                saved.saveVote(poiId, voted, remote.setVote(poiId, voted).votes)
            }
            PendingType.COMMENT -> {
                remote.addComment(poiId, action.payloadAs<QueuedComment>().text)
                saved.addComment(poiId)
            }
            PendingType.NOTIFICATION_READ -> action.payloadAs<QueuedRead>().id?.let { notifications.markRead(it) } ?: notifications.markAllRead()
        }
    }
}
