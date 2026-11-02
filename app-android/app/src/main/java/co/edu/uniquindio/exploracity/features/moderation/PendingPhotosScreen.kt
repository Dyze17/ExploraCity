package co.edu.uniquindio.exploracity.features.moderation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import co.edu.uniquindio.exploracity.R
import co.edu.uniquindio.exploracity.core.ui.components.BadgeSize
import co.edu.uniquindio.exploracity.core.ui.components.CategoryTag
import co.edu.uniquindio.exploracity.core.ui.components.EmptyState
import co.edu.uniquindio.exploracity.core.ui.components.EmptyStateTone
import co.edu.uniquindio.exploracity.core.ui.components.ExploraButton
import co.edu.uniquindio.exploracity.core.ui.components.ExploraButtonStyle
import co.edu.uniquindio.exploracity.core.ui.components.ExploraSnackbarHost
import co.edu.uniquindio.exploracity.core.ui.components.ExploraTopAppBar
import co.edu.uniquindio.exploracity.core.ui.components.PhotoViewer
import co.edu.uniquindio.exploracity.core.ui.components.RemotePhoto
import co.edu.uniquindio.exploracity.core.ui.components.StatusBadge
import co.edu.uniquindio.exploracity.core.ui.components.ViewerPhoto
import co.edu.uniquindio.exploracity.core.ui.components.initialFocus
import co.edu.uniquindio.exploracity.core.ui.components.labelRes
import co.edu.uniquindio.exploracity.core.ui.components.relativeTimeText
import co.edu.uniquindio.exploracity.core.ui.components.rememberNow
import co.edu.uniquindio.exploracity.core.ui.theme.ExploraCityTheme
import co.edu.uniquindio.exploracity.core.ui.theme.ExploraElevation
import co.edu.uniquindio.exploracity.core.ui.theme.exploraColors
import co.edu.uniquindio.exploracity.core.ui.theme.exploraShadow
import co.edu.uniquindio.exploracity.domain.model.Author
import co.edu.uniquindio.exploracity.domain.model.Category
import co.edu.uniquindio.exploracity.domain.model.PendingPhoto
import co.edu.uniquindio.exploracity.domain.model.PendingPhotoPlace
import co.edu.uniquindio.exploracity.domain.model.PhotoRejectionReason
import co.edu.uniquindio.exploracity.domain.model.PublicationStatus
import co.edu.uniquindio.exploracity.domain.model.ThemeMode
import java.time.Instant

