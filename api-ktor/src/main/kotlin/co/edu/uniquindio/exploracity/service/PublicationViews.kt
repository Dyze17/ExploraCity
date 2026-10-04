package co.edu.uniquindio.exploracity.service

import co.edu.uniquindio.exploracity.model.CandidateResponse
import co.edu.uniquindio.exploracity.model.DecisionAction
import co.edu.uniquindio.exploracity.model.GeoPoint
import co.edu.uniquindio.exploracity.model.HoursResponse
import co.edu.uniquindio.exploracity.model.PhotoResponse
import co.edu.uniquindio.exploracity.model.PublicationResponse
import co.edu.uniquindio.exploracity.model.PublicationStatus
import co.edu.uniquindio.exploracity.model.RejectionReason
import co.edu.uniquindio.exploracity.model.RejectionResponse
import co.edu.uniquindio.exploracity.model.ReviewAuthorResponse
import co.edu.uniquindio.exploracity.model.ReviewItemResponse
import co.edu.uniquindio.exploracity.model.SuspicionResponse
import co.edu.uniquindio.exploracity.model.iso
import co.edu.uniquindio.exploracity.repository.OwnPlaceRow
import co.edu.uniquindio.exploracity.repository.PlaceFields
import co.edu.uniquindio.exploracity.repository.PlaceRepository
import co.edu.uniquindio.exploracity.repository.PublicationRepository
import java.util.UUID

/**
 * Cómo se cuenta una publicación a quien la publicó (22–24) y a la moderación (33 y 33A). Se usa dentro de una
 * transacción.
 */
class PublicationViews(
    private val publications: PublicationRepository,
    private val places: PlaceRepository,
    private val cards: PlaceCards,
) {

    /** 22–24 · La publicación propia; una rechazada trae el motivo, qué corregir (E1) y, si fue duplicado, el original. */
    fun publicationOf(place: OwnPlaceRow): PublicationResponse {
        val photos = publications.photosOf(place.id)
        val location = place.location()
        return PublicationResponse(
            id = place.id.toString(),
            title = place.fields.title,
            category = place.fields.category,
            status = place.status,
            location = location,
            photos = photos.map { PhotoResponse(it.id.toString(), it.url) },
            submittedAt = place.submittedAt.iso(),
            description = place.fields.description,
            photo = photos.firstOrNull()?.url,
            hours = hoursOf(place.fields),
            price = place.fields.price,
            votes = publications.votes(place.id),
            comments = publications.comments(place.id),
            pointsEarned = place.pointsEarned,
            possibleDuplicate = place.possibleDuplicate,
            similarIds = publications.similarIds(place.id).map { it.toString() },
            duplicateNote = place.duplicateNote,
            rejection = if (place.status == PublicationStatus.REJECTED) rejectionOf(place) else null,
        )
    }

    /** 33 · La pendiente con el historial de su autor y, si es posible duplicado, los lugares con que se compara (33A). */
    fun reviewItemOf(place: OwnPlaceRow): ReviewItemResponse {
        val (verified, rejected) = place.authorId?.let { publications.authorHistory(it) } ?: (0 to 0)
        val author = checkNotNull(places.author(place.authorId)) { "Una pendiente siempre tiene autor" }
        return ReviewItemResponse(
            id = place.id.toString(),
            title = place.fields.title,
            category = place.fields.category,
            categoryOrigin = place.categoryOrigin,
            description = place.fields.description,
            photos = publications.photosOf(place.id).map { PhotoResponse(it.id.toString(), it.url) },
            hours = hoursOf(place.fields),
            price = place.fields.price,
            address = place.fields.address,
            location = place.location(),
            submittedAt = place.submittedAt.iso(),
            author = ReviewAuthorResponse(PlaceCards.authorOf(author), verified, rejected),
            duplicate = if (place.possibleDuplicate) {
                SuspicionResponse(candidatesOf(publications.similarIds(place.id), place.location()), place.duplicateNote)
            } else {
                null
            },
        )
    }

    /** 33A · Los lugares publicados de [ids] vistos desde [from]: quién los publicó, cuándo y cuántas fotos tienen. */
    fun candidatesOf(ids: Collection<UUID>, from: GeoPoint): List<CandidateResponse> =
        places.findPublic(ids, from).map { row ->
            CandidateResponse(
                place = cards.summaryOf(row),
                distanceMeters = row.distanceMeters,
                author = places.author(row.authorId)?.let(PlaceCards::authorOf),
                publishedAt = publications.firstVerifiedAt(row.id)?.iso(),
                photoCount = places.photos(row.id).size,
            )
        }

    fun hoursOf(fields: PlaceFields): HoursResponse? {
        val bits = fields.hoursDays ?: return null
        val opens = fields.opens ?: return null
        val closes = fields.closes ?: return null
        return HoursResponse(PlaceCards.days(bits).map { it.name }, opens.toString(), closes.toString())
    }

    private fun rejectionOf(place: OwnPlaceRow): RejectionResponse? {
        val decision = publications.latestDecision(place.id)?.takeIf { it.action == DecisionAction.REJECTED } ?: return null
        val reason = decision.rejectionReason ?: return null
        return RejectionResponse(
            reason = reason,
            message = decision.rejectionMessage ?: ModerationTexts.defaultMessage(reason),
            reviewerName = decision.moderatorName ?: ModerationTexts.FORMER_MODERATOR,
            rejectedAt = decision.createdAt.iso(),
            // Un duplicado no se corrige: el lugar ya existe.
            canResubmit = decision.canResubmit == true && reason != RejectionReason.DUPLICATE,
            fixes = ModerationTexts.fixes(reason),
            duplicateOf = decision.duplicateOf?.let { places.find(it, place.location()) }?.let(cards::summaryOf),
        )
    }

    private fun OwnPlaceRow.location() = GeoPoint(fields.latitude, fields.longitude)
}
