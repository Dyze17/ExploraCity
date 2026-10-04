package co.edu.uniquindio.exploracity.repository

import co.edu.uniquindio.exploracity.model.Badges
import co.edu.uniquindio.exploracity.model.Comments
import co.edu.uniquindio.exploracity.model.NotificationType
import co.edu.uniquindio.exploracity.model.Notifications
import co.edu.uniquindio.exploracity.model.Users
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.statements.InsertStatement
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

/**
 * Un aviso guardado, con lo que su frase necesita. El autor y el texto de un comentario salen del comentario, así
 * respetan su eliminación; el nombre de la insignia siguiente sale del catálogo.
 */
data class NotificationRow(
    val id: UUID,
    val type: NotificationType,
    val createdAt: Instant,
    val read: Boolean,
    val placeId: UUID?,
    val placeTitle: String?,
    val points: Int?,
    val reason: String?,
    /** COMMENTED: null si la cuenta que comentó se eliminó. */
    val commentAuthor: String?,
    val commentText: String?,
    val existingPlaceId: UUID?,
    val existingTitle: String?,
    val achievement: String?,
    val nextBadge: String?,
    val remaining: Int?,
)

/** 25 · Avisos dentro de la app (ADR-09: sin push, la app los consulta). Corre en la transacción de quien llama. */
class NotificationRepository {

    /** Los más recientes primero, como máximo [limit]. */
    fun list(userId: UUID, limit: Int): List<NotificationRow> = Notifications
        .join(Comments, JoinType.LEFT, Notifications.commentId, Comments.id)
        .join(Users, JoinType.LEFT, Comments.authorId, Users.id)
        .join(Badges, JoinType.LEFT, Notifications.badgeId, Badges.id)
        .select(
            Notifications.id, Notifications.type, Notifications.createdAt, Notifications.readAt, Notifications.placeId,
            Notifications.placeTitle, Notifications.points, Notifications.reason, Notifications.existingPlaceId,
            Notifications.existingTitle, Notifications.achievement, Notifications.remaining, Comments.text, Users.name,
            Badges.name,
        )
        .where { Notifications.userId eq userId }
        .orderBy(Notifications.createdAt to SortOrder.DESC, Notifications.id to SortOrder.DESC)
        .limit(limit)
        .map { row ->
            NotificationRow(
                id = row[Notifications.id],
                type = row[Notifications.type],
                createdAt = row[Notifications.createdAt].toInstant(),
                read = row[Notifications.readAt] != null,
                placeId = row[Notifications.placeId],
                placeTitle = row[Notifications.placeTitle],
                points = row[Notifications.points],
                reason = row[Notifications.reason],
                commentAuthor = row.getOrNull(Users.name),
                commentText = row.getOrNull(Comments.text),
                existingPlaceId = row[Notifications.existingPlaceId],
                existingTitle = row[Notifications.existingTitle],
                achievement = row[Notifications.achievement],
                nextBadge = row.getOrNull(Badges.name),
                remaining = row[Notifications.remaining],
            )
        }

    fun unread(userId: UUID): Int =
        Notifications.selectAll().where { (Notifications.userId eq userId) and Notifications.readAt.isNull() }.count().toInt()

    /** 14 · Alguien comentó en un lugar de [userId]. */
    fun commented(userId: UUID, placeId: UUID, placeTitle: String, commentId: UUID, at: Instant) {
        Notifications.insert {
            it[Notifications.userId] = userId
            it[type] = NotificationType.COMMENTED
            it[Notifications.placeId] = placeId
            it[Notifications.placeTitle] = placeTitle
            it[Notifications.commentId] = commentId
            it[createdAt] = at.atOffset(ZoneOffset.UTC)
        }
    }

    /** 27 · Desbloqueó [achievement]; [nextBadgeId] es la insignia bloqueada más cercana y le faltan [remaining]. */
    fun achievement(userId: UUID, achievement: String, nextBadgeId: String?, remaining: Int?, at: Instant) {
        Notifications.insert {
            it[Notifications.userId] = userId
            it[type] = NotificationType.ACHIEVEMENT
            it[Notifications.achievement] = achievement
            it[badgeId] = nextBadgeId
            it[Notifications.remaining] = remaining
            it[createdAt] = at.atOffset(ZoneOffset.UTC)
        }
    }

    /** 34 · Un moderador verificó su publicación; [points] son los que ganó con eso (0 si ya la habían verificado). */
    fun verified(userId: UUID, placeId: UUID, placeTitle: String, points: Int, at: Instant) = insert(userId, NotificationType.VERIFIED, at) {
        it[Notifications.placeId] = placeId
        it[Notifications.placeTitle] = placeTitle
        it[Notifications.points] = points
    }

    /** 35 · Rechazada; [reason] es la frase del motivo («la foto no permite reconocer el lugar»). */
    fun rejected(userId: UUID, placeId: UUID, placeTitle: String, reason: String, at: Instant) = insert(userId, NotificationType.REJECTED, at) {
        it[Notifications.placeId] = placeId
        it[Notifications.placeTitle] = placeTitle
        it[Notifications.reason] = reason
    }

    /** 35 · Rechazada por duplicado de [existingId]. */
    fun duplicateRejected(userId: UUID, placeId: UUID, placeTitle: String, existingId: UUID, existingTitle: String, at: Instant) =
        insert(userId, NotificationType.DUPLICATE_REJECTED, at) {
            it[Notifications.placeId] = placeId
            it[Notifications.placeTitle] = placeTitle
            it[existingPlaceId] = existingId
            it[Notifications.existingTitle] = existingTitle
        }

    /** 36 · Pasó a finalizada; [reason] es el motivo en minúscula. */
    fun finalized(userId: UUID, placeId: UUID, placeTitle: String, reason: String, at: Instant) = insert(userId, NotificationType.FINALIZED, at) {
        it[Notifications.placeId] = placeId
        it[Notifications.placeTitle] = placeTitle
        it[Notifications.reason] = reason
    }

    private fun insert(userId: UUID, type: NotificationType, at: Instant, fill: Notifications.(InsertStatement<Number>) -> Unit) {
        Notifications.insert {
            it[Notifications.userId] = userId
            it[Notifications.type] = type
            it[createdAt] = at.atOffset(ZoneOffset.UTC)
            fill(it)
        }
    }

    /** false si el aviso no existe o es de otra persona. */
    fun markRead(userId: UUID, id: UUID, at: Instant): Boolean {
        val mine = Notifications.selectAll().where { (Notifications.id eq id) and (Notifications.userId eq userId) }.count() > 0
        if (mine) {
            Notifications.update({ (Notifications.id eq id) and Notifications.readAt.isNull() }) { it[readAt] = at.atOffset(ZoneOffset.UTC) }
        }
        return mine
    }

    fun markAllRead(userId: UUID, at: Instant) {
        Notifications.update({ (Notifications.userId eq userId) and Notifications.readAt.isNull() }) {
            it[readAt] = at.atOffset(ZoneOffset.UTC)
        }
    }
}
