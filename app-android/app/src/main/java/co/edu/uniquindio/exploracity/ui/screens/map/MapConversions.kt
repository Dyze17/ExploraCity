package co.edu.uniquindio.exploracity.ui.screens.map

import co.edu.uniquindio.exploracity.domain.model.GeoBounds
import co.edu.uniquindio.exploracity.domain.model.GeoPoint
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds

// Del dominio (sin tipos de Google Maps) a los del SDK y de vuelta: solo la UI conoce el SDK.

internal fun GeoPoint.toLatLng() = LatLng(latitude, longitude)

internal fun LatLng.toGeoPoint() = GeoPoint(latitude, longitude)

internal fun LatLngBounds.toGeoBounds() = GeoBounds(
    southwest = GeoPoint(southwest.latitude, southwest.longitude),
    northeast = GeoPoint(northeast.latitude, northeast.longitude),
)

internal fun GeoBounds.toLatLngBounds() = LatLngBounds(southwest.toLatLng(), northeast.toLatLng())
