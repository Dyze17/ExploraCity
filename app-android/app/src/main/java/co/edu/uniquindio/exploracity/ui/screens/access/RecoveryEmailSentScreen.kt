package co.edu.uniquindio.exploracity.ui.screens.access

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import co.edu.uniquindio.exploracity.R
import co.edu.uniquindio.exploracity.domain.model.ThemeMode
import co.edu.uniquindio.exploracity.ui.components.ExploraButton
import co.edu.uniquindio.exploracity.ui.components.ExploraButtonStyle
import co.edu.uniquindio.exploracity.ui.theme.ExploraCityTheme
import co.edu.uniquindio.exploracity.ui.theme.exploraColors
import co.edu.uniquindio.exploracity.util.hasEmailApp
import co.edu.uniquindio.exploracity.util.openEmailApp
import co.edu.uniquindio.exploracity.viewmodel.RecoveryEmailSentUiState
import co.edu.uniquindio.exploracity.viewmodel.RecoveryEmailSentViewModel
import co.edu.uniquindio.exploracity.viewmodel.RecoveryMessage

/** 6.a · Revisa tu correo, conectada a su ViewModel. [onOpenLink] abre el enlace del correo (en desarrollo, el de prueba). */
@Composable
fun RecoveryEmailSentRoute(
    onBackToLogin: () -> Unit,
    onOpenLink: (token: String) -> Unit,
    viewModel: RecoveryEmailSentViewModel = viewModel(factory = RecoveryEmailSentViewModel.factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnOpenLink by rememberUpdatedState(onOpenLink)
    LaunchedEffect(state.openLink) {
        val token = state.openLink ?: return@LaunchedEffect
        viewModel.onLinkOpened()
        currentOnOpenLink(token)
    }
    val context = LocalContext.current
    val inspection = LocalInspectionMode.current
    // Sin app de correo, el botón no aparece (README 6).
    val hasEmailApp = remember(context) { inspection || context.hasEmailApp() }
    RecoveryEmailSentScreen(
        state = state,
        callbacks = RecoveryEmailSentCallbacks(
            onOpenMail = if (hasEmailApp) { { context.openEmailApp() } } else null,
            onResend = viewModel::onResend,
            onMessageShown = viewModel::onMessageShown,
            onBackToLogin = onBackToLogin,
            onDemoLink = viewModel::onDemoLink,
        ),
    )
}

class RecoveryEmailSentCallbacks(
    /** null si no hay app de correo. */
    val onOpenMail: (() -> Unit)? = {},
    val onResend: () -> Unit = {},
    val onMessageShown: () -> Unit = {},
    val onBackToLogin: () -> Unit = {},
    val onDemoLink: (expired: Boolean) -> Unit = {},
)

/**
 * 6.a · El correo al que salió el enlace, «Abrir mi correo», el reenvío con su cuenta regresiva de 60 s (deshabilitado
 * mientras corre; el lector oye «disponible en 42 segundos») y «Volver a iniciar sesión».
 */
@Composable
fun RecoveryEmailSentScreen(state: RecoveryEmailSentUiState, callbacks: RecoveryEmailSentCallbacks, modifier: Modifier = Modifier) {
    val snackbarHostState = remember { SnackbarHostState() }
    MessageEffect(state.message, snackbarHostState, callbacks)
    val verified = MaterialTheme.exploraColors.status.verified
    val template = stringResource(R.string.sent_body, state.email)
    val body = buildAnnotatedString {
        val start = template.indexOf(state.email)
        if (state.email.isEmpty() || start < 0) {
            append(template)
        } else {
            append(template.substring(0, start))
            withStyle(SpanStyle(fontWeight = FontWeight.W700)) { append(state.email) }
            append(template.substring(start + state.email.length))
        }
    }
    Box(modifier.fillMaxSize()) {
        AccessMessage(
            icon = R.drawable.ic_mark_email_read,
            iconContainer = verified.container,
            iconContent = verified.content,
            title = stringResource(R.string.sent_title),
            body = body,
            top = { if (state.offline) AccessOfflineNotice(stringResource(R.string.sent_offline)) },
        ) {
            callbacks.onOpenMail?.let { open ->
                ExploraButton(stringResource(R.string.sent_open_mail), onClick = open, modifier = Modifier.fillMaxWidth())
            }
            ResendButton(state, callbacks.onResend)
            ExploraButton(stringResource(R.string.back_to_login), onClick = callbacks.onBackToLogin, modifier = Modifier.fillMaxWidth(), style = ExploraButtonStyle.TEXT)
            if (state.demoLinks) DemoLinks(state.demoNoMail, callbacks.onDemoLink)
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing))
    }
}

