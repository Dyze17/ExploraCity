package co.edu.uniquindio.exploracity

import co.edu.uniquindio.exploracity.plugins.configureRouting
import co.edu.uniquindio.exploracity.plugins.configureSerialization
import co.edu.uniquindio.exploracity.plugins.configureStatusPages
import io.ktor.server.application.Application
import io.ktor.server.netty.EngineMain

fun main(args: Array<String>) = EngineMain.main(args)

fun Application.module() {
    configureSerialization()
    configureStatusPages()
    configureRouting()
}
