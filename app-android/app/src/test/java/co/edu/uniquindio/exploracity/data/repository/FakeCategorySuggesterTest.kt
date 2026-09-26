package co.edu.uniquindio.exploracity.data.repository

import co.edu.uniquindio.exploracity.data.connectivity.FakeConnectivity
import co.edu.uniquindio.exploracity.data.connectivity.OfflineException
import co.edu.uniquindio.exploracity.domain.model.Category
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration

/** 16 · La sugerencia de prueba, mientras la real llega del backend. */
class FakeCategorySuggesterTest {

    private val suggester = FakeCategorySuggester(latency = Duration.ZERO)

    @Test
    fun `reconoce la categoría por palabras clave, sin mayúsculas ni tildes`() = runTest {
        assertEquals(Category.GASTRONOMY, suggester.suggest("Café Las Acacias", "Café de barrio con tostión propia y patio interior."))
        assertEquals(Category.NATURE, suggester.suggest("Sendero La Vieja", "Caminata entre bosque de niebla en la MONTAÑA."))
        assertEquals(Category.HISTORY, suggester.suggest("Casa colonial", "Museo con la historia de la independencia."))
    }

    @Test
    fun `sin coincidencias o con empate no sugiere nada`() = runTest {
        assertNull(suggester.suggest("Lugar bonito", "Un sitio que vale la pena visitar con amigos."))
        assertNull(suggester.suggest("Café del parque", "Un rato tranquilo por la tarde."))
    }

    @Test
    fun `sin red no se intenta`() = runTest {
        val offline = OnlineOnlyCategorySuggester(suggester, FakeConnectivity(online = false))

        assertTrue(runCatching { offline.suggest("Café Las Acacias", "Café de barrio") }.exceptionOrNull() is OfflineException)
    }
}
