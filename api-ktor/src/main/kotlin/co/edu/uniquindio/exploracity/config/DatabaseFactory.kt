package co.edu.uniquindio.exploracity.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/** Pool JDBC (HikariCP), migraciones de Flyway y conexión de Exposed (ADR-04 y ADR-05). */
object DatabaseFactory {
    fun connect(settings: DatabaseSettings): Database {
        val dataSource = HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = settings.url
                username = settings.user
                password = settings.password
                maximumPoolSize = settings.maxPoolSize
                // Exposed abre y confirma cada transacción.
                isAutoCommit = false
                transactionIsolation = "TRANSACTION_READ_COMMITTED"
                validate()
            },
        )
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate()
        return Database.connect(dataSource)
    }
}

/** Una transacción fuera del hilo de la petición: JDBC bloquea. */
suspend fun <T> Database.query(block: JdbcTransaction.() -> T): T =
    withContext(Dispatchers.IO) { transaction(this@query) { block() } }
