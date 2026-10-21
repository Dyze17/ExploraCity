package co.edu.uniquindio.exploracity.core.util

import co.edu.uniquindio.exploracity.domain.model.HoursBlock
import co.edu.uniquindio.exploracity.domain.model.OpeningHours
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalTime

class OpeningHoursFormatterTest {

    private val words = HoursWords(everyDay = "Todos los días", to = "a", and = "y")

    private fun format(days: Set<DayOfWeek>, opens: Int = 7, closes: Int = 19) =
        formatOpeningHours(OpeningHours.of(days, LocalTime.of(opens, 0), LocalTime.of(closes, 0)), words, spanishFormats)

    @Test
    fun `lunes a sábado como en el lienzo 13a`() {
        assertEquals("Lunes a sábado, 7:00 a 19:00", format(DayOfWeek.entries.toSet() - SUNDAY))
    }

    @Test
    fun `toda la semana`() {
        assertEquals("Todos los días, 6:00 a 22:00", format(DayOfWeek.entries.toSet(), opens = 6, closes = 22))
    }

    @Test
    fun `un tramo que termina el domingo`() {
        assertEquals("Martes a domingo, 9:00 a 18:00", format(DayOfWeek.entries.toSet() - MONDAY, opens = 9, closes = 18))
    }

    @Test
    fun `días sueltos y parejas se enumeran`() {
        assertEquals("Lunes, miércoles y viernes, 7:00 a 19:00", format(setOf(MONDAY, WEDNESDAY, FRIDAY)))
        assertEquals("Lunes, martes y viernes, 7:00 a 19:00", format(setOf(MONDAY, TUESDAY, FRIDAY)))
        assertEquals("Sábado y domingo, 7:00 a 19:00", format(setOf(SATURDAY, SUNDAY)))
    }

    @Test
    fun `tramo y días sueltos juntos`() {
        assertEquals("Lunes a miércoles y sábado, 7:00 a 19:00", format(setOf(MONDAY, TUESDAY, WEDNESDAY, SATURDAY)))
    }

    @Test
    fun `por tramos, uno por línea en el orden de la semana, también los que cierran pasada la medianoche`() {
        val club = OpeningHours.Blocks(
            listOf(
                HoursBlock(setOf(FRIDAY, SATURDAY), LocalTime.of(22, 0), LocalTime.of(3, 0)),
                HoursBlock(setOf(MONDAY, TUESDAY, WEDNESDAY), LocalTime.of(16, 0), LocalTime.of(2, 0)),
            ),
        )

        assertEquals("Lunes a miércoles, 16:00 a 2:00\nViernes y sábado, 22:00 a 3:00", formatOpeningHours(club, words, spanishFormats))
    }

    @Test
    fun `siempre abierto no tiene días ni horas`() {
        val words = words.copy(alwaysOpen = "Siempre abierto · acceso libre")

        assertEquals("Siempre abierto · acceso libre", formatOpeningHours(OpeningHours.AlwaysOpen, words, spanishFormats))
    }

    @Test
    fun `en inglés, con los días y la hora de ese idioma`() {
        val english = HoursWords(everyDay = "Every day", to = "to", and = "and")
        val hours = OpeningHours.of(DayOfWeek.entries.toSet() - SUNDAY, LocalTime.of(7, 0), LocalTime.of(19, 0))
        assertEquals("Monday to Saturday, 7:00 AM to 7:00 PM", formatOpeningHours(hours, english, englishFormats))
        val loose = OpeningHours.of(setOf(MONDAY, WEDNESDAY, FRIDAY), LocalTime.of(9, 30), LocalTime.of(13, 0))
        assertEquals("Monday, Wednesday and Friday, 9:30 AM to 1:00 PM", formatOpeningHours(loose, english, englishFormats))
    }
}
