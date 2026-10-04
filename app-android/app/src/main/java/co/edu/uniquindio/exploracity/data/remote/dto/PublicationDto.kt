package co.edu.uniquindio.exploracity.data.remote.dto

import co.edu.uniquindio.exploracity.data.repository.ModerationSummary
import co.edu.uniquindio.exploracity.domain.model.Category
import co.edu.uniquindio.exploracity.domain.model.CategoryOrigin
import co.edu.uniquindio.exploracity.domain.model.DuplicateCandidate
import co.edu.uniquindio.exploracity.domain.model.DuplicateCheck
import co.edu.uniquindio.exploracity.domain.model.DuplicateSuspicion
import co.edu.uniquindio.exploracity.domain.model.FinalizeReason
import co.edu.uniquindio.exploracity.domain.model.FixKind
import co.edu.uniquindio.exploracity.domain.model.ModerationWork
import co.edu.uniquindio.exploracity.domain.model.OwnPublication
import co.edu.uniquindio.exploracity.domain.model.PriceRange
import co.edu.uniquindio.exploracity.domain.model.PublicationChanges
import co.edu.uniquindio.exploracity.domain.model.PublicationStatus
import co.edu.uniquindio.exploracity.domain.model.PublicationSubmission
import co.edu.uniquindio.exploracity.domain.model.PublishedPhoto
import co.edu.uniquindio.exploracity.domain.model.RejectDecision
import co.edu.uniquindio.exploracity.domain.model.Rejection
import co.edu.uniquindio.exploracity.domain.model.RejectionReason
import co.edu.uniquindio.exploracity.domain.model.RequiredFix
import co.edu.uniquindio.exploracity.domain.model.ResolvedPublication
import co.edu.uniquindio.exploracity.domain.model.ReviewAuthor
import co.edu.uniquindio.exploracity.domain.model.ReviewItem
import co.edu.uniquindio.exploracity.domain.model.SimilarPlace
import co.edu.uniquindio.exploracity.domain.model.SubmitResult
import kotlinx.serialization.Serializable
import java.time.Instant

// DTO de Publicar y Moderar (docs/api). Las peticiones no tienen valores por defecto: así viaja todo, también lo que
// coincide con el valor por defecto de la API.

/** 19 · Una foto subida o ya publicada. */
@Serializable
data class PhotoDto(val id: String, val url: String) {
    fun toDomain() = PublishedPhoto(id, url)
}

@Serializable
data class SuggestionRequest(val title: String, val description: String)

/** 16 · Sin [category], el modelo no vio una clara. */
@Serializable
data class SuggestionDto(val category: Category? = null)

/** 17A · Un lugar parecido al que se publica, a [distanceMeters] del pin. */
@Serializable
data class SimilarPlaceDto(
    val id: String,
    val title: String,
    val category: Category,
    val status: PublicationStatus,
    val location: GeoPointDto,
    val distanceMeters: Int,
    val photo: String? = null,
) {
    fun toDomain() = SimilarPlace(id, title, category, status, location.toDomain(), distanceMeters, photo)
}

/** 17 · La búsqueda de parecidos que se hizo en el teléfono. La nota vacía no viaja. */
@Serializable
data class DuplicateCheckRequest(val location: GeoPointDto, val similarIds: List<String>, val note: String?, val failed: Boolean) {
    companion object {
        fun of(check: DuplicateCheck) =
            DuplicateCheckRequest(GeoPointDto.of(check.location), check.similarIds, check.note.trim().ifEmpty { null }, check.failed)
    }
}

