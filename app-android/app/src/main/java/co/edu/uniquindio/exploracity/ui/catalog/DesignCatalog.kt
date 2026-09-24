package co.edu.uniquindio.exploracity.ui.catalog

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import co.edu.uniquindio.exploracity.R
import co.edu.uniquindio.exploracity.domain.model.Category
import co.edu.uniquindio.exploracity.domain.model.PublicationStatus
import co.edu.uniquindio.exploracity.ui.components.colors
import co.edu.uniquindio.exploracity.ui.components.iconRes
import co.edu.uniquindio.exploracity.ui.components.labelRes
import co.edu.uniquindio.exploracity.ui.theme.ExploraCityTheme
import co.edu.uniquindio.exploracity.ui.theme.ExploraSpacing
import co.edu.uniquindio.exploracity.ui.theme.ThemeMode
import co.edu.uniquindio.exploracity.ui.theme.exploraColors

/** Muestrario del sistema de diseño para comparar con «Sistema de Diseño.dc.html». Solo para desarrollo. */
@Composable
internal fun DesignCatalog(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val explora = MaterialTheme.exploraColors
    Column(
        modifier
            .fillMaxSize()
            .background(scheme.surface)
            .verticalScroll(rememberScrollState())
            .padding(ExploraSpacing.ScreenMargin),
        verticalArrangement = Arrangement.spacedBy(ExploraSpacing.BetweenBlocks),
    ) {
        Section("Roles Material 3") {
            Swatch("primary", scheme.primary, scheme.onPrimary)
            Swatch("primaryContainer", scheme.primaryContainer, scheme.onPrimaryContainer)
            Swatch("secondary", scheme.secondary, scheme.onSecondary)
            Swatch("secondaryContainer", scheme.secondaryContainer, scheme.onSecondaryContainer)
            Swatch("tertiary", scheme.tertiary, scheme.onTertiary)
            Swatch("tertiaryContainer", scheme.tertiaryContainer, scheme.onTertiaryContainer)
            Swatch("error", scheme.error, scheme.onError)
            Swatch("errorContainer", scheme.errorContainer, scheme.onErrorContainer)
            Swatch("inverseSurface", scheme.inverseSurface, scheme.inverseOnSurface)
        }
        Section("Superficies") {
            Swatch("surface", scheme.surface, scheme.onSurface, border = scheme.outlineVariant)
            Swatch("lowest · tarjeta", scheme.surfaceContainerLowest, scheme.onSurface, border = scheme.outlineVariant)
            Swatch("low", scheme.surfaceContainerLow, scheme.onSurface)
            Swatch("container", scheme.surfaceContainer, scheme.onSurfaceVariant)
            Swatch("high", scheme.surfaceContainerHigh, scheme.onSurfaceVariant)
            Swatch("highest", scheme.surfaceContainerHighest, scheme.onSurfaceVariant)
            Swatch("outline", scheme.outline, scheme.surface)
        }
        Section("Estados") {
            PublicationStatus.entries.forEach { status ->
                Badge(stringResource(status.labelRes), status.iconRes, status.colors.container, status.colors.content)
            }
            Badge(
                stringResource(R.string.duplicate_flag),
                R.drawable.ic_join_inner,
                explora.duplicateFlag.container,
                explora.duplicateFlag.content,
                Modifier.dashedBorder(1.dp, explora.duplicateFlag.border, 8.dp),
            )
        }
        Section("Categorías · marcador y chip") {
            Category.entries.forEach { category ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Marker(category.iconRes, category.colors.marker, category.colors.onMarker)
                    Chip(stringResource(category.labelRes), category.iconRes, category.colors.container, category.colors.content)
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(ExploraSpacing.BetweenChips)) {
            Title("Tipografía")
            with(MaterialTheme.typography) {
                listOf(
                    "displayLarge" to displayLarge, "displayMedium" to displayMedium,
                    "headlineLarge" to headlineLarge, "headlineMedium" to headlineMedium,
                    "titleLarge" to titleLarge, "titleMedium" to titleMedium, "titleSmall" to titleSmall,
                    "bodyLarge" to bodyLarge, "bodyMedium" to bodyMedium, "bodySmall" to bodySmall,
                    "labelLarge" to labelLarge, "labelMedium" to labelMedium, "labelSmall" to labelSmall,
                ).forEach { (name, style) -> TypeSample(name, style) }
            }
            Text(
                "Texto secundario · iconos de apoyo · placeholder",
                style = MaterialTheme.typography.bodyMedium,
                color = explora.textSecondary,
            )
        }
        Section("Formas") {
            with(MaterialTheme.shapes) {
                listOf("4 xs" to extraSmall, "8 chips" to small, "12 campos" to medium, "16 tarjetas" to large, "28 hojas" to extraLarge)
                    .forEach { (name, shape) -> ShapeSample(name, shape) }
            }
        }
        val icons = remember {
            R.drawable::class.java.fields
                .filter { it.name.startsWith("ic_") }
                .sortedBy { it.name }
                .map { it.getInt(null) }
        }
        Section("Iconos · Material Symbols Rounded (${icons.size})") {
            icons.forEach { icon ->
                Icon(painterResource(icon), contentDescription = null, tint = explora.iconSecondary)
            }
        }
    }
}

@Composable
private fun Title(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(ExploraSpacing.BetweenChips)) {
        Title(title)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(ExploraSpacing.BetweenChips),
            verticalArrangement = Arrangement.spacedBy(ExploraSpacing.BetweenChips),
        ) { content() }
    }
}

