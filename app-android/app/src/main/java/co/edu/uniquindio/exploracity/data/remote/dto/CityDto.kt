package co.edu.uniquindio.exploracity.data.remote.dto

import co.edu.uniquindio.exploracity.domain.model.City
import co.edu.uniquindio.exploracity.domain.model.GeoBounds
import co.edu.uniquindio.exploracity.domain.model.GeoPoint
import kotlinx.serialization.Serializable

// DTO de la API (docs/api): la forma del JSON, separada del dominio.

@Serializable
data class GeoPointDto(val latitude: Double, val longitude: Double) {
    fun toDomain() = GeoPoint(latitude, longitude)

    companion object {
        fun of(point: GeoPoint) = GeoPointDto(point.latitude, point.longitude)
    }
}

@Serializable
data class GeoBoundsDto(val southwest: GeoPointDto, val northeast: GeoPointDto) {
    fun toDomain() = GeoBounds(southwest.toDomain(), northeast.toDomain())

    companion object {
        fun of(bounds: GeoBounds) = GeoBoundsDto(GeoPointDto.of(bounds.southwest), GeoPointDto.of(bounds.northeast))
    }
}

@Serializable
data class CityDto(val name: String, val center: GeoPointDto, val bounds: GeoBoundsDto) {
    fun toDomain() = City(name, center.toDomain(), bounds.toDomain())

    companion object {
        fun of(city: City) = CityDto(city.name, GeoPointDto.of(city.center), GeoBoundsDto.of(city.bounds))
    }
}
