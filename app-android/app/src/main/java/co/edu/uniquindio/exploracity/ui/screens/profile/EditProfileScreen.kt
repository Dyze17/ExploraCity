package co.edu.uniquindio.exploracity.ui.screens.profile

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import co.edu.uniquindio.exploracity.R
import co.edu.uniquindio.exploracity.domain.model.Author
import co.edu.uniquindio.exploracity.domain.model.ProfileForm
import co.edu.uniquindio.exploracity.domain.model.ProfileLimits
import co.edu.uniquindio.exploracity.domain.model.Residency
import co.edu.uniquindio.exploracity.domain.model.ThemeMode
import co.edu.uniquindio.exploracity.ui.components.DiscardChangesDialog
import co.edu.uniquindio.exploracity.ui.components.EmptyState
import co.edu.uniquindio.exploracity.ui.components.EmptyStateTone
import co.edu.uniquindio.exploracity.ui.components.ExploraButton
import co.edu.uniquindio.exploracity.ui.components.ExploraButtonStyle
import co.edu.uniquindio.exploracity.ui.components.ExploraTextField
import co.edu.uniquindio.exploracity.ui.components.ExploraTopAppBar
import co.edu.uniquindio.exploracity.ui.components.InitialsAvatar
import co.edu.uniquindio.exploracity.ui.components.OptionButton
import co.edu.uniquindio.exploracity.ui.components.SkeletonBlock
import co.edu.uniquindio.exploracity.ui.components.onBlur
import co.edu.uniquindio.exploracity.ui.components.rememberShimmerBrush
import co.edu.uniquindio.exploracity.ui.theme.ExploraCityTheme
import co.edu.uniquindio.exploracity.ui.theme.FontScaleThresholds
import co.edu.uniquindio.exploracity.viewmodel.EditProfileContent
import co.edu.uniquindio.exploracity.viewmodel.EditProfileDone
import co.edu.uniquindio.exploracity.viewmodel.EditProfileUiState
import co.edu.uniquindio.exploracity.viewmodel.EditProfileViewModel
import co.edu.uniquindio.exploracity.viewmodel.ProfileSaveError

/** 28 · Editar perfil, conectada a su ViewModel. Al guardar vuelve al perfil (26), que lo dice. */
@Composable
fun EditProfileRoute(
    onLeave: () -> Unit,
    onSaved: () -> Unit,
    viewModel: EditProfileViewModel = viewModel(factory = EditProfileViewModel.factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnLeave by rememberUpdatedState(onLeave)
    val currentOnSaved by rememberUpdatedState(onSaved)
    LaunchedEffect(state.done) {
        when (state.done) {
            EditProfileDone.SAVED -> currentOnSaved()
            EditProfileDone.LEFT -> currentOnLeave()
            null -> Unit
        }
    }
    // Con cambios, el gesto de volver pregunta antes de perderlos, como la X.
    BackHandler(enabled = state.changed) { viewModel.onBack() }
    // La galería de Android: sin permisos, una sola foto.
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        viewModel.onPhotoPicked(uri?.toString())
    }
    EditProfileScreen(
        state = state,
        callbacks = EditProfileCallbacks(
            onBack = viewModel::onBack,
            onRetry = viewModel::onRetry,
            onSave = viewModel::onSave,
            onSaveErrorShown = viewModel::onSaveErrorShown,
            onChangePhoto = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onRemovePhoto = viewModel::onRemovePhoto,
            onPhotoProblemShown = viewModel::onPhotoProblemShown,
            onNameChange = viewModel::onNameChange,
            onNameBlur = viewModel::onNameBlur,
            onBioChange = viewModel::onBioChange,
            onResidencyChange = viewModel::onResidencyChange,
            onDiscard = viewModel::onDiscard,
            onKeepEditing = viewModel::onKeepEditing,
        ),
    )
}

