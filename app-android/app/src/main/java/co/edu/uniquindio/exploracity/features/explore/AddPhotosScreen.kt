package co.edu.uniquindio.exploracity.features.explore

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import co.edu.uniquindio.exploracity.R
import co.edu.uniquindio.exploracity.core.ui.components.DiscardChangesDialog
import co.edu.uniquindio.exploracity.core.ui.components.ExploraButton
import co.edu.uniquindio.exploracity.core.ui.components.ExploraSnackbarHost
import co.edu.uniquindio.exploracity.core.ui.components.ExploraTopAppBar
import co.edu.uniquindio.exploracity.core.ui.theme.ExploraCityTheme
import co.edu.uniquindio.exploracity.core.ui.theme.exploraColors
import co.edu.uniquindio.exploracity.domain.model.ThemeMode
import co.edu.uniquindio.exploracity.features.publish.PhotoCallbacks
import co.edu.uniquindio.exploracity.features.publish.PhotosSection
import co.edu.uniquindio.exploracity.features.publish.rememberPhotoAccess

/** v1.4.0 · Agregar fotos, conectada a su ViewModel. [onSent] vuelve al detalle con el aviso; [onLeave], sin él. */
@Composable
fun AddPhotosRoute(
    onSent: () -> Unit,
    onLeave: () -> Unit,
    viewModel: AddPhotosViewModel = viewModel(factory = AddPhotosViewModel.factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnSent by rememberUpdatedState(onSent)
    val currentOnLeave by rememberUpdatedState(onLeave)
    LaunchedEffect(state.done) {
        when (state.done) {
            true -> currentOnSent()
            false -> currentOnLeave()
            null -> Unit
        }
    }
    BackHandler(enabled = state.done == null) { viewModel.onBack() }
    val access = rememberPhotoAccess(
        photosLeft = viewModel.photosLeft,
        onCameraShot = viewModel::onCameraShot,
        onCameraResult = viewModel::onCameraResult,
        onCameraUnavailable = viewModel::onCameraUnavailable,
        onPicked = viewModel::onGalleryPicked,
    )
    AddPhotosScreen(
        placeTitle = viewModel.placeTitle,
        slots = viewModel.slots,
        state = state,
        photos = PhotoCallbacks(
            onTakePhoto = access.takePhoto,
            onPickPhotos = access.pickPhotos,
            onRetry = viewModel::onRetryPhoto,
            onRemove = viewModel::onRemovePhoto,
        ),
        onBack = viewModel::onBack,
        onSend = viewModel::onSend,
        onErrorShown = viewModel::onErrorShown,
        onDiscard = viewModel::onDiscard,
        onKeepEditing = viewModel::onKeepEditing,
    )
}

/**
 * v1.4.0 · Sin lienzo: el nombre del lugar, cuántas caben y que un moderador las revisa antes de que se vean; las fotos
 * con su estado, como en 19 pero sin portada, y «Enviar fotos» fijo abajo. Si no salen, el aviso dice por qué.
 */
@Composable
fun AddPhotosScreen(
    placeTitle: String,
    slots: Int,
    state: AddPhotosUiState,
    photos: PhotoCallbacks,
    onBack: () -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    onErrorShown: () -> Unit = {},
    onDiscard: () -> Unit = {},
    onKeepEditing: () -> Unit = {},
) {
    val snackbarHostState = remember { SnackbarHostState() }
    ErrorEffect(state.error, snackbarHostState, onErrorShown)
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        ExploraTopAppBar(title = stringResource(R.string.add_photos_title), subtitle = placeTitle, onBack = onBack)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    pluralStringResource(R.plurals.add_photos_intro, slots, slots, placeTitle),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 22.sp),
                    color = MaterialTheme.exploraColors.textSecondary,
                )
                PhotosSection(state.photos, state.photoStatus, photos, max = slots, withCover = false)
            }
            ExploraSnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
        }
        Box(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(16.dp)) {
            ExploraButton(
                stringResource(R.string.add_photos_send),
                onClick = onSend,
                modifier = Modifier.fillMaxWidth(),
                enabled = state.canSend,
                disabledReason = stringResource(if (state.photos.isEmpty()) R.string.add_photos_need_one else R.string.add_photos_wait_upload),
                loading = state.sending,
                icon = R.drawable.ic_add_a_photo,
            )
        }
    }
    if (state.confirmDiscard) {
        DiscardChangesDialog(body = stringResource(R.string.add_photos_discard_body), onDiscard = onDiscard, onKeepEditing = onKeepEditing)
    }
}

@Composable
private fun ErrorEffect(error: AddPhotosError?, hostState: SnackbarHostState, onShown: () -> Unit) {
    val currentOnShown by rememberUpdatedState(onShown)
    val text = error?.let {
        stringResource(
            when (it) {
                AddPhotosError.FULL -> R.string.add_photos_full
                AddPhotosError.OFFLINE -> R.string.add_photos_offline
                AddPhotosError.FAILED -> R.string.add_photos_failed
            },
        )
    }
    LaunchedEffect(error) {
        if (text == null) return@LaunchedEffect
        hostState.showSnackbar(text, withDismissAction = true, duration = SnackbarDuration.Long)
        currentOnShown()
    }
}

@Preview(name = "Agregar fotos · claro", widthDp = 360, heightDp = 640)
@Composable
private fun AddPhotosPreview() {
    ExploraCityTheme(ThemeMode.LIGHT) {
        AddPhotosScreen("Mirador de la Secreta", slots = 3, state = AddPhotosUiState(), photos = PhotoCallbacks(), onBack = {}, onSend = {})
    }
}
