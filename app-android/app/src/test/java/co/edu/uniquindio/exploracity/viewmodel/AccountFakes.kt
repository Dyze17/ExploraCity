package co.edu.uniquindio.exploracity.viewmodel

import co.edu.uniquindio.exploracity.data.local.SessionManager
import co.edu.uniquindio.exploracity.data.repository.AccountRepository
import co.edu.uniquindio.exploracity.domain.model.Account
import co.edu.uniquindio.exploracity.domain.model.DataExport
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlin.time.Duration.Companion.seconds

// Dobles de la cuenta y la sesión para 29, 28 y 30: cada operación tarda 1 s de tiempo virtual.

internal class FakeAccounts : AccountRepository {
    var error: Exception? = null
    var exports = 0
    var deleteError: Exception? = null
    var deleted = false

    val current = MutableStateFlow(Account("ana.rios@correo.com"))
    override val account: StateFlow<Account> = current

    /** «Cambiar correo»: lo que se pidió (correo nuevo y contraseña) y cómo falla. */
    val emailRequests = mutableListOf<Pair<String, String>>()
    var emailError: Exception? = null
    var resends = 0
    val confirmed = mutableListOf<String>()
    var confirmError: Exception? = null

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

    override suspend fun requestEmailChange(newEmail: String, password: String) {
        emailRequests += newEmail to password
        delay(1.seconds)
        emailError?.let { throw it }
        current.update { it.copy(pendingEmail = newEmail) }
    }

    override suspend fun resendEmailChange() {
        resends++
        delay(1.seconds)
    }

    override suspend fun confirmEmailChange(token: String): String {
        confirmed += token
        delay(1.seconds)
        confirmError?.let { throw it }
        val email = current.value.pendingEmail ?: "ana.nueva@correo.com"
        current.value = Account(email)
        return email
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
