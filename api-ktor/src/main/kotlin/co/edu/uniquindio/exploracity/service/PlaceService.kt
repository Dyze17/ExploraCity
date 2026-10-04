package co.edu.uniquindio.exploracity.service

import co.edu.uniquindio.exploracity.config.query
import co.edu.uniquindio.exploracity.model.CommentRequest
import co.edu.uniquindio.exploracity.model.CommentResponse
import co.edu.uniquindio.exploracity.model.CommentsPageResponse
import co.edu.uniquindio.exploracity.model.CountResponse
import co.edu.uniquindio.exploracity.model.FeedPageResponse
import co.edu.uniquindio.exploracity.model.GeoPoint
import co.edu.uniquindio.exploracity.model.MapAreaResponse
import co.edu.uniquindio.exploracity.model.PlaceDetailsResponse
import co.edu.uniquindio.exploracity.model.VisitRequest
import co.edu.uniquindio.exploracity.model.VisitResponse
import co.edu.uniquindio.exploracity.model.VoteResponse
import co.edu.uniquindio.exploracity.model.iso
import co.edu.uniquindio.exploracity.plugins.ApiException
import co.edu.uniquindio.exploracity.repository.CommentRow
import co.edu.uniquindio.exploracity.repository.NotificationRepository
import co.edu.uniquindio.exploracity.repository.PlaceFilter
import co.edu.uniquindio.exploracity.repository.PlaceRepository
import co.edu.uniquindio.exploracity.repository.PlaceRow
import org.jetbrains.exposed.v1.jdbc.Database
import java.time.Clock
import java.time.Instant
import java.util.Base64
import java.util.UUID

/**
 * Componente de Puntos de Interés y Social (SAD) · El feed y el mapa (7, 8, 9 y 10), el detalle (13), el voto «Es
 * importante», «Marcar como visitado» (14.b) y los comentarios (14). Solo se ven los lugares verificados y finalizados.
 */
