package org.openmeds.reminder.reminder

import kotlinx.coroutines.flow.StateFlow

interface ReminderCapabilitySource {
    val snapshot: StateFlow<ReminderCapabilitySnapshot>
}
