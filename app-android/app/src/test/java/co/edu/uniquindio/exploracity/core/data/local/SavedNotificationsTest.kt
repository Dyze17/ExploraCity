package co.edu.uniquindio.exploracity.core.data.local

import co.edu.uniquindio.exploracity.core.data.remote.dto.NotificationDto
import co.edu.uniquindio.exploracity.domain.model.FinalizeReason
import co.edu.uniquindio.exploracity.domain.model.Notification
import co.edu.uniquindio.exploracity.domain.model.RejectionReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

/** v1.2.0 · Los códigos de los avisos llegan de la API y sobreviven guardados sin conexión: con ellos se traducen. */
class SavedNotificationsTest {

    private val at = Instant.parse("2026-10-21T15:00:00Z")

    @Test
    fun `los códigos de la API llegan al aviso y uno desconocido no rompe la lista`() {
        val rejected = NotificationDto("1", "REJECTED", at.toString(), false, placeId = "p", placeTitle = "Café", reason = "la foto…", rejectionReason = "PHOTO")
        val finalized = NotificationDto("2", "FINALIZED", at.toString(), false, placeId = "p", placeTitle = "Café", finalizeReason = "NUEVO_MOTIVO")
        val achievement = NotificationDto("3", "ACHIEVEMENT", at.toString(), false, achievement = "Caminante", achievementId = "caminante", nextBadgeId = "cultura-viva")

        assertEquals(RejectionReason.PHOTO, (rejected.toDomain() as Notification.Rejected).rejectionReason)
        assertNull((finalized.toDomain() as Notification.Finalized).finalizeReason)
        val badge = achievement.toDomain() as Notification.Achievement
        assertEquals("caminante", badge.achievementId)
        assertEquals("cultura-viva", badge.nextBadgeId)
    }

    @Test
    fun `guardados para ver sin conexión conservan sus códigos`() {
        val notifications = listOf(
            Notification.Rejected("1", at, false, "p", "Café", "la foto no permite reconocer el lugar", RejectionReason.PHOTO),
            Notification.Finalized("2", at, true, "p", "Café", "el lugar cerró de forma permanente", FinalizeReason.CLOSED),
            Notification.Achievement("3", at, false, "Caminante", "Cultura viva", 2, "caminante", "cultura-viva"),
        )

        assertEquals(notifications, notifications.mapIndexed { i, it -> it.toEntity(i).toDomain() })
    }
}
