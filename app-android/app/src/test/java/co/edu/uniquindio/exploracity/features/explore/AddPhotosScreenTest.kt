package co.edu.uniquindio.exploracity.features.explore

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import co.edu.uniquindio.exploracity.core.ui.theme.ExploraCityTheme
import co.edu.uniquindio.exploracity.domain.model.DraftPhoto
import co.edu.uniquindio.exploracity.domain.model.ThemeMode
import co.edu.uniquindio.exploracity.features.publish.PhotoCallbacks
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** v1.4.0 · Agregar fotos a un lugar ya publicado: ninguna es la portada (visto en el S20+ el 2026-11-01). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "es")
class AddPhotosScreenTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `las fotos elegidas no dicen que la primera será la portada`() {
        val photos = listOf(DraftPhoto("a", "", "a.jpg", "https://medios.test/a.jpg"), DraftPhoto("b", "", "b.jpg", "https://medios.test/b.jpg"))
        compose.setContent {
            ExploraCityTheme(ThemeMode.LIGHT) {
                AddPhotosScreen("Mirador", slots = 2, state = AddPhotosUiState(photos = photos), photos = PhotoCallbacks(), onBack = {}, onSend = {})
            }
        }

        compose.onNodeWithText("2 fotos elegidas").assertExists()
        compose.onNodeWithText("Portada").assertDoesNotExist()
        compose.onNodeWithText("Llegaste al máximo de 2 fotos.").assertExists()
        compose.onNodeWithText("Enviar fotos").assertIsEnabled()
    }
}
