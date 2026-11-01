package co.edu.uniquindio.exploracity.features.explore

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import co.edu.uniquindio.exploracity.core.data.repository.samplePois
import co.edu.uniquindio.exploracity.core.ui.theme.ExploraCityTheme
import co.edu.uniquindio.exploracity.domain.model.PoiDetails
import co.edu.uniquindio.exploracity.domain.model.PoiPhoto
import co.edu.uniquindio.exploracity.domain.model.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 13 · Tocar la galería abre las fotos enteras a pantalla completa; al cerrar, queda en la última que se vio. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "es")
class DetailGalleryTest {

    @get:Rule
    val compose = createComposeRule()

    private val details = PoiDetails(
        poi = samplePois.first { it.id == "plaza-de-bolivar" },
        description = "Capitolio, catedral y alcaldía alrededor de la plaza.",
        photos = listOf(PoiPhoto(null, "Fachada"), PoiPhoto(null, "Catedral"), PoiPhoto(null, "Pila")),
        address = "Carrera 14 con calle 20",
        hours = null,
        author = null,
        voted = false,
        visited = false,
    )

    private fun showDetail() {
        compose.setContent {
            ExploraCityTheme(ThemeMode.LIGHT) {
                PoiDetailScreen(PoiDetailUiState(DetailContent.Loaded(details)), DetailCallbacks())
            }
        }
    }

    @Test
    fun `tocar una foto abre el visor y la X lo cierra`() {
        showDetail()

        compose.onNodeWithContentDescription("Fachada. Foto 1 de 3").performClick()
        compose.onNodeWithText("Pellizca o toca dos veces para acercar").assertExists()

        compose.onNodeWithContentDescription("Cerrar las fotos").performClick()
        compose.onNodeWithText("Pellizca o toca dos veces para acercar").assertDoesNotExist()
    }

    @Test
    fun `al cerrar el visor la galeria queda en la ultima foto vista`() {
        showDetail()

        compose.onNodeWithContentDescription("Fachada. Foto 1 de 3").performClick()
        compose.onNode(hasScrollToIndexAction() and hasAnyAncestor(isDialog())).performScrollToIndex(2)
        compose.onNodeWithText("Foto 3 de 3").assertExists()
        compose.onNodeWithContentDescription("Cerrar las fotos").performClick()

        compose.onNodeWithContentDescription("Pila. Foto 3 de 3").assertExists()
    }
}