/** 20 · [photos] son las direcciones de las fotos ya subidas, en orden: la primera es la portada. */
@Serializable
data class SubmissionRequest(
    val clientId: String,
    val title: String,
    val description: String,
    val category: Category,
    val categoryOrigin: CategoryOrigin,
    val location: GeoPointDto,
    val address: String?,
    val hours: HoursDto?,
    val price: PriceRange?,
    val photos: List<String>,
    val duplicateCheck: DuplicateCheckRequest?,
    val resubmitId: String?,
) {
    companion object {
        fun of(submission: PublicationSubmission) = SubmissionRequest(
            clientId = submission.clientId,
            title = submission.title,
            description = submission.description,
            category = submission.category,
            categoryOrigin = submission.categoryOrigin,
            location = GeoPointDto.of(submission.location),
            address = submission.address,
            hours = submission.hours?.let(HoursDto::of),
            price = submission.price,
            photos = submission.photos.mapNotNull { it.remoteUrl },
            duplicateCheck = submission.duplicateCheck?.let(DuplicateCheckRequest::of),
            resubmitId = submission.resubmitId,
        )
    }
}

@Serializable
data class SubmitDto(val publicationId: String, val firstPublicationPoints: Int? = null) {
    fun toDomain() = SubmitResult(publicationId, firstPublicationPoints)
}

/** 23 · Lo editable. Con el pin en su sitio la dirección no viaja: la API conserva la que tenía. */
@Serializable
data class ChangesRequest(
    val title: String,
    val category: Category,
    val description: String,
    val location: GeoPointDto,
    val address: String?,
    val hours: HoursDto?,
    val price: PriceRange?,
    val photos: List<String>,
    val duplicateCheck: DuplicateCheckRequest?,
) {
    companion object {
        fun of(changes: PublicationChanges): ChangesRequest {
            val clean = changes.trimmed()
            return ChangesRequest(
                title = clean.title,
                category = clean.category,
                description = clean.description,
                location = GeoPointDto.of(clean.location),
                address = clean.address,
                hours = clean.openingHours?.let(HoursDto::of),
                price = clean.price,
                photos = clean.photos.mapNotNull { it.remoteUrl },
                duplicateCheck = clean.duplicateCheck?.let(DuplicateCheckRequest::of),
            )
        }
    }
}

@Serializable
data class PhotoUrlRequest(val url: String)

@Serializable
data class FixDto(val kind: FixKind, val text: String) {
    fun toDomain() = RequiredFix(kind, text)
}

/** 24 · Con DUPLICATE, [duplicateOf] es el lugar que ya existía. */
@Serializable
data class RejectionDto(
    val reason: RejectionReason,
    val message: String,
    val reviewerName: String,
    val rejectedAt: String,
    val canResubmit: Boolean,
    val fixes: List<FixDto> = emptyList(),
    val duplicateOf: PlaceDto? = null,
) {
    fun toDomain() = Rejection(
        reason = reason,
        message = message,
        reviewerName = reviewerName,
        rejectedAt = Instant.parse(rejectedAt),
        canResubmit = canResubmit,
        fixes = fixes.map { it.toDomain() },
        duplicateOf = duplicateOf?.toDomain(),
    )
}

/** 22–24 · Una publicación propia en cualquier estado. */
@Serializable
data class PublicationDto(
    val id: String,
    val title: String,
    val category: Category,
    val status: PublicationStatus,
    val location: GeoPointDto,
    val photos: List<PhotoDto>,
    val submittedAt: String,
    val description: String,
    val photo: String? = null,
    val hours: HoursDto? = null,
    val price: PriceRange? = null,
    val votes: Int,
    val comments: Int,
    val pointsEarned: Int,
    val possibleDuplicate: Boolean,
    val similarIds: List<String> = emptyList(),
    val duplicateNote: String? = null,
    val rejection: RejectionDto? = null,
) {
    fun toDomain() = OwnPublication(
        id = id,
        title = title,
        category = category,
        status = status,
        location = location.toDomain(),
        photos = photos.map { it.toDomain() },
        submittedAt = Instant.parse(submittedAt),
        description = description,
        photoUrl = photo,
        hours = hours?.toDomain(),
        price = price,
        votes = votes,
        comments = comments,
        pointsEarned = pointsEarned,
        possibleDuplicate = possibleDuplicate,
        similarIds = similarIds,
        duplicateNote = duplicateNote,
        rejection = rejection?.toDomain(),
    )
}

