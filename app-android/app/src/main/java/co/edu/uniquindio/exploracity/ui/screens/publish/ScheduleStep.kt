package co.edu.uniquindio.exploracity.ui.screens.publish

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.TimePickerDialogDefaults
import androidx.compose.material3.TimePickerDisplayMode
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.edu.uniquindio.exploracity.R
import co.edu.uniquindio.exploracity.domain.model.PriceRange
import co.edu.uniquindio.exploracity.ui.components.ExploraButton
import co.edu.uniquindio.exploracity.ui.components.ExploraButtonStyle
import co.edu.uniquindio.exploracity.ui.components.ExploraCheckbox
import co.edu.uniquindio.exploracity.ui.components.labelRes
import co.edu.uniquindio.exploracity.ui.components.scaledWithFont
import co.edu.uniquindio.exploracity.ui.components.spokenRes
import co.edu.uniquindio.exploracity.ui.components.symbol
import co.edu.uniquindio.exploracity.ui.theme.FontScaleThresholds
import co.edu.uniquindio.exploracity.ui.theme.exploraColors
import co.edu.uniquindio.exploracity.util.spanishColombia
import co.edu.uniquindio.exploracity.viewmodel.DraftField
import co.edu.uniquindio.exploracity.viewmodel.PublishUiState
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

/** El orden de la semana en Colombia, como el resto de la app: empieza el lunes. */
private val week = DayOfWeek.entries

private val timeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("H:mm", spanishColombia)

/** Cuál de las dos horas se está eligiendo. */
private enum class TimeField { OPENS, CLOSES }

/**
 * 18 · Horario y rango de precio. El horario es sugerido: «No tengo el horario exacto» libera el paso. El cierre debe
 * ser posterior a la apertura, con el mensaje junto al campo. El precio es opcional y se quita tocándolo otra vez.
 */
@Composable
internal fun ScheduleStep(state: PublishUiState, callbacks: PublishCallbacks) {
    val draft = state.draft
    val hours = draft.hours
    val enabled = !draft.hoursUnknown
    val stacked = LocalDensity.current.fontScale > FontScaleThresholds.StackRows
    var picking by rememberSaveable { mutableStateOf<TimeField?>(null) }
    val daysFocus = remember { FocusRequester() }
    val opensFocus = remember { FocusRequester() }
    val closesFocus = remember { FocusRequester() }
    LaunchedEffect(state.errorFocusRequest) {
        if (state.errorFocusRequest == 0) return@LaunchedEffect
        when (state.stepErrors.firstOrNull()) {
            DraftField.DAYS -> daysFocus.requestFocus()
            DraftField.OPENS -> opensFocus.requestFocus()
            DraftField.CLOSES -> closesFocus.requestFocus()
            else -> Unit
        }
    }

    StepHeading(stringResource(R.string.publish_schedule_heading), stringResource(R.string.publish_schedule_intro))
    if (state.showErrors && state.stepErrors.isNotEmpty()) ErrorSummary(state.stepErrors, closesBeforeOpens = state.closesBeforeOpens)

    Section(stringResource(R.string.publish_schedule_days)) {
        DayToggles(hours.days, enabled, state.showScheduleError(DraftField.DAYS), daysFocus, callbacks.onDayToggle)
    }

    val opens = @Composable { modifier: Modifier ->
        TimeField(
            label = stringResource(R.string.publish_schedule_opens),
            time = hours.opens,
            enabled = enabled,
            error = if (state.showScheduleError(DraftField.OPENS)) stringResource(R.string.publish_schedule_opens_missing) else null,
            pickLabel = stringResource(R.string.publish_schedule_pick_opens),
            onPick = { picking = TimeField.OPENS },
            modifier = modifier.focusRequester(opensFocus),
        )
    }
    val closesError = when {
        !enabled -> null
        state.closesBeforeOpens -> stringResource(R.string.publish_schedule_closes_before)
        state.showScheduleError(DraftField.CLOSES) -> stringResource(R.string.publish_schedule_closes_missing)
        else -> null
    }
    val closes = @Composable { modifier: Modifier ->
        TimeField(
            label = stringResource(R.string.publish_schedule_closes),
            time = hours.closes,
            enabled = enabled,
            error = closesError,
            pickLabel = stringResource(R.string.publish_schedule_pick_closes),
            onPick = { picking = TimeField.CLOSES },
            modifier = modifier.focusRequester(closesFocus),
        )
    }
    if (stacked) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            opens(Modifier.fillMaxWidth())
            closes(Modifier.fillMaxWidth())
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            opens(Modifier.weight(1f))
            closes(Modifier.weight(1f))
        }
    }
    UnknownHours(draft.hoursUnknown, callbacks.onHoursUnknownChange)

    Section(stringResource(R.string.publish_schedule_price)) {
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PriceOrder.forEach { price -> PriceOption(price, selected = draft.price == price, onClick = { callbacks.onPriceChange(price) }) }
        }
    }

    when (picking) {
        TimeField.OPENS -> TimeDialog(
            title = stringResource(R.string.publish_schedule_pick_opens),
            initial = hours.opens ?: LocalTime.of(8, 0),
            onDismiss = { picking = null },
            onPick = {
                callbacks.onOpensChange(it)
                picking = null
            },
        )
        TimeField.CLOSES -> TimeDialog(
            title = stringResource(R.string.publish_schedule_pick_closes),
            initial = hours.closes ?: LocalTime.of(18, 0),
            onDismiss = { picking = null },
            onPick = {
                callbacks.onClosesChange(it)
                picking = null
            },
        )
        null -> Unit
    }
}

