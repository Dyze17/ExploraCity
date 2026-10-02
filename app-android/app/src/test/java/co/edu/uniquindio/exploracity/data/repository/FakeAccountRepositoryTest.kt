package co.edu.uniquindio.exploracity.data.repository

import co.edu.uniquindio.exploracity.data.connectivity.FakeConnectivity
import co.edu.uniquindio.exploracity.data.connectivity.OfflineException
import co.edu.uniquindio.exploracity.domain.model.VisitExperience
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** 29 · «Descargar mis datos»: el archivo trae la cuenta, la reputación, las publicaciones y lo hecho en el feed. */
class FakeAccountRepositoryTest {

    // 26 de septiembre a las 22:00 en Bogotá: en UTC ya es el 27, y el nombre debe llevar la fecha local.
    private val clock = Clock.fixed(Instant.parse("2026-09-27T03:00:00Z"), ZoneOffset.UTC)
    private val pois = FakePoiRepository()
    private val publications = FakePublicationRepository(pois)
    private val repository = FakeAccountRepository(pois, publications, FakeUserRepository(pois, publications), clock)

    private suspend fun exported(): JsonObject = Json.parseToJsonElement(repository.exportData().content).jsonObject

    @Test
    fun `el archivo lleva la fecha de Bogotá en el nombre`() = runTest {
        assertEquals("exploracity-mis-datos-2026-09-26.json", repository.exportData().fileName)
    }

    @Test
    fun `trae la cuenta, la reputación y todas las publicaciones propias`() = runTest {
        val file = exported()

        val account = file.getValue("cuenta").jsonObject
        assertEquals("ana.rios@correo.com", account.getValue("correo").jsonPrimitive.content)
        assertEquals("Ana Ríos", account.getValue("nombre").jsonPrimitive.content)
        assertEquals("Residente", account.getValue("comoSePresenta").jsonPrimitive.content)
        val reputation = file.getValue("reputacion").jsonObject
        assertEquals("Aventurero", reputation.getValue("nivel").jsonPrimitive.content)
        assertEquals(9, reputation.getValue("insignias").jsonArray.size)
        val mine = publications.myPublications()
        val titles = file.getValue("publicaciones").jsonArray.map { it.jsonObject.getValue("titulo").jsonPrimitive.content }
        assertEquals(mine.map { it.title }, titles)
    }

    @Test
    fun `incluye los votos, los visitados y los comentarios de la persona`() = runTest {
        val place = pois.places().first()
        pois.setVote(place.id, voted = true)
        pois.markVisited(place.id, VisitExperience(recommends = true, text = "Buen café"))
        pois.addComment(place.id, "Vuelvo seguro.")

        val file = exported()

        assertEquals(listOf(place.title), file.getValue("votos").jsonArray.map { it.jsonPrimitive.content })
        val visit = file.getValue("visitados").jsonArray.single().jsonObject
        assertEquals("Buen café", visit.getValue("experiencia").jsonPrimitive.content)
        val comments = file.getValue("comentarios").jsonArray.map { it.jsonObject.getValue("texto").jsonPrimitive.content }
        assertEquals(listOf("Vuelvo seguro."), comments)
    }

    @Test
    fun `sin red no se puede descargar ni borrar la cuenta, pero el correo sí se conoce`() = runTest {
        val offline = OnlineOnlyAccountRepository(repository, FakeConnectivity(online = false))

        assertEquals("ana.rios@correo.com", offline.account().email)
        assertTrue(runCatching { offline.exportData() }.exceptionOrNull() is OfflineException)
        assertTrue(runCatching { offline.deleteAccount() }.exceptionOrNull() is OfflineException)
        assertTrue(!repository.deleted)
    }

    @Test
    fun `con red, borrar la cuenta llega al servidor`() = runTest {
        OnlineOnlyAccountRepository(repository, FakeConnectivity()).deleteAccount()

        assertTrue(repository.deleted)
    }
}
