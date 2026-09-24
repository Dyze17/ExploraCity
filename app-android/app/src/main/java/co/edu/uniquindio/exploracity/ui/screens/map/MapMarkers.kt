package co.edu.uniquindio.exploracity.ui.screens.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.edu.uniquindio.exploracity.domain.model.Category
import co.edu.uniquindio.exploracity.ui.components.iconRes
import co.edu.uniquindio.exploracity.ui.theme.CategoryColors
import co.edu.uniquindio.exploracity.ui.theme.ExploraCityTheme
import co.edu.uniquindio.exploracity.ui.theme.ThemeMode
import co.edu.uniquindio.exploracity.ui.theme.exploraColors
import kotlin.math.sqrt

// Estos composables se dibujan a mapa de bits para los marcadores de Google Maps: sin estado ni semántica
// (la descripción va en el propio marcador). El área táctil es el mapa de bits: mínimo 48 dp (README 8).

/** Distancia del centro del círculo a la punta, en fracciones del diámetro: un cuadrado girado 45°. */
private val TipFactor = sqrt(2f) / 2f

/**
 * Gota con el color y el icono de la categoría, como en el lienzo 8.a: un cuadrado de [diameter] con tres
 * esquinas redondas, girado para que la cuarta sea la punta. Seleccionado = 52 dp y borde de 3 dp.
 */
@Composable
fun PlacePin(category: Category, colors: CategoryColors, selected: Boolean, border: Color) {
    val diameter = if (selected) 52.dp else 40.dp
    val borderWidth = if (selected) 3.dp else 2.dp
    val iconSize = if (selected) 24.dp else 20.dp
    val height = diameter * (0.5f + TipFactor)
    // 4 dp a cada lado: 48 dp táctiles con la gota de 40 dp.
    Box(Modifier.padding(horizontal = 4.dp).size(width = diameter, height = height)) {
        Canvas(Modifier.size(width = diameter, height = height)) {
            val stroke = borderWidth.toPx()
            val radius = size.width / 2 - stroke / 2
            val center = Offset(size.width / 2, size.width / 2)
            val tip = Offset(size.width / 2, size.height - stroke / 2)
            val path = Path().apply {
                moveTo(tip.x, tip.y)
                // Los lados de la punta tocan el círculo a 45° de la vertical.
                arcTo(Rect(center, radius), startAngleDegrees = 45f, sweepAngleDegrees = -270f, forceMoveTo = false)
                close()
            }
            drawPath(path, colors.marker)
            drawPath(path, border, style = Stroke(width = stroke))
        }
        Icon(
            painterResource(category.iconRes),
            contentDescription = null,
            tint = colors.onMarker,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (diameter - iconSize) / 2)
                .size(iconSize),
        )
    }
}

/** Grupo: círculo con el número de lugares (48 dp; 56 dp desde 100). Crece con la fuente para no cortar la cifra. */
@Composable
fun GroupPin(count: Int) {
    val colors = MaterialTheme.exploraColors
    val large = count >= 100
    val minSize: Dp = if (large) 56.dp else 48.dp
    Box(
        Modifier
            .sizeIn(minWidth = minSize, minHeight = minSize)
            .aspectRatio(1f)
            .background(colors.mapCluster.container, CircleShape)
            .border(3.dp, colors.mapClusterBorder, CircleShape)
            .padding(6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            count.toString(),
            style = MaterialTheme.typography.labelLarge.copy(fontSize = if (large) 15.sp else 14.sp, fontWeight = FontWeight.W700),
            color = colors.mapCluster.content,
        )
    }
}

/** «Estás aquí»: punto tertiary con borde blanco, dentro de 48 dp. No es tocable. */
@Composable
fun UserPin() {
    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(20.dp)
                .background(MaterialTheme.colorScheme.tertiary, CircleShape)
                .border(3.dp, MaterialTheme.exploraColors.mapClusterBorder, CircleShape),
        )
    }
}

@Preview(name = "Marcadores 8.a · claro", backgroundColor = 0xFFE6E0D4, showBackground = true)
@Composable
private fun MarkersPreview() {
    ExploraCityTheme(ThemeMode.LIGHT) {
        val palette = MaterialTheme.exploraColors.category
        val border = MaterialTheme.exploraColors.mapClusterBorder
        Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            PlacePin(Category.NATURE, palette.nature, selected = false, border = border)
            PlacePin(Category.GASTRONOMY, palette.gastronomy, selected = true, border = border)
            GroupPin(24)
            GroupPin(137)
            UserPin()
        }
    }
}
