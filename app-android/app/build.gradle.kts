import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

// La clave de Google Maps vive en local.properties (fuera de git): MAPS_API_KEY=... Ahí también puede ir la dirección
// de la API (API_BASE_URL); sin ella, la del equipo por USB con `adb reverse tcp:8080 tcp:8080` (A1).
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use(::load)
}

val contractExamples = rootProject.layout.projectDirectory.dir("../docs/api/ejemplos")

android {
    namespace = "co.edu.uniquindio.exploracity"
    compileSdk = 37

    defaultConfig {
        applicationId = "co.edu.uniquindio.exploracity"
        minSdk = 28
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["MAPS_API_KEY"] = localProperties.getProperty("MAPS_API_KEY", "")
        val apiBaseUrl = localProperties.getProperty("API_BASE_URL", "http://localhost:8080/").trimEnd('/') + "/"
        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
    }

    buildTypes {
        release {
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
        // de desarrollo. BuildConfig.API_BASE_URL: la dirección de la API.
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
