package co.edu.uniquindio.exploracity.domain.model

import java.time.Duration
import java.time.Instant

/** 32 · Urgencia según lo que lleva esperando: rojo desde 3 días, ámbar desde 1 día, neutra el resto (README · State Management). */
enum class ReviewUrgency { HIGH, MEDIUM, NORMAL }

/** 33 · Quién publicó y su historial con la moderación («18 verificadas · 0 rechazos previos»). */
data class ReviewAuthor(val author: Author, val verified: Int, val rejected: Int)

/**
 * Un lugar ya publicado al que se parece la pendiente (32, 33 y 33A), a [distanceMeters] de ella. Para comparar lado a
 * lado (33A) trae también quién lo publicó, cuándo y cuántas fotos tiene.
 */
data class DuplicateCandidate(
    val poi: Poi,
    val distanceMeters: Int,
    val author: Author? = null,
    val publishedAt: Instant? = null,
    val photoCount: Int = 0,
)

/** Posible duplicado (ADR-14): los lugares parecidos que el autor dijo que son otro (17B) y su nota, si dejó una. */
data class DuplicateSuspicion(val candidates: List<DuplicateCandidate>, val authorNote: String? = null)

/**
 * Una publicación que espera revisión, con todo lo que el moderador necesita para decidir en una pantalla (33):
 * fotos, texto, horario, precio, dirección y coordenadas, y el historial del autor.
 */
data class ReviewItem(
    val id: String,
    val title: String,
    val category: Category,
    /** «Categoría sugerida» (16) o «Elegida por el autor». */
    val categoryOrigin: CategoryOrigin,
    val description: String,
    val photos: List<PublishedPhoto>,
    val hours: OpeningHours?,
    val price: PriceRange?,
    /** Dirección legible; null si solo hay coordenadas. */
    val address: String?,
    val location: GeoPoint,
    val submittedAt: Instant,
    val author: ReviewAuthor,
    /** Motivo de un reporte de la comunidad («información desactualizada»); null si no la reportaron. */
    val reportReason: String? = null,
    val duplicate: DuplicateSuspicion? = null,
) {
    /** Días completos esperando: «Lleva 3 días». */
    fun waitingDays(now: Instant): Long = Duration.between(submittedAt, now).toDays()

    fun urgency(now: Instant): ReviewUrgency = when {
        waitingDays(now) >= URGENT_DAYS -> ReviewUrgency.HIGH
        waitingDays(now) >= 1 -> ReviewUrgency.MEDIUM
        else -> ReviewUrgency.NORMAL
    }

    private companion object {
        const val URGENT_DAYS = 3
    }
}

/** 32 · La cola, de la más antigua a la más reciente. [savedAt] llega con lo guardado sin conexión: solo lectura (32.c). */
data class ReviewQueue(val items: List<ReviewItem>, val savedAt: Instant? = null)

/** 37 · «Tu trabajo de hoy»: lo que decidió este moderador desde la medianoche. */
data class ModerationWork(val verified: Int, val rejected: Int, val finalized: Int)

/**
 * 35 · Rechazar: el motivo, el mensaje tal como lo escribió el moderador (vacío: el servidor pone el del motivo), si
 * quien la publicó puede corregirla y reenviarla y, con [RejectionReason.DUPLICATE], el id del lugar original.
 */
data class RejectDecision(
    val reason: RejectionReason,
    val message: String,
    val canResubmit: Boolean,
    val originalId: String? = null,
)

/** 36 · ¿Por qué se finaliza? Los tres motivos de 36.a. */
enum class FinalizeReason {
    /** «El lugar cerró de forma permanente». */
    CLOSED,

    /** «Fue un evento temporal que ya pasó». */
    EVENT_ENDED,

    /** «Se unificó con otra publicación». */
    MERGED,
}

/**
 * «Resueltas» (E1): una publicación ya decidida, con su estado de ahora (verificada, rechazada o finalizada), quién la
 * decidió y cuándo. [note] es la nota interna de 34, que solo ven los moderadores. Sin [authorName], la cuenta que la
 * publicó se eliminó.
 */
data class ResolvedPublication(
    val id: String,
    val title: String,
    val category: Category,
    val status: PublicationStatus,
    val authorName: String?,
    val decidedAt: Instant,
    val decidedBy: String,
    val note: String? = null,
    val rejectionReason: RejectionReason? = null,
    val finalizeReason: FinalizeReason? = null,
    val votes: Int = 0,
    val comments: Int = 0,
) {
    /** 36 · Las públicas cambian de estado; una rechazada la corrige quien la publicó. */
    val canChangeState: Boolean get() = status == PublicationStatus.VERIFIED || status == PublicationStatus.FINALIZED
}

/** Otra persona ya decidió esta publicación: ya no está en la cola (33). */
class AlreadyReviewedException : Exception("La publicación ya no está pendiente")

/** 36 · La publicación ya no está en el estado que se vio: otra persona la cambió o quien la publicó la editó. */
class StateChangedException : Exception("La publicación cambió de estado")
