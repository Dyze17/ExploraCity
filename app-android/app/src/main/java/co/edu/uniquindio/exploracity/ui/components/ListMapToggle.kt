package co.edu.uniquindio.exploracity.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.edu.uniquindio.exploracity.R
import co.edu.uniquindio.exploracity.ui.theme.exploraColors

enum class FeedMode { LIST, MAP }

/**
 * Conmutador Lista ⇄ Mapa (radioGroup), siempre visible en 7 y 8. El modo activo muestra icono y texto;
 * el otro solo icono, con su nombre para el lector. Con [expanded] (fuente grande) ambos muestran texto
 * y ocupan todo el ancho.
 */
@Composable
fun ListMapToggle(
    selected: FeedMode,
    onSelect: (FeedMode) -> Unit,
    modifier: Modifier = Modifier,
    expanded: Boolean = false,
) {
    Row(
        modifier
            .then(if (expanded) Modifier.fillMaxWidth() else Modifier)
            .background(MaterialTheme.colorScheme.surfaceContainer, CircleShape)
            .padding(horizontal = 4.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        val itemModifier = if (expanded) Modifier.weight(1f) else Modifier
        ToggleItem(FeedMode.LIST, R.drawable.ic_view_list, stringResource(R.string.feed_mode_list), selected, expanded, onSelect, itemModifier)
        ToggleItem(FeedMode.MAP, R.drawable.ic_map, stringResource(R.string.feed_mode_map), selected, expanded, onSelect, itemModifier)
    }
}

@Composable
private fun ToggleItem(
    mode: FeedMode,
    @DrawableRes icon: Int,
    label: String,
    selected: FeedMode,
    expanded: Boolean,
    onSelect: (FeedMode) -> Unit,
    modifier: Modifier,
) {
    val isSelected = mode == selected
    val content = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.exploraColors.textSecondary
    val showLabel = isSelected || expanded
    // Área táctil de 48 dp con la píldora visible de 40 dp centrada, como en el lienzo.
    Row(
        modifier
            .heightIn(min = 48.dp)
            .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(mode) })
            .then(if (showLabel) Modifier else Modifier.semantics { contentDescription = label })
            .padding(vertical = 4.dp)
            .clip(CircleShape)
            .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .heightIn(min = 40.dp)
            .widthIn(min = 48.dp)
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = content, modifier = Modifier.size(18.dp.scaledWithFont()))
        if (showLabel) {
            Text(label, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W700), color = content)
        }
    }
}
