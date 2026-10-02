package co.edu.uniquindio.exploracity.viewmodel

import co.edu.uniquindio.exploracity.data.local.AppPreferences
import co.edu.uniquindio.exploracity.data.local.SessionStore
import co.edu.uniquindio.exploracity.data.repository.AuthRepository
import co.edu.uniquindio.exploracity.data.repository.DemoMailbox
import co.edu.uniquindio.exploracity.domain.model.ExpiredLinkException
import co.edu.uniquindio.exploracity.domain.model.InvalidCredentialsException
import co.edu.uniquindio.exploracity.domain.model.NewAccount
import co.edu.uniquindio.exploracity.domain.model.Registration
import co.edu.uniquindio.exploracity.domain.model.ResetLink
import co.edu.uniquindio.exploracity.domain.model.ThemeMode
import co.edu.uniquindio.exploracity.domain.model.UserRole
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestCoroutineScheduler
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.time.Duration.Companion.seconds

// Dobles del acceso (1–6C) que comparten las pruebas de sus ViewModels.

internal class MemoryAccessPreferences : AppPreferences {
    val seen = MutableStateFlow(false)
    override val themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    override val onboardingSeen = seen

    override suspend fun setThemeMode(mode: ThemeMode) {
        themeMode.value = mode
    }

    override suspend fun setOnboardingSeen() {
        seen.value = true
    }
}

internal class MemorySessions : SessionStore {
    val current = MutableStateFlow<UserRole?>(null)
    override val role = current

    override suspend fun open(role: UserRole) {
        current.value = role
    }

    override suspend fun close() {
        current.value = null
    }
}

/**
 * Dos cuentas: «ana@correo.com» y «moderador@correo.com», ambas con «clave-segura». Registrar, pedir el enlace y
 * guardar la contraseña tardan 1 s; abrir el enlace, medio. Cada operación anota lo que recibió y puede fallar.
 */
internal class FakeAuth : AuthRepository {
    var signIns = 0
    var resumes = 0
    var lastEmail: String? = null
    var signInError: Exception? = null
    var resumeError: Exception? = null
    var resumeDelay = 0.5.seconds

    val registered = mutableListOf<NewAccount>()
    var registerError: Exception? = null
    var welcomeEmailSent = true

    val resetRequests = mutableListOf<String>()
    var resetRequestError: Exception? = null

    /** El enlace que abre 6.b; null = vencido. */
    var link: ResetLink? = ResetLink("ana@correo.com", Instant.EPOCH)
    var linkError: Exception? = null
    val resets = mutableListOf<Pair<String, String>>()
    var resetError: Exception? = null

    override suspend fun signIn(email: String, password: String): UserRole {
        signIns++
        lastEmail = email
        delay(1.seconds)
        signInError?.let { throw it }
        if (password != "clave-segura") throw InvalidCredentialsException()
        return when (email) {
            "ana@correo.com" -> UserRole.USER
            "moderador@correo.com" -> UserRole.MODERATOR
            else -> throw InvalidCredentialsException()
        }
    }

    override suspend fun resumeSession() {
        resumes++
        delay(resumeDelay)
        resumeError?.let { throw it }
    }

    override suspend fun register(account: NewAccount): Registration {
        registered += account
        delay(1.seconds)
        registerError?.let { throw it }
        return Registration(UserRole.USER, welcomeEmailSent)
    }

    override suspend fun requestPasswordReset(email: String) {
        resetRequests += email
        delay(1.seconds)
        resetRequestError?.let { throw it }
    }

    override suspend fun openResetLink(token: String): ResetLink {
        delay(0.5.seconds)
        linkError?.let { throw it }
        return link ?: throw ExpiredLinkException("ana@correo.com")
    }

    override suspend fun resetPassword(token: String, password: String) {
        resets += token to password
        delay(1.seconds)
        resetError?.let { throw it }
    }
}

/** El buzón de prueba de 6.a: un enlace que sirve y otro vencido para «ana@correo.com»; a otros correos no llega nada. */
internal class FakeMailbox : DemoMailbox {
    override fun latestResetLink(email: String): String? = if (email == "ana@correo.com") "enlace-vigente" else null

    override fun expiredResetLink(email: String): String? = if (email == "ana@correo.com") "enlace-vencido" else null
}

/** La hora de las pruebas: avanza con el planificador de corrutinas (advanceTimeBy), desde [Instant.EPOCH]. */
@OptIn(ExperimentalCoroutinesApi::class)
internal class SchedulerClock(private val scheduler: TestCoroutineScheduler) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId): Clock = this

    override fun instant(): Instant = Instant.ofEpochMilli(scheduler.currentTime)
}
