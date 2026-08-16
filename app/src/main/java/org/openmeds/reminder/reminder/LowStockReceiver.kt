package org.openmeds.reminder.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.openmeds.reminder.MainActivity
import org.openmeds.reminder.MedicationApplication
import org.openmeds.reminder.domain.model.Medication

class LowStockReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val medicationId = intent.getLongExtra(PendingIntentFactory.EXTRA_MEDICATION_ID, -1L)
        if (medicationId < 0) return
        val container = (context.applicationContext as MedicationApplication).container
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val medication = container.medicationRepository.medicationById(medicationId)
                if (medication != null) showLowStockNotification(context, medication)
            } finally {
                pending.finish()
            }
        }
    }

    private fun showLowStockNotification(context: Context, medication: Medication) {
        ensureChannel(context)
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, LOW_STOCK_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("${medication.name} 库存不足")
            .setContentText("库存仅剩 ${medication.stock.value / 1000.0} ${medication.unit}，请及时补药。")
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java)
            .notify(medication.id.toInt(), notification)
    }

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            LOW_STOCK_CHANNEL_ID,
            "补药提醒",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val LOW_STOCK_CHANNEL_ID = "low_stock"
    }
}
