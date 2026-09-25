package co.edu.uniquindio.exploracity.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class OwnPublicationTest {

    private fun rejection(vararg kinds: FixKind) = Rejection(
        reason = RejectionReason.OTHER,
        message = "Motivo",
        reviewerName = "Laura M.",
        rejectedAt = Instant.EPOCH,
        canResubmit = true,
        fixes = kinds.map { RequiredFix(it, it.name) },
    )

    @Test
    fun `corregir abre en el primer paso del formulario que haya que tocar (24 · foto y pin abren en la ubicación)`() {
        assertEquals(3, rejection(FixKind.PHOTOS, FixKind.LOCATION).firstStepToFix)
        assertEquals(1, rejection(FixKind.PRICE, FixKind.DESCRIPTION).firstStepToFix)
        assertEquals(5, rejection(FixKind.PHOTOS).firstStepToFix)
    }

    @Test
    fun `sin correcciones abre en el paso 1, como la tabla de interacciones de 24`() {
        assertEquals(1, rejection().firstStepToFix)
    }
}
