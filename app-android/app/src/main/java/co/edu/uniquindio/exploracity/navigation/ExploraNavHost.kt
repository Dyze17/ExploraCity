package co.edu.uniquindio.exploracity.navigation

import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.toRoute
import co.edu.uniquindio.exploracity.R
import co.edu.uniquindio.exploracity.domain.model.SentSummary
import co.edu.uniquindio.exploracity.domain.model.UserRole
import co.edu.uniquindio.exploracity.ui.catalog.DesignCatalog
import co.edu.uniquindio.exploracity.ui.screens.PlaceholderLink
import co.edu.uniquindio.exploracity.ui.screens.PlaceholderScreen
import co.edu.uniquindio.exploracity.ui.screens.access.LoginRoute
import co.edu.uniquindio.exploracity.ui.screens.access.OnboardingRoute
import co.edu.uniquindio.exploracity.ui.screens.access.SplashRoute
import co.edu.uniquindio.exploracity.ui.screens.account.DeleteAccountRoute
import co.edu.uniquindio.exploracity.ui.screens.comments.CommentsRoute
import co.edu.uniquindio.exploracity.ui.screens.detail.PoiDetailRoute
import co.edu.uniquindio.exploracity.ui.screens.feed.FeedRoute
import co.edu.uniquindio.exploracity.ui.screens.legal.LegalDocumentsScreen
import co.edu.uniquindio.exploracity.ui.screens.map.FeedMapRoute
import co.edu.uniquindio.exploracity.ui.screens.notifications.NotificationsRoute
import co.edu.uniquindio.exploracity.ui.screens.profile.BadgesRoute
import co.edu.uniquindio.exploracity.ui.screens.profile.EditProfileRoute
import co.edu.uniquindio.exploracity.ui.screens.profile.OwnProfileRoute
import co.edu.uniquindio.exploracity.ui.screens.profile.PublicProfileRoute
import co.edu.uniquindio.exploracity.ui.screens.publication.EditPublicationRoute
import co.edu.uniquindio.exploracity.ui.screens.publication.MyPublicationsRoute
import co.edu.uniquindio.exploracity.ui.screens.publication.RejectedPublicationRoute
import co.edu.uniquindio.exploracity.ui.screens.publish.PublishFormRoute
import co.edu.uniquindio.exploracity.ui.screens.publish.PublishSentScreen
import co.edu.uniquindio.exploracity.ui.screens.settings.SettingsRoute
import co.edu.uniquindio.exploracity.viewmodel.FeedViewModel
import co.edu.uniquindio.exploracity.viewmodel.PublicationMessage
import co.edu.uniquindio.exploracity.viewmodel.PublishExit
import co.edu.uniquindio.exploracity.viewmodel.SplashDestination

/**
 * Grafo de navegación completo. Cada destino es por ahora una [PlaceholderScreen] con los enlaces que
 * define el README («Navegación e interacciones»); se reemplaza por la pantalla real al implementarla.
 */
