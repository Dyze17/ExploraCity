package co.edu.uniquindio.exploracity.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import co.edu.uniquindio.exploracity.R
import co.edu.uniquindio.exploracity.domain.model.UserRole

/** Pestañas de la barra inferior, en el orden del diseño. */
enum class TopLevelDestination(
    val graph: Any,
    @DrawableRes val icon: Int,
    @StringRes val label: Int,
) {
    EXPLORE(ExploreGraph, R.drawable.ic_explore, R.string.tab_explore),
    PUBLISH(PublishGraph, R.drawable.ic_add_location_alt, R.string.tab_publish),
    MODERATION(ModerationGraph, R.drawable.ic_shield_person, R.string.tab_moderation),
    NOTIFICATIONS(NotificationsGraph, R.drawable.ic_notifications, R.string.tab_notifications),
    PROFILE(ProfileGraph, R.drawable.ic_person, R.string.tab_profile),
}

/** Con rol Moderador aparece «Moderación» como 3.ª pestaña (lienzo 7.c); con Usuario hay 4. */
fun topLevelDestinations(role: UserRole): List<TopLevelDestination> =
    TopLevelDestination.entries.filter { it != TopLevelDestination.MODERATION || role == UserRole.MODERATOR }

/** Pantallas raíz de cada pestaña: las únicas que muestran la barra inferior en el diseño. */
val routesWithBottomBar = setOf(Feed::class, Notifications::class, Profile::class, ModerationQueue::class)
