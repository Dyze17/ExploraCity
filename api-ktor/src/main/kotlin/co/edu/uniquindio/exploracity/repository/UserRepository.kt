package co.edu.uniquindio.exploracity.repository

import co.edu.uniquindio.exploracity.model.Role
import co.edu.uniquindio.exploracity.model.Users
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.notInList
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update

/** Cuentas de las personas (ADR-05: el acceso a datos solo pasa por los repositorios). */
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
}
