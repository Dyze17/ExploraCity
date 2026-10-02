package co.edu.uniquindio.exploracity.data.repository

import co.edu.uniquindio.exploracity.data.connectivity.ConnectivityObserver
import co.edu.uniquindio.exploracity.data.connectivity.OfflineException
import co.edu.uniquindio.exploracity.domain.model.AuthRules
import co.edu.uniquindio.exploracity.domain.model.InvalidCredentialsException
import co.edu.uniquindio.exploracity.domain.model.UserRole
import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** Acceso (SAD: componente de Usuarios, JWT emitido por el backend). */
interface AuthRepository {
    /** 3 · El rol de la cuenta. Lanza [InvalidCredentialsException] si no coinciden, o excepción si falla la red. */
    suspend fun signIn(email: String, password: String): UserRole

    /** 1 · Al abrir la app con sesión, el servidor la confirma (con la API, renueva el token). Lanza excepción si falla. */
    suspend fun resumeSession()
}

/**
 * Temporal hasta que exista la API: las cuentas de prueba ([accounts], correo → rol). El moderador es una cuenta
 * precargada, como dice el SAD. Para no guardar contraseñas en el repositorio, acepta cualquiera que cumpla las reglas;
 * un correo que no es de prueba da el error de 3.c.
 */
class FakeAuthRepository(
    private val accounts: Map<String, UserRole> = sampleLogins,
    private val latency: Duration = 1300.milliseconds,
    private val resumeLatency: Duration = 500.milliseconds,
) : AuthRepository {

    override suspend fun signIn(email: String, password: String): UserRole {
        delay(latency)
        val role = accounts[email.trim().lowercase()] ?: throw InvalidCredentialsException()
        if (!AuthRules.isValidPassword(password)) throw InvalidCredentialsException()
        return role
    }

    override suspend fun resumeSession() {
        delay(resumeLatency)
    }
}

/** Sin red no se intenta: el inicio de sesión ya lo dice antes (3.c) y el arranque ofrece seguir sin conexión (1.c). */
class OnlineOnlyAuthRepository(
    private val remote: AuthRepository,
    private val connectivity: ConnectivityObserver,
) : AuthRepository {
    override suspend fun signIn(email: String, password: String): UserRole {
        if (!connectivity.isOnline.value) throw OfflineException()
        return remote.signIn(email, password)
    }

    override suspend fun resumeSession() {
        if (!connectivity.isOnline.value) throw OfflineException()
        remote.resumeSession()
    }
}
