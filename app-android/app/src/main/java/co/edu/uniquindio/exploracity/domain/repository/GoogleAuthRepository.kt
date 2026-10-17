package co.edu.uniquindio.exploracity.domain.repository

import co.edu.uniquindio.exploracity.domain.model.GoogleSignInOutcome
import co.edu.uniquindio.exploracity.domain.model.Registration
import co.edu.uniquindio.exploracity.domain.model.Residency
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
