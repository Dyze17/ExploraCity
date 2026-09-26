package co.edu.uniquindio.exploracity.domain.model

import java.time.Instant

/** Motivos de rechazo del moderador (35.a): la lista del lienzo más «Otro motivo». */
enum class RejectionReason {
    /** «La foto no permite reconocer el lugar». */
    PHOTO,

    /** «La ubicación no corresponde». */
    LOCATION,

    /** «El lugar ya está publicado»: la 24 cambia a la variante de duplicado. */
    DUPLICATE,

    /** «Contenido inapropiado o publicidad». */
    INAPPROPRIATE,

    OTHER,
}

/**
 * Qué hay que corregir (24.a · «Qué revisar antes de reenviar»), con el paso del formulario donde se corrige (15–19). El
 * texto lo manda el servidor; la app solo sabe el icono y el paso.
 */
enum class FixKind(val step: Int) {
    TITLE(1),
    DESCRIPTION(1),
    CATEGORY(2),
    LOCATION(3),
    SCHEDULE(4),
    PRICE(4),
    PHOTOS(5),
}

data class RequiredFix(val kind: FixKind, val text: String)

/**
 * Decisión del moderador sobre una publicación. [message] llega tal como lo escribió («Detalle para el autor», 35.a);
 * [canResubmit] es la casilla «Permitir que el autor corrija y reenvíe». Con [RejectionReason.DUPLICATE], [duplicateOf]
 * es el lugar que ya existía.
 */
data class Rejection(
    val reason: RejectionReason,
    val message: String,
    val reviewerName: String,
    val rejectedAt: Instant,
    val canResubmit: Boolean,
    val fixes: List<RequiredFix> = emptyList(),
    val duplicateOf: Poi? = null,
) {
    /**
     * Paso en que abre «Corregir y reenviar»: el primero del formulario que haya que corregir (con foto y ubicación,
     * el 3). Sin correcciones, el paso 1, como dice la tabla de interacciones de 24.
     */
    val firstStepToFix: Int get() = fixes.minOfOrNull { it.kind.step } ?: 1
}

/** Una publicación propia tal como la ve su autor (22–24): también pendiente o rechazada, que el feed no muestra. */
data class OwnPublication(
    val id: String,
    val title: String,
    val category: Category,
    val status: PublicationStatus,
    val location: GeoPoint,
    val photos: Int,
    val submittedAt: Instant,
    val description: String = "",
    val photoUrl: String? = null,
    /** Votos y comentarios: solo las públicas (verificadas y finalizadas) los reciben. */
    val votes: Int = 0,
    val comments: Int = 0,
    /** Puntos que ganó con ella (verificada +15, primera publicación +20): se descuentan si la elimina (23). */
    val pointsEarned: Int = 0,
    /** Pendiente que el autor confirmó como distinta de un lugar cercano: el moderador la revisa como posible duplicado (ADR-14). */
    val possibleDuplicate: Boolean = false,
    /** Solo en las rechazadas. */
    val rejection: Rejection? = null,
) {
    /** Visible en el feed: tiene detalle (13) y comentarios. */
    val isPublic: Boolean get() = status == PublicationStatus.VERIFIED || status == PublicationStatus.FINALIZED
}

/** Reglas del paso 1 (15) que valen también al editar (23): título 5–60 y descripción 30–600 caracteres. */
object PublicationLimits {
    const val TITLE_MIN = 5
    const val TITLE_MAX = 60
    const val DESCRIPTION_MIN = 30
    const val DESCRIPTION_MAX = 600
}

/**
 * 23 · Lo que se puede cambiar de una publicación ya enviada. Los espacios de los extremos no cuentan: un título de
 * cinco espacios no es un título.
 */
data class PublicationChanges(val title: String, val category: Category, val description: String) {
    val titleLength: Int get() = title.trim().length

    val descriptionLength: Int get() = description.trim().length

    /** Caracteres que faltan para el mínimo (0 si ya lo cumple): «El título necesita al menos 5. Van 4». */
    val titleMissing: Int get() = (PublicationLimits.TITLE_MIN - titleLength).coerceAtLeast(0)

    val descriptionMissing: Int get() = (PublicationLimits.DESCRIPTION_MIN - descriptionLength).coerceAtLeast(0)

    val isValid: Boolean get() = titleMissing == 0 && descriptionMissing == 0

    /** Sin los espacios de los extremos, como se guarda. */
    fun trimmed(): PublicationChanges = copy(title = title.trim(), description = description.trim())

    companion object {
        fun of(publication: OwnPublication) = PublicationChanges(publication.title, publication.category, publication.description)
    }
}
