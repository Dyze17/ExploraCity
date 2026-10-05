package co.edu.uniquindio.exploracity.data.repository

import co.edu.uniquindio.exploracity.data.connectivity.ConnectivityObserver
import co.edu.uniquindio.exploracity.data.connectivity.OfflineException
import co.edu.uniquindio.exploracity.data.local.ReviewsDao
import co.edu.uniquindio.exploracity.data.local.toDomain
import co.edu.uniquindio.exploracity.data.local.toEntity
import co.edu.uniquindio.exploracity.domain.model.AlreadyReviewedException
import co.edu.uniquindio.exploracity.domain.model.DuplicateCandidate
import co.edu.uniquindio.exploracity.domain.model.FinalizeReason
import co.edu.uniquindio.exploracity.domain.model.ModerationWork
import co.edu.uniquindio.exploracity.domain.model.RejectDecision
import co.edu.uniquindio.exploracity.domain.model.ResolvedPublication
import co.edu.uniquindio.exploracity.domain.model.ReviewItem
import co.edu.uniquindio.exploracity.domain.model.ReviewQueue
import co.edu.uniquindio.exploracity.domain.model.StateChangedException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.io.IOException
import java.time.Clock
import java.time.Instant

/** Resumen de la cola para el acceso rápido del moderador en el feed (7.c). */
data class ModerationSummary(val pending: Int, val oldestWaitingDays: Int)
/** Moderación (SAD: componente de Moderación). Solo la usa quien entró con rol de moderador. */
interface ModerationRepository {
    /** Pendientes por revisar: el badge de la pestaña, que desaparece en cero (37). */
    val pendingCount: StateFlow<Int>

    /** 7.c · Cuántas esperan y desde hace cuántos días la más antigua. Lanza excepción si falla la red. */
    suspend fun summary(): ModerationSummary

    /** 32 · La cola, de la más antigua a la más reciente. Lanza excepción si falla la red y no hay nada guardado. */
    suspend fun queue(): ReviewQueue

    /** 33 · Una pendiente; null si ya no está en la cola. Lanza excepción si falla la red y no está guardada. */
    suspend fun item(id: String): ReviewItem?

    /** 33 · Los ids de la cola en orden, de la última carga: «1 de 7» y la siguiente al decidir. */
    suspend fun queueIds(): List<String>

    /**
     * 34 · Verifica la pendiente: entra al feed y al mapa, y el autor recibe el aviso y sus puntos. [note] es la nota
     * interna, que solo ven los moderadores. Lanza [AlreadyReviewedException] si otra persona ya la decidió.
     */
    suspend fun verify(id: String, note: String?)

    /**
     * 35 · Los lugares publicados con que se puede enlazar el original de un duplicado: los parecidos que ya trae la
     * pendiente o, si no trae, los más cercanos. Lanza excepción si falla la red.
     */
    suspend fun duplicateOptions(id: String): List<DuplicateCandidate>

    /**
     * 35 · Rechaza la pendiente: quien la publicó la ve rechazada con el motivo (24) y recibe el aviso (25). Lanza
     * [AlreadyReviewedException] si otra persona ya la decidió.
     */
    suspend fun reject(id: String, decision: RejectDecision)

    /** 37 · Lo decidido hoy por este moderador. Lanza excepción si falla la red. */
    suspend fun todayWork(): ModerationWork

    /** «Resueltas» (E1) · Lo ya decidido, de lo más reciente a lo más antiguo. Lanza excepción si falla la red. */
    suspend fun resolved(): List<ResolvedPublication>

    /** 36 · Una resuelta; null si ya no lo está (volvió a pendiente o la borraron). Lanza excepción si falla la red. */
    suspend fun resolvedItem(id: String): ResolvedPublication?

    /**
     * 36 · Pasa a finalizada una verificada: sigue en el feed y el mapa con su chip (B2 de Daniel), quien la publicó
     * recibe el aviso con el motivo y sus puntos no se descuentan. Lanza [StateChangedException] si ya no está verificada.
     */
    suspend fun finalize(id: String, reason: FinalizeReason)

    /**
     * 36 · Vuelve a pendiente una pública: sale del feed y del mapa y entra a la cola como recién enviada. [reason] queda
     * como nota interna. Lanza [StateChangedException] si ya no es pública.
     */
    suspend fun reopen(id: String, reason: String)
}
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
