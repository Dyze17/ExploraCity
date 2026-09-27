package co.edu.uniquindio.exploracity.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
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
}

class DataStoreAppPreferences(private val dataStore: DataStore<Preferences>) : AppPreferences {
    override val themeMode: Flow<ThemeMode> = dataStore.data
        .map { prefs -> ThemeMode.entries.firstOrNull { it.name == prefs[THEME_KEY] } ?: ThemeMode.SYSTEM }
        .distinctUntilChanged()

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[THEME_KEY] = mode.name }
    }

    private companion object {
        val THEME_KEY = stringPreferencesKey("tema")
    }
}
