package org.openmeds.reminder.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.openmeds.reminder.MedicationApplication

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val eventId = intent.getLongExtra(PendingIntentFactory.EXTRA_EVENT_ID, -1L)
        val kindLink = intent.getIntExtra(PendingIntentFactory.EXTRA_ALARM_KIND, -1)
        if (eventId < 0 || kindLink < 0) return
        val orchestrator = (context.applicationContext as MedicationApplication).container.reminderOrchestrator
        val firedAt = Instant.now()
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (kindLink) {
                    AlarmKind.DOSE.link -> orchestrator.onInitialAlarm(eventId, firedAt)
                    AlarmKind.RETRY_1.link -> orchestrator.onRetry(eventId, 1, firedAt)
                    AlarmKind.RETRY_2.link -> orchestrator.onRetry(eventId, 2, firedAt)
                    AlarmKind.RETRY_3.link -> orchestrator.onRetry(eventId, 3, firedAt)
                    AlarmKind.FINALIZE.link -> orchestrator.onFinalize(eventId, firedAt)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
