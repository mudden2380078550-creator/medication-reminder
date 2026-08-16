package org.openmeds.reminder.reminder

import android.app.PendingIntent
import android.content.Context
import android.content.Intent

class PendingIntentFactory {

    fun requestCode(eventId: Long, kind: AlarmKind): Int = (eventId.toInt() shl 4) or kind.link

    fun alarmPendingIntent(context: Context, eventId: Long, kind: AlarmKind): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
            .putExtra(EXTRA_EVENT_ID, eventId)
            .putExtra(EXTRA_ALARM_KIND, kind.link)
        return PendingIntent.getBroadcast(
            context,
            requestCode(eventId, kind),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun lowStockPendingIntent(context: Context, medicationId: Long): PendingIntent {
        val intent = Intent(context, LowStockReceiver::class.java)
            .putExtra(EXTRA_MEDICATION_ID, medicationId)
        return PendingIntent.getBroadcast(
            context,
            requestCode(medicationId, AlarmKind.LOW_STOCK),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val EXTRA_EVENT_ID = "org.openmeds.reminder.extra.EVENT_ID"
        const val EXTRA_ALARM_KIND = "org.openmeds.reminder.extra.ALARM_KIND"
        const val EXTRA_MEDICATION_ID = "org.openmeds.reminder.extra.MEDICATION_ID"
    }
}
