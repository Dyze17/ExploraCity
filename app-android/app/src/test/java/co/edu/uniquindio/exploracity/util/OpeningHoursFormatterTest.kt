package co.edu.uniquindio.exploracity.util

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
        formatOpeningHours(OpeningHours(days, LocalTime.of(opens, 0), LocalTime.of(closes, 0)), words)

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
}
