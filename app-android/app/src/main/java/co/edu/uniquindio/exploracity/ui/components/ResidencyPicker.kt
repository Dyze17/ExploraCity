package co.edu.uniquindio.exploracity.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import co.edu.uniquindio.exploracity.R
import co.edu.uniquindio.exploracity.domain.model.Residency
import co.edu.uniquindio.exploracity.ui.theme.FontScaleThresholds

/**
 * «¿Cómo te presentas?» (4 y 28): De visita o Residente, como en el perfil (26 y 31). Con fuente grande, una debajo de
 * otra.
 */
@Composable
fun ResidencyPicker(selected: Residency, onSelect: (Residency) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val stacked = LocalDensity.current.fontScale > FontScaleThresholds.StackRows
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.profile_residency_title),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { heading() },
        )
        val options: @Composable (Modifier) -> Unit = { itemModifier ->
            OptionButton(
                stringResource(R.string.residency_visitor),
                R.drawable.ic_luggage,
                selected == Residency.VISITOR,
                { if (enabled) onSelect(Residency.VISITOR) },
                itemModifier,
            )
            OptionButton(
                stringResource(R.string.residency_resident),
                R.drawable.ic_home_pin,
                selected == Residency.RESIDENT,
                { if (enabled) onSelect(Residency.RESIDENT) },
                itemModifier,
            )
        }
        if (stacked) {
            Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) { options(Modifier.fillMaxWidth()) }
        } else {
            Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { options(Modifier.weight(1f)) }
        }
    }
}
