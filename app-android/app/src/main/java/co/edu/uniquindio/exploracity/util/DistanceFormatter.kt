package co.edu.uniquindio.exploracity.util

import java.text.NumberFormat
import java.util.Locale

private val spanishColombia: Locale = Locale.forLanguageTag("es-CO")

/** «850 m» por debajo de 1 km; «1,2 km» desde 1 km, con coma decimal (es-CO). */
fun formatDistance(meters: Int): String {
    if (meters < 1000) return "$meters m"
    val format = NumberFormat.getNumberInstance(spanishColombia).apply {
        minimumFractionDigits = 1
        maximumFractionDigits = 1
    }
    return "${format.format(meters / 1000.0)} km"
}
