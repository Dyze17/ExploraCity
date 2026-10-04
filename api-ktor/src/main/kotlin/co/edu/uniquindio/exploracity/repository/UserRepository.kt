package co.edu.uniquindio.exploracity.repository

import co.edu.uniquindio.exploracity.model.ReportReason
import co.edu.uniquindio.exploracity.model.Residency
import co.edu.uniquindio.exploracity.model.Role
import co.edu.uniquindio.exploracity.model.UserReports
import co.edu.uniquindio.exploracity.model.Users
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.notInList
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

/** Una cuenta tal como está guardada. [email] y [pendingEmail] van en minúscula. */
data class UserRecord(
    val id: UUID,
    val email: String,
    val pendingEmail: String?,
    val passwordHash: String,
    val name: String,
    val bio: String?,
    val residency: Residency,
    val photoUrl: String?,
    val photoPublicId: String?,
    val role: Role,
    val createdAt: Instant,
)

/** Ya hay una cuenta con ese correo (índice único sobre lower(email)). */
class EmailTakenException : Exception("El correo ya tiene cuenta")

/**
 * Cuentas de las personas (ADR-05: el acceso a datos solo pasa por los repositorios). Salvo [syncModeratorRoles], cada
 * función corre dentro de la transacción de quien la llama (`Database.query`).
 */
class UserRepository(private val database: Database) {
    /**
     * Deja con rol de moderador exactamente las cuentas de [moderators] (correos en minúscula). Devuelve cuántas
     * cambiaron. Corre al arrancar, antes de atender peticiones.
     */
    fun syncModeratorRoles(moderators: Set<String>): Int = transaction(database) {
        val emails = moderators.toList()
        val promoted = Users.update({ (Users.email.lowerCase() inList emails) and (Users.role neq Role.MODERATOR) }) {
            it[role] = Role.MODERATOR
        }
        val demoted = Users.update({ (Users.email.lowerCase() notInList emails) and (Users.role eq Role.MODERATOR) }) {
            it[role] = Role.USER
        }
        promoted + demoted
    }

    fun findById(id: UUID): UserRecord? = Users.selectAll().where { Users.id eq id }.singleOrNull()?.toUser()

    fun findByEmail(email: String): UserRecord? =
        Users.selectAll().where { Users.email.lowerCase() eq email.lowercase() }.singleOrNull()?.toUser()

    /** La cuenta que espera confirmar [email] como su correo nuevo. */
    fun findByPendingEmail(email: String): UserRecord? =
        Users.selectAll().where { Users.pendingEmail.lowerCase() eq email.lowercase() }.firstOrNull()?.toUser()

    /** Lanza [EmailTakenException] si otra cuenta se llevó el correo justo antes. */
    fun create(email: String, passwordHash: String, name: String, residency: Residency, role: Role, createdAt: Instant): UserRecord {
        val id = uniqueEmail {
            Users.insert {
                it[Users.email] = email
                it[Users.passwordHash] = passwordHash
                it[Users.name] = name
                it[Users.residency] = residency
                it[Users.role] = role
                it[Users.createdAt] = createdAt.atOffset(ZoneOffset.UTC)
            }[Users.id]
        }
        return checkNotNull(findById(id))
    }

    fun updatePassword(id: UUID, passwordHash: String) {
        Users.update({ Users.id eq id }) { it[Users.passwordHash] = passwordHash }
    }

    fun setPendingEmail(id: UUID, email: String?) {
        Users.update({ Users.id eq id }) { it[pendingEmail] = email }
    }

    /** El correo nuevo pasa a ser el de la cuenta. Lanza [EmailTakenException] si otra cuenta lo tomó. */
    fun changeEmail(id: UUID, email: String) {
        uniqueEmail {
            Users.update({ Users.id eq id }) {
                it[Users.email] = email
                it[pendingEmail] = null
            }
        }
    }

    fun updateProfile(id: UUID, name: String, bio: String?, residency: Residency) {
        Users.update({ Users.id eq id }) {
            it[Users.name] = name
            it[Users.bio] = bio
            it[Users.residency] = residency
        }
    }

    fun updatePhoto(id: UUID, url: String?, publicId: String?) {
        Users.update({ Users.id eq id }) {
            it[photoUrl] = url
            it[photoPublicId] = publicId
        }
    }

    /** 30 · Lo demás lo resuelven las llaves foráneas del esquema (V2): unas cosas se borran y otras quedan sin autor. */
    fun delete(id: UUID) {
        Users.deleteWhere { Users.id eq id }
    }

    /** 31A · Reporte anónimo para la moderación. */
    fun report(reportedId: UUID, reporterId: UUID, reason: ReportReason, at: Instant) {
        UserReports.insert {
            it[UserReports.reportedId] = reportedId
            it[UserReports.reporterId] = reporterId
            it[UserReports.reason] = reason
            it[createdAt] = at.atOffset(ZoneOffset.UTC)
        }
    }

    private fun <T> uniqueEmail(write: () -> T): T = try {
        write()
    } catch (e: ExposedSQLException) {
        if (e.sqlState == UNIQUE_VIOLATION) throw EmailTakenException()
        throw e
    }

    private fun ResultRow.toUser() = UserRecord(
        id = this[Users.id],
        email = this[Users.email],
        pendingEmail = this[Users.pendingEmail],
        passwordHash = this[Users.passwordHash],
        name = this[Users.name],
        bio = this[Users.bio],
        residency = this[Users.residency],
        photoUrl = this[Users.photoUrl],
        photoPublicId = this[Users.photoPublicId],
        role = this[Users.role],
        createdAt = this[Users.createdAt].toInstant(),
    )

    private companion object {
        /** SQLSTATE de PostgreSQL para una clave única repetida. */
        const val UNIQUE_VIOLATION = "23505"
    }
}