/** Como en el lienzo 18.a: de lo más barato a lo más caro y al final la entrada libre. */
private val PriceOrder = listOf(PriceRange.LOW, PriceRange.MEDIUM, PriceRange.HIGH, PriceRange.FREE)

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.W700),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        content()
    }
}

/** Días de 48 dp: elegido = relleno, borde y texto en negrita; el lector dice «lunes, seleccionado». */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayToggles(days: Set<DayOfWeek>, enabled: Boolean, error: Boolean, focusRequester: FocusRequester, onToggle: (DayOfWeek) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val explora = MaterialTheme.exploraColors
    val selectedText = stringResource(R.string.publish_schedule_day_selected)
    val notSelectedText = stringResource(R.string.publish_schedule_day_not_selected)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            week.forEachIndexed { index, day ->
                val selected = day in days
                val shape = RoundedCornerShape(12.dp)
                val content = when {
                    !enabled -> explora.textPlaceholder
                    selected -> explora.onPrimaryContainerAccent
                    else -> scheme.onSurface
                }
                val full = day.getDisplayName(TextStyle.FULL, spanishColombia)
                Box(
                    Modifier
                        .then(if (index == 0) Modifier.focusRequester(focusRequester) else Modifier)
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .clip(shape)
                        .background(if (selected && enabled) scheme.primaryContainer else Color.Transparent)
                        .border(1.dp, if (!enabled) scheme.outlineVariant else if (selected) scheme.primary else if (error) scheme.error else scheme.outline, shape)
                        .toggleable(value = selected, enabled = enabled, onValueChange = { onToggle(day) })
                        .semantics { stateDescription = if (selected) selectedText else notSelectedText }
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        day.getDisplayName(TextStyle.SHORT, spanishColombia).removeSuffix(".").replaceFirstChar { it.titlecase(spanishColombia) },
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = if (selected) FontWeight.W700 else FontWeight.W600),
                        color = content,
                        modifier = Modifier.semantics { contentDescription = full },
                    )
                }
            }
        }
        if (error) FieldError(stringResource(R.string.publish_schedule_days_missing))
    }
}

/** «Abre» / «Cierra»: se ve como un campo y abre el selector de hora. */
@Composable
private fun TimeField(
    label: String,
    time: LocalTime?,
    enabled: Boolean,
    error: String?,
    pickLabel: String,
    onPick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val explora = MaterialTheme.exploraColors
    val shape = RoundedCornerShape(12.dp)
    val value = time?.let(timeFormat::format)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.W600), color = explora.textSecondary)
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clip(shape)
                .background(if (enabled) scheme.surfaceContainerLowest else Color.Transparent)
                .border(if (error != null) 2.dp else 1.dp, if (!enabled) scheme.outlineVariant else if (error != null) scheme.error else scheme.outline, shape)
                .clickable(enabled = enabled, role = Role.Button, onClickLabel = pickLabel, onClick = onPick)
                .semantics {
                    contentDescription = "$label, ${value ?: ""}".trimEnd(',', ' ')
                    if (error != null) error(error)
                }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                value ?: stringResource(R.string.publish_schedule_time_empty),
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                color = if (!enabled || value == null) explora.textPlaceholder else scheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Icon(painterResource(R.drawable.ic_schedule), null, tint = explora.iconSecondary, modifier = Modifier.size(20.dp.scaledWithFont()))
        }
        if (error != null) FieldError(error)
    }
}

