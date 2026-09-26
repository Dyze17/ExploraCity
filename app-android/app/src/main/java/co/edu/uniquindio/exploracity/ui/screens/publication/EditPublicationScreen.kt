package co.edu.uniquindio.exploracity.ui.screens.publication

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.focus.onFocusChanged
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import co.edu.uniquindio.exploracity.R
import co.edu.uniquindio.exploracity.domain.model.Category
import co.edu.uniquindio.exploracity.domain.model.GeoPoint
import co.edu.uniquindio.exploracity.domain.model.OwnPublication
import co.edu.uniquindio.exploracity.domain.model.PublicationChanges
import co.edu.uniquindio.exploracity.domain.model.PublicationLimits
import co.edu.uniquindio.exploracity.domain.model.PublicationStatus
import co.edu.uniquindio.exploracity.ui.components.EmptyState
import co.edu.uniquindio.exploracity.ui.components.EmptyStateTone
import co.edu.uniquindio.exploracity.ui.components.ExploraButton
import co.edu.uniquindio.exploracity.ui.components.ExploraButtonStyle
import co.edu.uniquindio.exploracity.ui.components.ExploraTextField
import co.edu.uniquindio.exploracity.ui.components.ExploraTopAppBar
import co.edu.uniquindio.exploracity.ui.components.SkeletonBlock
import co.edu.uniquindio.exploracity.ui.components.colors
import co.edu.uniquindio.exploracity.ui.components.iconRes
import co.edu.uniquindio.exploracity.ui.components.initialFocus
import co.edu.uniquindio.exploracity.ui.components.labelRes
import co.edu.uniquindio.exploracity.ui.components.rememberShimmerBrush
import co.edu.uniquindio.exploracity.ui.components.scaledWithFont
import co.edu.uniquindio.exploracity.ui.theme.ExploraCityTheme
import co.edu.uniquindio.exploracity.ui.theme.ThemeMode
import co.edu.uniquindio.exploracity.ui.theme.exploraColors
import co.edu.uniquindio.exploracity.viewmodel.Done
import co.edu.uniquindio.exploracity.viewmodel.EditContent
import co.edu.uniquindio.exploracity.viewmodel.EditPublicationUiState
import co.edu.uniquindio.exploracity.viewmodel.EditPublicationViewModel
import co.edu.uniquindio.exploracity.viewmodel.PublicationMessage
import co.edu.uniquindio.exploracity.viewmodel.SaveError
import java.time.Instant

