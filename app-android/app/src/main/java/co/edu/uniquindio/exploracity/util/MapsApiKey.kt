package co.edu.uniquindio.exploracity.util

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build

private const val MAPS_API_KEY_META = "com.google.android.geo.API_KEY"

/**
 * Si la app se compiló con clave de Google Maps (MAPS_API_KEY en local.properties, fuera de git). Sin ella el
 * SDK no puede dibujar el mapa y puede cerrar la app, así que el mapa (8) muestra un aviso en su lugar.
 */
fun Context.hasMapsApiKey(): Boolean {
    val info: ApplicationInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong()))
    } else {
        @Suppress("DEPRECATION")
        packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
    }
    return !info.metaData?.getString(MAPS_API_KEY_META).isNullOrBlank()
}
