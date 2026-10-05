package co.edu.uniquindio.exploracity.data.location

import co.edu.uniquindio.exploracity.domain.model.GeoPoint

/** Ubicación de la persona. Solo se consulta con el permiso de ubicación concedido. */
interface LocationProvider {
    suspend fun currentLocation(): GeoPoint
}
