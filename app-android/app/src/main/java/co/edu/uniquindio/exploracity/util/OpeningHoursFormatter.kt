package co.edu.uniquindio.exploracity.util

import co.edu.uniquindio.exploracity.domain.model.OpeningHours
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

private val timeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("H:mm", spanishColombia)

/** Palabras de enlace (de strings.xml): «Todos los días», «a» y «y». */
data class HoursWords(val everyDay: String, val to: String, val and: String)

/**
 * «Lunes a sábado, 7:00 a 19:00». Los días seguidos forman un tramo («martes a domingo»); los sueltos se enumeran
 * («lunes, miércoles y viernes»). La semana empieza el lunes, como en el paso 4.
 */
fun formatOpeningHours(hours: OpeningHours, words: HoursWords): String {
    val days = formatDays(hours.days, words)
    val time = "${timeFormat.format(hours.opens)} ${words.to} ${timeFormat.format(hours.closes)}"
    return "$days, $time".replaceFirstChar { it.titlecase(spanishColombia) }
}

private fun formatDays(days: Set<DayOfWeek>, words: HoursWords): String {
    if (days.size == DayOfWeek.entries.size) return words.everyDay
    val runs = mutableListOf<MutableList<DayOfWeek>>()
    for (day in DayOfWeek.entries.filter { it in days }) {
        val last = runs.lastOrNull()
        if (last != null && last.last().value + 1 == day.value) last += day else runs += mutableListOf(day)
    }
    // Tres o más días seguidos forman un tramo; uno o dos se nombran sueltos («lunes, martes y viernes»).
    val parts = runs.flatMap { run ->
        if (run.size >= 3) listOf("${run.first().spanishName()} ${words.to} ${run.last().spanishName()}") else run.map { it.spanishName() }
    }
    return when (parts.size) {
        1 -> parts.single()
        else -> parts.dropLast(1).joinToString(", ") + " ${words.and} " + parts.last()
    }
}

private fun DayOfWeek.spanishName(): String = getDisplayName(TextStyle.FULL, spanishColombia)
