package co.edu.uniquindio.exploracity.domain.repository

import co.edu.uniquindio.exploracity.domain.model.DuplicateCandidate
import co.edu.uniquindio.exploracity.domain.model.FinalizeReason
import co.edu.uniquindio.exploracity.domain.model.ModerationWork
import co.edu.uniquindio.exploracity.domain.model.RejectDecision
import co.edu.uniquindio.exploracity.domain.model.ResolvedPublication
import co.edu.uniquindio.exploracity.domain.model.ReviewItem
import co.edu.uniquindio.exploracity.domain.model.ReviewQueue
import kotlinx.coroutines.flow.StateFlow

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
