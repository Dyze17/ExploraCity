package co.edu.uniquindio.exploracity.data.remote.dto

import co.edu.uniquindio.exploracity.domain.model.City
import co.edu.uniquindio.exploracity.domain.model.GeoBounds
import co.edu.uniquindio.exploracity.domain.model.GeoPoint
import kotlinx.serialization.Serializable

// DTO de la API (docs/api): la forma del JSON, separada del dominio.

@Serializable
data class GeoPointDto(val latitude: Double, val longitude: Double) {
    fun toDomain() = GeoPoint(latitude, longitude)
}

@Serializable
data class GeoBoundsDto(val southwest: GeoPointDto, val northeast: GeoPointDto) {
    fun toDomain() = GeoBounds(southwest.toDomain(), northeast.toDomain())
}

@Serializable
data class CityDto(val name: String, val center: GeoPointDto, val bounds: GeoBoundsDto) {
    fun toDomain() = City(name, center.toDomain(), bounds.toDomain())
}