class PlaceService(
    private val database: Database,
    private val places: PlaceRepository,
    private val notifications: NotificationRepository,
    private val reputation: ReputationService,
    private val cards: PlaceCards,
    private val clock: Clock,
) {
    /** 7 · Página [page] (desde 0), del más cercano al más lejano. */
    suspend fun feed(filter: PlaceFilter, near: GeoPoint?, page: Int, pageSize: Int): FeedPageResponse = database.query {
        val origin = cards.origin(near)
        val items = places.search(filter, origin, limit = pageSize, offset = page * pageSize)
        val total = places.count(filter, origin)
        FeedPageResponse(items.map(cards::summaryOf), total, hasMore = page * pageSize + items.size < total)
    }

    /** 9 · El conteo en vivo del botón de la hoja de filtros. */
    suspend fun count(filter: PlaceFilter, near: GeoPoint?): CountResponse =
        database.query { CountResponse(places.count(filter, cards.origin(near))) }

    /** 8 · Los más cercanos del área visible, como máximo [limit], y cuántos hay en el área. */
    suspend fun map(filter: PlaceFilter, near: GeoPoint?, limit: Int): MapAreaResponse = database.query {
        val origin = cards.origin(near)
        MapAreaResponse(places.search(filter, origin, limit).map(cards::summaryOf), places.count(filter, origin))
    }

    /** 13 · El detalle, con el voto y la visita de quien lo abre. */
    suspend fun details(userId: UUID, placeId: String, near: GeoPoint?): PlaceDetailsResponse = database.query {
        val place = place(placeId, near)
        PlaceDetailsResponse(
            place = cards.summaryOf(place),
            description = place.description,
            photos = places.photos(place.id),
            address = place.address,
            hours = cards.hoursOf(place),
            author = places.author(place.authorId)?.let(PlaceCards::authorOf),
            voted = places.hasVoted(place.id, userId),
            visited = places.hasVisited(place.id, userId),
        )
    }

    /** «Es importante»: un voto por persona. Devuelve el total del lugar. */
    suspend fun vote(userId: UUID, placeId: String, voted: Boolean): VoteResponse = database.query {
        val place = place(placeId, near = null)
        if (voted) {
            val added = places.addVote(place.id, userId, clock.instant())
            // C1: el voto propio cuenta en el total, pero no para la insignia de quien publicó.
            val author = place.authorId
            if (added && author != null && author != userId) reputation.awardBadges(author)
        } else {
            places.removeVote(place.id, userId)
        }
        VoteResponse(places.votes(place.id))
    }

    /**
     * 14.b · Marca la visita con la experiencia (todo opcional). La primera vez en un lugar de otra persona da 5 puntos
     * (G2); en uno propio no suma (C1). Marcarla otra vez solo cambia la experiencia.
     */
    suspend fun visit(userId: UUID, placeId: String, request: VisitRequest): VisitResponse {
        val text = request.text?.trim()?.ifEmpty { null }
        if ((text?.length ?: 0) > TEXT_MAX) throw ApiException.badRequest("invalid_experience")
        return database.query {
            val place = place(placeId, near = null)
            val own = place.authorId == userId
            val points = places.visit(
                placeId = place.id,
                userId = userId,
                recommends = request.recommends,
                text = text,
                showName = request.showName,
                points = if (own) 0 else VISIT_POINTS,
                at = clock.instant(),
            )
            if (!own) reputation.awardBadges(userId)
            VisitResponse(points)
        }
    }

    /** 14 · Del más reciente al más antiguo, [pageSize] por página. */
    suspend fun comments(userId: UUID, placeId: String, cursor: String?, pageSize: Int): CommentsPageResponse {
        val after = cursor?.let(::decodeCursor)
        return database.query {
            val place = place(placeId, near = null)
            // Uno de más dice si hay otra página.
            val rows = places.comments(place.id, after, pageSize + 1)
            val page = rows.take(pageSize)
            CommentsPageResponse(
                placeTitle = place.title,
                items = page.map { it.toResponse(userId) },
                total = places.commentCount(place.id),
                nextCursor = page.lastOrNull()?.takeIf { rows.size > pageSize }?.let(::encodeCursor),
            )
        }
    }

    /**
     * 14 · Publica el comentario (1 a 300 caracteres). Comentar no da puntos. A quien publicó el lugar le llega un aviso,
     * salvo que comente en lo propio. Con [CommentRequest.clientId], un reenvío de la cola no lo duplica: devuelve el que
     * ya estaba y `false`.
     */
    suspend fun addComment(userId: UUID, placeId: String, request: CommentRequest): Pair<CommentResponse, Boolean> {
        val text = request.text.trim()
        if (text.isEmpty() || text.length > TEXT_MAX) throw ApiException.badRequest("invalid_comment")
        val clientId = request.clientId?.let { runCatching { UUID.fromString(it) }.getOrNull() ?: throw ApiException.badRequest() }
        return database.query {
            val place = place(placeId, near = null)
            val now = clock.instant()
            val (id, created) = places.addComment(place.id, userId, clientId, text, now)
            if (created) {
                val author = place.authorId
                if (author != null && author != userId) notifications.commented(author, place.id, place.title, id, now)
                reputation.awardBadges(userId)
            }
            checkNotNull(places.comment(id)).toResponse(userId) to created
        }
    }

    /** Dentro de una transacción: el lugar público, o 404 si no existe o no se ve. */
    private fun place(placeId: String, near: GeoPoint?): PlaceRow {
        val id = runCatching { UUID.fromString(placeId) }.getOrNull() ?: throw placeNotFound()
        return places.find(id, cards.origin(near)) ?: throw placeNotFound()
    }

    private fun CommentRow.toResponse(viewer: UUID) = CommentResponse(
        id = id.toString(),
        author = author?.let(PlaceCards::authorOf),
        text = text,
        createdAt = createdAt.iso(),
        mine = author?.id == viewer,
    )

    companion object {
        /** G2 · Marcar un lugar como visitado. */
        const val VISIT_POINTS = 5

        /** README 14: comentarios y experiencias de hasta 300 caracteres. */
        const val TEXT_MAX = 300

        fun placeNotFound() = ApiException.notFound("place_not_found")

        private val encoder = Base64.getUrlEncoder().withoutPadding()
        private val decoder = Base64.getUrlDecoder()

        /** El cursor es opaco para la app: la fecha (con sus microsegundos) y el id del último comentario de la página. */
        fun encodeCursor(row: CommentRow): String = encoder.encodeToString("${row.createdAt}|${row.id}".toByteArray())

        private fun decodeCursor(cursor: String): Pair<Instant, UUID> = runCatching {
            val (time, id) = String(decoder.decode(cursor)).split('|', limit = 2)
            Instant.parse(time) to UUID.fromString(id)
        }.getOrNull() ?: throw ApiException.badRequest("invalid_cursor")
    }
}
