package co.edu.uniquindio.exploracity.ui.screens.moderation

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import co.edu.uniquindio.exploracity.R
import co.edu.uniquindio.exploracity.domain.model.ModerationWork
import co.edu.uniquindio.exploracity.domain.model.ReviewQueue
import co.edu.uniquindio.exploracity.domain.model.ThemeMode
import co.edu.uniquindio.exploracity.ui.components.EmptyState
import co.edu.uniquindio.exploracity.ui.components.EmptyStateTone
import co.edu.uniquindio.exploracity.ui.components.ExploraButton
import co.edu.uniquindio.exploracity.ui.components.ExploraButtonStyle
import co.edu.uniquindio.exploracity.ui.components.ExploraSnackbarHost
import co.edu.uniquindio.exploracity.ui.components.OfflineBanner
import co.edu.uniquindio.exploracity.ui.components.POICardSkeleton
import co.edu.uniquindio.exploracity.ui.components.dashedBorder
import co.edu.uniquindio.exploracity.ui.components.relativeTimeText
import co.edu.uniquindio.exploracity.ui.components.rememberNow
import co.edu.uniquindio.exploracity.ui.components.rememberShimmerBrush
import co.edu.uniquindio.exploracity.ui.components.scaledWithFont
import co.edu.uniquindio.exploracity.ui.theme.ExploraCityTheme
import co.edu.uniquindio.exploracity.ui.theme.FontScaleThresholds
import co.edu.uniquindio.exploracity.ui.theme.Outfit
import co.edu.uniquindio.exploracity.ui.theme.exploraColors
import co.edu.uniquindio.exploracity.viewmodel.ModerationQueueUiState
import co.edu.uniquindio.exploracity.viewmodel.ModerationQueueViewModel
import co.edu.uniquindio.exploracity.viewmodel.QueueContent
import co.edu.uniquindio.exploracity.viewmodel.QueueFilter
import co.edu.uniquindio.exploracity.viewmodel.QueueMessage
import java.time.Instant

/**
 * 32 y 37 · La cola de moderación, conectada a su ViewModel. [allReviewed] llega de la revisión cuando se verificó la
 * última (C1).
 */
@Composable
fun ModerationQueueRoute(
    onOpenReview: (String) -> Unit,
    onOpenResolved: () -> Unit,
    onExplore: () -> Unit,
    allReviewed: Boolean = false,
    onAllReviewedShown: () -> Unit = {},
    viewModel: ModerationQueueViewModel = viewModel(factory = ModerationQueueViewModel.factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }
    val currentOnAllReviewedShown by rememberUpdatedState(onAllReviewedShown)
    LaunchedEffect(allReviewed) {
        if (!allReviewed) return@LaunchedEffect
        viewModel.onAllReviewed()
        currentOnAllReviewedShown()
    }
    ModerationQueueScreen(
        state = state,
        callbacks = ModerationQueueCallbacks(
            onOpenReview = onOpenReview,
            onOpenResolved = onOpenResolved,
            onExplore = onExplore,
            onFilterChange = viewModel::onFilterChange,
            onRetry = viewModel::onRetry,
            onMessageShown = viewModel::onMessageShown,
        ),
    )
}

class ModerationQueueCallbacks(
    val onOpenReview: (String) -> Unit = {},
    val onOpenResolved: () -> Unit = {},
    val onExplore: () -> Unit = {},
    val onFilterChange: (QueueFilter) -> Unit = {},
    val onRetry: () -> Unit = {},
    val onMessageShown: () -> Unit = {},
)

/**
 * 32.a–32.c y 37.a · Encabezado con cuántas esperan (o la antigüedad de lo guardado), los filtros «Pendientes» y
 * «Posibles duplicados» (relleno y check, no solo color) y «Resueltas»; la lista de la más antigua a la más reciente.
 * Sin conexión se lee lo guardado y se explica por qué no se puede decidir.
 */
