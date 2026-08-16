package org.openmeds.reminder.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.time.LocalTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class ReminderSettings(
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val lowStockTime: LocalTime = LocalTime.of(9, 0)
)

interface ReminderPreferences {
    val flow: Flow<ReminderSettings>

    suspend fun updateSound(enabled: Boolean)

    suspend fun updateVibration(enabled: Boolean)

    suspend fun updateLowStockTime(time: LocalTime)
}

private val Context.reminderDataStore by preferencesDataStore(
    name = "reminder_settings",
    scope = CoroutineScope(Dispatchers.IO)
)

class DataStoreReminderPreferences(private val context: Context) : ReminderPreferences {

    override val flow: Flow<ReminderSettings> = context.reminderDataStore.data.map { prefs ->
        ReminderSettings(
            soundEnabled = prefs[KEY_SOUND] ?: true,
            vibrationEnabled = prefs[KEY_VIBRATION] ?: true,
            lowStockTime = prefs[KEY_LOW_STOCK_MINUTES]
                ?.let { LocalTime.of(it / 60, it % 60) }
                ?: LocalTime.of(9, 0)
        )
    }

    override suspend fun updateSound(enabled: Boolean) {
        context.reminderDataStore.edit { it[KEY_SOUND] = enabled }
    }

    override suspend fun updateVibration(enabled: Boolean) {
        context.reminderDataStore.edit { it[KEY_VIBRATION] = enabled }
    }

    override suspend fun updateLowStockTime(time: LocalTime) {
        context.reminderDataStore.edit { it[KEY_LOW_STOCK_MINUTES] = time.hour * 60 + time.minute }
    }

    private companion object {
        val KEY_SOUND = booleanPreferencesKey("sound_enabled")
        val KEY_VIBRATION = booleanPreferencesKey("vibration_enabled")
        val KEY_LOW_STOCK_MINUTES = intPreferencesKey("low_stock_minutes")
    }
}
