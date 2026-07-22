package com.smithware.notepilot.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("settings")

data class AppSettings(
    val mockPremium: Boolean = false,
    val keepScreenOnCapture: Boolean = true,
    val darkMode: Boolean = false,
    val draftTranscript: String = "",
    val aiThoughtDumpEnabled: Boolean = false,
    val anthropicApiKey: String = ""
)

class SettingsStore(private val context: Context) {
    private val mockPremium = booleanPreferencesKey("mockPremium")
    private val keepScreenOnCapture = booleanPreferencesKey("keepScreenOnCapture")
    private val darkMode = booleanPreferencesKey("darkMode")
    private val draftTranscript = stringPreferencesKey("draftTranscript")
    private val aiThoughtDumpEnabled = booleanPreferencesKey("aiThoughtDumpEnabled")
    private val anthropicApiKey = stringPreferencesKey("anthropicApiKey")

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            mockPremium = prefs[mockPremium] ?: false,
            keepScreenOnCapture = prefs[keepScreenOnCapture] ?: true,
            darkMode = prefs[darkMode] ?: false,
            draftTranscript = prefs[draftTranscript] ?: "",
            aiThoughtDumpEnabled = prefs[aiThoughtDumpEnabled] ?: false,
            anthropicApiKey = prefs[anthropicApiKey] ?: ""
        )
    }

    suspend fun setMockPremium(value: Boolean) = context.dataStore.edit { it[mockPremium] = value }
    suspend fun setKeepScreenOnCapture(value: Boolean) = context.dataStore.edit { it[keepScreenOnCapture] = value }
    suspend fun setDarkMode(value: Boolean) = context.dataStore.edit { it[darkMode] = value }
    suspend fun saveDraftTranscript(value: String) = context.dataStore.edit { it[draftTranscript] = value }
    suspend fun setAiThoughtDumpEnabled(value: Boolean) = context.dataStore.edit { it[aiThoughtDumpEnabled] = value }

    // Plaintext DataStore, same as every other local setting here -- excluded from
    // Android backup/device-transfer via data_extraction_rules.xml/backup_rules.xml
    // so the key doesn't leave the device through those paths.
    suspend fun setAnthropicApiKey(value: String) = context.dataStore.edit { it[anthropicApiKey] = value.trim() }
}
