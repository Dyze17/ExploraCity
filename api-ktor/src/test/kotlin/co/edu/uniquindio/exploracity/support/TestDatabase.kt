package co.edu.uniquindio.exploracity.support

import co.edu.uniquindio.exploracity.config.DatabaseFactory
import co.edu.uniquindio.exploracity.config.DatabaseSettings
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.Assume
import org.junit.Before
import org.testcontainers.DockerClientFactory
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

/**
 * PostGIS real para las pruebas, con la misma imagen de docker-compose.yml. El contenedor se crea una vez por
 * ejecución y cada prueba empieza con las tablas vacías (el catálogo de insignias se queda).
 */
object TestDatabase {
    private val image = DockerImageName.parse("postgis/postgis:16-3.5").asCompatibleSubstituteFor("postgres")

    private val container: PostgreSQLContainer by lazy { PostgreSQLContainer(image).also { it.start() } }

    val database: Database by lazy {
        DatabaseFactory.connect(DatabaseSettings(container.jdbcUrl, container.username, container.password, maxPoolSize = 4))
    }

    /** En el CI siempre hay Docker y nada se omite; aquí, sin Docker, las pruebas con base de datos se saltan. */
    fun assumeAvailable() {
        if (System.getenv("CI") == null) {
            Assume.assumeTrue("Sin Docker: se omiten las pruebas con base de datos", DockerClientFactory.instance().isDockerAvailable)
        }
    }

    fun reset() {
        transaction(database) {
            // CASCADE vacía también todo lo que cuelga de personas y lugares; los intentos de acceso van por correo.
            exec("TRUNCATE users, places, login_attempts RESTART IDENTITY CASCADE")
        }
    }
}

/** Base de las pruebas que usan la base de datos. */
abstract class DatabaseTest {
    protected val database: Database get() = TestDatabase.database

    @Before
    fun prepareDatabase() {
        TestDatabase.assumeAvailable()
        TestDatabase.reset()
    }
}
