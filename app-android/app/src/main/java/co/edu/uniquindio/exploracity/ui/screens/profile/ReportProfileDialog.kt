package co.edu.uniquindio.exploracity.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.edu.uniquindio.exploracity.R
import co.edu.uniquindio.exploracity.domain.model.ReportReason
import co.edu.uniquindio.exploracity.ui.components.ExploraButton
import co.edu.uniquindio.exploracity.ui.components.ExploraButtonStyle
import co.edu.uniquindio.exploracity.ui.theme.exploraColors
import co.edu.uniquindio.exploracity.viewmodel.ReportDialogState
import co.edu.uniquindio.exploracity.viewmodel.ReportError

private val ReportReason.labelRes: Int
    get() = when (this) {
        ReportReason.IMPERSONATION -> R.string.report_reason_impersonation
        ReportReason.INAPPROPRIATE_CONTENT -> R.string.report_reason_inappropriate
        ReportReason.SPAM -> R.string.report_reason_spam
    }

/**
 * 31A · Reportar perfil. Diálogo destructivo del sistema de diseño: el foco inicial va a «Cancelar» y «Reportar» nunca
 * es la acción por defecto. El motivo es obligatorio (radios de 48 dp) y ninguno viene marcado: el lienzo muestra uno,
 * pero el README pide que «Reportar» se habilite al elegirlo. Si falla, el aviso queda dentro del diálogo.
 */
@Composable
fun ReportProfileDialog(
    name: String,
    state: ReportDialogState,
    onReasonChange: (ReportReason) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val explora = MaterialTheme.exploraColors
    val title = stringResource(R.string.report_title, name)
    val cancelFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { cancelFocus.requestFocus() }
    // Con fuente grande el texto se desplaza y el aviso de error queda al final, fuera de la vista (S20+ al 200 %).
    val scroll = rememberScrollState()
    LaunchedEffect(state.error) { if (state.error != null) scroll.animateScrollTo(scroll.maxValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.semantics { paneTitle = title },
        icon = {
            Box(Modifier.size(44.dp).background(scheme.errorContainer, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_flag), null, tint = scheme.onErrorContainer, modifier = Modifier.size(24.dp))
            }
        },
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(Modifier.verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.report_body, name),
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                    color = explora.textSecondary,
                )
                val reasons = stringResource(R.string.report_reasons)
                Column(Modifier.selectableGroup().semantics { contentDescription = reasons }) {
                    ReportReason.entries.forEach { reason ->
                        ReasonRow(stringResource(reason.labelRes), selected = state.reason == reason, enabled = !state.sending) { onReasonChange(reason) }
                    }
                }
                state.error?.let { error ->
                    Text(
                        stringResource(if (error == ReportError.OFFLINE) R.string.report_offline else R.string.report_failed),
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.W600),
                        color = scheme.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
        },
        confirmButton = {
            ExploraButton(
                stringResource(R.string.report_confirm),
                onClick = onConfirm,
                style = ExploraButtonStyle.DESTRUCTIVE,
                enabled = state.reason != null,
                disabledReason = stringResource(R.string.report_choose_reason),
                loading = state.sending,
            )
        },
        dismissButton = {
            ExploraButton(
                stringResource(R.string.report_cancel),
                onClick = onDismiss,
                modifier = Modifier.focusRequester(cancelFocus),
                style = ExploraButtonStyle.TEXT,
                enabled = !state.sending,
            )
        },
        containerColor = scheme.surface,
        titleContentColor = scheme.onSurface,
    )
}

@Composable
private fun ReasonRow(label: String, selected: Boolean, enabled: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect)
            .padding(end = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            enabled = enabled,
            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary, unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant),
        )
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}
