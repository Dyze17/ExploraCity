package co.edu.uniquindio.exploracity.service

import co.edu.uniquindio.exploracity.model.Category
import co.edu.uniquindio.exploracity.model.PublicationStatus
import co.edu.uniquindio.exploracity.repository.HoursBlock
import co.edu.uniquindio.exploracity.repository.MunicipalityFilter
import co.edu.uniquindio.exploracity.repository.PlaceFilter
import co.edu.uniquindio.exploracity.repository.PlaceRow
import co.edu.uniquindio.exploracity.support.MutableClock
import co.edu.uniquindio.exploracity.support.testConfig
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** La tarjeta de un lugar: si está abierto según la hora del lugar (o de la ciudad) y la frase del mapa. */
class PlaceCardsTest {

    @Test
    fun `los lugares sin municipio son de la ciudad de la configuración`() {
        fun unassigned(name: String, region: String?) =
            cards.withUnassigned(PlaceFilter(municipality = MunicipalityFilter(name, region))).municipality!!.includeUnassigned

        assertEquals(true, unassigned("armenia", "QUINDIO"))
        assertEquals(true, unassigned("Armenia", null))
        assertEquals(false, unassigned("Armenia", "Antioquia"))
        assertEquals(false, unassigned("Calarcá", "Quindío"))
        assertNull(cards.withUnassigned(PlaceFilter()).municipality)
    }

    // Lunes 5 de octubre de 2026, 7:59 en Armenia.
    private val clock = MutableClock(Instant.parse("2026-10-05T12:59:00Z"))
    private val cards = PlaceCards(testConfig().city, clock)

    private val weekdays = HoursBlock(0b0011111, LocalTime.of(8, 0), LocalTime.of(18, 0))

    private fun row(schedule: List<HoursBlock> = listOf(weekdays), alwaysOpen: Boolean = false, timeZone: ZoneId? = null) = PlaceRow(
        id = UUID.randomUUID(),
        authorId = null,
        title = "Museo del Oro Quimbaya",
        description = "Orfebrería prehispánica del Quindío. Entrada libre los domingos.",
        category = Category.CULTURE,
        status = PublicationStatus.VERIFIED,
        latitude = 4.5339,
        longitude = -75.6811,
        distanceMeters = 0,
        votes = 0,
        comments = 0,
        cover = null,
        price = null,
        address = null,
        schedule = schedule,
        alwaysOpen = alwaysOpen,
        timeZone = timeZone,
    )

    @Test
    fun `abierto según el horario y la hora de la ciudad, con el cierre ya cerrado`() {
        assertEquals(false, cards.summaryOf(row()).openNow)
        clock.advance(java.time.Duration.ofMinutes(1))
        assertEquals(true, cards.summaryOf(row()).openNow)
        // Lunes 18:00: cerrado.
        clock.now = Instant.parse("2026-10-05T23:00:00Z")
        assertEquals(false, cards.summaryOf(row()).openNow)
        // Sábado a mediodía, con horario de lunes a viernes.
        clock.now = Instant.parse("2026-10-03T17:00:00Z")
        assertEquals(false, cards.summaryOf(row()).openNow)
        assertNull(cards.summaryOf(row(schedule = emptyList())).openNow)
    }

    @Test
    fun `por tramos, con cierre después de medianoche, y siempre abierto`() {
        // Entre semana de 16:00 a 2:00 y el fin de semana de 22:00 a 3:00, como una discoteca.
        val club = row(
            schedule = listOf(
                HoursBlock(0b0011111, LocalTime.of(16, 0), LocalTime.of(2, 0)),
                HoursBlock(0b1100000, LocalTime.of(22, 0), LocalTime.of(3, 0)),
            ),
        )
        fun openAt(local: String): Boolean? {
            clock.now = OffsetDateTime.parse("$local-05:00").toInstant()
            return cards.summaryOf(club).openNow
        }

        // El lunes 5 de octubre de 2026, hora de Armenia.
        assertEquals(false, openAt("2026-10-05T15:59:00"), "Lunes antes de abrir")
        assertEquals(true, openAt("2026-10-05T23:00:00"), "Lunes en la noche")
        assertEquals(true, openAt("2026-10-06T01:59:00"), "Madrugada del martes, del tramo del lunes")
        assertEquals(false, openAt("2026-10-06T02:00:00"), "Martes a las 2:00 ya cerró")
        assertEquals(true, openAt("2026-10-05T01:00:00"), "Madrugada del lunes, del tramo del domingo")
        assertEquals(false, openAt("2026-10-10T02:30:00"), "Madrugada del sábado: el viernes cerró a las 2:00")
        assertEquals(true, openAt("2026-10-11T02:59:00"), "Madrugada del domingo, del tramo del sábado")
        assertEquals(false, openAt("2026-10-10T18:00:00"), "Sábado en la tarde: abre a las 22:00")
        assertEquals(true, cards.summaryOf(row(schedule = emptyList(), alwaysOpen = true)).openNow, "Siempre abierto")
    }

    @Test
    fun `con su zona horaria, abre con la hora del lugar y no la de la ciudad`() {
        // Lunes 7:59 en Armenia es 14:59 en Madrid y 7:59 en Austin (horario de verano).
        assertEquals(true, cards.summaryOf(row(timeZone = ZoneId.of("Europe/Madrid"))).openNow)
        assertEquals(false, cards.summaryOf(row(timeZone = ZoneId.of("America/Chicago"))).openNow)
        assertEquals(false, cards.summaryOf(row(timeZone = ZoneId.of("America/Bogota"))).openNow)
        // Lunes 18:30 en Armenia ya es martes en Madrid (01:30): cerrado.
        clock.now = Instant.parse("2026-10-05T23:30:00Z")
        assertEquals(false, cards.summaryOf(row(timeZone = ZoneId.of("Europe/Madrid"))).openNow)
    }

    @Test
    fun `la frase del mapa es la primera oración, y si no cabe se corta en una palabra`() {
        assertEquals("Orfebrería prehispánica del Quindío.", cards.summaryOf(row()).summary)
        val long = "Un recorrido por los cafetales de la vereda, con la historia de cada familia que siembra y tuesta su propio grano"
        val summary = PlaceCards.shortSummary(long)
        assertEquals("Un recorrido por los cafetales de la vereda, con la historia de cada familia que siembra…", summary)
        assertEquals(true, summary.length <= 90)
    }

    @Test
    fun `los días del horario van de lunes a domingo`() {
        assertEquals(listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.SUNDAY), PlaceCards.days(1 + 4 + 64))
        assertEquals(listOf("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"), PlaceCards.hoursOf(weekdays).days)
    }
}
