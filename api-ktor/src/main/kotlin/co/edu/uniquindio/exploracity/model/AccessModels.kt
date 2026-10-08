package co.edu.uniquindio.exploracity.model

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.temporal.ChronoUnit

// Cuerpos de Sesión, Recuperar la contraseña y Cuenta (docs/api). Los nombres son los del contrato.

/** Fechas del contrato: ISO 8601 en UTC, al segundo (2026-10-03T15:00:00Z). */
fun Instant.iso(): String = truncatedTo(ChronoUnit.SECONDS).toString()

/** 4 · El registro: el «¿Cómo te presentas?» es [residency]. */
@Serializable
data class RegisterRequest(
    val name: String,
    val email: String,
    val password: String,
    val residency: Residency,
    /** Lo pone la app (un UUID): repetir el registro con el mismo y la misma contraseña abre la sesión (V7). */
    val clientId: String? = null,
)

@Serializable
data class LoginRequest(val email: String, val password: String)

/**
 * ADR-15 · Entrar con Google: el ID token que la app recibe de Google. La primera vez, con [registration] (4 en modo
 * Google, C1); sin ella, una cuenta nueva responde registration_required.
 */
@Serializable
data class GoogleSignInRequest(val idToken: String, val registration: GoogleRegistration? = null)

/** 4 en modo Google: el nombre (el de Google, que la persona puede cambiar) y el «¿Cómo te presentas?». */
@Serializable
data class GoogleRegistration(val name: String, val residency: Residency)

/** B1 · Vincular Google a la cuenta con contraseña de ese mismo correo: la contraseña, una sola vez. */
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
 * La sesión que abren el registro, el inicio de sesión y cada renovación: los dos tokens, la persona y su cuenta, que la
 * app guarda con la sesión. [expiresIn] son los segundos que dura el token de acceso.
 */
@Serializable
data class SessionResponse(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
    val userId: String,
    val role: Role,
    val email: String,
    val pendingEmail: String? = null,
    /** Solo al registrarse (4): false si el correo de bienvenida no salió. La cuenta se creó igual. */
    val welcomeEmailSent: Boolean? = null,
)

/** 6.b · Para quién es el enlace del correo y hasta cuándo vale. */
@Serializable
data class ResetLinkResponse(val email: String, val expiresAt: String)

/**
 * 28 y 29 · El correo de la cuenta y, con un cambio pedido, el nuevo que espera confirmación. [hasPassword] y
 * [googleLinked] dicen cómo entra (ADR-15): sin contraseña no se ofrece «Cambiar correo» (D1).
 */
@Serializable
data class AccountResponse(
    val email: String,
    val pendingEmail: String? = null,
    val hasPassword: Boolean,
    val googleLinked: Boolean,
)

/** «Cambiar correo» · El correo nuevo y la contraseña actual. */
@Serializable
data class EmailChangeRequest(val newEmail: String, val password: String)

/** Solo en desarrollo: el enlace de un correo del buzón. */
@Serializable
data class DevLinkRequest(val email: String, val purpose: LinkPurpose)

@Serializable
data class DevLinkResponse(val token: String)
