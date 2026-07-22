package com.smithware.notepilot.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NotePilotDao {
    @Query("SELECT * FROM captures ORDER BY pinned DESC, updatedAt DESC")
    fun observeAll(): Flow<List<CaptureEntity>>

    @Query("SELECT * FROM captures WHERE archived = 0 ORDER BY updatedAt DESC LIMIT 6")
    fun observeRecent(): Flow<List<CaptureEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(capture: CaptureEntity)

    @Delete
    suspend fun delete(capture: CaptureEntity)

    @Query("UPDATE captures SET section = :section, archived = :archived, updatedAt = :now WHERE id = :id")
    suspend fun move(id: String, section: Section, archived: Boolean, now: Long = System.currentTimeMillis())

    @Query("UPDATE captures SET pinned = :pinned, updatedAt = :now WHERE id = :id")
    suspend fun pin(id: String, pinned: Boolean, now: Long = System.currentTimeMillis())

    @Query("UPDATE captures SET completed = :completed, updatedAt = :now WHERE id = :id")
    suspend fun complete(id: String, completed: Boolean, now: Long = System.currentTimeMillis())

    @Query("UPDATE captures SET title = :title, cleanedContent = :content, type = :type, checklistItems = :items, category = :category, updatedAt = :now WHERE id = :id")
    suspend fun updateContent(id: String, title: String, content: String, type: CaptureType, items: String, category: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE captures SET reminderScheduled = 1, updatedAt = :now WHERE id = :id")
    suspend fun markReminderScheduled(id: String, now: Long = System.currentTimeMillis())
}
