package co.edu.uniquindio.exploracity.domain.model

/** README · Reglas de validación: correo con formato válido y contraseña de al menos 8 caracteres. */
object AuthRules {
    const val PASSWORD_MIN = 8

    // Algo antes y después de la @, y un dominio con punto: lo demás lo decide el servidor.
    private val email = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s.]{2,}$")

    fun isValidEmail(text: String): Boolean = email.matches(text.trim())

    fun isValidPassword(text: String): Boolean = text.length >= PASSWORD_MIN
}

/** El correo o la contraseña no coinciden (3.c). No dice cuál, para no revelar qué correos tienen cuenta. */
class InvalidCredentialsException : Exception("Credenciales incorrectas")

/**
 * Solo en compilaciones de desarrollo: una cuenta de prueba que el inicio de sesión (3) puede rellenar. No lleva
 * contraseña: el servidor falso acepta cualquiera válida para las cuentas de prueba.
 */
data class DemoAccount(val label: String, val email: String)
