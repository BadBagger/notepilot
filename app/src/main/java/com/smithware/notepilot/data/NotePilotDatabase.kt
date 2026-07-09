package com.smithware.notepilot.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [CaptureEntity::class], version = 1, exportSchema = false)
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
                ).build().also { instance = it }
            }
    }
}
