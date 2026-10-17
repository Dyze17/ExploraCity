package co.edu.uniquindio.exploracity.domain.model

/**
 * La ciudad que atiende la app (la decide la API): dónde abre el mapa (8) sin permiso de ubicación y hasta dónde llega
 * la búsqueda por dirección (17.b).
 */
data class City(val name: String, val center: GeoPoint, val bounds: GeoBounds)
