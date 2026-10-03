package co.edu.uniquindio.exploracity.data.remote.dto

import co.edu.uniquindio.exploracity.domain.model.Author
import co.edu.uniquindio.exploracity.domain.model.Badge
import co.edu.uniquindio.exploracity.domain.model.BadgeMetric
import co.edu.uniquindio.exploracity.domain.model.Category
import co.edu.uniquindio.exploracity.domain.model.OwnProfile
import co.edu.uniquindio.exploracity.domain.model.PublicationCounts
import co.edu.uniquindio.exploracity.domain.model.ReportReason
import co.edu.uniquindio.exploracity.domain.model.Residency
import kotlinx.serialization.Serializable
import java.time.YearMonth

// DTO de Perfil (docs/api).

@Serializable
data class AuthorDto(val id: String, val name: String, val points: Int) {
    fun toDomain() = Author(id, name, points)
}

@Serializable
data class PublicationCountsDto(val pending: Int, val verified: Int, val rejected: Int, val finalized: Int) {
    fun toDomain() = PublicationCounts(pending, verified, rejected, finalized)
}

@Serializable
data class BadgeDto(
    val id: String,
    val name: String,
    val metric: BadgeMetric,
    val category: Category? = null,
    val progress: Int,
    val target: Int,
    val howTo: String,
    val tip: String? = null,
) {
    fun toDomain() = Badge(id, name, metric, progress, target, howTo, tip, category)
}

/** 26 · El perfil propio. [memberSince] es el mes en que se creó la cuenta («2026-03»). */
@Serializable
data class OwnProfileDto(
    val author: AuthorDto,
    val residency: Residency,
    val city: String,
    val memberSince: String,
    val publications: PublicationCountsDto,
    val badges: List<BadgeDto>,
    val bio: String? = null,
    val photo: String? = null,
) {
    fun toDomain() = OwnProfile(
        author = author.toDomain(),
        residency = residency,
        city = city,
        memberSince = YearMonth.parse(memberSince),
        publications = publications.toDomain(),
        badges = badges.map { it.toDomain() },
        bio = bio,
        photo = photo,
    )
}

/** 28 · Lo que se edita del perfil; la foto va aparte. */
@Serializable
data class ProfileUpdateRequest(val name: String, val bio: String?, val residency: Residency)

@Serializable
data class ReportRequest(val reason: ReportReason)
