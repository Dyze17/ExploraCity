package co.edu.uniquindio.exploracity.ui.screens.account

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
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.ui.text.input.KeyboardType
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
import co.edu.uniquindio.exploracity.ui.screens.access.AccessOfflineNotice
import co.edu.uniquindio.exploracity.ui.screens.access.LinkExpiredContent
import co.edu.uniquindio.exploracity.ui.screens.access.LinkSentRoute
import co.edu.uniquindio.exploracity.ui.screens.access.LinkSentTexts
import co.edu.uniquindio.exploracity.ui.screens.access.PasswordEye
import co.edu.uniquindio.exploracity.ui.screens.access.SyncText
import co.edu.uniquindio.exploracity.ui.screens.access.withBoldEmail
import co.edu.uniquindio.exploracity.ui.theme.ExploraCityTheme
import co.edu.uniquindio.exploracity.ui.theme.exploraColors
import co.edu.uniquindio.exploracity.viewmodel.ChangeEmailError
import co.edu.uniquindio.exploracity.viewmodel.ChangeEmailField
import co.edu.uniquindio.exploracity.viewmodel.ChangeEmailUiState
import co.edu.uniquindio.exploracity.viewmodel.ChangeEmailViewModel
import co.edu.uniquindio.exploracity.viewmodel.ConfirmEmailContent
import co.edu.uniquindio.exploracity.viewmodel.ConfirmEmailDone
import co.edu.uniquindio.exploracity.viewmodel.ConfirmEmailViewModel
import co.edu.uniquindio.exploracity.viewmodel.EmailChangeRequested
import co.edu.uniquindio.exploracity.viewmodel.LinkSentViewModel

/** «Cambiar correo», conectada a su ViewModel. Con el enlace enviado sigue a «Confirma tu correo nuevo». */
@Composable
fun ChangeEmailRoute(
    onBack: () -> Unit,
    onSent: (EmailChangeRequested) -> Unit,
    viewModel: ChangeEmailViewModel = viewModel(factory = ChangeEmailViewModel.factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnSent by rememberUpdatedState(onSent)
    LaunchedEffect(state.sent) {
        val sent = state.sent ?: return@LaunchedEffect
        viewModel.onSentHandled()
        currentOnSent(sent)
    }
    ChangeEmailScreen(
        state = state,
        callbacks = ChangeEmailCallbacks(
            onBack = onBack,
            onEmailChange = viewModel::onEmailChange,
            onPasswordChange = viewModel::onPasswordChange,
            onEmailBlur = viewModel::onEmailBlur,
            onPasswordBlur = viewModel::onPasswordBlur,
            onSubmit = viewModel::onSubmit,
            onErrorShown = viewModel::onErrorShown,
        ),
    )
}

class ChangeEmailCallbacks(
    val onBack: () -> Unit = {},
    val onEmailChange: (String) -> Unit = {},
    val onPasswordChange: (String) -> Unit = {},
    val onEmailBlur: () -> Unit = {},
    val onPasswordBlur: () -> Unit = {},
    val onSubmit: () -> Unit = {},
    val onErrorShown: () -> Unit = {},
)

/**
 * Sin diseño propio (C2, A1 y B1): el correo de ahora, el cambio que espera confirmación si lo hay, el correo nuevo y la
 * contraseña. «Enviar enlace» se habilita con los dos en orden y, deshabilitado, dice por qué. Los errores van junto a
 * su campo; si el correo no sale, el aviso ofrece «Reintentar».
 */
@Composable
fun ChangeEmailScreen(state: ChangeEmailUiState, callbacks: ChangeEmailCallbacks, modifier: Modifier = Modifier) {
    val snackbarHostState = remember { SnackbarHostState() }
    ErrorEffect(state.error, snackbarHostState, callbacks)
    val emailFocus = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }
    LaunchedEffect(state.focusRequest) {
        when (state.focusField) {
            ChangeEmailField.EMAIL -> emailFocus.requestFocus()
            ChangeEmailField.PASSWORD -> passwordFocus.requestFocus()
            null -> Unit
        }
    }

    val focusManager = LocalFocusManager.current
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).pointerInput(Unit) { detectTapGestures { focusManager.clearFocus() } }) {
        Column(Modifier.fillMaxSize().imePadding()) {
            ExploraTopAppBar(title = stringResource(R.string.change_email_title), onBack = callbacks.onBack)
            if (state.offline) AccessOfflineNotice(stringResource(R.string.change_email_offline))
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    withBoldEmail(stringResource(R.string.change_email_intro, state.currentEmail), state.currentEmail),
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 22.sp),
                    color = MaterialTheme.exploraColors.textSecondary,
                )
                state.pendingEmail?.let { PendingNotice(it) }
                EmailField(state, callbacks, emailFocus, passwordFocus)
                PasswordField(state, callbacks, passwordFocus)
                val reason = when {
                    state.sending -> null
                    state.offline -> stringResource(R.string.change_email_offline_reason)
                    !state.canSubmit -> stringResource(R.string.change_email_fix_reason)
                    else -> null
                }
                ExploraButton(
                    stringResource(if (state.sending) R.string.recover_sending else R.string.recover_submit),
                    onClick = callbacks.onSubmit,
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

/** B1 · «Falta confirmar …»: pedir otro cambio deja sin efecto el enlace anterior. */
@Composable
private fun PendingNotice(pendingEmail: String) {
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
            stringResource(R.string.change_email_pending, pendingEmail),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 19.sp),
            color = warning.content,
        )
    }
}

