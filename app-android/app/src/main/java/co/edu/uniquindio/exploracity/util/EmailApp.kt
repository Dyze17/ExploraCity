package co.edu.uniquindio.exploracity.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent

/**
 * 6.a · «Abrir mi correo»: la app de correo del teléfono (si hay varias, Android pregunta cuál). False si no hay
 * ninguna; entonces el botón no se muestra. El manifiesto declara la consulta (Android 11+).
 */
fun Context.hasEmailApp(): Boolean =
    packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_EMAIL), 0).isNotEmpty()

/** Abre la app de correo; false si ya no está (se desinstaló mientras tanto). */
fun Context.openEmailApp(): Boolean = try {
    startActivity(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_EMAIL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (_: ActivityNotFoundException) {
    false
}
