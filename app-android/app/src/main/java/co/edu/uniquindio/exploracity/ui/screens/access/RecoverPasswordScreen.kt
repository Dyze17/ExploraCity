package co.edu.uniquindio.exploracity.ui.screens.access

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import co.edu.uniquindio.exploracity.R
import co.edu.uniquindio.exploracity.domain.model.AuthRules
import co.edu.uniquindio.exploracity.domain.model.ThemeMode
import co.edu.uniquindio.exploracity.ui.components.ExploraButton
import co.edu.uniquindio.exploracity.ui.components.ExploraSnackbarHost
import co.edu.uniquindio.exploracity.ui.components.ExploraTextField
import co.edu.uniquindio.exploracity.ui.components.ExploraTopAppBar
import co.edu.uniquindio.exploracity.ui.components.onBlur
import co.edu.uniquindio.exploracity.ui.theme.ExploraCityTheme
import co.edu.uniquindio.exploracity.ui.theme.exploraColors
import co.edu.uniquindio.exploracity.viewmodel.RecoverPasswordUiState
import co.edu.uniquindio.exploracity.viewmodel.RecoverPasswordViewModel
import co.edu.uniquindio.exploracity.viewmodel.RecoverySent

/** 5 · Recuperar contraseña, conectada a su ViewModel. Con el enlace enviado sigue a «Revisa tu correo» (6.a). */
@Composable
fun RecoverPasswordRoute(
    onBack: () -> Unit,
    onSent: (RecoverySent) -> Unit,
    viewModel: RecoverPasswordViewModel = viewModel(factory = RecoverPasswordViewModel.factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnSent by rememberUpdatedState(onSent)
    LaunchedEffect(state.sent) {
        val sent = state.sent ?: return@LaunchedEffect
        viewModel.onSentHandled()
        currentOnSent(sent)
    }
    RecoverPasswordScreen(
        state = state,
        onBack = onBack,
        onEmailChange = viewModel::onEmailChange,
        onEmailBlur = viewModel::onEmailBlur,
        onSubmit = viewModel::onSubmit,
        onFailureShown = viewModel::onFailureShown,
    )
}

/**
 * 5.a · Correo con su validación y «Enviar enlace». Si el servicio de correo falla, el aviso ofrece «Reintentar» y el
 * correo sigue escrito. Sin conexión, el aviso ámbar va arriba y el botón queda deshabilitado.
 */
@Composable
fun RecoverPasswordScreen(
    state: RecoverPasswordUiState,
    onBack: () -> Unit,
    onEmailChange: (String) -> Unit,
    onEmailBlur: () -> Unit,
    onSubmit: () -> Unit,
    onFailureShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    SendFailedEffect(state.failed, snackbarHostState, onRetry = onSubmit, onShown = onFailureShown)
    val emailFocus = remember { FocusRequester() }
    LaunchedEffect(state.focusRequest) {
        if (state.focusRequest > 0) emailFocus.requestFocus()
    }

    val focusManager = LocalFocusManager.current
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).pointerInput(Unit) { detectTapGestures { focusManager.clearFocus() } }) {
        Column(Modifier.fillMaxSize().imePadding()) {
            ExploraTopAppBar(title = stringResource(R.string.recover_title), onBack = onBack)
            if (state.offline) AccessOfflineNotice(stringResource(R.string.recover_offline))
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                AccessIcon(R.drawable.ic_mail, MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer, size = 72)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AccessHeadline(stringResource(R.string.recover_headline))
                    Text(
                        stringResource(R.string.recover_body),
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 22.sp),
                        color = MaterialTheme.exploraColors.textSecondary,
                    )
                }
                EmailField(state, onEmailChange, onEmailBlur, onSubmit, emailFocus)
                val reason = when {
                    state.offline -> stringResource(R.string.recover_offline_reason)
                    !state.emailValid -> stringResource(R.string.recover_fix_email)
                    else -> null
                }
                ExploraButton(
                    stringResource(if (state.sending) R.string.recover_sending else R.string.recover_submit),
                    onClick = onSubmit,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.canSubmit || state.sending,
                    disabledReason = reason,
                    loading = state.sending,
                )
            }
        }
        ExploraSnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing))
    }
}

@Composable
private fun EmailField(state: RecoverPasswordUiState, onChange: (String) -> Unit, onBlur: () -> Unit, onSubmit: () -> Unit, focus: FocusRequester) {
    val textState = rememberTextFieldState(state.email)
    SyncText(textState, state.email, onChange)
    val minutes = AuthRules.RESET_LINK_DURATION.inWholeMinutes.toInt()
    ExploraTextField(
        state = textState,
        label = stringResource(R.string.login_email),
        supportingText = pluralStringResource(R.plurals.recover_expiry_hint, minutes, minutes),
        errorMessage = if (state.showEmailError) stringResource(R.string.login_email_error) else null,
        enabled = !state.sending,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Send, autoCorrectEnabled = false),
        onKeyboardAction = { onSubmit() },
        leadingIcon = R.drawable.ic_mail,
        modifier = Modifier.fillMaxWidth().focusRequester(focus).onBlur(onBlur),
    )
}

/** «No pudimos enviar el correo. Intenta en unos minutos» + «Reintentar», hasta que la persona decida. */
@Composable
private fun SendFailedEffect(failed: Boolean, hostState: SnackbarHostState, onRetry: () -> Unit, onShown: () -> Unit) {
    val currentOnRetry by rememberUpdatedState(onRetry)
    val currentOnShown by rememberUpdatedState(onShown)
    val text = stringResource(R.string.recover_send_failed)
    val retry = stringResource(R.string.action_retry)
    LaunchedEffect(failed) {
        if (!failed) return@LaunchedEffect
        val result = hostState.showSnackbar(text, actionLabel = retry, withDismissAction = true, duration = SnackbarDuration.Indefinite)
        currentOnShown()
        if (result == SnackbarResult.ActionPerformed) currentOnRetry()
    }
}

private val previewState = RecoverPasswordUiState(email = "ana.rios@correo.com")

@Preview(name = "5.a · solicitud · claro", widthDp = 360, heightDp = 800)
@Composable
private fun RecoverPreview() {
    ExploraCityTheme(ThemeMode.LIGHT) { RecoverPasswordScreen(previewState, {}, {}, {}, {}, {}) }
}

@Preview(name = "5.a · enviando · oscuro", widthDp = 360, heightDp = 800)
@Composable
private fun RecoverSendingPreview() {
    ExploraCityTheme(ThemeMode.DARK) { RecoverPasswordScreen(previewState.copy(sending = true), {}, {}, {}, {}, {}) }
}

@Preview(name = "5 · sin conexión y correo incompleto", widthDp = 360, heightDp = 800)
@Composable
private fun RecoverOfflinePreview() {
    ExploraCityTheme(ThemeMode.LIGHT) { RecoverPasswordScreen(RecoverPasswordUiState(email = "ana.rios@", emailTouched = true, offline = true), {}, {}, {}, {}, {}) }
}
