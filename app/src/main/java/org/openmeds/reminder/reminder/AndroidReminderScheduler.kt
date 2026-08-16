package org.openmeds.reminder.reminder

import android.app.AlarmManager
import android.content.Context
import java.time.Instant
import org.openmeds.reminder.domain.model.DoseEvent

class AndroidReminderScheduler(
    private val context: Context,
    private val capabilityChecker: ReminderCapabilityChecker,
    private val pendingIntentFactory: PendingIntentFactory
) : ReminderScheduler {

    override fun scheduleDose(event: DoseEvent) {
        schedule(event.scheduledAt.toEpochMilli(), event.id, AlarmKind.DOSE, exact = true)
    }

    override fun scheduleRetry(eventId: Long, attempt: Int, at: Instant) {
        schedule(at.toEpochMilli(), eventId, AlarmKind.forRetry(attempt), exact = true)
    }

    override fun scheduleFinalize(eventId: Long, at: Instant) {
        schedule(at.toEpochMilli(), eventId, AlarmKind.FINALIZE, exact = true)
    }

    override fun cancelEvent(eventId: Long) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        for (kind in AlarmKind.entries) {
            alarmManager.cancel(pendingIntentFactory.alarmPendingIntent(context, eventId, kind))
        }
    }

    override fun scheduleLowStock(medicationId: Long, at: Instant) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pendingIntent = pendingIntentFactory.lowStockPendingIntent(context, medicationId)
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), pendingIntent)
    }

    override fun cancelLowStock(medicationId: Long) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        alarmManager.cancel(pendingIntentFactory.lowStockPendingIntent(context, medicationId))
    }

    private fun schedule(triggerAtMillis: Long, eventId: Long, kind: AlarmKind, exact: Boolean) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pendingIntent = pendingIntentFactory.alarmPendingIntent(context, eventId, kind)
        if (exact && capabilityChecker.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }
}
