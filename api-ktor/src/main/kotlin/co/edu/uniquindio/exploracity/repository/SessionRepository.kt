package co.edu.uniquindio.exploracity.repository

import co.edu.uniquindio.exploracity.model.RefreshTokens
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.vendors.ForUpdateOption
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

/** Un token de renovación guardado (solo su hash). */
data class StoredRefreshToken(
    val id: UUID,
    val userId: UUID,
    val expiresAt: Instant,
    val revokedAt: Instant?,
    val replacedBy: UUID?,
)

/** D1 · Tokens de renovación. Cada función corre dentro de la transacción de quien la llama. */
class SessionRepository {
    fun insert(userId: UUID, tokenHash: String, expiresAt: Instant, createdAt: Instant): UUID = RefreshTokens.insert {
        it[RefreshTokens.userId] = userId
        it[RefreshTokens.tokenHash] = tokenHash
        it[RefreshTokens.expiresAt] = expiresAt.atOffset(ZoneOffset.UTC)
        it[RefreshTokens.createdAt] = createdAt.atOffset(ZoneOffset.UTC)
    }[RefreshTokens.id]

    /** Bloquea la fila hasta el final de la transacción: dos renovaciones a la vez no usan el mismo token. */
    fun findForUpdate(tokenHash: String): StoredRefreshToken? = RefreshTokens.selectAll()
        .where { RefreshTokens.tokenHash eq tokenHash }
        .forUpdate(ForUpdateOption.ForUpdate)
        .singleOrNull()
        ?.let { row ->
            StoredRefreshToken(
                id = row[RefreshTokens.id],
                userId = row[RefreshTokens.userId],
                expiresAt = row[RefreshTokens.expiresAt].toInstant(),
                revokedAt = row[RefreshTokens.revokedAt]?.toInstant(),
                replacedBy = row[RefreshTokens.replacedBy],
            )
        }

    fun markReplaced(id: UUID, replacement: UUID, at: Instant) {
        RefreshTokens.update({ RefreshTokens.id eq id }) {
            it[revokedAt] = at.atOffset(ZoneOffset.UTC)
            it[replacedBy] = replacement
        }
    }

    fun revoke(id: UUID, at: Instant) {
        RefreshTokens.update({ (RefreshTokens.id eq id) and RefreshTokens.revokedAt.isNull() }) {
            it[revokedAt] = at.atOffset(ZoneOffset.UTC)
        }
    }

    /** Cierra todas las sesiones de la cuenta. Devuelve cuántas seguían abiertas. */
    fun revokeAll(userId: UUID, at: Instant): Int =
        RefreshTokens.update({ (RefreshTokens.userId eq userId) and RefreshTokens.revokedAt.isNull() }) {
            it[revokedAt] = at.atOffset(ZoneOffset.UTC)
        }
}
