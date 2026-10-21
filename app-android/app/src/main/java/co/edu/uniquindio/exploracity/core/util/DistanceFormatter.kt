package co.edu.uniquindio.exploracity.core.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import java.text.NumberFormat
import java.util.Locale

/** «1.000» en español, «1,000» en inglés: el avance de una insignia (27). */
fun formatCount(count: Int, locale: Locale): String = NumberFormat.getIntegerInstance(locale).format(count)

@Composable
@ReadOnlyComposable
fun formatCount(count: Int): String = formatCount(count, appLocale())

/** «850 m» por debajo de 1 km; desde 1 km, con un decimal: «1,2 km» en español, «1.2 km» en inglés. */
fun formatDistance(meters: Int, locale: Locale): String {
    if (meters < 1000) return "$meters m"
    val format = NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = 1
        maximumFractionDigits = 1
    }
    return "${format.format(meters / 1000.0)} km"
}

@Composable
@ReadOnlyComposable
fun formatDistance(meters: Int): String = formatDistance(meters, appLocale())
