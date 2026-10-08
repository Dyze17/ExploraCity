package co.edu.uniquindio.exploracity.data.remote.dto

import co.edu.uniquindio.exploracity.domain.model.Account
import co.edu.uniquindio.exploracity.domain.model.ResetLink
import co.edu.uniquindio.exploracity.domain.model.Residency
import co.edu.uniquindio.exploracity.domain.model.UserRole
import kotlinx.serialization.Serializable
import java.time.Instant

// DTO de Sesión, Recuperar la contraseña y Cuenta (docs/api).

@Serializable
data class RegisterRequest(val name: String, val email: String, val password: String, val residency: Residency, val clientId: String)

@Serializable
data class LoginRequest(val email: String, val password: String)

/** ADR-15 · El ID token de Google y, la primera vez, el registro en modo Google (C1). */
@Serializable
data class GoogleSignInRequest(val idToken: String, val registration: GoogleRegistrationDto? = null)

@Serializable
data class GoogleRegistrationDto(val name: String, val residency: Residency)

/** B1 · Vincular Google con la contraseña de la cuenta de ese correo. */
@Serializable
data class GoogleLinkRequest(val idToken: String, val password: String)

@Serializable
data class RefreshRequest(val refreshToken: String)

@Serializable
data class EmailRequest(val email: String)

@Serializable
data class TokenRequest(val token: String)

@Serializable
data class ResetPasswordRequest(val token: String, val password: String)

/**
 * La sesión que abren el registro, el inicio de sesión y cada renovación: los dos tokens, la persona y su cuenta.
 * [welcomeEmailSent] solo llega al registrarse.
 */
@Serializable
data class SessionDto(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
    val userId: String,
    val role: UserRole,
    val email: String,
    val pendingEmail: String? = null,
    /** ADR-15 · Cómo entra la cuenta. Sin el campo (una API anterior), con contraseña. */
    val hasPassword: Boolean = true,
    val googleLinked: Boolean = false,
    val welcomeEmailSent: Boolean? = null,
) {
    val account: Account get() = Account(email, pendingEmail, hasPassword)
}

@Serializable
data class ResetLinkDto(val email: String, val expiresAt: String) {
    fun toDomain() = ResetLink(email, Instant.parse(expiresAt))
}

@Serializable
data class AccountDto(val email: String, val pendingEmail: String? = null, val hasPassword: Boolean = true) {
    fun toDomain() = Account(email, pendingEmail, hasPassword)
}

@Serializable
data class EmailChangeRequest(val newEmail: String, val password: String)

/** Solo en desarrollo: para qué es el enlace que se busca en el buzón. */
enum class LinkPurpose { PASSWORD_RESET, EMAIL_CHANGE }

@Serializable
data class DevLinkRequest(val email: String, val purpose: LinkPurpose)

@Serializable
data class DevLinkDto(val token: String)
