package co.edu.uniquindio.exploracity.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import co.edu.uniquindio.exploracity.R
import co.edu.uniquindio.exploracity.util.RelativeTime
import co.edu.uniquindio.exploracity.util.formatDate
import co.edu.uniquindio.exploracity.util.relativeTime
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import kotlin.time.Duration.Companion.seconds

/** La hora actual, puesta al día cada 30 s mientras la pantalla está abierta: «hace 2 días» no se queda viejo. */
@Composable
fun rememberNow(): State<Instant> = produceState(Instant.now()) {
    while (true) {
        delay(30.seconds)
        value = Instant.now()
    }
}

/** «hace un momento», «hace 2 horas», «hace 5 días» o, pasado un mes, «12 de marzo». */
@Composable
fun relativeTimeText(then: Instant, now: Instant): String = when (val time = relativeTime(then, now, ZoneId.systemDefault())) {
    RelativeTime.JustNow -> stringResource(R.string.time_just_now)
    is RelativeTime.Minutes -> pluralStringResource(R.plurals.time_minutes_ago, time.count, time.count)
    is RelativeTime.Hours -> pluralStringResource(R.plurals.time_hours_ago, time.count, time.count)
    is RelativeTime.Days -> pluralStringResource(R.plurals.time_days_ago, time.count, time.count)
    is RelativeTime.On -> formatDate(time.date, withYear = !time.sameYear)
}
