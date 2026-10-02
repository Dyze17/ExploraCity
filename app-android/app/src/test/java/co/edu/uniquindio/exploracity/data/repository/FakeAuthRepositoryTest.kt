package co.edu.uniquindio.exploracity.data.repository

import co.edu.uniquindio.exploracity.data.connectivity.FakeConnectivity
import co.edu.uniquindio.exploracity.data.connectivity.OfflineException
import co.edu.uniquindio.exploracity.domain.model.AuthRules
import co.edu.uniquindio.exploracity.domain.model.InvalidCredentialsException
import co.edu.uniquindio.exploracity.domain.model.UserRole
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 3 · Las cuentas de prueba y las reglas del formulario. */
class FakeAuthRepositoryTest {

    private val accounts = mapOf("ana@correo.com" to ("clave-segura" to UserRole.USER), "mod@correo.com" to ("clave-segura" to UserRole.MODERATOR))
    private val auth = FakeAuthRepository(accounts)

    @Test
    fun `devuelve el rol de la cuenta, sin importar mayúsculas ni espacios en el correo`() = runTest {
        assertEquals(UserRole.USER, auth.signIn(" Ana@Correo.com ", "clave-segura"))
        assertEquals(UserRole.MODERATOR, auth.signIn("mod@correo.com", "clave-segura"))
    }

    @Test
    fun `un correo desconocido o una contraseña distinta dan el mismo error`() = runTest {
        assertTrue(runCatching { auth.signIn("nadie@correo.com", "clave-segura") }.exceptionOrNull() is InvalidCredentialsException)
        assertTrue(runCatching { auth.signIn("ana@correo.com", "Clave-segura") }.exceptionOrNull() is InvalidCredentialsException)
    }

    @Test
    fun `sin red no se intenta`() = runTest {
        val offline = OnlineOnlyAuthRepository(auth, FakeConnectivity(online = false))

        assertTrue(runCatching { offline.signIn("ana@correo.com", "clave-segura") }.exceptionOrNull() is OfflineException)
        assertTrue(runCatching { offline.resumeSession() }.exceptionOrNull() is OfflineException)
    }

    @Test
    fun `las reglas del formulario`() {
        assertTrue(AuthRules.isValidEmail("ana.rios@correo.com"))
        assertTrue(AuthRules.isValidEmail(" ana@correo.com.co "))
        assertFalse(AuthRules.isValidEmail("ana.rios@correo"))
        assertFalse(AuthRules.isValidEmail("ana rios@correo.com"))
        assertFalse(AuthRules.isValidEmail("@correo.com"))
        assertTrue(AuthRules.isValidPassword("12345678"))
        assertFalse(AuthRules.isValidPassword("1234567"))
    }
}
