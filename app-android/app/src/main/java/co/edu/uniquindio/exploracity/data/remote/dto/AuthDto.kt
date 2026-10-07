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
    val welcomeEmailSent: Boolean? = null,
) {
    val account: Account get() = Account(email, pendingEmail)
}

@Serializable
data class ResetLinkDto(val email: String, val expiresAt: String) {
    fun toDomain() = ResetLink(email, Instant.parse(expiresAt))
}

@Serializable
data class AccountDto(val email: String, val pendingEmail: String? = null) {
    fun toDomain() = Account(email, pendingEmail)
}

@Serializable
data class EmailChangeRequest(val newEmail: String, val password: String)

/** Solo en desarrollo: para qué es el enlace que se busca en el buzón. */
enum class LinkPurpose { PASSWORD_RESET, EMAIL_CHANGE }

@Serializable
data class DevLinkRequest(val email: String, val purpose: LinkPurpose)

@Serializable
data class DevLinkDto(val token: String)
