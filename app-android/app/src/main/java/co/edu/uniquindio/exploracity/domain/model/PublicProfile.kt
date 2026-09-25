package co.edu.uniquindio.exploracity.domain.model

/** SAD: el usuario es turista o residente. «De visita» evita chocar con «Turista», el primer nivel. */
enum class Residency { RESIDENT, VISITOR }

/** 31A · Motivos de reporte de un perfil. */
enum class ReportReason { IMPERSONATION, INAPPROPRIATE_CONTENT, SPAM }

/**
 * 31 · Lo que cualquiera puede ver de una persona: sin correo ni datos de contacto, y de sus lugares solo los
 * verificados y finalizados (nunca pendientes ni rechazados).
 */
data class PublicProfile(
    val author: Author,
    val residency: Residency,
    val city: String,
    val bio: String?,
    val places: List<Poi>,
    val badges: Int,
) {
    val verifiedCount: Int get() = places.count { it.status == PublicationStatus.VERIFIED }
    val finalizedCount: Int get() = places.count { it.status == PublicationStatus.FINALIZED }
}
