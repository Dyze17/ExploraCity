package co.edu.uniquindio.exploracity.core.util

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.annotation.RequiresApi
import androidx.core.content.edit
import co.edu.uniquindio.exploracity.domain.model.AppLanguage
import java.util.Locale

/**
 * v1.2.0 · El idioma de la app (29). Desde Android 13 lo guarda el sistema (LocaleManager): también se cambia en los
 * ajustes del teléfono, que lo ofrecen gracias al localeConfig que genera el build. Antes de Android 13 lo guarda la
 * app y lo aplica a cada actividad al crearla ([wrap]). Se lee de SharedPreferences y no de DataStore porque
 * attachBaseContext no puede esperar.
 */
object AppLanguages {

    fun current(context: Context): AppLanguage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val locales = context.localeManager().applicationLocales
        if (locales.isEmpty) AppLanguage.SYSTEM else AppLanguage.of(locales[0].toLanguageTag())
    } else {
        AppLanguage.of(context.languagePreferences().getString(KEY, null))
    }

    /** Cambia el idioma: Android 13 o posterior vuelve a crear las pantallas solo; antes se recrea [activity]. */
    fun set(activity: Activity, language: AppLanguage) {
        if (language == current(activity)) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.localeManager().applicationLocales = language.tag?.let { LocaleList.forLanguageTags(it) } ?: LocaleList.getEmptyLocaleList()
        } else {
            activity.languagePreferences().edit { if (language.tag == null) remove(KEY) else putString(KEY, language.tag) }
            activity.recreate()
        }
    }

    /** Antes de Android 13: el contexto de la actividad con el idioma elegido. Desde 13 lo hace el sistema. */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val tag = AppLanguage.of(base.languagePreferences().getString(KEY, null)).tag ?: return base
        val locale = Locale.forLanguageTag(tag)
        // Lo que se formatea con el idioma por omisión (fechas, cifras) también cambia.
        Locale.setDefault(locale)
        val configuration = Configuration(base.resources.configuration).apply { setLocales(LocaleList(locale)) }
        return base.createConfigurationContext(configuration)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun Context.localeManager(): LocaleManager = getSystemService(LocaleManager::class.java)

    private fun Context.languagePreferences() = applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    private const val PREFERENCES = "idioma"
    private const val KEY = "etiqueta"
}
