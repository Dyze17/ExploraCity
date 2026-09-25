package co.edu.uniquindio.exploracity.data.local

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * Base local (SAD: data/local, caché Room). El esquema de cada versión se exporta a app/schemas y cada cambio lleva
 * migración: la cola de envío no se puede perder al actualizar la app.
 *
 * - 1: lo guardado para ver sin conexión (12.a).
 * - 2: la cola de envío (pending_actions).
 * - 3: los últimos avisos (saved_notifications, 25).
 * - 4: el último perfil propio (saved_profile, 26 y 27).
 */
@Database(
    entities = [
        SavedPoiEntity::class,
        SavedDetailsEntity::class,
        CacheInfoEntity::class,
        PendingActionEntity::class,
        SavedNotificationEntity::class,
        SavedProfileEntity::class,
    ],
    version = 4,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4)],
)
@TypeConverters(SavedPlacesConverters::class)
abstract class ExploraDatabase : RoomDatabase() {
    abstract fun savedPlacesDao(): SavedPlacesDao

    abstract fun pendingActionsDao(): PendingActionsDao

    abstract fun notificationsDao(): NotificationsDao

    abstract fun profileDao(): ProfileDao

    companion object {
        fun build(context: Context): ExploraDatabase =
            Room.databaseBuilder(context, ExploraDatabase::class.java, "exploracity.db").build()
    }
}
