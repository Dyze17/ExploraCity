package co.edu.uniquindio.exploracity.ui.screens.access

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import co.edu.uniquindio.exploracity.R
import co.edu.uniquindio.exploracity.domain.model.Registration
import co.edu.uniquindio.exploracity.domain.model.Residency
import co.edu.uniquindio.exploracity.domain.model.ThemeMode
import co.edu.uniquindio.exploracity.navigation.LegalTab
import co.edu.uniquindio.exploracity.ui.components.ExploraButton
import co.edu.uniquindio.exploracity.ui.components.ExploraButtonStyle
import co.edu.uniquindio.exploracity.ui.components.ExploraSnackbarHost
import co.edu.uniquindio.exploracity.ui.components.ExploraTextField
import co.edu.uniquindio.exploracity.ui.components.ExploraTopAppBar
import co.edu.uniquindio.exploracity.ui.components.ResidencyPicker
import co.edu.uniquindio.exploracity.ui.components.onBlur
import co.edu.uniquindio.exploracity.ui.theme.ExploraCityTheme
import co.edu.uniquindio.exploracity.ui.theme.exploraColors
import co.edu.uniquindio.exploracity.viewmodel.GoogleMode
import co.edu.uniquindio.exploracity.viewmodel.GoogleUiState
import co.edu.uniquindio.exploracity.viewmodel.RegisterFailure
import co.edu.uniquindio.exploracity.viewmodel.RegisterField
import co.edu.uniquindio.exploracity.viewmodel.RegisterUiState
import co.edu.uniquindio.exploracity.viewmodel.RegisterViewModel

/**
 * 4 · Registro, conectado a su ViewModel. Al crear la cuenta sigue a [onRegistered] (el feed dice si quedó lista o si
 * no llegó el correo de bienvenida). [onSignInInstead] abre el inicio de sesión con el correo que ya tenía cuenta. Si
 * «Continuar con Google» encuentra la cuenta ya creada, sigue a [onSignedIn]; [onForgotPassword] viene del diálogo para
 * vincular (B1).
 */
