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
import co.edu.uniquindio.exploracity.ui.screens.access.ExpiredLinkScreen
import co.edu.uniquindio.exploracity.ui.screens.access.LoginRoute
import co.edu.uniquindio.exploracity.ui.screens.access.NewPasswordRoute
import co.edu.uniquindio.exploracity.ui.screens.access.OnboardingRoute
import co.edu.uniquindio.exploracity.ui.screens.access.RecoverPasswordRoute
import co.edu.uniquindio.exploracity.ui.screens.access.RecoveryEmailSentRoute
import co.edu.uniquindio.exploracity.ui.screens.access.RegisterRoute
import co.edu.uniquindio.exploracity.ui.screens.access.SplashRoute
import co.edu.uniquindio.exploracity.ui.screens.account.ChangeEmailRoute
import co.edu.uniquindio.exploracity.ui.screens.account.ConfirmEmailRoute
import co.edu.uniquindio.exploracity.ui.screens.account.DeleteAccountRoute
import co.edu.uniquindio.exploracity.ui.screens.account.EmailChangeSentRoute
import co.edu.uniquindio.exploracity.ui.screens.account.EmailLinkExpiredScreen
import co.edu.uniquindio.exploracity.ui.screens.comments.CommentsRoute
import co.edu.uniquindio.exploracity.ui.screens.detail.PoiDetailRoute
import co.edu.uniquindio.exploracity.ui.screens.feed.FeedRoute
import co.edu.uniquindio.exploracity.ui.screens.legal.LegalDocumentsScreen
import co.edu.uniquindio.exploracity.ui.screens.map.FeedMapRoute
import co.edu.uniquindio.exploracity.ui.screens.moderation.ChangeStateRoute
import co.edu.uniquindio.exploracity.ui.screens.moderation.CompareDuplicatesRoute
import co.edu.uniquindio.exploracity.ui.screens.moderation.ModerationQueueRoute
import co.edu.uniquindio.exploracity.ui.screens.moderation.RejectPublicationRoute
import co.edu.uniquindio.exploracity.ui.screens.moderation.ResolvedPublicationsRoute
import co.edu.uniquindio.exploracity.ui.screens.moderation.ReviewDetailRoute
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
import co.edu.uniquindio.exploracity.viewmodel.Decision
import co.edu.uniquindio.exploracity.viewmodel.DecisionNotice
import co.edu.uniquindio.exploracity.viewmodel.FeedViewModel
import co.edu.uniquindio.exploracity.viewmodel.PublicationMessage
import co.edu.uniquindio.exploracity.viewmodel.PublishExit
import co.edu.uniquindio.exploracity.viewmodel.SplashDestination
import co.edu.uniquindio.exploracity.viewmodel.StateTarget

/**
 * Grafo de navegación completo, con los destinos y enlaces que define el README («Navegación e interacciones»): el
 * acceso, y Explorar, Publicar, Avisos, Perfil y Moderación con sus pantallas.
 */
