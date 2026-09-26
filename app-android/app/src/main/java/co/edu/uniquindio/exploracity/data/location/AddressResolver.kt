package co.edu.uniquindio.exploracity.data.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import androidx.annotation.RequiresApi
import co.edu.uniquindio.exploracity.data.connectivity.ConnectivityObserver
import co.edu.uniquindio.exploracity.data.connectivity.OfflineException
import co.edu.uniquindio.exploracity.domain.model.GeoBounds
import co.edu.uniquindio.exploracity.domain.model.GeoPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Dirección aproximada de un punto (17): «Cl. 45 #19-32» en [street], «Chapinero» en [neighborhood] y «Bogotá» en
 * [city]. Cualquiera puede faltar: en un parque no hay calle.
 */
data class ApproximateAddress(val street: String?, val neighborhood: String?, val city: String?) {
    /** La línea de la tarjeta: «Cl. 45 #19-32, Chapinero»; sin calle, «Chapinero, Bogotá». */
    val line: String get() = (if (street != null) listOfNotNull(street, neighborhood ?: city) else listOfNotNull(neighborhood, city)).joinToString(", ")

    /** La etiqueta sobre el mapa: «Chapinero, Bogotá». */
    val area: String? get() = listOfNotNull(neighborhood, city).distinct().joinToString(", ").ifEmpty { null }
}

/** Direcciones de puntos del mapa y búsqueda por dirección (SAD: geocodificación de Google Maps Platform). */
interface AddressResolver {
    /** La dirección de [point]; null si no hay ninguna. Lanza excepción si falla la red o el servicio. */
    suspend fun addressOf(point: GeoPoint): ApproximateAddress?

    /** 17.b · Una dirección o barrio escrito por la persona, dentro de [bounds]; null si no lo encuentra. */
    suspend fun search(query: String, bounds: GeoBounds): GeoPoint?
}

/**
 * Con el Geocoder de Android: lo resuelve Google desde el teléfono, sin clave ni costo (opción A de Daniel). Si más
 * adelante lo hace el backend, se cambia en AppContainer.
 */
class GeocoderAddressResolver(context: Context) : AddressResolver {

    private val geocoder = Geocoder(context.applicationContext, Locale.forLanguageTag("es-CO"))

    override suspend fun addressOf(point: GeoPoint): ApproximateAddress? {
        requirePresent()
        val found = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            geocode { listener -> geocoder.getFromLocation(point.latitude, point.longitude, 1, listener) }
        } else {
            withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(point.latitude, point.longitude, 1).orEmpty()
            }
        }
        return found.firstOrNull()?.toParts()?.toApproximate()
    }

    override suspend fun search(query: String, bounds: GeoBounds): GeoPoint? {
        requirePresent()
        val sw = bounds.southwest
        val ne = bounds.northeast
        val found = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            geocode { listener -> geocoder.getFromLocationName(query, 1, sw.latitude, sw.longitude, ne.latitude, ne.longitude, listener) }
        } else {
            withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                geocoder.getFromLocationName(query, 1, sw.latitude, sw.longitude, ne.latitude, ne.longitude).orEmpty()
            }
        }
        return found.firstOrNull()?.let { GeoPoint(it.latitude, it.longitude) }?.takeIf { it in bounds }
    }

    private fun requirePresent() {
        if (!Geocoder.isPresent()) throw IOException("El teléfono no tiene servicio de direcciones")
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private suspend fun geocode(request: (Geocoder.GeocodeListener) -> Unit): List<Address> =
        suspendCancellableCoroutine { continuation ->
            request(
                object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) {
                        if (continuation.isActive) continuation.resume(addresses)
                    }

                    override fun onError(errorMessage: String?) {
                        if (continuation.isActive) continuation.resumeWithException(IOException(errorMessage ?: "Sin respuesta"))
                    }
                },
            )
        }
}

/** Sin red no se intenta: la tarjeta lo explica y la dirección llega al volver la red. */
class OnlineOnlyAddressResolver(
    private val remote: AddressResolver,
    private val connectivity: ConnectivityObserver,
) : AddressResolver {
    override suspend fun addressOf(point: GeoPoint): ApproximateAddress? {
        requireOnline()
        return remote.addressOf(point)
    }

    override suspend fun search(query: String, bounds: GeoBounds): GeoPoint? {
        requireOnline()
        return remote.search(query, bounds)
    }

    private fun requireOnline() {
        if (!connectivity.isOnline.value) throw OfflineException()
    }
}

/** Los campos de [Address] que sirven, sin tipos de Android para poder probar el formato. */
internal data class AddressParts(
    val firstLine: String?,
    val thoroughfare: String?,
    val subThoroughfare: String?,
    val subLocality: String?,
    val locality: String?,
)

private fun Address.toParts() = AddressParts(
    firstLine = if (maxAddressLineIndex >= 0) getAddressLine(0) else null,
    thoroughfare = thoroughfare,
    subThoroughfare = subThoroughfare,
    subLocality = subLocality,
    locality = locality,
)

/** Código plus («8FVC+XX»): Google lo da cuando no hay calle; no le dice nada a la persona. */
private val plusCode = Regex("^[23456789CFGHJMPQRVWX]{4,8}\\+[23456789CFGHJMPQRVWX]{0,3}(\\s|$)", RegexOption.IGNORE_CASE)

/**
 * La calle sale del primer tramo de la dirección completa («Cl. 45 #19-32, Bogotá, Colombia»), que Google ya escribe
 * como se usa en Colombia; si ese tramo no es una calle (un código plus o el nombre de la ciudad), de la vía y el número.
 */
internal fun AddressParts.toApproximate(): ApproximateAddress? {
    val neighborhood = subLocality?.trim()?.ifEmpty { null }
    val city = locality?.trim()?.ifEmpty { null }
    val first = firstLine?.substringBefore(',')?.trim()?.ifEmpty { null }
    val fromLine = first?.takeUnless { plusCode.containsMatchIn(it) || it == city || it == neighborhood || it == UNNAMED_ROAD }
    val fromParts = thoroughfare?.trim()?.ifEmpty { null }?.takeUnless { it == UNNAMED_ROAD }?.let { road ->
        subThoroughfare?.trim()?.ifEmpty { null }?.let { number -> "$road # ${number.removePrefix("#").trim()}" } ?: road
    }
    val street = fromLine ?: fromParts
    if (street == null && neighborhood == null && city == null) return null
    return ApproximateAddress(street, neighborhood.takeUnless { it == city }, city)
}

private const val UNNAMED_ROAD = "Unnamed Road"
