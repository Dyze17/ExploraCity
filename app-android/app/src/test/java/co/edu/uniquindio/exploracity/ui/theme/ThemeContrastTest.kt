package co.edu.uniquindio.exploracity.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.fail
import org.junit.Test

/**
 * Regla global del diseño: contraste ≥ 4,5:1 en ambos temas para texto,
 * y ≥ 3:1 para contenido no textual (iconos, bordes de campos y chips).
 */
class ThemeContrastTest {

    private data class ColorPair(val name: String, val background: Color, val foreground: Color)

    @Test
    fun `texto en tema claro cumple 4,5 a 1`() = assertContrast(textPairs(LightColorScheme, LightExploraColors), 4.5)

    @Test
    fun `texto en tema oscuro cumple 4,5 a 1`() = assertContrast(textPairs(DarkColorScheme, DarkExploraColors), 4.5)

    @Test
    fun `contenido no textual en tema claro cumple 3 a 1`() =
        assertContrast(nonTextPairs(LightColorScheme, LightExploraColors), 3.0)

    @Test
    fun `contenido no textual en tema oscuro cumple 3 a 1`() =
        assertContrast(nonTextPairs(DarkColorScheme, DarkExploraColors), 3.0)

    private fun textPairs(s: ColorScheme, e: ExploraColors) = buildList {
        add(ColorPair("primary", s.primary, s.onPrimary))
        add(ColorPair("primaryContainer", s.primaryContainer, s.onPrimaryContainer))
        add(ColorPair("secondaryContainer", s.secondaryContainer, s.onSecondaryContainer))
        add(ColorPair("tertiary", s.tertiary, s.onTertiary))
        add(ColorPair("tertiaryContainer", s.tertiaryContainer, s.onTertiaryContainer))
        add(ColorPair("error", s.error, s.onError))
        add(ColorPair("errorContainer", s.errorContainer, s.onErrorContainer))
        add(ColorPair("background", s.background, s.onBackground))
        add(ColorPair("snackbar", s.inverseSurface, s.inverseOnSurface))
        add(ColorPair("acción del snackbar", s.inverseSurface, s.inversePrimary))

        val surfaces = mapOf(
            "surface" to s.surface,
            "surfaceContainerLowest" to s.surfaceContainerLowest,
            "surfaceContainerLow" to s.surfaceContainerLow,
            "surfaceContainer" to s.surfaceContainer,
            "surfaceContainerHigh" to s.surfaceContainerHigh,
            "surfaceContainerHighest" to s.surfaceContainerHighest,
        )
        surfaces.forEach { (name, surface) ->
            add(ColorPair("$name / onSurface", surface, s.onSurface))
            add(ColorPair("$name / onSurfaceVariant", surface, s.onSurfaceVariant))
            add(ColorPair("$name / textSecondary", surface, e.textSecondary))
            add(ColorPair("$name / iconSecondary", surface, e.iconSecondary))
            add(ColorPair("$name / textPlaceholder", surface, e.textPlaceholder))
        }
        // Texto de acento (botones de texto, enlaces, errores) sobre los fondos donde aparece.
        listOf("surface" to s.surface, "tarjeta" to s.surfaceContainerLowest, "surfaceContainer" to s.surfaceContainer)
            .forEach { (name, surface) ->
                add(ColorPair("$name / primary", surface, s.primary))
                add(ColorPair("$name / tertiary", surface, s.tertiary))
                add(ColorPair("$name / error", surface, s.error))
            }

        add(ColorPair("surface / texto de acento", s.surface, e.onSurfaceAccent))
        add(ColorPair("tarjeta / texto de acento", s.surfaceContainerLowest, e.onSurfaceAccent))
        add(ColorPair("botón deshabilitado", s.surfaceContainerHighest, e.textPlaceholder))
        add(ColorPair("pestaña seleccionada / indicador", s.primaryContainer, e.onPrimaryContainerAccent))
        add(ColorPair("pestaña seleccionada / barra", s.surfaceContainer, e.onPrimaryContainerAccent))
        add(ColorPair("badge de pestaña", s.error, s.onError))
        add(ColorPair("campo con error / texto", e.errorFieldContainer, s.onSurface))
        add(ColorPair("campo con error / placeholder", e.errorFieldContainer, e.textPlaceholder))
        add(ColorPair("campo deshabilitado / texto", s.surfaceContainerHigh, e.iconSecondary))
        add(ColorPair("warning", e.warning.container, e.warning.content))
        add(ColorPair("estado pendiente", e.status.pending.container, e.status.pending.content))
        add(ColorPair("estado verificada", e.status.verified.container, e.status.verified.content))
        add(ColorPair("estado rechazada", e.status.rejected.container, e.status.rejected.content))
        add(ColorPair("estado resuelta", e.status.finalized.container, e.status.finalized.content))
        add(ColorPair("posible duplicado", e.duplicateFlag.container, e.duplicateFlag.content))
        add(ColorPair("cluster del mapa", e.mapCluster.container, e.mapCluster.content))
        categories(e).forEach { (name, c) -> add(ColorPair("chip $name", c.container, c.content)) }
    }

    private fun nonTextPairs(s: ColorScheme, e: ExploraColors) = buildList {
        // secondary con blanco da 4,23:1 en claro: solo se usa detrás de iconos.
        add(ColorPair("secondary / icono", s.secondary, s.onSecondary))
        add(ColorPair("outline / surface", s.surface, s.outline))
        add(ColorPair("outline / tarjeta", s.surfaceContainerLowest, s.outline))
        add(ColorPair("borde de posible duplicado", e.duplicateFlag.container, e.duplicateFlag.border))
        categories(e).forEach { (name, c) -> add(ColorPair("marcador $name", c.marker, c.onMarker)) }
    }

    private fun categories(e: ExploraColors) = with(e.category) {
        listOf(
            "gastronomía" to gastronomy,
            "cultura" to culture,
            "naturaleza" to nature,
            "entretenimiento" to entertainment,
            "historia" to history,
        )
    }

    private fun assertContrast(pairs: List<ColorPair>, minimum: Double) {
        val failures = pairs
            .map { it to contrast(it.background, it.foreground) }
            .filter { (_, ratio) -> ratio < minimum }
        if (failures.isNotEmpty()) {
            fail(
                failures.joinToString(prefix = "Contraste menor que $minimum:1\n", separator = "\n") { (pair, ratio) ->
                    "  ${pair.name}: ${"%.2f".format(ratio)}:1"
                },
            )
        }
    }

    private fun contrast(a: Color, b: Color): Double {
        val la = a.luminance() + 0.05
        val lb = b.luminance() + 0.05
        return maxOf(la, lb) / minOf(la, lb)
    }
}
