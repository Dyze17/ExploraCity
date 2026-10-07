package co.edu.uniquindio.exploracity.data.repository

import co.edu.uniquindio.exploracity.data.connectivity.ConnectivityObserver
import co.edu.uniquindio.exploracity.data.connectivity.OfflineException
import co.edu.uniquindio.exploracity.domain.model.EmailDeliveryException
import co.edu.uniquindio.exploracity.domain.model.EmailTakenException
import co.edu.uniquindio.exploracity.domain.model.ExpiredLinkException
import co.edu.uniquindio.exploracity.domain.model.InvalidCredentialsException
import co.edu.uniquindio.exploracity.domain.model.NewAccount
import co.edu.uniquindio.exploracity.domain.model.Registration
import co.edu.uniquindio.exploracity.domain.model.ResetLink
import co.edu.uniquindio.exploracity.domain.model.UnconfirmedRegistrationException
import co.edu.uniquindio.exploracity.domain.model.UserRole

/** Acceso (SAD: componente de Usuarios, JWT emitido por el backend). */
interface AuthRepository {
    /** 3 · El rol de la cuenta. Lanza [InvalidCredentialsException] si no coinciden, o excepción si falla la red. */
    suspend fun signIn(email: String, password: String): UserRole

    /** 1 · Al abrir la app con sesión, el servidor la confirma (con la API, renueva el token). Lanza excepción si falla. */
    suspend fun resumeSession()

    /**
     * 4 · Crea la cuenta, siempre con rol de usuario: los moderadores vienen precargados (SAD). Lanza
     * [EmailTakenException] si el correo ya tiene cuenta, y [UnconfirmedRegistrationException] si el servidor no respondió
     * y no se sabe si la creó. Si falla el correo de bienvenida, la cuenta se crea igual.
     */
    suspend fun register(account: NewAccount): Registration

    /**
     * 5 · Pide el enlace para crear una contraseña nueva. Responde igual exista o no la cuenta, para no revelarlo; lanza
     * [EmailDeliveryException] si el servicio de correo falla.
     */
    suspend fun requestPasswordReset(email: String)

    /** 6.b · Abre el enlace del correo. Lanza [ExpiredLinkException] si venció o ya se usó. */
    suspend fun openResetLink(token: String): ResetLink

    /** 6.b · Guarda la contraseña nueva y el enlace deja de servir. Lanza [ExpiredLinkException] si venció mientras tanto. */
    suspend fun resetPassword(token: String, password: String)
}
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
