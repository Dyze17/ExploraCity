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
    val photoUrl: String? = null,
    /** Solo en las rechazadas. */
    val rejection: Rejection? = null,
)
