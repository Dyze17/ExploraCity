package co.edu.uniquindio.exploracity.domain.model

import kotlinx.serialization.Serializable

/**
 * La ciudad que atiende la app (la decide la API): dónde abre el mapa (8) sin permiso de ubicación y hasta dónde llega
 * la búsqueda por dirección (17.b). Es también la que se explora mientras no se sabe en qué municipio está la persona
 * (ADR-16); [region] es su departamento.
 */
data class City(val name: String, val center: GeoPoint, val bounds: GeoBounds, val region: String? = null) {
    val municipality: Municipality get() = Municipality(name, region)
}

/**
 * ADR-16 · Un municipio («Calarcá») y su departamento («Quindío»), como los nombra el Geocoder del teléfono. El
 * departamento separa los que se llaman igual: hay una Armenia en el Quindío y otra en Antioquia.
 */
@Serializable
data class Municipality(val name: String, val region: String? = null)
