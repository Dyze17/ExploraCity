package co.edu.uniquindio.exploracity

import co.edu.uniquindio.exploracity.config.AppConfig
import co.edu.uniquindio.exploracity.config.DatabaseFactory
import co.edu.uniquindio.exploracity.config.JwtConfig
import co.edu.uniquindio.exploracity.plugins.configureRouting
import co.edu.uniquindio.exploracity.plugins.configureSecurity
import co.edu.uniquindio.exploracity.plugins.configureSerialization
import co.edu.uniquindio.exploracity.plugins.configureStatusPages
import co.edu.uniquindio.exploracity.repository.UserRepository
import co.edu.uniquindio.exploracity.service.SecurityService
import io.ktor.server.application.Application
import io.ktor.server.netty.EngineMain
import org.jetbrains.exposed.v1.jdbc.Database
import java.time.Clock

fun main(args: Array<String>) = EngineMain.main(args)

/** Arranque real: configuración de application.conf y base de datos migrada con Flyway. */
fun Application.module() {
    val config = AppConfig.load(environment.config)
    exploraModule(config, DatabaseFactory.connect(config.database))
}

/** La API con sus dependencias (inyección manual); las pruebas la arman con su propia base de datos y reloj. */
fun Application.exploraModule(config: AppConfig, database: Database, clock: Clock = Clock.systemUTC()) {
    val users = UserRepository(database)
    val security = SecurityService(users, config.moderators)
    security.syncModeratorRoles()

    configureSerialization()
    configureStatusPages()
    configureSecurity(JwtConfig(config.jwt, clock))
    configureRouting(config)
}