@Composable
private fun Swatch(name: String, container: Color, content: Color, border: Color? = null) {
    val shape = MaterialTheme.shapes.small
    Box(
        Modifier
            .background(container, shape)
            .then(if (border != null) Modifier.border(1.dp, border, shape) else Modifier)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(name, style = MaterialTheme.typography.labelLarge, color = content)
    }
}

@Composable
private fun Badge(label: String, @DrawableRes icon: Int, container: Color, content: Color, modifier: Modifier = Modifier) {
    Row(
        modifier
            .background(container, MaterialTheme.shapes.small)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = content, modifier = Modifier.size(16.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = content)
    }
}

@Composable
private fun Chip(label: String, @DrawableRes icon: Int, container: Color, content: Color) {
    Row(
        Modifier
            .background(container, MaterialTheme.shapes.small)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = content)
    }
}

/** Gota del mapa: 40 dp, esquina inferior en punta tras girar -45°. */
@Composable
private fun Marker(@DrawableRes icon: Int, color: Color, onColor: Color) {
    Box(
        Modifier
            .padding(end = ExploraSpacing.BetweenChips)
            .size(40.dp)
            .rotate(-45f)
            .background(color, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 4.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = onColor, modifier = Modifier.rotate(45f).size(20.dp))
    }
}

private fun Modifier.dashedBorder(width: Dp, color: Color, radius: Dp) = drawBehind {
    val stroke = width.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(stroke / 2, stroke / 2),
        size = Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius(radius.toPx()),
        style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
    )
}

@Composable
private fun TypeSample(name: String, style: TextStyle) {
    Text("$name · ${style.fontSize.value.toInt()}/${style.lineHeight.value.toInt()}", style = style, color = MaterialTheme.colorScheme.onSurface)
}

@Composable
private fun ShapeSample(name: String, shape: Shape) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(64.dp)) {
        Box(Modifier.size(56.dp).background(MaterialTheme.colorScheme.primaryContainer, shape))
        Text(name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Preview(name = "Claro", widthDp = 360, heightDp = 2200)
@Composable
private fun DesignCatalogLightPreview() {
    ExploraCityTheme(ThemeMode.LIGHT) { DesignCatalog() }
}

@Preview(name = "Oscuro", widthDp = 360, heightDp = 2200)
@Composable
private fun DesignCatalogDarkPreview() {
    ExploraCityTheme(ThemeMode.DARK) { DesignCatalog() }
}

@Preview(name = "Claro · fuente 200 %", widthDp = 360, heightDp = 3200, fontScale = 2f)
@Composable
private fun DesignCatalogLargeFontPreview() {
    ExploraCityTheme(ThemeMode.LIGHT) { DesignCatalog() }
}
