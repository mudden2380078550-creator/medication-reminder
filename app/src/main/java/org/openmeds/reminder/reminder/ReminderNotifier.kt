package org.openmeds.reminder.reminder

interface ReminderNotifier {
    fun showDoseBatch(epochMinute: Long)

    fun dismissDoseBatch(epochMinute: Long)
}