@Composable
private fun FieldError(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(painterResource(R.drawable.ic_error), null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp.scaledWithFont()))
        Text(text, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp), color = MaterialTheme.colorScheme.error)
    }
}

/** «No tengo el horario exacto»: casilla de 24 dp en una fila de 48. */
@Composable
private fun UnknownHours(checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ExploraCheckbox(checked)
        Text(stringResource(R.string.publish_schedule_unknown), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp), color = MaterialTheme.colorScheme.onSurface)
    }
}

/** Un rango de precio como radio: elegido = relleno, borde y check. El lector oye «hasta 25.000 pesos». */
@Composable
private fun PriceOption(price: PriceRange, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val explora = MaterialTheme.exploraColors
    val shape = RoundedCornerShape(12.dp)
    val label = stringResource(price.labelRes)
    val visible = price.symbol?.let { "$it · $label" } ?: label
    val spoken = stringResource(price.spokenRes)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(if (selected) scheme.primaryContainer else Color.Transparent)
            .border(1.dp, if (selected) scheme.primary else scheme.outline, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = spoken }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) Icon(painterResource(R.drawable.ic_check), null, tint = explora.onPrimaryContainerAccent, modifier = Modifier.size(20.dp.scaledWithFont()))
        Text(
            visible,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = if (selected) FontWeight.W700 else FontWeight.W600),
            color = if (selected) explora.onPrimaryContainerAccent else scheme.onSurface,
        )
    }
}

/**
 * Selector de hora de Material 3 en 24 h, como el resto de la app («7:00», «19:00»). Con fuente grande solo se escribe
 * la hora (el reloj no cabe y el teclado es más fácil de leer y de usar con el lector) y los botones van en columna:
 * en fila, «Aceptar» se partía letra por letra.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(title: String, initial: LocalTime, onDismiss: () -> Unit, onPick: (LocalTime) -> Unit) {
    val largeFont = LocalDensity.current.fontScale > FontScaleThresholds.StackRows
    val picker = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
    // Solo mientras el diálogo está abierto: TimePickerDisplayMode no se puede guardar en el estado de la pantalla.
    var mode by remember { mutableStateOf(if (largeFont) TimePickerDisplayMode.Input else TimePickerDisplayMode.Picker) }
    val accept = @Composable { modifier: Modifier ->
        ExploraButton(
            stringResource(R.string.publish_schedule_accept),
            onClick = { onPick(LocalTime.of(picker.hour, picker.minute)) },
            modifier = modifier,
            style = ExploraButtonStyle.TEXT,
        )
    }
    val cancel = @Composable { modifier: Modifier ->
        ExploraButton(stringResource(R.string.publish_schedule_cancel), onClick = onDismiss, modifier = modifier, style = ExploraButtonStyle.TEXT)
    }
    TimePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            if (largeFont) {
                Column(horizontalAlignment = Alignment.End) {
                    accept(Modifier)
                    cancel(Modifier)
                }
            } else {
                accept(Modifier)
            }
        },
        dismissButton = if (largeFont) null else ({ cancel(Modifier) }),
        title = { Text(title, style = MaterialTheme.typography.labelLarge) },
        modeToggleButton = if (largeFont) {
            null
        } else {
            {
                TimePickerDialogDefaults.DisplayModeToggle(
                    onDisplayModeChange = { mode = if (mode == TimePickerDisplayMode.Picker) TimePickerDisplayMode.Input else TimePickerDisplayMode.Picker },
                    displayMode = mode,
                )
            }
        },
    ) {
        if (mode == TimePickerDisplayMode.Picker) TimePicker(picker) else TimeInput(picker)
    }
}
