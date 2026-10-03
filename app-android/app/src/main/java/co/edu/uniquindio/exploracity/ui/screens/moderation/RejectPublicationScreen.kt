package co.edu.uniquindio.exploracity.ui.screens.moderation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import co.edu.uniquindio.exploracity.R
import co.edu.uniquindio.exploracity.domain.model.DuplicateCandidate
import co.edu.uniquindio.exploracity.domain.model.RejectionReason
import co.edu.uniquindio.exploracity.domain.model.ThemeMode
import co.edu.uniquindio.exploracity.ui.components.EmptyState
import co.edu.uniquindio.exploracity.ui.components.EmptyStateTone
import co.edu.uniquindio.exploracity.ui.components.ExploraButton
import co.edu.uniquindio.exploracity.ui.components.ExploraButtonStyle
import co.edu.uniquindio.exploracity.ui.components.ExploraCheckbox
import co.edu.uniquindio.exploracity.ui.components.ExploraSnackbarHost
import co.edu.uniquindio.exploracity.ui.components.ExploraTextField
import co.edu.uniquindio.exploracity.ui.components.ExploraTopAppBar
import co.edu.uniquindio.exploracity.ui.components.scaledWithFont
import co.edu.uniquindio.exploracity.ui.screens.access.AccessOfflineNotice
import co.edu.uniquindio.exploracity.ui.screens.access.SyncText
import co.edu.uniquindio.exploracity.ui.theme.ExploraCityTheme
import co.edu.uniquindio.exploracity.ui.theme.FontScaleThresholds
import co.edu.uniquindio.exploracity.ui.theme.exploraColors
import co.edu.uniquindio.exploracity.viewmodel.RejectProblem
import co.edu.uniquindio.exploracity.viewmodel.RejectPublicationUiState
import co.edu.uniquindio.exploracity.viewmodel.RejectPublicationViewModel
import co.edu.uniquindio.exploracity.viewmodel.ReviewContent
import co.edu.uniquindio.exploracity.viewmodel.ReviewDone
import java.time.Instant

/**
 * 35 · Rechazar con motivo, conectada a su ViewModel. Al rechazar abre la siguiente pendiente ([onNext], con cuántas
 * quedan) o vuelve a la cola vacía ([onQueueEmpty]), como al verificar (C1).
 */
