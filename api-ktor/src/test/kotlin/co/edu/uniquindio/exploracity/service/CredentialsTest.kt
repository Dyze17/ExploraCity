package co.edu.uniquindio.exploracity.service

import co.edu.uniquindio.exploracity.support.newPassword
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** ADR-06 · Contraseñas con BCrypt, tokens aleatorios guardados como hash y las reglas del acceso. */
class CredentialsTest {

    @Test
    fun `las contraseñas se guardan con BCrypt de costo 12`() = runBlocking {
        val password = newPassword()
        val hasher = PasswordHasher()

        val hash = hasher.hash(password)

        assertTrue(hash.startsWith("\$2a\$12\$"), hash)
        assertTrue(hasher.verify(password, hash))
        assertFalse(hasher.verify(newPassword(), hash))
    }

    @Test
    fun `sin cuenta la comparación falla igual, sin revelar nada`() = runBlocking {
        assertFalse(PasswordHasher(cost = 4).verify(newPassword(), null))
    }

    @Test
    fun `los tokens son aleatorios, caben en un enlace y se guardan como SHA-256`() {
        val first = SecretTokens.generate()
        val second = SecretTokens.generate()

        assertNotEquals(first, second)
        assertTrue(Regex("[A-Za-z0-9_-]{43}").matches(first), first)
        assertEquals(64, SecretTokens.hash(first).length)
        assertEquals(SecretTokens.hash(first), SecretTokens.hash(first))
        assertNotEquals(SecretTokens.hash(first), SecretTokens.hash(second))
    }

    @Test
    fun `las reglas del acceso son las de la app`() {
        assertEquals("ana@correo.com", AccountRules.normalizeEmail("  Ana@Correo.COM "))
        assertTrue(AccountRules.isValidEmail("ana@correo.com"))
        assertFalse(AccountRules.isValidEmail("ana@correo"))
        assertFalse(AccountRules.isValidEmail("ana correo@correo.com"))
        assertTrue(AccountRules.isValidNewPassword("a".repeat(7) + "1"))
        assertFalse(AccountRules.isValidNewPassword("a".repeat(6) + "1"))
        assertFalse(AccountRules.isValidNewPassword("1".repeat(9)))
        assertTrue(AccountRules.isValidName("Al"))
        assertFalse(AccountRules.isValidName("A".repeat(41)))
    }
}
