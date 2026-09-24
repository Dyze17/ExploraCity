package co.edu.uniquindio.exploracity.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import co.edu.uniquindio.exploracity.R
import co.edu.uniquindio.exploracity.domain.model.UserRole
import co.edu.uniquindio.exploracity.navigation.AuthGraph
import co.edu.uniquindio.exploracity.navigation.ExploraNavHost
import co.edu.uniquindio.exploracity.navigation.Login
import co.edu.uniquindio.exploracity.navigation.MainGraph
import co.edu.uniquindio.exploracity.navigation.TopLevelDestination
import co.edu.uniquindio.exploracity.navigation.navigateToTab
import co.edu.uniquindio.exploracity.navigation.routesWithBottomBar
import co.edu.uniquindio.exploracity.navigation.topLevelDestinations
import co.edu.uniquindio.exploracity.ui.components.ExploraNavigationBar
import co.edu.uniquindio.exploracity.ui.components.NavigationBarItem

/**
 * Raíz de la app: barra inferior en las pantallas raíz de cada pestaña y el grafo de navegación.
 * El Scaffold pinta el fondo del tema en toda la ventana, también detrás de las barras del sistema.
 */
@Composable
fun ExploraApp(navController: NavHostController = rememberNavController()) {
    // Temporal: el rol llegará de la sesión (JWT en DataStore) cuando exista data/; hoy lo elige el inicio de sesión de demo.
    var role by rememberSaveable { mutableStateOf(UserRole.USER) }
    val destination = navController.currentBackStackEntryAsState().value?.destination
    val tabs = topLevelDestinations(role)
    val showBottomBar = destination != null && routesWithBottomBar.any { destination.hasRoute(it) }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (showBottomBar) {
                ExploraNavigationBar(
                    items = tabs.map { tab ->
                        val label = stringResource(tab.label)
                        // Sin datos todavía: los badges llegan con Avisos (25) y la cola de moderación (32).
                        NavigationBarItem(icon = tab.icon, label = label, badgeDescription = badgeDescription(tab, label, 0))
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
            onLogin = { newRole ->
                role = newRole
                navController.navigate(MainGraph) { popUpTo<AuthGraph> { inclusive = true } }
            },
            onLogout = {
                role = UserRole.USER
                navController.navigate(Login) { popUpTo<MainGraph> { inclusive = true } }
            },
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        )
    }
}

@Composable
private fun badgeDescription(tab: TopLevelDestination, label: String, count: Int): String? = when (tab) {
    TopLevelDestination.NOTIFICATIONS -> stringResource(R.string.tab_notifications_badge, label, count)
    TopLevelDestination.MODERATION -> stringResource(R.string.tab_moderation_badge, label, count)
    else -> null
}
