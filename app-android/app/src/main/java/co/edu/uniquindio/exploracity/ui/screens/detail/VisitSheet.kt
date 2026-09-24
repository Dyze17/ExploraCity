package co.edu.uniquindio.exploracity.ui.screens.detail

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.edu.uniquindio.exploracity.R
import co.edu.uniquindio.exploracity.domain.model.VisitExperience
import co.edu.uniquindio.exploracity.ui.components.ExploraButton
import co.edu.uniquindio.exploracity.ui.components.ExploraButtonStyle
import co.edu.uniquindio.exploracity.ui.components.ExploraCheckbox
import co.edu.uniquindio.exploracity.ui.components.ExploraTextField
import co.edu.uniquindio.exploracity.ui.components.NoNavigationBarScrim
import co.edu.uniquindio.exploracity.ui.components.SheetHandle
import co.edu.uniquindio.exploracity.ui.components.scaledWithFont
import co.edu.uniquindio.exploracity.ui.theme.ExploraCityTheme
import co.edu.uniquindio.exploracity.ui.theme.FontScaleThresholds
import co.edu.uniquindio.exploracity.ui.theme.Outfit
import co.edu.uniquindio.exploracity.ui.theme.ScrimAlpha
import co.edu.uniquindio.exploracity.ui.theme.ThemeMode
import co.edu.uniquindio.exploracity.ui.theme.exploraColors
import co.edu.uniquindio.exploracity.viewmodel.VisitSheetState

/**
 * 14.b · Hoja «Marcar como visitado». Todo es opcional salvo la marca: no bloquea. Modal, con el foco inicial en
 * el título y hasta el 90 % del alto (el formulario se desplaza; los botones quedan fijos). Está abierta mientras
 * [sheet] no sea null; al cerrarse desde fuera se anima la salida con el último estado.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisitSheet(
    placeTitle: String,
    sheet: VisitSheetState?,
    onDraftChange: (VisitExperience) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var lastShown by remember { mutableStateOf<VisitSheetState?>(null) }
    LaunchedEffect(sheet) {
        if (sheet != null) lastShown = sheet
    }
    LaunchedEffect(sheet == null) {
        if (sheet == null && lastShown != null) {
            sheetState.hide()
            lastShown = null
        }
    }
    val shown = sheet ?: lastShown ?: return
    val maxHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() * 0.9f }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = ScrimAlpha.Sheet),
        dragHandle = null,
    ) {
        VisitSheetContent(placeTitle, shown, onDraftChange, onConfirm, onDismiss, Modifier.heightIn(max = maxHeight))
    }
}

@Composable
private fun VisitSheetContent(
    placeTitle: String,
    sheet: VisitSheetState,
    onDraftChange: (VisitExperience) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val titleFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { titleFocus.requestFocus() }
    NoNavigationBarScrim()
    val stackRows = LocalDensity.current.fontScale > FontScaleThresholds.StackRows
    val draft = sheet.draft
    val currentDraft by rememberUpdatedState(draft)
    val currentOnDraftChange by rememberUpdatedState(onDraftChange)

    // El campo lleva su propio estado de texto; cada cambio se copia al borrador del ViewModel (sobrevive al cierre).
    val textState = rememberTextFieldState(draft.text)
    LaunchedEffect(textState) {
        snapshotFlow { textState.text.toString() }.collect { text ->
            if (text != currentDraft.text) currentOnDraftChange(currentDraft.copy(text = text))
        }
    }

    Column(modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 20.dp)) {
        SheetHandle(Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(16.dp))
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(R.string.visit_title, placeTitle),
                    style = MaterialTheme.typography.headlineSmall.copy(fontFamily = Outfit, fontWeight = FontWeight.W600, lineHeight = 28.sp),
                    modifier = Modifier.focusRequester(titleFocus).focusable().semantics { heading() },
                )
                Text(
                    stringResource(R.string.visit_subtitle),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 19.sp),
                    color = MaterialTheme.exploraColors.iconSecondary,
                )
            }
            Recommend(draft.recommends, stackRows) { onDraftChange(draft.copy(recommends = it)) }
            ExploraTextField(
                state = textState,
                label = stringResource(R.string.visit_experience),
                supportingText = stringResource(R.string.visit_counter, textState.text.length, VisitExperience.MAX_LENGTH),
                singleLine = false,
                inputTransformation = InputTransformation.maxLength(VisitExperience.MAX_LENGTH),
                modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
            )
            ShowName(draft.showName) { onDraftChange(draft.copy(showName = it)) }
        }
        Spacer(Modifier.height(20.dp))
        Buttons(sheet.sending, stackRows, onConfirm, onCancel)
    }
}

/** «¿Lo recomiendas?» Sí / No: elección opcional; tocar la elegida la quita. */
@Composable
private fun Recommend(recommends: Boolean?, stackRows: Boolean, onChange: (Boolean?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.visit_recommend),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.W700),
            color = MaterialTheme.exploraColors.textSecondary,
            modifier = Modifier.semantics { heading() },
        )
        val status = MaterialTheme.exploraColors.status
        val options = @Composable { itemModifier: Modifier ->
            RecommendOption(
                label = stringResource(R.string.visit_yes),
                icon = R.drawable.ic_thumb_up,
                selected = recommends == true,
                selectedColors = status.verified.container to status.verified.content,
                onClick = { onChange(if (recommends == true) null else true) },
                modifier = itemModifier,
            )
            RecommendOption(
                label = stringResource(R.string.visit_no),
                icon = R.drawable.ic_thumb_down,
                selected = recommends == false,
                selectedColors = status.rejected.container to status.rejected.content,
                onClick = { onChange(if (recommends == false) null else false) },
                modifier = itemModifier,
            )
        }
        if (stackRows) {
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) { options(Modifier.fillMaxWidth()) }
        } else {
            Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { options(Modifier.weight(1f)) }
        }
    }
}

