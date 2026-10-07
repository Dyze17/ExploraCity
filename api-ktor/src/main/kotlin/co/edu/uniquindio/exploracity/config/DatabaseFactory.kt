package co.edu.uniquindio.exploracity.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.sql.SQLException
import java.sql.SQLTransientConnectionException

/** Pool JDBC (HikariCP), migraciones de Flyway y conexión de Exposed (ADR-04 y ADR-05). */
object DatabaseFactory {
    fun connect(settings: DatabaseSettings): Database {
        val dataSource = pool(settings)
        // Con su propia conexión, sin el statement_timeout del pool: una migración puede tardar más que una consulta.
        Flyway.configure()
            .dataSource(settings.url, settings.user, settings.password)
            .locations("classpath:db/migration")
            .load()
            .migrate()
        return Database.connect(dataSource)
    }

    /** El pool de las peticiones. Con la base caída, el arranque falla de una vez (initializationFailTimeout). */
    fun pool(settings: DatabaseSettings): HikariDataSource = HikariDataSource(
        HikariConfig().apply {
            jdbcUrl = settings.url
            username = settings.user
            password = settings.password
            maximumPoolSize = settings.maxPoolSize
            // Por omisión, Hikari espera 30 s una conexión; la app se rinde a los 20 s.
            connectionTimeout = settings.connectionTimeout.toMillis()
            // PostgreSQL corta la consulta que pase de este tiempo (SQLSTATE 57014) y la transacción se deshace.
            addDataSourceProperty("options", "-c statement_timeout=${settings.statementTimeout.toMillis()}")
            // Exposed abre y confirma cada transacción.
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_READ_COMMITTED"
            validate()
        },
    )
}

/**
 * Una transacción fuera del hilo de la petición: JDBC bloquea. Exposed la repite ante una SQLException (hasta 3 veces),
 * lo que resuelve un choque entre dos peticiones, como dos envíos con el mismo clientId. Sin conexión libre o con una
 * consulta cortada por tiempo no se repite: cada intento volvería a esperar todo el tiempo límite.
 */
suspend fun <T> Database.query(block: JdbcTransaction.() -> T): T =
    withContext(Dispatchers.IO) {
        transaction(this@query) {
            try {
                block()
            } catch (e: SQLException) {
                if (e.isUnavailable()) maxAttempts = 1
                throw e
            }
        }
    }

/**
 * La base no atendió: el pool no tuvo conexión a tiempo, la conexión falló (SQLSTATE 08…) o PostgreSQL cortó la
 * consulta o se está apagando (57…, como el statement_timeout). Exposed envuelve el error de JDBC: se mira la cadena.
 */
private fun SQLException.isUnavailable(): Boolean = generateSequence<Throwable>(this) { it.cause }.any { cause ->
    cause is SQLTransientConnectionException || (cause as? SQLException)?.sqlState?.let { it.take(2) in UNAVAILABLE_CLASSES } == true
}

private val UNAVAILABLE_CLASSES = setOf("08", "57")
