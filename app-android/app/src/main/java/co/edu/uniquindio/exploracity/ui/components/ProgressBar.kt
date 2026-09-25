package co.edu.uniquindio.exploracity.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Barra de avance de los lienzos 26, 27 y 27A: pista redondeada y relleno sin el hueco ni el punto final de la
 * LinearProgressIndicator de M3. Con [description] el lector dice la frase completa («340 de 500 puntos, 68 por ciento
 * hacia Embajador Local»); sin ella, la barra es decorativa porque el mismo avance ya está escrito al lado.
 */
@Composable
fun ExploraProgressBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    description: String? = null,
) {
    val value = fraction.coerceIn(0f, 1f)
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .background(trackColor, CircleShape)
            // Solo la frase: con el rango de progreso, TalkBack le sumaría su propio porcentaje.
            .clearAndSetSemantics { if (description != null) contentDescription = description },
    ) {
        if (value > 0f) Box(Modifier.fillMaxWidth(value).fillMaxHeight().background(color, CircleShape))
    }
}
