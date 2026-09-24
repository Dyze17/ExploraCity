package co.edu.uniquindio.exploracity.ui.theme

import androidx.compose.ui.unit.dp

/** Espaciado en múltiplos de 4 dp. */
object ExploraSpacing {
    val IconToText = 4.dp
    val BetweenChips = 8.dp
    val CardPadding = 12.dp
    val ScreenMargin = 16.dp
    val BetweenBlocks = 24.dp
    val BetweenSections = 32.dp
}

/** Alturas del esqueleto de pantalla y áreas táctiles mínimas. */
object ExploraSizes {
    val MinTouchTarget = 48.dp
    val ModerationActionBar = 52.dp
    val NavigationItem = 56.dp
    val FieldMinHeight = 56.dp
    val TopAppBar = 56.dp
    val BottomBar = 80.dp
}

/** Umbrales de la regla «Fuente al 200 %». */
object FontScaleThresholds {
    /** Por encima: filas → columnas (tarjetas, barras de acciones, contadores). */
    const val StackRows = 1.3f

    /** Por encima: se retira el FAB; «Publicar» sigue en la barra inferior. */
    const val HideFab = 1.5f
}
