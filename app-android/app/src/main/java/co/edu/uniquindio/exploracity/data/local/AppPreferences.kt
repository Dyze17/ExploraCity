package co.edu.uniquindio.exploracity.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import co.edu.uniquindio.exploracity.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Preferencias del teléfono que no dependen de la cuenta: sobreviven al cierre de sesión (29A). */
interface AppPreferences {
    /** 29 · Tema elegido en Ajustes; Sistema mientras no se elija otro. */
    val themeMode: Flow<ThemeMode>

    suspend fun setThemeMode(mode: ThemeMode)

    /** 2 · El onboarding ya se vio en este teléfono: el arranque (1) va directo al inicio de sesión (3). */
    val onboardingSeen: Flow<Boolean>

    suspend fun setOnboardingSeen()
}

class DataStoreAppPreferences(private val dataStore: DataStore<Preferences>) : AppPreferences {
    override val themeMode: Flow<ThemeMode> = dataStore.data
        .map { prefs -> ThemeMode.entries.firstOrNull { it.name == prefs[THEME_KEY] } ?: ThemeMode.SYSTEM }
        .distinctUntilChanged()

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[THEME_KEY] = mode.name }
    }

    override val onboardingSeen: Flow<Boolean> = dataStore.data.map { it[ONBOARDING_KEY] ?: false }.distinctUntilChanged()

    override suspend fun setOnboardingSeen() {
        dataStore.edit { it[ONBOARDING_KEY] = true }
    }

    private companion object {
        val THEME_KEY = stringPreferencesKey("tema")
        val ONBOARDING_KEY = booleanPreferencesKey("onboarding_visto")
    }
}
