package co.edu.uniquindio.exploracity.navigation

import co.edu.uniquindio.exploracity.domain.model.UserRole
import org.junit.Assert.assertEquals
import org.junit.Test

class TopLevelDestinationTest {

    @Test
    fun `usuario ve 4 pestañas sin Moderación`() {
        assertEquals(
            listOf(
                TopLevelDestination.EXPLORE,
                TopLevelDestination.PUBLISH,
                TopLevelDestination.NOTIFICATIONS,
                TopLevelDestination.PROFILE,
            ),
            topLevelDestinations(UserRole.USER),
        )
    }

    @Test
    fun `moderador ve Moderación como tercera pestaña`() {
        assertEquals(
            listOf(
                TopLevelDestination.EXPLORE,
                TopLevelDestination.PUBLISH,
                TopLevelDestination.MODERATION,
                TopLevelDestination.NOTIFICATIONS,
                TopLevelDestination.PROFILE,
            ),
            topLevelDestinations(UserRole.MODERATOR),
        )
    }
}
