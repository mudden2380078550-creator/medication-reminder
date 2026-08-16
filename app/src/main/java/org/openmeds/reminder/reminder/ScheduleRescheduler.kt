package org.openmeds.reminder.reminder

interface ScheduleRescheduler {
    suspend fun rescheduleAll(reason: ReminderRescheduleReason)
}
