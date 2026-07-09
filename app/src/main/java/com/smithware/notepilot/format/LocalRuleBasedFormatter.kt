package com.smithware.notepilot.format

import com.smithware.notepilot.data.CaptureType
import com.smithware.notepilot.data.FormattedCapture
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class LocalRuleBasedFormatter : TranscriptFormatter {
    override fun formatTranscript(rawTranscript: String, context: FormatContext): FormattedCapture {
        val raw = rawTranscript.trim()
        if (raw.isBlank()) {
            return FormattedCapture("Empty capture", CaptureType.PlainNote, "", rawTranscript = raw, warnings = listOf("No transcript text was captured."))
        }
        val lower = raw.lowercase()
        val due = detectDateTime(lower, context.now)
        val type = when {
            hasAny(lower, "shopping list", "grocery list") -> CaptureType.ShoppingList
            due != null || hasAny(lower, "remind me to", "don't forget", "dont forget", "later today", "tomorrow", "next week") -> CaptureType.ReminderDraft
            hasAny(lower, "i need to", "remind me to", "add task", "i have to", "to do", "todo") -> CaptureType.TodoList
            hasAny(lower, "checklist", "list", "things i need", "order") -> CaptureType.Checklist
            hasAny(lower, "app idea", "idea", "remember this", "note") -> CaptureType.IdeaNote
            else -> CaptureType.PlainNote
        }
        val items = extractItems(raw, type)
        val title = titleFor(raw, type, items)
        val cleaned = when {
            type == CaptureType.PlainNote && items.isEmpty() -> raw.replaceFirstChar { it.titlecase(Locale.getDefault()) }
            items.isNotEmpty() -> items.joinToString("\n") { "- ${it.cleanItem()}" }
            else -> raw.cleanSentence()
        }
        val tags = buildList {
            if (type == CaptureType.ShoppingList) add("shopping")
            if (type == CaptureType.IdeaNote) add("idea")
            if (due != null) add("reminder")
        }
        val warnings = buildList {
            if (type == CaptureType.ReminderDraft && due == null) add("Reminder wording was detected, but no date or time was recognized.")
        }
        return FormattedCapture(title, type, cleaned, items.map { it.cleanItem() }, due, tags, confidence(type, items, due), raw, warnings)
    }

    private fun extractItems(raw: String, type: CaptureType): List<String> {
        val lower = raw.lowercase()
        val trimmed = raw
            .replace(Regex("(?i)^shopping list"), "")
            .replace(Regex("(?i)^grocery list"), "")
            .replace(Regex("(?i)^app idea[,]?"), "App idea:")
            .replace(Regex("(?i)i need to |remind me to |don't forget to |dont forget to |add task |i have to "), "")
            .trim(' ', '.', ',')
        val splitRegex = Regex("(?i)\\s+and also\\s+|\\s+and\\s+|,|;|\\n|\\.\\s+")
        val rough = if (type == CaptureType.ShoppingList && !lower.contains(" and ") && !raw.contains(",")) {
            trimmed.split(" ").chunkSmart()
        } else {
            trimmed.split(splitRegex)
        }
        return rough.map { it.cleanItem() }
            .filter { it.length > 1 }
            .filterNot { it.equals("also", true) || it.equals("list", true) }
            .distinct()
    }

    private fun List<String>.chunkSmart(): List<String> {
        val joiners = setOf("paper", "dog", "trash")
        val out = mutableListOf<String>()
        var index = 0
        while (index < size) {
            val word = this[index]
            if (word.lowercase() in joiners && index + 1 < size) {
                out += "$word ${this[index + 1]}"
                index += 2
            } else {
                out += word
                index += 1
            }
        }
        return out
    }

    private fun titleFor(raw: String, type: CaptureType, items: List<String>): String = when (type) {
        CaptureType.ShoppingList -> "Shopping List"
        CaptureType.TodoList -> "To-do"
        CaptureType.Checklist -> "Checklist"
        CaptureType.ReminderDraft -> items.firstOrNull()?.cleanItem()?.removePrefix("to ")?.cleanSentence() ?: "Reminder Draft"
        CaptureType.IdeaNote -> {
            val subject = Regex("(?i)(app idea|idea)[:,]?\\s*([^,.]+)").find(raw)?.groupValues?.getOrNull(2)?.trim()
            if (!subject.isNullOrBlank()) "App Idea: ${subject.cleanSentence()}" else "Idea Note"
        }
        CaptureType.PlainNote -> raw.take(42).cleanSentence()
    }

    private fun detectDateTime(lower: String, now: Long): Long? {
        val cal = Calendar.getInstance().apply { timeInMillis = now; set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        when {
            "later today" in lower -> cal.add(Calendar.HOUR_OF_DAY, 3)
            "tomorrow" in lower -> cal.add(Calendar.DAY_OF_YEAR, 1)
            "next week" in lower -> cal.add(Calendar.DAY_OF_YEAR, 7)
            Regex("in \\d+ minutes").containsMatchIn(lower) -> cal.add(Calendar.MINUTE, Regex("in (\\d+) minutes").find(lower)!!.groupValues[1].toInt())
            Regex("in \\d+ hours").containsMatchIn(lower) -> cal.add(Calendar.HOUR_OF_DAY, Regex("in (\\d+) hours").find(lower)!!.groupValues[1].toInt())
            weekdayIndex(lower) != null -> {
                val target = weekdayIndex(lower)!!
                val current = cal.get(Calendar.DAY_OF_WEEK)
                val add = ((target - current + 7) % 7).let { if (it == 0) 7 else it }
                cal.add(Calendar.DAY_OF_YEAR, add)
            }
            Regex("at \\d{1,2}").containsMatchIn(lower) -> Unit
            else -> return null
        }
        Regex("at (\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?").find(lower)?.let {
            var hour = it.groupValues[1].toInt()
            val minute = it.groupValues.getOrNull(2)?.toIntOrNull() ?: 0
            val meridiem = it.groupValues.getOrNull(3)
            if (meridiem == "pm" && hour < 12) hour += 12
            if (meridiem == "am" && hour == 12) hour = 0
            cal.set(Calendar.HOUR_OF_DAY, hour)
            cal.set(Calendar.MINUTE, minute)
        }
        if ("morning" in lower) cal.set(Calendar.HOUR_OF_DAY, 9)
        return cal.timeInMillis
    }

    private fun weekdayIndex(lower: String): Int? = listOf(
        "sunday" to Calendar.SUNDAY, "monday" to Calendar.MONDAY, "tuesday" to Calendar.TUESDAY,
        "wednesday" to Calendar.WEDNESDAY, "thursday" to Calendar.THURSDAY, "friday" to Calendar.FRIDAY, "saturday" to Calendar.SATURDAY
    ).firstOrNull { it.first in lower }?.second

    private fun confidence(type: CaptureType, items: List<String>, due: Long?) = when {
        due != null -> 0.86f
        items.size >= 2 -> 0.82f
        type != CaptureType.PlainNote -> 0.68f
        else -> 0.52f
    }

    private fun hasAny(text: String, vararg phrases: String) = phrases.any { it in text }
    private fun String.cleanItem(): String {
        val cleaned = trim(' ', '.', ',', '-', ':')
            .replace(Regex("(?i)^also\\s+"), "")
            .replace(Regex("(?i)^remember\\s+to\\s+"), "")
            .replace(Regex("(?i)^buy\\s+"), "")
            .replace(Regex("(?i)^should\\s+have\\s+a\\s+"), "")
            .replace(Regex("(?i)^turn\\s+speech\\s+into\\s+"), "Turns speech into ")
            .replace(Regex("(?i)^maybe\\s+call\\s+it\\s+"), "Possible name: ")
        return cleaned.replaceFirstChar { it.titlecase(Locale.getDefault()) }
    }
    private fun String.cleanSentence() = trim().replaceFirstChar { it.titlecase(Locale.getDefault()) }
}

fun Long.formatReminderTime(): String = SimpleDateFormat("EEE, MMM d h:mm a", Locale.getDefault()).format(this)
