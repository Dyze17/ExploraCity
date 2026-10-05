package co.edu.uniquindio.exploracity.data.local

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** El perfil guardado en memoria, para las pruebas que no necesitan Room. */
class FakeProfileDao : ProfileDao {
    private val saved = MutableStateFlow<SavedProfileEntity?>(null)

    override suspend fun get(key: String): SavedProfileEntity? = saved.value?.takeIf { it.key == key }

    override suspend fun save(profile: SavedProfileEntity) {
        saved.value = profile
    }

    override fun observe(key: String): Flow<SavedProfileEntity?> = saved.map { it?.takeIf { profile -> profile.key == key } }
}
