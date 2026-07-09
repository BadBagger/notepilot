package com.smithware.notepilot

import android.app.Application
import com.smithware.notepilot.data.NotePilotDatabase
import com.smithware.notepilot.data.NotePilotRepository
import com.smithware.notepilot.data.SettingsStore
import com.smithware.notepilot.format.LocalRuleBasedFormatter
import com.smithware.notepilot.notifications.ReminderScheduler

class NotePilotApp : Application() {
    val database by lazy { NotePilotDatabase.get(this) }
    val repository by lazy { NotePilotRepository(database.dao()) }
    val settingsStore by lazy { SettingsStore(this) }
    val formatter by lazy { LocalRuleBasedFormatter() }
    val reminderScheduler by lazy { ReminderScheduler(this) }
}
