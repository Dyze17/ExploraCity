package co.edu.uniquindio.exploracity.core.data.remote.dto

import co.edu.uniquindio.exploracity.domain.model.Category
import co.edu.uniquindio.exploracity.domain.model.Comment
import co.edu.uniquindio.exploracity.domain.model.FinalizeReason
import co.edu.uniquindio.exploracity.domain.model.HoursBlock
import co.edu.uniquindio.exploracity.domain.model.Municipality
import co.edu.uniquindio.exploracity.domain.model.Notification
import co.edu.uniquindio.exploracity.domain.model.OpeningHours
import co.edu.uniquindio.exploracity.domain.model.Poi
import co.edu.uniquindio.exploracity.domain.model.PoiDetails
import co.edu.uniquindio.exploracity.domain.model.PoiPhoto
import co.edu.uniquindio.exploracity.domain.model.PriceRange
import co.edu.uniquindio.exploracity.domain.model.PublicProfile
import co.edu.uniquindio.exploracity.domain.model.PublicationStatus
import co.edu.uniquindio.exploracity.domain.model.RejectionReason
import co.edu.uniquindio.exploracity.domain.model.Residency
import co.edu.uniquindio.exploracity.domain.model.TownOption
import co.edu.uniquindio.exploracity.domain.repository.CommentsPage
import co.edu.uniquindio.exploracity.domain.repository.FeedPage
import co.edu.uniquindio.exploracity.domain.repository.MapArea
import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.util.Locale

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

/** v1.3.0 · GET /v1/places/towns: una ciudad con lugares, para elegirla a mano. */
@Serializable
data class TownDto(val name: String, val region: String? = null, val places: Int, val center: GeoPointDto) {
    fun toDomain() = TownOption(Municipality(name, region), center.toDomain(), places = places)
}

@Serializable
data class TownsDto(val items: List<TownDto>)

@Serializable
data class MapAreaDto(val items: List<PlaceDto>, val total: Int) {
    fun toDomain() = MapArea(items.map { it.toDomain() }, total)
}

/** 18 · Un tramo del horario. Si [closes] no es posterior a [opens], cierra al día siguiente. */
@Serializable
data class HoursDto(val days: List<String>, val opens: String, val closes: String) {
    fun toDomain() = HoursBlock(days.map(DayOfWeek::valueOf).toSet(), LocalTime.parse(opens), LocalTime.parse(closes))

    companion object {
        /** Los días de lunes a domingo y las horas como «08:00». */
        fun of(block: HoursBlock) = HoursDto(block.days.sorted().map { it.name }, block.opens.hourMinute(), block.closes.hourMinute())

        /**
         * v1.3.0 · El horario de una respuesta: siempre abierto, todos los tramos de [schedule] o, sin él, el único de
         * [hours] (la API de antes).
         */
        fun scheduleOf(hours: HoursDto?, schedule: List<HoursDto>?, alwaysOpen: Boolean): OpeningHours? = when {
            alwaysOpen -> OpeningHours.AlwaysOpen
            !schedule.isNullOrEmpty() -> OpeningHours.Blocks(schedule.map { it.toDomain() })
            hours != null -> OpeningHours.Blocks(listOf(hours.toDomain()))
            else -> null
        }

        /** v1.3.0 · Los tramos que se envían; null sin horario o siempre abierto. */
        fun blocksOf(hours: OpeningHours?): List<HoursDto>? = (hours as? OpeningHours.Blocks)?.blocks?.map(::of)

        private fun LocalTime.hourMinute() = "%02d:%02d".format(Locale.ROOT, hour, minute)
    }
}

/** 13 · Sin [author], la cuenta que lo publicó se eliminó. */
@Serializable
data class PlaceDetailsDto(
    val place: PlaceDto,
    val description: String,
    val photos: List<String>,
    val address: String? = null,
    /** El primer tramo, para la app 1.2; con [schedule] no se lee. */
    val hours: HoursDto? = null,
    /** v1.3.0 · Todos los tramos, en orden. */
    val schedule: List<HoursDto>? = null,
    val alwaysOpen: Boolean = false,
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
        hours = HoursDto.scheduleOf(hours, schedule, alwaysOpen),
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
    /** v1.2.0 · Los códigos de lo de arriba, como texto: uno nuevo no rompe la lista. */
    val achievementId: String? = null,
    val nextBadgeId: String? = null,
    val rejectionReason: String? = null,
    val finalizeReason: String? = null,
) {
    /** null si es de un tipo que la app no conoce o le falta un dato: no se muestra. */
    fun toDomain(): Notification? {
        val at = Instant.parse(createdAt)
        return when (type) {
            "VERIFIED" -> Notification.Verified(id, at, read, placeId ?: return null, placeTitle ?: return null, points ?: 0)
            "FINALIZED" -> Notification.Finalized(
                id, at, read, placeId ?: return null, placeTitle ?: return null, reason,
                FinalizeReason.entries.firstOrNull { it.name == finalizeReason },
            )
            "COMMENTED" -> Notification.Commented(
                id, at, read, placeId ?: return null, placeTitle ?: return null, authorName, excerpt ?: return null,
            )
            "REJECTED" -> Notification.Rejected(
                id, at, read, placeId ?: return null, placeTitle ?: return null, reason ?: return null,
                RejectionReason.entries.firstOrNull { it.name == rejectionReason },
            )
            "DUPLICATE_REJECTED" -> Notification.DuplicateRejected(
                id, at, read, placeId ?: return null, placeTitle ?: return null, existingPlaceId ?: return null, existingTitle ?: return null,
            )
            "ACHIEVEMENT" -> Notification.Achievement(id, at, read, achievement ?: return null, nextBadge, remaining, achievementId, nextBadgeId)
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
    /** v1.3.0 · La ciudad de la persona; no viene si no tiene. */
    val town: TownRefDto? = null,
    val bio: String? = null,
    val photo: String? = null,
    val places: List<PlaceDto>,
    val badges: Int,
) {
    fun toDomain() = PublicProfile(author.toDomain(), residency, town?.toDomain(), bio, places.map { it.toDomain() }, badges, photo)
}
