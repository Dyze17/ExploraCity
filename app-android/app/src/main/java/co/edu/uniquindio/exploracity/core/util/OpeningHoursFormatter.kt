package co.edu.uniquindio.exploracity.core.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import co.edu.uniquindio.exploracity.domain.model.OpeningHours
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Palabras de enlace (de strings.xml): «Todos los días», «a» y «y». */
data class HoursWords(val everyDay: String, val to: String, val and: String)

/**
 * «Lunes a sábado, 7:00 a 19:00» («Monday to Saturday, 7:00 AM to 7:00 PM»). Los días seguidos forman un tramo
 * («martes a domingo»); los sueltos se enumeran («lunes, miércoles y viernes»). La semana empieza el lunes, como en el
 * paso 4.
 */
fun formatOpeningHours(hours: OpeningHours, words: HoursWords, formats: LocaleFormats): String {
    val timeFormat = DateTimeFormatter.ofPattern(formats.time, formats.locale)
    val days = formatDays(hours.days, words, formats.locale)
    val time = "${timeFormat.format(hours.opens)} ${words.to} ${timeFormat.format(hours.closes)}"
    return "$days, $time".replaceFirstChar { it.titlecase(formats.locale) }
}

@Composable
@ReadOnlyComposable
fun formatOpeningHours(hours: OpeningHours, words: HoursWords): String = formatOpeningHours(hours, words, localeFormats())

private fun formatDays(days: Set<DayOfWeek>, words: HoursWords, locale: Locale): String {
    if (days.size == DayOfWeek.entries.size) return words.everyDay
    val runs = mutableListOf<MutableList<DayOfWeek>>()
    for (day in DayOfWeek.entries.filter { it in days }) {
        val last = runs.lastOrNull()
        if (last != null && last.last().value + 1 == day.value) last += day else runs += mutableListOf(day)
    }
    // Tres o más días seguidos forman un tramo; uno o dos se nombran sueltos («lunes, martes y viernes»).
    val parts = runs.flatMap { run ->
        if (run.size >= 3) listOf("${run.first().fullName(locale)} ${words.to} ${run.last().fullName(locale)}") else run.map { it.fullName(locale) }
    }
    return when (parts.size) {
        1 -> parts.single()
        else -> parts.dropLast(1).joinToString(", ") + " ${words.and} " + parts.last()
    }
}

private fun DayOfWeek.fullName(locale: Locale): String = getDisplayName(TextStyle.FULL, locale)