/** v1.4.0 · «Fotos por revisar», conectada a su ViewModel. Tocar el nombre del lugar abre su detalle (13). */
@Composable
fun PendingPhotosRoute(
    onBack: () -> Unit,
    onOpenPlace: (String) -> Unit,
    viewModel: PendingPhotosViewModel = viewModel(factory = PendingPhotosViewModel.factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    PendingPhotosScreen(
        state = state,
        callbacks = PendingPhotosCallbacks(
            onBack = onBack,
            onRetry = viewModel::onRetry,
            onOpenPlace = onOpenPlace,
            onApprove = viewModel::onApprove,
            onAskReject = viewModel::onAskReject,
            onRejectReasonChange = viewModel::onRejectReasonChange,
            onCancelReject = viewModel::onCancelReject,
            onConfirmReject = viewModel::onConfirmReject,
            onMessageShown = viewModel::onMessageShown,
        ),
    )
}

class PendingPhotosCallbacks(
    val onBack: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onOpenPlace: (String) -> Unit = {},
    val onApprove: (PendingPhoto) -> Unit = {},
    val onAskReject: (PendingPhoto) -> Unit = {},
    val onRejectReasonChange: (PhotoRejectionReason) -> Unit = {},
    val onCancelReject: () -> Unit = {},
    val onConfirmReject: () -> Unit = {},
    val onMessageShown: () -> Unit = {},
)

/**
 * v1.4.0 · «Fotos por revisar» · Sin lienzo (D1): cada foto agregada, grande y tocable a pantalla completa, junto a las
 * que ya tiene el lugar para comparar, quién la agregó y cuándo, con «Rechazar» a la izquierda y «Publicar» a la derecha,
 * como en 33.
 */
@Composable
fun PendingPhotosScreen(state: PendingPhotosUiState, callbacks: PendingPhotosCallbacks, modifier: Modifier = Modifier) {
    val snackbarHostState = remember { SnackbarHostState() }
    MessageEffect(state.message, snackbarHostState, callbacks.onMessageShown)
    var viewer by rememberSaveable { mutableStateOf<String?>(null) }
    val items = (state.content as? PendingPhotosContent.Loaded)?.items
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        ExploraTopAppBar(
            title = stringResource(R.string.photos_review_title),
            subtitle = items?.takeIf { it.isNotEmpty() }?.let { pluralStringResource(R.plurals.photos_review_count, it.size, it.size) },
            onBack = callbacks.onBack,
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (val content = state.content) {
                PendingPhotosContent.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                PendingPhotosContent.Offline -> ModerationCentered {
                    EmptyState(
                        icon = R.drawable.ic_cloud_off,
                        title = stringResource(R.string.offline_title),
                        body = stringResource(R.string.photos_review_offline_body),
                        tone = EmptyStateTone.WARNING,
                    ) { ModerationRetryButton(callbacks.onRetry) }
                }
                PendingPhotosContent.Error -> ModerationCentered {
                    EmptyState(
                        icon = R.drawable.ic_sync_problem,
                        title = stringResource(R.string.photos_review_error_title),
                        body = stringResource(R.string.reset_error_body),
                        tone = EmptyStateTone.WARNING,
                    ) { ModerationRetryButton(callbacks.onRetry) }
                }
                is PendingPhotosContent.Loaded -> if (content.items.isEmpty()) {
                    ModerationCentered {
                        EmptyState(
                            icon = R.drawable.ic_photo_library,
                            title = stringResource(R.string.photos_review_empty_title),
                            body = stringResource(R.string.photos_review_empty_body),
                        )
                    }
                } else {
                    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp + bottom),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(content.items, key = { it.id }) { photo ->
                            PhotoCard(photo, deciding = state.deciding, callbacks = callbacks, onOpen = { viewer = photo.id })
                        }
                    }
                }
            }
            ExploraSnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
        }
    }
    items?.firstOrNull { it.id == viewer }?.let { photo ->
        // La agregada primero y después las que ya tiene el lugar, para pasarlas con el dedo.
        val all = listOf(ViewerPhoto(photo.url, stringResource(R.string.photos_review_photo, photo.place.title))) + photo.place.photoUrls.map { ViewerPhoto(it) }
        PhotoViewer(all, startIndex = 0, onDismiss = { viewer = null })
    }
    state.rejecting?.let { rejecting -> RejectDialog(rejecting, callbacks) }
}

