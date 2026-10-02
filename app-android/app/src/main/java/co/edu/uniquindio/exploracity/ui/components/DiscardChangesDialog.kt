package co.edu.uniquindio.exploracity.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp
import co.edu.uniquindio.exploracity.R
import co.edu.uniquindio.exploracity.ui.theme.exploraColors

/**
 * «¿Descartar los cambios?» al salir de una edición con cambios (23, 28). [body] dice qué queda como estaba. El foco
 * inicial va a «Seguir editando»: descartar es destructivo y nunca la acción por defecto.
 */
@Composable
fun DiscardChangesDialog(body: String, onDiscard: () -> Unit, onKeepEditing: () -> Unit) {
    val heading = stringResource(R.string.edit_discard_title)
    val keepFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { keepFocus.requestFocus() }
    AlertDialog(
        onDismissRequest = onKeepEditing,
        modifier = Modifier.semantics { paneTitle = heading },
        title = { Text(heading, style = MaterialTheme.typography.titleLarge) },
        text = {
            Text(body, style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp), color = MaterialTheme.exploraColors.textSecondary)
        },
        confirmButton = {
            ExploraButton(stringResource(R.string.edit_discard_confirm), onClick = onDiscard, style = ExploraButtonStyle.DESTRUCTIVE)
        },
        dismissButton = {
            ExploraButton(
                stringResource(R.string.edit_discard_keep),
                onClick = onKeepEditing,
                modifier = Modifier.initialFocus(keepFocus),
                style = ExploraButtonStyle.TEXT,
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
    )
}
