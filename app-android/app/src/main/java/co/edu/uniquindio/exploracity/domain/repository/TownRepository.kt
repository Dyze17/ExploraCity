package co.edu.uniquindio.exploracity.domain.repository

import co.edu.uniquindio.exploracity.domain.model.Municipality
import kotlinx.coroutines.flow.StateFlow

/** ADR-16 · El municipio que se explora: el de «Estás explorando …» (7) y el de «Toda la ciudad» (9). */
interface TownRepository {
    /** El de ahora: el de la persona o, mientras no se sabe dónde está, la ciudad de la API. */
    val current: StateFlow<Municipality>

    /**
     * Vuelve a preguntar dónde está la persona; sin permiso de ubicación vuelve a la ciudad de la API. Con [force]
     * pregunta aunque lo haya hecho hace poco: se acaba de dar el permiso.
     */
    suspend fun refresh(force: Boolean = false)

    /** true solo la primera vez: el permiso de ubicación se pide al abrir Explorar por primera vez en el teléfono. */
    suspend fun firstAsk(): Boolean
}
