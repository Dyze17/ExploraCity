package co.edu.uniquindio.exploracity.ui.screens.moderation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import co.edu.uniquindio.exploracity.R
import co.edu.uniquindio.exploracity.domain.model.Category
import co.edu.uniquindio.exploracity.domain.model.FinalizeReason
import co.edu.uniquindio.exploracity.domain.model.PublicationStatus
import co.edu.uniquindio.exploracity.domain.model.ResolvedPublication
import co.edu.uniquindio.exploracity.domain.model.ThemeMode
import co.edu.uniquindio.exploracity.ui.components.EmptyState
import co.edu.uniquindio.exploracity.ui.components.EmptyStateTone
import co.edu.uniquindio.exploracity.ui.components.ExploraButton
import co.edu.uniquindio.exploracity.ui.components.ExploraSnackbarHost
import co.edu.uniquindio.exploracity.ui.components.ExploraTextField
import co.edu.uniquindio.exploracity.ui.components.ExploraTopAppBar
import co.edu.uniquindio.exploracity.ui.components.StatusBadge
import co.edu.uniquindio.exploracity.ui.components.rememberNow
import co.edu.uniquindio.exploracity.ui.screens.access.AccessOfflineNotice
import co.edu.uniquindio.exploracity.ui.screens.access.SyncText
import co.edu.uniquindio.exploracity.ui.theme.ExploraCityTheme
import co.edu.uniquindio.exploracity.ui.theme.ExploraElevation
import co.edu.uniquindio.exploracity.ui.theme.FontScaleThresholds
import co.edu.uniquindio.exploracity.ui.theme.Outfit
import co.edu.uniquindio.exploracity.ui.theme.exploraColors
import co.edu.uniquindio.exploracity.ui.theme.exploraShadow
import co.edu.uniquindio.exploracity.viewmodel.ChangeStateContent
import co.edu.uniquindio.exploracity.viewmodel.ChangeStateProblem
import co.edu.uniquindio.exploracity.viewmodel.ChangeStateUiState
import co.edu.uniquindio.exploracity.viewmodel.ChangeStateViewModel
import co.edu.uniquindio.exploracity.viewmodel.StateTarget
import java.time.Instant

/** 36 · Cambiar estado, conectada a su ViewModel. Al guardar vuelve a «Resueltas» con lo hecho ([onDone]). */
@Composable
fun ChangeStateRoute(
    onBack: () -> Unit,
    onDone: (StateTarget) -> Unit,
    viewModel: ChangeStateViewModel = viewModel(factory = ChangeStateViewModel.factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnDone by rememberUpdatedState(onDone)
    LaunchedEffect(state.done) {
        val done = state.done ?: return@LaunchedEffect
        viewModel.onDoneHandled()
        currentOnDone(done)
    }
    ChangeStateScreen(
        state = state,
        callbacks = ChangeStateCallbacks(
            onBack = onBack,
            onRetry = viewModel::onRetry,
            onTargetChange = viewModel::onTargetChange,
            onFinalizeReasonChange = viewModel::onFinalizeReasonChange,
            onReopenReasonChange = viewModel::onReopenReasonChange,
            onConfirm = viewModel::onConfirm,
            onFailureShown = viewModel::onFailureShown,
        ),
    )
}

class ChangeStateCallbacks(
    val onBack: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onTargetChange: (StateTarget) -> Unit = {},
    val onFinalizeReasonChange: (FinalizeReason) -> Unit = {},
    val onReopenReasonChange: (String) -> Unit = {},
    val onConfirm: () -> Unit = {},
    val onFailureShown: () -> Unit = {},
)

/**
 * 36.a · Cambiar estado: la publicación, el nuevo estado («Resuelta / finalizada» o «Volver a pendiente») y su motivo,
 * con lo que pasará al confirmar. Es reversible: una finalizada vuelve con «Volver a pendiente». La acción se ve
 * deshabilitada hasta tener el motivo; al tocarla así se dice qué falta.
 */
@Composable
fun ChangeStateScreen(state: ChangeStateUiState, callbacks: ChangeStateCallbacks, modifier: Modifier = Modifier, now: Instant? = null) {
    val snackbarHostState = remember { SnackbarHostState() }
    FailureEffect(state.failed, snackbarHostState, callbacks)
    val clock by rememberNow()
    val current = now ?: clock
    val item = state.item
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).imePadding()) {
        ExploraTopAppBar(title = stringResource(R.string.change_state_title), subtitle = item?.title, onBack = callbacks.onBack)
        val largeFont = LocalDensity.current.fontScale > FontScaleThresholds.StackRows
        val offlineNotice = state.offline && item != null
        if (offlineNotice && !largeFont) AccessOfflineNotice(stringResource(R.string.change_state_offline))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (state.content) {
                ChangeStateContent.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                is ChangeStateContent.Loaded -> if (item != null) Form(item, state, callbacks, current, offlineNotice = offlineNotice && largeFont)
                ChangeStateContent.Gone -> ModerationCentered {
                    EmptyState(
                        icon = R.drawable.ic_history,
                        title = stringResource(R.string.change_state_gone_title),
                        body = stringResource(R.string.change_state_gone_body),
                    ) {
                        ExploraButton(stringResource(R.string.change_state_back), onClick = callbacks.onBack, modifier = Modifier.fillMaxWidth())
                    }
                }
                ChangeStateContent.Offline -> ModerationCentered {
                    EmptyState(
                        icon = R.drawable.ic_cloud_off,
                        title = stringResource(R.string.offline_title),
                        body = stringResource(R.string.change_state_offline_body),
                        tone = EmptyStateTone.WARNING,
                    ) { ModerationRetryButton(callbacks.onRetry) }
                }
                ChangeStateContent.Error -> ModerationCentered {
                    EmptyState(
                        icon = R.drawable.ic_sync_problem,
                        title = stringResource(R.string.change_state_error_title),
                        body = stringResource(R.string.reset_error_body),
                        tone = EmptyStateTone.WARNING,
                    ) { ModerationRetryButton(callbacks.onRetry) }
                }
            }
            ExploraSnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
        }
        if (item != null) {
            val finalize = state.target == StateTarget.FINALIZED
            DecisionBottomBar(onCancel = callbacks.onBack, cancelEnabled = !state.sending) { m ->
                ExplainedButton(
                    text = stringResource(
                        when {
                            state.sending -> R.string.change_state_sending
                            finalize -> R.string.change_state_confirm_finalize
                            else -> R.string.change_state_confirm_pending
                        },
                    ),
                    onClick = callbacks.onConfirm,
                    onExplain = callbacks.onConfirm,
                    enabled = state.canSend,
                    disabledReason = disabledReason(state),
                    modifier = m,
                    loading = state.sending,
                    icon = if (finalize) R.drawable.ic_task_alt else R.drawable.ic_schedule,
                )
            }
        }
    }
}

