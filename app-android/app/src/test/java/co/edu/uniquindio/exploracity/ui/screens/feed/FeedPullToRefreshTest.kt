package co.edu.uniquindio.exploracity.ui.screens.feed

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import co.edu.uniquindio.exploracity.data.repository.samplePois
import co.edu.uniquindio.exploracity.domain.model.ThemeMode
import co.edu.uniquindio.exploracity.ui.theme.ExploraCityTheme
import co.edu.uniquindio.exploracity.viewmodel.FeedContent
import co.edu.uniquindio.exploracity.viewmodel.FeedUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 7 · Deslizar hacia abajo recarga solo desde arriba del todo: el gesto que trae de vuelta la lista y la cabecera
 * (búsqueda y chips) se queda en eso y no sigue en recarga.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w384dp-h832dp-450dpi")
class FeedPullToRefreshTest {

    @get:Rule
    val compose = createComposeRule()

    private var refreshes = 0

    private fun showFeed() {
        val state = FeedUiState(areaName = "Armenia", content = FeedContent.Loaded(samplePois, samplePois.size, canLoadMore = false))
        compose.setContent {
            ExploraCityTheme(ThemeMode.LIGHT) {
                FeedScreen(state, isModerator = false, callbacks = FeedCallbacks(onRefresh = { refreshes++ }))
            }
        }
    }

    private val search get() = compose.onNodeWithText("Buscar lugares en Armenia")

    /**
     * Arrastra el dedo despacio y se detiene antes de soltar, como al bajar o subir leyendo: sin la inercia de un
     * deslizamiento rápido, que mueve la lista sola y nunca recarga.
     */
    private fun drag(fromY: Float, toY: Float) = compose.onRoot().performTouchInput {
        down(Offset(centerX, height * fromY))
        val steps = 40
        repeat(steps) { step -> moveTo(Offset(centerX, height * (fromY + (toY - fromY) * (step + 1) / steps))) }
        advanceEventTime(500)
        up()
    }

    @Test
    fun `arriba del todo, deslizar hacia abajo recarga`() {
        showFeed()

        drag(fromY = 0.45f, toY = 0.95f)

        assertEquals(1, refreshes)
    }

    @Test
    fun `tras bajar, el gesto que vuelve arriba despliega la cabecera y no recarga`() {
        showFeed()
        drag(fromY = 0.9f, toY = 0.4f)
        search.assertIsNotDisplayed()

        // Un gesto largo: devuelve la lista, despliega la cabecera y aún le sobra recorrido.
        drag(fromY = 0.15f, toY = 0.98f)

        search.assertIsDisplayed()
        assertEquals(0, refreshes)

        // Ya arriba del todo, el siguiente gesto sí recarga.
        drag(fromY = 0.45f, toY = 0.95f)
        assertEquals(1, refreshes)
    }
}
