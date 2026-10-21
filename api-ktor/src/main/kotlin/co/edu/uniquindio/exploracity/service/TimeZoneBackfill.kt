package co.edu.uniquindio.exploracity.service

import co.edu.uniquindio.exploracity.config.query
import co.edu.uniquindio.exploracity.repository.PublicationRepository
import kotlinx.coroutines.CancellationException
import org.jetbrains.exposed.v1.jdbc.Database
import org.slf4j.LoggerFactory

/**
 * ADR-17 · Al arrancar, la zona horaria de los lugares que no la tienen: los de antes de la 1.3.0 (V12) y los que se
 * publicaron cuando las zonas no cargaron. Si no falta ninguno, no carga las zonas.
 */
class TimeZoneBackfill(private val database: Database, private val publications: PublicationRepository, private val zones: TimeZones) {
    private val log = LoggerFactory.getLogger(TimeZoneBackfill::class.java)

    /** Cuántos lugares quedaron con zona. Un error no detiene la API: esos lugares siguen con la hora de la ciudad. */
    suspend fun run(): Int = try {
        val missing = database.query { publications.withoutTimeZone() }
        val found = missing.mapNotNull { place -> zones.of(place.latitude, place.longitude)?.let { place.id to it } }
        val filled = if (found.isEmpty()) 0 else database.query { found.count { (id, zone) -> publications.fillTimeZone(id, zone) } }
        if (missing.isNotEmpty()) log.info("Zona horaria de {} de {} lugares sin zona", filled, missing.size)
        filled
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        log.error("No se pudo llenar la zona horaria de los lugares", e)
        0
    }
}
