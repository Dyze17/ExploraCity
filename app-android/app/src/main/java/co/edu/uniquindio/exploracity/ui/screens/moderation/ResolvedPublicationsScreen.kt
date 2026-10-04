package co.edu.uniquindio.exploracity.ui.screens.moderation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import co.edu.uniquindio.exploracity.R
import co.edu.uniquindio.exploracity.domain.model.Category
import co.edu.uniquindio.exploracity.domain.model.FinalizeReason
import co.edu.uniquindio.exploracity.domain.model.PublicationStatus
import co.edu.uniquindio.exploracity.domain.model.RejectionReason
import co.edu.uniquindio.exploracity.domain.model.ResolvedPublication
import co.edu.uniquindio.exploracity.domain.model.ThemeMode
import co.edu.uniquindio.exploracity.ui.components.BadgeSize
import co.edu.uniquindio.exploracity.ui.components.CategoryTag
import co.edu.uniquindio.exploracity.ui.components.EmptyState
import co.edu.uniquindio.exploracity.ui.components.EmptyStateTone
import co.edu.uniquindio.exploracity.ui.components.ExploraSnackbarHost
import co.edu.uniquindio.exploracity.ui.components.ExploraTopAppBar
import co.edu.uniquindio.exploracity.ui.components.StatusBadge
import co.edu.uniquindio.exploracity.ui.components.rememberNow
import co.edu.uniquindio.exploracity.ui.components.scaledWithFont
import co.edu.uniquindio.exploracity.ui.theme.ExploraCityTheme
import co.edu.uniquindio.exploracity.ui.theme.ExploraElevation
import co.edu.uniquindio.exploracity.ui.theme.exploraColors
import co.edu.uniquindio.exploracity.ui.theme.exploraShadow
import co.edu.uniquindio.exploracity.util.RelativeTime
import co.edu.uniquindio.exploracity.util.formatDate
import co.edu.uniquindio.exploracity.util.relativeTime
import co.edu.uniquindio.exploracity.viewmodel.ResolvedContent
import co.edu.uniquindio.exploracity.viewmodel.ResolvedPublicationsUiState
import co.edu.uniquindio.exploracity.viewmodel.ResolvedPublicationsViewModel
import co.edu.uniquindio.exploracity.viewmodel.StateTarget
import java.time.Instant
import java.time.ZoneId

/**
 * «Resueltas» (E1), conectada a su ViewModel. Una verificada o finalizada abre 36 ([onOpen]); [notice] es el cambio de
 * estado con que se vuelve de allí.
 */
@Composable
fun ResolvedPublicationsRoute(
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    notice: StateTarget? = null,
    onNoticeShown: () -> Unit = {},
    viewModel: ResolvedPublicationsViewModel = viewModel(factory = ResolvedPublicationsViewModel.factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }
    ResolvedPublicationsScreen(state, onBack = onBack, onOpen = onOpen, onRetry = viewModel::onRetry, notice = notice, onNoticeShown = onNoticeShown)
}

/**
 * «Resueltas» · Sin lienzo (E1 de Daniel): lo ya decidido, de lo más reciente a lo más antiguo, con su estado, quién y
 * cuándo lo decidió, el motivo y la nota interna. Las verificadas y finalizadas se tocan para cambiar de estado (36);
 * las rechazadas no: las corrige quien las publicó.
 */
