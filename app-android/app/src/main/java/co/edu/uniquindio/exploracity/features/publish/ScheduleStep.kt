package co.edu.uniquindio.exploracity.features.publish

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
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
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.mutableIntStateOf
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
import co.edu.uniquindio.exploracity.core.ui.components.ExploraButton
import co.edu.uniquindio.exploracity.core.ui.components.ExploraButtonStyle
import co.edu.uniquindio.exploracity.core.ui.components.labelRes
import co.edu.uniquindio.exploracity.core.ui.components.scaledWithFont
import co.edu.uniquindio.exploracity.core.ui.components.spokenRes
import co.edu.uniquindio.exploracity.core.ui.components.symbol
import co.edu.uniquindio.exploracity.core.ui.theme.FontScaleThresholds
import co.edu.uniquindio.exploracity.core.ui.theme.exploraColors
import co.edu.uniquindio.exploracity.core.util.appLocale
import co.edu.uniquindio.exploracity.core.util.localeFormats
import co.edu.uniquindio.exploracity.domain.model.DraftHours
import co.edu.uniquindio.exploracity.domain.model.DraftSchedule
import co.edu.uniquindio.exploracity.domain.model.HoursMode
import co.edu.uniquindio.exploracity.domain.model.PriceRange
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

/** El orden de la semana en Colombia, como el resto de la app: empieza el lunes. */
private val week = DayOfWeek.entries

/** Cuál de las dos horas se está eligiendo. */
private enum class TimeField { OPENS, CLOSES }

/** 18 · Un campo de un tramo del horario que falta o está mal. */
internal data class ScheduleError(val block: Int, val field: DraftField)

/**
 * 18 · Lo que falta en cada tramo, en el orden de la pantalla: días, apertura y cierre (distinto de la apertura). Sin
 * horario o siempre abierto, nada.
 */
internal fun DraftSchedule.errors(): List<ScheduleError> = if (mode != HoursMode.HOURS) {
    emptyList()
} else {
    blocks.flatMapIndexed { index, block ->
        listOfNotNull(
            ScheduleError(index, DraftField.DAYS).takeIf { block.days.isEmpty() },
            ScheduleError(index, DraftField.OPENS).takeIf { block.opens == null },
            ScheduleError(index, DraftField.CLOSES).takeIf { block.closes == null || !block.timesDiffer },
        )
    }
}

/** 18 · Lo único que falla es que algún tramo abre y cierra a la misma hora. */
internal fun DraftSchedule.onlySameTimes(): Boolean {
    val errors = errors()
    return errors.isNotEmpty() && errors.all { it.field == DraftField.CLOSES && blocks[it.block].closes != null }
}

/** Lo que se hace con el horario y el precio (paso 4 del formulario y edición, 23). El [Int] es el tramo. */
class ScheduleCallbacks(
    val onModeChange: (HoursMode) -> Unit = {},
    val onDayToggle: (Int, DayOfWeek) -> Unit = { _, _ -> },
    val onOpensChange: (Int, LocalTime) -> Unit = { _, _ -> },
    val onClosesChange: (Int, LocalTime) -> Unit = { _, _ -> },
    val onAddBlock: () -> Unit = {},
    val onRemoveBlock: (Int) -> Unit = {},
    val onPriceChange: (PriceRange) -> Unit = {},
)

/**
 * 18 · Horario y rango de precio. El horario va por tramos de días (cada uno con su franja, que puede cerrar pasada la
 * medianoche), siempre abierto o sin saberlo; los dos últimos liberan el paso. El precio es opcional y se quita
 * tocándolo otra vez.
 */
@Composable
internal fun ScheduleStep(state: PublishUiState, callbacks: ScheduleCallbacks) {
    StepHeading(stringResource(R.string.publish_schedule_heading), stringResource(R.string.publish_schedule_intro))
    val draft = state.draft
    if (state.showErrors && state.stepErrors.isNotEmpty()) ErrorSummary(state.stepErrors, sameTimes = draft.schedule.onlySameTimes())
    ScheduleFields(
        schedule = draft.schedule,
        price = draft.price,
        showError = state::showScheduleError,
        firstError = draft.schedule.errors().firstOrNull(),
        errorFocusRequest = state.errorFocusRequest,
        callbacks = callbacks,
    )
}

/**
 * Cómo se cuenta el horario, sus tramos y el rango de precio. [showError] dice qué campos de qué tramo marcar en rojo;
 * el foco va a [firstError] cada vez que cambia [errorFocusRequest].
 */
