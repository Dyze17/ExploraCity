package co.edu.uniquindio.exploracity.ui.screens.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import co.edu.uniquindio.exploracity.R
import co.edu.uniquindio.exploracity.domain.model.Notification
import co.edu.uniquindio.exploracity.ui.components.EmptyState
import co.edu.uniquindio.exploracity.ui.components.EmptyStateTone
import co.edu.uniquindio.exploracity.ui.components.ExploraButton
import co.edu.uniquindio.exploracity.ui.components.ExploraButtonStyle
import co.edu.uniquindio.exploracity.ui.components.ExploraTopAppBar
import co.edu.uniquindio.exploracity.ui.components.OfflineBanner
import co.edu.uniquindio.exploracity.ui.components.SkeletonBlock
import co.edu.uniquindio.exploracity.ui.components.dashedBorder
import co.edu.uniquindio.exploracity.ui.components.relativeTimeText
import co.edu.uniquindio.exploracity.ui.components.rememberNow
import co.edu.uniquindio.exploracity.ui.components.rememberShimmerBrush
import co.edu.uniquindio.exploracity.ui.components.scaledWithFont
import co.edu.uniquindio.exploracity.ui.theme.ExploraCityTheme
import co.edu.uniquindio.exploracity.ui.theme.ExploraElevation
import co.edu.uniquindio.exploracity.ui.theme.FontScaleThresholds
import co.edu.uniquindio.exploracity.ui.theme.ThemeMode
import co.edu.uniquindio.exploracity.ui.theme.exploraColors
import co.edu.uniquindio.exploracity.ui.theme.exploraShadow
import co.edu.uniquindio.exploracity.viewmodel.NotificationsContent
import co.edu.uniquindio.exploracity.viewmodel.NotificationsUiState
import co.edu.uniquindio.exploracity.viewmodel.NotificationsViewModel
import java.time.Instant

/** 25 · Notificaciones, conectado a su ViewModel. Cada destino lo decide el tipo de aviso. */
@Composable
fun NotificationsRoute(
    onOpenPoi: (String) -> Unit,
    onOpenComments: (String) -> Unit,
    onOpenPublication: (String) -> Unit,
    onOpenBadges: () -> Unit,
    onExplore: () -> Unit,
    viewModel: NotificationsViewModel = viewModel(factory = NotificationsViewModel.factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResumed() }
    NotificationsScreen(
        state = state,
        callbacks = NotificationsCallbacks(
            onOpen = { notification ->
                viewModel.onOpen(notification)
                when (notification) {
                    is Notification.Verified -> onOpenPoi(notification.poiId)
                    is Notification.Finalized -> onOpenPoi(notification.poiId)
                    is Notification.Commented -> onOpenComments(notification.poiId)
                    // La tabla de interacciones de 25 lleva el rechazo por duplicado a 24, donde está el motivo.
                    is Notification.Rejected -> onOpenPublication(notification.publicationId)
                    is Notification.DuplicateRejected -> onOpenPublication(notification.publicationId)
                    is Notification.Achievement -> onOpenBadges()
                }
            },
            onGoToExisting = { notification ->
                viewModel.onOpen(notification)
                onOpenPoi(notification.existingPoiId)
            },
            onMarkAllRead = viewModel::onMarkAllRead,
            onAllReadShown = viewModel::onAllReadShown,
            onRetry = viewModel::onRetry,
            onExplore = onExplore,
        ),
    )
}

class NotificationsCallbacks(
    val onOpen: (Notification) -> Unit = {},
    val onGoToExisting: (Notification.DuplicateRejected) -> Unit = {},
    val onMarkAllRead: () -> Unit = {},
    val onAllReadShown: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onExplore: () -> Unit = {},
)

/**
 * 25.a · «Sin leer» (fondo de contenedor, punto y el prefijo «Sin leer» para el lector, nunca solo color) y
 * «Anteriores». Estilo de tarjetas de 25.a; el lienzo del rechazo por duplicado dibuja filas y «Nuevas», pero el
 * README pide «Sin leer»: la variante de duplicado usa aquí la misma tarjeta con el icono de DuplicateFlag.
 */
