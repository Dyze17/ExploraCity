package co.edu.uniquindio.exploracity.ui.screens.publication

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.edu.uniquindio.exploracity.R
import co.edu.uniquindio.exploracity.ui.components.ExploraButton
import co.edu.uniquindio.exploracity.ui.components.ExploraButtonStyle
import co.edu.uniquindio.exploracity.ui.components.initialFocus
import co.edu.uniquindio.exploracity.ui.theme.exploraColors
import co.edu.uniquindio.exploracity.viewmodel.DeleteDialogState
import co.edu.uniquindio.exploracity.viewmodel.DeleteError

/**
 * 23 · «¿Eliminar…?» con las consecuencias enumeradas. El foco inicial va a «Cancelar»; «Sí, eliminar» nunca es la
 * acción por defecto ni responde a Enter. Una publicación rechazada nunca fue pública: solo se pierden ella y sus
 * fotos. Los comentarios, votos y puntos del lienzo 23.a se agregarán al hacer 23, donde sí aplican. Si falla, el
 * aviso queda dentro del diálogo.
 */
@Composable
fun DeletePublicationDialog(
    title: String,
    photos: Int,
    state: DeleteDialogState,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val heading = stringResource(R.string.delete_publication_title, title)
    val cancelFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { cancelFocus.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.semantics { paneTitle = heading },
        icon = {
            Box(Modifier.size(44.dp).background(scheme.errorContainer, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_delete_forever), null, tint = scheme.onErrorContainer, modifier = Modifier.size(24.dp))
            }
        },
        title = { Text(heading, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    pluralStringResource(R.plurals.delete_publication_body, photos, photos),
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                    color = MaterialTheme.exploraColors.textSecondary,
                )
                state.error?.let { error ->
                    Text(
                        stringResource(if (error == DeleteError.OFFLINE) R.string.delete_publication_offline else R.string.delete_publication_failed),
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.W600),
                        color = scheme.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
        },
        confirmButton = {
            ExploraButton(
                stringResource(R.string.delete_publication_confirm),
                onClick = onConfirm,
                style = ExploraButtonStyle.DESTRUCTIVE,
                loading = state.deleting,
            )
        },
        dismissButton = {
            ExploraButton(
                stringResource(R.string.delete_publication_cancel),
                onClick = onDismiss,
                modifier = Modifier.initialFocus(cancelFocus),
                style = ExploraButtonStyle.TEXT,
                enabled = !state.deleting,
            )
        },
        containerColor = scheme.surface,
        titleContentColor = scheme.onSurface,
    )
}