@Composable
internal fun ScheduleFields(
    schedule: DraftSchedule,
    price: PriceRange?,
    showError: (Int, DraftField) -> Boolean,
    callbacks: ScheduleCallbacks,
    firstError: ScheduleError? = null,
    errorFocusRequest: Int = 0,
) {
    var pickingBlock by rememberSaveable { mutableIntStateOf(0) }
    var picking by rememberSaveable { mutableStateOf<TimeField?>(null) }

    Section(stringResource(R.string.publish_schedule_mode)) {
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HoursMode.entries.forEach { mode -> ModeOption(mode, selected = schedule.mode == mode, onClick = { callbacks.onModeChange(mode) }) }
        }
    }

    if (schedule.mode == HoursMode.HOURS) {
        schedule.blocks.forEachIndexed { index, block ->
            BlockFields(
                index = index,
                count = schedule.blocks.size,
                block = block,
                taken = schedule.takenByOthers(index),
                showError = { field -> showError(index, field) },
                focusOn = firstError?.takeIf { it.block == index }?.field,
                errorFocusRequest = errorFocusRequest,
                callbacks = callbacks,
                onPick = { field ->
                    pickingBlock = index
                    picking = field
                },
            )
        }
        if (schedule.canAddBlock) {
            ExploraButton(
                stringResource(R.string.publish_schedule_add_block),
                onClick = callbacks.onAddBlock,
                modifier = Modifier.fillMaxWidth(),
                style = ExploraButtonStyle.SECONDARY,
                icon = R.drawable.ic_add,
            )
        }
    }

    Section(stringResource(R.string.publish_schedule_price)) {
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PriceOrder.forEach { option -> PriceOption(option, selected = price == option, onClick = { callbacks.onPriceChange(option) }) }
        }
    }

    val picked = schedule.blocks.getOrNull(pickingBlock)?.takeIf { schedule.mode == HoursMode.HOURS }
    when (picking.takeIf { picked != null }) {
        TimeField.OPENS -> TimeDialog(
            title = stringResource(R.string.publish_schedule_pick_opens),
            initial = picked?.opens ?: LocalTime.of(8, 0),
            onDismiss = { picking = null },
            onPick = {
                callbacks.onOpensChange(pickingBlock, it)
                picking = null
            },
        )
        TimeField.CLOSES -> TimeDialog(
            title = stringResource(R.string.publish_schedule_pick_closes),
            initial = picked?.closes ?: LocalTime.of(18, 0),
            onDismiss = { picking = null },
            onPick = {
                callbacks.onClosesChange(pickingBlock, it)
                picking = null
            },
        )
        null -> Unit
    }
}

/** «Tiene horario», «Siempre abierto · acceso libre» o «No sé el horario», como radio: elegido = relleno, borde y check. */
@Composable
private fun ModeOption(mode: HoursMode, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val explora = MaterialTheme.exploraColors
    val shape = RoundedCornerShape(12.dp)
    val (title, body, icon) = when (mode) {
        HoursMode.HOURS -> Triple(R.string.publish_schedule_mode_hours, R.string.publish_schedule_mode_hours_body, R.drawable.ic_schedule)
        HoursMode.ALWAYS_OPEN -> Triple(R.string.publish_schedule_mode_always, R.string.publish_schedule_mode_always_body, R.drawable.ic_forest)
        HoursMode.UNKNOWN -> Triple(R.string.publish_schedule_mode_unknown, R.string.publish_schedule_mode_unknown_body, R.drawable.ic_info)
    }
    val content = if (selected) explora.onPrimaryContainerAccent else scheme.onSurface
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(shape)
            .background(if (selected) scheme.primaryContainer else Color.Transparent)
            .border(1.dp, if (selected) scheme.primary else scheme.outline, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(if (selected) R.drawable.ic_check else icon), null, tint = content, modifier = Modifier.size(20.dp.scaledWithFont()))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                stringResource(title),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = if (selected) FontWeight.W700 else FontWeight.W600),
                color = content,
            )
            Text(
                stringResource(body),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
                color = if (selected) explora.onPrimaryContainerAccent else explora.textSecondary,
            )
        }
    }
}

/**
 * Un tramo: con varios, va en su propia tarjeta con su número («Horario 2») y el botón para quitarlo. Los días que ya
 * tiene otro tramo no se pueden elegir. Si cierra antes de abrir, avisa que es al día siguiente.
 */
