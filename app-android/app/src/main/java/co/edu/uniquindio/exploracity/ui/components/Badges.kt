package co.edu.uniquindio.exploracity.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import co.edu.uniquindio.exploracity.R
import co.edu.uniquindio.exploracity.domain.model.Category
import co.edu.uniquindio.exploracity.domain.model.PublicationStatus
import co.edu.uniquindio.exploracity.ui.theme.exploraColors

/** REGULAR: badge suelto (detalle, listas propias). SMALL: dentro de POICard y filas compactas. */
enum class BadgeSize { REGULAR, SMALL }

private class BadgeMetrics(val padding: PaddingValues, val gap: Dp, val icon: Dp)

private fun BadgeSize.metrics() = when (this) {
    BadgeSize.REGULAR -> BadgeMetrics(PaddingValues(horizontal = 10.dp, vertical = 6.dp), gap = 6.dp, icon = 16.dp)
    BadgeSize.SMALL -> BadgeMetrics(PaddingValues(horizontal = 8.dp, vertical = 4.dp), gap = 4.dp, icon = 14.dp)
}

@Composable
private fun BadgeSize.textStyle(weight: FontWeight): TextStyle = when (this) {
    BadgeSize.REGULAR -> MaterialTheme.typography.labelMedium
    BadgeSize.SMALL -> MaterialTheme.typography.labelSmall
}.copy(fontWeight = weight)

@Composable
private fun IconLabel(
    label: String,
    @DrawableRes icon: Int,
    container: Color,
    content: Color,
    size: BadgeSize,
    modifier: Modifier = Modifier,
    weight: FontWeight = FontWeight.W700,
    dashedBorderColor: Color? = null,
) {
    val metrics = size.metrics()
    Row(
        modifier
            .background(container, MaterialTheme.shapes.small)
            // Después del fondo: si fuera antes, el fondo lo taparía.
            .then(if (dashedBorderColor != null) Modifier.dashedBorder(1.dp, dashedBorderColor, 8.dp) else Modifier)
            .padding(metrics.padding),
        horizontalArrangement = Arrangement.spacedBy(metrics.gap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = content, modifier = Modifier.size(metrics.icon.scaledWithFont()))
        Text(label, style = size.textStyle(weight), color = content)
    }
}

/**
 * Estado de una publicación: color + icono + texto. Solo «Rechazada» es tocable (abre el motivo, 24):
 * pasa [onClick] únicamente en ese caso. Sin [onClick] no es focusable por separado del ítem que lo contiene.
 */
@Composable
fun StatusBadge(
    status: PublicationStatus,
    modifier: Modifier = Modifier,
    size: BadgeSize = BadgeSize.REGULAR,
    label: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val text = label ?: stringResource(
        if (onClick != null && status == PublicationStatus.REJECTED) R.string.status_rejected_action else status.labelRes,
    )
    val clickModifier = if (onClick != null) {
        Modifier
            .minimumInteractiveComponentSize()
            .clip(MaterialTheme.shapes.small)
            .clickable(role = Role.Button, onClick = onClick)
    } else {
        Modifier
    }
    IconLabel(text, status.iconRes, status.colors.container, status.colors.content, size, modifier.then(clickModifier))
}

/** Marca «Posible duplicado»: siempre después del StatusBadge, con borde discontinuo. */
@Composable
fun DuplicateFlag(modifier: Modifier = Modifier, size: BadgeSize = BadgeSize.REGULAR) {
    val colors = MaterialTheme.exploraColors.duplicateFlag
    IconLabel(
        label = stringResource(R.string.duplicate_flag),
        icon = R.drawable.ic_join_inner,
        container = colors.container,
        content = colors.content,
        size = size,
        modifier = modifier,
        dashedBorderColor = colors.border,
    )
}

/** Categoría como etiqueta informativa (no seleccionable), p. ej. dentro de POICard. */
@Composable
fun CategoryTag(category: Category, modifier: Modifier = Modifier, size: BadgeSize = BadgeSize.SMALL) {
    IconLabel(
        label = stringResource(category.labelRes),
        icon = category.iconRes,
        container = category.colors.container,
        content = category.colors.content,
        size = size,
        modifier = modifier,
        weight = FontWeight.W600,
    )
}

/** 12.a «Guardado»: el lugar se puede abrir sin conexión (su detalle está guardado en el teléfono). */
@Composable
fun SavedBadge(modifier: Modifier = Modifier, size: BadgeSize = BadgeSize.SMALL) {
    IconLabel(
        label = stringResource(R.string.saved_badge),
        icon = R.drawable.ic_download_done,
        container = MaterialTheme.colorScheme.surfaceContainerHighest,
        content = MaterialTheme.exploraColors.iconSecondary,
        size = size,
        modifier = modifier,
        weight = FontWeight.W700,
    )
}