@Composable
fun RegisterRoute(
    onBack: () -> Unit,
    onRegistered: (Registration) -> Unit,
    onSignedIn: () -> Unit,
    onOpenLegal: (LegalTab) -> Unit,
    onSignInInstead: (email: String) -> Unit,
    onForgotPassword: (email: String) -> Unit,
    viewModel: RegisterViewModel = viewModel(factory = RegisterViewModel.factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val google by viewModel.google.state.collectAsStateWithLifecycle()
    val currentOnRegistered by rememberUpdatedState(onRegistered)
    LaunchedEffect(state.registered) {
        state.registered?.let { currentOnRegistered(it) }
    }
    val currentOnSignedIn by rememberUpdatedState(onSignedIn)
    LaunchedEffect(state.signedIn) {
        if (state.signedIn) currentOnSignedIn()
    }
    RegisterScreen(
        state = state,
        callbacks = RegisterCallbacks(
            onBack = onBack,
            onNameChange = viewModel::onNameChange,
            onEmailChange = viewModel::onEmailChange,
            onPasswordChange = viewModel::onPasswordChange,
            onNameBlur = viewModel::onNameBlur,
            onEmailBlur = viewModel::onEmailBlur,
            onPasswordBlur = viewModel::onPasswordBlur,
            onResidencyChange = viewModel::onResidencyChange,
            onConsentChange = viewModel::onConsentChange,
            onOpenLegal = onOpenLegal,
            onSignInInstead = { onSignInInstead(state.email.trim()) },
            onSubmit = viewModel::onSubmit,
            onFailureDismissed = viewModel::onFailureDismissed,
            onUseEmailInstead = viewModel::onUseEmailInstead,
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
        google = google,
    )
}

class RegisterCallbacks(
    val onBack: () -> Unit = {},
    val onNameChange: (String) -> Unit = {},
    val onEmailChange: (String) -> Unit = {},
    val onPasswordChange: (String) -> Unit = {},
    val onNameBlur: () -> Unit = {},
    val onEmailBlur: () -> Unit = {},
    val onPasswordBlur: () -> Unit = {},
    val onResidencyChange: (Residency) -> Unit = {},
    val onConsentChange: (Boolean) -> Unit = {},
    val onOpenLegal: (LegalTab) -> Unit = {},
    val onSignInInstead: () -> Unit = {},
    val onSubmit: () -> Unit = {},
    val onFailureDismissed: () -> Unit = {},
    val onUseEmailInstead: () -> Unit = {},
    val google: GoogleCallbacks = GoogleCallbacks(),
)

/**
 * 4.a/4.b · Nombre, correo, contraseña con su regla a la vista, «¿Cómo te presentas?» y la autorización de datos (Ley
 * 1581), nunca marcada de antemano. «Crear cuenta» se habilita con todo en orden y la autorización marcada; mientras
 * no, el texto de abajo dice qué falta. Los enlaces abren 4A sin perder lo escrito. Arriba, «Continuar con Google»
 * (ADR-15); en modo Google (C1) no hay correo ni contraseña que escribir.
 */
@Composable
fun RegisterScreen(state: RegisterUiState, callbacks: RegisterCallbacks, modifier: Modifier = Modifier, google: GoogleUiState = GoogleUiState()) {
    val snackbarHostState = remember { SnackbarHostState() }
    FailureEffect(state.failure, snackbarHostState, callbacks.onFailureDismissed)
    GoogleErrorEffect(google.error, snackbarHostState, callbacks.google.onErrorDismissed)
    google.link?.let { prompt ->
        GoogleLinkDialog(
            prompt = prompt,
            onIdentityChange = callbacks.google.onLinkPasswordChange,
            onSubmit = callbacks.google.onLinkSubmit,
            onDismiss = callbacks.google.onLinkDismissed,
            onForgotPassword = callbacks.google.onForgotPassword,
        )
    }
    val nameFocus = remember { FocusRequester() }
    val emailFocus = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }
    LaunchedEffect(state.focusRequest) {
        when (state.focusField) {
            RegisterField.NAME -> nameFocus.requestFocus()
            RegisterField.EMAIL -> emailFocus.requestFocus()
            RegisterField.PASSWORD -> passwordFocus.requestFocus()
            null -> Unit
        }
    }

    val focusManager = LocalFocusManager.current
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).pointerInput(Unit) { detectTapGestures { focusManager.clearFocus() } }) {
        Column(Modifier.fillMaxSize().imePadding()) {
            ExploraTopAppBar(title = stringResource(R.string.register_title), onBack = callbacks.onBack)
            if (state.offline) AccessOfflineNotice(stringResource(R.string.register_offline))
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                val googleMode = state.google
                if (googleMode == null) {
                    GoogleSignInButton(
                        busy = google.busy,
                        enabled = !state.submitting && !state.offline,
                        request = callbacks.google.request,
                        onResult = callbacks.google.onResult,
                    )
                    OrDivider()
                    NameField(state, callbacks, nameFocus, emailFocus)
                    EmailField(state, callbacks, emailFocus, passwordFocus)
                    PasswordField(state, callbacks, passwordFocus)
                } else {
                    GoogleModeCard(googleMode.email, enabled = !state.submitting, onUseEmailInstead = callbacks.onUseEmailInstead)
                    NameField(state, callbacks, nameFocus, next = null)
                }
                ResidencyPicker(state.residency, callbacks.onResidencyChange, enabled = !state.submitting)
                ConsentCard(state.consent, enabled = !state.submitting, callbacks)
                SubmitBlock(state, callbacks.onSubmit)
            }
        }
        ExploraSnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing))
    }
}

/** Sin [next] (modo Google, el único campo): «Listo» lo deja y quedan a la vista la autorización y el botón. */
@Composable
private fun NameField(state: RegisterUiState, callbacks: RegisterCallbacks, focus: FocusRequester, next: FocusRequester?) {
    val textState = rememberTextFieldState(state.name)
    SyncText(textState, state.name, callbacks.onNameChange)
    val focusManager = LocalFocusManager.current
    ExploraTextField(
        state = textState,
        label = stringResource(R.string.profile_field_name),
        errorMessage = when {
            !state.showNameError -> null
            state.nameExcess > 0 -> pluralStringResource(R.plurals.profile_too_long, state.nameExcess, state.nameExcess)
            else -> pluralStringResource(R.plurals.profile_name_short, state.nameMissing, state.nameMissing)
        },
        enabled = !state.submitting,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = if (next == null) ImeAction.Done else ImeAction.Next),
        onKeyboardAction = { if (next == null) focusManager.clearFocus() else next.requestFocus() },
        modifier = Modifier.fillMaxWidth().focusRequester(focus).onBlur(callbacks.onNameBlur),
    )
}