class EditProfileCallbacks(
    val onBack: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onSave: () -> Unit = {},
    val onSaveErrorShown: () -> Unit = {},
    val onChangePhoto: () -> Unit = {},
    val onRemovePhoto: () -> Unit = {},
    val onPhotoProblemShown: () -> Unit = {},
    val onNameChange: (String) -> Unit = {},
    val onNameBlur: () -> Unit = {},
    val onBioChange: (String) -> Unit = {},
    val onResidencyChange: (Residency) -> Unit = {},
    val onDiscard: () -> Unit = {},
    val onKeepEditing: () -> Unit = {},
)

/**
 * 28.a · Foto, nombre (2–40), «Sobre mí» (hasta 150, con contador), cómo se presenta y el correo, bloqueado con su
 * motivo. Pasarse de un límite se avisa al momento; «Guardar» queda deshabilitado mientras haya un error y dice por qué.
 */
@Composable
fun EditProfileScreen(state: EditProfileUiState, callbacks: EditProfileCallbacks, modifier: Modifier = Modifier) {
    val snackbarHostState = remember { SnackbarHostState() }
    NoticeEffect(state.saveError, state.photoUnreadable, snackbarHostState, callbacks)
    val form = state.form
    val author = state.author

    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxSize()) {
            ExploraTopAppBar(
                title = stringResource(R.string.profile_edit_title),
                onBack = callbacks.onBack,
                closeIcon = true,
                actions = { if (form != null) SaveButton(state, form, callbacks.onSave) },
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (state.content) {
                    EditProfileContent.LOADING -> EditProfileSkeleton()
                    EditProfileContent.LOADED -> if (form != null && author != null) EditProfileForm(author, form, state, callbacks)
                    EditProfileContent.ERROR -> Centered {
                        EmptyState(
                            icon = R.drawable.ic_sync_problem,
                            title = stringResource(R.string.rejected_error_title),
                            body = stringResource(R.string.profile_error_body),
                            tone = EmptyStateTone.WARNING,
                        ) { RetryButton(callbacks.onRetry) }
                    }
                    EditProfileContent.OFFLINE -> Centered {
                        EmptyState(
                            icon = R.drawable.ic_cloud_off,
                            title = stringResource(R.string.offline_title),
                            body = stringResource(R.string.profile_edit_offline_body),
                            tone = EmptyStateTone.WARNING,
                        ) { RetryButton(callbacks.onRetry) }
                    }
                }
            }
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars))
    }

    if (state.discardDialog) {
        DiscardChangesDialog(stringResource(R.string.profile_discard_body), callbacks.onDiscard, callbacks.onKeepEditing)
    }
}

@Composable
private fun EditProfileForm(author: Author, form: ProfileForm, state: EditProfileUiState, callbacks: EditProfileCallbacks) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PhotoBlock(author, form, state.preparingPhoto, callbacks)
        NameField(form, state.showNameError, callbacks)
        BioField(form, state.showBioError, callbacks.onBioChange)
        ResidencyPicker(form.residency, callbacks.onResidencyChange)
        EmailField(state.email)
    }
}

/** Avatar de 88 dp con la foto o las iniciales del nombre que se está escribiendo, «Cambiar foto» y, si hay, «Quitar foto». */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PhotoBlock(author: Author, form: ProfileForm, preparing: Boolean, callbacks: EditProfileCallbacks) {
    val shown = author.copy(name = form.name.ifBlank { author.name })
    val avatarDescription = stringResource(if (form.photo != null) R.string.profile_avatar_photo else R.string.profile_avatar_initials)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // El avatar es decorativo; aquí sí dice si hay foto, porque eso es lo que se edita.
        Box(Modifier.clearAndSetSemantics { contentDescription = avatarDescription }) {
            InitialsAvatar(shown, size = 88.dp, photo = form.photo)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ExploraButton(
                stringResource(if (preparing) R.string.profile_preparing_photo else R.string.profile_change_photo),
                onClick = callbacks.onChangePhoto,
                style = ExploraButtonStyle.SECONDARY,
                icon = R.drawable.ic_photo_camera,
                loading = preparing,
            )
            if (form.photo != null && !preparing) {
                ExploraButton(stringResource(R.string.profile_remove_photo), onClick = callbacks.onRemovePhoto, style = ExploraButtonStyle.TEXT)
            }
        }
    }
}