/** Elegida: color de estado (verificada / rechazada) + borde + check que sustituye al icono; nunca solo color. */
@Composable
private fun RecommendOption(
    label: String,
    @DrawableRes icon: Int,
    selected: Boolean,
    selectedColors: Pair<Color, Color>,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val shape = MaterialTheme.shapes.medium
    val (container, content) = if (selected) selectedColors else MaterialTheme.colorScheme.surface to MaterialTheme.exploraColors.textSecondary
    Row(
        modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .background(container, shape)
            .border(1.dp, if (selected) content else MaterialTheme.colorScheme.outline, shape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(if (selected) R.drawable.ic_check else icon), null, tint = content, modifier = Modifier.size(18.dp.scaledWithFont()))
        Text(label, style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (selected) FontWeight.W700 else FontWeight.W600), color = content)
    }
}

@Composable
private fun ShowName(checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange)
            .heightIn(min = 48.dp)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ExploraCheckbox(checked)
        Text(
            stringResource(R.string.visit_show_name),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 19.sp),
            color = MaterialTheme.exploraColors.textSecondary,
            modifier = Modifier.weight(1f),
        )
    }
}

/** «Cancelar» (1/3) y «Marcar visitado» (2/3). Con fuente grande pasan a columna, la acción principal arriba. */
@Composable
private fun Buttons(sending: Boolean, stackRows: Boolean, onConfirm: () -> Unit, onCancel: () -> Unit) {
    val confirm = stringResource(if (sending) R.string.visit_saving else R.string.visit_confirm)
    val cancel = stringResource(R.string.visit_cancel)
    if (stackRows) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ExploraButton(confirm, onClick = onConfirm, modifier = Modifier.fillMaxWidth(), loading = sending, icon = R.drawable.ic_flag)
            ExploraButton(cancel, onClick = onCancel, modifier = Modifier.fillMaxWidth(), style = ExploraButtonStyle.TEXT)
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            ExploraButton(cancel, onClick = onCancel, modifier = Modifier.weight(1f), style = ExploraButtonStyle.TEXT)
            ExploraButton(confirm, onClick = onConfirm, modifier = Modifier.weight(2f), loading = sending, icon = R.drawable.ic_flag)
        }
    }
}

@Preview(name = "14.b · claro", widthDp = 360)
@Composable
private fun VisitSheetLightPreview() {
    ExploraCityTheme(ThemeMode.LIGHT) {
        Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
            VisitSheetContent(
                "Café Las Acacias",
                VisitSheetState(VisitExperience(recommends = true, text = "Fui un martes a las 8, había mesa en el patio y el pan de queso salía del horno.")),
                {}, {}, {},
            )
        }
    }
}

@Preview(name = "14.b · oscuro · fuente 200 %", widthDp = 360, fontScale = 2f)
@Composable
private fun VisitSheetDarkPreview() {
    ExploraCityTheme(ThemeMode.DARK) {
        Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
            VisitSheetContent("Café Las Acacias", VisitSheetState(), {}, {}, {})
        }
    }
}
