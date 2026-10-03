package co.edu.uniquindio.exploracity.config

import co.edu.uniquindio.exploracity.model.Role
import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.Payload
import java.time.Clock
import java.util.UUID

/** La persona de un token de acceso válido. */
data class UserPrincipal(val id: UUID, val role: Role)

/**
 * Tokens de acceso (ADR-06): firmados con HMAC-SHA256, con la persona en `sub` y su rol en `role`. Duran
 * [JwtSettings.accessTtl]; después la app pide otro con su token de renovación.
 */
class JwtConfig(private val settings: JwtSettings, private val clock: Clock) {
    private val algorithm = Algorithm.HMAC256(settings.secret)

    val verifier: JWTVerifier = (
        JWT.require(algorithm)
            .withIssuer(settings.issuer)
            .withAudience(settings.audience)
            .withClaimPresence(ROLE_CLAIM) as JWTVerifier.BaseVerification
        ).build(clock)

    fun accessToken(user: UserPrincipal): String {
        val now = clock.instant()
        return JWT.create()
            .withIssuer(settings.issuer)
            .withAudience(settings.audience)
            .withSubject(user.id.toString())
            .withClaim(ROLE_CLAIM, user.role.name)
            .withIssuedAt(now)
            .withExpiresAt(now.plus(settings.accessTtl))
            .sign(algorithm)
    }

    /** null si el token, aunque bien firmado, no trae una persona y un rol que se entiendan. */
    fun principal(payload: Payload): UserPrincipal? {
        val id = payload.subject?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: return null
        val role = payload.getClaim(ROLE_CLAIM).asString()?.let { name -> Role.entries.firstOrNull { it.name == name } }
            ?: return null
        return UserPrincipal(id, role)
    }

    private companion object {
        const val ROLE_CLAIM = "role"
    }
}
