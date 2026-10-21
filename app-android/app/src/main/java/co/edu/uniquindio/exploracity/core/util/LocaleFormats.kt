package co.edu.uniquindio.exploracity.core.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import co.edu.uniquindio.exploracity.R
import java.util.Locale

/**
 * v1.2.0 · Cómo se escriben fechas, horas y cifras en el idioma de la app. Los patrones son de DateTimeFormatter y
 * están en strings.xml (format_*): cada traducción pone los suyos.
 */
data class LocaleFormats(
    val locale: Locale,
    /** «12 de marzo» · «March 12». */
    val dayMonth: String,
    /** «12 de marzo de 2025» · «March 12, 2025». */
    val dayMonthYear: String,
    /** «marzo de 2025» · «March 2025». */
    val monthYear: String,
    /** «9:12» · «9:12 AM». */
    val time: String,
)

/** El idioma de la pantalla: el de la app si se eligió en Ajustes, si no el del teléfono. */
@Composable
@ReadOnlyComposable
fun appLocale(): Locale = LocalConfiguration.current.locales[0]

/** Los formatos del idioma de la pantalla. */
@Composable
@ReadOnlyComposable
fun localeFormats(): LocaleFormats = LocaleFormats(
    locale = appLocale(),
    dayMonth = stringResource(R.string.format_day_month),
    dayMonthYear = stringResource(R.string.format_day_month_year),
    monthYear = stringResource(R.string.format_month_year),
    time = stringResource(R.string.format_time),
)