/** Con un correo que ya tiene cuenta: el aviso junto al campo y el enlace al inicio de sesión (3). */
@Composable
private fun EmailField(state: RegisterUiState, callbacks: RegisterCallbacks, focus: FocusRequester, next: FocusRequester) {
    val textState = rememberTextFieldState(state.email)
    SyncText(textState, state.email, callbacks.onEmailChange)
    Column {
        ExploraTextField(
            state = textState,
            label = stringResource(R.string.login_email),
            errorMessage = when {
                state.emailTaken -> stringResource(R.string.register_email_taken)
                state.showEmailError -> stringResource(R.string.login_email_error)
                else -> null
            },
            enabled = !state.submitting,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next, autoCorrectEnabled = false),
            onKeyboardAction = { next.requestFocus() },
            modifier = Modifier.fillMaxWidth().focusRequester(focus).onBlur(callbacks.onEmailBlur),
        )
        if (state.emailTaken) {
            ExploraButton(stringResource(R.string.register_sign_in_instead), onClick = callbacks.onSignInInstead, style = ExploraButtonStyle.TEXT)
        }
    }
}

/** La regla se ve desde el principio y cambia a «Contraseña válida» al cumplirse (4.a/4.b). */
@Composable
private fun PasswordField(state: RegisterUiState, callbacks: RegisterCallbacks, focus: FocusRequester) {
    val textState = rememberTextFieldState(state.password)
    SyncText(textState, state.password, callbacks.onPasswordChange)
    var revealed by rememberSaveable { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ExploraTextField(
            state = textState,
            label = stringResource(R.string.login_password),
            enabled = !state.submitting,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            // «Listo» deja el campo: aparece su aviso y quedan a la vista la autorización y el botón.
            onKeyboardAction = { focusManager.clearFocus() },
            secure = true,
            revealed = revealed,
            trailing = { PasswordEye(revealed) { revealed = !revealed } },
            modifier = Modifier.fillMaxWidth().focusRequester(focus).onBlur(callbacks.onPasswordBlur),
        )
        val requirement = when {
            state.passwordValid -> RequirementState.MET
            state.showPasswordError -> RequirementState.MISSING
            else -> RequirementState.PENDING
        }
        RequirementRow(
            stringResource(if (state.passwordValid) R.string.register_rule_valid else R.string.register_rule),
            requirement,
            Modifier.padding(start = 4.dp),
        )
    }
}

/**
 * La autorización (Ley 1581): la casilla y su texto completo son un solo control que se marca tocando la fila; el lector
 * oye lo que se autoriza y si está marcado. Los enlaces van aparte, debajo, y abren 4A.
 */
@Composable
private fun ConsentCard(consent: Boolean, enabled: Boolean, callbacks: RegisterCallbacks) {
    val shape = MaterialTheme.shapes.medium
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .padding(vertical = 4.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .toggleable(value = consent, enabled = enabled, role = Role.Checkbox, onValueChange = callbacks.onConsentChange)
                .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Checkbox(
                checked = consent,
                onCheckedChange = null,
                enabled = enabled,
                colors = CheckboxDefaults.colors(uncheckedColor = MaterialTheme.exploraColors.iconSecondary),
                modifier = Modifier.padding(12.dp),
            )
            Text(
                stringResource(R.string.register_consent),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 19.sp),
                color = MaterialTheme.exploraColors.textSecondary,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        Column(Modifier.padding(start = 40.dp, end = 8.dp, bottom = 4.dp)) {
            ExploraButton(
                stringResource(R.string.settings_policy),
                onClick = { callbacks.onOpenLegal(LegalTab.POLICY) },
                style = ExploraButtonStyle.TEXT,
                icon = R.drawable.ic_open_in_new,
            )
            ExploraButton(
                stringResource(R.string.settings_privacy_notice),
                onClick = { callbacks.onOpenLegal(LegalTab.PRIVACY_NOTICE) },
                style = ExploraButtonStyle.TEXT,
                icon = R.drawable.ic_open_in_new,
            )
        }
    }
}

