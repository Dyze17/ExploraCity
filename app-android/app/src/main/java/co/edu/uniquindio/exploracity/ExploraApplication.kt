package co.edu.uniquindio.exploracity

import android.app.Application
import co.edu.uniquindio.exploracity.data.AppContainer

class ExploraApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
