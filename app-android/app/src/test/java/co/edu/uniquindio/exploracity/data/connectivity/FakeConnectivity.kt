package co.edu.uniquindio.exploracity.data.connectivity

import kotlinx.coroutines.flow.MutableStateFlow

/** Red de prueba: se prende y se apaga a mano con [online]. */
class FakeConnectivity(online: Boolean = true) : ConnectivityObserver {
    override val isOnline = MutableStateFlow(online)

    var online: Boolean
        get() = isOnline.value
        set(value) {
            isOnline.value = value
        }
}