/** 23 · Editar publicación, conectada a su ViewModel. Al guardar o eliminar vuelve a Mis publicaciones (22) con el aviso. */
@Composable
fun EditPublicationRoute(
    onLeave: () -> Unit,
    onDone: (PublicationMessage) -> Unit,
    viewModel: EditPublicationViewModel = viewModel(factory = EditPublicationViewModel.factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnLeave by rememberUpdatedState(onLeave)
    val currentOnDone by rememberUpdatedState(onDone)
    LaunchedEffect(state.done) {
        val done = state.done ?: return@LaunchedEffect
        viewModel.onDoneHandled()
        when (done) {
            Done.Left -> currentOnLeave()
            is Done.WithMessage -> currentOnDone(done.message)
        }
    }
    // Con cambios, el gesto de volver pregunta antes de perderlos.
    BackHandler(enabled = state.changed) { viewModel.onBack() }
    EditPublicationScreen(
        state = state,
        callbacks = EditCallbacks(
            onBack = viewModel::onBack,
            onRetry = viewModel::onRetry,
            onSave = viewModel::onSave,
            onSaveErrorShown = viewModel::onSaveErrorShown,
            onTitleChange = viewModel::onTitleChange,
            onDescriptionChange = viewModel::onDescriptionChange,
            onCategoryChange = viewModel::onCategoryChange,
            onTitleBlur = viewModel::onTitleBlur,
            onDescriptionBlur = viewModel::onDescriptionBlur,
            onDiscard = viewModel::onDiscard,
            onKeepEditing = viewModel::onKeepEditing,
            onOpenDelete = viewModel::onOpenDelete,
            onDismissDelete = viewModel::onDismissDelete,
            onConfirmDelete = viewModel::onConfirmDelete,
        ),
    )
}

class EditCallbacks(
    val onBack: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onSave: () -> Unit = {},
    val onSaveErrorShown: () -> Unit = {},
    val onTitleChange: (String) -> Unit = {},
    val onDescriptionChange: (String) -> Unit = {},
    val onCategoryChange: (Category) -> Unit = {},
    val onTitleBlur: () -> Unit = {},
    val onDescriptionBlur: () -> Unit = {},
    val onDiscard: () -> Unit = {},
    val onKeepEditing: () -> Unit = {},
    val onOpenDelete: () -> Unit = {},
    val onDismissDelete: () -> Unit = {},
    val onConfirmDelete: () -> Unit = {},
)

/**
 * 23.a · Título, categoría y descripción con el aviso de que guardar la devuelve a verificación, y «Eliminar
 * publicación» al final. «Guardar» se habilita solo con cambios válidos y, deshabilitado, dice por qué.
 */
@Composable
fun EditPublicationScreen(state: EditPublicationUiState, callbacks: EditCallbacks, modifier: Modifier = Modifier) {
    val snackbarHostState = remember { SnackbarHostState() }
    SaveErrorEffect(state.saveError, snackbarHostState, callbacks.onSaveErrorShown)
    val form = state.form

    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxSize()) {
            ExploraTopAppBar(
                title = stringResource(R.string.edit_title),
                onBack = callbacks.onBack,
                actions = { if (form != null && state.original != null) SaveButton(state, form, callbacks.onSave) },
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                val original = state.original
                when (state.content) {
                    EditContent.Loading -> EditSkeleton()
                    is EditContent.Loaded -> if (original != null && form != null) EditForm(original, form, state, callbacks)
                    EditContent.NotFound -> Centered {
                        EmptyState(
                            icon = R.drawable.ic_delete,
                            title = stringResource(R.string.rejected_not_found_title),
                            body = stringResource(R.string.rejected_not_found_body),
                        ) { BackButton(callbacks.onBack) }
                    }
                    EditContent.NotEditable -> Centered {
                        EmptyState(
                            icon = R.drawable.ic_lock,
                            title = stringResource(R.string.edit_not_editable_title),
                            body = stringResource(R.string.edit_not_editable_body),
                        ) { BackButton(callbacks.onBack) }
                    }
                    EditContent.Error -> Centered {
                        EmptyState(
                            icon = R.drawable.ic_sync_problem,
                            title = stringResource(R.string.rejected_error_title),
                            body = stringResource(R.string.profile_error_body),
                            tone = EmptyStateTone.WARNING,
                        ) { RetryButton(callbacks.onRetry) }
                    }
                    EditContent.Offline -> Centered {
                        EmptyState(
                            icon = R.drawable.ic_cloud_off,
                            title = stringResource(R.string.offline_title),
                            body = stringResource(R.string.edit_offline_body),
                            tone = EmptyStateTone.WARNING,
                        ) { RetryButton(callbacks.onRetry) }
                    }
                }
            }
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars))
    }

    val original = state.original
    if (state.discardDialog) DiscardDialog(callbacks.onDiscard, callbacks.onKeepEditing)
    val delete = state.delete
    if (original != null && delete != null) DeletePublicationDialog(original, delete, callbacks.onConfirmDelete, callbacks.onDismissDelete)
}

/** «Guardar» de la barra: deshabilitado sin cambios o con un error, y dice por qué al recibir foco. */
@Composable
private fun SaveButton(state: EditPublicationUiState, form: PublicationChanges, onSave: () -> Unit) {
    val reason = when {
        !state.changed -> stringResource(R.string.edit_save_no_changes)
        form.titleMissing > 0 && form.descriptionMissing > 0 -> stringResource(R.string.edit_save_fix_both)
        form.titleMissing > 0 -> stringResource(R.string.edit_save_fix_title)
        form.descriptionMissing > 0 -> stringResource(R.string.edit_save_fix_description)
        else -> null
    }
    ExploraButton(
        stringResource(R.string.edit_save),
        onClick = onSave,
        style = ExploraButtonStyle.TEXT,
        enabled = state.canSave || state.saving,
        disabledReason = reason,
        loading = state.saving,
    )
}

@Composable
private fun SaveErrorEffect(error: SaveError?, hostState: SnackbarHostState, onShown: () -> Unit) {
    val currentOnShown by rememberUpdatedState(onShown)
    val offline = stringResource(R.string.edit_save_offline)
    val failed = stringResource(R.string.edit_save_failed)
    LaunchedEffect(error) {
        val text = when (error) {
            null -> return@LaunchedEffect
            SaveError.OFFLINE -> offline
            SaveError.FAILED -> failed
        }
        // Se consume al terminar: si se marcara antes, el cambio de clave cancelaría este efecto y el aviso.
        hostState.showSnackbar(text, withDismissAction = true, duration = SnackbarDuration.Long)
        currentOnShown()
    }
}

