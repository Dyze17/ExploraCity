package co.edu.uniquindio.exploracity.service

import co.edu.uniquindio.exploracity.model.Role
import co.edu.uniquindio.exploracity.repository.UserRepository
import org.slf4j.LoggerFactory

/**
 * Componente de Seguridad (SAD): autoriza por rol. Las cuentas de moderador están precargadas (ADR-06): son las de la
 * lista de la configuración, nunca las que pida la app.
 */
class SecurityService(private val users: UserRepository, private val moderators: Set<String>) {
    private val log = LoggerFactory.getLogger(SecurityService::class.java)

    /** El rol que le toca a una cuenta con este correo. */
    fun roleFor(email: String): Role = if (email.trim().lowercase() in moderators) Role.MODERATOR else Role.USER

    /** Al arrancar: la lista de la configuración manda, también para quitar el rol. */
    fun syncModeratorRoles() {
        if (moderators.isEmpty()) log.warn("MODERATOR_EMAILS está vacío: ninguna cuenta tendrá la pestaña Moderación.")
        val changed = users.syncModeratorRoles(moderators)
        if (changed > 0) log.info("Rol de moderador actualizado en $changed cuentas.")
    }
}
