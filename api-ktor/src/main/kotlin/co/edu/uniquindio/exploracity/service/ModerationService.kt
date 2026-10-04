package co.edu.uniquindio.exploracity.service

import co.edu.uniquindio.exploracity.config.CitySettings
import co.edu.uniquindio.exploracity.config.query
import co.edu.uniquindio.exploracity.model.CandidateResponse
import co.edu.uniquindio.exploracity.model.DecisionAction
import co.edu.uniquindio.exploracity.model.FinalizeReason
import co.edu.uniquindio.exploracity.model.GeoPoint
import co.edu.uniquindio.exploracity.model.ModerationSummaryResponse
import co.edu.uniquindio.exploracity.model.ModerationWorkResponse
import co.edu.uniquindio.exploracity.model.PublicationStatus
import co.edu.uniquindio.exploracity.model.RejectRequest
import co.edu.uniquindio.exploracity.model.RejectionReason
import co.edu.uniquindio.exploracity.model.ResolvedResponse
import co.edu.uniquindio.exploracity.model.ReviewItemResponse
import co.edu.uniquindio.exploracity.model.ReviewQueueResponse
import co.edu.uniquindio.exploracity.model.iso
import co.edu.uniquindio.exploracity.plugins.ApiException
import co.edu.uniquindio.exploracity.repository.ModerationRepository
import co.edu.uniquindio.exploracity.repository.NewDecision
import co.edu.uniquindio.exploracity.repository.NotificationRepository
import co.edu.uniquindio.exploracity.repository.OwnPlaceRow
import co.edu.uniquindio.exploracity.repository.PlaceRepository
import co.edu.uniquindio.exploracity.repository.PublicationRepository
import co.edu.uniquindio.exploracity.repository.ResolvedRow
import io.ktor.http.HttpStatusCode
import org.jetbrains.exposed.v1.jdbc.Database
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.util.UUID

/**
 * Componente de Moderación (SAD) · La cola (32), la revisión (33 y 33A), las decisiones (34, 35 y 36), «Tu trabajo de
 * hoy» (37) y «Resueltas». F1: un moderador nunca ve ni decide sus propias publicaciones.
 */
