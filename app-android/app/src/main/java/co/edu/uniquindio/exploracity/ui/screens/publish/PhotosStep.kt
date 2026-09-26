package co.edu.uniquindio.exploracity.ui.screens.publish

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.edu.uniquindio.exploracity.R
import co.edu.uniquindio.exploracity.domain.model.DraftPhoto
import co.edu.uniquindio.exploracity.domain.model.PhotoRules
import co.edu.uniquindio.exploracity.domain.model.PublicationStatus
import co.edu.uniquindio.exploracity.ui.components.ExploraButton
import co.edu.uniquindio.exploracity.ui.components.ExploraButtonStyle
import co.edu.uniquindio.exploracity.ui.components.PhotoThumbnail
import co.edu.uniquindio.exploracity.ui.components.colors
import co.edu.uniquindio.exploracity.ui.components.dashedBorder
import co.edu.uniquindio.exploracity.ui.components.scaledWithFont
import co.edu.uniquindio.exploracity.ui.theme.ExploraElevation
import co.edu.uniquindio.exploracity.ui.theme.FontScaleThresholds
import co.edu.uniquindio.exploracity.ui.theme.exploraColors
import co.edu.uniquindio.exploracity.ui.theme.exploraShadow
import co.edu.uniquindio.exploracity.viewmodel.DraftField
import co.edu.uniquindio.exploracity.viewmodel.PhotoProblem
import co.edu.uniquindio.exploracity.viewmodel.PhotoUpload
import co.edu.uniquindio.exploracity.viewmodel.PublishUiState
import co.edu.uniquindio.exploracity.viewmodel.SendError

/**
 * 19 · Fotografías: de 1 a 5, la primera es la portada. Cada foto dice su estado con icono y texto (subida, subiendo
 * con %, error recuperable) y el lector oye el progreso cada 25 %. Con una foto subida ya se puede enviar.
 */
@Composable
internal fun PhotosStep(state: PublishUiState, callbacks: PublishCallbacks) {
    val draft = state.draft
    val cameraFocus = remember { FocusRequester() }
    LaunchedEffect(state.errorFocusRequest) {
        if (state.errorFocusRequest != 0 && state.stepErrors.firstOrNull() == DraftField.PHOTOS) cameraFocus.requestFocus()
    }
    StepHeading(stringResource(R.string.publish_photos_heading), stringResource(R.string.publish_photos_intro))
    if (state.showPhotosError) ErrorSummary(listOf(DraftField.PHOTOS))
    state.sendError?.let { SendErrorBanner(it) }
    state.photoProblem?.let { ProblemNote(it) }
    if (draft.photos.isEmpty() && state.preparingPhotos == 0) {
        EmptyPicker(callbacks, cameraFocus)
        Note(R.drawable.ic_info, stringResource(R.string.publish_photos_rules))
    } else {
        Text(
            pluralStringResource(R.plurals.publish_photos_count, draft.photos.size.coerceAtLeast(1), draft.photos.size.coerceAtLeast(1)),
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.W700),
            color = MaterialTheme.exploraColors.textSecondary,
        )
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            draft.photos.forEachIndexed { index, photo ->
                PhotoRow(photo, index, draft.photos.size, state.uploads[photo.id], callbacks)
            }
            repeat(state.preparingPhotos) { PreparingRow() }
        }
        if (draft.photosLeft > 0) AddMore(draft.photosLeft, callbacks) else Note(R.drawable.ic_info, stringResource(R.string.publish_photos_full, PhotoRules.MAX))
    }
}

