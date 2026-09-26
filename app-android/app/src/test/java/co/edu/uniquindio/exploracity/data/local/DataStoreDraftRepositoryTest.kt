package co.edu.uniquindio.exploracity.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import co.edu.uniquindio.exploracity.domain.model.Category
import co.edu.uniquindio.exploracity.domain.model.CategoryOrigin
import co.edu.uniquindio.exploracity.domain.model.DuplicateCheck
import co.edu.uniquindio.exploracity.domain.model.GeoPoint
import co.edu.uniquindio.exploracity.domain.model.PublicationDraft
import co.edu.uniquindio.exploracity.domain.model.PublishStep
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 15 · El borrador sobrevive al cierre de la app: se guarda en DataStore como JSON, con una clave por borrador. El
 * DataStore es en memoria: el de archivo no puede reemplazar su archivo en Windows (en Android sí), y lo que se prueba
 * aquí es la clave y el JSON.
 */
class DataStoreDraftRepositoryTest {

    private val dataStore = MemoryDataStore()
    private val repository = DataStoreDraftRepository(dataStore)

    private val draft = PublicationDraft(
        title = "Café Las Acacias",
        description = "Café de barrio con tostión propia y un patio interior lleno de matas.",
        category = Category.GASTRONOMY,
        categoryOrigin = CategoryOrigin.SUGGESTED,
        step = PublishStep.CATEGORY,
    )

    @Test
    fun `guarda y recupera el borrador con su paso y el origen de la categoría`() = runTest {
        repository.save(DraftKey.New, draft)

        assertEquals(draft, repository.load(DraftKey.New))
    }

    @Test
    fun `guarda el pin y la marca de posible duplicado con su nota (17 y 17B)`() = runTest {
        val located = draft.copy(
            location = GeoPoint(4.63412, -74.06558),
            duplicateCheck = DuplicateCheck(GeoPoint(4.63412, -74.06558), listOf("la-fonda-cafe"), "Es el local del segundo piso."),
            step = PublishStep.SCHEDULE,
        )
        repository.save(DraftKey.New, located)

        val loaded = repository.load(DraftKey.New)
        assertEquals(located, loaded)
        assertEquals(true, loaded?.possibleDuplicate)
        assertEquals(true, loaded?.duplicatesChecked)
    }

    @Test
    fun `un borrador guardado antes del paso 3 se sigue leyendo`() = runTest {
        dataStore.edit {
            it[stringPreferencesKey("borrador:nueva")] = """{"title":"Café Las Acacias","category":"GASTRONOMY","step":"CATEGORY"}"""
        }

        val loaded = repository.load(DraftKey.New)
        assertEquals(PublicationDraft(title = "Café Las Acacias", category = Category.GASTRONOMY, step = PublishStep.CATEGORY), loaded)
    }

    @Test
    fun `corregir una rechazada no pisa el borrador de la publicación nueva`() = runTest {
        val resubmit = PublicationDraft(title = "Mirador de La Peña")
        repository.save(DraftKey.New, draft)
        repository.save(DraftKey.Resubmit("mirador-de-la-pena"), resubmit)

        assertEquals(draft, repository.load(DraftKey.New))
        assertEquals(resubmit, repository.load(DraftKey.Resubmit("mirador-de-la-pena")))

        repository.clear(DraftKey.Resubmit("mirador-de-la-pena"))
        assertNull(repository.load(DraftKey.Resubmit("mirador-de-la-pena")))
        assertEquals(draft, repository.load(DraftKey.New))
    }

    @Test
    fun `un borrador ilegible no rompe el formulario`() = runTest {
        dataStore.edit { it[stringPreferencesKey("borrador:nueva")] = "{no es json" }

        assertNull(repository.load(DraftKey.New))
    }

    private class MemoryDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())

        override val data: Flow<Preferences> = state

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            transform(state.value).also { state.value = it }
    }
}