@Composable
private fun NameField(form: ProfileForm, showError: Boolean, callbacks: EditProfileCallbacks) {
    val textState = rememberTextFieldState(form.name)
    val currentOnChange by rememberUpdatedState(callbacks.onNameChange)
    LaunchedEffect(textState) { snapshotFlow { textState.text.toString() }.collect { currentOnChange(it) } }
    ExploraTextField(
        state = textState,
        label = stringResource(R.string.profile_field_name),
        errorMessage = when {
            !showError -> null
            form.nameExcess > 0 -> pluralStringResource(R.plurals.profile_too_long, form.nameExcess, form.nameExcess)
            else -> pluralStringResource(R.plurals.profile_name_short, form.nameMissing, form.nameMissing)
        },
        modifier = Modifier.fillMaxWidth().onBlur(callbacks.onNameBlur),
    )
}

/** Se puede escribir de más: el contador y el aviso dicen por cuánto (28.a), en lugar de cortar lo escrito. */
@Composable
private fun BioField(form: ProfileForm, showError: Boolean, onChange: (String) -> Unit) {
    val textState = rememberTextFieldState(form.bio)
    val currentOnChange by rememberUpdatedState(onChange)
    LaunchedEffect(textState) { snapshotFlow { textState.text.toString() }.collect { currentOnChange(it) } }
    ExploraTextField(
        state = textState,
        label = stringResource(R.string.profile_field_bio),
        placeholder = stringResource(R.string.profile_bio_placeholder),
        errorMessage = if (showError) pluralStringResource(R.plurals.profile_too_long, form.bioExcess, form.bioExcess) else null,
        counter = stringResource(R.string.profile_bio_counter, textState.text.length, ProfileLimits.BIO_MAX),
        singleLine = false,
        minLines = 3,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** «¿Cómo te presentas?»: De visita o Residente, como en el perfil (26 y 31). Con fuente grande, una debajo de otra. */
@Composable
private fun ResidencyPicker(selected: Residency, onSelect: (Residency) -> Unit) {
    val stacked = LocalDensity.current.fontScale > FontScaleThresholds.StackRows
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.profile_residency_title),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { heading() },
        )
        val options: @Composable (Modifier) -> Unit = { itemModifier ->
            OptionButton(stringResource(R.string.residency_visitor), R.drawable.ic_luggage, selected == Residency.VISITOR, { onSelect(Residency.VISITOR) }, itemModifier)
            OptionButton(stringResource(R.string.residency_resident), R.drawable.ic_home_pin, selected == Residency.RESIDENT, { onSelect(Residency.RESIDENT) }, itemModifier)
        }
        if (stacked) {
            Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) { options(Modifier.fillMaxWidth()) }
        } else {
            Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { options(Modifier.weight(1f)) }
        }
    }
}