class ModerationService(
    private val database: Database,
    private val publications: PublicationRepository,
    private val places: PlaceRepository,
    private val moderation: ModerationRepository,
    private val notifications: NotificationRepository,
    private val reputation: ReputationService,
    private val views: PublicationViews,
    private val city: CitySettings,
    private val clock: Clock,
) {
    /** 7.c · Cuántas esperan (sin las propias) y desde hace cuántos días la más antigua. */
    suspend fun summary(moderatorId: UUID): ModerationSummaryResponse = database.query {
        val pending = publications.pending(excludeAuthor = moderatorId)
        val now = clock.instant()
        val oldest = pending.minOfOrNull { it.submittedAt }?.let { Duration.between(it, now).toDays().toInt() } ?: 0
        ModerationSummaryResponse(pending.size, oldest)
    }

    /** 32 · La cola, de la más antigua a la más reciente, sin las propias (F1). */
    suspend fun queue(moderatorId: UUID): ReviewQueueResponse = database.query {
        ReviewQueueResponse(publications.pending(excludeAuthor = moderatorId).map { views.reviewItemOf(it) })
    }

    /** 33 · Una pendiente; 404 si ya no está en la cola. */
    suspend fun item(moderatorId: UUID, placeId: String): ReviewItemResponse = database.query {
        views.reviewItemOf(pending(moderatorId, placeId, lock = false))
    }

    /**
     * 34 · Verifica: entra al feed y al mapa, y quien la publicó recibe el aviso con sus puntos (+15 la primera vez que
     * la verifican) y el avance de sus insignias.
     */
    suspend fun verify(moderatorId: UUID, placeId: String, note: String?) {
        val clean = note?.trim()?.ifEmpty { null }
        if ((clean?.length ?: 0) > NOTE_MAX) throw invalidDecision()
        database.query {
            val place = pending(moderatorId, placeId, lock = true)
            val now = clock.instant()
            val points = if (publications.everVerified(place.id)) 0 else VERIFIED_POINTS
            publications.setStatus(place.id, PublicationStatus.VERIFIED)
            publications.setPoints(place.id, place.pointsEarned + points, place.firstPublication)
            moderation.decide(NewDecision(place.id, moderatorId, DecisionAction.VERIFIED, note = clean), now)
            val author = checkNotNull(place.authorId)
            notifications.verified(author, place.id, place.fields.title, points, now)
            reputation.awardBadges(author)
        }
    }

    /**
     * 35 · Con quién se puede enlazar el original de un duplicado: los parecidos que ya trae la pendiente o, si no
     * trae, los publicados más cercanos (hasta 5 a 500 m o menos).
     */
    suspend fun duplicateOptions(moderatorId: UUID, placeId: String): List<CandidateResponse> = database.query {
        val place = pending(moderatorId, placeId, lock = false)
        val from = GeoPoint(place.fields.latitude, place.fields.longitude)
        val declared = views.candidatesOf(publications.similarIds(place.id), from)
        declared.ifEmpty {
            views.candidatesOf(places.nearby(from, NEARBY_METERS, NEARBY_LIMIT, place.id).map { it.id }, from)
        }
    }

    /**
     * 35 · Rechaza con un motivo. «Otro» pide un detalle de 20 a 400 caracteres y «Duplicado», el lugar original. Quien
     * la publicó la ve rechazada (24) y recibe el aviso. Sin permiso de reenvío pierde los +20 de la primera publicación
     * (D1), si los tenía.
     */
    suspend fun reject(moderatorId: UUID, placeId: String, request: RejectRequest) {
        val message = request.message.trim()
        if (message.length > MESSAGE_MAX) throw invalidDecision()
        if (request.reason == RejectionReason.OTHER && message.length < OTHER_MIN) throw invalidDecision()
        val duplicate = request.reason == RejectionReason.DUPLICATE
        val originalId = request.originalId?.let { runCatching { UUID.fromString(it) }.getOrNull() ?: throw invalidDecision() }
        if (duplicate && originalId == null) throw invalidDecision()
        database.query {
            val place = pending(moderatorId, placeId, lock = true)
            val original = originalId?.let { places.find(it, GeoPoint(place.fields.latitude, place.fields.longitude)) }
            if (duplicate && (original == null || original.id == place.id)) throw invalidDecision()
            val now = clock.instant()
            val canResubmit = request.canResubmit && !duplicate
            publications.setStatus(place.id, PublicationStatus.REJECTED)
            if (!canResubmit && place.firstPublication) {
                publications.setPoints(place.id, (place.pointsEarned - PublicationService.FIRST_PUBLICATION_POINTS).coerceAtLeast(0), firstPublication = false)
            }
            moderation.decide(
                NewDecision(
                    placeId = place.id,
                    moderatorId = moderatorId,
                    action = DecisionAction.REJECTED,
                    rejectionReason = request.reason,
                    rejectionMessage = message.ifEmpty { ModerationTexts.defaultMessage(request.reason) },
                    canResubmit = canResubmit,
                    duplicateOf = original?.id.takeIf { duplicate },
                ),
                now,
            )
            val author = checkNotNull(place.authorId)
            if (duplicate && original != null) {
                notifications.duplicateRejected(author, place.id, place.fields.title, original.id, original.title, now)
            } else {
                val reason = ModerationTexts.noticeReason(request.reason, message.ifEmpty { ModerationTexts.defaultMessage(request.reason) })
                notifications.rejected(author, place.id, place.fields.title, reason, now)
            }
        }
    }

    /** 37 · Lo que decidió este moderador desde la medianoche de la ciudad. */
    suspend fun todayWork(moderatorId: UUID): ModerationWorkResponse = database.query {
        val midnight = LocalDate.ofInstant(clock.instant(), city.timeZone).atStartOfDay(city.timeZone).toInstant()
        val counts = moderation.work(moderatorId, midnight)
        ModerationWorkResponse(
            verified = counts[DecisionAction.VERIFIED] ?: 0,
            rejected = counts[DecisionAction.REJECTED] ?: 0,
            finalized = counts[DecisionAction.FINALIZED] ?: 0,
        )
    }

    /** «Resueltas» · Lo ya decidido, de lo más reciente a lo más antiguo. */
    suspend fun resolved(): List<ResolvedResponse> = database.query { moderation.resolved().map { it.toResponse() } }

    /** 36 · Una resuelta; 404 si ya no lo está (volvió a pendiente o la borraron). */
    suspend fun resolvedItem(placeId: String): ResolvedResponse = database.query {
        val id = idOrNull(placeId) ?: throw notFound()
        moderation.resolved(id).singleOrNull()?.toResponse() ?: throw notFound()
    }

    /**
     * 36 · Pasa a finalizada una verificada: sigue en el feed con su chip, quien la publicó recibe el aviso con el motivo
     * y sus puntos no se descuentan.
     */
    suspend fun finalize(moderatorId: UUID, placeId: String, reason: FinalizeReason) {
        database.query {
            val place = public(moderatorId, placeId, setOf(PublicationStatus.VERIFIED))
            val now = clock.instant()
            publications.setStatus(place.id, PublicationStatus.FINALIZED)
            moderation.decide(NewDecision(place.id, moderatorId, DecisionAction.FINALIZED, finalizeReason = reason), now)
            place.authorId?.let { notifications.finalized(it, place.id, place.fields.title, ModerationTexts.sentence(reason), now) }
        }
    }

    /**
     * 36 · Vuelve a pendiente una pública: sale del feed y entra a la cola como recién enviada. El motivo (20 a 300
     * caracteres) queda como nota interna. Una sin autor (cuenta eliminada) no puede volver: nadie podría corregirla.
     */
    suspend fun reopen(moderatorId: UUID, placeId: String, reason: String) {
        val clean = reason.trim()
        if (clean.length !in REOPEN_MIN..NOTE_MAX) throw invalidDecision()
        database.query {
            val place = public(moderatorId, placeId, setOf(PublicationStatus.VERIFIED, PublicationStatus.FINALIZED))
            if (place.authorId == null) throw ApiException.conflict("cannot_reopen")
            val now = clock.instant()
            publications.setStatus(place.id, PublicationStatus.PENDING, resubmittedAt = now)
            moderation.decide(NewDecision(place.id, moderatorId, DecisionAction.REOPENED, note = clean), now)
        }
    }

    /** Dentro de una transacción: la pendiente [placeId], o 409 si ya no lo está. 403 si es del mismo moderador (F1). */
    private fun pending(moderatorId: UUID, placeId: String, lock: Boolean): OwnPlaceRow {
        val id = idOrNull(placeId) ?: throw notFound()
        val place = publications.find(id, lock) ?: throw notFound()
        if (place.authorId == null) throw notFound()
        if (place.authorId == moderatorId) throw ownPublication()
        if (place.status != PublicationStatus.PENDING) throw ApiException.conflict("already_reviewed")
        return place
    }

    /** Dentro de una transacción: la pública [placeId] en uno de [statuses], o 409 si cambió de estado. */
    private fun public(moderatorId: UUID, placeId: String, statuses: Set<PublicationStatus>): OwnPlaceRow {
        val id = idOrNull(placeId) ?: throw notFound()
        val place = publications.find(id, lock = true) ?: throw notFound()
        if (place.authorId == moderatorId) throw ownPublication()
        if (place.status !in statuses) throw ApiException.conflict("state_changed")
        return place
    }

    private fun ResolvedRow.toResponse() = ResolvedResponse(
        id = id.toString(),
        title = title,
        category = category,
        status = status,
        authorName = authorName,
        decidedAt = decidedAt.iso(),
        decidedBy = decidedBy ?: ModerationTexts.FORMER_MODERATOR,
        note = note,
        rejectionReason = rejectionReason,
        finalizeReason = finalizeReason?.takeIf { status == PublicationStatus.FINALIZED },
        votes = votes,
        comments = comments,
    )

    private fun idOrNull(text: String): UUID? = runCatching { UUID.fromString(text) }.getOrNull()

    private fun notFound() = ApiException.notFound("publication_not_found")

    private fun ownPublication() = ApiException(HttpStatusCode.Forbidden, "own_publication")

    private fun invalidDecision() = ApiException.badRequest("invalid_decision")

    companion object {
        /** G2 · Publicación verificada, la primera vez. */
        const val VERIFIED_POINTS = 15

        /** README · Rechazo: con «Otro», el detalle tiene de 20 a 400 caracteres. */
        const val OTHER_MIN = 20
        const val MESSAGE_MAX = 400

        /** 34 y 36 · Notas internas, como las limita la app: hasta 300; volver a pendiente pide al menos 20. */
        const val NOTE_MAX = 300
        const val REOPEN_MIN = 20

        /** 35 · Sin parecidos marcados, se ofrecen como original los publicados cercanos. */
        const val NEARBY_METERS = 500.0
        const val NEARBY_LIMIT = 5
    }
}
