package co.edu.uniquindio.exploracity.data.repository

import co.edu.uniquindio.exploracity.data.connectivity.FakeConnectivity
import co.edu.uniquindio.exploracity.data.connectivity.OfflineException
import co.edu.uniquindio.exploracity.domain.model.PublicationStatus
import co.edu.uniquindio.exploracity.domain.model.ReportReason
import co.edu.uniquindio.exploracity.domain.model.Residency
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeUserRepositoryTest {

    private val camilo = "camilo-r"

    @Test
    fun `el perfil trae solo los lugares verificados y finalizados de la persona`() = runTest {
        // Lugares fuera de la lista de prueba: sampleDetails los asigna a la primera autora de la lista, Camilo R.
        val hidden = listOf(
            samplePois.first().copy(id = "pendiente", status = PublicationStatus.PENDING),
            samplePois.first().copy(id = "rechazado", status = PublicationStatus.REJECTED),
        )
        val repository = FakeUserRepository(FakePoiRepository(pois = samplePois + hidden))

        val profile = requireNotNull(repository.publicProfile(camilo))

        assertEquals("Camilo R.", profile.author.name)
        assertEquals(Residency.RESIDENT, profile.residency)
        assertTrue(profile.places.isNotEmpty())
        assertTrue(profile.places.none { it.id == "pendiente" || it.id == "rechazado" })
        assertTrue(profile.places.all { sampleDetails(it).author.id == camilo })
        assertEquals(profile.places.size, profile.verifiedCount + profile.finalizedCount)
    }

    @Test
    fun `una persona que no existe no tiene perfil`() = runTest {
        assertNull(FakeUserRepository(FakePoiRepository()).publicProfile("no-existe"))
    }

    @Test
    fun `el reporte queda para moderación`() = runTest {
        val repository = FakeUserRepository(FakePoiRepository())

        repository.reportUser(camilo, ReportReason.SPAM)

        assertEquals(listOf(camilo to ReportReason.SPAM), repository.reports)
    }

    @Test
    fun `sin red el perfil y el reporte avisan que falta la conexión`() = runTest {
        val repository = OnlineOnlyUserRepository(FakeUserRepository(FakePoiRepository()), FakeConnectivity(online = false))

        assertTrue(runCatching { repository.publicProfile(camilo) }.exceptionOrNull() is OfflineException)
        assertTrue(runCatching { repository.reportUser(camilo, ReportReason.SPAM) }.exceptionOrNull() is OfflineException)
    }
}
