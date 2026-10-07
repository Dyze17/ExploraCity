package co.edu.uniquindio.exploracity.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage

/**
 * Una foto publicada (su URL en el servidor de imágenes) que llena su contenedor. Va encima del contenedor neutro del
 * lienzo, que sigue a la vista mientras carga, si no hay conexión ni copia guardada, o si no hay foto ([url] null).
 * Decorativa: quien la contiene dice de qué foto se trata.
 */
@Composable
fun RemotePhoto(url: String?, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    if (url.isNullOrEmpty()) return
    AsyncImage(model = url, contentDescription = null, contentScale = contentScale, modifier = modifier.fillMaxSize())
}

/** Una foto del servidor y no un archivo del teléfono: las de 19 y 28 se leen del disco, las publicadas se descargan. */
internal fun isRemotePhoto(path: String): Boolean = path.startsWith("https://") || path.startsWith("http://")
