package co.edu.uniquindio.exploracity.data.repository

import co.edu.uniquindio.exploracity.domain.model.AlreadyReviewedException
import co.edu.uniquindio.exploracity.domain.model.Author
import co.edu.uniquindio.exploracity.domain.model.CategoryOrigin
import co.edu.uniquindio.exploracity.domain.model.DuplicateCandidate
import co.edu.uniquindio.exploracity.domain.model.DuplicateSuspicion
import co.edu.uniquindio.exploracity.domain.model.FinalizeReason
import co.edu.uniquindio.exploracity.domain.model.FixKind
import co.edu.uniquindio.exploracity.domain.model.GeoPoint
import co.edu.uniquindio.exploracity.domain.model.ModerationWork
import co.edu.uniquindio.exploracity.domain.model.Notification
import co.edu.uniquindio.exploracity.domain.model.OwnPublication
import co.edu.uniquindio.exploracity.domain.model.Poi
import co.edu.uniquindio.exploracity.domain.model.PoiDetails
import co.edu.uniquindio.exploracity.domain.model.PoiPhoto
import co.edu.uniquindio.exploracity.domain.model.PublicationStatus
import co.edu.uniquindio.exploracity.domain.model.PublishedPhoto
import co.edu.uniquindio.exploracity.domain.model.RejectDecision
import co.edu.uniquindio.exploracity.domain.model.Rejection
import co.edu.uniquindio.exploracity.domain.model.RejectionReason
import co.edu.uniquindio.exploracity.domain.model.RequiredFix
import co.edu.uniquindio.exploracity.domain.model.ResolvedPublication
import co.edu.uniquindio.exploracity.domain.model.ReviewAuthor
import co.edu.uniquindio.exploracity.domain.model.ReviewItem
import co.edu.uniquindio.exploracity.domain.model.ReviewQueue
import co.edu.uniquindio.exploracity.domain.model.StateChangedException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Clock
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.toJavaDuration

/**
 * Temporal hasta que exista la API: las pendientes de otras personas (sampleReviewSeeds) y las de Ana, que salen de
 * [publications]. Decidir una de Ana cambia lo que ve en 22–24 y le llega el aviso (25); la de otra persona entra al
 * feed con su autor o sale de la cola. Lo decidido queda en «Resueltas», con las decisiones de muestra.
 */
