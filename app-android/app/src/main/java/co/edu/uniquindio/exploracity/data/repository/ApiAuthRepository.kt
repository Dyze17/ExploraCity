package co.edu.uniquindio.exploracity.data.repository

import co.edu.uniquindio.exploracity.data.remote.ApiException
import co.edu.uniquindio.exploracity.data.remote.ApiSession
import co.edu.uniquindio.exploracity.data.remote.AuthApi
import co.edu.uniquindio.exploracity.data.remote.dto.GoogleRegistrationDto
import co.edu.uniquindio.exploracity.data.remote.dto.GoogleSignInRequest
import co.edu.uniquindio.exploracity.data.remote.dto.RegisterRequest
import co.edu.uniquindio.exploracity.data.remote.mayHaveReachedApi
import co.edu.uniquindio.exploracity.domain.model.EmailDeliveryException
import co.edu.uniquindio.exploracity.domain.model.EmailTakenException
import co.edu.uniquindio.exploracity.domain.model.ExpiredLinkException
import co.edu.uniquindio.exploracity.domain.model.GoogleSignInOutcome
import co.edu.uniquindio.exploracity.domain.model.GoogleSignInUnavailableException
import co.edu.uniquindio.exploracity.domain.model.GoogleTokenRejectedException
import co.edu.uniquindio.exploracity.domain.model.InvalidCredentialsException
import co.edu.uniquindio.exploracity.domain.model.NewAccount
import co.edu.uniquindio.exploracity.domain.model.Registration
import co.edu.uniquindio.exploracity.domain.model.ResetLink
import co.edu.uniquindio.exploracity.domain.model.Residency
import co.edu.uniquindio.exploracity.domain.model.SessionEndedException
import co.edu.uniquindio.exploracity.domain.model.TooManyAttemptsException
import co.edu.uniquindio.exploracity.domain.model.UnconfirmedRegistrationException
import co.edu.uniquindio.exploracity.domain.model.UserRole
import kotlinx.coroutines.CancellationException
import java.io.IOException

/**
 * Acceso con la API (SAD: componente de Usuarios, ADR-06). Al entrar o registrarse guarda la sesión ([ApiSession]):
 * los tokens y la cuenta. Traduce los códigos de error a las excepciones que las pantallas ya explican.
 */
class ApiAuthRepository(private val api: AuthApi, private val session: ApiSession) : AuthRepository, GoogleAuthRepository {

    override suspend fun signIn(email: String, password: String): UserRole = translatingErrors {
        val opened = api.login(email.trim(), password)
        session.start(opened)
        opened.role
    }

    /**
     * 1 · Renueva la sesión guardada. Lanza [SessionEndedException] si no hay o si la API ya no la acepta (se cerró
     * desde otro lado, cambió la contraseña o venció): hay que volver a entrar.
     */
    override suspend fun resumeSession() {
        val refresh = session.tokens()?.refresh ?: throw SessionEndedException()
        val renewed = try {
            api.refresh(refresh)
        } catch (e: ApiException) {
            if (e.code != INVALID_REFRESH_TOKEN) throw e
            session.end()
            throw SessionEndedException()
        }
        session.start(renewed)
    }

    /**
     * 4 · El rol lo decide la API: los moderadores son los correos de su lista. Sin respuesta lanza
     * [UnconfirmedRegistrationException]: la API pudo crear la cuenta después de que la app se rindió. Repetirlo con el
     * mismo [NewAccount.clientId] y la misma contraseña abre la sesión en esa cuenta.
     */
    override suspend fun register(account: NewAccount): Registration = translatingErrors {
        val request = RegisterRequest(account.name.trim(), account.email.trim(), account.password, account.residency, account.clientId)
        val opened = try {
            api.register(request)
        } catch (e: IOException) {
            throw if (e.mayHaveReachedApi()) UnconfirmedRegistrationException(e) else e
        }
        session.start(opened)
        Registration(opened.role, welcomeEmailSent = opened.welcomeEmailSent ?: true)
    }

    /**
     * ADR-15 · registration_required y link_required no son fallos: dicen qué sigue (el registro en modo Google o
     * vincular con la contraseña).
     */
    override suspend fun signInWithGoogle(idToken: String): GoogleSignInOutcome = translatingErrors {
        val opened = try {
            api.google(GoogleSignInRequest(idToken))
        } catch (e: ApiException) {
            when (e.code) {
                REGISTRATION_REQUIRED -> return@translatingErrors GoogleSignInOutcome.RegistrationRequired(e.email.orEmpty(), e.name)
                LINK_REQUIRED -> return@translatingErrors GoogleSignInOutcome.LinkRequired(e.email.orEmpty())
                else -> throw e
            }
        }
        session.start(opened)
        GoogleSignInOutcome.SignedIn(opened.role)
    }

    /** C1 · Repetirlo con el mismo token es seguro: si la cuenta ya quedó creada, la API abre la sesión en ella. */
    override suspend fun registerWithGoogle(idToken: String, name: String, residency: Residency): Registration = translatingErrors {
        val opened = try {
            api.google(GoogleSignInRequest(idToken, GoogleRegistrationDto(name.trim(), residency)))
        } catch (e: IOException) {
            throw if (e.mayHaveReachedApi()) UnconfirmedRegistrationException(e) else e
        } catch (e: ApiException) {
            // Mientras tanto el correo tomó una cuenta con contraseña.
            throw if (e.code == LINK_REQUIRED) EmailTakenException() else e
        }
        session.start(opened)
        Registration(opened.role, welcomeEmailSent = opened.welcomeEmailSent ?: true)
    }

    override suspend fun linkGoogle(idToken: String, password: String): UserRole = translatingErrors {
        val opened = api.googleLink(idToken, password)
        session.start(opened)
        opened.role
    }

    override suspend fun requestPasswordReset(email: String) = translatingErrors { api.forgotPassword(email.trim()) }

    override suspend fun openResetLink(token: String): ResetLink = translatingErrors { api.openResetLink(token).toDomain() }

    override suspend fun resetPassword(token: String, password: String) = translatingErrors { api.resetPassword(token, password) }

    /**
     * 29A · Cierra la sesión en la API y en el teléfono. Sin red, o si la API falla, se cierra igual en el teléfono: el
     * token de renovación vence solo.
     */
    suspend fun signOut() {
        try {
            session.tokens()?.refresh?.let { api.logout(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // La sesión del teléfono se cierra igual.
        } finally {
            session.end()
        }
    }

    private companion object {
        const val INVALID_REFRESH_TOKEN = "invalid_refresh_token"
        const val REGISTRATION_REQUIRED = "registration_required"
        const val LINK_REQUIRED = "link_required"
    }
}

/** Los códigos de la API (docs/api) que el acceso y la cuenta explican con sus propias excepciones. */
internal suspend fun <T> translatingErrors(block: suspend () -> T): T = try {
    block()
} catch (e: ApiException) {
    throw when (e.code) {
        "invalid_credentials" -> InvalidCredentialsException()
        "email_taken" -> EmailTakenException()
        "email_delivery_failed" -> EmailDeliveryException()
        // Un enlace desconocido no dice de quién es: 6C abre 5 sin correo escrito.
        "link_expired" -> ExpiredLinkException(e.email.orEmpty())
        "too_many_attempts" -> TooManyAttemptsException()
        "invalid_google_token" -> GoogleTokenRejectedException()
        "google_sign_in_unavailable" -> GoogleSignInUnavailableException()
        // Otra cuenta de Google ya tiene ese correo o esa cuenta de Google ya está en otra cuenta.
        "google_account_in_use" -> EmailTakenException()
        else -> e
    }
}
