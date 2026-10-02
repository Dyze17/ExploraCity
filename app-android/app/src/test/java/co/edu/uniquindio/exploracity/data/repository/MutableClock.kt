package co.edu.uniquindio.exploracity.data.repository

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/** Un reloj que solo avanza cuando la prueba lo dice (enlaces de 30 minutos). */
internal class MutableClock(private var now: Instant = Instant.parse("2026-10-02T12:00:00Z")) : Clock() {
    fun advanceMinutes(minutes: Long) {
        now = now.plus(minutes, ChronoUnit.MINUTES)
    }

    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId): Clock = this

    override fun instant(): Instant = now
}