@Composable
fun ModerationQueueScreen(state: ModerationQueueUiState, callbacks: ModerationQueueCallbacks, modifier: Modifier = Modifier, now: Instant? = null) {
    val snackbarHostState = remember { SnackbarHostState() }
    MessageEffect(state.message, snackbarHostState, callbacks.onMessageShown)
    val clock by rememberNow()
    val current = now ?: clock
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxSize()) {
            val queue = (state.content as? QueueContent.Loaded)?.queue
            // Con fuente grande los filtros se desplazan con la lista: fijos le dejaban media pantalla.
            val filtersInList = LocalDensity.current.fontScale > FontScaleThresholds.StackRows && state.visible.isNotEmpty()
            Header(state, queue, current)
            if (queue != null && !state.empty && !filtersInList) {
                Filters(state, callbacks, Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp))
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (val content = state.content) {
                    QueueContent.Loading -> Loading()
                    QueueContent.Offline -> Centered {
                        EmptyState(
                            icon = R.drawable.ic_cloud_off,
                            title = stringResource(R.string.offline_title),
                            body = stringResource(R.string.moderation_offline_nothing),
                            tone = EmptyStateTone.WARNING,
                        ) { RetryButton(callbacks.onRetry) }
                    }
                    QueueContent.Error -> Centered {
                        EmptyState(
                            icon = R.drawable.ic_sync_problem,
                            title = stringResource(R.string.moderation_error_title),
                            body = stringResource(R.string.reset_error_body),
                            tone = EmptyStateTone.WARNING,
                        ) { RetryButton(callbacks.onRetry) }
                    }
                    is QueueContent.Loaded -> when {
                        state.empty -> EmptyQueue(state.work, content.queue.savedAt != null, callbacks)
                        state.visible.isEmpty() -> Centered {
                            EmptyState(
                                icon = R.drawable.ic_done_all,
                                title = stringResource(R.string.moderation_no_duplicates_title),
                                body = pluralStringResource(R.plurals.moderation_no_duplicates_body, state.items.size, state.items.size),
                            ) {
                                ExploraButton(
                                    stringResource(R.string.moderation_see_all),
                                    onClick = { callbacks.onFilterChange(QueueFilter.ALL) },
                                    modifier = Modifier.fillMaxWidth(),
                                    style = ExploraButtonStyle.SECONDARY,
                                )
                            }
                        }
                        else -> QueueList(state, current, callbacks, filtersInList)
                    }
                }
            }
        }
        ExploraSnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }
}

/** «Moderación» con cuántas esperan, la antigüedad de lo guardado (32.c) o «Nada pendiente» (37). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Header(state: ModerationQueueUiState, queue: ReviewQueue?, now: Instant) {
    val savedAt = queue?.savedAt
    val subtitle = when {
        queue == null -> null
        savedAt != null -> stringResource(R.string.moderation_updated_ago, relativeTimeText(savedAt, now))
        state.empty -> stringResource(R.string.moderation_nothing_pending)
        else -> pluralStringResource(R.plurals.moderation_waiting, state.items.size, state.items.size)
    }
    // Con fuente grande, «Moderador» va debajo: al lado partía el título en dos.
    FlowRow(
        Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                stringResource(R.string.moderation_title),
                style = MaterialTheme.typography.headlineSmall.copy(fontFamily = Outfit, fontWeight = FontWeight.W600, fontSize = 22.sp),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.exploraColors.textSecondary)
        }
        if (savedAt == null) ModeratorBadge()
    }
}

/** «Pendientes · 7», «Posibles duplicados · 2» (filtros de una sola elección), «Resueltas» y el orden. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Filters(state: ModerationQueueUiState, callbacks: ModerationQueueCallbacks, modifier: Modifier = Modifier) {
    val filtersLabel = stringResource(R.string.moderation_filters)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Los dos filtros pasan a otra línea si no caben (fuente grande): juntos partían el texto letra por letra.
        FlowRow(
            Modifier.fillMaxWidth().selectableGroup().semantics { contentDescription = filtersLabel },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            QueueChip(
                stringResource(R.string.moderation_filter_all, state.items.size),
                selected = state.filter == QueueFilter.ALL,
                onClick = { callbacks.onFilterChange(QueueFilter.ALL) },
            )
            if (state.duplicates > 0 || state.filter == QueueFilter.DUPLICATES) {
                QueueChip(
                    stringResource(R.string.moderation_filter_duplicates, state.duplicates),
                    selected = state.filter == QueueFilter.DUPLICATES,
                    onClick = { callbacks.onFilterChange(QueueFilter.DUPLICATES) },
                )
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            if (!state.readOnly) {
                ExploraButton(stringResource(R.string.moderation_resolved), onClick = callbacks.onOpenResolved, style = ExploraButtonStyle.SECONDARY)
            }
            val sortDescription = stringResource(R.string.moderation_sort_description)
            Row(
                Modifier.heightIn(min = 48.dp).clearAndSetSemantics { contentDescription = sortDescription },
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(R.drawable.ic_swap_vert), null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp.scaledWithFont()))
                Text(stringResource(R.string.moderation_sort), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary)
            }
        }
    }
}

/** Filtro de la cola: elegido = relleno primary y check, no solo color (README 32). */
@Composable
private fun QueueChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(if (selected) scheme.primary else scheme.surface)
            .then(if (selected) Modifier else Modifier.border(1.dp, scheme.outline, shape))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) Icon(painterResource(R.drawable.ic_check), null, tint = scheme.onPrimary, modifier = Modifier.size(18.dp.scaledWithFont()))
        Text(label, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.W700), color = if (selected) scheme.onPrimary else scheme.onSurface)
    }
}

