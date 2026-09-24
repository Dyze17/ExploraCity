package co.edu.uniquindio.exploracity.util

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Se piden las dos para que el diálogo del sistema deje elegir entre ubicación precisa y aproximada.
 * Para «Cercanos» (5 km) basta la aproximada; la precisa servirá al mapa (8) y al formulario de publicación.
 */
val LOCATION_PERMISSIONS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

fun Context.hasLocationPermission(): Boolean =
    LOCATION_PERMISSIONS.any { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }

/**
 * true si el sistema volverá a mostrar el diálogo tras una negativa. Después de pedirlo al menos una vez,
 * false significa que se negó para siempre y solo se puede dar desde la ficha de la app en Ajustes.
 */
fun Activity.shouldShowLocationRationale(): Boolean =
    LOCATION_PERMISSIONS.any { ActivityCompat.shouldShowRequestPermissionRationale(this, it) }

/**
 * Abre la ficha de la app en Ajustes para que la persona dé allí el permiso. La app no cambia nada por su cuenta.
 * En su propia tarea: si no, Ajustes queda apilado dentro de la app y reaparece al abrirla desde el icono.
 */
fun Context.openAppSettings() {
    startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}
