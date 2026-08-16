package org.openmeds.reminder.ui.reminder

import java.time.LocalTime
import org.openmeds.reminder.domain.model.DoseState

data class ReminderUiState(
    val items: List<ReminderItem> = emptyList(),
    val allHandled: Boolean = false
) {
    fun item(eventId: Long): ReminderItem? = items.firstOrNull { it.eventId == eventId }
}

data class ReminderItem(
    val eventId: Long,
    val medicationName: String,
    val doseText: String,
    val scheduledTime: LocalTime,
    val state: DoseState
)
