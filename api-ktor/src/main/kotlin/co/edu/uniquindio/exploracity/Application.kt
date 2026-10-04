package co.edu.uniquindio.exploracity

import co.edu.uniquindio.exploracity.config.AppConfig
import co.edu.uniquindio.exploracity.config.DatabaseFactory
import co.edu.uniquindio.exploracity.config.JwtConfig
import co.edu.uniquindio.exploracity.integration.DevMailbox
import co.edu.uniquindio.exploracity.integration.Integrations
import co.edu.uniquindio.exploracity.integration.LocalMediaStore
import co.edu.uniquindio.exploracity.plugins.configureRouting
import co.edu.uniquindio.exploracity.plugins.configureSecurity
import co.edu.uniquindio.exploracity.plugins.configureSerialization
import co.edu.uniquindio.exploracity.plugins.configureStatusPages
import co.edu.uniquindio.exploracity.repository.AccountLinkRepository
import co.edu.uniquindio.exploracity.repository.LoginAttemptRepository
import co.edu.uniquindio.exploracity.repository.PersonalDataRepository
import co.edu.uniquindio.exploracity.repository.ReputationRepository
import co.edu.uniquindio.exploracity.repository.SessionRepository
import co.edu.uniquindio.exploracity.repository.UserRepository
import co.edu.uniquindio.exploracity.routes.accountRoutes
import co.edu.uniquindio.exploracity.routes.authRoutes
import co.edu.uniquindio.exploracity.routes.devRoutes
import co.edu.uniquindio.exploracity.routes.profileRoutes
import co.edu.uniquindio.exploracity.service.AccountMail
import co.edu.uniquindio.exploracity.service.AccountService
import co.edu.uniquindio.exploracity.service.AuthService
import co.edu.uniquindio.exploracity.service.DevMailboxService
import co.edu.uniquindio.exploracity.service.PasswordHasher
import co.edu.uniquindio.exploracity.service.ProfileService
import co.edu.uniquindio.exploracity.service.ReputationService
import co.edu.uniquindio.exploracity.service.SecurityService
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.log
import io.ktor.server.http.content.staticFiles
import io.ktor.server.netty.EngineMain
import io.ktor.server.routing.routing
import org.jetbrains.exposed.v1.jdbc.Database
import java.time.Clock

fun main(args: Array<String>) = EngineMain.main(args)

/** Arranque real: configuración de application.conf y base de datos migrada con Flyway. */
fun Application.module() {
    val config = AppConfig.load(environment.config)
    exploraModule(config, DatabaseFactory.connect(config.database))
}

/**
 * La API con sus dependencias (inyección manual). Las pruebas la arman con su propia base de datos, reloj e
 * integraciones, y con un costo de BCrypt menor.
 */
fun Application.exploraModule(
    config: AppConfig,
    database: Database,
    clock: Clock = Clock.systemUTC(),
    integrations: Integrations = Integrations.create(config, clock),
    passwords: PasswordHasher = PasswordHasher(),
) {
    monitor.subscribe(ApplicationStopped) { integrations.close() }

    val users = UserRepository(database)
    val links = AccountLinkRepository()
    val security = SecurityService(users, config.moderators)
    security.syncModeratorRoles()

    val jwt = JwtConfig(config.jwt, clock)
    val mail = AccountMail(integrations.mail, config.mail.linkBaseUrl)
    val reputation = ReputationService(ReputationRepository(), config.city)
    val auth = AuthService(
        database, users, SessionRepository(), links, LoginAttemptRepository(), security, passwords, jwt, config.jwt, mail, clock,
    )
    val accounts = AccountService(
        database, users, links, PersonalDataRepository(), reputation, passwords, mail, integrations.media, config.city, clock,
    )
    val profiles = ProfileService(database, users, reputation, integrations.media, clock)
    val devMailbox = devMailbox(config, integrations)?.let { DevMailboxService(database, it, mail, users, links, clock) }

    configureSerialization()
    configureStatusPages()
    configureSecurity(jwt)
    configureRouting(config) {
        authRoutes(auth)
        accountRoutes(accounts)
        profileRoutes(profiles)
        devMailbox?.let { devRoutes(it) }
    }
    // C1 · Sin Cloudinary, la API sirve las fotos de su carpeta local.
    (integrations.media as? LocalMediaStore)?.let { local ->
        routing { staticFiles("/media", local.directory) }
    }
}

/** El buzón de desarrollo solo se expone si se pidió (DEV_MAILBOX=true) y si de verdad los correos no salen. */
private fun Application.devMailbox(config: AppConfig, integrations: Integrations): DevMailbox? {
    if (!config.mail.devMailbox) return null
    val mailbox = integrations.mail as? DevMailbox
    if (mailbox == null) log.warn("DEV_MAILBOX no aplica: los correos salen por SendGrid, no hay buzón que leer.")
    return mailbox
}
