package co.edu.uniquindio.exploracity.service

import co.edu.uniquindio.exploracity.config.CitySettings
import co.edu.uniquindio.exploracity.model.AuthorResponse
import co.edu.uniquindio.exploracity.model.BadgeMetric
import co.edu.uniquindio.exploracity.model.BadgeResponse
import co.edu.uniquindio.exploracity.model.ProfileResponse
import co.edu.uniquindio.exploracity.model.PublicationCountsResponse
import co.edu.uniquindio.exploracity.model.PublicationStatus
import co.edu.uniquindio.exploracity.repository.Activity
import co.edu.uniquindio.exploracity.repository.BadgeDefinition
import co.edu.uniquindio.exploracity.repository.NotificationRepository
import co.edu.uniquindio.exploracity.repository.ReputationRepository
import co.edu.uniquindio.exploracity.repository.UserRecord
import java.time.Clock
import java.time.YearMonth
import java.util.UUID

/**
 * Motor de Reputación y Logros (SAD) · Puntos e insignias con su avance, calculados de lo que hizo la persona. Se usa
 * dentro de una transacción: el perfil (26), «Descargar mis datos» (29) y el perfil público (31) cuentan lo mismo.
 */
class ReputationService(
    private val reputation: ReputationRepository,
    private val notifications: NotificationRepository,
    private val city: CitySettings,
    private val clock: Clock,
) {

    /** 26 · El perfil propio de [user]. */
    fun profileOf(user: UserRecord): ProfileResponse {
        val counts = reputation.statusCounts(user.id)
        return ProfileResponse(
            author = AuthorResponse(user.id.toString(), user.name, reputation.points(user.id)),
            residency = user.residency,
            city = city.name,
            memberSince = memberSince(user).toString(),
            publications = PublicationCountsResponse(
                pending = counts[PublicationStatus.PENDING] ?: 0,
                verified = counts[PublicationStatus.VERIFIED] ?: 0,
                rejected = counts[PublicationStatus.REJECTED] ?: 0,
                finalized = counts[PublicationStatus.FINALIZED] ?: 0,
            ),
            badges = badges(user.id),
            bio = user.bio,
            photo = user.photoUrl,
        )
    }

    /**
     * 27 · Todo el catálogo, en su orden, con el avance de [userId]. Una insignia que ya desbloqueó se queda completa
     * aunque después la cifra baje (un voto que se quita, un comentario de una cuenta eliminada…).
     */
    fun badges(userId: UUID): List<BadgeResponse> {
        val activity = reputation.activity(userId)
        val unlocked = reputation.unlocked(userId)
        return reputation.catalog().map { badge ->
            BadgeResponse(
                id = badge.id,
                name = badge.name,
                metric = badge.metric,
                category = badge.category,
                progress = if (badge.id in unlocked) badge.target else progress(badge, activity),
                target = badge.target,
                howTo = badge.howTo,
                tip = badge.tip,
            )
        }
    }

    /** 31 · Cuántas insignias tiene desbloqueadas. */
    fun unlockedCount(userId: UUID): Int = badges(userId).count { it.progress >= it.target }

    fun points(user: UserRecord): Int = reputation.points(user.id)

    /** El mes en que se creó la cuenta, en la zona horaria de la ciudad. */
    fun memberSince(user: UserRecord): YearMonth = YearMonth.from(user.createdAt.atZone(city.timeZone))

    /**
     * B1 · Después de una acción que mueve las cifras de [userId] (comentar, visitar, recibir un voto…). Cada insignia
     * que llega a su meta se anota una sola vez y trae un aviso de logro con la insignia bloqueada más cercana y lo que
     * le falta. Dentro de la transacción de la acción.
     */
    fun awardBadges(userId: UUID) {
        val activity = reputation.activity(userId)
        val catalog = reputation.catalog()
        val unlocked = reputation.unlocked(userId).toMutableSet()
        val progress = catalog.associate { it.id to progress(it, activity) }
        val reached = catalog.filter { it.id !in unlocked && progress.getValue(it.id) >= it.target }
        if (reached.isEmpty()) return
        val now = clock.instant()
        // Si otra acción a la vez ya la anotó, el aviso es de esa acción.
        val fresh = reached.filter { reputation.unlock(userId, it.id, now) }
        unlocked += reached.map { it.id }
        val next = catalog
            .filter { it.id !in unlocked }
            .maxByOrNull { progress.getValue(it.id).toDouble() / it.target }
        val remaining = next?.let { it.target - progress.getValue(it.id) }
        fresh.forEach { badge -> notifications.achievement(userId, badge.name, next?.id, remaining, now) }
    }

    /** El avance nunca pasa de la meta: «10 de 10», no «12 de 10». */
    private fun progress(badge: BadgeDefinition, activity: Activity): Int {
        val done = when (badge.metric) {
            BadgeMetric.PUBLICATIONS -> activity.publications
            BadgeMetric.VERIFIED_PLACES -> activity.verifiedPlaces
            BadgeMetric.CATEGORY_PLACES -> badge.category?.let { activity.verifiedByCategory[it] } ?: 0
            BadgeMetric.COMMENTS -> activity.comments
            BadgeMetric.VISITS -> activity.visits
            BadgeMetric.VOTES_RECEIVED -> activity.votesReceived
        }
        return done.coerceAtMost(badge.target)
    }
}