@Composable
private fun BlockFields(
    index: Int,
    count: Int,
    block: DraftHours,
    taken: Set<DayOfWeek>,
    showError: (DraftField) -> Boolean,
    focusOn: DraftField?,
    errorFocusRequest: Int,
    callbacks: ScheduleCallbacks,
    onPick: (TimeField) -> Unit,
) {
    val stacked = LocalDensity.current.fontScale > FontScaleThresholds.StackRows
    val daysFocus = remember { FocusRequester() }
    val opensFocus = remember { FocusRequester() }
    val closesFocus = remember { FocusRequester() }
    LaunchedEffect(errorFocusRequest) {
        if (errorFocusRequest == 0) return@LaunchedEffect
        when (focusOn) {
            DraftField.DAYS -> daysFocus.requestFocus()
            DraftField.OPENS -> opensFocus.requestFocus()
            DraftField.CLOSES -> closesFocus.requestFocus()
            else -> Unit
        }
    }
    val shape = RoundedCornerShape(16.dp)
    val card = if (count > 1) {
        Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape).padding(12.dp)
    } else {
        Modifier
    }
    Column(card.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (count > 1) BlockHeader(index, onRemove = { callbacks.onRemoveBlock(index) })
        Section(stringResource(R.string.publish_schedule_days)) {
            DayToggles(block.days, taken, showError(DraftField.DAYS), daysFocus) { callbacks.onDayToggle(index, it) }
        }
        val opens = @Composable { modifier: Modifier ->
            TimeField(
                label = stringResource(R.string.publish_schedule_opens),
                time = block.opens,
                error = if (showError(DraftField.OPENS)) stringResource(R.string.publish_schedule_opens_missing) else null,
                pickLabel = stringResource(R.string.publish_schedule_pick_opens),
                onPick = { onPick(TimeField.OPENS) },
                modifier = modifier.focusRequester(opensFocus),
            )
        }
        val closesError = when {
            !block.timesDiffer -> stringResource(R.string.publish_schedule_same_time)
            showError(DraftField.CLOSES) -> stringResource(R.string.publish_schedule_closes_missing)
            else -> null
        }
        val closes = @Composable { modifier: Modifier ->
            TimeField(
                label = stringResource(R.string.publish_schedule_closes),
                time = block.closes,
                error = closesError,
                pickLabel = stringResource(R.string.publish_schedule_pick_closes),
                onPick = { onPick(TimeField.CLOSES) },
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
        if (block.pastMidnight) Note(R.drawable.ic_schedule, R.string.publish_schedule_past_midnight)
    }
}

/** «Horario 2» y el botón para quitarlo, que el lector dice «Quitar el horario 2». */
@Composable
private fun BlockHeader(index: Int, onRemove: () -> Unit) {
    val number = index + 1
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.publish_schedule_block, number),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.W700),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        IconButton(onClick = onRemove) {
            Icon(
                painterResource(R.drawable.ic_delete),
                contentDescription = stringResource(R.string.publish_schedule_remove_block, number),
                tint = MaterialTheme.exploraColors.iconSecondary,
            )
        }
    }
}

/** Una aclaración junto al campo, sin ser un error: «Cierra al día siguiente.». */
@Composable
private fun Note(@DrawableRes icon: Int, @StringRes text: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(icon), null, tint = MaterialTheme.exploraColors.iconSecondary, modifier = Modifier.size(16.dp.scaledWithFont()))
        Text(stringResource(text), style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp), color = MaterialTheme.exploraColors.textSecondary)
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

/**
 * Días de 48 dp: elegido = relleno, borde y texto en negrita; el lector dice «lunes, seleccionado». Un día de otro tramo
 * se ve apagado, no se puede tocar y el lector dice «en otro horario».
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayToggles(days: Set<DayOfWeek>, taken: Set<DayOfWeek>, error: Boolean, focusRequester: FocusRequester, onToggle: (DayOfWeek) -> Unit) {
    val locale = appLocale()
    val scheme = MaterialTheme.colorScheme
    val explora = MaterialTheme.exploraColors
    val selectedText = stringResource(R.string.publish_schedule_day_selected)
    val notSelectedText = stringResource(R.string.publish_schedule_day_not_selected)
    val takenText = stringResource(R.string.publish_schedule_day_taken)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            week.forEachIndexed { index, day ->
                val selected = day in days
                val enabled = day !in taken
                val shape = RoundedCornerShape(12.dp)
                val content = when {
                    !enabled -> explora.textPlaceholder
                    selected -> explora.onPrimaryContainerAccent
                    else -> scheme.onSurface
                }
                val full = day.getDisplayName(TextStyle.FULL, locale)
                Box(
                    Modifier
                        .then(if (index == 0) Modifier.focusRequester(focusRequester) else Modifier)
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .clip(shape)
                        .background(if (selected) scheme.primaryContainer else Color.Transparent)
                        .border(1.dp, if (!enabled) scheme.outlineVariant else if (selected) scheme.primary else if (error) scheme.error else scheme.outline, shape)
                        .toggleable(value = selected, enabled = enabled, onValueChange = { onToggle(day) })
                        .semantics { stateDescription = if (!enabled) takenText else if (selected) selectedText else notSelectedText }
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        day.getDisplayName(TextStyle.SHORT, locale).removeSuffix(".").replaceFirstChar { it.titlecase(locale) },
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
    error: String?,
    pickLabel: String,
    onPick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val explora = MaterialTheme.exploraColors
    val shape = RoundedCornerShape(12.dp)
    val formats = localeFormats()
    val value = time?.let { DateTimeFormatter.ofPattern(formats.time, formats.locale).format(it) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.W600), color = explora.textSecondary)
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clip(shape)
                .background(scheme.surfaceContainerLowest)
                .border(if (error != null) 2.dp else 1.dp, if (error != null) scheme.error else scheme.outline, shape)
                .clickable(role = Role.Button, onClickLabel = pickLabel, onClick = onPick)
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
                color = if (value == null) explora.textPlaceholder else scheme.onSurface,
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
