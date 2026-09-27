package co.edu.uniquindio.exploracity.viewmodel

import co.edu.uniquindio.exploracity.data.connectivity.OfflineException
import co.edu.uniquindio.exploracity.domain.model.DuplicateCheck
import co.edu.uniquindio.exploracity.domain.model.GeoPoint
import co.edu.uniquindio.exploracity.domain.model.SimilarPlace
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** 17 · Lo del mapa mientras se pone el pin: su dirección, la ubicación, la búsqueda por dirección y los parecidos. */
data class PinState(
    /** Dirección del pin; null mientras no esté puesto. */
    val address: PinAddress? = null,
    /** El mapa debe llevar el pin aquí («Usar mi ubicación», búsqueda, ajuste fino). */
    val pinTarget: GeoPoint? = null,
    /** 17.b · Se negó el permiso de ubicación: aviso, búsqueda por dirección y pin a mano. */
    val locationDenied: Boolean = false,
    val addressQuery: String = "",
    val addressSearch: AddressSearch = AddressSearch.Idle,
    /** «Buscando lugares cercanos…» dentro del botón. */
    val searchingNearby: Boolean = false,
    /** 17A/17B abierta. */
    val duplicates: DuplicateReview? = null,
    /** 17A · Se abrió «Ver este lugar»: la hoja se esconde mientras tanto y vuelve al regresar. */
    val awayForPlace: Boolean = false,
)

/**
 * 17 · El pin de un mapa: la dirección de donde quedó, «Usar mi ubicación», la búsqueda por dirección y los lugares
 * parecidos (17A/17B). Lo usan el formulario (17) y la edición (23): cada uno guarda el punto donde le corresponde
 * ([location], [onLocationChange]) y el estado del mapa en el suyo ([read], [write]).
 */
