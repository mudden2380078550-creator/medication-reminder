package org.openmeds.reminder.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.LocalTime
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.openmeds.reminder.settings.ReminderPreferences
import org.openmeds.reminder.settings.ReminderSettings

class SettingsViewModel(
    private val preferences: ReminderPreferences
) : ViewModel() {

    val settings: StateFlow<ReminderSettings> = preferences.flow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReminderSettings())

    fun setSound(enabled: Boolean) {
        viewModelScope.launch { preferences.updateSound(enabled) }
    }

    fun setVibration(enabled: Boolean) {
        viewModelScope.launch { preferences.updateVibration(enabled) }
    }

    fun setLowStockTime(time: LocalTime) {
        viewModelScope.launch { preferences.updateLowStockTime(time) }
    }
}
