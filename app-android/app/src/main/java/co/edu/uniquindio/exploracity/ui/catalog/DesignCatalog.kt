package co.edu.uniquindio.exploracity.ui.catalog

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import co.edu.uniquindio.exploracity.R
import co.edu.uniquindio.exploracity.domain.model.Category
import co.edu.uniquindio.exploracity.domain.model.PublicationStatus
import co.edu.uniquindio.exploracity.ui.components.BadgeSize
import co.edu.uniquindio.exploracity.ui.components.CategoryChip
import co.edu.uniquindio.exploracity.ui.components.CategoryTag
import co.edu.uniquindio.exploracity.ui.components.DuplicateFlag
import co.edu.uniquindio.exploracity.ui.components.ExploraButton
import co.edu.uniquindio.exploracity.ui.components.ExploraButtonStyle
import co.edu.uniquindio.exploracity.ui.components.ExploraNavigationBar
import co.edu.uniquindio.exploracity.ui.components.NavigationBarItem
import co.edu.uniquindio.exploracity.ui.components.ExploraTextField
import co.edu.uniquindio.exploracity.ui.components.POICard
import co.edu.uniquindio.exploracity.ui.components.PublishFab
import co.edu.uniquindio.exploracity.ui.components.StatusBadge
import co.edu.uniquindio.exploracity.ui.components.colors
import co.edu.uniquindio.exploracity.ui.components.iconRes
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
        verticalArrangement = Arrangement.spacedBy(ExploraSpacing.BetweenSections),
    ) {
        Section("POICard") {
            POICard(
                title = "Café Las Acacias",
                category = Category.GASTRONOMY,
                status = PublicationStatus.VERIFIED,
                distance = "1,2 km",
                votes = 48,
                comments = 12,
                onClick = {},
                modifier = Modifier.fillMaxWidth(),
            )
            POICard(
                title = "Sendero Quebrada La Vieja",
                category = Category.NATURE,
                status = PublicationStatus.PENDING,
                distance = "3,4 km",
                votes = 1,
                comments = 0,
                onClick = {},
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Section("Estados") {
            PublicationStatus.entries.forEach { StatusBadge(it) }
            StatusBadge(PublicationStatus.REJECTED, onClick = {})
            DuplicateFlag()
            StatusBadge(PublicationStatus.PENDING, size = BadgeSize.SMALL)
            DuplicateFlag(size = BadgeSize.SMALL)
        }
        Section("Categorías · filtro, etiqueta y marcador") {
            val selected = remember { mutableStateListOf(Category.GASTRONOMY, Category.NATURE) }
            Category.entries.forEach { category ->
                CategoryChip(
                    category = category,
                    selected = category in selected,
                    onSelectedChange = { if (it) selected += category else selected -= category },
                )
            }
            Category.entries.forEach { CategoryTag(it) }
            Category.entries.forEach { Marker(it.iconRes, it.colors.marker, it.colors.onMarker) }
        }
        Section("Botones") {
            ExploraButton("Enviar a verificación", onClick = {}, modifier = Modifier.fillMaxWidth())
            ExploraButton(
                "Guardar borrador",
                onClick = {},
                modifier = Modifier.fillMaxWidth(),
                style = ExploraButtonStyle.SECONDARY,
            )
            ExploraButton("Omitir por ahora", onClick = {}, modifier = Modifier.fillMaxWidth(), style = ExploraButtonStyle.TEXT)
            ExploraButton(
                "Eliminar publicación",
                onClick = {},
                modifier = Modifier.fillMaxWidth(),
                style = ExploraButtonStyle.DESTRUCTIVE,
                icon = R.drawable.ic_delete,
            )
            ExploraButton(
                "Continuar",
                onClick = {},
                modifier = Modifier.fillMaxWidth(),
                enabled = false,
                disabledReason = "completa el título",
            )
            ExploraButton("Entrando…", onClick = {}, modifier = Modifier.fillMaxWidth(), loading = true)
        }
        Section("FAB") {
            PublishFab(onClick = {})
            PublishFab(onClick = {}, expanded = true)
        }
        Section("Barra inferior · Usuario y Moderador") {
            val explore = NavigationBarItem(R.drawable.ic_explore, "Explorar")
            val publish = NavigationBarItem(R.drawable.ic_add_location_alt, "Publicar")
            val notifications = NavigationBarItem(R.drawable.ic_notifications, "Avisos", 3, "Avisos, 3 sin leer")
            val profile = NavigationBarItem(R.drawable.ic_person, "Perfil")
            val moderation = NavigationBarItem(R.drawable.ic_shield_person, "Moderación", 7, "Moderación, 7 por revisar")
            ExploraNavigationBar(listOf(explore, publish, notifications, profile), selectedIndex = 0, onSelect = {})
            ExploraNavigationBar(listOf(explore, publish, moderation, notifications, profile), selectedIndex = 2, onSelect = {})
        }
        Section("Campos") {
            ExploraTextField(
                state = rememberTextFieldState("Café Las Acacias"),
                label = "Título del lugar",
                supportingText = "Entre 5 y 60 caracteres · 16/60",
                modifier = Modifier.fillMaxWidth(),
            )
            ExploraTextField(
                state = rememberTextFieldState(),
                label = "Descripción",
                placeholder = "Ej. Tostión propia y patio interior",
                supportingText = "Cuéntanos qué hace especial el lugar · mínimo 30 caracteres",
                singleLine = false,
                modifier = Modifier.fillMaxWidth(),
            )
            ExploraTextField(
                state = rememberTextFieldState(),
                label = "Horario de atención",
                placeholder = "Ej. Lunes a sábado, 8:00 a 18:00",
                errorMessage = "Falta el horario. Escribe los días y las horas de atención.",
                modifier = Modifier.fillMaxWidth(),
            )
            ExploraTextField(
                state = rememberTextFieldState("Carrera 14 # 20-35"),
                label = "Ubicación",
                enabled = false,
                disabledReason = "Se activa en el paso 3",
                modifier = Modifier.fillMaxWidth(),
            )
        }
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

/** Gota del mapa: 40 dp, esquina inferior en punta tras girar -45°. El componente real llega con el mapa (8). */
@Composable
private fun Marker(@DrawableRes icon: Int, color: Color, onColor: Color) {
    Box(
        Modifier
            .size(40.dp)
            .rotate(-45f)
            .background(color, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 4.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = onColor, modifier = Modifier.rotate(45f).size(20.dp))
    }
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

@Preview(name = "Claro", widthDp = 360, heightDp = 3000)
@Composable
private fun DesignCatalogLightPreview() {
    ExploraCityTheme(ThemeMode.LIGHT) { DesignCatalog() }
}

@Preview(name = "Oscuro", widthDp = 360, heightDp = 3000)
@Composable
private fun DesignCatalogDarkPreview() {
    ExploraCityTheme(ThemeMode.DARK) { DesignCatalog() }
}

@Preview(name = "Claro · fuente 200 %", widthDp = 360, heightDp = 5000, fontScale = 2f)
@Composable
private fun DesignCatalogLargeFontPreview() {
    ExploraCityTheme(ThemeMode.LIGHT) { DesignCatalog() }
}
