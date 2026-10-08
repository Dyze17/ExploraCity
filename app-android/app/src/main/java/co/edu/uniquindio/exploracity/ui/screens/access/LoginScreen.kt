package co.edu.uniquindio.exploracity.ui.screens.access

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import co.edu.uniquindio.exploracity.R
import co.edu.uniquindio.exploracity.data.google.GoogleCredentialResult
import co.edu.uniquindio.exploracity.domain.model.ThemeMode
import co.edu.uniquindio.exploracity.ui.components.ExploraButton
import co.edu.uniquindio.exploracity.ui.components.ExploraButtonStyle
import co.edu.uniquindio.exploracity.ui.components.ExploraSnackbarHost
import co.edu.uniquindio.exploracity.ui.components.ExploraTextField
import co.edu.uniquindio.exploracity.ui.components.onBlur
import co.edu.uniquindio.exploracity.ui.theme.ExploraCityTheme
import co.edu.uniquindio.exploracity.ui.theme.Outfit
import co.edu.uniquindio.exploracity.ui.theme.exploraColors
import co.edu.uniquindio.exploracity.viewmodel.GoogleRegistrationStart
import co.edu.uniquindio.exploracity.viewmodel.GoogleUiState
import co.edu.uniquindio.exploracity.viewmodel.LoginError
import co.edu.uniquindio.exploracity.viewmodel.LoginField
import co.edu.uniquindio.exploracity.viewmodel.LoginUiState
import co.edu.uniquindio.exploracity.viewmodel.LoginViewModel

/**
 * 3 · Inicio de sesión, conectado a su ViewModel. Al entrar sigue a [onSignedIn]. [notice] es el aviso con que se
 * llega («Cerraste sesión.», «Ya puedes entrar con tu contraseña nueva»). [suggestedEmail] llega del registro (4)
 * cuando el correo ya tenía cuenta. «¿Olvidaste tu contraseña?» pasa a 5 el correo escrito. Con una cuenta de Google
 * nueva sigue a [onGoogleRegistration] (C1).
 */