class FakeModerationRepository internal constructor(
    private val pois: FakePoiRepository,
    private val publications: FakePublicationRepository,
    private val notifications: FakeNotificationRepository,
    private val clock: Clock = Clock.systemUTC(),
    private val latency: Duration = 1400.milliseconds,
    private val itemLatency: Duration = 500.milliseconds,
    private val actionLatency: Duration = 800.milliseconds,
    private val currentUser: Author = sampleCurrentUser,
    private val moderatorName: String = SAMPLE_MODERATOR_NAME,
    seeds: List<ReviewSeed> = sampleReviewSeeds,
    decisionSeeds: List<DecisionSeed> = sampleDecisions,
) : ModerationRepository {

    // Las pendientes de otras personas cuentan su espera desde que se abrió la app, como las de Ana.
    private val openedAt = clock.instant()
    private val others = seeds.toMutableList()

    /** Públicas de otras personas que se devolvieron a pendiente (36), con su detalle para publicarlas otra vez. */
    private val reopened = mutableListOf<Reopened>()

    private class Reopened(val details: PoiDetails, val submittedAt: Instant)

    /** Notas internas (34) y motivos de volver a pendiente (36): solo para moderadores. */
    private val notes = mutableMapOf<String, String>()

    /** Lo decidido sobre cada publicación, la última decisión al final. Las rechazadas de otras personas guardan cómo eran. */
    private val decisions = decisionSeeds.map { seed ->
        Decision(seed.id, openedAt - seed.decidedAgo.toJavaDuration(), seed.decidedBy, seed.note, seed.finalizeReason)
    }.toMutableList()

    private class Decision(
        val id: String,
        val at: Instant,
        val by: String,
        val note: String? = null,
        val finalizeReason: FinalizeReason? = null,
        val rejected: ResolvedPublication? = null,
    )

    private var work = ModerationWork(verified = 0, rejected = 0, finalized = 0)

    private val pending = MutableStateFlow(0)
    override val pendingCount: StateFlow<Int> = pending.asStateFlow()

    override suspend fun summary(): ModerationSummary {
        delay(300.milliseconds)
        val items = items()
        val now = clock.instant()
        return ModerationSummary(items.size, items.maxOfOrNull { it.waitingDays(now).toInt() } ?: 0)
    }

    override suspend fun queue(): ReviewQueue {
        delay(latency)
        return ReviewQueue(items())
    }

    override suspend fun item(id: String): ReviewItem? {
        delay(itemLatency)
        return items().firstOrNull { it.id == id }
    }

    override suspend fun queueIds(): List<String> = items().map { it.id }

    override suspend fun verify(id: String, note: String?) {
        delay(actionLatency)
        val now = clock.instant()
        val ana = publications.markVerified(id)
        if (ana != null) {
            notifications.deliver(Notification.Verified("verificada-$id-${now.toEpochMilli()}", now, read = false, id, ana.title, VERIFIED_POINTS))
        } else {
            val seed = others.firstOrNull { it.id == id }
            val back = reopened.firstOrNull { it.details.poi.id == id }
            when {
                seed != null -> {
                    others.remove(seed)
                    pois.publish(seed.toPublicDetails(authorOf(seed.authorId)))
                }
                back != null -> {
                    reopened.remove(back)
                    pois.publish(back.details.copy(poi = back.details.poi.copy(status = PublicationStatus.VERIFIED)))
                }
                else -> throw AlreadyReviewedException()
            }
        }
        val clean = note?.trim()?.takeIf { it.isNotEmpty() }
        clean?.let { notes[id] = it }
        decisions += Decision(id, now, moderatorName, note = clean)
        work = work.copy(verified = work.verified + 1)
        items()
    }

    override suspend fun duplicateOptions(id: String): List<DuplicateCandidate> {
        delay(itemLatency)
        val item = items().firstOrNull { it.id == id } ?: throw AlreadyReviewedException()
        item.duplicate?.candidates?.takeIf { it.isNotEmpty() }?.let { return it }
        return pois.places()
            .filter { it.id != id }
            .map { candidate(it, item.location) }
            .filter { it.distanceMeters <= NEARBY_METERS }
            .sortedBy { it.distanceMeters }
            .take(NEARBY_LIMIT)
    }

    override suspend fun reject(id: String, decision: RejectDecision) {
        delay(actionLatency)
        val now = clock.instant()
        val original = decision.originalId?.let { originalId -> pois.places().firstOrNull { it.id == originalId } }
        require(decision.reason != RejectionReason.DUPLICATE || original != null) { "Falta el lugar original: ${decision.originalId}" }
        val duplicate = decision.reason == RejectionReason.DUPLICATE
        val rejection = Rejection(
            reason = decision.reason,
            message = decision.message.trim().ifEmpty { decision.reason.sentence().replaceFirstChar { it.uppercase() } + "." },
            reviewerName = moderatorName,
            rejectedAt = now,
            // Un duplicado no se corrige: el lugar ya existe.
            canResubmit = decision.canResubmit && !duplicate,
            fixes = decision.reason.fixes(),
        )
        val ana = publications.markRejected(id, rejection, original?.id)
        var snapshot: ResolvedPublication? = null
        if (ana != null) {
            val noticeId = "rechazada-$id-${now.toEpochMilli()}"
            notifications.deliver(
                if (original != null) {
                    Notification.DuplicateRejected(noticeId, now, read = false, id, ana.title, original.id, original.title)
                } else {
                    Notification.Rejected(noticeId, now, read = false, id, ana.title, rejection.noticeReason())
                },
            )
        } else {
            val item = items().firstOrNull { it.id == id } ?: throw AlreadyReviewedException()
            others.removeAll { it.id == id }
            reopened.removeAll { it.details.poi.id == id }
            snapshot = ResolvedPublication(
                id = id,
                title = item.title,
                category = item.category,
                status = PublicationStatus.REJECTED,
                authorName = item.author.author.name,
                decidedAt = now,
                decidedBy = moderatorName,
                rejectionReason = decision.reason,
            )
        }
        decisions += Decision(id, now, moderatorName, rejected = snapshot)
        work = work.copy(rejected = work.rejected + 1)
        items()
    }

    override suspend fun todayWork(): ModerationWork {
        delay(itemLatency)
        return work
    }

    override suspend fun resolved(): List<ResolvedPublication> {
        delay(latency)
        return resolvedNow()
    }

    override suspend fun resolvedItem(id: String): ResolvedPublication? {
        delay(itemLatency)
        return resolvedNow().firstOrNull { it.id == id }
    }

    override suspend fun finalize(id: String, reason: FinalizeReason) {
        delay(actionLatency)
        val poi = pois.places().firstOrNull { it.id == id && it.status == PublicationStatus.VERIFIED } ?: throw StateChangedException()
        val now = clock.instant()
        pois.setStatus(id, PublicationStatus.FINALIZED)
        decisions += Decision(id, now, moderatorName, finalizeReason = reason)
        if (pois.detailsOf(poi).author?.id == currentUser.id) {
            notifications.deliver(Notification.Finalized("finalizada-$id-${now.toEpochMilli()}", now, read = false, id, poi.title, reason.sentence()))
        }
        work = work.copy(finalized = work.finalized + 1)
    }

    override suspend fun reopen(id: String, reason: String) {
        delay(actionLatency)
        val poi = pois.places().firstOrNull { it.id == id && it.status in PUBLIC_STATUSES } ?: throw StateChangedException()
        notes[id] = reason.trim()
        // La de Ana vuelve a sus pendientes (22); la de otra persona, a la cola con lo que tenía.
        if (publications.markReopened(id) == null) {
            val details = pois.detailsOf(poi)
            pois.remove(id)
            reopened += Reopened(details.copy(poi = poi), clock.instant())
        }
        items()
    }

    /** La cola al día; de paso, el badge. */
    private fun items(): List<ReviewItem> {
        val ana = ReviewAuthor(
            currentUser,
            verified = publications.publicCount(),
            rejected = publications.rejectedCount(),
        )
        val all = others.map { it.toItem() } + reopened.map { it.toItem() } + publications.pendingOnes().map { it.toItem(ana) }
        return all.sortedBy { it.submittedAt }.also { pending.value = it.size }
    }

    /** Las públicas con una decisión y las rechazadas (de Ana y de otras personas), de la más reciente a la más antigua. */
    private fun resolvedNow(): List<ResolvedPublication> {
        val places = pois.places().associateBy { it.id }
        val pendingIds = items().map { it.id }.toSet()
        val latest = decisions.sortedByDescending { it.at }.distinctBy { it.id }
        val fromLog = latest.mapNotNull { decision ->
            val poi = places[decision.id]
            when {
                poi != null -> poi.toResolved(decision)
                decision.id !in pendingIds -> decision.rejected
                else -> null
            }
        }
        val anaRejected = publications.rejectedOnes().mapNotNull { it.toResolved() }
        val anaIds = anaRejected.map { it.id }.toSet()
        return (fromLog.filterNot { it.id in anaIds } + anaRejected).sortedByDescending { it.decidedAt }
    }

    private fun Poi.toResolved(decision: Decision) = ResolvedPublication(
        id = id,
        title = title,
        category = category,
        status = status,
        authorName = pois.detailsOf(this).author?.name,
        decidedAt = decision.at,
        decidedBy = decision.by,
        note = decision.note,
        finalizeReason = decision.finalizeReason.takeIf { status == PublicationStatus.FINALIZED },
        votes = votes,
        comments = comments,
    )

    private fun OwnPublication.toResolved(): ResolvedPublication? {
        val rejection = rejection ?: return null
        return ResolvedPublication(
            id = id,
            title = title,
            category = category,
            status = PublicationStatus.REJECTED,
            authorName = currentUser.name,
            decidedAt = rejection.rejectedAt,
            decidedBy = rejection.reviewerName,
            rejectionReason = rejection.reason,
        )
    }

    private fun authorOf(id: String): Author = sampleAuthors.first { it.id == id }

    private fun candidates(ids: List<String>, from: GeoPoint): List<DuplicateCandidate> {
        val places = pois.places().associateBy { it.id }
        return ids.mapNotNull { places[it] }.map { candidate(it, from) }.sortedBy { it.distanceMeters }
    }

    /** Un lugar publicado visto desde la pendiente, con lo que 33A muestra al lado: autor, fecha de publicación y fotos. */
    private fun candidate(poi: Poi, from: GeoPoint): DuplicateCandidate {
        val details = pois.detailsOf(poi)
        val verifiedAt = decisions.lastOrNull { it.id == poi.id && it.finalizeReason == null && it.rejected == null }?.at
        val publishedAt = verifiedAt ?: (openedAt - samplePublishedAgo(poi.id).toJavaDuration())
        return DuplicateCandidate(poi, from.distanceTo(poi.location), details.author, publishedAt, details.photos.size)
    }

    private fun ReviewSeed.toItem(): ReviewItem {
        val (verified, rejected) = sampleReviewHistory[authorId] ?: (0 to 0)
        return ReviewItem(
            id = id,
            title = title,
            category = category,
            categoryOrigin = categoryOrigin,
            description = description,
            photos = samplePublishedPhotos(id, photoCount),
            hours = hours,
            price = price,
            address = address,
            location = location,
            submittedAt = openedAt - submittedAgo.toJavaDuration(),
            author = ReviewAuthor(authorOf(authorId), verified, rejected),
            reportReason = reportReason,
            duplicate = similarIds.takeIf { it.isNotEmpty() }?.let { DuplicateSuspicion(candidates(it, location), note) },
        )
    }

    private fun Reopened.toItem(): ReviewItem {
        val poi = details.poi
        val (verified, rejected) = sampleReviewHistory[details.author?.id] ?: (0 to 0)
        return ReviewItem(
            id = poi.id,
            title = poi.title,
            category = poi.category,
            categoryOrigin = CategoryOrigin.CHOSEN,
            description = details.description,
            photos = samplePublishedPhotos(poi.id, details.photos.size),
            hours = details.hours,
            price = poi.price,
            address = details.address,
            location = poi.location,
            submittedAt = submittedAt,
            // Los lugares de muestra siempre tienen autor.
            author = ReviewAuthor(checkNotNull(details.author), verified, rejected),
        )
    }

    private fun OwnPublication.toItem(author: ReviewAuthor): ReviewItem = ReviewItem(
        id = id,
        title = title,
        category = category,
        // El servidor de publicaciones aún no guarda si la categoría fue sugerida.
        categoryOrigin = CategoryOrigin.CHOSEN,
        description = description,
        photos = photos,
        hours = hours,
        price = price,
        address = null,
        location = location,
        submittedAt = submittedAt,
        author = author,
        duplicate = if (possibleDuplicate) DuplicateSuspicion(candidates(similarIds, location), duplicateNote) else null,
    )

    private fun ReviewSeed.toPublicDetails(author: Author): PoiDetails {
        val photos = samplePublishedPhotos(id, photoCount)
        return PoiDetails(
            poi = Poi(
                id = id,
                title = title,
                category = category,
                status = PublicationStatus.VERIFIED,
                location = location,
                distanceMeters = 0,
                votes = 0,
                comments = 0,
                photoUrl = photos.firstOrNull()?.url,
                price = price,
                summary = description.substringBefore('.').take(80),
            ),
            description = description,
            photos = photos.mapIndexed { i, _: PublishedPhoto -> PoiPhoto(url = null, description = "Foto ${i + 1} de $title") },
            address = address,
            hours = hours,
            author = author,
            voted = false,
            visited = false,
        )
    }

    private companion object {
        val PUBLIC_STATUSES = setOf(PublicationStatus.VERIFIED, PublicationStatus.FINALIZED)

        /** Sin parecidos marcados, se ofrecen como original los lugares a menos de esto (35). */
        const val NEARBY_METERS = 500
        const val NEARBY_LIMIT = 5
    }
}