@Composable
fun NotificationsScreen(state: NotificationsUiState, callbacks: NotificationsCallbacks, modifier: Modifier = Modifier) {
    val snackbarHostState = remember { SnackbarHostState() }
    AllReadEffect(state.allReadShown, snackbarHostState, callbacks.onAllReadShown)
    val loaded = state.content as? NotificationsContent.Loaded
    // Con fuente grande, «Marcar leídas» en la barra partía el título a mitad de palabra (S20+ al 200 %): pasa a la
    // fila de «Sin leer».
    val markAllInHeader = LocalDensity.current.fontScale > FontScaleThresholds.StackRows

    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxSize()) {
            ExploraTopAppBar(
                title = stringResource(R.string.notifications_title),
                actions = {
                    if (!markAllInHeader && loaded != null && loaded.unread.isNotEmpty()) MarkAllButton(callbacks.onMarkAllRead)
                },
            )
            loaded?.savedAt?.let { savedAt -> SavedBanner(savedAt, state.offline, callbacks.onRetry) }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (val content = state.content) {
                    NotificationsContent.Loading -> NotificationsSkeleton()
                    is NotificationsContent.Loaded -> if (content.items.isEmpty()) {
                        Centered {
                            EmptyState(
                                icon = R.drawable.ic_notifications,
                                title = stringResource(R.string.notifications_empty_title),
                                body = stringResource(R.string.notifications_empty_body),
                            ) {
                                ExploraButton(
                                    stringResource(R.string.notifications_explore),
                                    onClick = callbacks.onExplore,
                                    modifier = Modifier.fillMaxWidth(),
                                    icon = R.drawable.ic_explore,
                                )
                            }
                        }
                    } else {
                        NotificationList(content, callbacks, markAllInHeader)
                    }
                    NotificationsContent.Error -> Centered {
                        EmptyState(
                            icon = R.drawable.ic_sync_problem,
                            title = stringResource(R.string.notifications_error_title),
                            body = stringResource(R.string.notifications_error_body),
                            tone = EmptyStateTone.WARNING,
                        ) {
                            ExploraButton(stringResource(R.string.action_retry), onClick = callbacks.onRetry, modifier = Modifier.fillMaxWidth(), icon = R.drawable.ic_refresh)
                        }
                    }
                    NotificationsContent.Offline -> Centered {
                        EmptyState(
                            icon = R.drawable.ic_cloud_off,
                            title = stringResource(R.string.offline_title),
                            body = stringResource(R.string.notifications_offline_body),
                            tone = EmptyStateTone.WARNING,
                        ) {
                            ExploraButton(stringResource(R.string.action_retry), onClick = callbacks.onRetry, modifier = Modifier.fillMaxWidth(), icon = R.drawable.ic_refresh)
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun AllReadEffect(shown: Boolean, hostState: SnackbarHostState, onShown: () -> Unit) {
    val currentOnShown by rememberUpdatedState(onShown)
    val text = stringResource(R.string.notifications_all_read)
    LaunchedEffect(shown) {
        if (!shown) return@LaunchedEffect
        // Se consume al terminar: si se marcara antes, el cambio de clave cancelaría este efecto y el aviso.
        hostState.showSnackbar(text, withDismissAction = true, duration = SnackbarDuration.Short)
        currentOnShown()
    }
}

/** Sin red (o sin respuesta del servidor): la lista es la guardada y se dice de cuándo es (README 25). */
@Composable
private fun SavedBanner(savedAt: Instant, offline: Boolean, onRetry: () -> Unit) {
    val now by rememberNow()
    OfflineBanner(
        title = stringResource(if (offline) R.string.offline_title else R.string.notifications_not_updated_title),
        body = stringResource(R.string.notifications_saved_body, relativeTimeText(savedAt, now)),
        onRetry = onRetry,
    )
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun MarkAllButton(onClick: () -> Unit) {
    ExploraButton(stringResource(R.string.notifications_mark_all), onClick = onClick, style = ExploraButtonStyle.TEXT)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NotificationList(content: NotificationsContent.Loaded, callbacks: NotificationsCallbacks, markAllInHeader: Boolean) {
    val now by rememberNow()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val unread = content.unread
        if (unread.isNotEmpty()) {
            item(key = "unread-header") {
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    SectionHeader(
                        stringResource(R.string.notifications_unread_header, unread.size),
                        pluralStringResource(R.plurals.notifications_unread_header_spoken, unread.size, unread.size),
                    )
                    if (markAllInHeader) MarkAllButton(callbacks.onMarkAllRead)
                }
            }
            notificationItems(unread, now, callbacks)
        }
        val earlier = content.earlier
        if (earlier.isNotEmpty()) {
            item(key = "earlier-header") {
                val title = stringResource(R.string.notifications_earlier)
                SectionHeader(title, title, Modifier.padding(top = if (unread.isNotEmpty()) 8.dp else 0.dp))
            }
            notificationItems(earlier, now, callbacks)
        }
    }
}

private fun LazyListScope.notificationItems(items: List<Notification>, now: Instant, callbacks: NotificationsCallbacks) {
    items(items, key = { it.id }) { notification -> NotificationCard(notification, now, callbacks) }
}

@Composable
private fun SectionHeader(text: String, spoken: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.W700),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.clearAndSetSemantics {
            contentDescription = spoken
            heading()
        },
    )
}

/**
 * Tarjeta de aviso. Sin leer: primaryContainer, icono del color del tipo sobre blanco y punto; leída: tarjeta neutra
 * con el icono apagado. Toda la tarjeta abre el destino; «Ver el motivo y corregir» e «Ir al lugar existente» son
 * enlaces aparte (48 dp).
 */
@Composable
private fun NotificationCard(notification: Notification, now: Instant, callbacks: NotificationsCallbacks) {
    val scheme = MaterialTheme.colorScheme
    val explora = MaterialTheme.exploraColors
    val unread = !notification.read
    val message = notificationMessage(notification)
    val time = relativeTimeText(notification.createdAt, now)
    val points = (notification as? Notification.Verified)?.points?.takeIf { it > 0 }
    val meta = points?.let { stringResource(R.string.notification_meta, time, pluralStringResource(R.plurals.notification_points, it, it)) } ?: time
    // «…visible para todos, hace 20 minutos»: sin el punto final del mensaje delante de la coma.
    val spoken = "${message.removeSuffix(".")}, $meta".let { if (unread) stringResource(R.string.notification_unread_description, it) else it }
    val shape = MaterialTheme.shapes.large
    val textColor = if (unread) scheme.onPrimaryContainer else explora.textSecondary
    val metaColor = if (unread) explora.onPrimaryContainerAccent else scheme.onSurfaceVariant

    Row(
        Modifier
            .fillMaxWidth()
            .then(if (unread) Modifier else Modifier.exploraShadow(ExploraElevation.Card, shape, explora.shadow))
            .clip(shape)
            .background(if (unread) scheme.primaryContainer else scheme.surfaceContainerLowest)
            .clickable(role = Role.Button) { callbacks.onOpen(notification) }
            .semantics(mergeDescendants = true) { contentDescription = spoken }
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        NotificationIcon(notification)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(message, style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp), color = textColor)
            if (notification is Notification.DuplicateRejected) {
                Text(
                    stringResource(R.string.notification_duplicate_hint),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 19.sp),
                    color = if (unread) scheme.onPrimaryContainer else explora.textSecondary,
                )
            }
            Text(meta, style = MaterialTheme.typography.bodySmall, color = metaColor)
            when (notification) {
                is Notification.Rejected -> NotificationLink(
                    stringResource(R.string.notification_fix),
                    color = if (unread) explora.onPrimaryContainerAccent else scheme.tertiary,
                    onClick = { callbacks.onOpen(notification) },
                )
                is Notification.DuplicateRejected -> NotificationLink(
                    stringResource(R.string.notification_go_existing),
                    color = if (unread) explora.onPrimaryContainerAccent else scheme.tertiary,
                    arrow = true,
                    onClick = { callbacks.onGoToExisting(notification) },
                )
                else -> Unit
            }
        }
        if (unread) {
            Box(Modifier.padding(top = 6.dp).size(10.dp).background(scheme.primary, CircleShape))
        }
    }
}

