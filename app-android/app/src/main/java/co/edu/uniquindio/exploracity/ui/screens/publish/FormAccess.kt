package co.edu.uniquindio.exploracity.ui.screens.publish

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import co.edu.uniquindio.exploracity.util.LocationPurpose
import co.edu.uniquindio.exploracity.util.rememberLocationPermissionRequester
import co.edu.uniquindio.exploracity.util.shouldShowLocationRationale

// Lo que el formulario (15–19) y la edición (23) piden fuera de la app: el permiso de ubicación, la cámara y la galería.

/**
 * 17 · Permiso de ubicación del mapa del pin. [useMyLocation] lo pide (o responde al momento si ya está); [allow] es el
 * botón del aviso de 17.b. [canAskAgain]: si Android aún muestra su diálogo; si no, el aviso lleva a Ajustes. Se revisa
 * al negarlo y al volver a la app.
 */
class PinLocationAccess(
    val useMyLocation: () -> Unit,
    val allow: () -> Unit,
    val isGranted: Boolean,
    val canAskAgain: Boolean,
)

@Composable
internal fun rememberPinLocationAccess(onGranted: () -> Unit, onDenied: () -> Unit): PinLocationAccess {
    val activity = LocalActivity.current
    val currentOnGranted by rememberUpdatedState(onGranted)
    val currentOnDenied by rememberUpdatedState(onDenied)
    var canAskAgain by remember { mutableStateOf(true) }
    val requester = rememberLocationPermissionRequester { purpose, granted ->
        if (purpose != LocationPurpose.PLACE_PIN) return@rememberLocationPermissionRequester
        if (granted) {
            currentOnGranted()
        } else {
            canAskAgain = activity?.shouldShowLocationRationale() == true
            currentOnDenied()
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { canAskAgain = activity?.shouldShowLocationRationale() == true }
    return PinLocationAccess(
        useMyLocation = { requester.request(LocationPurpose.PLACE_PIN) },
        allow = { requester.allow(LocationPurpose.PLACE_PIN) },
        isGranted = requester.isGranted,
        canAskAgain = canAskAgain,
    )
}

/** 19 · Cámara (la app de cámara del teléfono, sin pedir permiso) y galería (el selector de fotos de Android). */
class PhotoAccess(val takePhoto: () -> Unit, val pickPhotos: () -> Unit)

/**
 * [onCameraShot] da dónde escribirá la foto la cámara (null si ya hay 5); [onCameraResult] recibe si se tomó. La galería
 * deja elegir como máximo [photosLeft].
 */
@Composable
internal fun rememberPhotoAccess(
    photosLeft: Int,
    onCameraShot: () -> String?,
    onCameraResult: (Boolean) -> Unit,
    onCameraUnavailable: () -> Unit,
    onPicked: (List<String>) -> Unit,
): PhotoAccess {
    val currentOnPicked by rememberUpdatedState(onPicked)
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture(), onCameraResult)
    val pickMany = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(photosLeft.coerceAtLeast(2))) { uris ->
        currentOnPicked(uris.map(Uri::toString))
    }
    val pickOne = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { currentOnPicked(listOf(it.toString())) }
    }
    return PhotoAccess(
        takePhoto = {
            onCameraShot()?.let { shot ->
                try {
                    camera.launch(shot.toUri())
                } catch (e: ActivityNotFoundException) {
                    onCameraUnavailable()
                }
            }
        },
        pickPhotos = {
            val request = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            when {
                photosLeft >= 2 -> pickMany.launch(request)
                photosLeft == 1 -> pickOne.launch(request)
            }
        },
    )
}
