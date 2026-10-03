package co.edu.uniquindio.exploracity.repository

import co.edu.uniquindio.exploracity.model.AccountLinks
import co.edu.uniquindio.exploracity.model.LinkPurpose
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.vendors.ForUpdateOption
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

/** Un enlace del correo guardado (solo el hash de su token). [email] es la dirección a la que se envió. */
data class StoredLink(
    val id: UUID,
    val userId: UUID,
    val purpose: LinkPurpose,
    val email: String,
    val expiresAt: Instant,
    val usedAt: Instant?,
) {
    /** Sirve si nadie lo usó y no venció. */
    fun usableAt(now: Instant): Boolean = usedAt == null && now.isBefore(expiresAt)
}

/** Enlaces del correo: recuperar la contraseña y confirmar un correo nuevo. Corre en la transacción de quien llama. */
class AccountLinkRepository {
    fun insert(userId: UUID, purpose: LinkPurpose, tokenHash: String, email: String, expiresAt: Instant, createdAt: Instant): UUID =
        AccountLinks.insert {
            it[AccountLinks.userId] = userId
            it[AccountLinks.purpose] = purpose
            it[AccountLinks.tokenHash] = tokenHash
            it[AccountLinks.email] = email
            it[AccountLinks.expiresAt] = expiresAt.atOffset(ZoneOffset.UTC)
            it[AccountLinks.createdAt] = createdAt.atOffset(ZoneOffset.UTC)
        }[AccountLinks.id]

    /** Con [lock], la fila queda bloqueada hasta el final de la transacción: un enlace no se usa dos veces. */
    fun find(tokenHash: String, purpose: LinkPurpose, lock: Boolean = false): StoredLink? {
        val query = AccountLinks.selectAll()
            .where { (AccountLinks.tokenHash eq tokenHash) and (AccountLinks.purpose eq purpose) }
            .let { if (lock) it.forUpdate(ForUpdateOption.ForUpdate) else it }
        return query.singleOrNull()?.let { row ->
            StoredLink(
                id = row[AccountLinks.id],
                userId = row[AccountLinks.userId],
                purpose = row[AccountLinks.purpose],
                email = row[AccountLinks.email],
                expiresAt = row[AccountLinks.expiresAt].toInstant(),
                usedAt = row[AccountLinks.usedAt]?.toInstant(),
            )
        }
    }

    /** Cuándo se envió el último enlace de [purpose] a la cuenta: la espera de 60 s cuenta desde ahí (6.a). */
    fun lastSentAt(userId: UUID, purpose: LinkPurpose): Instant? = AccountLinks.select(AccountLinks.createdAt)
        .where { (AccountLinks.userId eq userId) and (AccountLinks.purpose eq purpose) }
        .orderBy(AccountLinks.createdAt, SortOrder.DESC)
        .limit(1)
        .singleOrNull()
        ?.get(AccountLinks.createdAt)
        ?.toInstant()

    fun markUsed(id: UUID, at: Instant) {
        AccountLinks.update({ AccountLinks.id eq id }) { it[usedAt] = at.atOffset(ZoneOffset.UTC) }
    }

    /** Los enlaces de [purpose] que aún no se usaron dejan de servir. */
    fun expireAll(userId: UUID, purpose: LinkPurpose, at: Instant) {
        AccountLinks.update({ (AccountLinks.userId eq userId) and (AccountLinks.purpose eq purpose) and AccountLinks.usedAt.isNull() }) {
            it[usedAt] = at.atOffset(ZoneOffset.UTC)
        }
    }
}
