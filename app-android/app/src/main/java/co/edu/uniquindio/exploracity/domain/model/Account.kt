package co.edu.uniquindio.exploracity.domain.model

/**
 * La cuenta de la sesión: lo que no es público del perfil. El correo solo lo ve la persona (28 y 29). [pendingEmail] es
 * el correo nuevo que espera confirmación por enlace («Cambiar correo»); hasta entonces se entra con [email].
 */
data class Account(val email: String, val pendingEmail: String? = null)

/**
 * 29 · «Descargar mis datos»: el archivo que arma el servidor con todo lo de la persona (Ley 1581, derecho de acceso).
 * [fileName] es el nombre que se propone al guardarlo.
 */
class DataExport(val fileName: String, val content: String)