@Composable
private fun QueueList(state: ModerationQueueUiState, now: Instant, callbacks: ModerationQueueCallbacks, filtersInList: Boolean) {
    val offline = (state.content as? QueueContent.Loaded)?.queue?.savedAt != null
    val listState = rememberLazyListState()
    val firstCard = (if (filtersInList) 1 else 0) + (if (offline) 1 else 0)
    LaunchedEffect(offline) {
        // El aviso entra encima de la primera tarjeta y la lista se queda en ella: si estaba arriba, que se vea.
        if (offline && listState.firstVisibleItemIndex <= firstCard && listState.firstVisibleItemScrollOffset == 0) listState.scrollToItem(0)
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (filtersInList) item(key = "filters") { Filters(state, callbacks) }
        if (offline) {
            item(key = "offline") {
                OfflineBanner(
                    title = stringResource(R.string.offline_title),
                    body = stringResource(R.string.moderation_offline_body),
                    onRetry = callbacks.onRetry,
                    modifier = Modifier.clip(MaterialTheme.shapes.medium),
                )
            }
        }
        items(state.visible, key = { it.id }) { item ->
            ReviewCard(item, now, onOpen = { callbacks.onOpenReview(item.id) })
        }
        if (offline) item(key = "offline-hint") { OfflineHint() }
    }
}

/** 32.c · Por qué no se puede decidir sin conexión, al final de la lista. */
@Composable
private fun OfflineHint() {
    val explora = MaterialTheme.exploraColors
    Row(
        Modifier
            .fillMaxWidth()
            .dashedBorder(1.dp, explora.outlineDisabled, 12.dp)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(painterResource(R.drawable.ic_info), null, tint = explora.iconSecondary, modifier = Modifier.size(20.dp.scaledWithFont()))
        Text(stringResource(R.string.moderation_offline_hint), style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 19.sp), color = explora.textSecondary)
    }
}

/** 37 · Cola vacía: cierra el trabajo del día y ofrece seguir. Guardada sin conexión, no hay cifras de hoy. */
@Composable
private fun EmptyQueue(work: ModerationWork?, offline: Boolean, callbacks: ModerationQueueCallbacks) {
    val verified = MaterialTheme.exploraColors.status.verified
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Box(Modifier.size(88.dp).background(verified.container, RoundedCornerShape(28.dp)), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_done_all), null, tint = verified.content, modifier = Modifier.size(42.dp))
        }
        Text(
            stringResource(R.string.moderation_empty_title),
            style = MaterialTheme.typography.headlineSmall.copy(fontFamily = Outfit, fontWeight = FontWeight.W600, fontSize = 22.sp),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            stringResource(R.string.moderation_empty_body),
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 22.sp),
            color = MaterialTheme.exploraColors.textSecondary,
            textAlign = TextAlign.Center,
        )
        if (work != null && !offline) WorkCard(work)
        if (!offline) {
            ExploraButton(
                stringResource(R.string.moderation_see_resolved),
                onClick = callbacks.onOpenResolved,
                modifier = Modifier.fillMaxWidth(),
                icon = R.drawable.ic_history,
            )
        }
        ExploraButton(stringResource(R.string.moderation_go_explore), onClick = callbacks.onExplore, modifier = Modifier.fillMaxWidth(), style = ExploraButtonStyle.TEXT)
    }
}

