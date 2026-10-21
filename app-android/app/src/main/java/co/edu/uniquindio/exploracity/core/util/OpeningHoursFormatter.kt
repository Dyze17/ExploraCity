package co.edu.uniquindio.exploracity.core.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource
import co.edu.uniquindio.exploracity.R
import co.edu.uniquindio.exploracity.domain.model.HoursBlock
import co.edu.uniquindio.exploracity.domain.model.OpeningHours
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Palabras de enlace (de strings.xml): «Todos los días», «a», «y» y, sin horario, «Siempre abierto · acceso libre». */
data class HoursWords(val everyDay: String, val to: String, val and: String, val alwaysOpen: String = "")

/**
 * «Lunes a sábado, 7:00 a 19:00» («Monday to Saturday, 7:00 AM to 7:00 PM»), un tramo por línea y en el orden de la
 * semana. Los días seguidos forman un tramo («martes a domingo»); los sueltos se enumeran («lunes, miércoles y
 * viernes»). La semana empieza el lunes, como en el paso 4. Un tramo que cierra pasada la medianoche se lee igual:
 * «viernes y sábado, 22:00 a 3:00».
 */
fun formatOpeningHours(hours: OpeningHours, words: HoursWords, formats: LocaleFormats): String = when (hours) {
    OpeningHours.AlwaysOpen -> words.alwaysOpen
    is OpeningHours.Blocks -> hours.blocks.sortedBy { block -> block.days.min() }.joinToString("\n") { formatBlock(it, words, formats) }
}

@Composable
@ReadOnlyComposable
fun formatOpeningHours(hours: OpeningHours): String = formatOpeningHours(
    hours,
    HoursWords(
        everyDay = stringResource(R.string.hours_every_day),
        to = stringResource(R.string.hours_to),
        and = stringResource(R.string.list_and),
        alwaysOpen = stringResource(R.string.hours_always_open),
    ),
    localeFormats(),
)

private fun formatBlock(block: HoursBlock, words: HoursWords, formats: LocaleFormats): String {
    val timeFormat = DateTimeFormatter.ofPattern(formats.time, formats.locale)
    val days = formatDays(block.days, words, formats.locale)
    val time = "${timeFormat.format(block.opens)} ${words.to} ${timeFormat.format(block.closes)}"
    return "$days, $time".replaceFirstChar { it.titlecase(formats.locale) }
}

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
