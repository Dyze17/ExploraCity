package co.edu.uniquindio.exploracity.service

import co.edu.uniquindio.exploracity.model.Category
import co.edu.uniquindio.exploracity.model.PublicationStatus
import co.edu.uniquindio.exploracity.repository.PlaceRow
import co.edu.uniquindio.exploracity.support.MutableClock
import co.edu.uniquindio.exploracity.support.testConfig
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** La tarjeta de un lugar: si está abierto según la hora de la ciudad y la frase del mapa. */
class PlaceCardsTest {

    // Lunes 5 de octubre de 2026, 7:59 en Armenia.
    private val clock = MutableClock(Instant.parse("2026-10-05T12:59:00Z"))
    private val cards = PlaceCards(testConfig().city, clock)

    private fun row(days: Int? = 0b0011111, opens: LocalTime? = LocalTime.of(8, 0), closes: LocalTime? = LocalTime.of(18, 0)) = PlaceRow(
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
        hoursDays = days,
        opens = opens,
        closes = closes,
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
        assertNull(cards.summaryOf(row(days = null, opens = null, closes = null)).openNow)
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
        assertEquals(listOf("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"), cards.hoursOf(row())!!.days)
    }
}
