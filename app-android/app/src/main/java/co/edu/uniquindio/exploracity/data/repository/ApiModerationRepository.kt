package co.edu.uniquindio.exploracity.data.repository

import co.edu.uniquindio.exploracity.data.remote.ApiException
import co.edu.uniquindio.exploracity.data.remote.ModerationApi
import co.edu.uniquindio.exploracity.domain.model.AlreadyReviewedException
import co.edu.uniquindio.exploracity.domain.model.DuplicateCandidate
import co.edu.uniquindio.exploracity.domain.model.FinalizeReason
import co.edu.uniquindio.exploracity.domain.model.ModerationWork
import co.edu.uniquindio.exploracity.domain.model.RejectDecision
import co.edu.uniquindio.exploracity.domain.model.ResolvedPublication
import co.edu.uniquindio.exploracity.domain.model.ReviewItem
import co.edu.uniquindio.exploracity.domain.model.ReviewQueue
import co.edu.uniquindio.exploracity.domain.model.StateChangedException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Moderación con la API (SAD: componente de Moderación). Leer la cola sin conexión lo hace OfflineModerationRepository,
 * que envuelve a este. Una decisión que llega tarde (otra persona ya decidió, o la publicación cambió de estado o la
 * borraron) se traduce a las excepciones que las pantallas ya conocen.
 */
class ApiModerationRepository(private val api: ModerationApi) : ModerationRepository {

    private val pending = MutableStateFlow(0)

    /** Lo que dijo el servidor la última vez (resumen o cola), menos lo decidido desde entonces. */
    override val pendingCount: StateFlow<Int> = pending.asStateFlow()

    @Volatile
    private var lastQueue: List<String> = emptyList()

    override suspend fun summary(): ModerationSummary = api.summary().also { pending.value = it.pending }

    override suspend fun queue(): ReviewQueue {
        val items = api.queue()
        lastQueue = items.map { it.id }
        pending.value = items.size
        return ReviewQueue(items)
    }

    /** null si ya no está en la cola: la decidió otra persona o la borró quien la publicó. */
    override suspend fun item(id: String): ReviewItem? = try {
        api.item(id)
    } catch (e: ApiException) {
        if (e.leftQueue()) null else throw e
    }

    override suspend fun queueIds(): List<String> = lastQueue

    override suspend fun verify(id: String, note: String?) = decide(id) { api.verify(id, note) }

    override suspend fun duplicateOptions(id: String): List<DuplicateCandidate> = api.duplicateOptions(id)

    override suspend fun reject(id: String, decision: RejectDecision) = decide(id) { api.reject(id, decision) }

    override suspend fun todayWork(): ModerationWork = api.today()

    override suspend fun resolved(): List<ResolvedPublication> = api.resolved()

    /** null si ya no está resuelta: volvió a pendiente o la borraron. */
    override suspend fun resolvedItem(id: String): ResolvedPublication? = try {
        api.resolvedItem(id)
    } catch (e: ApiException) {
        if (e.status == HttpStatusCode.NotFound.value) null else throw e
    }

    override suspend fun finalize(id: String, reason: FinalizeReason) = changeState { api.finalize(id, reason) }

    override suspend fun reopen(id: String, reason: String) = changeState { api.reopen(id, reason) }

    /** Verificar o rechazar. Si ya no estaba pendiente, sale de la cola igual y se avisa con [AlreadyReviewedException]. */
    private suspend fun decide(id: String, send: suspend () -> Unit) {
        try {
            send()
        } catch (e: ApiException) {
            if (!e.leftQueue()) throw e
            leave(id)
            throw AlreadyReviewedException()
        }
        leave(id)
    }

    /** 36 · Si ya no está en el estado que se vio, o la borraron, [StateChangedException]. */
    private suspend fun changeState(send: suspend () -> Unit) {
        try {
            send()
        } catch (e: ApiException) {
            if (e.status == HttpStatusCode.NotFound.value || e.code == STATE_CHANGED) throw StateChangedException()
            throw e
        }
    }

    private fun leave(id: String) {
        if (id !in lastQueue) return
        lastQueue = lastQueue - id
        pending.update { (it - 1).coerceAtLeast(0) }
    }

    /** La pendiente ya no está: la decidió otra persona (409) o la borró quien la publicó (404). */
    private fun ApiException.leftQueue(): Boolean = status == HttpStatusCode.NotFound.value || code == ALREADY_REVIEWED

    private companion object {
        const val ALREADY_REVIEWED = "already_reviewed"
        const val STATE_CHANGED = "state_changed"
    }
}
