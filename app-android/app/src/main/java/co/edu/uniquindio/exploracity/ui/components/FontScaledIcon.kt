package co.edu.uniquindio.exploracity.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp

/**
 * Tamaño de un icono que acompaña a un texto (badges, chips, metadatos, botones): escala con la fuente
 * igual que el texto, como en los lienzos al 200 %. Los iconos sueltos (barras, campos) no lo usan.
 */
@Composable
@ReadOnlyComposable
internal fun Dp.scaledWithFont(): Dp = this * LocalDensity.current.fontScale
