package org.openmeds.reminder.reminder

enum class ReminderRescheduleReason {
    SCHEDULE_CHANGED,
    BOOT,
    TIMEZONE_CHANGED,
    TIME_CHANGED,
    APP_UPGRADE,
    EXACT_ALARM_GRANTED,
    RESTORE
}
