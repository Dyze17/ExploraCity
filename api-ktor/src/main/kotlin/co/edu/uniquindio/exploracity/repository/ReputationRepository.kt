package co.edu.uniquindio.exploracity.repository

import co.edu.uniquindio.exploracity.model.BadgeMetric
import co.edu.uniquindio.exploracity.model.Badges
import co.edu.uniquindio.exploracity.model.Category
import co.edu.uniquindio.exploracity.model.Comments
import co.edu.uniquindio.exploracity.model.Places
import co.edu.uniquindio.exploracity.model.PublicationStatus
import co.edu.uniquindio.exploracity.model.UserBadges
import co.edu.uniquindio.exploracity.model.UserPoints
import co.edu.uniquindio.exploracity.model.Visits
import co.edu.uniquindio.exploracity.model.Votes
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

/** Una insignia del catálogo (V3). */
data class BadgeDefinition(
    val id: String,
    val name: String,
    val metric: BadgeMetric,
    val category: Category?,
    val target: Int,
    val howTo: String,
    val tip: String?,
)

/** Lo que hizo una persona, con lo que se mide el avance de cada insignia. */
data class Activity(
    val publications: Int,
    val verifiedPlaces: Int,
    val verifiedByCategory: Map<Category, Int>,
    val comments: Int,
    val visits: Int,
    val votesReceived: Int,
)

/** Puntos, publicaciones e insignias (26 y 27). Corre dentro de la transacción de quien llama. */
class ReputationRepository {
    /** G2 · De la vista user_points: lo ganado con sus lugares y sus visitas. */
    fun points(userId: UUID): Int = UserPoints.select(UserPoints.points)
        .where { UserPoints.userId eq userId }
        .singleOrNull()
        ?.get(UserPoints.points) ?: 0

    fun statusCounts(userId: UUID): Map<PublicationStatus, Int> {
        val count = Places.id.count()
        return Places.select(Places.status, count)
            .where { Places.authorId eq userId }
            .groupBy(Places.status)
            .associate { it[Places.status] to it[count].toInt() }
    }

    /**
     * Las verificadas cuentan también si después pasaron a finalizadas: ya las revisó un moderador. C1: lo propio no
     * suma, así que las visitas son a lugares de otras personas y los votos, los que otras personas dieron a sus lugares
     * (también los de cuentas eliminadas, que quedan como cifras).
     */
    fun activity(userId: UUID): Activity {
        val count = Places.id.count()
        val verified = Places.select(Places.category, count)
            .where { (Places.authorId eq userId) and (Places.status inList VERIFIED_STATUSES) }
            .groupBy(Places.category)
            .associate { it[Places.category] to it[count].toInt() }
        return Activity(
            publications = Places.selectAll().where { Places.authorId eq userId }.count().toInt(),
            verifiedPlaces = verified.values.sum(),
            verifiedByCategory = verified,
            comments = Comments.selectAll().where { Comments.authorId eq userId }.count().toInt(),
            visits = (Visits innerJoin Places).selectAll()
                .where { (Visits.userId eq userId) and (Places.authorId.isNull() or (Places.authorId neq userId)) }
                .count().toInt(),
            votesReceived = (Votes innerJoin Places).selectAll()
                .where { (Places.authorId eq userId) and (Votes.userId.isNull() or (Votes.userId neq userId)) }
                .count().toInt(),
        )
    }

    /** Las insignias que ya desbloqueó: no se pierden aunque después la cifra baje. */
    fun unlocked(userId: UUID): Set<String> =
        UserBadges.select(UserBadges.badgeId).where { UserBadges.userId eq userId }.map { it[UserBadges.badgeId] }.toSet()

    /** Devuelve false si ya estaba desbloqueada (dos acciones a la vez no la anotan dos veces). */
    fun unlock(userId: UUID, badgeId: String, at: Instant): Boolean = UserBadges.insertIgnore {
        it[UserBadges.userId] = userId
        it[UserBadges.badgeId] = badgeId
        it[unlockedAt] = at.atOffset(ZoneOffset.UTC)
    }.insertedCount > 0

    fun catalog(): List<BadgeDefinition> = Badges.selectAll().orderBy(Badges.position).map { row ->
        BadgeDefinition(
            id = row[Badges.id],
            name = row[Badges.name],
            metric = row[Badges.metric],
            category = row[Badges.category],
            target = row[Badges.target],
            howTo = row[Badges.howTo],
            tip = row[Badges.tip],
        )
    }

    private companion object {
        val VERIFIED_STATUSES = listOf(PublicationStatus.VERIFIED, PublicationStatus.FINALIZED)
    }
}
