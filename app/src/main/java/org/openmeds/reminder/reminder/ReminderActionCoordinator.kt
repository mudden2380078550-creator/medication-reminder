package org.openmeds.reminder.reminder

import java.time.Instant

interface ReminderActionCoordinator {
    suspend fun onHandled(eventId: Long)

    suspend fun onSnoozed(eventId: Long, actedAt: Instant)
}
