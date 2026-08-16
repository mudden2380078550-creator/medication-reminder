package org.openmeds.reminder.domain.model

enum class DoseAction {
    TAKEN,
    SKIPPED,
    SNOOZE
}

enum class DoseActionResult {
    Applied,
    AlreadyHandled,
    NotFound
}
