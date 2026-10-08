package co.edu.uniquindio.exploracity.data.local

import androidx.datastore.preferences.core.stringPreferencesKey
import co.edu.uniquindio.exploracity.domain.model.Account
import co.edu.uniquindio.exploracity.domain.model.UserRole
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** D1 · La sesión con la API en el DataStore de sesión: los tokens y la cuenta, junto al rol. */
class TokenStoreTest {

    private val dataStore = MemoryDataStore()
    private val tokens = DataStoreTokenStore(dataStore)
    private val accounts = DataStoreAccountStore(dataStore)
    private val roles = DataStoreSessionStore(dataStore)

    @Test
    fun `guarda los dos tokens y los borra al cerrar la sesión`() = runTest {
        assertNull(tokens.tokens())

        tokens.save(AuthTokens("acceso", "renovacion"))
        assertEquals(AuthTokens("acceso", "renovacion"), tokens.tokens())

        tokens.clear()
        assertNull(tokens.tokens())
    }

    @Test
    fun `guarda la cuenta con su correo pendiente, y lo quita al confirmarlo`() = runTest {
        accounts.save(SessionAccount("ana", Account("ana@correo.com", pendingEmail = "nueva@correo.com")))
        assertEquals(SessionAccount("ana", Account("ana@correo.com", "nueva@correo.com")), accounts.account.first())

        accounts.save(SessionAccount("ana", Account("nueva@correo.com")))
        assertEquals(SessionAccount("ana", Account("nueva@correo.com")), accounts.account.first())

        accounts.clear()
        assertNull(accounts.account.first())
    }

    @Test
    fun `recuerda si la cuenta entra solo con Google, y una sesión de antes lo da por sabido`() = runTest {
        accounts.save(SessionAccount("pedro", Account("pedro@gmail.com", hasPassword = false)))
        assertEquals(SessionAccount("pedro", Account("pedro@gmail.com", hasPassword = false)), accounts.account.first())

        accounts.clear()
        // Una sesión guardada antes de ADR-15 no tiene la marca: esas cuentas tienen contraseña.
        dataStore.updateData { prefs ->
            prefs.toMutablePreferences().apply {
                this[stringPreferencesKey("persona")] = "ana"
                this[stringPreferencesKey("correo")] = "ana@correo.com"
            }
        }
        assertEquals(true, accounts.account.first()?.account?.hasPassword)
    }

    @Test
    fun `comparten el archivo de la sesión sin pisar el rol`() = runTest {
        roles.open(UserRole.MODERATOR)
        tokens.save(AuthTokens("acceso", "renovacion"))
        accounts.save(SessionAccount("laura", Account("laura@correo.com")))

        tokens.clear()
        accounts.clear()

        assertEquals(UserRole.MODERATOR, roles.role.first())
    }
}
