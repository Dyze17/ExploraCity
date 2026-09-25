package co.edu.uniquindio.exploracity.data

import co.edu.uniquindio.exploracity.data.location.LocationProvider
import co.edu.uniquindio.exploracity.data.location.SimulatedLocationProvider
import co.edu.uniquindio.exploracity.data.repository.FakeModerationRepository
import co.edu.uniquindio.exploracity.data.repository.FakePoiRepository
import co.edu.uniquindio.exploracity.data.repository.ModerationRepository
import co.edu.uniquindio.exploracity.data.repository.PoiRepository
import co.edu.uniquindio.exploracity.data.repository.sampleCurrentUser
import co.edu.uniquindio.exploracity.domain.model.Author
import co.edu.uniquindio.exploracity.domain.model.GeoPoint

/**
 * Dependencias de la app (inyección manual). Hoy con repositorios en memoria; al llegar la API se cambian
 * aquí por los que usan data/remote (Ktor Client) y data/local (Room, DataStore) sin tocar la UI.
 */
class AppContainer {
    /** Temporal: llegará de la sesión (JWT en DataStore) cuando exista el inicio de sesión real. */
    val currentUser: Author = sampleCurrentUser

    val poiRepository: PoiRepository = FakePoiRepository(currentUser = currentUser)
    val moderationRepository: ModerationRepository = FakeModerationRepository()
    val locationProvider: LocationProvider = SimulatedLocationProvider()

    /** Temporal: llegará de la ubicación del dispositivo y la geocodificación de Google Maps (ADR-08). */
    val areaName: String = "Bogotá"

    /** Centro del área: donde abre el mapa (8) mientras no haya permiso de ubicación. Temporal como [areaName]. */
    val areaCenter: GeoPoint = GeoPoint(4.6097, -74.0817)
}
