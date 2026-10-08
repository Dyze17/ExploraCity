package co.edu.uniquindio.exploracity.data.repository

import co.edu.uniquindio.exploracity.data.connectivity.ConnectivityObserver
import co.edu.uniquindio.exploracity.data.connectivity.OfflineException
import co.edu.uniquindio.exploracity.domain.model.EmailTakenException
import co.edu.uniquindio.exploracity.domain.model.GoogleSignInOutcome
import co.edu.uniquindio.exploracity.domain.model.GoogleSignInUnavailableException
import co.edu.uniquindio.exploracity.domain.model.GoogleTokenRejectedException
import co.edu.uniquindio.exploracity.domain.model.InvalidCredentialsException
import co.edu.uniquindio.exploracity.domain.model.Registration
import co.edu.uniquindio.exploracity.domain.model.Residency
import co.edu.uniquindio.exploracity.domain.model.TooManyAttemptsException
import co.edu.uniquindio.exploracity.domain.model.UnconfirmedRegistrationException
import co.edu.uniquindio.exploracity.domain.model.UserRole

/**
 * ADR-15 · Entrar con Google, con el ID token que da Credential Manager. Al entrar guarda la sesión, como el inicio de
 * sesión con contraseña. Todas pueden lanzar [GoogleTokenRejectedException] (el token venció: dura una hora) y
 * [GoogleSignInUnavailableException] (la API no lo tiene configurado).
 */
interface GoogleAuthRepository {
    suspend fun signInWithGoogle(idToken: String): GoogleSignInOutcome

    /**
     * C1 · Crea la cuenta sin contraseña. Lanza [EmailTakenException] si mientras tanto el correo tomó otra cuenta, y
     * [UnconfirmedRegistrationException] si la API no respondió: repetirlo con el mismo token entra con ella si quedó.
     */
    suspend fun registerWithGoogle(idToken: String, name: String, residency: Residency): Registration

    /**
     * B1 · Vincula Google a la cuenta con contraseña de ese correo. Lanza [InvalidCredentialsException] si la contraseña
     * no coincide y [TooManyAttemptsException] tras 5 intentos (A1).
     */
    suspend fun linkGoogle(idToken: String, password: String): UserRole
}

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