@Composable
fun LoginRoute(
    onSignedIn: () -> Unit,
    onForgotPassword: (email: String) -> Unit,
    onCreateAccount: () -> Unit,
    onGoogleRegistration: (GoogleRegistrationStart) -> Unit,
    notice: String? = null,
    onNoticeShown: () -> Unit = {},
    suggestedEmail: String? = null,
    onSuggestedEmailUsed: () -> Unit = {},
    viewModel: LoginViewModel = viewModel(factory = LoginViewModel.factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val google by viewModel.google.state.collectAsStateWithLifecycle()
    val currentOnSignedIn by rememberUpdatedState(onSignedIn)
    LaunchedEffect(state.signedIn) {
        if (state.signedIn) currentOnSignedIn()
    }
    val currentOnGoogleRegistration by rememberUpdatedState(onGoogleRegistration)
    LaunchedEffect(state.googleRegistration) {
        val start = state.googleRegistration ?: return@LaunchedEffect
        viewModel.onGoogleRegistrationShown()
        currentOnGoogleRegistration(start)
    }
    val currentOnSuggestedEmailUsed by rememberUpdatedState(onSuggestedEmailUsed)
    LaunchedEffect(suggestedEmail) {
        if (suggestedEmail == null) return@LaunchedEffect
        viewModel.onSuggestedEmail(suggestedEmail)
        currentOnSuggestedEmailUsed()
    }
    LoginScreen(
        state = state,
        callbacks = LoginCallbacks(
            onEmailChange = viewModel::onEmailChange,
            onPasswordChange = viewModel::onPasswordChange,
            onEmailBlur = viewModel::onEmailBlur,
            onPasswordBlur = viewModel::onPasswordBlur,
            onSubmit = viewModel::onSubmit,
            onErrorDismissed = viewModel::onErrorDismissed,
            onForgotPassword = { onForgotPassword(state.email.trim()) },
            onCreateAccount = onCreateAccount,
            onNoticeShown = onNoticeShown,
            google = GoogleCallbacks(
                request = viewModel::requestGoogleCredential,
                onResult = viewModel.google::onCredential,
                onLinkPasswordChange = viewModel.google::onLinkPasswordChange,
                onLinkSubmit = viewModel.google::onLinkSubmit,
                onLinkDismissed = viewModel.google::onLinkDismissed,
                onErrorDismissed = viewModel.google::onErrorDismissed,
                onForgotPassword = { email ->
                    viewModel.google.onLinkDismissed()
                    onForgotPassword(email)
                },
            ),
        ),
        notice = notice,
        google = google,
    )
}

class LoginCallbacks(
    val onEmailChange: (String) -> Unit = {},
    val onPasswordChange: (String) -> Unit = {},
    val onEmailBlur: () -> Unit = {},
    val onPasswordBlur: () -> Unit = {},
    val onSubmit: () -> Unit = {},
    val onErrorDismissed: () -> Unit = {},
    val onForgotPassword: () -> Unit = {},
    val onCreateAccount: () -> Unit = {},
    val onNoticeShown: () -> Unit = {},
    val google: GoogleCallbacks = GoogleCallbacks(),
)

/** ADR-15 · «Continuar con Google» y la contraseña para vincular (B1), iguales en 3 y en 4. */
class GoogleCallbacks(
    val request: suspend (Context) -> GoogleCredentialResult = { GoogleCredentialResult.Cancelled },
    val onResult: (GoogleCredentialResult) -> Unit = {},
    val onLinkPasswordChange: (String) -> Unit = {},
    val onLinkSubmit: () -> Unit = {},
    val onLinkDismissed: () -> Unit = {},
    val onErrorDismissed: () -> Unit = {},
    val onForgotPassword: (email: String) -> Unit = {},
)

/**
 * 3.a–3.c · Correo y contraseña con el ojo de 48 dp. «Iniciar sesión» se habilita al pasar las dos validaciones y,
 * deshabilitado, dice por qué. Mientras entra, los campos se bloquean y el botón dice «Entrando…». Sin conexión, el
 * aviso va arriba; si no coinciden, el aviso queda hasta que la persona lo cierra. Debajo, «Continuar con Google»
 * (ADR-15).
 */
@Composable
fun LoginScreen(
    state: LoginUiState,
    callbacks: LoginCallbacks,
    modifier: Modifier = Modifier,
    notice: String? = null,
    google: GoogleUiState = GoogleUiState(),
) {
    val snackbarHostState = remember { SnackbarHostState() }
    ErrorEffect(state.error, snackbarHostState, callbacks.onErrorDismissed)
    GoogleErrorEffect(google.error, snackbarHostState, callbacks.google.onErrorDismissed)
    NoticeEffect(notice, snackbarHostState, callbacks.onNoticeShown)
    google.link?.let { prompt ->
        GoogleLinkDialog(
            prompt = prompt,
            onIdentityChange = callbacks.google.onLinkPasswordChange,
            onSubmit = callbacks.google.onLinkSubmit,
            onDismiss = callbacks.google.onLinkDismissed,
            onForgotPassword = callbacks.google.onForgotPassword,
        )
    }
    val emailFocus = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }
    LaunchedEffect(state.focusRequest) {
        when (state.focusField) {
            LoginField.EMAIL -> emailFocus.requestFocus()
            LoginField.PASSWORD -> passwordFocus.requestFocus()
            null -> Unit
        }
    }

    val focusManager = LocalFocusManager.current
    // Tocar fuera de los campos los deja: se cierra el teclado y aparece el aviso del campo, si lo hay.
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).pointerInput(Unit) { detectTapGestures { focusManager.clearFocus() } }) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
            if (state.offline) AccessOfflineNotice(stringResource(R.string.login_offline))
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Logo(size = 56, iconSize = 30)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.login_title),
                        style = MaterialTheme.typography.headlineLarge.copy(fontFamily = Outfit, fontWeight = FontWeight.W600, fontSize = 28.sp, lineHeight = 36.sp),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(stringResource(R.string.login_subtitle), style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 22.sp), color = MaterialTheme.exploraColors.textSecondary)
                }
                EmailField(state, callbacks, emailFocus, passwordFocus)
                PasswordField(state, callbacks, passwordFocus)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    ExploraButton(stringResource(R.string.login_forgot_password), onClick = callbacks.onForgotPassword, style = ExploraButtonStyle.TEXT)
                }
                SubmitButton(state, callbacks.onSubmit)
                OrDivider()
                GoogleSignInButton(
                    busy = google.busy,
                    enabled = !state.submitting && !state.offline,
                    request = callbacks.google.request,
                    onResult = callbacks.google.onResult,
                )
                Spacer(Modifier.height(8.dp))
                FirstTime(callbacks.onCreateAccount)
            }
        }
        ExploraSnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing))
    }
}