/** Una foto agregada: grande, el lugar (con su categoría y estado), quién la agregó y cuándo, lo que ya tiene y las dos decisiones. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PhotoCard(photo: PendingPhoto, deciding: String?, callbacks: PendingPhotosCallbacks, onOpen: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val explora = MaterialTheme.exploraColors
    val shape = MaterialTheme.shapes.medium
    val now by rememberNow()
    val small = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp)
    val description = stringResource(R.string.photos_review_photo, photo.place.title)
    val openAction = stringResource(R.string.review_photo_open)
    Column(
        Modifier
            .fillMaxWidth()
            .exploraShadow(ExploraElevation.Card, shape, explora.shadow)
            .clip(shape)
            .background(scheme.surfaceContainerLowest)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(scheme.surfaceContainerHigh)
                .clickable(onClickLabel = openAction, onClick = onOpen)
                .clearAndSetSemantics {
                    contentDescription = description
                    role = Role.Image
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_photo_camera), null, tint = explora.textPlaceholder, modifier = Modifier.size(40.dp))
            RemotePhoto(photo.url)
        }
        Text(
            photo.place.title,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
            color = scheme.tertiary,
            modifier = Modifier.heightIn(min = 32.dp).clickable(role = Role.Button) { callbacks.onOpenPlace(photo.place.id) },
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            CategoryTag(photo.place.category)
            StatusBadge(photo.place.status, size = BadgeSize.SMALL)
        }
        Text(
            stringResource(
                R.string.photos_review_from,
                photo.contributor?.name ?: stringResource(R.string.deleted_user),
                relativeTimeText(photo.submittedAt, now),
            ),
            style = small,
            color = explora.textSecondary,
        )
        if (photo.place.photoUrls.isNotEmpty()) {
            Text(pluralStringResource(R.plurals.photos_review_place_has, photo.place.photoUrls.size, photo.place.photoUrls.size), style = small, color = explora.textSecondary)
            Row(Modifier.clearAndSetSemantics { }, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                photo.place.photoUrls.forEach { url ->
                    Box(Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(scheme.surfaceContainerHigh)) { RemotePhoto(url) }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val busy = deciding != null
            ExploraButton(
                stringResource(R.string.photos_review_reject),
                onClick = { callbacks.onAskReject(photo) },
                modifier = Modifier.weight(1f),
                style = ExploraButtonStyle.SECONDARY,
                enabled = !busy,
            )
            ExploraButton(
                stringResource(R.string.photos_review_approve),
                onClick = { callbacks.onApprove(photo) },
                modifier = Modifier.weight(1f),
                enabled = !busy || deciding == photo.id,
                loading = deciding == photo.id,
            )
        }
    }
}

/** «¿Por qué no se publica?»: el motivo es obligatorio y ninguno viene marcado; el foco va a «Cancelar». */
@Composable
private fun RejectDialog(state: PhotoRejectState, callbacks: PendingPhotosCallbacks) {
    val title = stringResource(R.string.photos_review_reject_title)
    val cancelFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { cancelFocus.requestFocus() }
    val reasons = stringResource(R.string.photos_review_reasons)
    AlertDialog(
        onDismissRequest = callbacks.onCancelReject,
        modifier = Modifier.semantics { paneTitle = title },
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.photos_review_reject_body, state.photo.contributor?.name ?: stringResource(R.string.deleted_user)),
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                    color = MaterialTheme.exploraColors.textSecondary,
                )
                Column(Modifier.selectableGroup().semantics { contentDescription = reasons }) {
                    PhotoRejectionReason.entries.forEach { reason ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .selectable(selected = state.reason == reason, role = Role.RadioButton) { callbacks.onRejectReasonChange(reason) }
                                .padding(end = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = state.reason == reason,
                                onClick = null,
                                colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary, unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant),
                            )
                            Text(stringResource(reason.labelRes), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        },
        confirmButton = {
            ExploraButton(
                stringResource(R.string.photos_review_reject),
                onClick = callbacks.onConfirmReject,
                style = ExploraButtonStyle.DESTRUCTIVE,
                enabled = state.reason != null,
                disabledReason = stringResource(R.string.photos_review_choose_reason),
            )
        },
        dismissButton = {
            ExploraButton(
                stringResource(R.string.photos_review_cancel),
                onClick = callbacks.onCancelReject,
                modifier = Modifier.initialFocus(cancelFocus),
                style = ExploraButtonStyle.TEXT,
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun MessageEffect(message: PhotoReviewMessage?, hostState: SnackbarHostState, onShown: () -> Unit) {
    val currentOnShown by rememberUpdatedState(onShown)
    val text = message?.let {
        stringResource(
            when (it) {
                PhotoReviewMessage.APPROVED -> R.string.photos_review_approved
                PhotoReviewMessage.REJECTED -> R.string.photos_review_rejected
                PhotoReviewMessage.ALREADY_REVIEWED -> R.string.photos_review_already
                PhotoReviewMessage.PLACE_FULL -> R.string.photos_review_full
                PhotoReviewMessage.FAILED -> R.string.photos_review_failed
                PhotoReviewMessage.OFFLINE -> R.string.photos_review_offline
            },
        )
    }
    LaunchedEffect(message) {
        if (text == null) return@LaunchedEffect
        hostState.showSnackbar(text, withDismissAction = true)
        currentOnShown()
    }
}

private val previewPhotos = listOf(
    PendingPhoto(
        "foto-1", "", Instant.now().minusSeconds(7_200), Author("laura", "Laura Gómez", 120),
        PendingPhotoPlace("mirador", "Mirador de la Secreta", Category.NATURE, PublicationStatus.VERIFIED, "Ana Ríos", listOf("", "")),
    ),
)

@Preview(name = "Fotos por revisar · claro", widthDp = 360, heightDp = 800)
@Composable
private fun PendingPhotosPreview() {
    ExploraCityTheme(ThemeMode.LIGHT) {
        PendingPhotosScreen(PendingPhotosUiState(PendingPhotosContent.Loaded(previewPhotos)), PendingPhotosCallbacks())
    }
}

@Preview(name = "Fotos por revisar · oscuro", widthDp = 360, heightDp = 800)
@Composable
private fun PendingPhotosDarkPreview() {
    ExploraCityTheme(ThemeMode.DARK) {
        PendingPhotosScreen(PendingPhotosUiState(PendingPhotosContent.Loaded(previewPhotos)), PendingPhotosCallbacks())
    }
}