@Composable
fun RejectPublicationRoute(
    onBack: () -> Unit,
    onBackToQueue: () -> Unit,
    onNext: (id: String, remaining: Int) -> Unit,
    onQueueEmpty: () -> Unit,
    viewModel: RejectPublicationViewModel = viewModel(factory = RejectPublicationViewModel.factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnNext by rememberUpdatedState(onNext)
    val currentOnQueueEmpty by rememberUpdatedState(onQueueEmpty)
    LaunchedEffect(state.done) {
        when (val done = state.done) {
            is ReviewDone.Next -> currentOnNext(done.id, done.remaining)
            ReviewDone.QueueEmpty -> currentOnQueueEmpty()
            null -> return@LaunchedEffect
        }
        viewModel.onDoneHandled()
    }
    RejectPublicationScreen(
        state = state,
        callbacks = RejectCallbacks(
            onBack = onBack,
            onBackToQueue = onBackToQueue,
            onRetry = viewModel::onRetry,
            onReasonChange = viewModel::onReasonChange,
            onChooseOriginal = viewModel::onChooseOriginal,
            onOriginalChange = viewModel::onOriginalChange,
            onDismissOriginal = viewModel::onDismissOriginal,
            onSuggestion = viewModel::onSuggestion,
            onMessageChange = viewModel::onMessageChange,
            onCanResubmitChange = viewModel::onCanResubmitChange,
            onReject = viewModel::onReject,
            onFailureShown = viewModel::onFailureShown,
        ),
    )
}

class RejectCallbacks(
    val onBack: () -> Unit = {},
    val onBackToQueue: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onReasonChange: (RejectionReason) -> Unit = {},
    val onChooseOriginal: () -> Unit = {},
    val onOriginalChange: (String) -> Unit = {},
    val onDismissOriginal: () -> Unit = {},
    val onSuggestion: (String) -> Unit = {},
    val onMessageChange: (String) -> Unit = {},
    val onCanResubmitChange: (Boolean) -> Unit = {},
    val onReject: () -> Unit = {},
    val onFailureShown: () -> Unit = {},
)

/**
 * 35, 35.a y 35.b · El motivo es obligatorio. Con «Duplicado de un lugar existente» va el enlace al original (si hay
 * varios parecidos, abre un selector) y un mensaje sugerido que se puede editar; con «Otro motivo», un detalle de al
 * menos 20 caracteres. «Rechazar» se ve deshabilitado hasta que se cumple; al tocarlo así se dice qué falta: el aviso
 * arriba, el borde de error en el grupo y el foco en el primer motivo, sin diálogos.
 */
@Composable
fun RejectPublicationScreen(state: RejectPublicationUiState, callbacks: RejectCallbacks, modifier: Modifier = Modifier) {
    val snackbarHostState = remember { SnackbarHostState() }
    FailureEffect(state.failed, snackbarHostState, callbacks)
    val item = state.item
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxSize().imePadding()) {
            ExploraTopAppBar(title = stringResource(R.string.reject_title), subtitle = item?.title, onBack = callbacks.onBack, closeIcon = true)
            // Con fuente grande el aviso se desplaza con el formulario, como en 33.
            val largeFont = LocalDensity.current.fontScale > FontScaleThresholds.StackRows
            val offlineNotice = state.offline && item != null
            if (offlineNotice && !largeFont) AccessOfflineNotice(stringResource(R.string.review_offline))
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (state.content) {
                    ReviewContent.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    is ReviewContent.Loaded -> RejectForm(state, callbacks, offlineNotice = offlineNotice && largeFont)
                    ReviewContent.Gone -> ModerationCentered {
                        EmptyState(
                            icon = R.drawable.ic_done_all,
                            title = stringResource(R.string.review_gone_title),
                            body = stringResource(R.string.review_gone_body),
                        ) {
                            ExploraButton(stringResource(R.string.review_back_to_queue), onClick = callbacks.onBackToQueue, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    ReviewContent.Offline -> ModerationCentered {
                        EmptyState(
                            icon = R.drawable.ic_cloud_off,
                            title = stringResource(R.string.offline_title),
                            body = stringResource(R.string.review_offline_body),
                            tone = EmptyStateTone.WARNING,
                        ) { ModerationRetryButton(callbacks.onRetry) }
                    }
                    ReviewContent.Error -> ModerationCentered {
                        EmptyState(
                            icon = R.drawable.ic_sync_problem,
                            title = stringResource(R.string.review_error_title),
                            body = stringResource(R.string.reset_error_body),
                            tone = EmptyStateTone.WARNING,
                        ) { ModerationRetryButton(callbacks.onRetry) }
                    }
                }
                ExploraSnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
            }
            if (item != null) {
                DecisionBottomBar(onCancel = callbacks.onBack, cancelEnabled = !state.sending) { m ->
                    ExplainedButton(
                        text = stringResource(if (state.sending) R.string.reject_sending else R.string.reject_confirm),
                        onClick = callbacks.onReject,
                        onExplain = callbacks.onReject,
                        enabled = state.canSend,
                        disabledReason = disabledReason(state),
                        modifier = m,
                        style = ExploraButtonStyle.DESTRUCTIVE,
                        loading = state.sending,
                        icon = R.drawable.ic_cancel,
                    )
                }
            }
        }
    }
    if (state.choosingOriginal) OriginalDialog(state, callbacks)
}

/** Lo que oye el lector en «Rechazar» deshabilitado: la red primero, después lo que falta. */
@Composable
private fun disabledReason(state: RejectPublicationUiState): String? = when {
    state.offline -> stringResource(R.string.review_offline_reason)
    RejectProblem.NO_REASON in state.problems -> stringResource(R.string.reject_no_reason_reason)
    RejectProblem.NO_ORIGINAL in state.problems -> stringResource(R.string.reject_no_original_reason)
    RejectProblem.SHORT_DETAIL in state.problems -> stringResource(R.string.reject_short_detail_reason, RejectPublicationViewModel.DETAIL_MIN)
    else -> null
}

@Composable
private fun RejectForm(state: RejectPublicationUiState, callbacks: RejectCallbacks, offlineNotice: Boolean) {
    val firstReason = remember { FocusRequester() }
    val originalLink = remember { FocusRequester() }
    val messageField = remember { FocusRequester() }
    val errors = if (state.showErrors) state.problems else emptySet()
    // Cada intento sin poder rechazar lleva el foco (y la vista) al primer problema.
    LaunchedEffect(state.attempts) {
        if (state.attempts == 0) return@LaunchedEffect
        when {
            RejectProblem.NO_REASON in state.problems -> firstReason.requestFocus()
            RejectProblem.NO_ORIGINAL in state.problems -> originalLink.requestFocus()
            RejectProblem.SHORT_DETAIL in state.problems -> messageField.requestFocus()
        }
    }
    val suggestion = state.original?.let { stringResource(R.string.reject_duplicate_suggestion, it.poi.title) }
    LaunchedEffect(state.reason, suggestion) {
        if (state.reason == RejectionReason.DUPLICATE && suggestion != null) callbacks.onSuggestion(suggestion)
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (offlineNotice) Box(Modifier.clip(MaterialTheme.shapes.medium)) { AccessOfflineNotice(stringResource(R.string.review_offline)) }
        if (RejectProblem.NO_REASON in errors) MissingReasonBanner() else IntroBanner()
        ChoiceGroup(
            label = stringResource(R.string.reject_reasons),
            required = true,
            error = if (RejectProblem.NO_REASON in errors) stringResource(R.string.reject_choose_reason) else null,
        ) {
            RejectionReason.entries.forEachIndexed { index, reason ->
                ChoiceRow(
                    label = stringResource(reason.labelRes),
                    selected = state.reason == reason,
                    onSelect = { callbacks.onReasonChange(reason) },
                    modifier = if (index == 0) Modifier.focusRequester(firstReason) else Modifier,
                    enabled = !state.sending,
                    extra = if (reason == RejectionReason.DUPLICATE) {
                        { OriginalLink(state, missing = RejectProblem.NO_ORIGINAL in errors, callbacks, Modifier.focusRequester(originalLink)) }
                    } else {
                        null
                    },
                )
            }
        }
        MessageField(state, short = RejectProblem.SHORT_DETAIL in errors, callbacks, Modifier.focusRequester(messageField))
        if (state.reason != null && state.reason != RejectionReason.DUPLICATE) ResubmitRow(state, callbacks)
    }
}

/** «Quien la publicó verá el motivo tal como lo escribas. Sé claro y dile qué corregir.» (35.a). */
@Composable
private fun IntroBanner() {
    val warning = MaterialTheme.exploraColors.warning
    HintCard(stringResource(R.string.reject_intro), container = warning.container, content = warning.content)
}

/** 35.b · «Falta el motivo»: en tono de error y anunciado al aparecer. */
@Composable
private fun MissingReasonBanner() {
    val scheme = MaterialTheme.colorScheme
    Box(Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
        HintCard(
            text = stringResource(R.string.reject_missing_body),
            title = stringResource(R.string.reject_missing_title),
            icon = R.drawable.ic_error,
            container = scheme.errorContainer,
            content = scheme.onErrorContainer,
        )
    }
}

/**
 * Dentro de «Duplicado de un lugar existente»: el original elegido («La Fonda Café · a 23 m»), que abre el selector, o
 * «Elige el lugar original». Sin lugares cerca, se dice que hay que elegir otro motivo.
 */
@Composable
private fun OriginalLink(state: RejectPublicationUiState, missing: Boolean, callbacks: RejectCallbacks, modifier: Modifier) {
    val explora = MaterialTheme.exploraColors
    val textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 19.sp)
    when {
        !state.optionsLoaded -> Text(stringResource(R.string.reject_original_loading), style = textStyle, color = explora.textSecondary)
        state.options.isEmpty() -> Text(stringResource(R.string.reject_original_none), style = textStyle, color = explora.textSecondary)
        else -> {
            val original = state.original
            val label = original?.let { stringResource(R.string.reject_original, it.poi.title, it.distanceMeters) }
                ?: stringResource(R.string.reject_original_choose)
            val spoken = original?.let { stringResource(R.string.reject_original_spoken, it.poi.title, it.distanceMeters) }
                ?: stringResource(R.string.reject_original_choose)
            val action = stringResource(R.string.reject_original_change)
            Row(
                modifier
                    .heightIn(min = 48.dp)
                    .clickable(enabled = !state.sending, role = Role.Button, onClickLabel = action, onClick = callbacks.onChooseOriginal)
                    .semantics(mergeDescendants = true) { contentDescription = spoken },
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(R.drawable.ic_link), null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp.scaledWithFont()))
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.W700, textDecoration = TextDecoration.Underline),
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            if (missing) InlineError(stringResource(R.string.reject_original_missing))
        }
    }
}

