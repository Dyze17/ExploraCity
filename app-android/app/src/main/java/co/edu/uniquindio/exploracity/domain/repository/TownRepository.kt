package co.edu.uniquindio.exploracity.domain.repository

import co.edu.uniquindio.exploracity.domain.model.GeoPoint
import co.edu.uniquindio.exploracity.domain.model.Municipality
import co.edu.uniquindio.exploracity.domain.model.TownOption
import kotlinx.coroutines.flow.StateFlow

/** El municipio que se explora (7) y de dónde salió. */
sealed interface ExploredTown {
    val municipality: Municipality?

    /** ADR-16 · El de la ubicación de la persona. */
    data class Located(override val municipality: Municipality) : ExploredTown

    /** v1.3.0 · Elegido a mano en «Elegir ciudad»; [center] es desde donde se miden las distancias y abre el mapa. */
    data class Chosen(override val municipality: Municipality, val center: GeoPoint) : ExploredTown

    /** v1.3.0 · Sin ubicación ni ciudad elegida: el feed pide elegir una, en vez de mostrar la de la API. */
    data object Unknown : ExploredTown {
        override val municipality: Municipality? get() = null
    }
}

/** ADR-16 · El municipio que se explora: el de «Estás explorando …» (7) y el de «Toda la ciudad» (9). */
interface TownRepository {
    /** El de ahora: el de la persona, el que eligió a mano o, si no hay ninguno, [ExploredTown.Unknown]. */
    val current: StateFlow<ExploredTown>

    /**
     * Vuelve a preguntar dónde está la persona, si sigue su ubicación; con una ciudad elegida no hace nada. Con [force]
     * pregunta aunque lo haya hecho hace poco: se acaba de dar el permiso.
     */
    suspend fun refresh(force: Boolean = false)

    /** true solo la primera vez: el permiso de ubicación se pide al abrir Explorar por primera vez en el teléfono. */
    suspend fun firstAsk(): Boolean

    /** v1.3.0 · Elegir una ciudad a mano: se queda hasta volver a «Usar mi ubicación». */
    suspend fun choose(option: TownOption)

    /** v1.3.0 · Volver a seguir la ubicación de la persona. */
    suspend fun useLocation()

    /** v1.3.0 · Las ciudades con lugares publicados, de las que más tienen. Lanza excepción si falla la red. */
    suspend fun withPlaces(): List<TownOption>

    /** v1.3.0 · Ciudades y pueblos que se llaman como [query], en cualquier país. Lanza excepción si falla la red. */
    suspend fun search(query: String): List<TownOption>
}
