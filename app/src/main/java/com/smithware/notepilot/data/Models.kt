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
    val category: String = "",
    val checklistItems: String = "",
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val completed: Boolean = false,
    val source: CaptureSource = CaptureSource.Voice,
    val detectedDateTime: Long? = null,
    val reminderDateTime: Long? = null,
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
    val reminderDateTime: Long? = null,
    val detectedTags: List<String> = emptyList(),
    val confidenceScore: Float = 0.5f,
    val rawTranscript: String,
    val warnings: List<String> = emptyList(),
    // Appended after the original positional parameters (rather than inserted where it
    // conceptually belongs, next to detectedTags) so existing positional-argument call
    // sites -- e.g. LocalRuleBasedFormatter's FormattedCapture(title, type, cleaned, ...)
    // -- don't silently shift onto the wrong parameter. Always pass this one by name.
    val category: String = ""
)

fun FormattedCapture.toEntity(
    section: Section = Section.Inbox,
    source: CaptureSource = CaptureSource.Voice,
    titleOverride: String = title,
    cleanedOverride: String = cleanedText,
    dueOverride: Long? = detectedDateTime,
    reminderOverride: Long? = reminderDateTime
) = CaptureEntity(
    title = titleOverride.ifBlank { "Untitled capture" },
    cleanedContent = cleanedOverride,
    rawTranscript = rawTranscript,
    type = type,
    section = section,
    tags = detectedTags.joinToString("|"),
    category = category,
    checklistItems = checklistItems.joinToString("|"),
    source = source,
    detectedDateTime = dueOverride,
    reminderDateTime = reminderOverride
)
