package co.edu.uniquindio.exploracity.data

import co.edu.uniquindio.exploracity.data.repository.FakeModerationRepository
import co.edu.uniquindio.exploracity.data.repository.FakePoiRepository
import co.edu.uniquindio.exploracity.data.repository.ModerationRepository
import co.edu.uniquindio.exploracity.data.repository.PoiRepository

/**
 * Dependencias de la app (inyección manual). Hoy con repositorios en memoria; al llegar la API se cambian
 * aquí por los que usan data/remote (Ktor Client) y data/local (Room, DataStore) sin tocar la UI.
 */
class AppContainer {
    val poiRepository: PoiRepository = FakePoiRepository()
    val moderationRepository: ModerationRepository = FakeModerationRepository()

    /** Temporal: llegará de la ubicación del dispositivo y la geocodificación de Google Maps (ADR-08). */
    val areaName: String = "Bogotá"
}
