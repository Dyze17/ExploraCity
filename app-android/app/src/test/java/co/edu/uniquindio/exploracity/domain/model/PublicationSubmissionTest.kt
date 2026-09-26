package co.edu.uniquindio.exploracity.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalTime

/** 19 → 20 · Lo que se envía sale del borrador completo. */
class PublicationSubmissionTest {

    private val here = GeoPoint(4.6383, -74.0656)
    private val complete = PublicationDraft(
        title = "  Café Las Acacias ",
        description = "Café de barrio con tostión propia y un patio interior lleno de matas.",
        category = Category.GASTRONOMY,
        categoryOrigin = CategoryOrigin.SUGGESTED,
        location = here,
        hours = DraftHours(setOf(DayOfWeek.MONDAY), LocalTime.of(7, 0), LocalTime.of(19, 0)),
        photos = listOf(DraftPhoto("f1", "/fotos/f1.jpg", "patio.jpg")),
    )

    @Test
    fun `toma el borrador completo, con el título sin espacios de sobra`() {
        val submission = PublicationSubmission.from(complete, resubmitId = null)

        assertEquals("Café Las Acacias", submission?.title)
        assertEquals(OpeningHours(setOf(DayOfWeek.MONDAY), LocalTime.of(7, 0), LocalTime.of(19, 0)), submission?.hours)
    }

    @Test
    fun `sin horario exacto no lleva horario, aunque se hubiera empezado a elegir`() {
        val submission = PublicationSubmission.from(complete.copy(hoursUnknown = true, hours = DraftHours(opens = LocalTime.of(9, 0))), null)

        assertNull(submission?.hours)
    }

    @Test
    fun `sin fotos, sin pin o con el horario a medias no se puede enviar`() {
        assertNull(PublicationSubmission.from(complete.copy(photos = emptyList()), null))
        assertNull(PublicationSubmission.from(complete.copy(location = null), null))
        assertNull(PublicationSubmission.from(complete.copy(hours = DraftHours(setOf(DayOfWeek.MONDAY))), null))
    }

    @Test
    fun `la búsqueda de parecidos solo viaja si es la del pin actual`() {
        val moved = complete.copy(duplicateCheck = DuplicateCheck(GeoPoint(4.0, -74.0), listOf("otro"), "nota"))

        assertNull(PublicationSubmission.from(moved, null)?.duplicateCheck)
        assertEquals(listOf("otro"), PublicationSubmission.from(moved.copy(duplicateCheck = moved.duplicateCheck?.copy(location = here)), null)?.duplicateCheck?.similarIds)
    }
}
