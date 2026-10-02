package co.edu.uniquindio.exploracity.viewmodel

import co.edu.uniquindio.exploracity.data.local.SessionManager
import co.edu.uniquindio.exploracity.data.repository.AccountRepository
import co.edu.uniquindio.exploracity.domain.model.Account
import co.edu.uniquindio.exploracity.domain.model.DataExport
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

// Dobles de la cuenta y la sesión para 29, 28 y 30: cada operación tarda 1 s de tiempo virtual.

internal class FakeAccounts : AccountRepository {
    var error: Exception? = null
    var exports = 0
    var deleteError: Exception? = null
    var deleted = false

    override fun account() = Account("ana.rios@correo.com")

    override suspend fun exportData(): DataExport {
        exports++
        delay(1.seconds)
        error?.let { throw it }
        return DataExport("exploracity-mis-datos-2026-09-26.json", "{\"correo\":\"ana.rios@correo.com\"}")
    }

    override suspend fun deleteAccount() {
        delay(1.seconds)
        deleteError?.let { throw it }
        deleted = true
    }
}

internal class FakeSession : SessionManager {
    var pending = 0
    var error: Exception? = null
    var signedOut = false
    var accountDataDeleted = false

    override suspend fun pendingSends() = pending

    override suspend fun signOut() {
        delay(1.seconds)
        error?.let { throw it }
        signedOut = true
    }

    override suspend fun deleteAccountData() {
        accountDataDeleted = true
    }
}
