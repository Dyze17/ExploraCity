package co.edu.uniquindio.exploracity.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import co.edu.uniquindio.exploracity.data.remote.CityApi
import co.edu.uniquindio.exploracity.data.remote.dto.CityDto
import co.edu.uniquindio.exploracity.domain.model.City
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

/**
 * B1 · La ciudad que atiende la app (nombre, centro del mapa y límites de la búsqueda por dirección): la decide la API
 * (`/v1/city`) y se guarda en el teléfono para abrir sin conexión. La app no entra sin ella: el arranque (1), el inicio
 * de sesión (3) y el registro (4) la dejan lista antes de abrir el feed.
 */
class CityRepository(private val api: CityApi, private val store: DataStore<Preferences>) {

    @Volatile
    private var loaded: City? = null

    /**
     * La ciudad guardada, para las pantallas de la app. La primera vez la lee del teléfono antes de seguir, como el
     * tema al abrir (MainActivity): Android puede volver a abrir la app directo en el feed, sin pasar por el arranque.
     */
    val current: City
        get() = loaded ?: runBlocking { saved() } ?: error("La ciudad se deja lista antes de entrar a la app")

    /** La guardada o, si no hay ninguna, la de la API. Lanza excepción si no hay ninguna y falla la red. */
    suspend fun ensure(): City = loaded ?: saved() ?: refresh()

    /** La de la API, que queda guardada. Lanza excepción si falla la red. */
    suspend fun refresh(): City {
        val city = api.city()
        store.edit { it[CITY_KEY] = cityJson.encodeToString(CityDto.of(city)) }
        loaded = city
        return city
    }

    private suspend fun saved(): City? {
        val json = store.data.first()[CITY_KEY] ?: return null
        return runCatching { cityJson.decodeFromString<CityDto>(json).toDomain() }.getOrNull()?.also { loaded = it }
    }

    private companion object {
        val CITY_KEY = stringPreferencesKey("ciudad")
        val cityJson = Json { ignoreUnknownKeys = true }
    }
}
