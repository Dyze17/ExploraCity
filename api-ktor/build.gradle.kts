plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktor)
}

group = "co.edu.uniquindio"
version = "0.1.0"

application {
    mainClass = "co.edu.uniquindio.exploracity.ApplicationKt"
}

kotlin {
    jvmToolchain(25)
}

ktor {
    fatJar {
        archiveFileName = "exploracity-api.jar"
    }
}

dependencies {
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.auth.jwt)
    implementation(libs.ktor.serialization.kotlinx.json)

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)

    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.java.time)
    implementation(libs.hikari)
    implementation(libs.postgresql)
    implementation(libs.flyway.core)
    implementation(libs.flyway.postgresql)
    // Solo actúa con socketFactory en DATABASE_URL (Cloud Run); en el equipo la conexión es la de siempre.
    runtimeOnly(libs.cloud.sql.postgres.socket.factory)

    implementation(libs.bcrypt)
    implementation(libs.logback.classic)

    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.testcontainers.postgresql)
}

// Ejemplos del contrato (docs/api/ejemplos): los leen estas pruebas y las de la app.
val contractExamples = layout.projectDirectory.dir("../docs/api/ejemplos")

tasks.test {
    systemProperty("exploracity.contract", contractExamples.asFile.absolutePath)
    inputs.dir(contractExamples).withPropertyName("contractExamples")
}

// `./gradlew run` toma las variables de api-ktor/.env (fuera de git; ver .env.example).
tasks.named<JavaExec>("run") {
    val env = file(".env")
    if (env.exists()) {
        env.readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && '=' in it }
            .forEach { line ->
                val (name, value) = line.split('=', limit = 2)
                environment(name.trim(), value.trim())
            }
    }
}
