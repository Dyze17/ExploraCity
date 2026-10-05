package co.edu.uniquindio.exploracity.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import co.edu.uniquindio.exploracity.ExploraApplication
import co.edu.uniquindio.exploracity.R
import co.edu.uniquindio.exploracity.domain.model.UserRole
import co.edu.uniquindio.exploracity.navigation.AuthGraph
import co.edu.uniquindio.exploracity.navigation.ConfirmEmail
import co.edu.uniquindio.exploracity.navigation.EmailLink
import co.edu.uniquindio.exploracity.navigation.ExploraNavHost
import co.edu.uniquindio.exploracity.navigation.MainGraph
import co.edu.uniquindio.exploracity.navigation.NewPassword
import co.edu.uniquindio.exploracity.navigation.SessionNotice
import co.edu.uniquindio.exploracity.navigation.Splash
import co.edu.uniquindio.exploracity.navigation.TopLevelDestination
import co.edu.uniquindio.exploracity.navigation.navigateToTab
import co.edu.uniquindio.exploracity.navigation.openLogin
import co.edu.uniquindio.exploracity.navigation.routesWithBottomBar
import co.edu.uniquindio.exploracity.navigation.topLevelDestinations
import co.edu.uniquindio.exploracity.ui.components.ExploraNavigationBar
import co.edu.uniquindio.exploracity.ui.components.NavigationBarItem
import kotlinx.coroutines.flow.first

/**
 * Raíz de la app: barra inferior en las pantallas raíz de cada pestaña y el grafo de navegación.
 * El Scaffold pinta el fondo del tema en toda la ventana, también detrás de las barras del sistema. [emailLink] es el
 * enlace del correo con que se abrió la app; se abre al terminar el arranque (1) y se avisa con [onEmailLinkHandled].
 */
@Composable
fun ExploraApp(
    navController: NavHostController = rememberNavController(),
    emailLink: String? = null,
    onEmailLinkHandled: () -> Unit = {},
) {
    val container = (LocalContext.current.applicationContext as ExploraApplication).container
    // El rol llega con la sesión (3), guardada en el teléfono: sobrevive al cierre de la app.
    val session by container.sessionStore.role.collectAsStateWithLifecycle(initialValue = null)
    val role = session ?: UserRole.USER
    val destination = navController.currentBackStackEntryAsState().value?.destination
    val tabs = topLevelDestinations(role)
    val showBottomBar = destination != null && routesWithBottomBar.any { destination.hasRoute(it) }
    val unread by container.notificationRepository.unreadCount.collectAsStateWithLifecycle()
    val pendingReviews by container.moderationRepository.pendingCount.collectAsStateWithLifecycle()

    // La API ya no acepta la sesión (venció o se cerró desde otro lado): se borra lo de la cuenta y se vuelve a entrar.
    LaunchedEffect(Unit) {
        container.sessionEnded.collect {
            if (container.sessionStore.role.first() == null) return@collect
            container.sessionManager.sessionEnded()
            navController.openLogin(SessionNotice.SESSION_ENDED)
        }
    }

    // Un enlace del correo, cuando el arranque ya decidió por dónde se entra. Confirmar un correo pide la sesión abierta.
    val started = destination != null && !destination.hasRoute<Splash>()
    LaunchedEffect(emailLink, started) {
        if (emailLink == null || !started) return@LaunchedEffect
        when (val link = EmailLink.parse(emailLink)) {
            is EmailLink.ResetPassword -> navController.navigate(NewPassword(link.token))
            is EmailLink.ConfirmEmail -> if (session != null) navController.navigate(ConfirmEmail(link.token))
            null -> Unit
        }
        onEmailLinkHandled()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (showBottomBar) {
                ExploraNavigationBar(
                    items = tabs.map { tab ->
                        val label = stringResource(tab.label)
                        // El de Moderación desaparece cuando la cola llega a cero (37).
                        val count = when (tab) {
                            TopLevelDestination.NOTIFICATIONS -> unread
                            TopLevelDestination.MODERATION -> pendingReviews
                            else -> 0
                        }
                        NavigationBarItem(icon = tab.icon, label = label, badgeCount = count, badgeDescription = badgeDescription(tab, label, count))
                    },
                    selectedIndex = tabs.indexOfFirst { tab -> destination.hierarchy.any { it.hasRoute(tab.graph::class) } }
                        .takeIf { it >= 0 },
                    onSelect = { navController.navigateToTab(tabs[it]) },
                )
            }
        },
    ) { padding ->
        ExploraNavHost(
            navController = navController,
            role = role,
            onEnterApp = { navController.navigate(MainGraph) { popUpTo<AuthGraph> { inclusive = true } } },
            onLogout = { notice -> navController.openLogin(notice) },
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        )
    }
}

@Composable
private fun badgeDescription(tab: TopLevelDestination, label: String, count: Int): String? = when (tab) {
    TopLevelDestination.NOTIFICATIONS -> pluralStringResource(R.plurals.tab_notifications_badge, count, label, count)
    TopLevelDestination.MODERATION -> pluralStringResource(R.plurals.tab_moderation_badge, count, label, count)
    else -> null
}
