package co.edu.uniquindio.exploracity.model

// Los valores son los mismos de la app (domain/model) y de las restricciones CHECK del esquema.

enum class Role { USER, MODERATOR }

/** «De visita» o «Residente» (4 y 28). */
enum class Residency { RESIDENT, VISITOR }

enum class Category { GASTRONOMY, CULTURE, NATURE, ENTERTAINMENT, HISTORY }

/** 16 · Si la categoría fue la sugerida por la IA o la eligió quien publica. */
enum class CategoryOrigin { SUGGESTED, CHOSEN }

enum class PublicationStatus { PENDING, VERIFIED, REJECTED, FINALIZED }

/** 18 · Rango de precio. */
enum class PriceRange { FREE, LOW, MEDIUM, HIGH }

/** Para qué sirve un enlace enviado por correo, y su ruta: `…/enlace/restablecer?token=…`. */
enum class LinkPurpose(val path: String) {
    PASSWORD_RESET("restablecer"),
    EMAIL_CHANGE("confirmar-correo"),
}

/** Lo que hizo el moderador (34, 35 y 36). */
enum class DecisionAction { VERIFIED, REJECTED, FINALIZED, REOPENED }

/** 35 · Motivos de rechazo, con el duplicado primero. */
enum class RejectionReason { DUPLICATE, PHOTO, LOCATION, INAPPROPRIATE, OTHER }

/** 36 · Motivos de finalizar. */
enum class FinalizeReason { CLOSED, EVENT_ENDED, MERGED }

/** 24.a · Qué corregir antes de reenviar, con el paso del formulario donde se corrige (como FixKind de la app). */
enum class FixKind { TITLE, DESCRIPTION, CATEGORY, LOCATION, SCHEDULE, PRICE, PHOTOS }

/** 27 · Qué cuenta el avance de una insignia. */
enum class BadgeMetric { PUBLICATIONS, VERIFIED_PLACES, CATEGORY_PLACES, COMMENTS, VISITS, VOTES_RECEIVED }

/** 25 · Tipos de aviso. v1.4.0 · PHOTO_APPROVED y PHOTO_REJECTED: la revisión de una foto agregada (V15). */
enum class NotificationType { VERIFIED, FINALIZED, COMMENTED, REJECTED, DUPLICATE_REJECTED, ACHIEVEMENT, PHOTO_APPROVED, PHOTO_REJECTED }

/** 31A · Motivos de reporte de un perfil. */
enum class ReportReason { IMPERSONATION, INAPPROPRIATE_CONTENT, SPAM }

/** v1.4.0 · Por qué no se publica una foto agregada a un lugar. */
enum class PhotoRejectionReason { NOT_THE_PLACE, INAPPROPRIATE, LOW_QUALITY }

/** v1.4.0 · Motivos de reporte de un lugar (13). Con [OTHER], el detalle es obligatorio. */
enum class PlaceReportReason { CLOSED, WRONG_LOCATION, OUTDATED_INFO, BAD_PHOTOS, INAPPROPRIATE, DUPLICATE, OTHER }

/**
 * v1.4.0 · Cómo se cerró un reporte de lugar: se descartó en «Reportados», el lugar pasó a finalizada (36) o se decidió
 * en la revisión (33) después de volver a pendiente.
 */
enum class ReportResolution { DISMISSED, FINALIZED, REVIEWED }

/** Niveles de reputación según los puntos (G2), los mismos de la app (UserLevel). */
enum class UserLevel(val minPoints: Int) {
    NEWCOMER(0),
    EXPLORER(100),
    ADVENTURER(250),
    LOCAL_AMBASSADOR(500),
    ;

    companion object {
        fun fromPoints(points: Int): UserLevel = entries.last { points >= it.minPoints }
    }
}
