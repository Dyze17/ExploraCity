package co.edu.uniquindio.exploracity.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import co.edu.uniquindio.exploracity.data.photos.PhotoStore
import co.edu.uniquindio.exploracity.data.sync.queuedPhotos
import co.edu.uniquindio.exploracity.domain.model.UserRole
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * La sesión abierta en el teléfono (SAD: DataStore de sesión). Hoy guarda el rol con el que se entró (3); con el inicio
 * de sesión real guardará también el token.
 */
interface SessionStore {
    /** null: no hay sesión. */
    val role: Flow<UserRole?>

    suspend fun open(role: UserRole)

    suspend fun close()
}

class DataStoreSessionStore(private val dataStore: DataStore<Preferences>) : SessionStore {
    override val role: Flow<UserRole?> = dataStore.data
        .map { prefs -> UserRole.entries.firstOrNull { it.name == prefs[ROLE_KEY] } }
        .distinctUntilChanged()

    override suspend fun open(role: UserRole) {
        dataStore.edit { it[ROLE_KEY] = role.name }
    }

    override suspend fun close() {
        dataStore.edit { it.remove(ROLE_KEY) }
    }

    private companion object {
        val ROLE_KEY = stringPreferencesKey("rol")
    }
}

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
    private val sessions: SessionStore,
    private val cancelSending: () -> Unit,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : SessionManager {

    override suspend fun pendingSends(): Int = database.pendingActionsDao().count()

    override suspend fun signOut() {
        // Primero la sesión: si algo de lo que sigue falla, al abrir de nuevo la app se pide entrar (3).
        sessions.close()
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