/** «Crear cuenta» y, mientras esté deshabilitado, qué falta (4.a). Sin conexión lo dice el aviso de arriba. */
@Composable
private fun SubmitBlock(state: RegisterUiState, onSubmit: () -> Unit) {
    val (visible, reason) = when {
        state.submitting -> null to null
        state.offline -> null to stringResource(R.string.register_offline_reason)
        !state.fieldsValid && state.google != null ->
            stringResource(R.string.register_google_fix_fields) to stringResource(R.string.register_google_fix_fields_reason)
        !state.fieldsValid -> stringResource(R.string.register_fix_fields) to stringResource(R.string.register_fix_fields_reason)
        !state.consent -> stringResource(R.string.register_consent_missing) to stringResource(R.string.register_consent_missing_reason)
        else -> null to null
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ExploraButton(
            stringResource(if (state.submitting) R.string.register_submitting else R.string.register_submit),
            onClick = onSubmit,
            modifier = Modifier.fillMaxWidth(),
            enabled = state.canSubmit || state.submitting,
            disabledReason = reason,
            loading = state.submitting,
        )
        if (visible != null) {
            // El botón ya lo anuncia al recibir el foco: aquí solo se ve.
            Text(
                visible,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
                color = MaterialTheme.exploraColors.iconSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clearAndSetSemantics { },
            )
        }
    }
}

/**
 * El fallo queda en pantalla hasta que la persona lo cierra, con lo escrito intacto. Si el servidor no respondió, no
 * dice que la cuenta no se creó: intentarlo de nuevo es seguro y, si ya quedó, entra con ella.
 */
@Composable
private fun FailureEffect(failure: RegisterFailure?, hostState: SnackbarHostState, onDismissed: () -> Unit) {
    val currentOnDismissed by rememberUpdatedState(onDismissed)
    val notCreated = stringResource(R.string.register_failed)
    val unconfirmed = stringResource(R.string.register_unconfirmed)
    val close = stringResource(R.string.login_close)
    LaunchedEffect(failure) {
        if (failure == null) return@LaunchedEffect
        val text = if (failure == RegisterFailure.UNCONFIRMED) unconfirmed else notCreated
        hostState.showSnackbar(text, actionLabel = close, duration = SnackbarDuration.Indefinite)
        currentOnDismissed()
    }
}

// Sin contraseñas escritas en el código (GitGuardian): una cualquiera que cumpla la regla.
private val previewState = RegisterUiState(name = "Ana Ríos", email = "ana.rios@correo.com", password = "a".repeat(9) + "1")

@Preview(name = "4.a · sin marcar · claro", widthDp = 360, heightDp = 900)
@Composable
private fun RegisterPreview() {
    ExploraCityTheme(ThemeMode.LIGHT) { RegisterScreen(previewState, RegisterCallbacks()) }
}

@Preview(name = "4.b · marcado y listo · oscuro", widthDp = 360, heightDp = 900)
@Composable
private fun RegisterReadyPreview() {
    ExploraCityTheme(ThemeMode.DARK) { RegisterScreen(previewState.copy(residency = Residency.RESIDENT, consent = true), RegisterCallbacks()) }
}

@Preview(name = "4 · modo Google · claro", widthDp = 360, heightDp = 900)
@Composable
private fun RegisterGooglePreview() {
    ExploraCityTheme(ThemeMode.LIGHT) {
        RegisterScreen(RegisterUiState(name = "Ana Ríos", google = GoogleMode("ana.rios@gmail.com")), RegisterCallbacks())
    }
}

@Preview(name = "4 · correo con cuenta y sin conexión", widthDp = 360, heightDp = 900)
@Composable
private fun RegisterTakenPreview() {
    ExploraCityTheme(ThemeMode.LIGHT) {
        RegisterScreen(previewState.copy(takenEmail = "ana.rios@correo.com", password = "a", passwordTouched = true, offline = true), RegisterCallbacks())
    }
}
