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
    /** La ciudad de la persona o, si no tiene, la de la configuración: la que lee la app 1.2. La de ahora usa [town]. */
    val city: String,
    /** v1.3.0 · La ciudad de la persona; no viene si todavía no se sabe. */
    val town: TownRef? = null,
    val memberSince: String,
    val publications: PublicationCountsResponse,
    val badges: List<BadgeResponse>,
    val bio: String? = null,
    val photo: String? = null,
)

/** v1.3.0 · Una ciudad y su departamento, como los nombra el Geocoder del teléfono. */
@Serializable
data class TownRef(val name: String, val region: String? = null)

/**
 * 28 · Lo que se puede editar del perfil; la foto va aparte. «Sobre mí» vacío se guarda como null. Con [townChanged], la
 * ciudad pasa a ser [town] (null la quita); sin él, como en la app 1.2, se queda la que tenía.
 */
@Serializable
data class ProfileUpdateRequest(
    val name: String,
    val bio: String? = null,
    val residency: Residency,
    val town: TownRef? = null,
    val townChanged: Boolean = false,
)

/** 31A · El motivo del reporte. */
@Serializable
data class ReportRequest(val reason: ReportReason)
