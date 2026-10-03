package co.edu.uniquindio.exploracity.model

import kotlinx.serialization.Serializable

/** Coordenada en grados, como GeoPoint de la app. */
@Serializable
data class GeoPoint(val latitude: Double, val longitude: Double)

/** Rectángulo de coordenadas, como GeoBounds de la app. */
@Serializable
data class GeoBounds(val southwest: GeoPoint, val northeast: GeoPoint)
