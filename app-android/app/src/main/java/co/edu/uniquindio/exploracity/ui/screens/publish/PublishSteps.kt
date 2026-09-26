package co.edu.uniquindio.exploracity.ui.screens.publish

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.edu.uniquindio.exploracity.R
import co.edu.uniquindio.exploracity.domain.model.Category
import co.edu.uniquindio.exploracity.domain.model.CategoryOrigin
import co.edu.uniquindio.exploracity.domain.model.PublicationDraft
import co.edu.uniquindio.exploracity.domain.model.PublicationLimits
import co.edu.uniquindio.exploracity.ui.components.ExploraButton
import co.edu.uniquindio.exploracity.ui.components.ExploraButtonStyle
import co.edu.uniquindio.exploracity.ui.components.ExploraTextField
import co.edu.uniquindio.exploracity.ui.components.iconRes
import co.edu.uniquindio.exploracity.ui.components.initialFocus
import co.edu.uniquindio.exploracity.ui.components.labelRes
import co.edu.uniquindio.exploracity.ui.components.onBlur
import co.edu.uniquindio.exploracity.ui.components.scaledWithFont
import co.edu.uniquindio.exploracity.ui.theme.FontScaleThresholds
import co.edu.uniquindio.exploracity.ui.theme.exploraColors
import co.edu.uniquindio.exploracity.viewmodel.DraftField
import co.edu.uniquindio.exploracity.viewmodel.PublishUiState
import co.edu.uniquindio.exploracity.viewmodel.Suggestion

/**
 * 15 · Título y descripción, con ejemplos en los campos vacíos (15.b) y el aviso del borrador automático. El error de un
 * campo se ve al salir de él o al tocar «Continuar» (21); en ese caso el foco va al primero con error.
 */
@Composable
internal fun BasicsStep(state: PublishUiState, callbacks: PublishCallbacks) {
    val draft = state.draft
    val titleFocus = remember { FocusRequester() }
    val descriptionFocus = remember { FocusRequester() }
    LaunchedEffect(state.errorFocusRequest) {
        if (state.errorFocusRequest == 0) return@LaunchedEffect
        when (state.stepErrors.firstOrNull()) {
            DraftField.TITLE -> titleFocus.requestFocus()
            DraftField.DESCRIPTION -> descriptionFocus.requestFocus()
            else -> Unit
        }
    }

    StepHeading(stringResource(R.string.publish_basics_heading), stringResource(R.string.publish_basics_intro))
    if (state.showErrors && state.stepErrors.isNotEmpty()) ErrorSummary(state.stepErrors)

    val titleState = rememberTextFieldState(draft.title)
    val currentOnTitle by rememberUpdatedState(callbacks.onTitleChange)
    LaunchedEffect(titleState) { snapshotFlow { titleState.text.toString() }.collect { currentOnTitle(it) } }
    val titleLength = draft.title.trim().length
    ExploraTextField(
        state = titleState,
        label = stringResource(R.string.publish_field_title),
        placeholder = stringResource(R.string.publish_title_placeholder),
        supportingText = if (titleState.text.isEmpty()) {
            stringResource(R.string.publish_title_hint_empty)
        } else {
            stringResource(R.string.edit_title_hint, titleState.text.length, PublicationLimits.TITLE_MAX)
        },
        errorMessage = if (state.showTitleError) {
            pluralStringResource(R.plurals.edit_title_error, titleLength, titleLength, PublicationLimits.TITLE_MIN)
        } else {
            null
        },
        inputTransformation = InputTransformation.maxLength(PublicationLimits.TITLE_MAX),
        modifier = Modifier.fillMaxWidth().focusRequester(titleFocus).onBlur(callbacks.onTitleBlur),
    )

    val descriptionState = rememberTextFieldState(draft.description)
    val currentOnDescription by rememberUpdatedState(callbacks.onDescriptionChange)
    LaunchedEffect(descriptionState) { snapshotFlow { descriptionState.text.toString() }.collect { currentOnDescription(it) } }
    ExploraTextField(
        state = descriptionState,
        label = stringResource(R.string.publish_field_description),
        placeholder = stringResource(R.string.publish_description_placeholder),
        supportingText = if (descriptionState.text.isEmpty()) {
            stringResource(R.string.publish_description_hint_empty)
        } else {
            stringResource(R.string.edit_description_hint, PublicationLimits.DESCRIPTION_MIN, descriptionState.text.length, PublicationLimits.DESCRIPTION_MAX)
        },
        errorMessage = if (state.showDescriptionError) {
            pluralStringResource(R.plurals.edit_description_error, draft.descriptionMissing, draft.descriptionMissing)
        } else {
            null
        },
        singleLine = false,
        minLines = 5,
        inputTransformation = InputTransformation.maxLength(PublicationLimits.DESCRIPTION_MAX),
        modifier = Modifier.fillMaxWidth().focusRequester(descriptionFocus).onBlur(callbacks.onDescriptionBlur),
    )
    // 21.a: con la descripción corta, un ejemplo concreto de lo que sirve.
    if (state.showDescriptionError) Note(R.drawable.ic_lightbulb, stringResource(R.string.publish_description_tip))
    // También al corregir una rechazada: ese borrador se guarda aparte (24).
    Note(R.drawable.ic_save, stringResource(R.string.publish_autosave))
}

