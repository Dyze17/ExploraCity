package co.edu.uniquindio.exploracity.data.local

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import co.edu.uniquindio.exploracity.domain.model.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** 29 · El tema elegido se guarda en el teléfono; sin elegir, sigue al sistema. */
class DataStoreAppPreferencesTest {

    private val dataStore = MemoryDataStore()
    private val preferences = DataStoreAppPreferences(dataStore)

    @Test
    fun `sin elegir, el tema sigue al sistema`() = runTest {
        assertEquals(ThemeMode.SYSTEM, preferences.themeMode.first())
    }

    @Test
    fun `guarda el tema elegido`() = runTest {
        preferences.setThemeMode(ThemeMode.DARK)

        assertEquals(ThemeMode.DARK, preferences.themeMode.first())
    }

    @Test
    fun `un valor que ya no existe vuelve al del sistema`() = runTest {
        dataStore.edit { it[stringPreferencesKey("tema")] = "SEPIA" }

        assertEquals(ThemeMode.SYSTEM, preferences.themeMode.first())
    }
}
