package co.edu.uniquindio.exploracity.features.moderation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import co.edu.uniquindio.exploracity.R
import co.edu.uniquindio.exploracity.core.ui.components.BadgeSize
import co.edu.uniquindio.exploracity.core.ui.components.CategoryTag
import co.edu.uniquindio.exploracity.core.ui.components.EmptyState
import co.edu.uniquindio.exploracity.core.ui.components.EmptyStateTone
import co.edu.uniquindio.exploracity.core.ui.components.ExploraButton
import co.edu.uniquindio.exploracity.core.ui.components.ExploraButtonStyle
import co.edu.uniquindio.exploracity.core.ui.components.ExploraSnackbarHost
import co.edu.uniquindio.exploracity.core.ui.components.ExploraTopAppBar
import co.edu.uniquindio.exploracity.core.ui.components.RemotePhoto
import co.edu.uniquindio.exploracity.core.ui.components.StatusBadge
import co.edu.uniquindio.exploracity.core.ui.components.initialFocus
import co.edu.uniquindio.exploracity.core.ui.components.relativeTimeText
import co.edu.uniquindio.exploracity.core.ui.components.rememberNow
import co.edu.uniquindio.exploracity.core.ui.theme.ExploraCityTheme
import co.edu.uniquindio.exploracity.core.ui.theme.ExploraElevation
import co.edu.uniquindio.exploracity.core.ui.theme.FontScaleThresholds
import co.edu.uniquindio.exploracity.core.ui.theme.exploraColors
import co.edu.uniquindio.exploracity.core.ui.theme.exploraShadow
import co.edu.uniquindio.exploracity.domain.model.Category
import co.edu.uniquindio.exploracity.domain.model.PlaceReport
import co.edu.uniquindio.exploracity.domain.model.PlaceReportReason
import co.edu.uniquindio.exploracity.domain.model.PublicationStatus
import co.edu.uniquindio.exploracity.domain.model.ReportedPlace
import co.edu.uniquindio.exploracity.domain.model.ThemeMode
import java.time.Instant

/**
 * v1.4.0 · «Reportados», conectada a su ViewModel. «Ver el lugar» abre 13 ([onOpenPlace]) y «Cambiar estado», 36
 * ([onChangeState]); [notice] es el cambio de estado con que se vuelve de allí.
 */
@Composable
fun ReportedPlacesRoute(
    onBack: () -> Unit,
    onOpenPlace: (String) -> Unit,
    onChangeState: (String) -> Unit,
    notice: StateTarget? = null,
    onNoticeShown: () -> Unit = {},
    viewModel: ReportedPlacesViewModel = viewModel(factory = ReportedPlacesViewModel.factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }
    ReportedPlacesScreen(
        state = state,
        callbacks = ReportedPlacesCallbacks(
            onBack = onBack,
            onRetry = viewModel::onRetry,
            onOpenPlace = onOpenPlace,
            onChangeState = onChangeState,
            onAskDismiss = viewModel::onAskDismiss,
            onCancelDismiss = viewModel::onCancelDismiss,
            onConfirmDismiss = viewModel::onConfirmDismiss,
            onMessageShown = viewModel::onMessageShown,
        ),
        notice = notice,
        onNoticeShown = onNoticeShown,
    )
}

class ReportedPlacesCallbacks(
    val onBack: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onOpenPlace: (String) -> Unit = {},
    val onChangeState: (String) -> Unit = {},
    val onAskDismiss: (ReportedPlace) -> Unit = {},
    val onCancelDismiss: () -> Unit = {},
    val onConfirmDismiss: () -> Unit = {},
    val onMessageShown: () -> Unit = {},
)

/**
 * v1.4.0 · «Reportados» · Sin lienzo: los lugares públicos con reportes abiertos, los más reportados primero. Cada
 * tarjeta trae lo que dijeron (sin quién: es anónimo) y lo que se puede hacer: ver el lugar, descartar los reportes
 * (queda como está) o cambiarle el estado (36: finalizar o volver a pendiente).
 */