@Composable
fun ResolvedPublicationsScreen(
    state: ResolvedPublicationsUiState,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    notice: StateTarget? = null,
    onNoticeShown: () -> Unit = {},
    now: Instant? = null,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    NoticeEffect(notice, snackbarHostState, onNoticeShown)
    val clock by rememberNow()
    val current = now ?: clock
    val items = (state.content as? ResolvedContent.Loaded)?.items
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        ExploraTopAppBar(
            title = stringResource(R.string.resolved_title),
            subtitle = items?.takeIf { it.isNotEmpty() }?.let { pluralStringResource(R.plurals.resolved_count, it.size, it.size) },
            onBack = onBack,
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (val content = state.content) {
                ResolvedContent.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                ResolvedContent.Offline -> ModerationCentered {
                    EmptyState(
                        icon = R.drawable.ic_cloud_off,
                        title = stringResource(R.string.offline_title),
                        body = stringResource(R.string.resolved_offline_body),
                        tone = EmptyStateTone.WARNING,
                    ) { ModerationRetryButton(onRetry) }
                }
                ResolvedContent.Error -> ModerationCentered {
                    EmptyState(
                        icon = R.drawable.ic_sync_problem,
                        title = stringResource(R.string.resolved_error_title),
                        body = stringResource(R.string.reset_error_body),
                        tone = EmptyStateTone.WARNING,
                    ) { ModerationRetryButton(onRetry) }
                }
                is ResolvedContent.Loaded -> if (content.items.isEmpty()) {
                    ModerationCentered {
                        EmptyState(
                            icon = R.drawable.ic_history,
                            title = stringResource(R.string.resolved_empty_title),
                            body = stringResource(R.string.resolved_empty_body),
                        )
                    }
                } else {
                    ResolvedList(content.items, current, onOpen)
                }
            }
            ExploraSnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
        }
    }
}

/**
 * La lista va de lo más reciente a lo más antiguo: lo que se acaba de cambiar en 36 entra arriba, encima de la que se
 * veía primero. Si la lista estaba arriba, sube con ello para que se vea.
 */
@Composable
private fun ResolvedList(items: List<ResolvedPublication>, now: Instant, onOpen: (String) -> Unit) {
    val listState = rememberLazyListState()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    LaunchedEffect(items.first().id) {
        if (listState.firstVisibleItemIndex <= 1 && listState.firstVisibleItemScrollOffset == 0) listState.scrollToItem(0)
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp + bottom),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items, key = { it.id }) { item -> ResolvedCard(item, now, onOpen = { onOpen(item.id) }) }
    }
}

/**
 * Una resuelta: nombre, categoría y estado (con icono y texto), «Verificada hace 20 minutos · por Laura M.», quién la
 * publicó, el motivo y la nota interna si la hay. El lector la oye toda junta.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResolvedCard(item: ResolvedPublication, now: Instant, onOpen: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val explora = MaterialTheme.exploraColors
    val shape = MaterialTheme.shapes.medium
    val action = stringResource(R.string.resolved_change_state)
    val reason = item.rejectionReason?.let { stringResource(it.shortLabelRes) } ?: item.finalizeReason?.let { stringResource(it.labelRes) }
    val small = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp)
    Row(
        Modifier
            .fillMaxWidth()
            .exploraShadow(ExploraElevation.Card, shape, explora.shadow)
            .clip(shape)
            .background(scheme.surfaceContainerLowest)
            .then(if (item.canChangeState) Modifier.clickable(role = Role.Button, onClickLabel = action, onClick = onOpen) else Modifier)
            .padding(14.dp)
            .semantics(mergeDescendants = true) { },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(item.title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp), color = scheme.onSurface)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                CategoryTag(item.category)
                StatusBadge(item.status, size = BadgeSize.SMALL)
            }
            Text(
                stringResource(R.string.resolved_decided_by, decidedText(item.status, item.decidedAt, now), item.decidedBy),
                style = small,
                color = explora.textSecondary,
            )
            Text(stringResource(R.string.resolved_author, item.authorName ?: stringResource(R.string.deleted_user)), style = small, color = explora.textSecondary)
            if (reason != null) Text(stringResource(R.string.resolved_reason, reason), style = small, color = scheme.onSurface)
            item.note?.let { note ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(painterResource(R.drawable.ic_sticky_note_2), null, tint = explora.iconSecondary, modifier = Modifier.size(16.dp.scaledWithFont()))
                    Text(stringResource(R.string.resolved_note, note), style = small, color = explora.textSecondary)
                }
            }
        }
        if (item.canChangeState) {
            Icon(painterResource(R.drawable.ic_chevron_right), null, tint = explora.iconSecondary, modifier = Modifier.size(20.dp.scaledWithFont()))
        }
    }
}

/** «Verificada hace 20 minutos», «Rechazada hace 3 horas» o, pasado un mes, «Finalizada el 12 de marzo». */
@Composable
internal fun decidedText(status: PublicationStatus, decidedAt: Instant, now: Instant): String {
    val (ago, on) = when (status) {
        PublicationStatus.REJECTED -> R.string.decided_rejected_ago to R.string.decided_rejected_on
        PublicationStatus.FINALIZED -> R.string.decided_finalized_ago to R.string.decided_finalized_on
        else -> R.string.decided_verified_ago to R.string.decided_verified_on
    }
    return when (val time = relativeTime(decidedAt, now, ZoneId.systemDefault())) {
        RelativeTime.JustNow -> stringResource(ago, stringResource(R.string.time_just_now))
        is RelativeTime.Minutes -> stringResource(ago, pluralStringResource(R.plurals.time_minutes_ago, time.count, time.count))
        is RelativeTime.Hours -> stringResource(ago, pluralStringResource(R.plurals.time_hours_ago, time.count, time.count))
        is RelativeTime.Days -> stringResource(ago, pluralStringResource(R.plurals.time_days_ago, time.count, time.count))
        is RelativeTime.On -> stringResource(on, formatDate(time.date, withYear = !time.sameYear))
    }
}