// Lo que el servidor escribe en los avisos y en 24: con la API, estos textos los manda el backend.
/** El motivo dentro de una frase: «Mirador de La Peña fue rechazada: la foto no permite reconocer el lugar». */
private fun RejectionReason.sentence(): String = when (this) {
    RejectionReason.DUPLICATE -> "el lugar ya está publicado"
    RejectionReason.PHOTO -> "la foto no permite reconocer el lugar"
    RejectionReason.LOCATION -> "la ubicación no corresponde"
    RejectionReason.INAPPROPRIATE -> "contenido inapropiado o publicidad"
    RejectionReason.OTHER -> "otro motivo"
}
/** Con «Otro motivo», el aviso dice lo que escribió el moderador. */
private fun Rejection.noticeReason(): String =
    if (reason == RejectionReason.OTHER) message.trim().trimEnd('.').replaceFirstChar { it.lowercase() } else reason.sentence()
/** «Qué revisar antes de reenviar» (24.a) según el motivo. */
private fun RejectionReason.fixes(): List<RequiredFix> = when (this) {
    RejectionReason.PHOTO -> listOf(RequiredFix(FixKind.PHOTOS, "Una foto donde se reconozca el lugar"))
    RejectionReason.LOCATION -> listOf(RequiredFix(FixKind.LOCATION, "El pin sobre la entrada del lugar"))
    else -> emptyList()
}
/** «Casa de la Independencia pasó a finalizada: el lugar cerró de forma permanente». */
private fun FinalizeReason.sentence(): String = when (this) {
    FinalizeReason.CLOSED -> "el lugar cerró de forma permanente"
    FinalizeReason.EVENT_ENDED -> "fue un evento temporal que ya pasó"
    FinalizeReason.MERGED -> "se unificó con otra publicación"
}