/**
 * 16 · Categoría con sugerencia automática. La lista está activa desde el primer instante: elegir a mano cancela la
 * sugerencia. La sugerida llega preseleccionada, pero es un radio normal. Filas de 56 dp que crecen con la fuente.
 */
@Composable
internal fun CategoryStep(state: PublishUiState, callbacks: PublishCallbacks) {
    StepHeading(stringResource(R.string.publish_category_heading))
    SuggestionBanner(state.suggestion, callbacks.onRetrySuggestion)
    if (state.showCategoryError) ErrorSummary(listOf(DraftField.CATEGORY))
    val list = stringResource(R.string.publish_category_list)
    Column(
        Modifier.selectableGroup().semantics { contentDescription = list },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Category.entries.forEach { category ->
            CategoryOption(category, state.draft, onSelect = { callbacks.onCategoryChange(category) })
        }
    }
}

/** El aviso de la sugerencia según su estado (16.a, 16.b, 16.c). Sin lenguaje técnico ni mención del modelo. */
@Composable
private fun SuggestionBanner(suggestion: Suggestion, onRetry: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val explora = MaterialTheme.exploraColors
    when (suggestion) {
        Suggestion.Idle -> Unit
        Suggestion.Searching -> Banner(
            container = scheme.tertiaryContainer,
            content = scheme.onTertiaryContainer,
            leading = { CircularProgressIndicator(Modifier.size(18.dp.scaledWithFont()), color = scheme.onTertiaryContainer, strokeWidth = 2.dp) },
            title = stringResource(R.string.publish_suggesting),
            body = stringResource(R.string.publish_suggesting_body),
            spoken = stringResource(R.string.publish_suggesting_spoken),
        )
        is Suggestion.Ready -> Banner(
            container = scheme.tertiaryContainer,
            content = scheme.onTertiaryContainer,
            leading = { BannerIcon(R.drawable.ic_auto_awesome, scheme.onTertiaryContainer) },
            title = stringResource(R.string.publish_suggested_title),
            body = stringResource(R.string.publish_suggested_body, stringResource(suggestion.category.labelRes)),
        )
        is Suggestion.Replaced -> Banner(
            container = scheme.surfaceContainer,
            content = explora.textSecondary,
            leading = { BannerIcon(R.drawable.ic_auto_awesome, explora.iconSecondary) },
            title = stringResource(R.string.publish_suggestion_replaced_title),
            body = stringResource(R.string.publish_suggestion_replaced_body, stringResource(suggestion.suggested.labelRes)),
        )
        Suggestion.NoAnswer -> Banner(
            container = explora.warning.container,
            content = explora.warning.content,
            leading = { BannerIcon(R.drawable.ic_info, explora.warning.content) },
            title = stringResource(R.string.publish_no_answer_title),
            body = stringResource(R.string.publish_no_answer_body),
        ) {
            ExploraButton(
                stringResource(R.string.publish_retry_suggestion),
                onClick = onRetry,
                style = ExploraButtonStyle.TEXT,
                icon = R.drawable.ic_refresh,
            )
        }
        Suggestion.NotFound -> Banner(
            container = scheme.surfaceContainer,
            content = explora.textSecondary,
            leading = { BannerIcon(R.drawable.ic_info, explora.iconSecondary) },
            title = null,
            body = stringResource(R.string.publish_not_found_body),
        )
    }
}

@Composable
internal fun BannerIcon(icon: Int, tint: Color) {
    Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(20.dp.scaledWithFont()))
}

/** Aviso de color con icono, título opcional y acción opcional (16 · sugerencia, 17.b · sin permiso). */
@Composable
internal fun Banner(
    container: Color,
    content: Color,
    leading: @Composable () -> Unit,
    title: String?,
    body: String,
    /** Lo que dice el lector en lugar del texto visible (16.a: «Buscando una categoría»). Se anuncia al cambiar. */
    spoken: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        Modifier.fillMaxWidth().background(container, RoundedCornerShape(12.dp)).padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = if (action != null) 4.dp else 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            if (spoken != null) {
                Modifier.clearAndSetSemantics {
                    contentDescription = spoken
                    liveRegion = LiveRegionMode.Polite
                }
            } else {
                Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
            },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            leading()
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (title != null) Text(title, style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.W700), color = content)
                Text(body, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp), color = content)
            }
        }
        action?.invoke()
    }
}