@Composable
private fun notificationMessage(notification: Notification): String = when (notification) {
    is Notification.Verified -> stringResource(R.string.notification_verified, notification.poiTitle)
    is Notification.Finalized -> stringResource(R.string.notification_finalized, notification.poiTitle)
    is Notification.Commented -> stringResource(R.string.notification_commented, notification.authorName, notification.poiTitle, excerpt(notification.excerpt))
    is Notification.Rejected -> stringResource(R.string.notification_rejected, notification.title, notification.reason)
    is Notification.DuplicateRejected -> stringResource(R.string.notification_duplicate, notification.title, notification.existingTitle)
    is Notification.Achievement -> {
        val achievement = stringResource(R.string.notification_achievement, notification.achievement)
        val next = notification.remaining?.let { remaining ->
            notification.nextBadge?.let { badge -> pluralStringResource(R.plurals.notification_achievement_next, remaining, remaining, badge) }
        }
        if (next != null) "$achievement $next" else achievement
    }
}

/** El comentario completo está en 14: aquí basta el comienzo. */
private fun excerpt(text: String): String = if (text.length <= EXCERPT_LENGTH) text else text.take(EXCERPT_LENGTH).trimEnd() + "…"

private const val EXCERPT_LENGTH = 80

/** Icono de 44 dp del tipo de aviso. El de duplicado lleva el estilo de DuplicateFlag (borde discontinuo) siempre. */
@Composable
private fun NotificationIcon(notification: Notification) {
    val scheme = MaterialTheme.colorScheme
    val explora = MaterialTheme.exploraColors
    val shape = RoundedCornerShape(14.dp)
    if (notification is Notification.DuplicateRejected) {
        val flag = explora.duplicateFlag
        Box(
            Modifier.size(44.dp).background(flag.container, shape).dashedBorder(1.dp, flag.border, 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_join_inner), null, tint = flag.content, modifier = Modifier.size(24.dp))
        }
        return
    }
    val (icon, accent) = when (notification) {
        is Notification.Verified -> R.drawable.ic_verified to explora.status.verified.content
        is Notification.Finalized -> R.drawable.ic_task_alt to explora.status.finalized.content
        is Notification.Commented -> R.drawable.ic_chat_bubble to scheme.onTertiaryContainer
        is Notification.Rejected -> R.drawable.ic_cancel to explora.status.rejected.content
        is Notification.Achievement -> R.drawable.ic_military_tech to explora.warningAccent
        is Notification.DuplicateRejected -> error("dibujado arriba")
    }
    val unread = !notification.read
    val (container: Color, tint: Color) = if (unread) scheme.surfaceContainerLowest to accent else scheme.surfaceContainer to scheme.onSurfaceVariant
    Box(Modifier.size(44.dp).background(container, shape), contentAlignment = Alignment.Center) {
        Icon(painterResource(icon), null, tint = tint, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun NotificationLink(text: String, color: Color, onClick: () -> Unit, arrow: Boolean = false) {
    Row(
        Modifier
            .heightIn(min = 48.dp)
            .clip(MaterialTheme.shapes.small)
            .clickable(role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.W700), color = color)
        if (arrow) Icon(painterResource(R.drawable.ic_arrow_forward), null, tint = color, modifier = Modifier.size(18.dp.scaledWithFont()))
    }
}

/** 25.b · Siluetas con un solo anuncio. */
@Composable
private fun NotificationsSkeleton() {
    val brush = rememberShimmerBrush()
    val loading = stringResource(R.string.notifications_loading)
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clearAndSetSemantics {
                contentDescription = loading
                liveRegion = LiveRegionMode.Polite
            },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SkeletonBlock(brush, Modifier.width(120.dp).height(12.dp), RoundedCornerShape(6.dp))
        listOf(0.9f, 0.7f, 0.8f, 0.6f).forEach { width ->
            Row(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLowest, MaterialTheme.shapes.large).padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SkeletonBlock(brush, Modifier.size(44.dp), RoundedCornerShape(14.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SkeletonBlock(brush, Modifier.fillMaxWidth(width).height(14.dp))
                    SkeletonBlock(brush, Modifier.fillMaxWidth(0.4f).height(12.dp))
                }
            }
        }
    }
}