/** 19.a · Sin fotos: «Toma una foto ahora o elige una de tu galería», con los dos botones. */
@Composable
private fun EmptyPicker(callbacks: PublishCallbacks, cameraFocus: FocusRequester) {
    val scheme = MaterialTheme.colorScheme
    val stacked = LocalDensity.current.fontScale > FontScaleThresholds.StackRows
    Column(
        Modifier
            .fillMaxWidth()
            .background(scheme.surfaceContainerLow, RoundedCornerShape(16.dp))
            .dashedBorder(2.dp, scheme.outline, 16.dp)
            .padding(horizontal = 16.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(72.dp).background(scheme.primaryContainer, RoundedCornerShape(22.dp)), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_add_a_photo), null, tint = MaterialTheme.exploraColors.onPrimaryContainerAccent, modifier = Modifier.size(34.dp))
        }
        Text(
            stringResource(R.string.publish_photos_empty),
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
            color = MaterialTheme.exploraColors.textSecondary,
        )
        val camera = @Composable { modifier: Modifier ->
            ExploraButton(stringResource(R.string.publish_photos_camera), onClick = callbacks.onTakePhoto, modifier = modifier.focusRequester(cameraFocus), icon = R.drawable.ic_photo_camera)
        }
        val gallery = @Composable { modifier: Modifier ->
            ExploraButton(stringResource(R.string.publish_photos_gallery), onClick = callbacks.onPickPhotos, modifier = modifier, style = ExploraButtonStyle.SECONDARY, icon = R.drawable.ic_photo_library)
        }
        if (stacked) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                camera(Modifier.fillMaxWidth())
                gallery(Modifier.fillMaxWidth())
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                camera(Modifier.weight(1f))
                gallery(Modifier.weight(1f))
            }
        }
    }
}

/**
 * Una foto: miniatura, nombre, «Portada» en la primera y su estado. Quitarla (o cancelar su subida) está siempre a
 * mano; «Reintentar» aparece si no pudo subir. El lector oye «Foto 1 de 3, patio-interior.jpg, portada, subida».
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PhotoRow(photo: DraftPhoto, index: Int, total: Int, upload: PhotoUpload?, callbacks: PublishCallbacks) {
    val scheme = MaterialTheme.colorScheme
    val explora = MaterialTheme.exploraColors
    val shape = RoundedCornerShape(16.dp)
    val failed = upload == PhotoUpload.Failed
    val cover = index == 0
    val status = when {
        photo.uploaded -> stringResource(R.string.publish_photo_uploaded)
        upload is PhotoUpload.Uploading -> stringResource(R.string.publish_photo_uploading, upload.percent)
        failed -> stringResource(R.string.publish_photo_failed)
        else -> stringResource(R.string.publish_photo_waiting)
    }
    // El progreso se anuncia cada 25 % (README 19): la descripción solo cambia en esos saltos.
    val spokenStatus = if (upload is PhotoUpload.Uploading) stringResource(R.string.publish_photo_uploading, upload.percent / 25 * 25) else status
    val spoken = listOfNotNull(
        stringResource(R.string.publish_photo_position, index + 1, total),
        photo.name,
        stringResource(R.string.publish_photo_cover).lowercase().takeIf { cover },
    ).joinToString(", ")
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (failed) Modifier.border(1.dp, scheme.error, shape) else Modifier.exploraShadow(ExploraElevation.Card, shape, explora.shadow))
            .background(if (failed) explora.errorFieldContainer else scheme.surfaceContainerLowest, shape)
            .padding(start = 12.dp, top = 12.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PhotoThumbnail(photo.path)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FlowRow(
                Modifier.clearAndSetSemantics { contentDescription = spoken },
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Text(photo.name, style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.W700), color = scheme.onSurface)
                if (cover) {
                    Text(
                        stringResource(R.string.publish_photo_cover),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.W700),
                        color = explora.onPrimaryContainerAccent,
                        modifier = Modifier.background(scheme.primaryContainer, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            Column(
                Modifier.clearAndSetSemantics {
                    contentDescription = spokenStatus
                    liveRegion = LiveRegionMode.Polite
                },
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                when {
                    photo.uploaded -> StatusLine(R.drawable.ic_check_circle, status, PublicationStatus.VERIFIED.colors.content)
                    upload is PhotoUpload.Uploading -> {
                        ProgressBar(upload.percent)
                        Text(status, style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.W600), color = explora.textSecondary)
                    }
                    failed -> StatusLine(R.drawable.ic_error, status, scheme.error)
                    else -> StatusLine(R.drawable.ic_schedule, status, explora.textSecondary)
                }
            }
            if (failed) {
                ExploraButton(stringResource(R.string.action_retry), onClick = { callbacks.onRetryPhoto(photo.id) }, style = ExploraButtonStyle.DESTRUCTIVE_TEXT, icon = R.drawable.ic_refresh)
            }
        }
        val uploading = upload is PhotoUpload.Uploading
        IconButton(onClick = { callbacks.onRemovePhoto(photo.id) }) {
            Icon(
                painterResource(if (uploading) R.drawable.ic_close else R.drawable.ic_delete),
                contentDescription = stringResource(if (uploading) R.string.publish_photo_cancel else R.string.publish_photo_remove, photo.name),
                tint = explora.iconSecondary,
            )
        }
    }
}

/** Una foto elegida que se está comprimiendo, antes de tener miniatura. */
@Composable
private fun PreparingRow() {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    val text = stringResource(R.string.publish_photo_preparing)
    Row(
        Modifier
            .fillMaxWidth()
            .exploraShadow(ExploraElevation.Card, shape, MaterialTheme.exploraColors.shadow)
            .background(scheme.surfaceContainerLowest, shape)
            .clearAndSetSemantics {
                contentDescription = text
                liveRegion = LiveRegionMode.Polite
            }
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(72.dp).background(scheme.surfaceContainerHigh, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(24.dp), color = scheme.primary, strokeWidth = 2.dp)
        }
        Text(text, style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.W600), color = MaterialTheme.exploraColors.textSecondary)
    }
}