class PinPicker(
    private val scope: CoroutineScope,
    private val places: PublishPlaces,
    private val read: () -> PinState,
    private val write: ((PinState) -> PinState) -> Unit,
    /** Dónde está el pin ahora; null si aún no se puso. */
    private val location: () -> GeoPoint?,
    private val onLocationChange: (GeoPoint) -> Unit,
    /** false mientras el formulario no está listo (se está leyendo). */
    private val enabled: () -> Boolean,
) {
    private var addressJob: Job? = null
    private var searchJob: Job? = null
    private var nearbyJob: Job? = null

    /** Qué hacer cuando la búsqueda de parecidos termina: sin parecidos, si falló o tras «Es otro lugar» (17B). */
    private var onChecked: ((DuplicateCheck) -> Unit)? = null

    init {
        // Sin red la tarjeta promete la dirección para cuando vuelva: se busca sola al reconectar.
        scope.launch {
            places.connectivity.isOnline.drop(1).filter { it }.collect {
                val point = location()
                if (read().address == PinAddress.Offline && point != null) resolveAddress(point)
            }
        }
    }

    /**
     * La persona puso el pin en [point]: al soltarlo tras arrastrar el mapa, con los botones de ajuste fino o al elegir
     * un resultado. Con [moveMap] el mapa lo sigue. Se guarda y se busca su dirección.
     */
    fun onPinMoved(point: GeoPoint, moveMap: Boolean = false) {
        if (!enabled()) return
        if (moveMap) write { it.copy(pinTarget = point) }
        if (point == location()) return
        // Los parecidos que se buscaban eran los del punto anterior.
        cancelNearby()
        onLocationChange(point)
        resolveAddress(point)
    }

    /** El mapa ya llevó el pin a [target]; si mientras tanto llegó otro destino, ese sigue pendiente. */
    fun onPinTargetShown(target: GeoPoint) = write { if (it.pinTarget == target) it.copy(pinTarget = null) else it }

    /** «Usar mi ubicación» con el permiso concedido (o al abrir el mapa si ya lo estaba). */
    fun onUseMyLocation() {
        write { it.copy(locationDenied = false) }
        scope.launch {
            val here = try {
                places.locationProvider.currentLocation()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Sin ubicación el pin sigue donde estaba y se puede mover a mano.
                null
            }
            if (here != null) onPinMoved(here, moveMap = true)
        }
    }

    /** 17.b · Se negó el permiso: el pin queda donde está y aparecen el aviso y la búsqueda por dirección. */
    fun onLocationDenied() = write { it.copy(locationDenied = true) }

    fun onAddressQueryChange(query: String) = write {
        it.copy(addressQuery = query, addressSearch = if (it.addressSearch == AddressSearch.Searching) it.addressSearch else AddressSearch.Idle)
    }

    /** 17.b · Busca lo escrito dentro de la ciudad y, si lo encuentra, lleva el pin allí. */
    fun onSearchAddress() {
        val query = read().addressQuery.trim()
        if (query.isEmpty() || read().addressSearch == AddressSearch.Searching) return
        searchJob?.cancel()
        write { it.copy(addressSearch = AddressSearch.Searching) }
        searchJob = scope.launch {
            val outcome = try {
                withTimeoutOrNull(ADDRESS_TIMEOUT) { Located(places.addresses.search(query, places.cityBounds)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: OfflineException) {
                write { it.copy(addressSearch = AddressSearch.Offline) }
                return@launch
            } catch (e: Exception) {
                null
            }
            val point = outcome?.point
            write {
                it.copy(
                    addressSearch = when {
                        outcome == null -> AddressSearch.Failed
                        point == null -> AddressSearch.NotFound(query)
                        else -> AddressSearch.Idle
                    },
                )
            }
            if (point != null) onPinMoved(point, moveMap = true)
        }
    }

    /** La dirección aproximada de [point]; la anterior se descarta si el pin se movió. */
    fun resolveAddress(point: GeoPoint) {
        addressJob?.cancel()
        write { it.copy(address = PinAddress.Loading) }
        addressJob = scope.launch {
            val address = try {
                withTimeoutOrNull(ADDRESS_TIMEOUT) { places.addresses.addressOf(point)?.let { PinAddress.Found(it) } ?: PinAddress.NotFound }
            } catch (e: CancellationException) {
                throw e
            } catch (e: OfflineException) {
                PinAddress.Offline
            } catch (e: Exception) {
                null
            }
            write { it.copy(address = address ?: PinAddress.Unavailable) }
        }
    }

    /**
     * Busca parecidos a menos de 50 m del pin, como máximo 500 ms. Sin parecidos, o si falla o tarda, [onChecked] recibe
     * el resultado al momento (el fallo queda anotado para que el servidor repita la búsqueda); con parecidos abre 17A y
     * [onChecked] llega tras «Es otro lugar» (17B). [previousNote] es la nota que ya se había escrito para este lugar;
     * [excludeId], la publicación que se edita o se reenvía, que no cuenta como parecida.
     */
    fun searchNearby(title: String, previousNote: String, excludeId: String? = null, onChecked: (DuplicateCheck) -> Unit) {
        val point = location() ?: return
        nearbyJob?.cancel()
        this.onChecked = onChecked
        write { it.copy(searchingNearby = true) }
        nearbyJob = scope.launch {
            val found = try {
                withTimeoutOrNull(NEARBY_TIMEOUT) { places.duplicateFinder.similarPlaces(title.trim(), point, excludeId) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            write { it.copy(searchingNearby = false) }
            if (found.isNullOrEmpty()) {
                this@PinPicker.onChecked = null
                onChecked(DuplicateCheck(point, failed = found == null))
            } else {
                write { it.copy(duplicates = DuplicateReview(found, note = previousNote)) }
            }
        }
    }

    /** Deja de buscar parecidos (se movió el pin o se volvió atrás). */
    fun cancelNearby() {
        nearbyJob?.cancel()
        write { it.copy(searchingNearby = false) }
    }

    /** 17A · Deslizar la hoja hacia abajo o «Atrás»: vuelve al mapa con el pin donde estaba. */
    fun onDuplicatesDismiss() = write { it.copy(duplicates = null) }

    /** 17A · «Es otro lugar, continuar» → 17B en la misma hoja. */
    fun onNotSamePlace() = write { it.copy(duplicates = it.duplicates?.copy(different = true)) }

    /** 17B · «Volver a los lugares parecidos» o la flecha. */
    fun onBackToSimilar() = write { it.copy(duplicates = it.duplicates?.copy(different = false)) }

    fun onDuplicateNoteChange(note: String) = write { it.copy(duplicates = it.duplicates?.copy(note = note)) }

    /** 17B · «Continuar»: la marca «posible duplicado» y la nota. */
    fun onConfirmDifferent() {
        val review = read().duplicates ?: return
        val point = location() ?: return
        val done = onChecked ?: return
        onChecked = null
        write { it.copy(duplicates = null) }
        done(DuplicateCheck(point, review.places.map(SimilarPlace::id), review.note.trim()))
    }

    /** 17A · «Ver este lugar»: todo queda intacto y la hoja vuelve al regresar. */
    fun onLeaveForPlace() = write { it.copy(awayForPlace = true) }

    fun onBackFromPlace() = write { it.copy(awayForPlace = false) }

    /** Cierra el mapa sin salir del formulario (23): se descarta lo que siga en curso y la búsqueda por dirección. */
    fun reset() {
        searchJob?.cancel()
        nearbyJob?.cancel()
        onChecked = null
        write {
            it.copy(pinTarget = null, addressQuery = "", addressSearch = AddressSearch.Idle, searchingNearby = false, duplicates = null, awayForPlace = false)
        }
    }

    /** Sale del mapa: nada de lo que siga en curso debe tocar el formulario después. */
    fun cancelAll() {
        addressJob?.cancel()
        searchJob?.cancel()
        nearbyJob?.cancel()
    }

    private class Located(val point: GeoPoint?)

    companion object {
        /** README · Duplicados: «máx. 500 ms»; después se sigue sin aviso. */
        val NEARBY_TIMEOUT = 500.milliseconds

        /** La dirección del pin o la búsqueda por dirección: después se muestran solo las coordenadas. */
        val ADDRESS_TIMEOUT = 8.seconds
    }
}
