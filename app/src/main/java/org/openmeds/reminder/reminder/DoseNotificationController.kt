package org.openmeds.reminder.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.openmeds.reminder.MainActivity
import org.openmeds.reminder.data.repository.MedicationRepository

class DoseNotificationController(
    private val context: Context,
    private val repository: MedicationRepository
) : ReminderNotifier {

    override fun showDoseBatch(epochMinute: Long) {
        CoroutineScope(Dispatchers.IO).launch {
            val events = repository.actionableEventsForMinute(epochMinute)
            if (events.isEmpty()) return@launch
            val names = events.map { repository.medication(it.id)?.name ?: "服药" }
            ensureChannel()
            val contentIntent = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(context, DOSE_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setOngoing(true)
                .setContentTitle(if (names.size == 1) "该服用 ${names.first()} 了" else "该服用多种药了")
                .setContentText(names.joinToString("、"))
                .setContentIntent(contentIntent)
                .build()
            context.getSystemService(NotificationManager::class.java).notify(epochMinute.toInt(), notification)
        }
    }

    override fun dismissDoseBatch(epochMinute: Long) {
        context.getSystemService(NotificationManager::class.java).cancel(epochMinute.toInt())
    }

    private fun ensureChannel() {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            DOSE_CHANNEL_ID,
            "服药提醒",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "到点服药的全屏与声音提醒"
            setSound(Settings.System.DEFAULT_ALARM_ALERT_URI, null)
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val DOSE_CHANNEL_ID = "dose_reminders"
    }
}