@Composable
private fun ResendButton(state: RecoveryEmailSentUiState, onResend: () -> Unit) {
    val waiting = state.resendIn > 0
    val text = when {
        state.resending -> stringResource(R.string.sent_resending)
        waiting -> stringResource(R.string.sent_resend_in, state.resendIn / 60, state.resendIn % 60)
        else -> stringResource(R.string.sent_resend)
    }
    val description = if (waiting && !state.resending) pluralStringResource(R.plurals.sent_resend_in_description, state.resendIn, state.resendIn) else null
    ExploraButton(
        text,
        onClick = onResend,
        modifier = Modifier.fillMaxWidth().semantics { if (description != null) contentDescription = description },
        style = ExploraButtonStyle.TEXT,
        enabled = state.canResend || state.resending,
        disabledReason = if (state.offline && !waiting) stringResource(R.string.recover_offline_reason) else null,
        loading = state.resending,
    )
}

/** Solo en compilaciones de desarrollo: lo que haría el enlace del correo. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DemoLinks(noMail: Boolean, onDemoLink: (Boolean) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.sent_demo_title), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.exploraColors.textSecondary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ExploraButton(stringResource(R.string.sent_demo_open), onClick = { onDemoLink(false) }, style = ExploraButtonStyle.SECONDARY)
            ExploraButton(stringResource(R.string.sent_demo_expired), onClick = { onDemoLink(true) }, style = ExploraButtonStyle.SECONDARY)
        }
        if (noMail) Text(stringResource(R.string.sent_demo_no_mail), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.exploraColors.textSecondary)
    }
}

@Composable
private fun MessageEffect(message: RecoveryMessage?, hostState: SnackbarHostState, callbacks: RecoveryEmailSentCallbacks) {
    val currentCallbacks by rememberUpdatedState(callbacks)
    val resent = stringResource(R.string.sent_resent)
    val failed = stringResource(R.string.recover_send_failed)
    val retry = stringResource(R.string.action_retry)
    LaunchedEffect(message) {
        when (message) {
            null -> Unit
            RecoveryMessage.RESENT -> {
                currentCallbacks.onMessageShown()
                hostState.showSnackbar(resent, withDismissAction = true)
            }
            RecoveryMessage.SEND_FAILED -> {
                currentCallbacks.onMessageShown()
                val result = hostState.showSnackbar(failed, actionLabel = retry, withDismissAction = true, duration = SnackbarDuration.Indefinite)
                if (result == SnackbarResult.ActionPerformed) currentCallbacks.onResend()
            }
        }
    }
}

private val previewState = RecoveryEmailSentUiState(email = "ana.rios@correo.com", resendIn = 42)

@Preview(name = "6.a · correo enviado · claro", widthDp = 360, heightDp = 800)
@Composable
private fun SentPreview() {
    ExploraCityTheme(ThemeMode.LIGHT) { RecoveryEmailSentScreen(previewState, RecoveryEmailSentCallbacks()) }
}

@Preview(name = "6.a · reenvío disponible · oscuro", widthDp = 360, heightDp = 800)
@Composable
private fun SentReadyPreview() {
    ExploraCityTheme(ThemeMode.DARK) { RecoveryEmailSentScreen(previewState.copy(resendIn = 0, demoLinks = true), RecoveryEmailSentCallbacks()) }
}

@Preview(name = "6.a · sin conexión ni app de correo", widthDp = 360, heightDp = 800)
@Composable
private fun SentOfflinePreview() {
    ExploraCityTheme(ThemeMode.LIGHT) {
        RecoveryEmailSentScreen(previewState.copy(resendIn = 0, offline = true), RecoveryEmailSentCallbacks(onOpenMail = null))
    }
}
