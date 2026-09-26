package co.edu.uniquindio.exploracity.ui.screens.publish

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import co.edu.uniquindio.exploracity.R
import co.edu.uniquindio.exploracity.domain.model.Category
import co.edu.uniquindio.exploracity.domain.model.PublicationDraft
import co.edu.uniquindio.exploracity.domain.model.PublishStep
import co.edu.uniquindio.exploracity.ui.components.EmptyState
import co.edu.uniquindio.exploracity.ui.components.EmptyStateTone
import co.edu.uniquindio.exploracity.ui.components.ExploraButton
import co.edu.uniquindio.exploracity.ui.components.ExploraButtonStyle
import co.edu.uniquindio.exploracity.ui.components.ExploraTopAppBar
import co.edu.uniquindio.exploracity.ui.components.SkeletonBlock
import co.edu.uniquindio.exploracity.ui.components.rememberShimmerBrush
import co.edu.uniquindio.exploracity.ui.components.scaledWithFont
import co.edu.uniquindio.exploracity.ui.theme.ExploraCityTheme
import co.edu.uniquindio.exploracity.ui.theme.FontScaleThresholds
import co.edu.uniquindio.exploracity.ui.theme.Outfit
import co.edu.uniquindio.exploracity.ui.theme.ThemeMode
import co.edu.uniquindio.exploracity.ui.theme.exploraColors
import co.edu.uniquindio.exploracity.viewmodel.DraftField
import co.edu.uniquindio.exploracity.viewmodel.PublishContent
import co.edu.uniquindio.exploracity.viewmodel.PublishExit
import co.edu.uniquindio.exploracity.viewmodel.PublishUiState
import co.edu.uniquindio.exploracity.viewmodel.PublishViewModel
import co.edu.uniquindio.exploracity.viewmodel.Suggestion