/** «Mensaje para quien la publicó»: sugerido con duplicado; obligatorio (20 caracteres) con «Otro motivo». */
@Composable
private fun MessageField(state: RejectPublicationUiState, short: Boolean, callbacks: RejectCallbacks, modifier: Modifier) {
    val textState = rememberTextFieldState(state.message)
    SyncText(textState, state.message, callbacks.onMessageChange)
    val length = textState.text.trim().length
    val missing = (RejectPublicationViewModel.DETAIL_MIN - length).coerceAtLeast(0)
    val supporting = when {
        state.reason == RejectionReason.DUPLICATE && state.suggested -> stringResource(R.string.reject_message_suggested)
        state.reason == RejectionReason.OTHER -> stringResource(R.string.reject_message_min, RejectPublicationViewModel.DETAIL_MIN)
        else -> null
    }
    ExploraTextField(
        state = textState,
        label = stringResource(R.string.reject_message),
        placeholder = stringResource(R.string.reject_message_placeholder),
        supportingText = supporting,
        errorMessage = if (short) pluralStringResource(R.plurals.reject_message_short, missing, missing, RejectPublicationViewModel.DETAIL_MIN) else null,
        counter = stringResource(R.string.verify_note_counter, textState.text.length, RejectPublicationViewModel.MESSAGE_MAX),
        singleLine = false,
        minLines = 3,
        enabled = !state.sending,
        inputTransformation = InputTransformation.maxLength(RejectPublicationViewModel.MESSAGE_MAX),
        modifier = modifier.fillMaxWidth(),
    )
}

