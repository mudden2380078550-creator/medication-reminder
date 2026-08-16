package org.openmeds.reminder.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager

data class ReminderCapabilitySnapshot(
    val notifications: Boolean,
    val exactAlarms: Boolean,
    val fullScreen: Boolean,
    val batteryRestricted: Boolean
)

class ReminderCapabilityChecker(private val context: Context) {

    fun snapshot(): ReminderCapabilitySnapshot {
        val notifications = Build.VERSION.SDK_INT < 33 ||
            context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
        return ReminderCapabilitySnapshot(
            notifications = notifications,
            exactAlarms = canScheduleExactAlarms(),
            fullScreen = hasFullScreenAccess(),
            batteryRestricted = isBatteryRestricted()
        )
    }

    fun canScheduleExactAlarms(): Boolean =
        if (Build.VERSION.SDK_INT >= 31) {
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        } else {
            true
        }

    fun exactAlarmDegraded(): Boolean = !canScheduleExactAlarms()

    private fun hasFullScreenAccess(): Boolean =
        context.checkSelfPermission(Manifest.permission.USE_FULL_SCREEN_INTENT) == PackageManager.PERMISSION_GRANTED

    private fun isBatteryRestricted(): Boolean {
        val powerManager = context.getSystemService(PowerManager::class.java)
        return !powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }
}
