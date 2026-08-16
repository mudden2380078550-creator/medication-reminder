package org.openmeds.reminder.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.openmeds.reminder.MedicationApplication
import org.openmeds.reminder.domain.model.DoseAction

class DoseActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val eventId = intent.getLongExtra(EXTRA_EVENT_ID, -1L)
        val actionName = intent.getStringExtra(EXTRA_ACTION) ?: return
        val action = runCatching { DoseAction.valueOf(actionName) }.getOrNull() ?: return
        if (eventId < 0) return
        val container = (context.applicationContext as MedicationApplication).container
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                container.medicationRepository.recordDoseAction(eventId, action, Instant.now())
                if (action == DoseAction.SNOOZE) {
                    container.reminderOrchestrator.onSnoozed(eventId, Instant.now())
                } else {
                    container.reminderOrchestrator.onHandled(eventId)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_EVENT_ID = "org.openmeds.reminder.extra.EVENT_ID"
        const val EXTRA_ACTION = "org.openmeds.reminder.extra.DOSE_ACTION"
    }
}