@Composable
fun ExploraNavHost(
    navController: NavHostController,
    role: UserRole,
    onEnterApp: () -> Unit,
    onLogout: (SessionNotice?) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavHost(navController = navController, startDestination = AuthGraph, modifier = modifier) {
        authGraph(navController, onEnterApp)
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

/** Marca en la entrada del inicio de sesión (3) con el correo que el registro (4) encontró con cuenta. */
private const val SUGGESTED_EMAIL_KEY = "correo_sugerido"

/** Marca en la entrada del feed (7) para recibir una vez a quien acaba de crear su cuenta (4). */
private const val WELCOME_NOTICE_KEY = "aviso_bienvenida"

/**
 * Vuelve al inicio de sesión (3), que suele estar debajo en la pila; si no está (un enlace abierto desde fuera), lo abre
 * en lugar de lo demás del acceso. [notice] y [email] son el aviso y el correo con que se llega.
 */
private fun NavController.backToLogin(notice: SessionNotice? = null, email: String? = null) {
    if (!popBackStack<Login>(inclusive = false)) navigate(Login) { popUpTo<AuthGraph>() }
    val entry = currentBackStackEntry ?: return
    if (notice != null) entry.savedStateHandle[SESSION_NOTICE_KEY] = notice.name
    if (email != null) entry.savedStateHandle[SUGGESTED_EMAIL_KEY] = email
}

/** Marca en la revisión siguiente (33) con cuántas quedan: «Verificada. Quedan 6 por revisar» (C1). */
private const val VERIFIED_NOTICE_KEY = "aviso_verificada"

/** Como [VERIFIED_NOTICE_KEY], al llegar desde un rechazo (35): «Rechazada. Quedan 5 por revisar». */
private const val REJECTED_NOTICE_KEY = "aviso_rechazada"

/** Marca en la cola (32) cuando se decidió la última, con la decisión: ya está vacía (37) y lo dice. */
private const val ALL_REVIEWED_KEY = "cola_revisada"

/** Marca en la revisión (33) al volver de 33A con «Verificar como lugar distinto»: abre 34 sin el aviso de duplicado. */
private const val VERIFY_DISTINCT_KEY = "verificar_distinto"

/** Marca en «Resueltas» con el cambio de estado hecho en 36, para decirlo una vez. */
private const val STATE_CHANGED_KEY = "estado_cambiado"

/** C1 · Abre la siguiente pendiente en lugar de la revisión (y de 33A y 35, si estaban encima), con el aviso de lo decidido. */
private fun NavController.toNextReview(id: String, decision: Decision, remaining: Int) {
    navigate(ReviewDetail(id)) { popUpTo<ReviewDetail> { inclusive = true } }
    val key = if (decision == Decision.REJECTED) REJECTED_NOTICE_KEY else VERIFIED_NOTICE_KEY
    currentBackStackEntry?.savedStateHandle?.set(key, remaining)
}

/** C1 · Ya no queda ninguna: vuelve a la cola, que está vacía (37) y dice lo último que se decidió. */
private fun NavController.toEmptyQueue(decision: Decision) {
    popBackStack<ModerationQueue>(inclusive = false)
    currentBackStackEntry?.savedStateHandle?.set(ALL_REVIEWED_KEY, decision.name)
}

/** Marca en la entrada de Ajustes (29) con el correo nuevo ya confirmado, para decirlo una vez. */
private const val EMAIL_CHANGED_KEY = "correo_cambiado"

/** Vuelve a Ajustes (29), que suele estar debajo; si no está (un enlace abierto desde fuera), lo abre. */
private fun NavController.backToSettings(emailChanged: String? = null) {
    if (!popBackStack<Settings>(inclusive = false)) navigate(Settings)
    if (emailChanged != null) currentBackStackEntry?.savedStateHandle?.set(EMAIL_CHANGED_KEY, emailChanged)
}

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

private fun NavGraphBuilder.authGraph(nav: NavController, onEnterApp: () -> Unit) {
    navigation<AuthGraph>(startDestination = Splash) {
        composable<Splash> {
            SplashRoute(
                onDestination = { destination ->
                    when (destination) {
                        SplashDestination.ONBOARDING -> nav.navigate(Onboarding) { popUpTo<Splash> { inclusive = true } }
                        SplashDestination.LOGIN -> nav.navigate(Login) { popUpTo<Splash> { inclusive = true } }
                        SplashDestination.SESSION_ENDED -> {
                            nav.navigate(Login) { popUpTo<Splash> { inclusive = true } }
                            nav.currentBackStackEntry?.savedStateHandle?.set(SESSION_NOTICE_KEY, SessionNotice.SESSION_ENDED.name)
                        }
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
            val suggestedEmail by entry.savedStateHandle.getStateFlow<String?>(SUGGESTED_EMAIL_KEY, null).collectAsStateWithLifecycle()
            LoginRoute(
                onSignedIn = onEnterApp,
                onForgotPassword = { email -> nav.navigate(RecoverPassword(email)) },
                onCreateAccount = { nav.navigate(Register) },
                notice = when (SessionNotice.entries.firstOrNull { it.name == notice }) {
                    SessionNotice.SIGNED_OUT -> stringResource(R.string.session_signed_out)
                    SessionNotice.ACCOUNT_DELETED -> stringResource(R.string.session_account_deleted)
                    SessionNotice.PASSWORD_CHANGED -> stringResource(R.string.session_reset_done)
                    SessionNotice.SESSION_ENDED -> stringResource(R.string.session_ended)
                    null -> null
                },
                onNoticeShown = { entry.savedStateHandle[SESSION_NOTICE_KEY] = null },
                suggestedEmail = suggestedEmail,
                onSuggestedEmailUsed = { entry.savedStateHandle[SUGGESTED_EMAIL_KEY] = null },
            )
        }
        composable<Register> {
            RegisterRoute(
                onBack = nav.back(),
                onRegistered = { registration ->
                    onEnterApp()
                    val notice = if (registration.welcomeEmailSent) WelcomeNotice.ACCOUNT_READY else WelcomeNotice.WELCOME_EMAIL_FAILED
                    nav.currentBackStackEntry?.savedStateHandle?.set(WELCOME_NOTICE_KEY, notice.name)
                },
                onOpenLegal = { tab -> nav.navigate(LegalDocuments(tab)) },
                onSignInInstead = { email -> nav.backToLogin(email = email) },
            )
        }
        composable<LegalDocuments> { entry ->
            LegalDocumentsScreen(initialTab = entry.toRoute<LegalDocuments>().tab, onBack = nav.back())
        }
        composable<RecoverPassword> {
            RecoverPasswordRoute(onBack = nav.back(), onSent = { sent -> nav.navigate(RecoveryEmailSent(sent.email, sent.sentAtMillis)) })
        }
        composable<RecoveryEmailSent> {
            RecoveryEmailSentRoute(onBackToLogin = { nav.backToLogin() }, onOpenLink = { token -> nav.navigate(NewPassword(token)) })
        }
        composable<NewPassword> {
            NewPasswordRoute(
                onBack = nav.back(),
                onSaved = { nav.backToLogin(SessionNotice.PASSWORD_CHANGED) },
                // 6C queda sobre el inicio de sesión: «atrás» no vuelve a un enlace que ya no sirve.
                onExpired = { email -> nav.navigate(ExpiredLink(email)) { popUpTo<Login>() } },
            )
        }
        composable<ExpiredLink> { entry ->
            val email = entry.toRoute<ExpiredLink>().email
            ExpiredLinkScreen(
                onRequestNew = { nav.navigate(RecoverPassword(email)) { popUpTo<Login>() } },
                onBackToLogin = { nav.backToLogin() },
            )
        }
    }
}

private fun NavGraphBuilder.exploreGraph(nav: NavController, role: UserRole) {
    navigation<ExploreGraph>(startDestination = Feed) {
        composable<Feed> { entry ->
            val welcome by entry.savedStateHandle.getStateFlow<String?>(WELCOME_NOTICE_KEY, null).collectAsStateWithLifecycle()
            FeedRoute(
                viewModel = exploreFeedViewModel(nav, entry, role),
                isModerator = role == UserRole.MODERATOR,
                onOpenPoi = { nav.navigate(PoiDetail(it)) },
                onOpenMap = { nav.navigate(FeedMap()) },
                onPublish = { nav.navigateToTab(TopLevelDestination.PUBLISH) },
                onOpenModeration = { nav.navigateToTab(TopLevelDestination.MODERATION) },
                notice = when (WelcomeNotice.entries.firstOrNull { it.name == welcome }) {
                    WelcomeNotice.ACCOUNT_READY -> stringResource(R.string.register_ready)
                    WelcomeNotice.WELCOME_EMAIL_FAILED -> stringResource(R.string.register_welcome_failed)
                    null -> null
                },
                onNoticeShown = { entry.savedStateHandle[WELCOME_NOTICE_KEY] = null },
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
        composable<Settings> { entry ->
            val emailChanged by entry.savedStateHandle.getStateFlow<String?>(EMAIL_CHANGED_KEY, null).collectAsStateWithLifecycle()
            SettingsRoute(
                onBack = nav.back(),
                onOpenPolicy = { nav.navigate(LegalDocuments(LegalTab.POLICY)) },
                onOpenPrivacyNotice = { nav.navigate(LegalDocuments(LegalTab.PRIVACY_NOTICE)) },
                onChangeEmail = { nav.navigate(ChangeEmail()) },
                onDeleteAccount = { nav.navigate(DeleteAccount) },
                onSignedOut = { onLogout(SessionNotice.SIGNED_OUT) },
                onOpenDesignCatalog = { nav.navigate(DesignSystemCatalog) },
                emailChanged = emailChanged,
                onEmailChangedShown = { entry.savedStateHandle[EMAIL_CHANGED_KEY] = null },
            )
        }
        composable<ChangeEmail> {
            ChangeEmailRoute(
                onBack = nav.back(),
                onSent = { sent -> nav.navigate(EmailChangeSent(sent.email, sent.currentEmail, sent.sentAtMillis)) },
            )
        }
        composable<EmailChangeSent> { entry ->
            EmailChangeSentRoute(
                currentEmail = entry.toRoute<EmailChangeSent>().currentEmail,
                onBackToSettings = { nav.backToSettings() },
                onOpenLink = { token -> nav.navigate(ConfirmEmail(token)) },
            )
        }
        composable<ConfirmEmail> {
            ConfirmEmailRoute(
                onBack = nav.back(),
                onConfirmed = { email -> nav.backToSettings(emailChanged = email) },
                // Queda sobre Ajustes: «atrás» no vuelve a un enlace que ya no sirve.
                onExpired = { email -> nav.navigate(EmailLinkExpired(email)) { popUpTo<Settings>() } },
            )
        }
        composable<EmailLinkExpired> { entry ->
            val email = entry.toRoute<EmailLinkExpired>().email
            EmailLinkExpiredScreen(
                onRequestNew = { nav.navigate(ChangeEmail(email)) { popUpTo<Settings>() } },
                onBackToSettings = { nav.backToSettings() },
            )
        }
        composable<DeleteAccount> {
            DeleteAccountRoute(onBack = nav.back(), onDeleted = { onLogout(SessionNotice.ACCOUNT_DELETED) })
        }
        composable<DesignSystemCatalog> { DesignCatalog(Modifier.safeDrawingPadding()) }
    }
}

private fun NavGraphBuilder.moderationGraph(nav: NavController) {
    navigation<ModerationGraph>(startDestination = ModerationQueue) {
        composable<ModerationQueue> { entry ->
            val allReviewed by entry.savedStateHandle.getStateFlow<String?>(ALL_REVIEWED_KEY, null).collectAsStateWithLifecycle()
            ModerationQueueRoute(
                onOpenReview = { id -> nav.navigate(ReviewDetail(id)) },
                onOpenResolved = { nav.navigate(ResolvedPublications) },
                onExplore = { nav.navigateToTab(TopLevelDestination.EXPLORE) },
                allReviewed = allReviewed?.let(Decision::valueOf),
                onAllReviewedShown = { entry.savedStateHandle[ALL_REVIEWED_KEY] = null },
            )
        }
        composable<ReviewDetail> { entry ->
            val id = entry.toRoute<ReviewDetail>().publicationId
            val handle = entry.savedStateHandle
            val verified by handle.getStateFlow<Int?>(VERIFIED_NOTICE_KEY, null).collectAsStateWithLifecycle()
            val rejected by handle.getStateFlow<Int?>(REJECTED_NOTICE_KEY, null).collectAsStateWithLifecycle()
            val verifyDistinct by handle.getStateFlow(VERIFY_DISTINCT_KEY, false).collectAsStateWithLifecycle()
            ReviewDetailRoute(
                onBack = nav.back(),
                onCompare = { nav.navigate(CompareDuplicates(id)) },
                // Con aviso de duplicado, 35 abre con «Duplicado» elegido (README 33).
                onReject = { duplicate -> nav.navigate(RejectPublication(id, duplicate = duplicate)) },
                onNext = { next, remaining -> nav.toNextReview(next, Decision.VERIFIED, remaining) },
                onQueueEmpty = { nav.toEmptyQueue(Decision.VERIFIED) },
                notice = verified?.let { DecisionNotice(Decision.VERIFIED, it) } ?: rejected?.let { DecisionNotice(Decision.REJECTED, it) },
                onNoticeShown = {
                    handle[VERIFIED_NOTICE_KEY] = null
                    handle[REJECTED_NOTICE_KEY] = null
                },
                verifyDistinct = verifyDistinct,
                onVerifyDistinctHandled = { handle[VERIFY_DISTINCT_KEY] = false },
            )
        }
        composable<CompareDuplicates> { entry ->
            val id = entry.toRoute<CompareDuplicates>().publicationId
            CompareDuplicatesRoute(
                onBack = nav.back(),
                onOpenPlace = { nav.navigate(PoiDetail(it)) },
                onVerifyDistinct = {
                    nav.previousBackStackEntry?.savedStateHandle?.set(VERIFY_DISTINCT_KEY, true)
                    nav.popBackStack()
                },
                onRejectDuplicate = { originalId -> nav.navigate(RejectPublication(id, duplicate = true, originalId = originalId)) },
            )
        }
        composable<RejectPublication> {
            RejectPublicationRoute(
                onBack = nav.back(),
                onBackToQueue = { nav.popBackStack<ModerationQueue>(inclusive = false) },
                onNext = { next, remaining -> nav.toNextReview(next, Decision.REJECTED, remaining) },
                onQueueEmpty = { nav.toEmptyQueue(Decision.REJECTED) },
            )
        }
        composable<ResolvedPublications> { entry ->
            val changed by entry.savedStateHandle.getStateFlow<String?>(STATE_CHANGED_KEY, null).collectAsStateWithLifecycle()
            ResolvedPublicationsRoute(
                onBack = nav.back(),
                onOpen = { id -> nav.navigate(FinalizePublication(id)) },
                notice = changed?.let(StateTarget::valueOf),
                onNoticeShown = { entry.savedStateHandle[STATE_CHANGED_KEY] = null },
            )
        }
        composable<FinalizePublication> {
            ChangeStateRoute(
                onBack = nav.back(),
                onDone = { target ->
                    nav.popBackStack()
                    nav.currentBackStackEntry?.savedStateHandle?.set(STATE_CHANGED_KEY, target.name)
                },
            )
        }
    }
}
