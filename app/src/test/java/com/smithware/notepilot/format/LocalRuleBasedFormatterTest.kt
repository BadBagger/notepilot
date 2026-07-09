package com.smithware.notepilot.format

import com.smithware.notepilot.data.CaptureType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
