package co.edu.uniquindio.exploracity.ui.screens

import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import co.edu.uniquindio.exploracity.data.repository.sampleCurrentUser
import co.edu.uniquindio.exploracity.data.repository.samplePois
import co.edu.uniquindio.exploracity.domain.model.Comment
import co.edu.uniquindio.exploracity.domain.model.Notification
import co.edu.uniquindio.exploracity.domain.model.PoiDetails
import co.edu.uniquindio.exploracity.domain.model.ThemeMode
import co.edu.uniquindio.exploracity.ui.screens.comments.CommentsCallbacks
import co.edu.uniquindio.exploracity.ui.screens.comments.CommentsScreen
import co.edu.uniquindio.exploracity.ui.screens.detail.DetailCallbacks
import co.edu.uniquindio.exploracity.ui.screens.detail.PoiDetailScreen
import co.edu.uniquindio.exploracity.ui.screens.notifications.NotificationsCallbacks
import co.edu.uniquindio.exploracity.ui.screens.notifications.NotificationsScreen
import co.edu.uniquindio.exploracity.ui.theme.ExploraCityTheme
import co.edu.uniquindio.exploracity.viewmodel.CommentsContent
import co.edu.uniquindio.exploracity.viewmodel.CommentsUiState
import co.edu.uniquindio.exploracity.viewmodel.DetailContent
import co.edu.uniquindio.exploracity.viewmodel.NotificationsContent
import co.edu.uniquindio.exploracity.viewmodel.NotificationsUiState
import co.edu.uniquindio.exploracity.viewmodel.PoiDetailUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

/** 30 (A1) · Lo que dejó una cuenta eliminada sigue a la vista como «Usuario eliminado», sin nivel ni perfil. */
@RunWith(RobolectricTestRunner::class)
class DeletedAuthorTest {

    @get:Rule
    val compose = createComposeRule()

    private val plaza = samplePois.first { it.id == "plaza-de-bolivar" }

    @Test
    fun `un comentario de una cuenta eliminada se ve como Usuario eliminado`() {
        val comment = Comment("c-1", author = null, text = "Muy bonita de noche.", createdAt = Instant.now())
        compose.setContent {
            ExploraCityTheme(ThemeMode.LIGHT) {
                CommentsScreen(
                    CommentsUiState(sampleCurrentUser, CommentsContent.Loaded(plaza.title, listOf(comment), total = 1, nextCursor = null)),
                    CommentsCallbacks(),
                )
            }
        }

        // Un solo nodo para el lector, sin nivel: «Usuario eliminado, hace un momento. Muy bonita de noche.».
        compose.onNodeWithContentDescription("Usuario eliminado, ", substring = true).assertExists()
    }

    @Test
    fun `un lugar de una cuenta eliminada no lleva a ningún perfil`() {
        val details = PoiDetails(
            poi = plaza,
            description = "Capitolio, catedral y alcaldía alrededor de la plaza.",
            photos = emptyList(),
            address = "Carrera 14 con calle 20",
            hours = null,
            author = null,
            voted = false,
            visited = false,
        )
        compose.setContent {
            ExploraCityTheme(ThemeMode.LIGHT) {
                PoiDetailScreen(PoiDetailUiState(DetailContent.Loaded(details)), DetailCallbacks())
            }
        }

        compose.onNodeWithContentDescription("Publicado por un usuario eliminado").assertExists().assertHasNoClickAction()
    }

    @Test
    fun `el aviso de un comentario de una cuenta eliminada no tiene nombre`() {
        val notice = Notification.Commented("n-1", Instant.now(), read = false, plaza.id, plaza.title, authorName = null, excerpt = "Hola")
        compose.setContent {
            ExploraCityTheme(ThemeMode.LIGHT) {
                NotificationsScreen(NotificationsUiState(NotificationsContent.Loaded(listOf(notice))), NotificationsCallbacks())
            }
        }

        compose.onNodeWithContentDescription("Usuario eliminado comentó en Plaza de Bolívar", substring = true).assertExists()
    }
}
