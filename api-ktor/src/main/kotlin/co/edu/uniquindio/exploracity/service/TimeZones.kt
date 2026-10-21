package co.edu.uniquindio.exploracity.service

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import net.iakovlev.timeshape.TimeZoneEngine
import org.slf4j.LoggerFactory
import java.time.DateTimeException
import java.time.ZoneId
import kotlin.jvm.optionals.getOrNull

/**
 * v1.3.0 · La zona horaria de un punto (ADR-17), para que «Abierto ahora» use la hora del lugar y no la de la ciudad de
 * la configuración: un lugar en Austin abre a las 9 de Austin. null si no se sabe.
 */
fun interface TimeZones {
    suspend fun of(latitude: Double, longitude: Double): ZoneId?

    companion object {
        /** Sin zonas: todos los lugares usan la hora de la ciudad, como antes de la 1.3.0. Lo usan las pruebas. */
        val NONE = TimeZones { _, _ -> null }
    }
}

/**
 * Con Timeshape: los límites de las zonas de todo el mundo, también las del mar (timezone-boundary-builder), vienen en
 * el jar, sin red. Ocupan unos 210 MB y cargarlos tarda un par de segundos, así que se cargan en [scope] la primera vez
 * que hacen falta (al publicar o mover un pin, o al arrancar si hay lugares sin zona) y se quedan. Si fallan, [of]
 * responde null y los lugares usan la hora de la ciudad.
 */
class TimeshapeZones(scope: CoroutineScope, load: () -> TimeZoneEngine = TimeZoneEngine::initialize) : TimeZones {
    private val log = LoggerFactory.getLogger(TimeshapeZones::class.java)

    private val engine: Deferred<TimeZoneEngine?> = scope.async(Dispatchers.IO, start = CoroutineStart.LAZY) {
        val started = System.nanoTime()
        try {
            load().also { log.info("Zonas horarias cargadas en {} ms", (System.nanoTime() - started) / 1_000_000) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.error("No se pudieron cargar las zonas horarias: «Abierto ahora» usará la hora de la ciudad", e)
            null
        }
    }

    override suspend fun of(latitude: Double, longitude: Double): ZoneId? = engine.await()?.query(latitude, longitude)?.getOrNull()
}

/** La zona guardada en la base de datos; null si Java ya no la conoce. */
internal fun zoneOrNull(id: String): ZoneId? = try {
    ZoneId.of(id)
} catch (e: DateTimeException) {
    null
}
