package co.edu.uniquindio.exploracity

import android.app.Application
import android.content.Context
import androidx.work.Configuration
import co.edu.uniquindio.exploracity.data.AppContainer
import co.edu.uniquindio.exploracity.data.sync.ExploraWorkerFactory
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade

class ExploraApplication : Application(), Configuration.Provider, SingletonImageLoader.Factory {
    val container: AppContainer by lazy { AppContainer(this) }

    /**
     * WorkManager se inicia al pedirlo (el inicializador automático está quitado en el manifiesto) con una fábrica que
     * da al worker de la cola las dependencias de la app.
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(ExploraWorkerFactory { container.pendingSender }).build()

    /**
     * Las fotos publicadas se descargan con Ktor, como el resto de la app. La caché en disco guarda las que ya se vieron:
     * los lugares guardados (12.a) las muestran sin conexión.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader = imageLoader(context)
}

private const val PHOTO_CACHE_BYTES = 100L * 1024 * 1024

private fun imageLoader(context: Context): ImageLoader = ImageLoader.Builder(context)
    .components { add(KtorNetworkFetcherFactory()) }
    .diskCache { DiskCache.Builder().directory(context.cacheDir.resolve("fotos")).maxSizeBytes(PHOTO_CACHE_BYTES).build() }
    .crossfade(true)
    .build()
