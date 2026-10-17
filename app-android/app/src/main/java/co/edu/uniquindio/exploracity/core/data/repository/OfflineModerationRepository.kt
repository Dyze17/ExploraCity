package co.edu.uniquindio.exploracity.core.data.repository

import co.edu.uniquindio.exploracity.core.data.connectivity.ConnectivityObserver
import co.edu.uniquindio.exploracity.core.data.connectivity.OfflineException
import co.edu.uniquindio.exploracity.core.data.local.ReviewsDao
import co.edu.uniquindio.exploracity.core.data.local.toDomain
import co.edu.uniquindio.exploracity.core.data.local.toEntity
import co.edu.uniquindio.exploracity.domain.model.AlreadyReviewedException
import co.edu.uniquindio.exploracity.domain.model.DuplicateCandidate
import co.edu.uniquindio.exploracity.domain.model.FinalizeReason
import co.edu.uniquindio.exploracity.domain.model.ModerationWork
import co.edu.uniquindio.exploracity.domain.model.RejectDecision
import co.edu.uniquindio.exploracity.domain.model.ResolvedPublication
import co.edu.uniquindio.exploracity.domain.model.ReviewItem
import co.edu.uniquindio.exploracity.domain.model.ReviewQueue
import co.edu.uniquindio.exploracity.domain.repository.ModerationRepository
import co.edu.uniquindio.exploracity.domain.repository.ModerationSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.io.IOException
import java.time.Clock
import java.time.Instant

/**
 * Envuelve al servidor de moderación ([remote]) y guarda en Room la última cola: sin conexión se lee lo guardado con su
 * antigüedad (32.c) y no se decide nada, para no dejar decisiones a medias. El badge sale de lo guardado. «Resueltas» y
 * los cambios de estado (36) necesitan red: no se guardan.
 */
class OfflineModerationRepository(
    private val remote: ModerationRepository,
    private val dao: ReviewsDao,
    private val connectivity: ConnectivityObserver,
    scope: CoroutineScope,
    private val clock: Clock = Clock.systemUTC(),
) : ModerationRepository {

    override val pendingCount: StateFlow<Int> = dao.count().stateIn(scope, SharingStarted.Eagerly, 0)

    /** Sale de la cola (y la guarda): la tarjeta del feed y el badge cuentan lo mismo. */
    override suspend fun summary(): ModerationSummary {
        val items = queue().items
        val now = clock.instant()
        return ModerationSummary(items.size, items.maxOfOrNull { it.waitingDays(now).toInt() } ?: 0)
    }

    override suspend fun queue(): ReviewQueue {
        if (!connectivity.isOnline.value) return saved() ?: throw OfflineException()
        val fresh = try {
            remote.queue().items
        } catch (e: IOException) {
            // Hay red pero el servidor no respondió: mejor lo guardado que un error.
            return saved() ?: throw e
        }
        dao.replace(fresh.mapIndexed { i, item -> item.toEntity(i) }, clock.millis())
        return ReviewQueue(fresh)
    }

    override suspend fun item(id: String): ReviewItem? {
        if (!connectivity.isOnline.value) return dao.item(id)?.toDomain() ?: throw OfflineException()
        return remote.item(id)
    }

    override suspend fun queueIds(): List<String> = dao.ids()

    override suspend fun verify(id: String, note: String?) = decide(id) { remote.verify(id, note) }

    override suspend fun duplicateOptions(id: String): List<DuplicateCandidate> = online { remote.duplicateOptions(id) }

    override suspend fun reject(id: String, decision: RejectDecision) = decide(id) { remote.reject(id, decision) }

    override suspend fun todayWork(): ModerationWork = online { remote.todayWork() }

    override suspend fun resolved(): List<ResolvedPublication> = online { remote.resolved() }

    override suspend fun resolvedItem(id: String): ResolvedPublication? = online { remote.resolvedItem(id) }

    override suspend fun finalize(id: String, reason: FinalizeReason) = online { remote.finalize(id, reason) }

    override suspend fun reopen(id: String, reason: String) {
        online { remote.reopen(id, reason) }
        // Vuelve a la cola: el badge la cuenta ya, sin esperar a que se abra la cola.
        try {
            queue()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Se pone al día la próxima vez que se abra la cola.
        }
    }

    /** Verificar o rechazar: con red, y la decidida sale también de lo guardado (aunque la haya decidido otra persona). */
    private suspend fun decide(id: String, send: suspend () -> Unit) {
        online {
            try {
                send()
            } catch (e: AlreadyReviewedException) {
                dao.remove(id)
                throw e
            }
        }
        dao.remove(id)
    }

    private suspend fun <T> online(block: suspend () -> T): T {
        if (!connectivity.isOnline.value) throw OfflineException()
        return block()
    }

    private suspend fun saved(): ReviewQueue? {
        val savedAt = dao.savedAt() ?: return null
        return ReviewQueue(dao.all().map { it.toDomain() }, Instant.ofEpochMilli(savedAt))
    }
}
