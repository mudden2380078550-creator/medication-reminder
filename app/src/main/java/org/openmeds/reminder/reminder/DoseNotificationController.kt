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
import org.openmeds.reminder.data.repository.MedicationRepository
import org.openmeds.reminder.domain.model.DoseAction
import org.openmeds.reminder.ui.reminder.ReminderActivity

class DoseNotificationController(
    private val context: Context,
    private val repository: MedicationRepository,
    private val capabilityChecker: ReminderCapabilityChecker
) : ReminderNotifier {

    override fun showDoseBatch(epochMinute: Long) {
        CoroutineScope(Dispatchers.IO).launch {
            val events = repository.actionableEventsForMinute(epochMinute)
            if (events.isEmpty()) return@launch
            val names = events.map { repository.medication(it.id)?.name ?: "服药" }
            ensureChannel()
            val builder = NotificationCompat.Builder(context, DOSE_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setOngoing(true)
                .setContentTitle(if (names.size == 1) "该服用 ${names.first()} 了" else "该服用多种药了")
                .setContentText(names.joinToString("、"))
                .setContentIntent(openActivityIntent(epochMinute))
            if (capabilityChecker.snapshot().fullScreen) {
                builder.setFullScreenIntent(openActivityIntent(epochMinute), true)
            }
            for (event in events) {
                builder.addAction(action(event.id, DoseAction.TAKEN, "已服用"))
                builder.addAction(action(event.id, DoseAction.SNOOZE, "10 分钟后提醒"))
                builder.addAction(action(event.id, DoseAction.SKIPPED, "跳过"))
            }
            context.getSystemService(NotificationManager::class.java).notify(epochMinute.toInt(), builder.build())
        }
    }

    override fun dismissDoseBatch(epochMinute: Long) {
        context.getSystemService(NotificationManager::class.java).cancel(epochMinute.toInt())
    }

    private fun openActivityIntent(epochMinute: Long): PendingIntent {
        val intent = Intent(context, ReminderActivity::class.java)
            .putExtra(ReminderActivity.EXTRA_EPOCH_MINUTE, epochMinute)
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun action(eventId: Long, action: DoseAction, title: String): NotificationCompat.Action {
        val intent = Intent(context, DoseActionReceiver::class.java)
            .putExtra(DoseActionReceiver.EXTRA_EVENT_ID, eventId)
            .putExtra(DoseActionReceiver.EXTRA_ACTION, action.name)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            (eventId.toInt() shl 3) or (100 + action.ordinal),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Action.Builder(0, title, pendingIntent).build()
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
