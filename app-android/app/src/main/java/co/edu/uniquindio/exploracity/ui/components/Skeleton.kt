package co.edu.uniquindio.exploracity.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import co.edu.uniquindio.exploracity.ui.theme.ExploraElevation
import co.edu.uniquindio.exploracity.ui.theme.exploraColors
import co.edu.uniquindio.exploracity.ui.theme.exploraShadow

/** Brillo que recorre los skeletons (1,2 s, lineal), como en el lienzo 11. */
@Composable
fun rememberShimmerBrush(): Brush {
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    val highlight = MaterialTheme.exploraColors.skeletonHighlight
    val width = with(LocalDensity.current) { 340.dp.toPx() }
    val band = with(LocalDensity.current) { 80.dp.toPx() }
    val offset by rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue = -band,
        targetValue = width,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1200, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmer-offset",
    )
    return Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(offset, 0f),
        end = Offset(offset + band, 0f),
    )
}

@Composable
fun SkeletonBlock(brush: Brush, modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(8.dp)) {
    Box(modifier.clip(shape).background(brush))
}

/**
 * Silueta exacta de [POICard] para que no haya salto al cargar. No se anuncia por sí sola: el contenedor
 * de la lista da un único anuncio («Cargando lugares»).
 */
@Composable
fun POICardSkeleton(brush: Brush, modifier: Modifier = Modifier, titleWidth: Float = 0.78f, lineWidths: Pair<Float, Float> = 0.52f to 0.38f) {
    val shape = MaterialTheme.shapes.large
    Row(
        modifier
            .fillMaxWidth()
            .exploraShadow(ExploraElevation.Card, shape, MaterialTheme.exploraColors.shadow)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SkeletonBlock(brush, Modifier.size(96.dp), RoundedCornerShape(12.dp))
        Column(Modifier.weight(1f).padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Bar(brush, 16.dp, titleWidth)
            Bar(brush, 12.dp, lineWidths.first)
            Bar(brush, 12.dp, lineWidths.second)
        }
    }
}

@Composable
private fun Bar(brush: Brush, height: Dp, fraction: Float) {
    SkeletonBlock(brush, Modifier.fillMaxWidth(fraction).height(height))
}