@Composable
private fun disabledReason(state: ChangeStateUiState): String? = when {
    state.offline -> stringResource(R.string.change_state_offline_reason)
    ChangeStateProblem.NO_FINALIZE_REASON in state.problems -> stringResource(R.string.reject_no_reason_reason)
    ChangeStateProblem.SHORT_REOPEN_REASON in state.problems -> stringResource(R.string.reject_short_detail_reason, ChangeStateViewModel.REASON_MIN)
    else -> null
}

@Composable
private fun Form(item: ResolvedPublication, state: ChangeStateUiState, callbacks: ChangeStateCallbacks, now: Instant, offlineNotice: Boolean) {
    val firstReason = remember { FocusRequester() }
    val reasonField = remember { FocusRequester() }
    val errors = if (state.showErrors) state.problems else emptySet()
    LaunchedEffect(state.attempts) {
        if (state.attempts == 0) return@LaunchedEffect
        when {
            ChangeStateProblem.NO_FINALIZE_REASON in state.problems -> firstReason.requestFocus()
            ChangeStateProblem.SHORT_REOPEN_REASON in state.problems -> reasonField.requestFocus()
        }
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (offlineNotice) Box(Modifier.clip(MaterialTheme.shapes.medium)) { AccessOfflineNotice(stringResource(R.string.change_state_offline)) }
        PlaceCard(item, now)
        TargetGroup(state, callbacks)
        when (state.target) {
            StateTarget.FINALIZED -> {
                ChoiceGroup(
                    label = stringResource(R.string.change_state_why_finalize),
                    required = true,
                    error = if (ChangeStateProblem.NO_FINALIZE_REASON in errors) stringResource(R.string.reject_choose_reason) else null,
                ) {
                    FinalizeReason.entries.forEachIndexed { index, reason ->
                        ChoiceRow(
                            label = stringResource(reason.labelRes),
                            selected = state.finalizeReason == reason,
                            onSelect = { callbacks.onFinalizeReasonChange(reason) },
                            modifier = if (index == 0) Modifier.focusRequester(firstReason) else Modifier,
                            enabled = !state.sending,
                        )
                    }
                }
                HintCard(stringResource(R.string.change_state_finalize_hint))
            }
            StateTarget.PENDING -> {
                ReopenReasonField(state, short = ChangeStateProblem.SHORT_REOPEN_REASON in errors, callbacks, Modifier.focusRequester(reasonField))
                HintCard(stringResource(R.string.change_state_pending_hint))
            }
            null -> Unit
        }
    }
}

/** La publicación que cambia: foto, nombre, «Verificada hace 5 meses» y sus votos y comentarios (36.a). */
@Composable
private fun PlaceCard(item: ResolvedPublication, now: Instant) {
    val shape = RoundedCornerShape(16.dp)
    val explora = MaterialTheme.exploraColors
    Row(
        Modifier
            .fillMaxWidth()
            .exploraShadow(ExploraElevation.Card, shape, explora.shadow)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest, shape)
            .padding(12.dp)
            .semantics(mergeDescendants = true) { },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(80.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_photo_camera), null, tint = explora.textPlaceholder, modifier = Modifier.size(28.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                item.title,
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = Outfit, fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.W600),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            StatusBadge(item.status, label = decidedText(item.status, item.decidedAt, now))
            Text(
                stringResource(
                    R.string.change_state_activity,
                    pluralStringResource(R.plurals.change_state_votes, item.votes, item.votes),
                    pluralStringResource(R.plurals.change_state_comments, item.comments, item.comments),
                ),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, fontWeight = FontWeight.W600),
                color = explora.textSecondary,
            )
        }
    }
}

