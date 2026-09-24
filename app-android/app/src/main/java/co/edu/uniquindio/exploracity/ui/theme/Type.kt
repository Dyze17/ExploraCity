package co.edu.uniquindio.exploracity.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import co.edu.uniquindio.exploracity.R

// Outfit y Manrope son fuentes variables (eje wght): un solo archivo sirve para todos los pesos.
private val Weights = listOf(FontWeight.W400, FontWeight.W500, FontWeight.W600, FontWeight.W700, FontWeight.W800)

private fun variableFamily(resId: Int) = FontFamily(
    Weights.map { weight ->
        Font(
            resId = resId,
            weight = weight,
            variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
        )
    },
)

/** Display, headline y titleLarge. */
val Outfit = variableFamily(R.font.outfit_variable)

/** Title medium/small, body y label. */
val Manrope = variableFamily(R.font.manrope_variable)

// Interlínea centrada y sin recorte, como el line-height de CSS de los lienzos.
private val CssLineHeight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

private fun style(
    family: FontFamily,
    weight: FontWeight,
    size: Int,
    lineHeight: Int,
    letterSpacing: TextUnit = 0.sp,
) = TextStyle(
    fontFamily = family,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing,
    lineHeightStyle = CssLineHeight,
)

val ExploraTypography = Typography(
    displayLarge = style(Outfit, FontWeight.W600, 44, 52),
    displayMedium = style(Outfit, FontWeight.W600, 34, 42),
    displaySmall = style(Outfit, FontWeight.W600, 30, 38), // derivado: la escala no lo define
    headlineLarge = style(Outfit, FontWeight.W600, 28, 36),
    headlineMedium = style(Outfit, FontWeight.W500, 24, 32),
    headlineSmall = style(Outfit, FontWeight.W500, 22, 28), // derivado: la escala no lo define
    titleLarge = style(Outfit, FontWeight.W600, 20, 28),
    titleMedium = style(Manrope, FontWeight.W700, 16, 24),
    titleSmall = style(Manrope, FontWeight.W700, 14, 20),
    bodyLarge = style(Manrope, FontWeight.W400, 16, 24),
    bodyMedium = style(Manrope, FontWeight.W400, 14, 20),
    bodySmall = style(Manrope, FontWeight.W400, 12, 16),
    labelLarge = style(Manrope, FontWeight.W600, 14, 20), // botones
    labelMedium = style(Manrope, FontWeight.W600, 12, 16, letterSpacing = 0.02.em),
    labelSmall = style(Manrope, FontWeight.W600, 11, 16), // navegación inferior
)
