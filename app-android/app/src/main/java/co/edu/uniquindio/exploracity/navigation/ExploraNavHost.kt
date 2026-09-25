package co.edu.uniquindio.exploracity.navigation

import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.toRoute
import co.edu.uniquindio.exploracity.domain.model.PublicationStatus
import co.edu.uniquindio.exploracity.domain.model.UserRole
import co.edu.uniquindio.exploracity.ui.catalog.DesignCatalog
import co.edu.uniquindio.exploracity.ui.components.labelRes
import co.edu.uniquindio.exploracity.ui.screens.PlaceholderLink
import co.edu.uniquindio.exploracity.ui.screens.PlaceholderScreen
import co.edu.uniquindio.exploracity.ui.screens.comments.CommentsRoute
import co.edu.uniquindio.exploracity.ui.screens.detail.PoiDetailRoute
import co.edu.uniquindio.exploracity.ui.screens.feed.FeedRoute
import co.edu.uniquindio.exploracity.ui.screens.map.FeedMapRoute
import co.edu.uniquindio.exploracity.ui.screens.notifications.NotificationsRoute
import co.edu.uniquindio.exploracity.ui.screens.profile.BadgesRoute
import co.edu.uniquindio.exploracity.ui.screens.profile.OwnProfileRoute
import co.edu.uniquindio.exploracity.ui.screens.profile.PublicProfileRoute
import co.edu.uniquindio.exploracity.viewmodel.FeedViewModel

/**
 * Grafo de navegación completo. Cada destino es por ahora una [PlaceholderScreen] con los enlaces que
 * define el README («Navegación e interacciones»); se reemplaza por la pantalla real al implementarla.
 */
@Composable
fun ExploraNavHost(
    navController: NavHostController,
    role: UserRole,
    onLogin: (UserRole) -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NavHost(navController = navController, startDestination = AuthGraph, modifier = modifier) {
        authGraph(navController, onLogin)
        navigation<MainGraph>(startDestination = ExploreGraph) {
            exploreGraph(navController, role)
            publishGraph(navController)
            notificationsGraph(navController)
            profileGraph(navController, onLogout)
            moderationGraph(navController)
        }
    }
}

