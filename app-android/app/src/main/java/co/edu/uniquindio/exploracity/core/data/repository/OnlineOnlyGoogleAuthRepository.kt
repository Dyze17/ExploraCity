package co.edu.uniquindio.exploracity.core.data.repository

import co.edu.uniquindio.exploracity.core.data.connectivity.ConnectivityObserver
import co.edu.uniquindio.exploracity.core.data.connectivity.OfflineException
import co.edu.uniquindio.exploracity.domain.model.GoogleSignInOutcome
import co.edu.uniquindio.exploracity.domain.model.Registration
import co.edu.uniquindio.exploracity.domain.model.Residency
import co.edu.uniquindio.exploracity.domain.model.UserRole
import co.edu.uniquindio.exploracity.domain.repository.GoogleAuthRepository
/** Sin red no se intenta: el inicio de sesión y el registro ya lo dicen arriba (3.c). */
class OnlineOnlyGoogleAuthRepository(
    private val remote: GoogleAuthRepository,
    private val connectivity: ConnectivityObserver,
) : GoogleAuthRepository {
    private fun requireOnline() {
        if (!connectivity.isOnline.value) throw OfflineException()
    }

    override suspend fun signInWithGoogle(idToken: String): GoogleSignInOutcome {
        requireOnline()
        return remote.signInWithGoogle(idToken)
    }

    override suspend fun registerWithGoogle(idToken: String, name: String, residency: Residency): Registration {
        requireOnline()
        return remote.registerWithGoogle(idToken, name, residency)
    }

    override suspend fun linkGoogle(idToken: String, password: String): UserRole {
        requireOnline()
        return remote.linkGoogle(idToken, password)
    }
}
