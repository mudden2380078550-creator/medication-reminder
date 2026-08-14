package org.openmeds.reminder.domain.model

import java.time.Instant

enum class DoseState {
    PENDING,
    SNOOZED,
    TAKEN,
    SKIPPED,
    UNCONFIRMED
}

data class DoseEvent(
    val id: Long,
    val medicationId: Long,
    val scheduleId: Long,
    val dose: MilliUnits,
    val scheduledAt: Instant,
    val state: DoseState,
    val actedAt: Instant?,
    val reminderCount: Int
) {
    init {
        require(reminderCount >= 0) { "Reminder count must not be negative" }
    }
}