/** Cambia de pestaña conservando la pila de cada una. «Publicar» se abre encima: al cerrar vuelve a la anterior (15A). */
fun NavController.navigateToTab(tab: TopLevelDestination) {
    if (tab == TopLevelDestination.PUBLISH) {
        navigate(PublishGraph) { launchSingleTop = true }
        return
    }
    navigate(tab.graph) {
        popUpTo<Feed> { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun NavController.back(): () -> Unit = { popBackStack() }

private fun PublicationStatus?.toFilter(): PublicationFilter = when (this) {
    null -> PublicationFilter.ALL
    PublicationStatus.PENDING -> PublicationFilter.PENDING
    PublicationStatus.VERIFIED -> PublicationFilter.VERIFIED
    PublicationStatus.REJECTED -> PublicationFilter.REJECTED
    PublicationStatus.FINALIZED -> PublicationFilter.FINALIZED
}

private fun PublicationFilter.toStatus(): PublicationStatus? = when (this) {
    PublicationFilter.ALL -> null
    PublicationFilter.PENDING -> PublicationStatus.PENDING
    PublicationFilter.VERIFIED -> PublicationStatus.VERIFIED
    PublicationFilter.REJECTED -> PublicationStatus.REJECTED
    PublicationFilter.FINALIZED -> PublicationStatus.FINALIZED
}

/**
 * El FeedViewModel vive en el grafo de Explorar, no en cada pantalla: lista (7) y mapa (8) comparten búsqueda,
 * filtros y la hoja 9, y el mapa muestra los mismos chips activos que la lista.
 */
@Composable
private fun exploreFeedViewModel(nav: NavController, entry: NavBackStackEntry, role: UserRole): FeedViewModel {
    val exploreEntry = remember(entry) { nav.getBackStackEntry<ExploreGraph>() }
    return viewModel(viewModelStoreOwner = exploreEntry, factory = FeedViewModel.factory(role == UserRole.MODERATOR))
}

private fun link(label: String, onClick: () -> Unit) = PlaceholderLink(label, onClick)

private fun NavGraphBuilder.authGraph(nav: NavController, onLogin: (UserRole) -> Unit) {
    navigation<AuthGraph>(startDestination = Splash) {
        composable<Splash> {
            PlaceholderScreen(
                "1", "Splash",
                listOf(
                    link("Primera vez → Onboarding") { nav.navigate(Onboarding) { popUpTo<Splash> { inclusive = true } } },
                    link("Onboarding ya visto → Inicio de sesión") { nav.navigate(Login) { popUpTo<Splash> { inclusive = true } } },
                ),
            )
        }
        composable<Onboarding> {
            PlaceholderScreen(
                "2", "Onboarding",
                listOf(
                    link("Saltar") { nav.navigate(Login) { popUpTo<Onboarding> { inclusive = true } } },
                    link("Crear mi cuenta") { nav.navigate(Register) },
                    link("Ya tengo cuenta") { nav.navigate(Login) { popUpTo<Onboarding> { inclusive = true } } },
                ),
            )
        }
        composable<Login> {
            PlaceholderScreen(
                "3", "Inicio de sesión",
                listOf(
                    link("Iniciar sesión") { onLogin(UserRole.USER) },
                    link("Iniciar sesión como moderador (demo)") { onLogin(UserRole.MODERATOR) },
                    link("¿Olvidaste tu contraseña?") { nav.navigate(RecoverPassword) },
                    link("Crear una cuenta") { nav.navigate(Register) },
                ),
            )
        }
        composable<Register> {
            PlaceholderScreen(
                "4", "Registro",
                listOf(
                    link("Crear cuenta") { onLogin(UserRole.USER) },
                    link("Política de tratamiento de datos") { nav.navigate(LegalDocuments(LegalTab.POLICY)) },
                    link("Aviso de privacidad") { nav.navigate(LegalDocuments(LegalTab.PRIVACY_NOTICE)) },
                ),
                onBack = nav.back(),
            )
        }
        composable<LegalDocuments> { entry ->
            val tab = entry.toRoute<LegalDocuments>().tab
            PlaceholderScreen(
                "4A", if (tab == LegalTab.POLICY) "Documentos legales · Política" else "Documentos legales · Aviso de privacidad",
                listOf(link("Entendido", nav.back())),
                onBack = nav.back(),
            )
        }
        composable<RecoverPassword> {
            PlaceholderScreen("5", "Recuperar contraseña", listOf(link("Enviar enlace") { nav.navigate(RecoveryEmailSent) }), onBack = nav.back())
        }
        composable<RecoveryEmailSent> {
            PlaceholderScreen(
                "6", "Correo enviado",
                listOf(
                    link("Abrir el enlace del correo (demo)") { nav.navigate(NewPassword) },
                    link("Volver a iniciar sesión") { nav.popBackStack(Login, inclusive = false) },
                ),
                onBack = nav.back(),
            )
        }
        composable<NewPassword> {
            PlaceholderScreen(
                "6.b", "Nueva contraseña",
                listOf(
                    link("Guardar contraseña") { nav.popBackStack(Login, inclusive = false) },
                    link("El enlace venció (demo)") { nav.navigate(ExpiredLink) { popUpTo<NewPassword> { inclusive = true } } },
                ),
                onBack = nav.back(),
            )
        }
        composable<ExpiredLink> {
            PlaceholderScreen(
                "6C", "Enlace vencido",
                listOf(
                    link("Pedir otro enlace") { nav.navigate(RecoverPassword) { popUpTo<RecoverPassword> { inclusive = true } } },
                    link("Volver a iniciar sesión") { nav.popBackStack(Login, inclusive = false) },
                ),
            )
        }
    }
}

private fun NavGraphBuilder.exploreGraph(nav: NavController, role: UserRole) {
    navigation<ExploreGraph>(startDestination = Feed) {
        composable<Feed> { entry ->
            FeedRoute(
                viewModel = exploreFeedViewModel(nav, entry, role),
                isModerator = role == UserRole.MODERATOR,
                onOpenPoi = { nav.navigate(PoiDetail(it)) },
                onOpenMap = { nav.navigate(FeedMap()) },
                onPublish = { nav.navigateToTab(TopLevelDestination.PUBLISH) },
                onOpenModeration = { nav.navigateToTab(TopLevelDestination.MODERATION) },
            )
        }
        composable<FeedMap> { entry ->
            FeedMapRoute(
                feedViewModel = exploreFeedViewModel(nav, entry, role),
                // Vuelve a la lista aunque el mapa se haya abierto desde otra pantalla (p. ej. el detalle).
                onOpenList = {
                    nav.navigate(Feed) {
                        popUpTo<Feed>()
                        launchSingleTop = true
                    }
                },
                onOpenPoi = { nav.navigate(PoiDetail(it)) },
            )
        }
        composable<PoiDetail> {
            PoiDetailRoute(
                onBack = nav.back(),
                onOpenComments = { nav.navigate(Comments(it)) },
                onAddComment = { nav.navigate(Comments(it, write = true)) },
                onOpenAuthor = { nav.navigate(PublicProfile(it)) },
                onOpenMap = { nav.navigate(FeedMap(focusPoiId = it)) },
            )
        }
        composable<Comments> { CommentsRoute(onBack = nav.back()) }
        composable<PublicProfile> {
            PublicProfileRoute(onBack = nav.back(), onOpenPoi = { nav.navigate(PoiDetail(it)) })
        }
    }
}

private fun NavGraphBuilder.publishGraph(nav: NavController) {
    navigation<PublishGraph>(startDestination = PublishForm) {
        composable<PublishForm> {
            PlaceholderScreen(
                "15–19", "Publicar un lugar",
                listOf(link("Enviar a verificación (paso 5)") { nav.navigate(PublishSent) { popUpTo<PublishForm> { inclusive = true } } }),
                onBack = nav.back(),
            )
        }
        composable<PublishSent> {
            PlaceholderScreen(
                "20", "Enviada a verificación",
                listOf(
                    link("Ver mis publicaciones") { nav.navigate(MyPublications()) { popUpTo<PublishGraph> { inclusive = true } } },
                    link("Publicar otro lugar") { nav.navigate(PublishForm) { popUpTo<PublishSent> { inclusive = true } } },
                    link("Volver a explorar") { nav.popBackStack<PublishGraph>(inclusive = true) },
                ),
            )
        }
    }
}

private fun NavGraphBuilder.notificationsGraph(nav: NavController) {
    navigation<NotificationsGraph>(startDestination = Notifications) {
        composable<Notifications> {
            NotificationsRoute(
                onOpenPoi = { nav.navigate(PoiDetail(it)) },
                onOpenComments = { nav.navigate(Comments(it)) },
                onOpenPublication = { nav.navigate(RejectedPublication(it)) },
                onOpenBadges = { nav.navigate(Badges) },
                onExplore = { nav.navigateToTab(TopLevelDestination.EXPLORE) },
            )
        }
    }
}

private fun NavGraphBuilder.profileGraph(nav: NavController, onLogout: () -> Unit) {
    navigation<ProfileGraph>(startDestination = Profile) {
        composable<Profile> {
            OwnProfileRoute(
                onOpenSettings = { nav.navigate(Settings) },
                onEditProfile = { nav.navigate(EditProfile) },
                onOpenBadges = { nav.navigate(Badges) },
                onOpenPublications = { status -> nav.navigate(MyPublications(status.toFilter())) },
            )
        }
        composable<Badges> { BadgesRoute(onBack = nav.back()) }
        composable<EditProfile> { PlaceholderScreen("28", "Editar perfil", emptyList(), onBack = nav.back()) }
        composable<MyPublications> { entry ->
            // Provisional hasta construir 22: el título dice con qué filtro se abrió desde las cifras del perfil (26).
            val status = entry.toRoute<MyPublications>().filter.toStatus()
            PlaceholderScreen(
                "22",
                if (status == null) "Mis publicaciones" else "Mis publicaciones · ${stringResource(status.labelRes)}",
                listOf(
                    link("Café La Fonda · editar") { nav.navigate(EditPublication("cafe-la-fonda")) },
                    link("Mirador del Alto · rechazada") { nav.navigate(RejectedPublication("mirador-del-alto")) },
                ),
                onBack = nav.back(),
            )
        }
        composable<EditPublication> { PlaceholderScreen("23", "Editar publicación", emptyList(), onBack = nav.back()) }
        composable<RejectedPublication> {
            PlaceholderScreen(
                "24", "Publicación rechazada",
                listOf(
                    link("Corregir y reenviar") { nav.navigateToTab(TopLevelDestination.PUBLISH) },
                    link("Ir al lugar existente") { nav.navigate(PoiDetail("cafe-las-acacias")) },
                ),
                onBack = nav.back(),
            )
        }
        composable<Settings> {
            PlaceholderScreen(
                "29", "Ajustes",
                listOf(
                    link("Política de tratamiento de datos") { nav.navigate(LegalDocuments(LegalTab.POLICY)) },
                    link("Aviso de privacidad") { nav.navigate(LegalDocuments(LegalTab.PRIVACY_NOTICE)) },
                    link("Cerrar sesión", onLogout),
                    link("Eliminar mi cuenta") { nav.navigate(DeleteAccount) },
                    link("Catálogo del sistema de diseño (desarrollo)") { nav.navigate(DesignSystemCatalog) },
                ),
                onBack = nav.back(),
            )
        }
        composable<DeleteAccount> {
            PlaceholderScreen(
                "30", "Eliminar cuenta",
                listOf(link("Eliminar cuenta (demo)", onLogout), link("Mejor no, volver", nav.back())),
                onBack = nav.back(),
            )
        }
        composable<DesignSystemCatalog> { DesignCatalog(Modifier.safeDrawingPadding()) }
    }
}

private fun NavGraphBuilder.moderationGraph(nav: NavController) {
    navigation<ModerationGraph>(startDestination = ModerationQueue) {
        composable<ModerationQueue> {
            PlaceholderScreen(
                "32", "Moderación",
                listOf(
                    link("Mirador del Alto de la Cruz") { nav.navigate(ReviewDetail("mirador-del-alto")) },
                    link("Café La Fonda · posible duplicado") { nav.navigate(ReviewDetail("cafe-la-fonda")) },
                ),
            )
        }
        composable<ReviewDetail> { entry ->
            val id = entry.toRoute<ReviewDetail>().publicationId
            PlaceholderScreen(
                "33", "Detalle de revisión",
                listOf(
                    link("Comparar lugares") { nav.navigate(CompareDuplicates(id)) },
                    link("Verificar (demo)") { nav.popBackStack<ModerationQueue>(inclusive = false) },
                    link("Rechazar") { nav.navigate(RejectPublication(id)) },
                    link("Pasar a finalizada") { nav.navigate(FinalizePublication(id)) },
                ),
                onBack = nav.back(),
            )
        }
        composable<CompareDuplicates> { entry ->
            val id = entry.toRoute<CompareDuplicates>().publicationId
            PlaceholderScreen(
                "33A", "Comparar lugares",
                listOf(
                    link("Verificar como lugar distinto (demo)") { nav.popBackStack<ModerationQueue>(inclusive = false) },
                    link("Rechazar por duplicado") { nav.navigate(RejectPublication(id)) },
                ),
                onBack = nav.back(),
            )
        }
        composable<RejectPublication> {
            PlaceholderScreen(
                "35", "Rechazar con motivo",
                listOf(link("Rechazar (demo)") { nav.popBackStack<ModerationQueue>(inclusive = false) }),
                onBack = nav.back(),
            )
        }
        composable<FinalizePublication> {
            PlaceholderScreen(
                "36", "Pasar a finalizada",
                listOf(link("Pasar a finalizada (demo)") { nav.popBackStack<ModerationQueue>(inclusive = false) }),
                onBack = nav.back(),
            )
        }
    }
}