@Composable
private fun StatusLine(icon: Int, text: String, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Icon(painterResource(icon), null, tint = color, modifier = Modifier.size(15.dp.scaledWithFont()).padding(top = 1.dp))
        Text(text, style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.W600), color = color)
    }
}

/** Barra de 8 dp del lienzo 19.b; la cifra va en el texto de al lado. */
@Composable
private fun ProgressBar(percent: Int) {
    Box(Modifier.fillMaxWidth().height(8.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape)) {
        Box(Modifier.fillMaxWidth(percent / 100f).height(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
    }
}

/** «Agregar otra foto (2 disponibles)»: abre un menú con la cámara y la galería. */
@Composable
private fun AddMore(left: Int, callbacks: PublishCallbacks) {
    var open by remember { mutableStateOf(false) }
    Box {
        ExploraButton(
            pluralStringResource(R.plurals.publish_photos_add_more, left, left),
            onClick = { open = true },
            modifier = Modifier.fillMaxWidth(),
            style = ExploraButtonStyle.SECONDARY,
            icon = R.drawable.ic_add,
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.publish_photos_take)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_photo_camera), null) },
                onClick = {
                    open = false
                    callbacks.onTakePhoto()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.publish_photos_choose)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_photo_library), null) },
                onClick = {
                    open = false
                    callbacks.onPickPhotos()
                },
            )
        }
    }
}

@Composable
private fun SendErrorBanner(error: SendError) {
    val warning = MaterialTheme.exploraColors.warning
    Banner(
        container = warning.container,
        content = warning.content,
        leading = { BannerIcon(R.drawable.ic_sync_problem, warning.content) },
        title = stringResource(R.string.publish_send_error_title),
        body = stringResource(
            when (error) {
                SendError.NO_PHOTO_UPLOADED -> R.string.publish_send_error_photos
                SendError.FAILED -> R.string.publish_send_error_failed
            },
        ),
    )
}

@Composable
private fun ProblemNote(problem: PhotoProblem) {
    val warning = MaterialTheme.exploraColors.warning
    Banner(
        container = warning.container,
        content = warning.content,
        leading = { BannerIcon(R.drawable.ic_info, warning.content) },
        title = null,
        body = stringResource(
            when (problem) {
                PhotoProblem.UNREADABLE -> R.string.publish_photos_unreadable
                PhotoProblem.NO_CAMERA -> R.string.publish_photos_no_camera
            },
        ),
    )
}