@Serializable
data class ModerationSummaryDto(val pending: Int, val oldestWaitingDays: Int) {
    fun toDomain() = ModerationSummary(pending, oldestWaitingDays)
}

@Serializable
data class ReviewAuthorDto(val author: AuthorDto, val verified: Int, val rejected: Int) {
    fun toDomain() = ReviewAuthor(author.toDomain(), verified, rejected)
}

/** 33A · Sin [author], la cuenta que publicó el lugar se eliminó. */
@Serializable
data class CandidateDto(
    val place: PlaceDto,
    val distanceMeters: Int,
    val author: AuthorDto? = null,
    val publishedAt: String? = null,
    val photoCount: Int,
) {
    fun toDomain() = DuplicateCandidate(place.toDomain(), distanceMeters, author?.toDomain(), publishedAt?.let(Instant::parse), photoCount)
}

@Serializable
data class SuspicionDto(val candidates: List<CandidateDto>, val authorNote: String? = null) {
    fun toDomain() = DuplicateSuspicion(candidates.map { it.toDomain() }, authorNote)
}

/** 33 · Una pendiente con todo lo que hace falta para decidir. */
@Serializable
data class ReviewItemDto(
    val id: String,
    val title: String,
    val category: Category,
    val categoryOrigin: CategoryOrigin,
    val description: String,
    val photos: List<PhotoDto>,
    val hours: HoursDto? = null,
    val price: PriceRange? = null,
    val address: String? = null,
    val location: GeoPointDto,
    val submittedAt: String,
    val author: ReviewAuthorDto,
    val reportReason: String? = null,
    val duplicate: SuspicionDto? = null,
) {
    fun toDomain() = ReviewItem(
        id = id,
        title = title,
        category = category,
        categoryOrigin = categoryOrigin,
        description = description,
        photos = photos.map { it.toDomain() },
        hours = hours?.toDomain(),
        price = price,
        address = address,
        location = location.toDomain(),
        submittedAt = Instant.parse(submittedAt),
        author = author.toDomain(),
        reportReason = reportReason,
        duplicate = duplicate?.toDomain(),
    )
}

@Serializable
data class ReviewQueueDto(val items: List<ReviewItemDto>)

@Serializable
data class VerifyRequest(val note: String?)

@Serializable
data class RejectRequest(val reason: RejectionReason, val message: String, val canResubmit: Boolean, val originalId: String?) {
    companion object {
        fun of(decision: RejectDecision) = RejectRequest(decision.reason, decision.message.trim(), decision.canResubmit, decision.originalId)
    }
}

@Serializable
data class FinalizeRequest(val reason: FinalizeReason)

@Serializable
data class ReopenRequest(val reason: String)

@Serializable
data class ModerationWorkDto(val verified: Int, val rejected: Int, val finalized: Int) {
    fun toDomain() = ModerationWork(verified, rejected, finalized)
}

/** «Resueltas» · Sin [authorName], la cuenta que la publicó se eliminó. */
@Serializable
data class ResolvedDto(
    val id: String,
    val title: String,
    val category: Category,
    val status: PublicationStatus,
    val authorName: String? = null,
    val decidedAt: String,
    val decidedBy: String,
    val note: String? = null,
    val rejectionReason: RejectionReason? = null,
    val finalizeReason: FinalizeReason? = null,
    val votes: Int,
    val comments: Int,
) {
    fun toDomain() = ResolvedPublication(
        id = id,
        title = title,
        category = category,
        status = status,
        authorName = authorName,
        decidedAt = Instant.parse(decidedAt),
        decidedBy = decidedBy,
        note = note,
        rejectionReason = rejectionReason,
        finalizeReason = finalizeReason,
        votes = votes,
        comments = comments,
    )
}
