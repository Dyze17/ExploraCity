package co.edu.uniquindio.exploracity.repository

import co.edu.uniquindio.exploracity.model.LoginAttempts
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.vendors.ForUpdateOption
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

/** A1 · Fallos de inicio de sesión por correo (en minúscula). Corre dentro de la transacción de quien llama. */
class LoginAttemptRepository {
    /** Hasta cuándo está bloqueado [email]; null si no lo está. */
    fun lockedUntil(email: String, now: Instant): Instant? = LoginAttempts.select(LoginAttempts.lockedUntil)
        .where { LoginAttempts.email eq email }
        .singleOrNull()
        ?.get(LoginAttempts.lockedUntil)
        ?.toInstant()
        ?.takeIf { now.isBefore(it) }

    /**
     * Anota un fallo. Se cuentan en una ventana de [window] que empieza con el primero; al llegar a [maxFailures], el
     * correo queda bloqueado [lock] y la cuenta vuelve a empezar. Devuelve hasta cuándo quedó bloqueado, o null.
     */
    fun recordFailure(email: String, now: Instant, maxFailures: Int, window: Duration, lock: Duration): Instant? {
        val nowAt = now.atOffset(ZoneOffset.UTC)
        // Las filas cuya ventana pasó y que no bloquean ya no dicen nada: se borran para que la tabla no crezca.
        val stale = now.minus(window).atOffset(ZoneOffset.UTC)
        LoginAttempts.deleteWhere {
            (LoginAttempts.windowStartedAt less stale) and
                (LoginAttempts.lockedUntil.isNull() or (LoginAttempts.lockedUntil less nowAt))
        }
        LoginAttempts.insertIgnore {
            it[LoginAttempts.email] = email
            it[failures] = 0
            it[windowStartedAt] = nowAt
        }
        // Bloqueada hasta el final de la transacción: dos fallos a la vez suman dos.
        val row = LoginAttempts.selectAll()
            .where { LoginAttempts.email eq email }
            .forUpdate(ForUpdateOption.ForUpdate)
            .single()
        val started = row[LoginAttempts.windowStartedAt].toInstant()
        val inWindow = now.isBefore(started.plus(window))
        val failures = if (inWindow) row[LoginAttempts.failures] + 1 else 1
        val lockedUntil = if (failures >= maxFailures) now.plus(lock) else null
        LoginAttempts.update({ LoginAttempts.email eq email }) {
            it[LoginAttempts.failures] = if (lockedUntil == null) failures else 0
            it[windowStartedAt] = if (inWindow && lockedUntil == null) started.atOffset(ZoneOffset.UTC) else nowAt
            it[LoginAttempts.lockedUntil] = lockedUntil?.atOffset(ZoneOffset.UTC)
        }
        return lockedUntil
    }

    /** Entrar bien o crear una contraseña nueva: el correo vuelve a empezar sin fallos ni bloqueo. */
    fun clear(email: String) {
        LoginAttempts.deleteWhere { LoginAttempts.email eq email }
    }
}