/**
 * «Nuevo estado»: cada opción con el icono y los colores de su estado, y un check en la elegida. Una finalizada solo
 * puede volver a pendiente.
 */
@Composable
private fun TargetGroup(state: ChangeStateUiState, callbacks: ChangeStateCallbacks) {
    val status = MaterialTheme.exploraColors.status
    ChoiceGroup(label = stringResource(R.string.change_state_new)) {
        if (state.canFinalize) {
            ChoiceRow(
                label = stringResource(R.string.status_finalized),
                selected = state.target == StateTarget.FINALIZED,
                onSelect = { callbacks.onTargetChange(StateTarget.FINALIZED) },
                enabled = !state.sending,
                icon = R.drawable.ic_task_alt,
                iconTint = status.finalized.content,
                colors = ChoiceColors(status.finalized.container, status.finalized.content, status.finalized.content),
            )
        }
        ChoiceRow(
            label = stringResource(R.string.change_state_pending),
            selected = state.target == StateTarget.PENDING,
            onSelect = { callbacks.onTargetChange(StateTarget.PENDING) },
            enabled = !state.sending,
            icon = R.drawable.ic_schedule,
            iconTint = status.pending.content,
            colors = ChoiceColors(status.pending.container, status.pending.content, status.pending.content),
        )
    }
}

/** «¿Por qué vuelve a pendiente?»: queda como nota interna; al menos 20 caracteres. */
@Composable
private fun ReopenReasonField(state: ChangeStateUiState, short: Boolean, callbacks: ChangeStateCallbacks, modifier: Modifier) {
    val textState = rememberTextFieldState(state.reopenReason)
    SyncText(textState, state.reopenReason, callbacks.onReopenReasonChange)
    val missing = (ChangeStateViewModel.REASON_MIN - textState.text.trim().length).coerceAtLeast(0)
    ExploraTextField(
        state = textState,
        label = stringResource(R.string.change_state_why_pending),
        placeholder = stringResource(R.string.change_state_pending_placeholder),
        supportingText = stringResource(R.string.change_state_pending_support, ChangeStateViewModel.REASON_MIN),
        errorMessage = if (short) pluralStringResource(R.plurals.reject_message_short, missing, missing, ChangeStateViewModel.REASON_MIN) else null,
        counter = stringResource(R.string.verify_note_counter, textState.text.length, ChangeStateViewModel.REASON_MAX),
        singleLine = false,
        minLines = 3,
        enabled = !state.sending,
        inputTransformation = InputTransformation.maxLength(ChangeStateViewModel.REASON_MAX),
        modifier = modifier.fillMaxWidth(),
    )
}

/** «No pudimos guardar el cambio. La publicación sigue igual» con «Reintentar». */
@Composable
private fun FailureEffect(failed: Boolean, hostState: SnackbarHostState, callbacks: ChangeStateCallbacks) {
    val currentCallbacks by rememberUpdatedState(callbacks)
    val text = stringResource(R.string.change_state_failed)
    val retry = stringResource(R.string.action_retry)
    LaunchedEffect(failed) {
        if (!failed) return@LaunchedEffect
        currentCallbacks.onFailureShown()
        val result = hostState.showSnackbar(text, actionLabel = retry, withDismissAction = true, duration = SnackbarDuration.Indefinite)
        if (result == SnackbarResult.ActionPerformed) currentCallbacks.onConfirm()
    }
}

private val previewNow: Instant = Instant.parse("2026-10-02T15:00:00Z")

private val previewItem = ResolvedPublication(
    "casa", "Casa de la Independencia", Category.HISTORY, PublicationStatus.VERIFIED, "Ana Ríos",
    previewNow.minusSeconds(150L * 86_400), "Laura M.", votes = 212, comments = 44,
)

@Preview(name = "36.a · pasar a finalizada · claro", widthDp = 360, heightDp = 800)
@Composable
private fun ChangeStatePreview() {
    ExploraCityTheme(ThemeMode.LIGHT) {
        ChangeStateScreen(
            ChangeStateUiState(content = ChangeStateContent.Loaded(previewItem), target = StateTarget.FINALIZED, finalizeReason = FinalizeReason.CLOSED),
            ChangeStateCallbacks(),
            now = previewNow,
        )
    }
}

@Preview(name = "36.a · volver a pendiente · oscuro", widthDp = 360, heightDp = 800)
@Composable
private fun ReopenPreview() {
    ExploraCityTheme(ThemeMode.DARK) {
        ChangeStateScreen(
            ChangeStateUiState(content = ChangeStateContent.Loaded(previewItem.copy(status = PublicationStatus.FINALIZED)), target = StateTarget.PENDING),
            ChangeStateCallbacks(),
            now = previewNow,
        )
    }
}
