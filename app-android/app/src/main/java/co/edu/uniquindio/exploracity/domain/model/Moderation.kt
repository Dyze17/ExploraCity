package co.edu.uniquindio.exploracity.domain.model

import java.time.Duration
import java.time.Instant

/** 32 · Urgencia según lo que lleva esperando: rojo desde 3 días, ámbar desde 1 día, neutra el resto (README · State Management). */
enum class ReviewUrgency { HIGH, MEDIUM, NORMAL }

/** 33 · Quién publicó y su historial con la moderación («18 verificadas · 0 rechazos previos»). */
data class ReviewAuthor(val author: Author, val verified: Int, val rejected: Int)

/** Un lugar ya publicado al que se parece la pendiente (32, 33 y 33A), a [distanceMeters] de ella. */
data class DuplicateCandidate(val poi: Poi, val distanceMeters: Int)

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

/** Otra persona ya decidió esta publicación: ya no está en la cola (33). */
class AlreadyReviewedException : Exception("La publicación ya no está pendiente")
