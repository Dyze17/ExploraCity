package co.edu.uniquindio.exploracity.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Niveles de elevación del diseño (claro). En oscuro no hay sombra: ver [ExploraColors.shadow]. */
object ExploraElevation {
    val Card = 1.dp
    val Fab = 3.dp
    val SheetOrDialog = 6.dp
}

/** Opacidad del scrim (`colorScheme.scrim`) por tipo de superficie. */
object ScrimAlpha {
    const val Dialog = 0.5f
    const val Sheet = 0.4f
}

/**
 * Sombra cálida del sistema de diseño. Se pasa `MaterialTheme.exploraColors.shadow`:
 * en oscuro es transparente y no se dibuja nada.
 */
fun Modifier.exploraShadow(elevation: Dp, shape: Shape, color: Color): Modifier =
    if (color.alpha == 0f) this else shadow(elevation, shape, clip = false, ambientColor = color, spotColor = color)
