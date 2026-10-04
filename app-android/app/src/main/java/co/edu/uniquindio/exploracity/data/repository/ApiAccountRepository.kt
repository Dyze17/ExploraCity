package co.edu.uniquindio.exploracity.data.repository

import co.edu.uniquindio.exploracity.data.remote.AccountApi
import co.edu.uniquindio.exploracity.data.remote.ApiSession
import co.edu.uniquindio.exploracity.domain.model.Account
import co.edu.uniquindio.exploracity.domain.model.DataExport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * La cuenta con la API (SAD: componente de Usuarios). El correo llega con la sesión y se guarda con ella ([ApiSession]):
 * se ve sin red. Cada cambio que confirma la API se guarda ahí también.
 */
class ApiAccountRepository(
    private val api: AccountApi,
    private val session: ApiSession,
    scope: CoroutineScope,
) : AccountRepository {

    override val account: StateFlow<Account> = session.account
        .map { it?.account ?: NO_ACCOUNT }
        .stateIn(scope, SharingStarted.Eagerly, NO_ACCOUNT)

    override suspend fun exportData(): DataExport = api.exportData()

    /** 30 · Si la API la borró, la sesión ya no existe: se cierra en el teléfono. */
    override suspend fun deleteAccount() {
        api.deleteAccount()
        session.end()
    }

    override suspend fun requestEmailChange(newEmail: String, password: String) = translatingErrors {
        session.updateAccount(api.requestEmailChange(newEmail.trim(), password).toDomain())
    }

    override suspend fun resendEmailChange() = translatingErrors { api.resendEmailChange() }

    override suspend fun confirmEmailChange(token: String): String = translatingErrors {
        val confirmed = api.confirmEmailChange(token).toDomain()
        session.updateAccount(confirmed)
        confirmed.email
    }

    private companion object {
        /** Antes de leer la sesión guardada, o sin sesión. */
        val NO_ACCOUNT = Account(email = "")
    }
}
