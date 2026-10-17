package co.edu.uniquindio.exploracity.core.data.repository

import co.edu.uniquindio.exploracity.core.data.connectivity.ConnectivityObserver
import co.edu.uniquindio.exploracity.core.data.connectivity.OfflineException
import co.edu.uniquindio.exploracity.domain.model.NewAccount
import co.edu.uniquindio.exploracity.domain.model.Registration
import co.edu.uniquindio.exploracity.domain.model.ResetLink
import co.edu.uniquindio.exploracity.domain.model.UserRole
import co.edu.uniquindio.exploracity.domain.repository.AuthRepository

/** Sin red no se intenta: cada pantalla de acceso lo dice antes (3.c) y el arranque ofrece seguir sin conexión (1.c). */
class OnlineOnlyAuthRepository(
    private val remote: AuthRepository,
    private val connectivity: ConnectivityObserver,
) : AuthRepository {
    private fun requireOnline() {
        if (!connectivity.isOnline.value) throw OfflineException()
    }

    override suspend fun signIn(email: String, password: String): UserRole {
        requireOnline()
        return remote.signIn(email, password)
    }

    override suspend fun resumeSession() {
        requireOnline()
        remote.resumeSession()
    }

    override suspend fun register(account: NewAccount): Registration {
        requireOnline()
        return remote.register(account)
    }

    override suspend fun requestPasswordReset(email: String) {
        requireOnline()
        remote.requestPasswordReset(email)
    }

    override suspend fun openResetLink(token: String): ResetLink {
        requireOnline()
        return remote.openResetLink(token)
    }

    override suspend fun resetPassword(token: String, password: String) {
        requireOnline()
        remote.resetPassword(token, password)
    }
}
