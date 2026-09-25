package co.edu.uniquindio.exploracity.data.local

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

/**
 * Actualizar la app no puede borrar lo guardado ni la cola: cada versión migra desde la anterior. La base vieja se crea
 * con el SQL de su esquema exportado (app/schemas) y Room la abre en la versión actual, migrando y validando el
 * resultado contra lo que espera. MigrationTestHelper no sirve aquí: lee los esquemas de los assets, y con Robolectric
 * solo se ven los de la app.
 */
@RunWith(RobolectricTestRunner::class)
class ExploraDatabaseMigrationTest {

    private val context = RuntimeEnvironment.getApplication()
    private val file = context.getDatabasePath(NAME)

    @After
    fun tearDown() {
        context.deleteDatabase(NAME)
    }

    @Test
    fun `de la 1 a la actual conserva lo guardado y agrega la cola y los avisos vacíos`() {
        createVersion(1) { execSQL("INSERT INTO cache_info (`key`, savedAtMillis) VALUES ('feed', 1000)") }

        val database = Room.databaseBuilder(context, ExploraDatabase::class.java, NAME).allowMainThreadQueries().build()
        runBlocking {
            assertEquals(1000L, database.savedPlacesDao().savedAt())
            assertEquals(0, database.pendingActionsDao().count())
            assertEquals(emptyList<SavedNotificationEntity>(), database.notificationsDao().all())
        }
        database.close()
    }

    @Test
    fun `de la 2 a la actual no pierde la cola de envío`() {
        createVersion(2) {
            execSQL("INSERT INTO pending_actions (type, poiId, payload, createdAtMillis) VALUES ('VISIT', 'cafe-las-acacias', '{}', 1000)")
        }

        val database = Room.databaseBuilder(context, ExploraDatabase::class.java, NAME).allowMainThreadQueries().build()
        runBlocking { assertEquals(1, database.pendingActionsDao().count()) }
        database.close()
    }

    @Test
    fun `de la 3 a la actual conserva los avisos y agrega el perfil guardado vacío`() {
        createVersion(3) {
            execSQL("INSERT INTO saved_notifications (id, position, type, createdAtMillis, read) VALUES ('n-1', 0, 'FINALIZED', 1000, 1)")
        }

        val database = Room.databaseBuilder(context, ExploraDatabase::class.java, NAME).allowMainThreadQueries().build()
        runBlocking {
            assertEquals(listOf("n-1"), database.notificationsDao().all().map { it.id })
            assertNull(database.profileDao().get())
        }
        database.close()
    }

    /** La base tal como la dejaba la versión [version] de la app, con los datos de [fill]. */
    private fun createVersion(version: Int, fill: SQLiteDatabase.() -> Unit) {
        val schema = Json.parseToJsonElement(File("schemas/${ExploraDatabase::class.qualifiedName}/$version.json").readText())
            .jsonObject.getValue("database").jsonObject
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            schema.getValue("entities").jsonArray.map { it.jsonObject }.forEach { entity ->
                val table = entity.string("tableName")
                db.execSQL(entity.string("createSql").replace("\${TABLE_NAME}", table))
                entity["indices"]?.jsonArray?.forEach { index -> db.execSQL(index.jsonObject.string("createSql").replace("\${TABLE_NAME}", table)) }
            }
            schema.getValue("setupQueries").jsonArray.forEach { db.execSQL(it.jsonPrimitive.content) }
            db.version = version
            db.fill()
        }
    }

    private fun JsonObject.string(key: String) = getValue(key).jsonPrimitive.content

    private companion object {
        const val NAME = "migracion.db"
    }
}
