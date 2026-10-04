package co.edu.uniquindio.exploracity.data.remote.dto

import co.edu.uniquindio.exploracity.data.repository.CommentsPage
import co.edu.uniquindio.exploracity.data.repository.FeedPage
import co.edu.uniquindio.exploracity.data.repository.MapArea
import co.edu.uniquindio.exploracity.domain.model.Category
import co.edu.uniquindio.exploracity.domain.model.Comment
import co.edu.uniquindio.exploracity.domain.model.Notification
import co.edu.uniquindio.exploracity.domain.model.OpeningHours
import co.edu.uniquindio.exploracity.domain.model.Poi
import co.edu.uniquindio.exploracity.domain.model.PoiDetails
import co.edu.uniquindio.exploracity.domain.model.PoiPhoto
import co.edu.uniquindio.exploracity.domain.model.PriceRange
import co.edu.uniquindio.exploracity.domain.model.PublicProfile
import co.edu.uniquindio.exploracity.domain.model.PublicationStatus
import co.edu.uniquindio.exploracity.domain.model.Residency
import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime

// DTO de Explorar, Social, Avisos y Perfil público (docs/api).

/** Un lugar público del feed, del mapa y del perfil público. */
@Serializable
data class PlaceDto(
    val id: String,
    val title: String,
    val category: Category,
    val status: PublicationStatus,
    val location: GeoPointDto,
    val distanceMeters: Int,
    val votes: Int,
    val comments: Int,
    val photo: String? = null,
    val price: PriceRange? = null,
    val openNow: Boolean? = null,
    val summary: String? = null,
) {
    fun toDomain() = Poi(
        id = id,
        title = title,
        category = category,
        status = status,
        location = location.toDomain(),
        distanceMeters = distanceMeters,
        votes = votes,
        comments = comments,
        photoUrl = photo,
        price = price,
        openNow = openNow,
        summary = summary,
    )
}

@Serializable
data class FeedPageDto(val items: List<PlaceDto>, val total: Int, val hasMore: Boolean) {
    fun toDomain() = FeedPage(items.map { it.toDomain() }, total, hasMore)
}

@Serializable
data class CountDto(val count: Int)

@Serializable
data class MapAreaDto(val items: List<PlaceDto>, val total: Int) {
    fun toDomain() = MapArea(items.map { it.toDomain() }, total)
}

@Serializable
data class HoursDto(val days: List<String>, val opens: String, val closes: String) {
    fun toDomain() = OpeningHours(days.map(DayOfWeek::valueOf).toSet(), LocalTime.parse(opens), LocalTime.parse(closes))
}

/** 13 · Sin [author], la cuenta que lo publicó se eliminó. */
@Serializable
data class PlaceDetailsDto(
    val place: PlaceDto,
    val description: String,
    val photos: List<String>,
    val address: String? = null,
    val hours: HoursDto? = null,
    val author: AuthorDto? = null,
    val voted: Boolean,
    val visited: Boolean,
) {
    fun toDomain() = PoiDetails(
        poi = place.toDomain(),
        description = description,
        // El formulario no pide descripción por foto: el lector oye «Mirador de la Secreta. Foto 1 de 3».
        photos = photos.map { PoiPhoto(it, place.title) },
        address = address.orEmpty(),
        hours = hours?.toDomain(),
        author = author?.toDomain(),
        voted = voted,
        visited = visited,
    )
}

@Serializable
data class VoteDto(val votes: Int)

@Serializable
data class VisitRequest(val recommends: Boolean?, val text: String?, val showName: Boolean)

@Serializable
data class VisitDto(val pointsAwarded: Int)

/** 14 · Sin [author], la cuenta que lo escribió se eliminó. */
@Serializable
data class CommentDto(
    val id: String,
    val author: AuthorDto? = null,
    val text: String,
    val createdAt: String,
    val mine: Boolean,
) {
    fun toDomain() = Comment(id, author?.toDomain(), text, Instant.parse(createdAt), mine)
}

@Serializable
data class CommentsPageDto(
    val placeTitle: String,
    val items: List<CommentDto>,
    val total: Int,
    val nextCursor: String? = null,
) {
    fun toDomain() = CommentsPage(placeTitle, items.map { it.toDomain() }, total, nextCursor)
}

@Serializable
data class CommentRequest(val text: String, val clientId: String)

/** 25 · Un aviso: cada tipo trae los campos de su frase. El tipo va como texto, así uno nuevo no rompe la lista. */
@Serializable
data class NotificationDto(
    val id: String,
    val type: String,
    val createdAt: String,
    val read: Boolean,
    val placeId: String? = null,
    val placeTitle: String? = null,
    val points: Int? = null,
    val reason: String? = null,
    val authorName: String? = null,
    val excerpt: String? = null,
    val existingPlaceId: String? = null,
    val existingTitle: String? = null,
    val achievement: String? = null,
    val nextBadge: String? = null,
    val remaining: Int? = null,
) {
    /** null si es de un tipo que la app no conoce o le falta un dato: no se muestra. */
    fun toDomain(): Notification? {
        val at = Instant.parse(createdAt)
        return when (type) {
            "VERIFIED" -> Notification.Verified(id, at, read, placeId ?: return null, placeTitle ?: return null, points ?: 0)
            "FINALIZED" -> Notification.Finalized(id, at, read, placeId ?: return null, placeTitle ?: return null, reason)
            "COMMENTED" -> Notification.Commented(
                id, at, read, placeId ?: return null, placeTitle ?: return null, authorName, excerpt ?: return null,
            )
            "REJECTED" -> Notification.Rejected(id, at, read, placeId ?: return null, placeTitle ?: return null, reason ?: return null)
            "DUPLICATE_REJECTED" -> Notification.DuplicateRejected(
                id, at, read, placeId ?: return null, placeTitle ?: return null, existingPlaceId ?: return null, existingTitle ?: return null,
            )
            "ACHIEVEMENT" -> Notification.Achievement(id, at, read, achievement ?: return null, nextBadge, remaining)
            else -> null
        }
    }
}

@Serializable
data class NotificationsDto(val items: List<NotificationDto>, val unread: Int)

/** 31 · Lo público de una persona. [badges] son las insignias desbloqueadas. */
@Serializable
data class PublicProfileDto(
    val author: AuthorDto,
    val residency: Residency,
    val city: String,
    val bio: String? = null,
    val photo: String? = null,
    val places: List<PlaceDto>,
    val badges: Int,
) {
    fun toDomain() = PublicProfile(author.toDomain(), residency, city, bio, places.map { it.toDomain() }, badges, photo)
}
