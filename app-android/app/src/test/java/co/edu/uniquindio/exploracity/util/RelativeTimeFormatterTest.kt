package co.edu.uniquindio.exploracity.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset

class RelativeTimeFormatterTest {

    private val now = Instant.parse("2026-09-24T15:00:00Z")
    private val utc = ZoneOffset.UTC

    private fun ago(duration: Duration) = relativeTime(now - duration, now, utc)

    @Test
    fun `menos de un minuto, o una hora futura, es hace un momento`() {
        assertEquals(RelativeTime.JustNow, ago(Duration.ofSeconds(40)))
        assertEquals(RelativeTime.JustNow, relativeTime(now + Duration.ofMinutes(2), now, utc))
    }

    @Test
    fun `cuenta minutos, horas y días`() {
        assertEquals(RelativeTime.Minutes(1), ago(Duration.ofSeconds(61)))
        assertEquals(RelativeTime.Minutes(59), ago(Duration.ofMinutes(59)))
        assertEquals(RelativeTime.Hours(1), ago(Duration.ofMinutes(60)))
        assertEquals(RelativeTime.Hours(23), ago(Duration.ofHours(23)))
        assertEquals(RelativeTime.Days(1), ago(Duration.ofHours(24)))
        assertEquals(RelativeTime.Days(2), ago(Duration.ofDays(2)))
        assertEquals(RelativeTime.Days(29), ago(Duration.ofDays(29)))
    }

    @Test
    fun `pasado un mes da la fecha, con año solo si es otro`() {
        assertEquals(RelativeTime.On(LocalDate.of(2026, 8, 10), sameYear = true), ago(Duration.ofDays(45)))
        assertEquals(RelativeTime.On(LocalDate.of(2025, 3, 12), sameYear = false), relativeTime(Instant.parse("2025-03-12T12:00:00Z"), now, utc))
    }

    @Test
    fun `la fecha es la del día en la zona del teléfono`() {
        // 2 de la mañana en UTC todavía es 31 de julio en Bogotá.
        val then = Instant.parse("2026-08-01T02:00:00Z")
        assertEquals(RelativeTime.On(LocalDate.of(2026, 7, 31), sameYear = true), relativeTime(then, now, ZoneId.of("America/Bogota")))
    }

    @Test
    fun `fechas en español`() {
        assertEquals("10 de agosto", formatDate(LocalDate.of(2026, 8, 10), withYear = false))
        assertEquals("12 de marzo de 2025", formatDate(LocalDate.of(2025, 3, 12), withYear = true))
    }

    @Test
    fun `el mes del perfil, con año solo si es otro (26 · desde marzo)`() {
        assertEquals("marzo", formatMonth(YearMonth.of(2026, 3), withYear = false))
        assertEquals("diciembre de 2025", formatMonth(YearMonth.of(2025, 12), withYear = true))
    }

    @Test
    fun `el día de envío cuenta días de calendario (22 · Enviada hoy, ayer, hace 4 días)`() {
        val bogota = ZoneId.of("America/Bogota")
        val now = Instant.parse("2026-09-25T15:00:00Z") // 10:00 en Bogotá

        assertEquals(SubmittedDay.Today(LocalTime.of(9, 12)), submittedDay(Instant.parse("2026-09-25T14:12:00Z"), now, bogota))
        // Anoche a las 23:50 fue ayer, aunque no hayan pasado 24 horas.
        assertEquals(SubmittedDay.Yesterday, submittedDay(Instant.parse("2026-09-25T04:50:00Z"), now, bogota))
        assertEquals(SubmittedDay.DaysAgo(4), submittedDay(Instant.parse("2026-09-21T15:00:00Z"), now, bogota))
        assertEquals(SubmittedDay.On(LocalDate.of(2026, 8, 1), sameYear = true), submittedDay(Instant.parse("2026-08-01T15:00:00Z"), now, bogota))
    }

    @Test
    fun `un envío con el reloj adelantado es de hoy`() {
        val now = Instant.parse("2026-09-25T15:00:00Z")

        assertTrue(submittedDay(now.plusSeconds(600), now, utc) is SubmittedDay.Today)
    }

    @Test
    fun `la hora va sin cero delante`() {
        assertEquals("9:12", formatTime(LocalTime.of(9, 12)))
        assertEquals("21:05", formatTime(LocalTime.of(21, 5)))
    }
}