@Composable
fun ReportedPlacesScreen(
    state: ReportedPlacesUiState,
    callbacks: ReportedPlacesCallbacks,
    modifier: Modifier = Modifier,
    notice: StateTarget? = null,
    onNoticeShown: () -> Unit = {},
) {
    val snackbarHostState = remember { SnackbarHostState() }
    MessageEffect(state.message, notice, snackbarHostState, callbacks.onMessageShown, onNoticeShown)
    val items = (state.content as? ReportedContent.Loaded)?.items
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        ExploraTopAppBar(
            title = stringResource(R.string.reported_title),
            subtitle = items?.takeIf { it.isNotEmpty() }?.let { pluralStringResource(R.plurals.reported_places_count, it.size, it.size) },
            onBack = callbacks.onBack,
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (val content = state.content) {
                ReportedContent.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                ReportedContent.Offline -> ModerationCentered {
                    EmptyState(
                        icon = R.drawable.ic_cloud_off,
                        title = stringResource(R.string.offline_title),
                        body = stringResource(R.string.reported_offline_body),
                        tone = EmptyStateTone.WARNING,
                    ) { ModerationRetryButton(callbacks.onRetry) }
                }
                ReportedContent.Error -> ModerationCentered {
                    EmptyState(
                        icon = R.drawable.ic_sync_problem,
                        title = stringResource(R.string.reported_error_title),
                        body = stringResource(R.string.reset_error_body),
                        tone = EmptyStateTone.WARNING,
                    ) { ModerationRetryButton(callbacks.onRetry) }
                }
                is ReportedContent.Loaded -> if (content.items.isEmpty()) {
                    ModerationCentered {
                        EmptyState(
                            icon = R.drawable.ic_flag,
                            title = stringResource(R.string.reported_empty_title),
                            body = stringResource(R.string.reported_empty_body),
                        )
                    }
                } else {
                    ReportedList(content.items, callbacks)
                }
            }
            ExploraSnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
        }
    }
    state.confirming?.let { place -> DismissDialog(place, state.dismissing, callbacks.onConfirmDismiss, callbacks.onCancelDismiss) }
}

@Composable
private fun ReportedList(items: List<ReportedPlace>, callbacks: ReportedPlacesCallbacks) {
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp + bottom),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items, key = { it.id }) { place -> ReportedCard(place, callbacks) }
    }
}

/**
 * Un lugar reportado: portada, nombre, categoría y estado, quién lo publicó, cuántos reportes y desde cuándo, cada
 * reporte y las tres acciones. Con fuente grande, «Descartar» y «Cambiar estado» se apilan.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReportedCard(place: ReportedPlace, callbacks: ReportedPlacesCallbacks) {
    val scheme = MaterialTheme.colorScheme
    val explora = MaterialTheme.exploraColors
    val shape = MaterialTheme.shapes.medium
    val now by rememberNow()
    val small = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp)
    Column(
        Modifier
            .fillMaxWidth()
            .exploraShadow(ExploraElevation.Card, shape, explora.shadow)
            .clip(shape)
            .background(scheme.surfaceContainerLowest)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.semantics(mergeDescendants = true) { }, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(scheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_photo_camera), null, tint = explora.textPlaceholder, modifier = Modifier.size(24.dp))
                RemotePhoto(place.photoUrl)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(place.title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp), color = scheme.onSurface)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    CategoryTag(place.category)
                    StatusBadge(place.status, size = BadgeSize.SMALL)
                }
                Text(stringResource(R.string.resolved_author, place.authorName ?: stringResource(R.string.deleted_user)), style = small, color = explora.textSecondary)
                Text(
                    pluralStringResource(R.plurals.reported_reports_count, place.reports.size, place.reports.size) + " · " +
                        stringResource(R.string.reported_first, relativeTimeText(place.firstReportedAt, now)),
                    style = small,
                    color = scheme.error,
                )
            }
        }
        ReportLines(place.reports)
        // «Ver el lugar» como enlace y las dos decisiones a lo ancho: las tres en una fila no cabían en 384 dp (S20+).
        ExploraButton(stringResource(R.string.reported_open_place), onClick = { callbacks.onOpenPlace(place.id) }, style = ExploraButtonStyle.TEXT)
        val dismiss = @Composable { modifier: Modifier ->
            ExploraButton(stringResource(R.string.reported_dismiss), onClick = { callbacks.onAskDismiss(place) }, modifier = modifier, style = ExploraButtonStyle.SECONDARY)
        }
        val changeState = @Composable { modifier: Modifier ->
            ExploraButton(stringResource(R.string.reported_change_state), onClick = { callbacks.onChangeState(place.id) }, modifier = modifier, style = ExploraButtonStyle.SECONDARY)
        }
        if (LocalDensity.current.fontScale > FontScaleThresholds.StackRows) {
            dismiss(Modifier.fillMaxWidth())
            changeState(Modifier.fillMaxWidth())
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                dismiss(Modifier.weight(1f))
                changeState(Modifier.weight(1f))
            }
        }
    }
}

/** «¿Descartar los reportes?»: el foco va a «Cancelar», como en los demás diálogos que no se deshacen. */
@Composable
private fun DismissDialog(place: ReportedPlace, dismissing: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val title = stringResource(R.string.reported_dismiss_title)
    val cancelFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { cancelFocus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.semantics { paneTitle = title },
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    pluralStringResource(R.plurals.reported_dismiss_body, place.reports.size, place.reports.size, place.title),
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                    color = MaterialTheme.exploraColors.textSecondary,
                )
            }
        },
        confirmButton = {
            ExploraButton(stringResource(R.string.reported_dismiss_confirm), onClick = onConfirm, loading = dismissing)
        },
        dismissButton = {
            ExploraButton(
                stringResource(R.string.reported_dismiss_cancel),
                onClick = onDismiss,
                modifier = Modifier.initialFocus(cancelFocus),
                style = ExploraButtonStyle.TEXT,
                enabled = !dismissing,
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
    )
}

