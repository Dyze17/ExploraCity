package co.edu.uniquindio.exploracity.core.data.repository

import android.os.SystemClock
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import co.edu.uniquindio.exploracity.core.data.location.AddressResolver
import co.edu.uniquindio.exploracity.core.data.location.LocationProvider
import co.edu.uniquindio.exploracity.core.util.runCatchingNonCancellation
import co.edu.uniquindio.exploracity.domain.model.GeoPoint
import co.edu.uniquindio.exploracity.domain.model.Municipality
import co.edu.uniquindio.exploracity.domain.model.TownOption
import co.edu.uniquindio.exploracity.domain.repository.ExploredTown
import co.edu.uniquindio.exploracity.domain.repository.TownRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * ADR-16 · El municipio que se explora. Sigue la ubicación de la persona (el Geocoder del teléfono, el mismo de la
 * dirección del pin) hasta que elige una ciudad a mano, que se queda hasta volver a «Usar mi ubicación» (v1.3.0). Lo
 * último que se supo queda guardado para abrir sin conexión. Sin permiso ni ciudad elegida es [ExploredTown.Unknown]:
 * ya no se cae en la ciudad de la API.
 */
class LocatedTownRepository(
    private val location: LocationProvider,
    private val addresses: AddressResolver,
    /** GET /v1/places/towns. */
    private val townsWithPlaces: suspend () -> List<TownOption>,
    private val store: DataStore<Preferences>,
    private val hasPermission: () -> Boolean,
    /** Milisegundos de un reloj que no retrocede. */
    private val now: () -> Long = SystemClock::elapsedRealtime,
    private val minInterval: Duration = MIN_INTERVAL,
) : TownRepository {

    private val mutex = Mutex()
    private var locatedAt: Long? = null

    // Como la ciudad (CityRepository), la primera vez se lee del teléfono antes de seguir: el feed la muestra al abrir.
    private val town: MutableStateFlow<ExploredTown> by lazy { MutableStateFlow(runBlocking { start() }) }

    override val current: StateFlow<ExploredTown> get() = town

    override suspend fun refresh(force: Boolean) = mutex.withLock {
        if (town.value is ExploredTown.Chosen) return@withLock
        if (!hasPermission()) {
            town.value = ExploredTown.Unknown
            return@withLock
        }
        val last = locatedAt
        if (!force && last != null && now() - last < minInterval.inWholeMilliseconds) return@withLock
        // Sin ubicación, sin red o en un punto sin municipio, se queda el de antes.
        val found = runCatchingNonCancellation { addresses.municipalityOf(location.currentLocation()) } ?: return@withLock
        locatedAt = now()
        store.edit { it[LOCATED_KEY] = json.encodeToString(found) }
        town.value = ExploredTown.Located(found)
    }

    override suspend fun firstAsk(): Boolean {
        if (store.data.first()[ASKED_KEY] == true) return false
        store.edit { it[ASKED_KEY] = true }
        return true
    }

    override suspend fun choose(option: TownOption) = mutex.withLock {
        val chosen = SavedChoice(option.municipality.name, option.municipality.region, option.center.latitude, option.center.longitude)
        store.edit { it[CHOSEN_KEY] = json.encodeToString(chosen) }
        town.value = chosen.toTown()
    }

    override suspend fun useLocation() {
        mutex.withLock {
            store.edit { it.remove(CHOSEN_KEY) }
            town.value = located()
        }
        refresh(force = true)
    }

    override suspend fun withPlaces(): List<TownOption> = townsWithPlaces()

    override suspend fun search(query: String): List<TownOption> = addresses.searchTowns(query)

    private suspend fun start(): ExploredTown = chosen()?.toTown() ?: located()

    /** El último que se supo, si todavía hay permiso de ubicación. */
    private suspend fun located(): ExploredTown {
        if (!hasPermission()) return ExploredTown.Unknown
        val saved = store.data.first()[LOCATED_KEY] ?: return ExploredTown.Unknown
        return runCatching { json.decodeFromString<Municipality>(saved) }.getOrNull()?.let { ExploredTown.Located(it) } ?: ExploredTown.Unknown
    }

    private suspend fun chosen(): SavedChoice? {
        val saved = store.data.first()[CHOSEN_KEY] ?: return null
        return runCatching { json.decodeFromString<SavedChoice>(saved) }.getOrNull()
    }

    @Serializable
    private data class SavedChoice(val name: String, val region: String? = null, val latitude: Double, val longitude: Double) {
        fun toTown() = ExploredTown.Chosen(Municipality(name, region), GeoPoint(latitude, longitude))
    }

    companion object {
        /** Volver a Explorar seguido no vuelve a preguntar: en un minuto nadie cambia de municipio. */
        val MIN_INTERVAL = 1.minutes

        private val LOCATED_KEY = stringPreferencesKey("municipio")
        private val CHOSEN_KEY = stringPreferencesKey("ciudad_elegida")
        private val ASKED_KEY = booleanPreferencesKey("permiso_ubicacion_pedido")
        private val json = Json { ignoreUnknownKeys = true }
    }
}
