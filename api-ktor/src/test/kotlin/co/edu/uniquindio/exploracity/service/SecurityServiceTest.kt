package co.edu.uniquindio.exploracity.service

import co.edu.uniquindio.exploracity.model.Residency
import co.edu.uniquindio.exploracity.model.Role
import co.edu.uniquindio.exploracity.model.Users
import co.edu.uniquindio.exploracity.repository.UserRepository
import co.edu.uniquindio.exploracity.support.DatabaseTest
import co.edu.uniquindio.exploracity.support.randomSecret
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.test.Test
import kotlin.test.assertEquals

/** ADR-06 · Las cuentas de moderador son las de la configuración. */
class SecurityServiceTest : DatabaseTest() {

    private fun insert(email: String, role: Role = Role.USER) = transaction(database) {
        Users.insert {
            it[Users.email] = email
            it[passwordHash] = randomSecret()
            it[name] = email.substringBefore('@')
            it[residency] = Residency.VISITOR
            it[Users.role] = role
        }
    }

    private fun roles(): Map<String, Role> =
        transaction(database) { Users.selectAll().associate { it[Users.email] to it[Users.role] } }

    @Test
    fun `al arrancar da y quita el rol según la lista`() {
        insert("Laura@Ejemplo.co")
        insert("ana@ejemplo.co")
        insert("antes@ejemplo.co", Role.MODERATOR)

        SecurityService(UserRepository(database), setOf("laura@ejemplo.co")).syncModeratorRoles()

        assertEquals(
            mapOf("Laura@Ejemplo.co" to Role.MODERATOR, "ana@ejemplo.co" to Role.USER, "antes@ejemplo.co" to Role.USER),
            roles(),
        )
    }

    @Test
    fun `con la lista vacía nadie modera`() {
        insert("laura@ejemplo.co", Role.MODERATOR)

        SecurityService(UserRepository(database), emptySet()).syncModeratorRoles()

        assertEquals(Role.USER, roles().values.single())
    }

    @Test
    fun `el rol de una cuenta nueva sale de la lista`() {
        val security = SecurityService(UserRepository(database), setOf("laura@ejemplo.co"))

        assertEquals(Role.MODERATOR, security.roleFor(" LAURA@ejemplo.co "))
        assertEquals(Role.USER, security.roleFor("ana@ejemplo.co"))
    }
}