/** 15–19 · Formulario de publicación, conectado a su ViewModel. */
@Composable
fun PublishFormRoute(
    onExit: (PublishExit) -> Unit,
    viewModel: PublishViewModel = viewModel(factory = PublishViewModel.factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnExit by rememberUpdatedState(onExit)
    LaunchedEffect(state.exit) {
        val exit = state.exit ?: return@LaunchedEffect
        viewModel.onExitHandled()
        currentOnExit(exit)
    }
    // El gesto de volver retrocede un paso; en el primero, cierra (con 15A si hay algo escrito).
    BackHandler(enabled = state.content == PublishContent.Editing) { viewModel.onBack() }
    PublishFormScreen(
        state = state,
        callbacks = PublishCallbacks(
            onClose = viewModel::onClose,
            onSaveAndClose = viewModel::onSaveAndClose,
            onDiscard = viewModel::onDiscard,
            onKeepEditing = viewModel::onKeepEditing,
            onBack = viewModel::onBack,
            onContinue = viewModel::onContinue,
            onRetry = viewModel::onRetry,
            onTitleChange = viewModel::onTitleChange,
            onDescriptionChange = viewModel::onDescriptionChange,
            onTitleBlur = viewModel::onTitleBlur,
            onDescriptionBlur = viewModel::onDescriptionBlur,
            onCategoryChange = viewModel::onCategoryChange,
            onRetrySuggestion = viewModel::onRetrySuggestion,
        ),
    )
}

class PublishCallbacks(
    val onClose: () -> Unit = {},
    val onSaveAndClose: () -> Unit = {},
    val onDiscard: () -> Unit = {},
    val onKeepEditing: () -> Unit = {},
    val onBack: () -> Unit = {},
    val onContinue: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onTitleChange: (String) -> Unit = {},
    val onDescriptionChange: (String) -> Unit = {},
    val onTitleBlur: () -> Unit = {},
    val onDescriptionBlur: () -> Unit = {},
    val onCategoryChange: (Category) -> Unit = {},
    val onRetrySuggestion: () -> Unit = {},
)

@get:StringRes
internal val PublishStep.labelRes: Int
    get() = when (this) {
        PublishStep.BASICS -> R.string.publish_step_basics
        PublishStep.CATEGORY -> R.string.publish_step_category
        PublishStep.LOCATION -> R.string.publish_step_location
        PublishStep.SCHEDULE -> R.string.publish_step_schedule
        PublishStep.PHOTOS -> R.string.publish_step_photos
    }

/**
 * 15.a · Cerrar (X), «Publicar un lugar» y el paso, «Guardar», el indicador de 5 segmentos, el paso actual y abajo
 * «Atrás» / «Continuar». Con fuente grande la barra de acciones pasa a columna, «Continuar» arriba (16 al 200 %).
 */
@Composable
fun PublishFormScreen(state: PublishUiState, callbacks: PublishCallbacks, modifier: Modifier = Modifier) {
    val step = state.step
    val stepLabel = stringResource(step.labelRes)
    // Con fuente grande la barra no lleva «Guardar» (16 al 200 %): el borrador se guarda solo y cerrar lo ofrece (15A).
    val largeFont = LocalDensity.current.fontScale > FontScaleThresholds.StackRows
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).imePadding()) {
        ExploraTopAppBar(
            title = stringResource(R.string.publish_title),
            subtitle = stringResource(R.string.publish_step, step.number, stepLabel),
            // Lo dice el indicador de pasos, justo debajo.
            subtitleDescription = "",
            onBack = callbacks.onClose,
            closeIcon = true,
            actions = {
                if (!largeFont && state.content == PublishContent.Editing && state.draft.hasContent) {
                    ExploraButton(stringResource(R.string.publish_save), onClick = callbacks.onSaveAndClose, style = ExploraButtonStyle.TEXT)
                }
            },
        )
        when (state.content) {
            PublishContent.Loading -> FormSkeleton()
            PublishContent.Error -> Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = R.drawable.ic_sync_problem,
                    title = stringResource(R.string.rejected_error_title),
                    body = stringResource(R.string.profile_error_body),
                    tone = EmptyStateTone.WARNING,
                ) {
                    ExploraButton(stringResource(R.string.action_retry), onClick = callbacks.onRetry, modifier = Modifier.fillMaxWidth(), icon = R.drawable.ic_refresh)
                }
            }
            PublishContent.Editing -> {
                StepIndicator(step, stepLabel, errors = if (state.showErrors) state.stepErrors.size else 0)
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    when (step) {
                        PublishStep.BASICS -> BasicsStep(state, callbacks)
                        PublishStep.CATEGORY -> CategoryStep(state, callbacks)
                        PublishStep.LOCATION, PublishStep.SCHEDULE, PublishStep.PHOTOS -> ComingStep(stepLabel)
                    }
                }
                ActionBar(isLast = step.next == null, onBack = callbacks.onBack, onContinue = callbacks.onContinue)
            }
        }
    }
    if (state.closeDialog) SaveDraftDialog(state.draft, callbacks)
}

/**
 * Indicador de 5 segmentos: los pasos hechos y el actual en primary. Con errores (21) el actual pasa a rojo con un icono
 * y el lector lo dice. Mantiene su tamaño con fuente grande: no lleva texto.
 */
@Composable
private fun StepIndicator(step: PublishStep, stepLabel: String, errors: Int) {
    val scheme = MaterialTheme.colorScheme
    val base = stringResource(R.string.publish_step_spoken, step.number, stepLabel)
    val spoken = if (errors > 0) "$base, ${pluralStringResource(R.plurals.publish_errors_title, errors, errors)}" else base
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
            .clearAndSetSemantics { contentDescription = spoken },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PublishStep.entries.forEach { segment ->
            val color = when {
                segment == step && errors > 0 -> scheme.error
                segment.number <= step.number -> scheme.primary
                else -> scheme.surfaceContainerHighest
            }
            Box(Modifier.weight(1f).height(6.dp).background(color, CircleShape))
        }
        if (errors > 0) Icon(painterResource(R.drawable.ic_error), contentDescription = null, tint = scheme.error, modifier = Modifier.size(16.dp))
    }
}

