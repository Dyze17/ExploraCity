package co.edu.uniquindio.exploracity.ui.screens.access

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import co.edu.uniquindio.exploracity.R
import co.edu.uniquindio.exploracity.domain.model.AuthRules
import co.edu.uniquindio.exploracity.domain.model.ThemeMode
import co.edu.uniquindio.exploracity.ui.components.EmptyState
import co.edu.uniquindio.exploracity.ui.components.EmptyStateTone
import co.edu.uniquindio.exploracity.ui.components.ExploraButton
import co.edu.uniquindio.exploracity.ui.components.ExploraSnackbarHost
import co.edu.uniquindio.exploracity.ui.components.ExploraTextField
import co.edu.uniquindio.exploracity.ui.components.ExploraTopAppBar
import co.edu.uniquindio.exploracity.ui.components.onBlur
import co.edu.uniquindio.exploracity.ui.components.scaledWithFont
import co.edu.uniquindio.exploracity.ui.theme.ExploraCityTheme
import co.edu.uniquindio.exploracity.ui.theme.exploraColors
import co.edu.uniquindio.exploracity.viewmodel.NewPasswordContent
import co.edu.uniquindio.exploracity.viewmodel.NewPasswordDone
import co.edu.uniquindio.exploracity.viewmodel.NewPasswordField
import co.edu.uniquindio.exploracity.viewmodel.NewPasswordUiState
import co.edu.uniquindio.exploracity.viewmodel.NewPasswordViewModel

/** 6.b · Nueva contraseña, conectada a su ViewModel. Guardada sigue a [onSaved]; con el enlace vencido, a [onExpired] (6C). */
@Composable
fun NewPasswordRoute(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onExpired: (email: String) -> Unit,
    viewModel: NewPasswordViewModel = viewModel(factory = NewPasswordViewModel.factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnSaved by rememberUpdatedState(onSaved)
    val currentOnExpired by rememberUpdatedState(onExpired)
    LaunchedEffect(state.done) {
        when (val done = state.done) {
            NewPasswordDone.Saved -> currentOnSaved()
            is NewPasswordDone.Expired -> currentOnExpired(done.email)
            null -> Unit
        }
    }
    NewPasswordScreen(
        state = state,
        callbacks = NewPasswordCallbacks(
            onBack = onBack,
            onRetry = viewModel::onRetry,
            onPasswordChange = viewModel::onPasswordChange,
            onConfirmChange = viewModel::onConfirmChange,
            onPasswordBlur = viewModel::onPasswordBlur,
            onConfirmBlur = viewModel::onConfirmBlur,
            onSubmit = viewModel::onSubmit,
            onSaveFailureDismissed = viewModel::onSaveFailureDismissed,
        ),
    )
}

class NewPasswordCallbacks(
    val onBack: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onPasswordChange: (String) -> Unit = {},
    val onConfirmChange: (String) -> Unit = {},
    val onPasswordBlur: () -> Unit = {},
    val onConfirmBlur: () -> Unit = {},
    val onSubmit: () -> Unit = {},
    val onSaveFailureDismissed: () -> Unit = {},
)

/**
 * 6.b · Para qué cuenta es, la contraseña nueva con sus requisitos como lista (icono y texto), «Repite la contraseña»
 * con su aviso si no coinciden, «Guardar contraseña» y cuánto le queda al enlace.
 */
@Composable
fun NewPasswordScreen(state: NewPasswordUiState, callbacks: NewPasswordCallbacks, modifier: Modifier = Modifier) {
    val snackbarHostState = remember { SnackbarHostState() }
    SaveFailedEffect(state.saveFailed, snackbarHostState, callbacks.onSaveFailureDismissed)
    val focusManager = LocalFocusManager.current
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).pointerInput(Unit) { detectTapGestures { focusManager.clearFocus() } }) {
        Column(Modifier.fillMaxSize().imePadding()) {
            ExploraTopAppBar(title = stringResource(R.string.reset_title), onBack = callbacks.onBack)
            when (state.content) {
                NewPasswordContent.LOADING -> Loading()
                NewPasswordContent.READY -> {
                    if (state.offline) AccessOfflineNotice(stringResource(R.string.reset_offline))
                    Form(state, callbacks)
                }
                NewPasswordContent.OFFLINE -> Centered {
                    EmptyState(
                        icon = R.drawable.ic_cloud_off,
                        title = stringResource(R.string.offline_title),
                        body = stringResource(R.string.reset_offline_body),
                        tone = EmptyStateTone.WARNING,
                    ) { RetryButton(callbacks.onRetry) }
                }
                NewPasswordContent.ERROR -> Centered {
                    EmptyState(
                        icon = R.drawable.ic_sync_problem,
                        title = stringResource(R.string.reset_error_title),
                        body = stringResource(R.string.reset_error_body),
                        tone = EmptyStateTone.WARNING,
                    ) { RetryButton(callbacks.onRetry) }
                }
            }
        }
        ExploraSnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing))
    }
}

@Composable
private fun Form(state: NewPasswordUiState, callbacks: NewPasswordCallbacks) {
    val passwordFocus = remember { FocusRequester() }
    val confirmFocus = remember { FocusRequester() }
    LaunchedEffect(state.focusRequest) {
        when (state.focusField) {
            NewPasswordField.PASSWORD -> passwordFocus.requestFocus()
            NewPasswordField.CONFIRM -> confirmFocus.requestFocus()
            null -> Unit
        }
    }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            stringResource(R.string.reset_for, state.email),
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 22.sp),
            color = MaterialTheme.exploraColors.textSecondary,
        )
        PasswordWithRequirements(state, callbacks, passwordFocus, confirmFocus)
        ConfirmField(state, callbacks, confirmFocus)
        val reason = when {
            state.saving -> null
            state.offline -> stringResource(R.string.reset_offline_reason)
            !state.canSubmit -> stringResource(R.string.reset_fix_reason)
            else -> null
        }
        ExploraButton(
            stringResource(if (state.saving) R.string.reset_saving else R.string.reset_submit),
            onClick = callbacks.onSubmit,
            modifier = Modifier.fillMaxWidth(),
            enabled = state.canSubmit || state.saving,
            disabledReason = reason,
            loading = state.saving,
        )
        if (state.minutesLeft > 0) ExpiryNotice(state.minutesLeft)
    }
}

