import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

// La clave de Google Maps vive en local.properties (fuera de git): MAPS_API_KEY=... Ahí también puede ir la dirección
// de la API para depuración (API_BASE_URL); sin ella, la del equipo por USB con `adb reverse tcp:8080 tcp:8080` (A1).
// En GitHub Actions la clave llega como variable de entorno, desde los secretos del repositorio (release.yml).
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use(::load)
}

val contractExamples = rootProject.layout.projectDirectory.dir("../docs/api/ejemplos")

// La API en Cloud Run (docs/despliegue.md). No es secreta: es la dirección pública del servicio, y también la de los
// enlaces https del correo (App Links).
val productionApiHost = "exploracity-api-31949725643.us-east1.run.app"

// A1 · La versión sale de la etiqueta vX.Y.Z, que release.yml pasa en RELEASE_VERSION: versionCode = X·10000 + Y·100 + Z,
// así cada versión puede instalarse encima de la anterior. Sin ella (en el equipo o en el CI), 0.1.0.
val releaseVersion: Pair<String, Int>? = providers.environmentVariable("RELEASE_VERSION").orNull?.let { version ->
    val (major, minor, patch) = Regex("""(\d+)\.(\d{1,2})\.(\d{1,2})""").matchEntire(version)?.destructured
        ?: error("RELEASE_VERSION debe ser X.Y.Z, con Y y Z menores que 100, y no «$version».")
    version to major.toInt() * 10_000 + minor.toInt() * 100 + patch.toInt()
}

// D2 · La firma de producción solo existe en release.yml, que arma el keystore desde los secretos del repositorio. Sin
// él, la versión de producción sale sin firmar (el CI la compila igual, para ver que no se rompió).
val releaseKeystore: String? = providers.environmentVariable("RELEASE_KEYSTORE_FILE").orNull

android {
    namespace = "co.edu.uniquindio.exploracity"
    compileSdk = 37

    defaultConfig {
        applicationId = "co.edu.uniquindio.exploracity"
        minSdk = 28
        targetSdk = 37
        versionCode = releaseVersion?.second ?: 1
        versionName = releaseVersion?.first ?: "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["MAPS_API_KEY"] =
            localProperties.getProperty("MAPS_API_KEY") ?: providers.environmentVariable("MAPS_API_KEY").orNull.orEmpty()
        // C1 · Los enlaces https del correo: src/release/AndroidManifest.xml los declara y EmailLink los reconoce.
        manifestPlaceholders["appLinkHost"] = productionApiHost
        buildConfigField("String", "APP_LINK_HOST", "\"$productionApiHost\"")
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                // PKCS12: la clave tiene la misma contraseña que el almacén.
                storePassword = providers.environmentVariable("RELEASE_KEYSTORE_PASSWORD").orNull
                    ?: error("Con RELEASE_KEYSTORE_FILE también hace falta RELEASE_KEYSTORE_PASSWORD.")
                keyPassword = storePassword
                keyAlias = "exploracity"
            }
        }
    }

    buildTypes {
        debug {
            // B1 · Convive con la de producción en el mismo teléfono: otro paquete, y «ExploraCity (dev)» en el
            // lanzador (src/debug/res). Su clave de Maps necesita este paquete con la huella de depuración.
            applicationIdSuffix = ".debug"
            val apiBaseUrl = localProperties.getProperty("API_BASE_URL", "http://localhost:8080/").trimEnd('/') + "/"
            buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
        }
        release {
            // Siempre la API de Cloud Run: API_BASE_URL de local.properties solo cambia la de depuración.
            buildConfigField("String", "API_BASE_URL", "\"https://$productionApiHost/\"")
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        // BuildConfig.DEBUG: el muestrario del sistema de diseño y el buzón de desarrollo solo existen en las compilaciones
        // de desarrollo. BuildConfig.API_BASE_URL: la dirección de la API. BuildConfig.APP_LINK_HOST: la de los enlaces
        // https del correo.
        buildConfig = true
    }

    // Robolectric necesita los recursos (fuentes, textos) para medir la UI en las pruebas JVM, y en JDK 25
    // acceso a internos de java.base y a sus librerías nativas de gráficos.
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.jvmArgs("--add-opens=java.base/jdk.internal.access=ALL-UNNAMED", "--enable-native-access=ALL-UNNAMED")
                // Ejemplos del contrato con la API (docs/api/ejemplos): los leen estas pruebas y las de api-ktor.
                it.systemProperty("exploracity.contract", contractExamples.asFile.absolutePath)
                it.inputs.dir(contractExamples).withPropertyName("contractExamples")
            }
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.client.auth)
    implementation(libs.ktor.serialization.kotlinx.json)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime)

    implementation(libs.maps.compose)
    implementation(libs.play.services.location)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.ktor3)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.work.testing)
    // compose-ui-test trae un Espresso anterior a SDK 35 (usa InputManager.getInstance, ya retirado).
    testImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
