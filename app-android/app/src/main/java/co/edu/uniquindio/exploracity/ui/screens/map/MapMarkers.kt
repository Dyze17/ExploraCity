package co.edu.uniquindio.exploracity.ui.screens.map

import androidx.annotation.DrawableRes
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import co.edu.uniquindio.exploracity.R
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
 * esquinas redondas, girado para que la cuarta sea la punta. Seleccionado = 52 dp y borde de 3 dp. También es el pin
 * del paso 3 del formulario (17.a).
 */
@Composable
fun PlacePin(category: Category, colors: CategoryColors, selected: Boolean, border: Color) {
    // 4 dp a cada lado: 48 dp táctiles con la gota de 40 dp.
    DropPin(
        fill = colors.marker,
        border = border,
        icon = category.iconRes,
        iconTint = colors.onMarker,
        diameter = if (selected) 52.dp else 40.dp,
        borderWidth = if (selected) 3.dp else 2.dp,
        iconSize = if (selected) 24.dp else 20.dp,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

/** 17A · «Tu nuevo lugar» en el minimapa: gota con «+», para distinguirla de los lugares existentes (cuadrados). */
@Composable
fun NewPlacePin() {
    DropPin(
        fill = MaterialTheme.colorScheme.primary,
        border = MaterialTheme.colorScheme.surface,
        icon = R.drawable.ic_add,
        iconTint = MaterialTheme.colorScheme.onPrimary,
        diameter = 30.dp,
        borderWidth = 2.dp,
        iconSize = 18.dp,
    )
}

/** 17A · Lugar existente en el minimapa: cuadrado con el número de su tarjeta. Forma + número, nunca solo color. */
@Composable
fun NumberPin(number: Int, size: Dp = 26.dp) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(size)
            .background(scheme.inverseSurface, RoundedCornerShape(7.dp))
            .border(2.dp, scheme.surface, RoundedCornerShape(7.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            number.toString(),
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.W700),
            color = scheme.inverseOnSurface,
        )
    }
}

@Composable
private fun DropPin(
    fill: Color,
    border: Color,
    @DrawableRes icon: Int,
    iconTint: Color,
    diameter: Dp,
    borderWidth: Dp,
    iconSize: Dp,
    modifier: Modifier = Modifier,
) {
    val height = diameter * (0.5f + TipFactor)
    Box(modifier.size(width = diameter, height = height)) {
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
            drawPath(path, fill)
            drawPath(path, border, style = Stroke(width = stroke))
        }
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = iconTint,
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
            NewPlacePin()
            NumberPin(1)
        }
    }
}
