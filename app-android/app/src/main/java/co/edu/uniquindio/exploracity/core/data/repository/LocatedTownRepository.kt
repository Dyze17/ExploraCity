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
import co.edu.uniquindio.exploracity.domain.model.Municipality
import co.edu.uniquindio.exploracity.domain.repository.TownRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * ADR-16 · El municipio de la persona según el Geocoder del teléfono, el mismo que da la dirección del pin (17). El
 * último que se supo queda guardado para abrir sin conexión. Sin permiso de ubicación, o mientras no se sabe dónde está,
 * es [defaultTown]: la ciudad de la API.
 */
class LocatedTownRepository(
    private val defaultTown: () -> Municipality,
    private val location: LocationProvider,
    private val addresses: AddressResolver,
    private val store: DataStore<Preferences>,
    private val hasPermission: () -> Boolean,
    /** Milisegundos de un reloj que no retrocede. */
    private val now: () -> Long = SystemClock::elapsedRealtime,
    private val minInterval: Duration = MIN_INTERVAL,
) : TownRepository {

    private val mutex = Mutex()
    private var locatedAt: Long? = null

    // Como la ciudad (CityRepository), la primera vez se lee del teléfono antes de seguir: el feed la muestra al abrir.
    private val town: MutableStateFlow<Municipality> by lazy {
        MutableStateFlow(if (hasPermission()) runBlocking { saved() } ?: defaultTown() else defaultTown())
    }

    override val current: StateFlow<Municipality> get() = town

    override suspend fun refresh(force: Boolean) = mutex.withLock {
        if (!hasPermission()) {
            town.value = defaultTown()
            return@withLock
        }
        val last = locatedAt
        if (!force && last != null && now() - last < minInterval.inWholeMilliseconds) return@withLock
        // Sin ubicación, sin red o en un punto sin municipio, se queda el de antes.
        val found = runCatchingNonCancellation { addresses.municipalityOf(location.currentLocation()) } ?: return@withLock
        locatedAt = now()
        store.edit { it[TOWN_KEY] = townJson.encodeToString(found) }
        town.value = found
    }

    override suspend fun firstAsk(): Boolean {
        if (store.data.first()[ASKED_KEY] == true) return false
        store.edit { it[ASKED_KEY] = true }
        return true
    }

    private suspend fun saved(): Municipality? {
        val json = store.data.first()[TOWN_KEY] ?: return null
        return runCatching { townJson.decodeFromString<Municipality>(json) }.getOrNull()
    }

    companion object {
        /** Volver a Explorar seguido no vuelve a preguntar: en un minuto nadie cambia de municipio. */
        val MIN_INTERVAL = 1.minutes

        private val TOWN_KEY = stringPreferencesKey("municipio")
        private val ASKED_KEY = booleanPreferencesKey("permiso_ubicacion_pedido")
        private val townJson = Json { ignoreUnknownKeys = true }
    }
}