@Composable
private fun EmailField(state: ChangeEmailUiState, callbacks: ChangeEmailCallbacks, focus: FocusRequester, next: FocusRequester) {
    val textState = rememberTextFieldState(state.newEmail)
    SyncText(textState, state.newEmail, callbacks.onEmailChange)
    ExploraTextField(
        state = textState,
        label = stringResource(R.string.change_email_new),
        errorMessage = when {
            state.emailTaken -> stringResource(R.string.register_email_taken)
            !state.emailTouched -> null
            !state.emailValid -> stringResource(R.string.login_email_error)
            state.sameAsCurrent -> stringResource(R.string.change_email_same)
            else -> null
        },
        enabled = !state.sending,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next, autoCorrectEnabled = false),
        onKeyboardAction = { next.requestFocus() },
        leadingIcon = R.drawable.ic_mail,
        modifier = Modifier.fillMaxWidth().focusRequester(focus).onBlur(callbacks.onEmailBlur),
    )
}

@Composable
private fun PasswordField(state: ChangeEmailUiState, callbacks: ChangeEmailCallbacks, focus: FocusRequester) {
    val textState = rememberTextFieldState(state.password)
    SyncText(textState, state.password, callbacks.onPasswordChange)
    var revealed by rememberSaveable { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    ExploraTextField(
        state = textState,
        label = stringResource(R.string.change_email_identity),
        supportingText = stringResource(R.string.change_email_identity_hint),
        errorMessage = when {
            state.wrongPassword -> stringResource(R.string.change_email_identity_wrong)
            state.passwordTouched && !state.passwordValid -> stringResource(R.string.login_password_error)
            else -> null
        },
        enabled = !state.sending,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        onKeyboardAction = {
            focusManager.clearFocus()
            callbacks.onSubmit()
        },
        leadingIcon = R.drawable.ic_lock,
        secure = true,
        revealed = revealed,
        trailing = { PasswordEye(revealed) { revealed = !revealed } },
        modifier = Modifier.fillMaxWidth().focusRequester(focus).onBlur(callbacks.onPasswordBlur),
    )
}

/** Si el correo no sale: «Reintentar»; otro fallo queda hasta «Cerrar». Lo escrito sigue ahí. */
@Composable
private fun ErrorEffect(error: ChangeEmailError?, hostState: SnackbarHostState, callbacks: ChangeEmailCallbacks) {
    val currentCallbacks by rememberUpdatedState(callbacks)
    val mailFailed = stringResource(R.string.recover_send_failed)
    val failed = stringResource(R.string.login_failed)
    val retry = stringResource(R.string.action_retry)
    val close = stringResource(R.string.login_close)
    LaunchedEffect(error) {
        when (error) {
            null -> Unit
            ChangeEmailError.MAIL_FAILED -> {
                val result = hostState.showSnackbar(mailFailed, actionLabel = retry, withDismissAction = true, duration = SnackbarDuration.Indefinite)
                currentCallbacks.onErrorShown()
                if (result == SnackbarResult.ActionPerformed) currentCallbacks.onSubmit()
            }
            ChangeEmailError.FAILED -> {
                hostState.showSnackbar(failed, actionLabel = close, duration = SnackbarDuration.Indefinite)
                currentCallbacks.onErrorShown()
            }
        }
    }
}

/** «Confirma tu correo nuevo»: como 6.a, con el correo con que se sigue entrando y la vuelta a Ajustes. */
@Composable
fun EmailChangeSentRoute(
    currentEmail: String,
    onBackToSettings: () -> Unit,
    onOpenLink: (token: String) -> Unit,
    viewModel: LinkSentViewModel = viewModel(factory = LinkSentViewModel.emailChangeFactory),
) {
    val email = viewModel.state.collectAsStateWithLifecycle().value.email
    LinkSentRoute(
        texts = LinkSentTexts(
            title = stringResource(R.string.change_email_sent_title),
            body = withBoldEmail(stringResource(R.string.change_email_sent_body, email, currentEmail), email),
            back = stringResource(R.string.back_to_settings),
        ),
        onBack = onBackToSettings,
        onOpenLink = onOpenLink,
        viewModel = viewModel,
    )
}

/** Abre el enlace del correo nuevo: confirmado, vuelve a Ajustes ([onConfirmed]); vencido, a [onExpired]. */
@Composable
fun ConfirmEmailRoute(
    onBack: () -> Unit,
    onConfirmed: (email: String) -> Unit,
    onExpired: (email: String) -> Unit,
    viewModel: ConfirmEmailViewModel = viewModel(factory = ConfirmEmailViewModel.factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnConfirmed by rememberUpdatedState(onConfirmed)
    val currentOnExpired by rememberUpdatedState(onExpired)
    LaunchedEffect(state.done) {
        when (val done = state.done) {
            is ConfirmEmailDone.Confirmed -> currentOnConfirmed(done.email)
            is ConfirmEmailDone.Expired -> currentOnExpired(done.email)
            null -> Unit
        }
    }
    ConfirmEmailScreen(state.content, onBack = onBack, onRetry = viewModel::onRetry)
}

/** «Confirmando tu correo…» y, si no se pudo, por qué y «Reintentar». */
@Composable
fun ConfirmEmailScreen(content: ConfirmEmailContent, onBack: () -> Unit, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        ExploraTopAppBar(title = stringResource(R.string.change_email_title), onBack = onBack)
        Box(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).windowInsetsPadding(WindowInsets.navigationBars).padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            when (content) {
                ConfirmEmailContent.LOADING -> Column(
                    Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                    Text(
                        stringResource(R.string.change_email_confirming),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.exploraColors.textSecondary,
                    )
                }
                ConfirmEmailContent.OFFLINE -> EmptyState(
                    icon = R.drawable.ic_cloud_off,
                    title = stringResource(R.string.offline_title),
                    body = stringResource(R.string.change_email_confirm_offline_body),
                    tone = EmptyStateTone.WARNING,
                ) { RetryButton(onRetry) }
                ConfirmEmailContent.ERROR -> EmptyState(
                    icon = R.drawable.ic_sync_problem,
                    title = stringResource(R.string.change_email_confirm_error_title),
                    body = stringResource(R.string.reset_error_body),
                    tone = EmptyStateTone.WARNING,
                ) { RetryButton(onRetry) }
            }
        }
    }
}

