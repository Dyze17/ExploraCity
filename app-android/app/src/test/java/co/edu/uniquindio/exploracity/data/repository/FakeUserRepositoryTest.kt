package co.edu.uniquindio.exploracity.data.repository

import co.edu.uniquindio.exploracity.domain.model.BadgeMetric
import co.edu.uniquindio.exploracity.domain.model.PhotoChange
import co.edu.uniquindio.exploracity.domain.model.ProfileUpdate
import co.edu.uniquindio.exploracity.domain.model.PublicationStatus
import co.edu.uniquindio.exploracity.domain.model.ReportReason
import co.edu.uniquindio.exploracity.domain.model.Residency
import co.edu.uniquindio.exploracity.domain.model.UserLevel
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
    fun `el perfil propio cuenta las siete publicaciones de Ana, también las que el feed no muestra`() = runTest {
        val profile = FakeUserRepository(FakePoiRepository()).ownProfile()

        assertEquals(sampleCurrentUser, profile.author)
        assertEquals(UserLevel.ADVENTURER, profile.author.level)
        val counts = profile.publications
        assertEquals(2, counts.verified)
        assertEquals(1, counts.finalized)
        assertEquals(2, counts.pending)
        assertEquals(2, counts.rejected)
        assertEquals(7, counts.total)
    }

    @Test
    fun `las insignias cuadran con las publicaciones y con el perfil público (2 de 9)`() = runTest {
        val repository = FakeUserRepository(FakePoiRepository())
        val own = repository.ownProfile()
        val public = requireNotNull(repository.publicProfile(sampleCurrentUser.id))

        assertEquals(9, own.badges.size)
        assertEquals(2, own.unlockedBadges)
        assertEquals(own.unlockedBadges, public.badges)
        // «10 verificadas · 3 de 10»: las verificadas de hoy más la que ya pasó a finalizada.
        val verified = own.badges.single { it.metric == BadgeMetric.VERIFIED_PLACES }
        assertEquals(own.publications.verified + own.publications.finalized, verified.progress)
    }

    @Test
    fun `editar el perfil propio cambia lo que ven 26 y 31, también la foto`() = runTest {
        val repository = FakeUserRepository(FakePoiRepository())

        repository.updateProfile(ProfileUpdate("Ana María", "Cafés con patio.", Residency.VISITOR, PhotoChange.Replace("/fotos/yo.jpg")))

        val own = repository.ownProfile()
        assertEquals("Ana María", own.author.name)
        assertEquals("Cafés con patio.", own.bio)
        assertEquals(Residency.VISITOR, own.residency)
        assertEquals("fake://perfil/yo.jpg", own.photo)
        val public = requireNotNull(repository.publicProfile("ana-rios"))
        assertEquals("Ana María", public.author.name)
        assertEquals("fake://perfil/yo.jpg", public.photo)

        repository.updateProfile(ProfileUpdate("Ana María", null, Residency.VISITOR, PhotoChange.Remove))
        assertNull(repository.ownProfile().photo)
        assertNull(repository.ownProfile().bio)
    }

    @Test
    fun `el servidor rechaza un nombre o un sobre mí fuera de las reglas`() = runTest {
        val repository = FakeUserRepository(FakePoiRepository())

        assertTrue(runCatching { repository.updateProfile(ProfileUpdate("A", null, Residency.RESIDENT, PhotoChange.Keep)) }.isFailure)
        assertTrue(runCatching { repository.updateProfile(ProfileUpdate("Ana", "a".repeat(151), Residency.RESIDENT, PhotoChange.Keep)) }.isFailure)
    }

    @Test
    fun `quitar la foto o eliminar la cuenta borra la copia del servidor`() = runTest {
        var removed = 0
        val repository = FakeUserRepository(FakePoiRepository(), photoRemover = { removed++ })
        repository.updateProfile(ProfileUpdate("Ana María", "Cafés.", Residency.RESIDENT, PhotoChange.Replace("/fotos/yo.jpg")))

        repository.updateProfile(ProfileUpdate("Ana María", "Cafés.", Residency.RESIDENT, PhotoChange.Remove))
        assertEquals(1, removed)

        repository.updateProfile(ProfileUpdate("Ana María", "Cafés.", Residency.RESIDENT, PhotoChange.Replace("/fotos/yo.jpg")))
        repository.deleteOwnAccount()
        assertEquals(2, removed)
        assertNull(repository.ownProfile().photo)
        assertEquals("Ana Ríos", repository.ownProfile().author.name)
    }
}
