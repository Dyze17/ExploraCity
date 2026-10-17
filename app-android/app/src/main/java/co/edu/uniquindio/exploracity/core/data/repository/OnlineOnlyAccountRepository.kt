package co.edu.uniquindio.exploracity.core.data.repository

import co.edu.uniquindio.exploracity.core.data.connectivity.ConnectivityObserver
import co.edu.uniquindio.exploracity.core.data.connectivity.OfflineException
import co.edu.uniquindio.exploracity.domain.model.Account
import co.edu.uniquindio.exploracity.domain.model.DataExport
import co.edu.uniquindio.exploracity.domain.repository.AccountRepository
import kotlinx.coroutines.flow.StateFlow
/** El correo es de la sesión; el archivo, el borrado y el cambio de correo necesitan al servidor. */
class OnlineOnlyAccountRepository(
    private val remote: AccountRepository,
    private val connectivity: ConnectivityObserver,
) : AccountRepository {
    override val account: StateFlow<Account> = remote.account

    private fun requireOnline() {
        if (!connectivity.isOnline.value) throw OfflineException()
    }

    override suspend fun exportData(): DataExport {
        requireOnline()
        return remote.exportData()
    }

    override suspend fun deleteAccount() {
        requireOnline()
        remote.deleteAccount()
    }

    override suspend fun requestEmailChange(newEmail: String, password: String) {
        requireOnline()
        remote.requestEmailChange(newEmail, password)
    }

    override suspend fun resendEmailChange() {
        requireOnline()
        remote.resendEmailChange()
    }

    override suspend fun confirmEmailChange(token: String): String {
        requireOnline()
        return remote.confirmEmailChange(token)
    }
}
