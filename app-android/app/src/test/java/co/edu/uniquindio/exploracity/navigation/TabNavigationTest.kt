package co.edu.uniquindio.exploracity.navigation

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Cambios de pestaña con [navigateToTab] sobre el mismo esqueleto de grafos que [ExploraNavHost], con pantallas vacías.
 * Reporte del 2026-10-05: tras «Volver a explorar» en 20, la pestaña «Perfil» volvía a mostrar la confirmación.
 */
@RunWith(RobolectricTestRunner::class)
class TabNavigationTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var nav: NavHostController

    @Before
    fun setUp() {
        compose.setContent {
            nav = rememberNavController()
            NavHost(nav, startDestination = AuthGraph) {
                navigation<AuthGraph>(startDestination = Login) { composable<Login> {} }
                navigation<MainGraph>(startDestination = ExploreGraph) {
                    navigation<ExploreGraph>(startDestination = Feed) { composable<Feed> {} }
                    navigation<PublishGraph>(startDestination = PublishForm()) {
                        composable<PublishForm> {}
                        composable<PublishSent> {}
                    }
                    navigation<NotificationsGraph>(startDestination = Notifications) { composable<Notifications> {} }
                    navigation<ProfileGraph>(startDestination = Profile) {
                        composable<Profile> {}
                        composable<MyPublications> {}
                    }
                    navigation<ModerationGraph>(startDestination = ModerationQueue) { composable<ModerationQueue> {} }
                }
            }
        }
        // Como onEnterApp: entra al feed (7) sin dejar el acceso debajo.
        act { navigate(MainGraph) { popUpTo<AuthGraph> { inclusive = true } } }
    }

    @Test
    fun `desde el feed, «Volver a explorar» en 20 abre el feed y Perfil abre 26`() {
        publishAndSend()

        act { navigateToTab(TopLevelDestination.EXPLORE) }
        assertAt<Feed>()
        act { navigateToTab(TopLevelDestination.PROFILE) }
        assertAt<Profile>()
        act { navigateToTab(TopLevelDestination.EXPLORE) }
        assertAt<Feed>()
    }

    @Test
    fun `publicar desde Mis publicaciones no deja 20 en la pila guardada de Perfil`() {
        act { navigateToTab(TopLevelDestination.PROFILE) }
        act { navigate(MyPublications()) }
        publishAndSend()

        act { navigateToTab(TopLevelDestination.EXPLORE) }
        assertAt<Feed>()
        // Perfil recupera su propia pila (26 › 22), sin «Publicar» encima.
        act { navigateToTab(TopLevelDestination.PROFILE) }
        assertAt<MyPublications>()
        act { popBackStack() }
        assertAt<Profile>()
    }

    @Test
    fun `publicar desde Avisos no deja 20 en la pila guardada de Avisos`() {
        act { navigateToTab(TopLevelDestination.NOTIFICATIONS) }
        publishAndSend()

        act { navigateToTab(TopLevelDestination.EXPLORE) }
        assertAt<Feed>()
        act { navigateToTab(TopLevelDestination.NOTIFICATIONS) }
        assertAt<Notifications>()
    }

    @Test
    fun `cada pestaña vuelve con su misma entrada y su estado`() {
        val tabs = listOf(
            TopLevelDestination.NOTIFICATIONS,
            TopLevelDestination.PROFILE,
            TopLevelDestination.MODERATION,
            TopLevelDestination.EXPLORE,
        )
        val firstVisit = tabs.associateWith { tab ->
            act { navigateToTab(tab) }
            currentEntryId()
        }

        for (tab in tabs) {
            act { navigateToTab(tab) }
            assertEquals(tab.name, firstVisit[tab], currentEntryId())
        }
    }

    /** «Publicar» (15) y «Enviar» en el paso 5, como en [ExploraNavHost]: llega a la confirmación (20). */
    private fun publishAndSend() {
        act { navigateToTab(TopLevelDestination.PUBLISH) }
        act { navigate(PublishSent("Mirador de la 19")) { popUpTo<PublishForm> { inclusive = true } } }
        assertAt<PublishSent>()
    }

    private fun act(block: NavHostController.() -> Unit) = compose.runOnIdle { nav.block() }

    private fun currentEntryId(): String? = compose.runOnIdle { nav.currentBackStackEntry?.id }

    /** Compara por el nombre de la ruta («Feed», «PublishSent»…) para que un fallo diga en qué pantalla quedó. */
    private inline fun <reified T : Any> assertAt() {
        val route = compose.runOnIdle { nav.currentDestination?.route }
        assertEquals(T::class.simpleName, route?.substringBefore('/')?.substringBefore('?')?.substringAfterLast('.'))
    }
}
