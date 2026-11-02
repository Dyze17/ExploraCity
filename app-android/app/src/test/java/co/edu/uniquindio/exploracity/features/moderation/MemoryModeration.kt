package co.edu.uniquindio.exploracity.features.moderation

import co.edu.uniquindio.exploracity.domain.model.Author
import co.edu.uniquindio.exploracity.domain.model.Category
import co.edu.uniquindio.exploracity.domain.model.CategoryOrigin
import co.edu.uniquindio.exploracity.domain.model.DuplicateCandidate
import co.edu.uniquindio.exploracity.domain.model.DuplicateSuspicion
import co.edu.uniquindio.exploracity.domain.model.FinalizeReason
import co.edu.uniquindio.exploracity.domain.model.GeoPoint
import co.edu.uniquindio.exploracity.domain.model.ModerationWork
import co.edu.uniquindio.exploracity.domain.model.PendingPhoto
import co.edu.uniquindio.exploracity.domain.model.PendingPhotoPlace
import co.edu.uniquindio.exploracity.domain.model.PhotoRejectionReason
import co.edu.uniquindio.exploracity.domain.model.PlaceReport
import co.edu.uniquindio.exploracity.domain.model.PlaceReportReason
import co.edu.uniquindio.exploracity.domain.model.Poi
import co.edu.uniquindio.exploracity.domain.model.PublicationStatus
import co.edu.uniquindio.exploracity.domain.model.RejectDecision
import co.edu.uniquindio.exploracity.domain.model.ReportedPlace
import co.edu.uniquindio.exploracity.domain.model.ResolvedPublication
import co.edu.uniquindio.exploracity.domain.model.ReviewAuthor
import co.edu.uniquindio.exploracity.domain.model.ReviewItem
import co.edu.uniquindio.exploracity.domain.model.ReviewQueue
import co.edu.uniquindio.exploracity.domain.repository.ModerationRepository
import co.edu.uniquindio.exploracity.domain.repository.ModerationSummary
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.Instant
import kotlin.time.Duration.Companion.seconds

/** Una cola en memoria para los ViewModels de moderación: cargarla y decidir tardan 1 s; abrir una, medio. */
internal class MemoryModeration(initial: List<ReviewItem>) : ModerationRepository {
    val items = initial.toMutableList()
    var savedAt: Instant? = null
    var queueError: Exception? = null
    var itemError: Exception? = null
    var verifyError: Exception? = null
    val verified = mutableListOf<Pair<String, String?>>()
    var work = ModerationWork(0, 0, 0)

    /** Los cercanos para enlazar el original (35) cuando la pendiente no trae parecidos. */
    var nearby: List<DuplicateCandidate> = emptyList()
    var rejectError: Exception? = null
    val rejected = mutableListOf<Pair<String, RejectDecision>>()

    val resolvedItems = mutableListOf<ResolvedPublication>()
    var resolvedError: Exception? = null
    var changeError: Exception? = null
    val finalized = mutableListOf<Pair<String, FinalizeReason>>()
    val reopened = mutableListOf<Pair<String, String>>()

    /** v1.4.0 · «Reportados». */
    val reportedPlaces = mutableListOf<ReportedPlace>()
    var reportedError: Exception? = null
    var dismissError: Exception? = null
    val dismissed = mutableListOf<String>()

    /** v1.4.0 · «Fotos por revisar». */
    val photos = mutableListOf<PendingPhoto>()
    var photoError: Exception? = null
    val approved = mutableListOf<String>()
    val rejectedPhotos = mutableListOf<Pair<String, PhotoRejectionReason>>()

    override val pendingCount = MutableStateFlow(initial.size)

    override suspend fun summary() = ModerationSummary(items.size, 0)

    override suspend fun queue(): ReviewQueue {
        delay(1.seconds)
        queueError?.let { throw it }
        return ReviewQueue(items.toList(), savedAt)
    }

    override suspend fun item(id: String): ReviewItem? {
        delay(0.5.seconds)
        itemError?.let { throw it }
        return items.firstOrNull { it.id == id }
    }

    override suspend fun queueIds(): List<String> = items.map { it.id }

    override suspend fun verify(id: String, note: String?) {
        verified += id to note
        delay(1.seconds)
        verifyError?.let { throw it }
        items.removeAll { it.id == id }
    }

    override suspend fun duplicateOptions(id: String): List<DuplicateCandidate> {
        delay(0.5.seconds)
        return nearby
    }

    override suspend fun reject(id: String, decision: RejectDecision) {
        rejected += id to decision
        delay(1.seconds)
        rejectError?.let { throw it }
        items.removeAll { it.id == id }
    }

