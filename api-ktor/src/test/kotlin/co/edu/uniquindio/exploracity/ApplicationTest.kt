package co.edu.uniquindio.exploracity

import co.edu.uniquindio.exploracity.model.Residency
import co.edu.uniquindio.exploracity.model.Role
import co.edu.uniquindio.exploracity.model.Users
import co.edu.uniquindio.exploracity.plugins.configureRouting
import co.edu.uniquindio.exploracity.plugins.configureSerialization
import co.edu.uniquindio.exploracity.plugins.configureStatusPages
import co.edu.uniquindio.exploracity.support.DatabaseTest
import co.edu.uniquindio.exploracity.support.contractExample
import co.edu.uniquindio.exploracity.support.randomSecret
import co.edu.uniquindio.exploracity.support.testConfig
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.test.Test
import kotlin.test.assertEquals

class ApplicationTest {

    @Test
    fun `health responde ok`() = testApplication {
        environment { config = MapApplicationConfig() }
        application {
            configureSerialization()
            configureRouting(testConfig())
        }

        val response = client.get("/health")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("""{"status":"ok"}""", response.bodyAsText())
    }

    @Test
    fun `la ciudad es Armenia y responde como dice el contrato`() = testApplication {
        environment { config = MapApplicationConfig() }
        application {
            configureSerialization()
            configureStatusPages()
            configureRouting(testConfig())
        }

        val response = client.get("/v1/city")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(contractExample("city.json"), Json.parseToJsonElement(response.bodyAsText()))
    }
}

/** El arranque completo con la base de datos: migra, sincroniza los moderadores y atiende. */
class ApplicationModuleTest : DatabaseTest() {

    @Test
    fun `al arrancar da el rol a las cuentas de la lista y atiende`() = testApplication {
        transaction(database) {
            Users.insert {
                it[email] = "Laura@Ejemplo.co"
                it[passwordHash] = randomSecret()
                it[name] = "Laura"
                it[residency] = Residency.RESIDENT
            }
        }
        environment { config = MapApplicationConfig() }
        application { exploraModule(testConfig(moderators = setOf("laura@ejemplo.co")), database) }

        val response = client.get("/v1/city")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(Role.MODERATOR, transaction(database) { Users.selectAll().single()[Users.role] })
    }
}
