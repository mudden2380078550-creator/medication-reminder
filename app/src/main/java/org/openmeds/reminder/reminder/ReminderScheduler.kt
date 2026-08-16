package org.openmeds.reminder.reminder

import org.openmeds.reminder.domain.model.DoseEvent
import java.time.Instant

interface ReminderScheduler {
    fun scheduleDose(event: DoseEvent)

    fun scheduleRetry(eventId: Long, attempt: Int, at: Instant)

    fun scheduleFinalize(eventId: Long, at: Instant)

    fun cancelEvent(eventId: Long)

    fun scheduleLowStock(medicationId: Long, at: Instant)
}
