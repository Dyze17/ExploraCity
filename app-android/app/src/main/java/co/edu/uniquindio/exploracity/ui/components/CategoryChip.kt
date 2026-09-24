package co.edu.uniquindio.exploracity.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.edu.uniquindio.exploracity.R
import co.edu.uniquindio.exploracity.domain.model.Category
import co.edu.uniquindio.exploracity.ui.theme.exploraColors

/**
 * Filtro de categoría. Seleccionado = color de la categoría + borde + check que sustituye al icono,
 * para que el estado no dependa solo del color. Área táctil de 48 dp.
 */
@Composable
fun CategoryChip(
    category: Category,
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = MaterialTheme.shapes.small
    val colors = category.colors
    val container = if (selected) colors.container else MaterialTheme.colorScheme.surface
    val content = if (selected) colors.content else MaterialTheme.exploraColors.textSecondary
    val border = if (selected) colors.marker else MaterialTheme.colorScheme.outline
    val state = stringResource(if (selected) R.string.state_selected else R.string.state_not_selected)

    Row(
        modifier
            .minimumInteractiveComponentSize()
            .clip(shape)
            .toggleable(value = selected, role = Role.Checkbox, onValueChange = onSelectedChange)
            .semantics { stateDescription = state }
            .heightIn(min = 36.dp)
            .background(container, shape)
            .border(1.dp, border, shape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(if (selected) R.drawable.ic_check else category.iconRes),
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(20.dp.scaledWithFont()),
        )
        Text(
            stringResource(category.labelRes),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (selected) FontWeight.W700 else FontWeight.W600),
            color = content,
        )
    }
}

/**
 * Filtro activo que no es una categoría («Cercanos», «Solo verificados»), en la fila de chips del feed.
 * Solo aparece activo (relleno tertiaryContainer + borde, como en 10.a); tocarlo lo quita.
 */
@Composable
fun ActiveFilterChip(
    label: String,
    @DrawableRes icon: Int,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = MaterialTheme.shapes.small
    val scheme = MaterialTheme.colorScheme
    val state = stringResource(R.string.state_selected)

    Row(
        modifier
            .minimumInteractiveComponentSize()
            .clip(shape)
            .toggleable(value = true, role = Role.Checkbox, onValueChange = { onRemove() })
            .semantics { stateDescription = state }
            .heightIn(min = 36.dp)
            .background(scheme.tertiaryContainer, shape)
            .border(1.dp, scheme.tertiary, shape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = scheme.onTertiaryContainer, modifier = Modifier.size(20.dp.scaledWithFont()))
        Text(label, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.W700), color = scheme.onTertiaryContainer)
    }
}
