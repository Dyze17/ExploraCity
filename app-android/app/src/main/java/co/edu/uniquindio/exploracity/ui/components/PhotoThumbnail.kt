package co.edu.uniquindio.exploracity.ui.components

import android.graphics.ImageDecoder
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Miniatura de una foto del teléfono (19.b), decodificada al tamaño en que se ve. Mientras carga, o si no se puede leer,
 * queda el fondo. Decorativa: la fila que la contiene dice de qué foto se trata.
 */
@Composable
fun PhotoThumbnail(path: String, modifier: Modifier = Modifier, size: Dp = 72.dp) {
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    val bitmap by produceState<ImageBitmap?>(null, path, sizePx) { value = withContext(Dispatchers.IO) { decodeThumbnail(path, sizePx) } }
    Box(modifier.size(size).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        bitmap?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
    }
}

private fun decodeThumbnail(path: String, sizePx: Int): ImageBitmap? = try {
    ImageDecoder.decodeBitmap(ImageDecoder.createSource(File(path))) { decoder, info, _ ->
        // Recortada al cuadrado: basta que el lado menor llegue al tamaño de la miniatura.
        val scale = sizePx.toFloat() / min(info.size.width, info.size.height)
        if (scale < 1f) decoder.setTargetSize((info.size.width * scale).roundToInt(), (info.size.height * scale).roundToInt())
    }.asImageBitmap()
} catch (e: IOException) {
    null
}