/** El correo no se edita aquí: candado y el motivo, que lleva a Ajustes › Cuenta (29). */
@Composable
private fun EmailField(email: String) {
    val textState = rememberTextFieldState(email)
    ExploraTextField(
        state = textState,
        label = stringResource(R.string.profile_field_email),
        enabled = false,
        disabledReason = stringResource(R.string.profile_email_locked),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** «Guardar» de la barra: deshabilitado sin cambios o con un error, y dice por qué al recibir foco. */
@Composable
private fun SaveButton(state: EditProfileUiState, form: ProfileForm, onSave: () -> Unit) {
    val nameWrong = form.nameMissing > 0 || form.nameExcess > 0
    val reason = when {
        state.preparingPhoto -> stringResource(R.string.profile_save_preparing)
        !state.changed -> stringResource(R.string.profile_save_no_changes)
        nameWrong && form.bioExcess > 0 -> stringResource(R.string.profile_save_fix_both)
        nameWrong -> stringResource(R.string.profile_save_fix_name)
        form.bioExcess > 0 -> stringResource(R.string.profile_save_fix_bio)
        else -> null
    }
    ExploraButton(
        stringResource(R.string.profile_edit_save),
        onClick = onSave,
        style = ExploraButtonStyle.TEXT,
        enabled = state.canSave || state.saving,
        disabledReason = reason,
        loading = state.saving,
    )
}

@Composable
private fun NoticeEffect(error: ProfileSaveError?, photoUnreadable: Boolean, hostState: SnackbarHostState, callbacks: EditProfileCallbacks) {
    val currentCallbacks by rememberUpdatedState(callbacks)
    val offline = stringResource(R.string.profile_save_offline)
    val failed = stringResource(R.string.profile_save_failed)
    val unreadable = stringResource(R.string.profile_photo_unreadable)
    LaunchedEffect(error) {
        val text = when (error) {
            null -> return@LaunchedEffect
            ProfileSaveError.OFFLINE -> offline
            ProfileSaveError.FAILED -> failed
        }
        // Se consume al terminar: si se marcara antes, el cambio de clave cancelaría este efecto y el aviso.
        hostState.showSnackbar(text, withDismissAction = true, duration = SnackbarDuration.Long)
        currentCallbacks.onSaveErrorShown()
    }
    LaunchedEffect(photoUnreadable) {
        if (!photoUnreadable) return@LaunchedEffect
        hostState.showSnackbar(unreadable, withDismissAction = true)
        currentCallbacks.onPhotoProblemShown()
    }
}

/** Mientras carga: el avatar y los campos, con un solo anuncio. */
@Composable
private fun EditProfileSkeleton() {
    val brush = rememberShimmerBrush()
    val loading = stringResource(R.string.own_profile_loading)
    Column(
        Modifier.fillMaxSize().padding(16.dp).clearAndSetSemantics { contentDescription = loading },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SkeletonBlock(brush, Modifier.size(88.dp), CircleShape)
        SkeletonBlock(brush, Modifier.size(width = 150.dp, height = 48.dp), RoundedCornerShape(12.dp))
        repeat(3) { SkeletonBlock(brush, Modifier.fillMaxWidth().height(if (it == 1) 110.dp else 56.dp), RoundedCornerShape(12.dp)) }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars).verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun RetryButton(onRetry: () -> Unit) {
    ExploraButton(stringResource(R.string.action_retry), onClick = onRetry, modifier = Modifier.fillMaxWidth(), icon = R.drawable.ic_refresh)
}

private val previewAuthor = Author("ana-rios", "Ana Ríos", 340)
private val previewForm = ProfileForm(
    name = "Ana Ríos",
    bio = "Camino Bogotá buscando cafés con patio y miradores poco conocidos, siempre con mi cámara y una libreta para anotar lo que encuentro en cada esquina del centro",
    residency = Residency.RESIDENT,
)
private val previewState = EditProfileUiState(
    content = EditProfileContent.LOADED,
    author = previewAuthor,
    original = previewForm.copy(bio = ""),
    form = previewForm,
    email = "ana.rios@correo.com",
)

@Preview(name = "28.a · error en un campo · claro", widthDp = 360, heightDp = 900)
@Composable
private fun EditProfileLightPreview() {
    ExploraCityTheme(ThemeMode.LIGHT) { EditProfileScreen(previewState, EditProfileCallbacks()) }
}

@Preview(name = "28.a · oscuro", widthDp = 360, heightDp = 900)
@Composable
private fun EditProfileDarkPreview() {
    ExploraCityTheme(ThemeMode.DARK) { EditProfileScreen(previewState.copy(form = previewForm.copy(bio = "Busco cafés con patio.")), EditProfileCallbacks()) }
}

@Preview(name = "28 · fuente 200 %", widthDp = 360, heightDp = 1800, fontScale = 2f)
@Composable
private fun EditProfileLargeFontPreview() {
    ExploraCityTheme(ThemeMode.LIGHT) { EditProfileScreen(previewState, EditProfileCallbacks()) }
}

@Preview(name = "28 · cargando", widthDp = 360, heightDp = 800)
@Composable
private fun EditProfileLoadingPreview() {
    ExploraCityTheme(ThemeMode.LIGHT) { EditProfileScreen(EditProfileUiState(), EditProfileCallbacks()) }
}
