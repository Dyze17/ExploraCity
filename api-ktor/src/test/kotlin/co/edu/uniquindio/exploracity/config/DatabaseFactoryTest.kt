package co.edu.uniquindio.exploracity.config

import co.edu.uniquindio.exploracity.support.DatabaseTest
import co.edu.uniquindio.exploracity.support.TestDatabase
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import java.sql.SQLException
import java.sql.SQLTransientConnectionException
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Con la base caída o lenta, la API responde con un error enseguida: la app se rinde a los 20 s, y si la API terminara
 * después (una cuenta creada, un comentario guardado), la app ya habría dicho que falló.
 */
class DatabaseFactoryTest : DatabaseTest() {

    /** Una sola conexión y 1 s de límite para todo: tres intentos de Exposed tardarían 3 s. */
    private fun withPool(block: suspend (pool: HikariDataSource, database: Database) -> Unit) {
        val settings = TestDatabase.settings.copy(
            maxPoolSize = 1,
            connectionTimeout = Duration.ofSeconds(1),
            statementTimeout = Duration.ofSeconds(1),
        )
        DatabaseFactory.pool(settings).use { pool ->
            val database = Database.connect(pool)
            try {
                runBlocking { block(pool, database) }
            } finally {
                TransactionManager.closeAndUnregister(database)
            }
        }
    }

    @Test
    fun `sin conexión libre la consulta falla tras un solo tiempo de espera, sin repetirse`() = withPool { pool, database ->
        database.query { exec("SELECT 1") }
        // La única conexión queda ocupada: para la consulta es como si la base no respondiera.
        pool.connection.use {
            var attempts = 0
            val started = System.nanoTime()

            val error = assertFailsWith<SQLException> {
                database.query {
                    attempts++
                    exec("SELECT 1")
                }
            }

            val waited = Duration.ofNanos(System.nanoTime() - started)
            assertEquals(1, attempts)
            assertTrue(waited < Duration.ofSeconds(3), "Esperó $waited")
            assertTrue(error.causes().any { it is SQLTransientConnectionException }, "Se esperaba el tiempo agotado del pool: $error")
        }
    }

    @Test
    fun `una consulta que pasa el tiempo límite se corta y no se repite`() = withPool { _, database ->
        var attempts = 0

        val error = assertFailsWith<SQLException> {
            database.query {
                attempts++
                exec("SELECT pg_sleep(3)")
            }
        }

        assertEquals(1, attempts)
        // query_canceled: PostgreSQL la cortó por el statement_timeout.
        assertEquals("57014", error.sqlState)
    }

    @Test
    fun `un choque entre peticiones sí se repite`() = withPool { _, database ->
        var attempts = 0

        val result = database.query {
            attempts++
            // Como un bloqueo mutuo (deadlock_detected): al repetirla, la transacción pasa.
            if (attempts == 1) throw SQLException("Bloqueo mutuo (prueba)", "40P01")
            exec("SELECT 1") { it.next() }
        }

        assertEquals(2, attempts)
        assertEquals(true, result)
    }

    private fun Throwable.causes(): Sequence<Throwable> = generateSequence(this) { it.cause }
}