private val previewNow = Instant.now()
private val previewItems = listOf(
    Notification.Verified("1", previewNow.minusSeconds(1200), read = false, "quinta-de-bolivar", "Quinta de Bolívar", points = 15),
    Notification.DuplicateRejected("2", previewNow.minusSeconds(2700), read = false, "p", "Puerta Falsa, tamales", "la-puerta-falsa", "La Puerta Falsa"),
    Notification.Rejected("3", previewNow.minusSeconds(10800), read = false, "m", "Mirador de La Peña", "la foto no permite reconocer el lugar"),
    Notification.Finalized("4", previewNow.minusSeconds(432000), read = true, "casa-independencia", "Casa de la Independencia"),
)

@Preview(name = "25.a · claro", widthDp = 360, heightDp = 800)
@Composable
private fun NotificationsLightPreview() {
    ExploraCityTheme(ThemeMode.LIGHT) { NotificationsScreen(NotificationsUiState(NotificationsContent.Loaded(previewItems)), NotificationsCallbacks()) }
}

@Preview(name = "25.c · oscuro · vacío", widthDp = 360, heightDp = 800)
@Composable
private fun NotificationsEmptyPreview() {
    ExploraCityTheme(ThemeMode.DARK) { NotificationsScreen(NotificationsUiState(NotificationsContent.Loaded(emptyList())), NotificationsCallbacks()) }
}
