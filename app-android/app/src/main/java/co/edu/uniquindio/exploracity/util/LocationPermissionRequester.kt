package co.edu.uniquindio.exploracity.util

import android.app.Activity
import android.content.Context
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

/** Para qué se pidió el permiso de ubicación: decide qué hacer con la respuesta. */
enum class LocationPurpose {
    /** «Ver N lugares» con «Cercanos» en la hoja de filtros (9). */
    APPLY_FILTERS,

    /** «Permitir» del aviso tras negar «Cercanos». */
    ENABLE_NEARBY,

    /** Botón «Mi ubicación» del mapa (8). */
    CENTER_MAP,
}

/**
 * Pide el permiso de ubicación y entrega la respuesta con su [LocationPurpose]. El motivo se guarda en el
 * estado de la pantalla: si Android cierra la app mientras el diálogo del sistema está abierto (pasa al negar),
 * la respuesta llega al proceso nuevo con el mismo motivo.
 */
@Stable
class LocationPermissionRequester internal constructor(
    private val context: Context,
    private val activity: Activity?,
    private val pending: MutableState<LocationPurpose?>,
    private val inSettings: MutableState<Boolean>,
    private val launcher: ManagedActivityResultLauncher<Array<String>, Map<String, Boolean>>,
    private val onResult: State<(LocationPurpose, Boolean) -> Unit>,
) {
    val isGranted: Boolean get() = context.hasLocationPermission()

    /** Pide el permiso; si ya está concedido responde al momento. */
    fun request(purpose: LocationPurpose) {
        if (isGranted) {
            onResult.value(purpose, true)
        } else {
            pending.value = purpose
            launcher.launch(LOCATION_PERMISSIONS)
        }
    }

    /** Acción «Permitir» de un aviso: vuelve a pedirlo o, si se negó para siempre, lleva a la ficha de la app. */
    fun allow(purpose: LocationPurpose) {
        when {
            isGranted -> onResult.value(purpose, true)
            activity?.shouldShowLocationRationale() == true -> request(purpose)
            else -> {
                // Negado para siempre: el sistema ya no muestra el diálogo. Al volver de Ajustes se revisa.
                pending.value = purpose
                inSettings.value = true
                context.openAppSettings()
            }
        }
    }
}

/** [onResult] recibe el motivo y si se concedió. Desde Ajustes solo llega si se concedió. */
@Composable
fun rememberLocationPermissionRequester(onResult: (LocationPurpose, granted: Boolean) -> Unit): LocationPermissionRequester {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val currentOnResult = rememberUpdatedState(onResult)
    val pending = rememberSaveable { mutableStateOf<LocationPurpose?>(null) }
    val inSettings = rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        pending.value?.let { currentOnResult.value(it, grants.values.any { granted -> granted }) }
        pending.value = null
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (inSettings.value) {
            val purpose = pending.value
            if (purpose != null && context.hasLocationPermission()) currentOnResult.value(purpose, true)
            pending.value = null
            inSettings.value = false
        }
    }
    return remember(context, activity, launcher) {
        LocationPermissionRequester(context, activity, pending, inSettings, launcher, currentOnResult)
    }
}
