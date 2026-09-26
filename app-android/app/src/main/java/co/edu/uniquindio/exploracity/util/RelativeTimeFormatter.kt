package co.edu.uniquindio.exploracity.util

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

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

/** Día de envío de una publicación como se dice en 22 («Enviada hoy · 9:12», «Enviada ayer», «hace 4 días»). */
sealed interface SubmittedDay {
    data class Today(val time: LocalTime) : SubmittedDay

    data object Yesterday : SubmittedDay

    data class DaysAgo(val days: Int) : SubmittedDay

    /** Pasado un mes, la fecha, como en [RelativeTime.On]. */
    data class On(val date: LocalDate, val sameYear: Boolean) : SubmittedDay
}

/**
 * Cuenta días de calendario en [zone], no periodos de 24 horas: lo enviado anoche a las 23:50 fue «ayer» aunque hayan
 * pasado diez minutos. Un envío del futuro (relojes que no coinciden) es de hoy.
 */
fun submittedDay(then: Instant, now: Instant, zone: ZoneId): SubmittedDay {
    val thenDate = then.atZone(zone).toLocalDate()
    val today = now.atZone(zone).toLocalDate()
    val days = ChronoUnit.DAYS.between(thenDate, today)
    return when {
        days <= 0 -> SubmittedDay.Today(then.atZone(zone).toLocalTime())
        days == 1L -> SubmittedDay.Yesterday
        days < 30 -> SubmittedDay.DaysAgo(days.toInt())
        else -> SubmittedDay.On(thenDate, sameYear = thenDate.year == today.year)
    }
}

private val hourMinute = DateTimeFormatter.ofPattern("H:mm", spanishColombia)

/** «9:12». */
fun formatTime(time: LocalTime): String = hourMinute.format(time)
