package org.openmeds.reminder.reminder

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.openmeds.reminder.MedicationApplication

class SystemRestoreReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val reason = when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> ReminderRescheduleReason.BOOT
            Intent.ACTION_TIMEZONE_CHANGED -> ReminderRescheduleReason.TIMEZONE_CHANGED
            Intent.ACTION_TIME_CHANGED -> ReminderRescheduleReason.TIME_CHANGED
            Intent.ACTION_MY_PACKAGE_REPLACED -> ReminderRescheduleReason.APP_UPGRADE
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED ->
                ReminderRescheduleReason.EXACT_ALARM_GRANTED
            else -> return
        }
        val orchestrator = (context.applicationContext as MedicationApplication).container.reminderOrchestrator
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                orchestrator.rescheduleAll(reason)
            } finally {
                pending.finish()
            }
        }
    }
}
