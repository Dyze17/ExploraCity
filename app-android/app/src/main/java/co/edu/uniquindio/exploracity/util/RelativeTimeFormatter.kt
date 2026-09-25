package co.edu.uniquindio.exploracity.util

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Antigüedad de un comentario como se dice en 14 («hace 2 días»). La pantalla pone las palabras. */
sealed interface RelativeTime {
    data object JustNow : RelativeTime

    data class Minutes(val count: Int) : RelativeTime

    data class Hours(val count: Int) : RelativeTime

    data class Days(val count: Int) : RelativeTime

    /** Pasado un mes se da la fecha: «hace 40 días» obliga a hacer cuentas. */
    data class On(val date: LocalDate, val sameYear: Boolean) : RelativeTime
}

/**
 * Menos de un minuto es «hace un momento»; también una hora del futuro, que llega si el reloj del teléfono y el
 * del servidor no coinciden. Hasta 30 días se cuenta el tiempo transcurrido; después, la fecha en [zone].
 */
fun relativeTime(then: Instant, now: Instant, zone: ZoneId): RelativeTime {
    val elapsed = Duration.between(then, now)
    return when {
        elapsed < Duration.ofMinutes(1) -> RelativeTime.JustNow
        elapsed < Duration.ofHours(1) -> RelativeTime.Minutes(elapsed.toMinutes().toInt())
        elapsed < Duration.ofDays(1) -> RelativeTime.Hours(elapsed.toHours().toInt())
        elapsed < Duration.ofDays(30) -> RelativeTime.Days(elapsed.toDays().toInt())
        else -> {
            val date = then.atZone(zone).toLocalDate()
            RelativeTime.On(date, sameYear = date.year == now.atZone(zone).year)
        }
    }
}

private val dayMonth = DateTimeFormatter.ofPattern("d 'de' MMMM", spanishColombia)
private val dayMonthYear = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", spanishColombia)

/** «12 de marzo»; de otro año, «12 de marzo de 2025». */
fun formatDate(date: LocalDate, withYear: Boolean): String = (if (withYear) dayMonthYear else dayMonth).format(date)

private val monthOnly = DateTimeFormatter.ofPattern("MMMM", spanishColombia)
private val monthYear = DateTimeFormatter.ofPattern("MMMM 'de' yyyy", spanishColombia)

/** «marzo»; de otro año, «marzo de 2025» (26: «Residente · Bogotá · desde marzo»). */
fun formatMonth(month: YearMonth, withYear: Boolean): String = (if (withYear) monthYear else monthOnly).format(month)