/** «Atrás» (1/3) y «Continuar» (2/3). Con fuente grande pasan a columna, «Continuar» arriba. */
@Composable
private fun ActionBar(isLast: Boolean, onBack: () -> Unit, onContinue: () -> Unit) {
    val stacked = LocalDensity.current.fontScale > FontScaleThresholds.StackRows
    val continueText = stringResource(if (isLast) R.string.publish_send else R.string.publish_continue)
    val backText = stringResource(R.string.publish_back)
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest))
        val padding = Modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp)
        if (stacked) {
            Column(padding, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExploraButton(continueText, onClick = onContinue, modifier = Modifier.fillMaxWidth())
                ExploraButton(backText, onClick = onBack, modifier = Modifier.fillMaxWidth(), style = ExploraButtonStyle.TEXT)
            }
        } else {
            Row(padding, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                ExploraButton(backText, onClick = onBack, modifier = Modifier.weight(1f), style = ExploraButtonStyle.TEXT)
                ExploraButton(continueText, onClick = onContinue, modifier = Modifier.weight(2f))
            }
        }
    }
}

/** 21 · Resumen arriba: cuántas cosas faltan y dónde. Se anuncia al aparecer; el foco va al primer campo con error. */
@Composable
internal fun ErrorSummary(errors: List<DraftField>) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    val detail = when {
        DraftField.CATEGORY in errors -> R.string.publish_errors_category
        DraftField.TITLE in errors && DraftField.DESCRIPTION in errors -> R.string.publish_errors_basics
        DraftField.TITLE in errors -> R.string.publish_errors_title_only
        else -> R.string.publish_errors_description_only
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.exploraColors.errorFieldContainer, shape)
            .border(1.dp, scheme.error, shape)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(painterResource(R.drawable.ic_error), contentDescription = null, tint = scheme.error, modifier = Modifier.size(20.dp.scaledWithFont()))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                pluralStringResource(R.plurals.publish_errors_title, errors.size, errors.size),
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.W700),
                color = scheme.error,
            )
            Text(stringResource(detail), style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 19.sp), color = scheme.onSurface)
        }
    }
}

/** Título grande del paso («¿Qué lugar quieres mostrar?») con su explicación opcional. */
@Composable
internal fun StepHeading(text: String, intro: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text,
            style = MaterialTheme.typography.headlineSmall.copy(fontFamily = Outfit, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.W600),
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (intro != null) {
            Text(intro, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp), color = MaterialTheme.exploraColors.iconSecondary)
        }
    }
}

/** Pasos 3 a 5 mientras llegan las partes 2 y 3: se puede recorrer el formulario completo. */
@Composable
private fun ComingStep(stepLabel: String) {
    StepHeading(stepLabel, stringResource(R.string.publish_step_coming))
}

@Composable
private fun FormSkeleton() {
    val brush = rememberShimmerBrush()
    val loading = stringResource(R.string.publish_loading)
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
        SkeletonBlock(brush, Modifier.fillMaxWidth().height(6.dp), CircleShape)
        SkeletonBlock(brush, Modifier.fillMaxWidth(0.7f).height(24.dp))
        SkeletonBlock(brush, Modifier.fillMaxWidth().height(56.dp), RoundedCornerShape(12.dp))
        SkeletonBlock(brush, Modifier.fillMaxWidth().height(140.dp), RoundedCornerShape(12.dp))
    }
}

@Preview(name = "15.a · claro", widthDp = 360, heightDp = 800)
@Composable
private fun BasicsLightPreview() {
    ExploraCityTheme(ThemeMode.LIGHT) {
        PublishFormScreen(
            PublishUiState(
                content = PublishContent.Editing,
                draft = PublicationDraft(title = "Café Las Acacias", description = "Café de barrio con tostión propia y un patio interior lleno de matas."),
            ),
            PublishCallbacks(),
        )
    }
}

@Preview(name = "16.b · oscuro", widthDp = 360, heightDp = 800)
@Composable
private fun CategoryDarkPreview() {
    ExploraCityTheme(ThemeMode.DARK) {
        PublishFormScreen(
            PublishUiState(
                content = PublishContent.Editing,
                draft = PublicationDraft(title = "Café Las Acacias", category = Category.GASTRONOMY, step = PublishStep.CATEGORY),
                suggestion = Suggestion.Ready(Category.GASTRONOMY),
            ),
            PublishCallbacks(),
        )
    }
}
