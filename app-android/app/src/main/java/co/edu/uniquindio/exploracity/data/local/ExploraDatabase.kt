package co.edu.uniquindio.exploracity.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/** Base local (SAD: data/local, caché Room). El esquema de cada versión se exporta a app/schemas. */
@Database(entities = [SavedPoiEntity::class, SavedDetailsEntity::class, CacheInfoEntity::class], version = 1)
@TypeConverters(SavedPlacesConverters::class)
abstract class ExploraDatabase : RoomDatabase() {
    abstract fun savedPlacesDao(): SavedPlacesDao

    companion object {
        fun build(context: Context): ExploraDatabase =
            Room.databaseBuilder(context, ExploraDatabase::class.java, "exploracity.db").build()
    }
}
