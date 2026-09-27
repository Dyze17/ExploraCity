package co.edu.uniquindio.exploracity.data.local

import android.content.Context
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/** Escribe en el documento que la persona eligió con el diálogo «Guardar» de Android: la app no necesita permisos. */
fun interface DocumentWriter {
    /** Lanza excepción si no se pudo escribir. */
    suspend fun write(uri: String, content: String)
}

class AndroidDocumentWriter(context: Context) : DocumentWriter {
    private val resolver = context.applicationContext.contentResolver

    override suspend fun write(uri: String, content: String) = withContext(Dispatchers.IO) {
        // «wt»: si la persona eligió un archivo que ya existía, se reemplaza en lugar de escribir encima a medias.
        val stream = resolver.openOutputStream(uri.toUri(), "wt") ?: throw IOException("No se pudo abrir el documento")
        stream.use { it.write(content.toByteArray()) }
    }
}
