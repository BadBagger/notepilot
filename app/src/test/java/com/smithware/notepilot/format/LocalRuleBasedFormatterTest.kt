package com.smithware.notepilot.format

import com.smithware.notepilot.data.CaptureType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class LocalRuleBasedFormatterTest {
    private val formatter = LocalRuleBasedFormatter()

    @Test
    fun todoSpeechBecomesTaskList() {
        val result = formatter.formatTranscript("I need to call mom tomorrow and pick up milk and trash bags and also remember to look into that voice app idea.")
        assertEquals(CaptureType.ReminderDraft, result.type)
        assertTrue(result.cleanedText.contains("Call mom tomorrow"))
        assertTrue(result.cleanedText.contains("Pick up milk"))
        assertTrue(result.cleanedText.contains("Look into that voice app idea"))
        assertTrue(result.detectedDateTime != null)
    }

    @Test
    fun appIdeaGetsIdeaTitleAndBullets() {
        val result = formatter.formatTranscript("App idea, voice notes app, should have a big mic button, turn speech into tasks, maybe call it NotePilot.")
        assertEquals(CaptureType.IdeaNote, result.type)
        assertTrue(result.title.startsWith("App Idea"))
        assertTrue(result.cleanedText.contains("Big mic button"))
    }

    @Test
    fun shoppingListSplitsItems() {
        val result = formatter.formatTranscript("Shopping list eggs milk chicken rice paper towels dog food.")
        assertEquals(CaptureType.ShoppingList, result.type)
        assertEquals(listOf("Eggs", "Milk", "Chicken", "Rice", "Paper towels", "Dog food"), result.checklistItems)
    }

    @Test
    fun bareTimeBecomesDeadlineWithReminderThirtyMinutesPrior() {
        val now = Calendar.getInstance().apply {
            set(2026, Calendar.JULY, 9, 17, 45, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val result = formatter.formatTranscript("make a note about Coca-Cola at 11:30", FormatContext(now))
        val due = Calendar.getInstance().apply { timeInMillis = result.detectedDateTime!! }
        val reminder = Calendar.getInstance().apply { timeInMillis = result.reminderDateTime!! }

        assertEquals(CaptureType.ReminderDraft, result.type)
        assertEquals("Make a note about Coca-Cola", result.title)
        assertEquals(Calendar.JULY, due.get(Calendar.MONTH))
        assertEquals(10, due.get(Calendar.DAY_OF_MONTH))
        assertEquals(11, due.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, due.get(Calendar.MINUTE))
        assertEquals(11, reminder.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, reminder.get(Calendar.MINUTE))
    }

    @Test
    fun explicitPriorReminderOverridesDefaultOffset() {
        val now = Calendar.getInstance().apply {
            set(2026, Calendar.JULY, 9, 8, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val result = formatter.formatTranscript("remind me to call Coke at 11:30am with a reminder 45 minutes prior", FormatContext(now))
        val reminder = Calendar.getInstance().apply { timeInMillis = result.reminderDateTime!! }

        assertEquals(10, reminder.get(Calendar.HOUR_OF_DAY))
        assertEquals(45, reminder.get(Calendar.MINUTE))
    }
}