@Composable
private fun PasswordWithRequirements(state: NewPasswordUiState, callbacks: NewPasswordCallbacks, focus: FocusRequester, next: FocusRequester) {
    val textState = rememberTextFieldState(state.password)
    SyncText(textState, state.password, callbacks.onPasswordChange)
    var revealed by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ExploraTextField(
            state = textState,
            label = stringResource(R.string.reset_field),
            enabled = !state.saving,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            onKeyboardAction = { next.requestFocus() },
            secure = true,
            revealed = revealed,
            trailing = { PasswordEye(revealed) { revealed = !revealed } },
            modifier = Modifier.fillMaxWidth().focusRequester(focus).onBlur(callbacks.onPasswordBlur),
        )
        Column(Modifier.padding(start = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            fun requirement(met: Boolean) = when {
                met -> RequirementState.MET
                state.showRequirementErrors -> RequirementState.MISSING
                else -> RequirementState.PENDING
            }
            RequirementRow(
                pluralStringResource(R.plurals.reset_rule_length, AuthRules.PASSWORD_MIN, AuthRules.PASSWORD_MIN),
                requirement(state.hasLength),
            )
            RequirementRow(stringResource(R.string.reset_rule_mix), requirement(state.hasLetterAndDigit))
        }
    }
}

@Composable
private fun ConfirmField(state: NewPasswordUiState, callbacks: NewPasswordCallbacks, focus: FocusRequester) {
    val textState = rememberTextFieldState(state.confirm)
    SyncText(textState, state.confirm, callbacks.onConfirmChange)
    val focusManager = LocalFocusManager.current
    // Sin ojo, como el lienzo: con error, el campo muestra su icono (el ojo de arriba ya permite revisar la nueva).
    ExploraTextField(
        state = textState,
        label = stringResource(R.string.reset_confirm),
        errorMessage = if (state.showMismatch) stringResource(R.string.reset_mismatch) else null,
        enabled = !state.saving,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        // «Listo» cierra el teclado y guarda: el inicio de sesión (3) no abre con el teclado del campo anterior.
        onKeyboardAction = {
            focusManager.clearFocus()
            callbacks.onSubmit()
        },
        secure = true,
        modifier = Modifier.fillMaxWidth().focusRequester(focus).onBlur(callbacks.onConfirmBlur),
    )
}

/** «Este enlace vence en 12 minutos…», en ámbar. Se pone al día cada minuto. */
@Composable
private fun ExpiryNotice(minutes: Int) {
    val warning = MaterialTheme.exploraColors.warning
    Row(
        Modifier
            .fillMaxWidth()
            .background(warning.container, MaterialTheme.shapes.medium)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) { },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(painterResource(R.drawable.ic_schedule), null, tint = warning.content, modifier = Modifier.size(20.dp.scaledWithFont()))
        Text(
            pluralStringResource(R.plurals.reset_expiry, minutes, minutes),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 19.sp),
            color = warning.content,
        )
    }
}

@Composable
private fun Loading() {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
            Text(stringResource(R.string.reset_loading), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.exploraColors.textSecondary)
        }
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

/** El fallo al guardar queda hasta que la persona lo cierra; lo escrito sigue ahí. */
@Composable
private fun SaveFailedEffect(failed: Boolean, hostState: SnackbarHostState, onDismissed: () -> Unit) {
    val currentOnDismissed by rememberUpdatedState(onDismissed)
    val text = stringResource(R.string.reset_save_failed)
    val close = stringResource(R.string.login_close)
    LaunchedEffect(failed) {
        if (!failed) return@LaunchedEffect
        hostState.showSnackbar(text, actionLabel = close, duration = SnackbarDuration.Indefinite)
        currentOnDismissed()
    }
}

// Sin contraseñas escritas en el código (GitGuardian): valores que muestran el error del lienzo.
private val previewState = NewPasswordUiState(
    content = NewPasswordContent.READY,
    email = "ana.rios@correo.com",
    minutesLeft = 12,
    password = "a".repeat(8) + "1",
    confirm = "a".repeat(6),
    confirmTouched = true,
)

@Preview(name = "6.b · con error · oscuro", widthDp = 360, heightDp = 800)
@Composable
private fun NewPasswordPreview() {
    ExploraCityTheme(ThemeMode.DARK) { NewPasswordScreen(previewState, NewPasswordCallbacks()) }
}

@Preview(name = "6.b · requisitos pendientes · claro", widthDp = 360, heightDp = 800)
@Composable
private fun NewPasswordPendingPreview() {
    ExploraCityTheme(ThemeMode.LIGHT) {
        NewPasswordScreen(previewState.copy(password = "a".repeat(5), passwordTouched = true, confirm = "", confirmTouched = false), NewPasswordCallbacks())
    }
}

@Preview(name = "6.b · revisando el enlace", widthDp = 360, heightDp = 800)
@Composable
private fun NewPasswordLoadingPreview() {
    ExploraCityTheme(ThemeMode.LIGHT) { NewPasswordScreen(NewPasswordUiState(), NewPasswordCallbacks()) }
}