@Composable
fun ExploraNavHost(
    navController: NavHostController,
    role: UserRole,
    onLogin: (UserRole) -> Unit,
    onEnterApp: () -> Unit,
    onLogout: (SessionNotice?) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavHost(navController = navController, startDestination = AuthGraph, modifier = modifier) {
        authGraph(navController, onLogin, onEnterApp)
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

/** Marca en la entrada del perfil (26) para avisar una vez, al volver de 28, que se guardó. */
private const val PROFILE_SAVED_KEY = "perfil_guardado"

/** Marca en la entrada del inicio de sesión (3) para avisar una vez «Cerraste sesión». */
private const val SESSION_NOTICE_KEY = "aviso_sesion"

/** Sale de la app al inicio de sesión (3) sin dejar nada detrás, con [notice] para decirlo allí. */
fun NavController.openLogin(notice: SessionNotice?) {
    navigate(Login) { popUpTo<MainGraph> { inclusive = true } }
    if (notice != null) currentBackStackEntry?.savedStateHandle?.set(SESSION_NOTICE_KEY, notice.name)
}

/**
 * Marca en la entrada de 22 (no en el SavedStateHandle de su ViewModel, que es otro) para avisar una vez, al volver de 23
 * o 24, que la publicación se eliminó o se guardó.
 */
private const val PUBLICATION_MESSAGE_KEY = "aviso_publicacion"

/**
 * Vuelve a Mis publicaciones (22) si ya estaba en la pila, con su filtro; si no, la abre en lugar de la pantalla
 * actual, así «atrás» no regresa a una publicación que ya se eliminó.
 */
private fun NavController.openMyPublications(message: PublicationMessage? = null) {
    if (!popBackStack<MyPublications>(inclusive = false)) {
        val current = currentBackStackEntry?.destination?.id
        navigate(MyPublications()) { if (current != null) popUpTo(current) { inclusive = true } }
    }
    // 22 lo dice («Publicación eliminada», «Guardamos los cambios…») y se pone al día.
    if (message != null) currentBackStackEntry?.savedStateHandle?.set(PUBLICATION_MESSAGE_KEY, message.name)
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

private fun NavGraphBuilder.authGraph(nav: NavController, onLogin: (UserRole) -> Unit, onEnterApp: () -> Unit) {
    navigation<AuthGraph>(startDestination = Splash) {
        composable<Splash> {
            SplashRoute(
                onDestination = { destination ->
                    when (destination) {
                        SplashDestination.ONBOARDING -> nav.navigate(Onboarding) { popUpTo<Splash> { inclusive = true } }
                        SplashDestination.LOGIN -> nav.navigate(Login) { popUpTo<Splash> { inclusive = true } }
                        SplashDestination.FEED -> onEnterApp()
                    }
                },
            )
        }
        composable<Onboarding> {
            OnboardingRoute(
                onSkip = { nav.navigate(Login) { popUpTo<Onboarding> { inclusive = true } } },
                // Atrás desde el registro vuelve al inicio de sesión, no al onboarding ya visto.
                onCreateAccount = {
                    nav.navigate(Login) { popUpTo<Onboarding> { inclusive = true } }
                    nav.navigate(Register)
                },
                onHaveAccount = { nav.navigate(Login) { popUpTo<Onboarding> { inclusive = true } } },
            )
        }
        composable<Login> { entry ->
            val notice by entry.savedStateHandle.getStateFlow<String?>(SESSION_NOTICE_KEY, null).collectAsStateWithLifecycle()
            LoginRoute(
                onSignedIn = onEnterApp,
                onForgotPassword = { nav.navigate(RecoverPassword) },
                onCreateAccount = { nav.navigate(Register) },
                notice = when (SessionNotice.entries.firstOrNull { it.name == notice }) {
                    SessionNotice.SIGNED_OUT -> stringResource(R.string.session_signed_out)
                    SessionNotice.ACCOUNT_DELETED -> stringResource(R.string.session_account_deleted)
                    null -> null
                },
                onNoticeShown = { entry.savedStateHandle[SESSION_NOTICE_KEY] = null },
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
            LegalDocumentsScreen(initialTab = entry.toRoute<LegalDocuments>().tab, onBack = nav.back())
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
        composable<PoiDetail> { entry ->
            PoiDetailRoute(
                focusComment = entry.toRoute<PoiDetail>().focusComment,
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
    navigation<PublishGraph>(startDestination = PublishForm()) {
        composable<PublishForm> {
            PublishFormRoute(
                onExit = { exit ->
                    when (exit) {
                        // Cerrar o «Guardar»: vuelve a la pestaña desde la que se abrió (15A).
                        PublishExit.Closed -> nav.popBackStack()
                        is PublishExit.Sent -> {
                            val summary = exit.summary
                            val route = PublishSent(summary.title, summary.possibleDuplicate, summary.queued, summary.firstPublicationPoints ?: 0)
                            nav.navigate(route) { popUpTo<PublishForm> { inclusive = true } }
                        }
                    }
                },
                // 17A · «Ver este lugar»: al volver, el formulario sigue intacto.
                onOpenPlace = { id -> nav.navigate(PoiDetail(id)) },
            )
        }
        composable<PublishSent> { entry ->
            val route = entry.toRoute<PublishSent>()
            PublishSentScreen(
                summary = SentSummary(route.title, route.possibleDuplicate, route.queued, route.firstPublicationPoints.takeIf { it > 0 }),
                onMyPublications = { nav.openMyPublications() },
                onPublishAnother = { nav.navigate(PublishForm()) { popUpTo<PublishSent> { inclusive = true } } },
                onExplore = { nav.navigateToTab(TopLevelDestination.EXPLORE) },
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

private fun NavGraphBuilder.profileGraph(nav: NavController, onLogout: (SessionNotice?) -> Unit) {
    navigation<ProfileGraph>(startDestination = Profile) {
        composable<Profile> { entry ->
            val saved by entry.savedStateHandle.getStateFlow(PROFILE_SAVED_KEY, false).collectAsStateWithLifecycle()
            OwnProfileRoute(
                onOpenSettings = { nav.navigate(Settings) },
                onEditProfile = { nav.navigate(EditProfile) },
                onOpenBadges = { nav.navigate(Badges) },
                onOpenPublications = { status -> nav.navigate(MyPublications(status.toFilter())) },
                profileSaved = saved,
                onProfileSavedShown = { entry.savedStateHandle[PROFILE_SAVED_KEY] = false },
            )
        }
        composable<Badges> { BadgesRoute(onBack = nav.back()) }
        composable<EditProfile> {
            EditProfileRoute(
                onLeave = nav.back(),
                onSaved = {
                    // 26 lo dice al volver («Guardamos tu perfil.») y se pone al día.
                    nav.previousBackStackEntry?.savedStateHandle?.set(PROFILE_SAVED_KEY, true)
                    nav.popBackStack()
                },
            )
        }
        composable<MyPublications> { entry ->
            val message by entry.savedStateHandle.getStateFlow<String?>(PUBLICATION_MESSAGE_KEY, null).collectAsStateWithLifecycle()
            MyPublicationsRoute(
                messageFromElsewhere = PublicationMessage.entries.firstOrNull { it.name == message },
                onMessageFromElsewhereHandled = { entry.savedStateHandle[PUBLICATION_MESSAGE_KEY] = null },
                onBack = nav.back(),
                onOpenPlace = { nav.navigate(PoiDetail(it)) },
                onOpenRejected = { nav.navigate(RejectedPublication(it)) },
                onEdit = { nav.navigate(EditPublication(it)) },
                onOpenComments = { nav.navigate(Comments(it)) },
                onResubmit = { id, step -> nav.navigate(PublishForm(resubmitId = id, step = step)) },
                onPublish = { nav.navigateToTab(TopLevelDestination.PUBLISH) },
            )
        }
        composable<EditPublication> {
            EditPublicationRoute(
                onLeave = nav.back(),
                onDone = { message -> nav.openMyPublications(message) },
                // 17A · «Ver este lugar» desde «Cambiar ubicación»: al volver, la edición sigue intacta.
                onOpenPlace = { id -> nav.navigate(PoiDetail(id)) },
            )
        }
        composable<RejectedPublication> {
            RejectedPublicationRoute(
                onBack = nav.back(),
                onResubmit = { id, step -> nav.navigate(PublishForm(resubmitId = id, step = step)) },
                onOpenExisting = { nav.navigate(PoiDetail(it, focusComment = true)) },
                onOpenMine = { nav.openMyPublications() },
                onDeleted = { nav.openMyPublications(PublicationMessage.DELETED) },
            )
        }
        composable<Settings> {
            SettingsRoute(
                onBack = nav.back(),
                onOpenPolicy = { nav.navigate(LegalDocuments(LegalTab.POLICY)) },
                onOpenPrivacyNotice = { nav.navigate(LegalDocuments(LegalTab.PRIVACY_NOTICE)) },
                onChangeEmail = { nav.navigate(ChangeEmail) },
                onDeleteAccount = { nav.navigate(DeleteAccount) },
                onSignedOut = { onLogout(SessionNotice.SIGNED_OUT) },
                onOpenDesignCatalog = { nav.navigate(DesignSystemCatalog) },
            )
        }
        composable<ChangeEmail> {
            PlaceholderScreen("sin número (llega con 1–6)", stringResource(R.string.change_email_title), emptyList(), onBack = nav.back())
        }
        composable<DeleteAccount> {
            DeleteAccountRoute(onBack = nav.back(), onDeleted = { onLogout(SessionNotice.ACCOUNT_DELETED) })
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
