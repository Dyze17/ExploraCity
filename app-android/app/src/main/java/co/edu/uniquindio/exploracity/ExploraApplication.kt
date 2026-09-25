package co.edu.uniquindio.exploracity

import android.app.Application
import androidx.work.Configuration
import co.edu.uniquindio.exploracity.data.AppContainer
import co.edu.uniquindio.exploracity.data.sync.ExploraWorkerFactory

class ExploraApplication : Application(), Configuration.Provider {
    val container: AppContainer by lazy { AppContainer(this) }

    /**
     * WorkManager se inicia al pedirlo (el inicializador automático está quitado en el manifiesto) con una fábrica que
     * da al worker de la cola las dependencias de la app.
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(ExploraWorkerFactory { container.pendingSender }).build()
}
