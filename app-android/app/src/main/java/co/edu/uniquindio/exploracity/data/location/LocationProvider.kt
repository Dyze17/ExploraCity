package co.edu.uniquindio.exploracity.data.location

import co.edu.uniquindio.exploracity.domain.model.GeoPoint

/** Ubicación de la persona. Solo se consulta con el permiso de ubicación concedido. */
interface LocationProvider {
    suspend fun currentLocation(): GeoPoint
}

/** Punto simulado en Bogotá (cerca del Parque Nacional), donde están los lugares de prueba. */
val SIMULATED_LOCATION = GeoPoint(4.6275, -74.0655)

/**
 * Temporal mientras los lugares sean de prueba: todos están en Bogotá, y con el GPS real, fuera de la ciudad,
 * el mapa quedaría vacío. Se cambia en AppContainer por uno con FusedLocationProvider al llegar los datos reales.
 */
class SimulatedLocationProvider(private val location: GeoPoint = SIMULATED_LOCATION) : LocationProvider {
    override suspend fun currentLocation(): GeoPoint = location
}