@Composable
private fun EditForm(original: OwnPublication, form: PublicationChanges, state: EditPublicationUiState, callbacks: EditCallbacks) {
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp + bottom),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Notice(original.status)
        TitleField(form, state.showTitleError, callbacks)
        CategoryField(form.category, callbacks.onCategoryChange)
        DescriptionField(form, state.showDescriptionError, callbacks)
        ExploraButton(
            stringResource(R.string.rejected_delete),
            onClick = callbacks.onOpenDelete,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            style = ExploraButtonStyle.DESTRUCTIVE,
            icon = R.drawable.ic_delete,
        )
    }
}

/** Aviso ámbar de 23.a; a una pendiente no le aplica «vuelve a verificación», porque ya está en ella. */
@Composable
private fun Notice(status: PublicationStatus) {
    val warning = MaterialTheme.exploraColors.warning
    Row(
        Modifier.fillMaxWidth().background(warning.container, MaterialTheme.shapes.medium).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(painterResource(R.drawable.ic_info), contentDescription = null, tint = warning.content, modifier = Modifier.size(20.dp.scaledWithFont()))
        Text(
            stringResource(if (status == PublicationStatus.PENDING) R.string.edit_notice_pending else R.string.edit_notice_verified),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 19.sp),
            color = warning.content,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun TitleField(form: PublicationChanges, showError: Boolean, callbacks: EditCallbacks) {
    val textState = rememberTextFieldState(form.title)
    val currentOnChange by rememberUpdatedState(callbacks.onTitleChange)
    LaunchedEffect(textState) { snapshotFlow { textState.text.toString() }.collect { currentOnChange(it) } }
    ExploraTextField(
        state = textState,
        label = stringResource(R.string.edit_field_title),
        supportingText = stringResource(R.string.edit_title_hint, textState.text.length, PublicationLimits.TITLE_MAX),
        errorMessage = if (showError) {
            pluralStringResource(R.plurals.edit_title_error, form.titleLength, form.titleLength, PublicationLimits.TITLE_MIN)
        } else {
            null
        },
        inputTransformation = InputTransformation.maxLength(PublicationLimits.TITLE_MAX),
        modifier = Modifier.fillMaxWidth().onBlur(callbacks.onTitleBlur),
    )
}

@Composable
private fun DescriptionField(form: PublicationChanges, showError: Boolean, callbacks: EditCallbacks) {
    val textState = rememberTextFieldState(form.description)
    val currentOnChange by rememberUpdatedState(callbacks.onDescriptionChange)
    LaunchedEffect(textState) { snapshotFlow { textState.text.toString() }.collect { currentOnChange(it) } }
    ExploraTextField(
        state = textState,
        label = stringResource(R.string.edit_field_description),
        supportingText = stringResource(
            R.string.edit_description_hint,
            PublicationLimits.DESCRIPTION_MIN,
            textState.text.length,
            PublicationLimits.DESCRIPTION_MAX,
        ),
        errorMessage = if (showError) {
            pluralStringResource(R.plurals.edit_description_error, form.descriptionMissing, form.descriptionMissing)
        } else {
            null
        },
        singleLine = false,
        minLines = 4,
        inputTransformation = InputTransformation.maxLength(PublicationLimits.DESCRIPTION_MAX),
        modifier = Modifier.fillMaxWidth().onBlur(callbacks.onDescriptionBlur),
    )
}

/** Avisa cuando el campo pierde el foco (no al entrar): ahí se muestra su error (15.b). */
@Composable
private fun Modifier.onBlur(onBlur: () -> Unit): Modifier {
    var focused by remember { mutableStateOf(false) }
    return onFocusChanged { state ->
        if (focused && !state.isFocused) onBlur()
        focused = state.isFocused
    }
}

/**
 * Categoría con su icono y un menú con las cinco; se ve como los demás campos (etiqueta encima, 56 dp, radio 12). La
 * sugerencia de IA es del paso 2 (16): al editar se elige a mano.
 */
@Composable
private fun CategoryField(category: Category, onChange: (Category) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val explora = MaterialTheme.exploraColors
    var expanded by remember { mutableStateOf(false) }
    val label = stringResource(R.string.edit_field_category)
    val categoryLabel = stringResource(category.labelRes)
    val spoken = stringResource(R.string.edit_category_spoken, categoryLabel)
    val changeLabel = stringResource(R.string.edit_category_change)
    val shape = MaterialTheme.shapes.medium
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant, modifier = Modifier.clearAndSetSemantics {})
        Box {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clip(shape)
                    .background(scheme.surface)
                    .border(1.dp, scheme.outline, shape)
                    .clickable(role = Role.DropdownList, onClickLabel = changeLabel) { expanded = true }
                    .clearAndSetSemantics { contentDescription = spoken }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(category.iconRes), contentDescription = null, tint = category.colors.content, modifier = Modifier.size(20.dp.scaledWithFont()))
                Text(categoryLabel, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurface, modifier = Modifier.weight(1f))
                Icon(painterResource(R.drawable.ic_expand_more), contentDescription = null, tint = explora.iconSecondary, modifier = Modifier.size(22.dp.scaledWithFont()))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, containerColor = scheme.surfaceContainerLowest) {
                Category.entries.forEach { option ->
                    val selected = option == category
                    DropdownMenuItem(
                        text = { Text(stringResource(option.labelRes), style = MaterialTheme.typography.bodyLarge) },
                        onClick = {
                            expanded = false
                            onChange(option)
                        },
                        leadingIcon = {
                            Icon(painterResource(option.iconRes), contentDescription = null, tint = option.colors.content, modifier = Modifier.size(22.dp.scaledWithFont()))
                        },
                        trailingIcon = if (selected) {
                            { Icon(painterResource(R.drawable.ic_check), contentDescription = stringResource(R.string.state_selected), modifier = Modifier.size(22.dp.scaledWithFont())) }
                        } else {
                            null
                        },
                    )
                }
            }
        }
    }
}

