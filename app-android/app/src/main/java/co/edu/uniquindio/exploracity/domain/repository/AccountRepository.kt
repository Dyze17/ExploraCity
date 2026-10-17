package co.edu.uniquindio.exploracity.domain.repository

import co.edu.uniquindio.exploracity.domain.model.Account
import co.edu.uniquindio.exploracity.domain.model.DataExport
import kotlinx.coroutines.flow.StateFlow
/** La cuenta de la sesión (SAD: componente de Usuarios): el correo y los datos personales (Ley 1581). */
interface AccountRepository {
    /** Llega con el inicio de sesión y se guarda con la sesión: no necesita red. Cambia al confirmar un correo nuevo. */
    val account: StateFlow<Account>

    /** 29 · «Descargar mis datos»: el servidor arma el archivo. Lanza excepción si falla la red. */
    suspend fun exportData(): DataExport

    /**
     * 30 · Borra la cuenta en el servidor: los datos personales y las fotos se van; lugares verificados, comentarios
     * y votos quedan sin autor. Todo o nada: si lanza excepción, la cuenta sigue intacta.
     */
    suspend fun deleteAccount()

    /**
     * Cambiar correo · Pide el enlace al correo nuevo; el cambio se hace al abrirlo y, mientras tanto, queda pendiente
     * en [account]. Otro pedido reemplaza al anterior (su enlace deja de servir). Lanza [InvalidCredentialsException]
     * si la contraseña no coincide, [EmailTakenException] si el correo nuevo ya tiene cuenta y
     * [EmailDeliveryException] si el correo no sale.
     */
    suspend fun requestEmailChange(newEmail: String, password: String)

    /** Vuelve a enviar el enlace del cambio pendiente; el anterior deja de servir. */
    suspend fun resendEmailChange()

    /**
     * Abre el enlace: el correo nuevo pasa a ser el de la cuenta y lo devuelve. Lanza [ExpiredLinkException] (con el
     * correo nuevo) si venció o ya se usó.
     */
    suspend fun confirmEmailChange(token: String): String
}
