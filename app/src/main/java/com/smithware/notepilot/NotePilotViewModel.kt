package com.smithware.notepilot

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smithware.notepilot.data.AppSettings
import com.smithware.notepilot.data.CaptureEntity
import com.smithware.notepilot.data.CaptureSource
import com.smithware.notepilot.data.CaptureType
import com.smithware.notepilot.data.FormattedCapture
import com.smithware.notepilot.data.NotePilotRepository
import com.smithware.notepilot.data.Section
import com.smithware.notepilot.data.SettingsStore
import com.smithware.notepilot.data.toEntity
import com.smithware.notepilot.format.CloudAiFormatterException
import com.smithware.notepilot.format.FormatContext
import com.smithware.notepilot.format.LocalRuleBasedFormatter
import com.smithware.notepilot.notifications.ReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class NotePilotState(
    val captures: List<CaptureEntity> = emptyList(),
    val recent: List<CaptureEntity> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val query: String = ""
) {
    val filtered: List<CaptureEntity> = captures.filter {
        query.isBlank() ||
            it.title.contains(query, true) ||
            it.cleanedContent.contains(query, true) ||
            it.rawTranscript.contains(query, true) ||
            it.tags.contains(query, true)
    }
}

class NotePilotViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as NotePilotApp
    private val repository: NotePilotRepository = app.repository
    private val settingsStore: SettingsStore = app.settingsStore
    private val formatter: LocalRuleBasedFormatter = app.formatter
    private val reminders: ReminderScheduler = app.reminderScheduler

    private val queryFlow = kotlinx.coroutines.flow.MutableStateFlow("")

    val state: StateFlow<NotePilotState> = combine(
        repository.captures,
        repository.recent,
        settingsStore.settings,
        queryFlow
    ) { captures, recent, settings, query ->
        NotePilotState(captures, recent, settings, query)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotePilotState())

    fun format(raw: String): FormattedCapture = formatter.formatTranscript(raw, FormatContext())
    fun setQuery(value: String) { queryFlow.value = value }

    /**
     * Splits one longer "thought dump" transcript into several proposed
     * captures. Uses the cloud AI formatter when the user opted in and set
     * an API key; otherwise (and on any AI failure) falls back to the local
     * rule-based formatter, which still returns something -- just as one
     * note covering the whole transcript instead of several.
     */
    suspend fun formatThoughtDump(raw: String): List<FormattedCapture> {
        val settings = state.value.settings
        if (settings.aiThoughtDumpEnabled && settings.anthropicApiKey.isNotBlank()) {
            try {
                return withContext(Dispatchers.IO) {
                    app.cloudAiFormatter.formatThoughtDump(raw, settings.anthropicApiKey)
                }
            } catch (e: CloudAiFormatterException) {
                val fallback = formatter.formatTranscript(raw, FormatContext())
                return listOf(
                    fallback.copy(
                        warnings = fallback.warnings + "AI thought-dump failed (${e.message ?: "unknown error"}); used local rules instead."
                    )
                )
            }
        }
        return listOf(formatter.formatTranscript(raw, FormatContext()))
    }

    fun saveFormatted(formatted: FormattedCapture, title: String, content: String, section: Section, dueAt: Long?, reminderAt: Long?) {
        viewModelScope.launch {
            val entity = formatted.toEntity(section = section, titleOverride = title, cleanedOverride = content, dueOverride = dueAt, reminderOverride = reminderAt)
            repository.save(entity)
            if (entity.type == CaptureType.ReminderDraft && entity.reminderDateTime != null) {
                reminders.schedule(entity.id, entity.title, entity.reminderDateTime)
                repository.markReminderScheduled(entity.id)
            }
            settingsStore.saveDraftTranscript("")
        }
    }

    fun saveTyped(title: String, content: String, type: CaptureType = CaptureType.PlainNote) {
        viewModelScope.launch {
            repository.save(
                CaptureEntity(
                    title = title.ifBlank { "Typed note" },
                    cleanedContent = content,
                    rawTranscript = content,
                    type = type,
                    source = CaptureSource.Typed,
                    section = Section.Inbox
                )
            )
        }
    }

    fun archive(capture: CaptureEntity) = viewModelScope.launch { repository.archive(capture) }
    fun delete(capture: CaptureEntity) = viewModelScope.launch { repository.delete(capture) }
    fun pin(capture: CaptureEntity) = viewModelScope.launch { repository.pin(capture) }
    fun complete(capture: CaptureEntity) = viewModelScope.launch { repository.complete(capture) }
    fun move(capture: CaptureEntity, section: Section) = viewModelScope.launch { repository.move(capture, section) }

    fun updateCapture(capture: CaptureEntity, title: String, content: String, type: CaptureType) = viewModelScope.launch {
        val items = if (type in setOf(CaptureType.Checklist, CaptureType.TodoList, CaptureType.ShoppingList)) {
            content.lines().map { it.trim().trimStart('-', ' ') }.filter { it.isNotBlank() }
        } else {
            emptyList()
        }
        repository.update(capture, title.ifBlank { "Untitled capture" }, content, type, items)
    }

    fun convertToChecklist(capture: CaptureEntity) = viewModelScope.launch {
        val items = capture.cleanedContent.lines().map { it.trim().trimStart('-', ' ') }.filter { it.isNotBlank() }
        repository.update(capture, capture.title, items.joinToString("\n") { "- $it" }, CaptureType.Checklist, items)
    }

    fun convertTranscriptToTasks(capture: CaptureEntity) = viewModelScope.launch {
        val formatted = formatter.formatTranscript(capture.rawTranscript)
        repository.update(capture, formatted.title, formatted.cleanedText, CaptureType.TodoList, formatted.checklistItems)
    }

    fun setMockPremium(value: Boolean) = viewModelScope.launch { settingsStore.setMockPremium(value) }
    fun setDarkMode(value: Boolean) = viewModelScope.launch { settingsStore.setDarkMode(value) }
    fun setKeepScreenOnCapture(value: Boolean) = viewModelScope.launch { settingsStore.setKeepScreenOnCapture(value) }
    fun saveDraftTranscript(value: String) = viewModelScope.launch { settingsStore.saveDraftTranscript(value) }
    fun setAiThoughtDumpEnabled(value: Boolean) = viewModelScope.launch { settingsStore.setAiThoughtDumpEnabled(value) }
    fun setAnthropicApiKey(value: String) = viewModelScope.launch { settingsStore.setAnthropicApiKey(value) }
    fun testNotification() = reminders.showTestNotification()
}
