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
import co.edu.uniquindio.exploracity.repository.ReputationRepository
import co.edu.uniquindio.exploracity.repository.UserRecord
import java.time.YearMonth

/**
 * Motor de Reputación y Logros (SAD) · Puntos e insignias con su avance, calculados de lo que hizo la persona. Se usa
 * dentro de una transacción: el perfil (26) y «Descargar mis datos» (29) cuentan lo mismo.
 */
class ReputationService(private val reputation: ReputationRepository, private val city: CitySettings) {

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
            badges = badges(user),
            bio = user.bio,
            photo = user.photoUrl,
        )
    }

    /** 27 · Todo el catálogo, en su orden, con el avance de [user]. */
    fun badges(user: UserRecord): List<BadgeResponse> {
        val activity = reputation.activity(user.id)
        return reputation.catalog().map { badge ->
            BadgeResponse(
                id = badge.id,
                name = badge.name,
                metric = badge.metric,
                category = badge.category,
                progress = progress(badge, activity),
                target = badge.target,
                howTo = badge.howTo,
                tip = badge.tip,
            )
        }
    }

    fun points(user: UserRecord): Int = reputation.points(user.id)

    /** El mes en que se creó la cuenta, en la zona horaria de la ciudad. */
    fun memberSince(user: UserRecord): YearMonth = YearMonth.from(user.createdAt.atZone(city.timeZone))

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
