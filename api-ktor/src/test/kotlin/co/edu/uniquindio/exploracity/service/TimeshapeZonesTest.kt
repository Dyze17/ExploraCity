package co.edu.uniquindio.exploracity.service

import kotlinx.coroutines.runBlocking
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** ADR-17 · Las zonas de verdad, con todo el mundo cargado como en Cloud Run. */
class TimeshapeZonesTest {

    @Test
    fun `cada punto tiene la zona de donde esta, tambien en el mar`() = runBlocking {
        val runtime = Runtime.getRuntime()
        System.gc()
        val before = runtime.totalMemory() - runtime.freeMemory()
        val started = System.nanoTime()
        val zones = TimeshapeZones(this)

        assertEquals(ZoneId.of("America/Bogota"), zones.of(4.5339, -75.6811), "Armenia")
        val loadedMs = (System.nanoTime() - started) / 1_000_000
        System.gc()
        val usedMb = (runtime.totalMemory() - runtime.freeMemory() - before) / (1024 * 1024)
        println("Timeshape: ${loadedMs} ms, ${usedMb} MB")

        assertEquals(ZoneId.of("America/Chicago"), zones.of(30.2672, -97.7431), "Austin")
        assertEquals(ZoneId.of("Europe/Madrid"), zones.of(40.4168, -3.7038), "Madrid")
        assertEquals(ZoneId.of("America/Bogota"), zones.of(12.5847, -81.7006), "San Andrés")
        assertEquals(ZoneId.of("Etc/GMT+2"), zones.of(0.0, -30.0), "En el Atlántico")
    }

    @Test
    fun `si no cargan, no hay zona y se usa la de la ciudad`() = runBlocking {
        val zones = TimeshapeZones(this) { error("sin datos") }

        assertNull(zones.of(4.5339, -75.6811))
    }
}
