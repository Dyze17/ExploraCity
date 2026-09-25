package co.edu.uniquindio.exploracity.domain.model

import java.time.Instant
import java.time.YearMonth

/**
 * Qué cuenta el avance de una insignia. La regla y la meta las fija el servidor (SAD: Motor de Reputación y Logros);
 * la app solo sabe nombrar la unidad («3 de 10 lugares verificados»).
 */
enum class BadgeMetric {
    /** Publicaciones enviadas. */
    PUBLICATIONS,

    /** Publicaciones verificadas por un moderador. */
    VERIFIED_PLACES,

    /** Publicaciones verificadas de la categoría de la insignia ([Badge.category]). */
    CATEGORY_PLACES,

    COMMENTS,

    /** Lugares marcados como visitados. */
    VISITS,

    /** Votos «Es importante» que suman los lugares de la persona. */
    VOTES_RECEIVED,
}

/** 27 · Insignia con su avance. El nombre, [howTo] y [tip] son textos del servidor, como el título de un lugar. */
data class Badge(
    val id: String,
    val name: String,
    val metric: BadgeMetric,
    val progress: Int,
    val target: Int,
    /** «Cómo se obtiene» (27A). */
    val howTo: String,
    /** Consejo para conseguirla; solo se muestra mientras está bloqueada. */
    val tip: String? = null,
    val category: Category? = null,
) {
    val unlocked: Boolean get() = progress >= target

    val fraction: Float get() = if (target <= 0) 1f else (progress.toFloat() / target).coerceIn(0f, 1f)
}

/** Orden de 27.a: primero las desbloqueadas; luego las bloqueadas, de la más avanzada a la menos. */
fun List<Badge>.inDisplayOrder(): List<Badge> =
    sortedWith(compareByDescending<Badge> { it.unlocked }.thenByDescending { if (it.unlocked) 0f else it.fraction })

/** Cuántas publicaciones propias hay en cada estado (26: «Activas» son las verificadas). */
data class PublicationCounts(val pending: Int = 0, val verified: Int = 0, val rejected: Int = 0, val finalized: Int = 0) {
    /** «Ver mis 7 publicaciones»: todas, también las rechazadas (22 · «Todas · 7»). */
    val total: Int get() = pending + verified + rejected + finalized

    operator fun get(status: PublicationStatus): Int = when (status) {
        PublicationStatus.PENDING -> pending
        PublicationStatus.VERIFIED -> verified
        PublicationStatus.REJECTED -> rejected
        PublicationStatus.FINALIZED -> finalized
    }

    companion object {
        fun of(statuses: List<PublicationStatus>): PublicationCounts {
            val counts = statuses.groupingBy { it }.eachCount()
            return PublicationCounts(
                pending = counts[PublicationStatus.PENDING] ?: 0,
                verified = counts[PublicationStatus.VERIFIED] ?: 0,
                rejected = counts[PublicationStatus.REJECTED] ?: 0,
                finalized = counts[PublicationStatus.FINALIZED] ?: 0,
            )
        }
    }
}

/**
 * 26 · Lo que la persona ve de sí misma: además de lo público, sus pendientes y rechazadas y el avance de cada
 * insignia. Sin correo: ese dato vive en la cuenta (28), no en el perfil.
 */
data class OwnProfile(
    val author: Author,
    val residency: Residency,
    val city: String,
    val memberSince: YearMonth,
    val publications: PublicationCounts,
    val badges: List<Badge>,
    /** Cuándo se guardó, si viene de lo guardado para ver sin conexión; null si llegó del servidor. */
    val savedAt: Instant? = null,
) {
    val unlockedBadges: Int get() = badges.count { it.unlocked }
}

/**
 * 26 · Camino al siguiente nivel. La barra mide los puntos sobre el umbral del siguiente, como en el lienzo (340 de
 * 500 = 68 %), y no el avance dentro del nivel actual. En el máximo ya no queda camino: 100 %.
 */
data class LevelProgress(val points: Int) {
    val level: UserLevel get() = UserLevel.fromPoints(points)

    val next: UserLevel? get() = level.next

    /** Puntos que faltan para [next]; 0 en el máximo. */
    val remaining: Int get() = next?.let { it.minPoints - points } ?: 0

    val fraction: Float get() = next?.let { (points.toFloat() / it.minPoints).coerceIn(0f, 1f) } ?: 1f

    /** Para el lector: «68 por ciento». */
    val percent: Int get() = next?.let { points * 100 / it.minPoints } ?: 100
}