/** «La publicación pasó a finalizada.» o «… volvió a pendiente.», al volver de 36. */
@Composable
private fun NoticeEffect(notice: StateTarget?, hostState: SnackbarHostState, onShown: () -> Unit) {
    val currentOnShown by rememberUpdatedState(onShown)
    val text = notice?.let { stringResource(if (it == StateTarget.FINALIZED) R.string.change_state_done_finalized else R.string.change_state_done_pending) }
    LaunchedEffect(notice) {
        if (text == null) return@LaunchedEffect
        hostState.showSnackbar(text, withDismissAction = true)
        currentOnShown()
    }
}

private val previewNow: Instant = Instant.parse("2026-10-02T15:00:00Z")

private val previewResolved = listOf(
    ResolvedPublication(
        "quinta", "Quinta de Bolívar", Category.HISTORY, PublicationStatus.VERIFIED, "Ana Ríos",
        previewNow.minusSeconds(1_200), "Laura M.", votes = 64, comments = 8,
    ),
    ResolvedPublication(
        "puerta", "Puerta Falsa, tamales", Category.GASTRONOMY, PublicationStatus.REJECTED, "Ana Ríos",
        previewNow.minusSeconds(2_700), "Laura M.", rejectionReason = RejectionReason.DUPLICATE,
    ),
    ResolvedPublication(
        "museo", "Museo Botero", Category.CULTURE, PublicationStatus.VERIFIED, "Camilo R.",
        previewNow.minusSeconds(172_800), "Andrés P.", note = "Revisé el horario con la página del museo.",
    ),
    ResolvedPublication(
        "casa", "Casa de la Independencia", Category.HISTORY, PublicationStatus.FINALIZED, "Ana Ríos",
        previewNow.minusSeconds(432_000), "Laura M.", finalizeReason = FinalizeReason.CLOSED,
    ),
)

@Preview(name = "Resueltas · claro", widthDp = 360, heightDp = 800)
@Composable
private fun ResolvedPreview() {
    ExploraCityTheme(ThemeMode.LIGHT) {
        ResolvedPublicationsScreen(ResolvedPublicationsUiState(ResolvedContent.Loaded(previewResolved)), onBack = {}, onOpen = {}, onRetry = {}, now = previewNow)
    }
}

@Preview(name = "Resueltas · oscuro", widthDp = 360, heightDp = 800)
@Composable
private fun ResolvedDarkPreview() {
    ExploraCityTheme(ThemeMode.DARK) {
        ResolvedPublicationsScreen(ResolvedPublicationsUiState(ResolvedContent.Loaded(previewResolved)), onBack = {}, onOpen = {}, onRetry = {}, now = previewNow)
    }
}
