package co.edu.uniquindio.exploracity.data.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import java.io.IOException

/** Si el teléfono tiene internet ahora. La app pasa a sus datos guardados (12.a, 8.c) cuando no. */
interface ConnectivityObserver {
    val isOnline: StateFlow<Boolean>
}

/** Se pidió algo que necesita internet sin conexión, y no había nada guardado que mostrar en su lugar. */
class OfflineException : IOException("Sin conexión")

/**
 * Red por defecto del sistema. Cuenta como conexión solo la que Android validó (con salida a internet real): un
 * wifi con portal cautivo no sirve para traer lugares.
 */
class AndroidConnectivityObserver(context: Context, scope: CoroutineScope) : ConnectivityObserver {
    private val manager = context.getSystemService(ConnectivityManager::class.java)

    override val isOnline: StateFlow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                trySend(capabilities.hasInternet())
            }

            override fun onLost(network: Network) {
                trySend(false)
            }
        }
        manager.registerDefaultNetworkCallback(callback)
        awaitClose { manager.unregisterNetworkCallback(callback) }
    }
        .distinctUntilChanged()
        // Desde el arranque: los repositorios leen el valor sin suscribirse.
        .stateIn(scope, SharingStarted.Eagerly, initialValue = currentlyOnline())

    private fun currentlyOnline(): Boolean = manager.getNetworkCapabilities(manager.activeNetwork)?.hasInternet() ?: false

    private fun NetworkCapabilities.hasInternet() =
        hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
