package com.smithware.notepilot

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smithware.notepilot.data.AiProvider
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
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
            it.tags.contains(query, true) ||
            it.category.contains(query, true)
    }
}

data class CategorizeProgress(val total: Int, val done: Int, val failed: Int, val running: Boolean)

class NotePilotViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as NotePilotApp
    private val repository: NotePilotRepository = app.repository
    private val settingsStore: SettingsStore = app.settingsStore
    private val formatter: LocalRuleBasedFormatter = app.formatter
    private val reminders: ReminderScheduler = app.reminderScheduler

    private val queryFlow = kotlinx.coroutines.flow.MutableStateFlow("")

    private val _categorizeProgress = MutableStateFlow<CategorizeProgress?>(null)
    val categorizeProgress: StateFlow<CategorizeProgress?> = _categorizeProgress.asStateFlow()

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
     * captures. Uses the cloud AI formatter (OpenAI or Anthropic, whichever
     * the user configured) when opted in and a key is set for that provider.
     * If that call fails and the *other* provider also has a key saved, retries
     * once with it before giving up -- e.g. a rate limit or outage on one
     * provider doesn't have to lose the split/category behavior entirely when
     * the user has both configured. Only ever retries when a second key
     * actually exists; otherwise (and if both fail) falls back to the local
     * rule-based formatter, which still returns something -- just as one note
     * covering the whole transcript instead of several, with no topic category.
     */
    suspend fun formatThoughtDump(raw: String): List<FormattedCapture> {
        val settings = state.value.settings
        if (!settings.aiThoughtDumpEnabled || settings.activeApiKey.isBlank()) {
            return listOf(formatter.formatTranscript(raw, FormatContext()))
        }

        val otherProvider = if (settings.aiProvider == AiProvider.OPENAI) AiProvider.ANTHROPIC else AiProvider.OPENAI
        val otherApiKey = if (otherProvider == AiProvider.OPENAI) settings.openAiApiKey else settings.anthropicApiKey

        return try {
            withContext(Dispatchers.IO) {
                app.cloudAiFormatter.formatThoughtDump(raw, settings.aiProvider, settings.activeApiKey)
            }
        } catch (primaryError: CloudAiFormatterException) {
            if (otherApiKey.isBlank()) {
                return localFallback(raw, primaryError.message)
            }
            try {
                withContext(Dispatchers.IO) {
                    app.cloudAiFormatter.formatThoughtDump(raw, otherProvider, otherApiKey)
                }
            } catch (secondaryError: CloudAiFormatterException) {
                localFallback(raw, "${primaryError.message ?: "unknown error"}; then ${secondaryError.message ?: "unknown error"}")
            }
        }
    }

    private fun localFallback(raw: String, errorDetail: String?): List<FormattedCapture> {
        val fallback = formatter.formatTranscript(raw, FormatContext())
        return listOf(
            fallback.copy(
                warnings = fallback.warnings + "AI thought-dump failed (${errorDetail ?: "unknown error"}); used local rules instead."
            )
        )
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

    fun updateCapture(capture: CaptureEntity, title: String, content: String, type: CaptureType, category: String = capture.category) = viewModelScope.launch {
        val items = if (type in setOf(CaptureType.Checklist, CaptureType.TodoList, CaptureType.ShoppingList)) {
            content.lines().map { it.trim().trimStart('-', ' ') }.filter { it.isNotBlank() }
        } else {
            emptyList()
        }
        repository.update(capture, title.ifBlank { "Untitled capture" }, content, type, items, category.trim())
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
    fun setAiProvider(value: AiProvider) = viewModelScope.launch { settingsStore.setAiProvider(value) }
    fun setAnthropicApiKey(value: String) = viewModelScope.launch { settingsStore.setAnthropicApiKey(value) }
    fun setOpenAiApiKey(value: String) = viewModelScope.launch { settingsStore.setOpenAiApiKey(value) }
    fun testNotification() = reminders.showTestNotification()

    /**
     * Backfills a topic category onto every saved capture that predates AI
     * thought-dump (or was captured via local rules, which never categorize).
     * One capture at a time -- not batched into a single giant prompt -- so a
     * failure on any one note just gets skipped rather than losing the whole
     * run, and progress can update live as each one finishes.
     */
    fun categorizeExistingNotes() = viewModelScope.launch {
        if (_categorizeProgress.value?.running == true) return@launch // already running
        val settings = state.value.settings
        if (!settings.aiThoughtDumpEnabled || settings.activeApiKey.isBlank()) return@launch

        val targets = state.value.captures.filter { it.category.isBlank() }
        if (targets.isEmpty()) {
            _categorizeProgress.value = CategorizeProgress(total = 0, done = 0, failed = 0, running = false)
            return@launch
        }

        _categorizeProgress.value = CategorizeProgress(total = targets.size, done = 0, failed = 0, running = true)
        for (capture in targets) {
            val noteText = "${capture.title}\n${capture.cleanedContent.ifBlank { capture.rawTranscript }}"
            try {
                val category = withContext(Dispatchers.IO) {
                    app.cloudAiFormatter.categorize(noteText, settings.aiProvider, settings.activeApiKey)
                }
                if (category.isNotBlank()) {
                    repository.update(capture, capture.title, capture.cleanedContent, capture.type, capture.itemList, category)
                }
                _categorizeProgress.update { it?.copy(done = it.done + 1) }
            } catch (e: CloudAiFormatterException) {
                _categorizeProgress.update { it?.copy(done = it.done + 1, failed = it.failed + 1) }
            }
        }
        _categorizeProgress.update { it?.copy(running = false) }
    }
}
