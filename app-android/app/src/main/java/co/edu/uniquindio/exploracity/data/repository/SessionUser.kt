package co.edu.uniquindio.exploracity.data.repository

import co.edu.uniquindio.exploracity.data.local.ProfileDao
import co.edu.uniquindio.exploracity.data.local.toDomain
import co.edu.uniquindio.exploracity.data.remote.ApiSession
import co.edu.uniquindio.exploracity.domain.model.Author
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * La persona de la sesión: su id sale de la sesión con la API, y su nombre y sus puntos del perfil propio guardado
 * (26), que se pone al día cada vez que llega uno nuevo. Antes de que llegue el perfil, el nombre va vacío; sin sesión,
 * también el id.
 */
class SessionUser(session: ApiSession, profiles: ProfileDao, scope: CoroutineScope) {
    val author: StateFlow<Author> = combine(session.account, profiles.observe()) { account, saved ->
        val profile = saved?.let { runCatching { it.toDomain().author }.getOrNull() }
        when {
            account == null -> NOBODY
            profile?.id == account.userId -> profile
            else -> Author(account.userId, "", 0)
        }
    }.stateIn(scope, SharingStarted.Eagerly, NOBODY)

    private companion object {
        val NOBODY = Author("", "", 0)
    }
}
