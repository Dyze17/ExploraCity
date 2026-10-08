package co.edu.uniquindio.exploracity.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import co.edu.uniquindio.exploracity.domain.model.Account
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** D1 · Los dos tokens de la sesión con la API: el de acceso (15 minutos) y el de renovación (30 días). */
data class AuthTokens(val access: String, val refresh: String)

/**
 * Los tokens de la sesión (SAD: DataStore de sesión). Viven en el mismo archivo que el rol, que está fuera de las
 * copias de seguridad y de la transferencia entre teléfonos (data_extraction_rules.xml).
 */
interface TokenStore {
    suspend fun tokens(): AuthTokens?

    suspend fun save(tokens: AuthTokens)

    suspend fun clear()
}

/** La cuenta de la sesión: quién es ([userId]) y su correo (29), para verlos sin red. */
data class SessionAccount(val userId: String, val account: Account)

interface AccountStore {
    /** null: no hay sesión. */
    val account: Flow<SessionAccount?>

    suspend fun save(account: SessionAccount)

    suspend fun clear()
}

class DataStoreTokenStore(private val dataStore: DataStore<Preferences>) : TokenStore {
    override suspend fun tokens(): AuthTokens? {
        val prefs = dataStore.data.first()
        val access = prefs[ACCESS_KEY] ?: return null
        val refresh = prefs[REFRESH_KEY] ?: return null
        return AuthTokens(access, refresh)
    }

    override suspend fun save(tokens: AuthTokens) {
        dataStore.edit {
            it[ACCESS_KEY] = tokens.access
            it[REFRESH_KEY] = tokens.refresh
        }
    }

    override suspend fun clear() {
        dataStore.edit {
            it.remove(ACCESS_KEY)
            it.remove(REFRESH_KEY)
        }
    }

    private companion object {
        val ACCESS_KEY = stringPreferencesKey("token_acceso")
        val REFRESH_KEY = stringPreferencesKey("token_renovacion")
    }
}

class DataStoreAccountStore(private val dataStore: DataStore<Preferences>) : AccountStore {
    override val account: Flow<SessionAccount?> = dataStore.data
        .map { prefs ->
            val userId = prefs[USER_KEY]
            val email = prefs[EMAIL_KEY]
            // Sin la marca (una sesión de antes de ADR-15), la cuenta tiene contraseña.
            if (userId == null || email == null) null else SessionAccount(userId, Account(email, prefs[PENDING_KEY], prefs[HAS_PASSWORD_KEY] ?: true))
        }
        .distinctUntilChanged()

    override suspend fun save(account: SessionAccount) {
        dataStore.edit {
            it[USER_KEY] = account.userId
            it[EMAIL_KEY] = account.account.email
            val pending = account.account.pendingEmail
            if (pending == null) it.remove(PENDING_KEY) else it[PENDING_KEY] = pending
            it[HAS_PASSWORD_KEY] = account.account.hasPassword
        }
    }

    override suspend fun clear() {
        dataStore.edit {
            it.remove(USER_KEY)
            it.remove(EMAIL_KEY)
            it.remove(PENDING_KEY)
            it.remove(HAS_PASSWORD_KEY)
        }
    }

    private companion object {
        val USER_KEY = stringPreferencesKey("persona")
        val EMAIL_KEY = stringPreferencesKey("correo")
        val PENDING_KEY = stringPreferencesKey("correo_pendiente")
        val HAS_PASSWORD_KEY = booleanPreferencesKey("tiene_contrasena")
    }
}
