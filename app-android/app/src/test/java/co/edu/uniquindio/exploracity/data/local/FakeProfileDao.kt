package co.edu.uniquindio.exploracity.data.local

/** El perfil guardado en memoria, para las pruebas que no necesitan Room. */
class FakeProfileDao : ProfileDao {
    private var saved: SavedProfileEntity? = null

    override suspend fun get(key: String): SavedProfileEntity? = saved?.takeIf { it.key == key }

    override suspend fun save(profile: SavedProfileEntity) {
        saved = profile
    }
}
