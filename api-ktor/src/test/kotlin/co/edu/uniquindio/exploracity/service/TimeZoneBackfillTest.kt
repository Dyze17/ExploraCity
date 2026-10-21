package co.edu.uniquindio.exploracity.service

import co.edu.uniquindio.exploracity.model.Places
import co.edu.uniquindio.exploracity.model.PublicationStatus
import co.edu.uniquindio.exploracity.repository.PublicationRepository
import co.edu.uniquindio.exploracity.support.DatabaseTest
import co.edu.uniquindio.exploracity.support.Fixtures
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.ZoneId
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

/** ADR-17 · Los lugares de antes de la 1.3.0 reciben su zona horaria al arrancar la API. */
class TimeZoneBackfillTest : DatabaseTest() {

    private var asked = 0
    private val zones = TimeZones { _, longitude ->
        asked++
        ZoneId.of(if (longitude > -30) "Europe/Madrid" else "America/Bogota")
    }

    private fun backfill() = runBlocking { TimeZoneBackfill(database, PublicationRepository(), zones).run() }

    private fun zone(id: UUID) = transaction(database) { Places.selectAll().where { Places.id eq id }.single()[Places.timeZone] }

    @Test
    fun `llena la zona de los que no tienen, en cualquier estado, y deja la que ya tenian`() {
        val armenia = Fixtures.place(database, author = null, title = "Plaza de Bolívar")
        val madrid = Fixtures.place(database, author = null, title = "Puerta del Sol", status = PublicationStatus.PENDING, latitude = 40.4169, longitude = -3.7035)
        val kept = Fixtures.place(database, author = null, title = "Parque de la Vida")
        transaction(database) { Places.update({ Places.id eq kept }) { it[timeZone] = "America/Lima" } }

        assertEquals(2, backfill())

        assertEquals("America/Bogota", zone(armenia))
        assertEquals("Europe/Madrid", zone(madrid))
        assertEquals("America/Lima", zone(kept))
        assertEquals(2, asked)
    }

    @Test
    fun `si no falta ninguno no busca zonas`() {
        Fixtures.place(database, author = null, title = "Plaza de Bolívar")
        backfill()
        asked = 0

        assertEquals(0, backfill())
        assertEquals(0, asked)
    }
}
