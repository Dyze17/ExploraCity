package co.edu.uniquindio.exploracity.ui.screens.access

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.edu.uniquindio.exploracity.R
import co.edu.uniquindio.exploracity.data.google.GoogleCredentialResult
import co.edu.uniquindio.exploracity.ui.components.ExploraButton
import co.edu.uniquindio.exploracity.ui.components.ExploraButtonStyle
import co.edu.uniquindio.exploracity.ui.components.ExploraTextField
import co.edu.uniquindio.exploracity.ui.theme.exploraColors
import co.edu.uniquindio.exploracity.viewmodel.GoogleError
import co.edu.uniquindio.exploracity.viewmodel.LinkError
import co.edu.uniquindio.exploracity.viewmodel.LinkPrompt
import kotlinx.coroutines.launch

/**
 * ADR-15 · «Continuar con Google» con el estilo que pide la guía de marca de Google: fondo blanco con borde gris en el
 * tema claro y casi negro en el oscuro, la «G» en sus colores y la tipografía del sistema (Roboto). [request] pide la
 * cuenta a Credential Manager con la Activity de la pantalla; el resultado va a [onResult].
 */
@Composable
internal fun GoogleSignInButton(
    busy: Boolean,
    enabled: Boolean,
    request: suspend (Context) -> GoogleCredentialResult,
    onResult: (GoogleCredentialResult) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Mientras está abierta la hoja de Google, otro toque no abre otra.
    var asking by remember { mutableStateOf(false) }
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val container = if (dark) Color(0xFF131314) else Color.White
    val border = if (dark) Color(0xFF8E918F) else Color(0xFF747775)
    val content = if (dark) Color(0xFFE3E3E3) else Color(0xFF1F1F1F)
    OutlinedButton(
        onClick = {
            if (asking || busy) return@OutlinedButton
            asking = true
            scope.launch {
                val result = try {
                    request(context)
                } finally {
                    asking = false
                }
                onResult(result)
            }
        },
        enabled = enabled,
        shape = CircleShape,
        border = BorderStroke(1.dp, border),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = container,
            disabledContentColor = content.copy(alpha = 0.38f),
        ),
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (busy) {
                CircularProgressIndicator(Modifier.size(18.dp), color = content, strokeWidth = 2.dp)
            } else {
                Image(painterResource(R.drawable.ic_google_g), contentDescription = null, modifier = Modifier.size(20.dp))
            }
            Text(
                stringResource(if (busy) R.string.google_signing_in else R.string.google_continue),
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )
        }
    }
}

/** La línea con «o» entre «Continuar con Google» y el formulario. */
@Composable
internal fun OrDivider(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
        Text(stringResource(R.string.google_or), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.exploraColors.textSecondary)
        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/**
 * B1 · El correo de la cuenta de Google ya tiene una cuenta con contraseña: se escribe esa contraseña para vincular. La
 * contraseña no se guarda; «¿Olvidaste tu contraseña?» lleva a 5 con el correo.
 */
@Composable
internal fun GoogleLinkDialog(
    prompt: LinkPrompt,
    onIdentityChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
    onForgotPassword: (email: String) -> Unit,
) {
    val heading = stringResource(R.string.google_link_title)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val textState = rememberTextFieldState(prompt.password)
    SyncText(textState, prompt.password, onIdentityChange)
    var revealed by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.semantics { paneTitle = heading },
        title = { Text(heading, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.google_link_body, prompt.email),
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                    color = MaterialTheme.exploraColors.textSecondary,
                )
                ExploraTextField(
                    state = textState,
                    label = stringResource(R.string.google_link_identity),
                    errorMessage = when (prompt.error) {
                        LinkError.CREDENTIALS -> stringResource(R.string.google_link_wrong)
                        LinkError.TOO_MANY_ATTEMPTS -> stringResource(R.string.login_too_many_attempts)
                        LinkError.FAILED -> stringResource(R.string.google_link_failed)
                        null -> null
                    },
                    enabled = !prompt.submitting,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    onKeyboardAction = { onSubmit() },
                    leadingIcon = R.drawable.ic_lock,
                    secure = true,
                    revealed = revealed,
                    trailing = { PasswordEye(revealed) { revealed = !revealed } },
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
                ExploraButton(
                    stringResource(R.string.login_forgot_password),
                    onClick = { onForgotPassword(prompt.email) },
                    style = ExploraButtonStyle.TEXT,
                    enabled = !prompt.submitting,
                )
            }
        },
        confirmButton = {
            ExploraButton(
                stringResource(if (prompt.submitting) R.string.google_link_submitting else R.string.google_link_submit),
                onClick = onSubmit,
                enabled = prompt.password.isNotEmpty() || prompt.submitting,
                loading = prompt.submitting,
            )
        },
        dismissButton = {
            ExploraButton(stringResource(R.string.google_link_cancel), onClick = onDismiss, style = ExploraButtonStyle.TEXT, enabled = !prompt.submitting)
        },
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
    )
}

/** El aviso de por qué no se entró con Google: queda hasta que la persona lo cierra, como los del inicio de sesión. */
@Composable
internal fun GoogleErrorEffect(error: GoogleError?, hostState: SnackbarHostState, onDismissed: () -> Unit) {
    val currentOnDismissed by rememberUpdatedState(onDismissed)
    val texts = mapOf(
        GoogleError.NO_ACCOUNT to stringResource(R.string.google_no_account),
        GoogleError.FAILED to stringResource(R.string.google_failed),
        GoogleError.REJECTED to stringResource(R.string.google_rejected),
        GoogleError.UNAVAILABLE to stringResource(R.string.google_unavailable),
        GoogleError.EMAIL_TAKEN to stringResource(R.string.google_email_taken),
    )
    val close = stringResource(R.string.login_close)
    LaunchedEffect(error) {
        val text = texts[error ?: return@LaunchedEffect] ?: return@LaunchedEffect
        val result = hostState.showSnackbar(text, actionLabel = close, duration = SnackbarDuration.Indefinite)
        if (result == SnackbarResult.ActionPerformed || result == SnackbarResult.Dismissed) currentOnDismissed()
    }
}

/** C1 · El registro en modo Google: con qué correo queda la cuenta y cómo volver al registro con contraseña. */
@Composable
internal fun GoogleModeCard(email: String, enabled: Boolean, onUseEmailInstead: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.ic_google_g), contentDescription = null, modifier = Modifier.size(20.dp))
            Text(stringResource(R.string.register_google_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        }
        Text(
            stringResource(R.string.register_google_email, email),
            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
            color = MaterialTheme.exploraColors.textSecondary,
        )
        ExploraButton(stringResource(R.string.register_google_use_email), onClick = onUseEmailInstead, style = ExploraButtonStyle.TEXT, enabled = enabled)
    }
}
