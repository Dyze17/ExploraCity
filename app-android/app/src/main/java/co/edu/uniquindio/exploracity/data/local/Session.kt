package co.edu.uniquindio.exploracity.data.local

import co.edu.uniquindio.exploracity.data.photos.PhotoStore
import co.edu.uniquindio.exploracity.data.sync.queuedPhotos
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 29A · La sesión en el teléfono. Con el inicio de sesión real, cerrarla también borrará el token (DataStore de sesión). */
interface SessionManager {
    /** Acciones hechas sin conexión que aún no llegan al servidor: se pierden al cerrar sesión. */
    suspend fun pendingSends(): Int

    /** Borra lo de la cuenta y conserva lo del teléfono: los borradores (con sus fotos) y el tema. */
    suspend fun signOut()

    /** 30 · La cuenta ya no existe: se borra todo lo suyo, también los borradores y sus fotos. Solo queda el tema. */
    suspend fun deleteAccountData()
}

class LocalSessionManager(
    private val database: ExploraDatabase,
    private val photos: PhotoStore,
    private val drafts: DraftRepository,
    private val cancelSending: () -> Unit,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : SessionManager {

    override suspend fun pendingSends(): Int = database.pendingActionsDao().count()

    override suspend fun signOut() {
        cancelSending()
        val queue = database.pendingActionsDao()
        // Las fotos de la cola comparten carpeta con las de los borradores: se borran solo las que esperaban envío.
        (queue.ofType(PendingType.PUBLICATION) + queue.ofType(PendingType.PUBLICATION_PHOTO))
            .flatMap { it.queuedPhotos() }
            .filter { it.path.isNotEmpty() }
            .forEach { photos.delete(it) }
        // Lo guardado para ver sin conexión, la cola, los avisos y el perfil son de esta cuenta.
        withContext(io) { database.clearAllTables() }
    }

    override suspend fun deleteAccountData() {
        signOut()
        drafts.clearAll()
        photos.deleteAll()
    }
}