/** «Tu trabajo de hoy»: verificadas, rechazadas y finalizadas, cada una con su icono. Se lee de una vez. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WorkCard(work: ModerationWork) {
    val title = stringResource(R.string.moderation_work_title)
    val spoken = stringResource(R.string.moderation_work_spoken, title, work.verified, work.rejected, work.finalized)
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest, MaterialTheme.shapes.medium)
            .border(1.dp, MaterialTheme.exploraColors.divider, MaterialTheme.shapes.medium)
            .clearAndSetSemantics { contentDescription = spoken }
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W700, letterSpacing = 1.sp),
            color = MaterialTheme.exploraColors.textSecondary,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            WorkStat(R.drawable.ic_verified, work.verified, stringResource(R.string.moderation_work_verified), MaterialTheme.exploraColors.status.verified.content)
            WorkStat(R.drawable.ic_cancel, work.rejected, stringResource(R.string.moderation_work_rejected), MaterialTheme.colorScheme.error)
            WorkStat(R.drawable.ic_task_alt, work.finalized, stringResource(R.string.moderation_work_finalized), MaterialTheme.exploraColors.status.finalized.content)
        }
    }
}

@Composable
private fun WorkStat(@DrawableRes icon: Int, value: Int, label: String, tint: androidx.compose.ui.graphics.Color) {
    Column(Modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Icon(painterResource(icon), null, tint = tint, modifier = Modifier.size(22.dp.scaledWithFont()))
        Text("$value", style = MaterialTheme.typography.headlineSmall.copy(fontFamily = Outfit, fontWeight = FontWeight.W600), color = MaterialTheme.colorScheme.onSurface)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.exploraColors.textSecondary)
    }
}

/** 32.b · La forma de la cola mientras carga. */
@Composable
private fun Loading() {
    val brush = rememberShimmerBrush()
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(4) { POICardSkeleton(brush) }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun RetryButton(onRetry: () -> Unit) {
    ExploraButton(stringResource(R.string.action_retry), onClick = onRetry, modifier = Modifier.fillMaxWidth(), icon = R.drawable.ic_refresh)
}

@Composable
private fun MessageEffect(message: QueueMessage?, hostState: SnackbarHostState, onShown: () -> Unit) {
    val currentOnShown by rememberUpdatedState(onShown)
    val updated = (message as? QueueMessage.Updated)?.newOnes ?: 0
    val text = when (message) {
        null -> null
        is QueueMessage.Updated ->
            if (updated == 0) stringResource(R.string.moderation_updated_none) else pluralStringResource(R.plurals.moderation_updated, updated, updated)
        QueueMessage.AllReviewed -> stringResource(R.string.moderation_all_reviewed)
    }
    LaunchedEffect(message) {
        if (text == null) return@LaunchedEffect
        hostState.showSnackbar(text, withDismissAction = true)
        currentOnShown()
    }
}

private val previewNow: Instant = Instant.parse("2026-10-02T15:00:00Z")

@Preview(name = "32.a · cola · claro", widthDp = 360, heightDp = 800)
@Composable
private fun QueuePreview() {
    ExploraCityTheme(ThemeMode.LIGHT) {
        ModerationQueueScreen(ModerationQueueUiState(content = QueueContent.Loaded(ReviewQueue(previewReviews(previewNow)))), ModerationQueueCallbacks(), now = previewNow)
    }
}

@Preview(name = "32.c · sin conexión · oscuro", widthDp = 360, heightDp = 800)
@Composable
private fun QueueOfflinePreview() {
    ExploraCityTheme(ThemeMode.DARK) {
        val queue = ReviewQueue(previewReviews(previewNow).take(2), savedAt = previewNow.minusSeconds(7200))
        ModerationQueueScreen(ModerationQueueUiState(content = QueueContent.Loaded(queue)), ModerationQueueCallbacks(), now = previewNow)
    }
}

@Preview(name = "37.a · cola vacía", widthDp = 360, heightDp = 800)
@Composable
private fun EmptyQueuePreview() {
    ExploraCityTheme(ThemeMode.LIGHT) {
        ModerationQueueScreen(
            ModerationQueueUiState(content = QueueContent.Loaded(ReviewQueue(emptyList())), work = ModerationWork(9, 2, 1)),
            ModerationQueueCallbacks(),
            now = previewNow,
        )
    }
}