/** Los avisos de «Descartar» y, al volver de 36, «La publicación pasó a finalizada.» o «… volvió a pendiente.». */
@Composable
private fun MessageEffect(
    message: ReportedMessage?,
    notice: StateTarget?,
    hostState: SnackbarHostState,
    onMessageShown: () -> Unit,
    onNoticeShown: () -> Unit,
) {
    val currentOnMessageShown by rememberUpdatedState(onMessageShown)
    val currentOnNoticeShown by rememberUpdatedState(onNoticeShown)
    val messageText = message?.let {
        stringResource(
            when (it) {
                ReportedMessage.DISMISSED -> R.string.reported_dismissed
                ReportedMessage.ALREADY_REVIEWED -> R.string.reported_already_reviewed
                ReportedMessage.FAILED -> R.string.reported_dismiss_failed
                ReportedMessage.OFFLINE -> R.string.reported_dismiss_offline
            },
        )
    }
    val noticeText = notice?.let { stringResource(if (it == StateTarget.FINALIZED) R.string.change_state_done_finalized else R.string.change_state_done_pending) }
    LaunchedEffect(message) {
        if (messageText == null) return@LaunchedEffect
        hostState.showSnackbar(messageText, withDismissAction = true)
        currentOnMessageShown()
    }
    LaunchedEffect(notice) {
        if (noticeText == null) return@LaunchedEffect
        hostState.showSnackbar(noticeText, withDismissAction = true)
        currentOnNoticeShown()
    }
}

private val previewNow: Instant = Instant.now()

private val previewReported = listOf(
    ReportedPlace(
        "mirador", "Mirador de la Secreta", Category.NATURE, PublicationStatus.VERIFIED, null, "Ana Ríos",
        listOf(PlaceReportReason.OUTDATED_INFO, PlaceReportReason.WRONG_LOCATION),
        listOf(
            PlaceReport(PlaceReportReason.OUTDATED_INFO, "Ahora abre a las 9:00, no a las 8:00.", previewNow.minusSeconds(3_600)),
            PlaceReport(PlaceReportReason.WRONG_LOCATION, null, previewNow.minusSeconds(172_800)),
        ),
        previewNow.minusSeconds(172_800),
    ),
    ReportedPlace(
        "casa", "Casa de la Independencia", Category.HISTORY, PublicationStatus.FINALIZED, null, null,
        listOf(PlaceReportReason.BAD_PHOTOS),
        listOf(PlaceReport(PlaceReportReason.BAD_PHOTOS, null, previewNow.minusSeconds(600))),
        previewNow.minusSeconds(600),
    ),
)

@Preview(name = "Reportados · claro", widthDp = 360, heightDp = 800)
@Composable
private fun ReportedPreview() {
    ExploraCityTheme(ThemeMode.LIGHT) {
        ReportedPlacesScreen(ReportedPlacesUiState(ReportedContent.Loaded(previewReported)), ReportedPlacesCallbacks())
    }
}

@Preview(name = "Reportados · oscuro", widthDp = 360, heightDp = 800)
@Composable
private fun ReportedDarkPreview() {
    ExploraCityTheme(ThemeMode.DARK) {
        ReportedPlacesScreen(ReportedPlacesUiState(ReportedContent.Loaded(previewReported)), ReportedPlacesCallbacks())
    }
}
