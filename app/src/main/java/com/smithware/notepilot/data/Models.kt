package com.smithware.notepilot.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

enum class CaptureType { PlainNote, Checklist, TodoList, ShoppingList, ReminderDraft, IdeaNote }
enum class CaptureSource { Voice, Typed }
enum class Section { Inbox, Notes, Tasks, Lists, Ideas, Archive }

@Entity(tableName = "captures")
data class CaptureEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val cleanedContent: String,
    val rawTranscript: String,
    val type: CaptureType,
    val section: Section = Section.Inbox,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val tags: String = "",
    val checklistItems: String = "",
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val completed: Boolean = false,
    val source: CaptureSource = CaptureSource.Voice,
    val detectedDateTime: Long? = null,
    val reminderScheduled: Boolean = false
) {
    val tagList: List<String> get() = tags.split("|").filter { it.isNotBlank() }
    val itemList: List<String> get() = checklistItems.split("|").filter { it.isNotBlank() }
}

data class FormattedCapture(
    val title: String,
    val type: CaptureType,
    val cleanedText: String,
    val checklistItems: List<String> = emptyList(),
    val detectedDateTime: Long? = null,
    val detectedTags: List<String> = emptyList(),
    val confidenceScore: Float = 0.5f,
    val rawTranscript: String,
    val warnings: List<String> = emptyList()
)

fun FormattedCapture.toEntity(
    section: Section = Section.Inbox,
    source: CaptureSource = CaptureSource.Voice,
    titleOverride: String = title,
    cleanedOverride: String = cleanedText,
    dueOverride: Long? = detectedDateTime
) = CaptureEntity(
    title = titleOverride.ifBlank { "Untitled capture" },
    cleanedContent = cleanedOverride,
    rawTranscript = rawTranscript,
    type = type,
    section = section,
    tags = detectedTags.joinToString("|"),
    checklistItems = checklistItems.joinToString("|"),
    source = source,
    detectedDateTime = dueOverride
)
