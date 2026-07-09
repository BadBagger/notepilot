package com.smithware.notepilot.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [CaptureEntity::class], version = 2, exportSchema = false)
@TypeConverters(Converters::class)
abstract class NotePilotDatabase : RoomDatabase() {
    abstract fun dao(): NotePilotDao

    companion object {
        @Volatile private var instance: NotePilotDatabase? = null

        fun get(context: Context): NotePilotDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    NotePilotDatabase::class.java,
                    "notepilot.db"
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE captures ADD COLUMN reminderDateTime INTEGER")
            }
        }
    }
}