@Composable
private fun EmailField(state: LoginUiState, callbacks: LoginCallbacks, focus: FocusRequester, next: FocusRequester) {
    val textState = rememberTextFieldState(state.email)
    SyncText(textState, state.email, callbacks.onEmailChange)
    ExploraTextField(
        state = textState,
        label = stringResource(R.string.login_email),
        errorMessage = if (state.showEmailError) stringResource(R.string.login_email_error) else null,
        enabled = !state.submitting,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next, autoCorrectEnabled = false),
        onKeyboardAction = { next.requestFocus() },
        leadingIcon = R.drawable.ic_mail,
        modifier = Modifier.fillMaxWidth().focusRequester(focus).onBlur(callbacks.onEmailBlur),
    )
}

@Composable
private fun PasswordField(state: LoginUiState, callbacks: LoginCallbacks, focus: FocusRequester) {
    val textState = rememberTextFieldState(state.password)
    SyncText(textState, state.password, callbacks.onPasswordChange)
    var revealed by rememberSaveable { mutableStateOf(false) }
    ExploraTextField(
        state = textState,
        label = stringResource(R.string.login_password),
        errorMessage = if (state.showPasswordError) stringResource(R.string.login_password_error) else null,
        enabled = !state.submitting,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        onKeyboardAction = { callbacks.onSubmit() },
        leadingIcon = R.drawable.ic_lock,
        secure = true,
        revealed = revealed,
        trailing = { PasswordEye(revealed) { revealed = !revealed } },
        modifier = Modifier.fillMaxWidth().focusRequester(focus).onBlur(callbacks.onPasswordBlur),
    )
}

@Composable
private fun SubmitButton(state: LoginUiState, onSubmit: () -> Unit) {
    val reason = when {
        state.offline -> stringResource(R.string.login_offline_reason)
        !state.emailValid || !state.passwordValid -> stringResource(R.string.login_fix_fields)
        else -> null
    }
    ExploraButton(
        stringResource(if (state.submitting) R.string.login_submitting else R.string.login_submit),
        onClick = onSubmit,
        modifier = Modifier.fillMaxWidth(),
        enabled = state.canSubmit || state.submitting,
        disabledReason = reason,
        loading = state.submitting,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FirstTime(onCreateAccount: () -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.login_first_time), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.exploraColors.textSecondary)
        ExploraButton(stringResource(R.string.login_create_account), onClick = onCreateAccount, style = ExploraButtonStyle.TEXT)
    }
}

/** El fallo queda en pantalla hasta que la persona lo cierra (README: el snackbar de error persiste). */
@Composable
private fun ErrorEffect(error: LoginError?, hostState: SnackbarHostState, onDismissed: () -> Unit) {
    val currentOnDismissed by rememberUpdatedState(onDismissed)
    val credentials = stringResource(R.string.login_credentials_error)
    val tooMany = stringResource(R.string.login_too_many_attempts)
    val failed = stringResource(R.string.login_failed)
    val close = stringResource(R.string.login_close)
    LaunchedEffect(error) {
        val text = when (error) {
            null -> return@LaunchedEffect
            LoginError.CREDENTIALS -> credentials
            LoginError.TOO_MANY_ATTEMPTS -> tooMany
            LoginError.FAILED -> failed
        }
        val result = hostState.showSnackbar(text, actionLabel = close, duration = SnackbarDuration.Indefinite)
        if (result == SnackbarResult.ActionPerformed || result == SnackbarResult.Dismissed) currentOnDismissed()
    }
}

@Composable
private fun NoticeEffect(notice: String?, hostState: SnackbarHostState, onShown: () -> Unit) {
    val currentOnShown by rememberUpdatedState(onShown)
    LaunchedEffect(notice) {
        if (notice == null) return@LaunchedEffect
        hostState.showSnackbar(notice, withDismissAction = true)
        currentOnShown()
    }
}

private val previewState = LoginUiState(email = "ana.rios@correo.com", password = "12345678")

@Preview(name = "3.a · con datos · claro", widthDp = 360, heightDp = 800)
@Composable
private fun LoginPreview() {
    ExploraCityTheme(ThemeMode.LIGHT) { LoginScreen(previewState, LoginCallbacks()) }
}

@Preview(name = "3.b · cargando · oscuro", widthDp = 360, heightDp = 800)
@Composable
private fun LoginSubmittingPreview() {
    ExploraCityTheme(ThemeMode.DARK) { LoginScreen(previewState.copy(submitting = true), LoginCallbacks()) }
}

@Preview(name = "3.c · errores y sin conexión", widthDp = 360, heightDp = 800)
@Composable
private fun LoginErrorPreview() {
    ExploraCityTheme(ThemeMode.LIGHT) {
        LoginScreen(LoginUiState(email = "ana.rios@correo", password = "12345", emailTouched = true, passwordTouched = true, offline = true), LoginCallbacks())
    }
}