@Composable
private fun RetryButton(onRetry: () -> Unit) {
    ExploraButton(stringResource(R.string.action_retry), onClick = onRetry, modifier = Modifier.fillMaxWidth(), icon = R.drawable.ic_refresh)
}

/** El enlace del correo nuevo ya no sirve: «Pedir otro enlace» vuelve a «Cambiar correo» con ese correo escrito. */
@Composable
fun EmailLinkExpiredScreen(onRequestNew: () -> Unit, onBackToSettings: () -> Unit, modifier: Modifier = Modifier) {
    val minutes = AuthRules.RESET_LINK_DURATION.inWholeMinutes.toInt()
    LinkExpiredContent(
        body = pluralStringResource(R.plurals.change_email_expired_body, minutes, minutes),
        back = stringResource(R.string.back_to_settings),
        onRequestNew = onRequestNew,
        onBack = onBackToSettings,
        modifier = modifier,
    )
}

private val previewState = ChangeEmailUiState(currentEmail = "ana.rios@correo.com", newEmail = "ana.nueva@correo.com")

@Preview(name = "Cambiar correo · claro", widthDp = 360, heightDp = 800)
@Composable
private fun ChangeEmailPreview() {
    ExploraCityTheme(ThemeMode.LIGHT) { ChangeEmailScreen(previewState, ChangeEmailCallbacks()) }
}

@Preview(name = "Cambiar correo · pendiente y con errores · oscuro", widthDp = 360, heightDp = 800)
@Composable
private fun ChangeEmailErrorsPreview() {
    ExploraCityTheme(ThemeMode.DARK) {
        ChangeEmailScreen(
            previewState.copy(pendingEmail = "ana.otra@correo.com", takenEmail = "ana.nueva@correo.com", wrongPassword = true),
            ChangeEmailCallbacks(),
        )
    }
}

@Preview(name = "Cambiar correo · confirmando", widthDp = 360, heightDp = 800)
@Composable
private fun ConfirmEmailPreview() {
    ExploraCityTheme(ThemeMode.LIGHT) { ConfirmEmailScreen(ConfirmEmailContent.LOADING, {}, {}) }
}

@Preview(name = "Cambiar correo · enlace vencido", widthDp = 360, heightDp = 800)
@Composable
private fun EmailLinkExpiredPreview() {
    ExploraCityTheme(ThemeMode.DARK) { EmailLinkExpiredScreen({}, {}) }
}