/** «¿Descartar los cambios?»: «Descartar» en rojo y nunca por defecto; el foco va a «Seguir editando». */
@Composable
private fun DiscardDialog(onDiscard: () -> Unit, onKeepEditing: () -> Unit) {
    val heading = stringResource(R.string.edit_discard_title)
    val keepFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { keepFocus.requestFocus() }
    AlertDialog(
        onDismissRequest = onKeepEditing,
        modifier = Modifier.semantics { paneTitle = heading },
        title = { Text(heading, style = MaterialTheme.typography.titleLarge) },
        text = {
            Text(
                stringResource(R.string.edit_discard_body),
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                color = MaterialTheme.exploraColors.textSecondary,
            )
        },
        confirmButton = {
            ExploraButton(stringResource(R.string.edit_discard_confirm), onClick = onDiscard, style = ExploraButtonStyle.DESTRUCTIVE)
        },
        dismissButton = {
            ExploraButton(
                stringResource(R.string.edit_discard_keep),
                onClick = onKeepEditing,
                modifier = Modifier.initialFocus(keepFocus),
                style = ExploraButtonStyle.TEXT,
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun RetryButton(onRetry: () -> Unit) {
    ExploraButton(stringResource(R.string.action_retry), onClick = onRetry, modifier = Modifier.fillMaxWidth(), icon = R.drawable.ic_refresh)
}

@Composable
private fun BackButton(onBack: () -> Unit) {
    ExploraButton(stringResource(R.string.navigate_back), onClick = onBack, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars).verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** Mientras carga: el aviso y los tres campos, con un solo anuncio. */
@Composable
private fun EditSkeleton() {
    val brush = rememberShimmerBrush()
    val loading = stringResource(R.string.rejected_loading)
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clearAndSetSemantics {
                contentDescription = loading
                liveRegion = LiveRegionMode.Polite
            },
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SkeletonBlock(brush, Modifier.fillMaxWidth().height(64.dp), MaterialTheme.shapes.medium)
        listOf(56, 56, 120).forEach { height ->
            SkeletonBlock(brush, Modifier.fillMaxWidth(0.25f).height(12.dp))
            SkeletonBlock(brush, Modifier.fillMaxWidth().height(height.dp), RoundedCornerShape(12.dp))
        }
    }
}

private val previewPublication = OwnPublication(
    id = "cafe-las-acacias",
    title = "Café Las Acacias",
    category = Category.GASTRONOMY,
    status = PublicationStatus.VERIFIED,
    location = GeoPoint(4.6383, -74.0655),
    photos = 3,
    submittedAt = Instant.now(),
    description = "Café de barrio con tostión propia y patio interior.",
    votes = 48,
    comments = 12,
    pointsEarned = 15,
)

@Preview(name = "23.a · claro", widthDp = 360, heightDp = 800)
@Composable
private fun EditLightPreview() {
    ExploraCityTheme(ThemeMode.LIGHT) {
        EditPublicationScreen(
            EditPublicationUiState(EditContent.Loaded(previewPublication), form = PublicationChanges.of(previewPublication)),
            EditCallbacks(),
        )
    }
}

@Preview(name = "23 · error · oscuro", widthDp = 360, heightDp = 800)
@Composable
private fun EditErrorDarkPreview() {
    ExploraCityTheme(ThemeMode.DARK) {
        EditPublicationScreen(
            EditPublicationUiState(
                EditContent.Loaded(previewPublication),
                form = PublicationChanges("Café", Category.GASTRONOMY, "Rico"),
                titleTouched = true,
                descriptionTouched = true,
            ),
            EditCallbacks(),
        )
    }
}
