package com.smithware.notepilot.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("settings")

/**
 * Which cloud AI service powers the opt-in AI thought-dump feature. NotePilot doesn't
 * favor either -- both are first-class, equally-supported options, so a user with only
 * an OpenAI key (or only an Anthropic key) gets the exact same feature either way.
 */
enum class AiProvider(val storageValue: String) {
    OPENAI("openai"),
    ANTHROPIC("anthropic");

    companion object {
        fun fromStorage(value: String): AiProvider = entries.firstOrNull { it.storageValue == value } ?: OPENAI
    }
}

data class AppSettings(
    val mockPremium: Boolean = false,
    val keepScreenOnCapture: Boolean = true,
    val darkMode: Boolean = false,
    val draftTranscript: String = "",
    val aiThoughtDumpEnabled: Boolean = false,
    val aiProvider: AiProvider = AiProvider.OPENAI,
    val anthropicApiKey: String = "",
    val openAiApiKey: String = ""
) {
    /** The API key for whichever provider is currently selected, for callers that don't care which. */
    val activeApiKey: String get() = if (aiProvider == AiProvider.OPENAI) openAiApiKey else anthropicApiKey
}

class SettingsStore(private val context: Context) {
    private val mockPremium = booleanPreferencesKey("mockPremium")
    private val keepScreenOnCapture = booleanPreferencesKey("keepScreenOnCapture")
    private val darkMode = booleanPreferencesKey("darkMode")
    private val draftTranscript = stringPreferencesKey("draftTranscript")
    private val aiThoughtDumpEnabled = booleanPreferencesKey("aiThoughtDumpEnabled")
    private val aiProvider = stringPreferencesKey("aiProvider")
    private val anthropicApiKey = stringPreferencesKey("anthropicApiKey")
    private val openAiApiKey = stringPreferencesKey("openAiApiKey")

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            mockPremium = prefs[mockPremium] ?: false,
            keepScreenOnCapture = prefs[keepScreenOnCapture] ?: true,
            darkMode = prefs[darkMode] ?: false,
            draftTranscript = prefs[draftTranscript] ?: "",
            aiThoughtDumpEnabled = prefs[aiThoughtDumpEnabled] ?: false,
            aiProvider = AiProvider.fromStorage(prefs[aiProvider] ?: AiProvider.OPENAI.storageValue),
            anthropicApiKey = prefs[anthropicApiKey] ?: "",
            openAiApiKey = prefs[openAiApiKey] ?: ""
        )
    }

    suspend fun setMockPremium(value: Boolean) = context.dataStore.edit { it[mockPremium] = value }
    suspend fun setKeepScreenOnCapture(value: Boolean) = context.dataStore.edit { it[keepScreenOnCapture] = value }
    suspend fun setDarkMode(value: Boolean) = context.dataStore.edit { it[darkMode] = value }
    suspend fun saveDraftTranscript(value: String) = context.dataStore.edit { it[draftTranscript] = value }
    suspend fun setAiThoughtDumpEnabled(value: Boolean) = context.dataStore.edit { it[aiThoughtDumpEnabled] = value }
    suspend fun setAiProvider(value: AiProvider) = context.dataStore.edit { it[aiProvider] = value.storageValue }

    // Plaintext DataStore, same as every other local setting here -- excluded from
    // Android backup/device-transfer via data_extraction_rules.xml/backup_rules.xml
    // so neither key leaves the device through those paths.
    suspend fun setAnthropicApiKey(value: String) = context.dataStore.edit { it[anthropicApiKey] = value.trim() }
    suspend fun setOpenAiApiKey(value: String) = context.dataStore.edit { it[openAiApiKey] = value.trim() }
}
