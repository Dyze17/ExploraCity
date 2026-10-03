package co.edu.uniquindio.exploracity.model

import kotlinx.serialization.Serializable

// Cuerpos de Perfil (docs/api).

/** Quien firma un lugar o un perfil: el nivel lo calcula la app con los puntos. */
@Serializable
data class AuthorResponse(val id: String, val name: String, val points: Int)

/** 26 · Publicaciones propias en cada estado. */
@Serializable
data class PublicationCountsResponse(val pending: Int, val verified: Int, val rejected: Int, val finalized: Int)

/** 27 · Una insignia del catálogo con el avance de la persona, que nunca pasa de la meta. */
@Serializable
data class BadgeResponse(
    val id: String,
    val name: String,
    val metric: BadgeMetric,
    val category: Category? = null,
    val progress: Int,
    val target: Int,
    val howTo: String,
    val tip: String? = null,
)

/** 26 · El perfil propio. [memberSince] es el mes en que se creó la cuenta (2026-03). */
@Serializable
data class ProfileResponse(
    val author: AuthorResponse,
    val residency: Residency,
    val city: String,
    val memberSince: String,
    val publications: PublicationCountsResponse,
    val badges: List<BadgeResponse>,
    val bio: String? = null,
    val photo: String? = null,
)

/** 28 · Lo que se puede editar del perfil; la foto va aparte. «Sobre mí» vacío se guarda como null. */
@Serializable
data class ProfileUpdateRequest(val name: String, val bio: String? = null, val residency: Residency)

/** 31A · El motivo del reporte. */
@Serializable
data class ReportRequest(val reason: ReportReason)