/** «Permitir que corrija y la reenvíe»: un duplicado no se corrige, así que con él no aparece. */
@Composable
private fun ResubmitRow(state: RejectPublicationUiState, callbacks: RejectCallbacks) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = state.canResubmit, enabled = !state.sending, role = Role.Checkbox, onValueChange = callbacks.onCanResubmitChange),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ExploraCheckbox(checked = state.canResubmit)
        Text(stringResource(R.string.reject_allow_resubmit), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp), color = MaterialTheme.colorScheme.onSurface)
    }
}

/** «¿Cuál es el lugar original?»: los parecidos (o los cercanos), del más cercano al más lejano. */
@Composable
private fun OriginalDialog(state: RejectPublicationUiState, callbacks: RejectCallbacks) {
    AlertDialog(
        onDismissRequest = callbacks.onDismissOriginal,
        title = { Text(stringResource(R.string.reject_original_dialog_title), style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()).selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.options.forEach { option: DuplicateCandidate ->
                    ChoiceRow(
                        label = stringResource(R.string.reject_original, option.poi.title, option.distanceMeters),
                        selected = option.poi.id == state.originalId,
                        onSelect = { callbacks.onOriginalChange(option.poi.id) },
                    )
                }
            }
        },
        confirmButton = {
            ExploraButton(stringResource(R.string.verify_cancel), onClick = callbacks.onDismissOriginal, style = ExploraButtonStyle.TEXT)
        },
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(28.dp),
    )
}

/** «No pudimos guardar la decisión. La publicación sigue pendiente» con «Reintentar» (como en 34). */
@Composable
private fun FailureEffect(failed: Boolean, hostState: SnackbarHostState, callbacks: RejectCallbacks) {
    val currentCallbacks by rememberUpdatedState(callbacks)
    val text = stringResource(R.string.review_verify_failed)
    val retry = stringResource(R.string.action_retry)
    LaunchedEffect(failed) {
        if (!failed) return@LaunchedEffect
        currentCallbacks.onFailureShown()
        val result = hostState.showSnackbar(text, actionLabel = retry, withDismissAction = true, duration = SnackbarDuration.Indefinite)
        if (result == SnackbarResult.ActionPerformed) currentCallbacks.onReject()
    }
}

private val previewNow: Instant = Instant.parse("2026-10-02T15:00:00Z")

@Preview(name = "35 · motivo «Duplicado» · claro", widthDp = 360, heightDp = 800)
@Composable
private fun RejectDuplicatePreview() {
    val item = previewReviews(previewNow)[1]
    ExploraCityTheme(ThemeMode.LIGHT) {
        RejectPublicationScreen(
            RejectPublicationUiState(
                content = ReviewContent.Loaded(item),
                reason = RejectionReason.DUPLICATE,
                options = item.duplicate?.candidates.orEmpty(),
                optionsLoaded = true,
                originalId = item.duplicate?.candidates?.firstOrNull()?.poi?.id,
                message = "Este lugar ya está publicado como «Museo Botero». Puedes comentarlo o marcarlo como visitado.",
                suggested = true,
            ),
            RejectCallbacks(),
        )
    }
}

@Preview(name = "35.b · sin motivo · oscuro", widthDp = 360, heightDp = 800)
@Composable
private fun RejectMissingPreview() {
    ExploraCityTheme(ThemeMode.DARK) {
        RejectPublicationScreen(
            RejectPublicationUiState(content = ReviewContent.Loaded(previewReviews(previewNow)[0]), optionsLoaded = true, showErrors = true, attempts = 1),
            RejectCallbacks(),
        )
    }
}
