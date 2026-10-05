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

/** Para qué sirve un enlace enviado por correo. */
enum class LinkPurpose { PASSWORD_RESET, EMAIL_CHANGE }

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

/** 25 · Tipos de aviso. */
enum class NotificationType { VERIFIED, FINALIZED, COMMENTED, REJECTED, DUPLICATE_REJECTED, ACHIEVEMENT }

/** 31A · Motivos de reporte de un perfil. */
enum class ReportReason { IMPERSONATION, INAPPROPRIATE_CONTENT, SPAM }

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