/**
 * Una categoría como radio. Elegida: primaryContainer, borde de 2 dp, check y la etiqueta de origen («Sugerida ·
 * seleccionada» o «Elegida por ti · seleccionada»): nunca solo color. El lector: «Gastronomía, sugerida
 * automáticamente» y el estado del radio.
 */
@Composable
private fun CategoryOption(category: Category, draft: PublicationDraft, onSelect: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val explora = MaterialTheme.exploraColors
    val selected = draft.category == category
    val suggested = selected && draft.categoryOrigin == CategoryOrigin.SUGGESTED
    val label = stringResource(category.labelRes)
    val spoken = if (suggested) stringResource(R.string.publish_category_suggested_spoken, label) else label
    val shape = RoundedCornerShape(12.dp)
    val content = if (selected) explora.onPrimaryContainerAccent else explora.textSecondary
    val large = LocalDensity.current.fontScale > FontScaleThresholds.StackRows
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = if (large) 80.dp else 56.dp)
            .clip(shape)
            .background(if (selected) scheme.primaryContainer else Color.Transparent)
            .border(if (selected) 2.dp else 1.dp, if (selected) scheme.primary else scheme.outline, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .clearAndSetSemantics { contentDescription = spoken }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(36.dp.scaledWithFont()).background(if (selected) scheme.surfaceContainerLowest else Color.Transparent, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(category.iconRes), contentDescription = null, tint = content, modifier = Modifier.size(20.dp.scaledWithFont()))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = if (selected) FontWeight.W700 else FontWeight.W600), color = content)
            if (selected) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (suggested) Icon(painterResource(R.drawable.ic_auto_awesome), contentDescription = null, tint = content, modifier = Modifier.size(14.dp.scaledWithFont()))
                    Text(
                        stringResource(if (suggested) R.string.publish_category_suggested_selected else R.string.publish_category_chosen_selected),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.W700),
                        color = content,
                    )
                }
            }
        }
        if (selected) Icon(painterResource(R.drawable.ic_check_circle), contentDescription = null, tint = content, modifier = Modifier.size(24.dp.scaledWithFont()))
    }
}

/** Aviso neutro con icono (borrador automático, ejemplo de descripción, reglas de las fotos). */
@Composable
internal fun Note(icon: Int, text: String) {
    val explora = MaterialTheme.exploraColors
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(12.dp)).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = explora.iconSecondary, modifier = Modifier.size(20.dp.scaledWithFont()))
        Text(text, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 19.sp), color = explora.textSecondary, modifier = Modifier.weight(1f))
    }
}

/**
 * 15A · «¿Guardar el borrador?» al cerrar con algo escrito. Foco inicial en «Guardar»; «Descartar» va en rojo y nunca
 * es la acción por defecto.
 */
@Composable
internal fun SaveDraftDialog(draft: PublicationDraft, callbacks: PublishCallbacks) {
    val scheme = MaterialTheme.colorScheme
    val heading = stringResource(R.string.publish_close_title)
    val saveFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { saveFocus.requestFocus() }
    val title = draft.title.trim()
    AlertDialog(
        onDismissRequest = callbacks.onKeepEditing,
        modifier = Modifier.semantics { paneTitle = heading },
        icon = {
            Box(Modifier.size(44.dp).background(scheme.primaryContainer, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_save), null, tint = MaterialTheme.exploraColors.onPrimaryContainerAccent, modifier = Modifier.size(24.dp))
            }
        },
        title = { Text(heading, style = MaterialTheme.typography.titleLarge) },
        text = {
            Text(
                if (title.isEmpty()) stringResource(R.string.publish_close_body_untitled) else stringResource(R.string.publish_close_body, title),
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                color = MaterialTheme.exploraColors.textSecondary,
            )
        },
        confirmButton = {
            ExploraButton(stringResource(R.string.publish_save), onClick = callbacks.onSaveAndClose, modifier = Modifier.initialFocus(saveFocus))
        },
        dismissButton = {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                ExploraButton(stringResource(R.string.publish_discard), onClick = callbacks.onDiscard, style = ExploraButtonStyle.DESTRUCTIVE_TEXT)
                ExploraButton(stringResource(R.string.edit_discard_keep), onClick = callbacks.onKeepEditing, style = ExploraButtonStyle.TEXT)
            }
        },
        containerColor = scheme.surface,
        titleContentColor = scheme.onSurface,
    )
}