    override suspend fun todayWork(): ModerationWork = work

    override suspend fun resolved(): List<ResolvedPublication> {
        delay(1.seconds)
        resolvedError?.let { throw it }
        return resolvedItems.toList()
    }

    override suspend fun resolvedItem(id: String): ResolvedPublication? {
        delay(0.5.seconds)
        resolvedError?.let { throw it }
        return resolvedItems.firstOrNull { it.id == id }
    }

    override suspend fun finalize(id: String, reason: FinalizeReason) {
        finalized += id to reason
        delay(1.seconds)
        changeError?.let { throw it }
        resolvedItems.replaceAll { if (it.id == id) it.copy(status = PublicationStatus.FINALIZED, finalizeReason = reason) else it }
    }

    override suspend fun reopen(id: String, reason: String) {
        reopened += id to reason
        delay(1.seconds)
        changeError?.let { throw it }
        resolvedItems.removeAll { it.id == id }
    }

    override suspend fun reported(): List<ReportedPlace> {
        delay(1.seconds)
        reportedError?.let { throw it }
        return reportedPlaces.toList()
    }

    override suspend fun pendingPhotos(): List<PendingPhoto> {
        delay(1.seconds)
        return photos.toList()
    }

    override suspend fun approvePhoto(id: String) {
        approved += id
        delay(1.seconds)
        photoError?.let { throw it }
        photos.removeAll { it.id == id }
    }

    override suspend fun rejectPhoto(id: String, reason: PhotoRejectionReason) {
        rejectedPhotos += id to reason
        delay(1.seconds)
        photoError?.let { throw it }
        photos.removeAll { it.id == id }
    }

    override suspend fun dismissReports(id: String) {
        dismissed += id
        delay(1.seconds)
        dismissError?.let { throw it }
        reportedPlaces.removeAll { it.id == id }
    }
}

/** v1.4.0 · Una foto agregada de prueba al lugar [placeId]. */
internal fun pendingPhoto(id: String, placeId: String = "mirador") = PendingPhoto(
    id = id,
    url = "https://medios.test/$id.jpg",
    submittedAt = Instant.parse("2026-10-01T15:00:00Z"),
    contributor = Author("laura", "Laura Gómez", 35),
    place = PendingPhotoPlace(placeId, "Lugar $placeId", Category.NATURE, PublicationStatus.VERIFIED, "Ana Ríos", emptyList()),
)

/** v1.4.0 · Un lugar reportado de prueba, con un reporte por cada motivo de [reasons]. */
internal fun reportedPlace(id: String, vararg reasons: PlaceReportReason = arrayOf(PlaceReportReason.CLOSED)) = ReportedPlace(
    id = id,
    title = "Lugar $id",
    category = Category.CULTURE,
    status = PublicationStatus.VERIFIED,
    photoUrl = null,
    authorName = "Camilo R.",
    reasons = reasons.distinct(),
    reports = reasons.map { PlaceReport(it, null, Instant.parse("2026-10-01T15:00:00Z")) },
    firstReportedAt = Instant.parse("2026-10-01T15:00:00Z"),
)

/** Una pendiente de prueba; con [duplicate], parecida a [candidates]. */
internal fun review(id: String, duplicate: Boolean = false, candidates: List<DuplicateCandidate> = emptyList()) = ReviewItem(
    id = id,
    title = "Lugar $id",
    category = Category.CULTURE,
    categoryOrigin = CategoryOrigin.CHOSEN,
    description = "Descripción de prueba del lugar $id.",
    photos = emptyList(),
    hours = null,
    price = null,
    address = null,
    location = GeoPoint(4.6, -74.07),
    submittedAt = Instant.parse("2026-10-01T10:00:00Z"),
    author = ReviewAuthor(Author("autor", "Autor", 100), verified = 1, rejected = 0),
    duplicate = if (duplicate) DuplicateSuspicion(candidates) else null,
)

/** Un lugar publicado a [meters] de la pendiente. */
internal fun candidate(id: String, meters: Int) = DuplicateCandidate(
    Poi(id, "Lugar publicado $id", Category.CULTURE, PublicationStatus.VERIFIED, GeoPoint(4.6, -74.07), 0, 10, 2),
    meters,
)

/** Una resuelta de prueba. */
internal fun resolved(id: String, status: PublicationStatus = PublicationStatus.VERIFIED) = ResolvedPublication(
    id = id,
    title = "Resuelta $id",
    category = Category.HISTORY,
    status = status,
    authorName = "Autor",
    decidedAt = Instant.parse("2026-10-01T10:00:00Z"),
    decidedBy = "Laura M.",
)
