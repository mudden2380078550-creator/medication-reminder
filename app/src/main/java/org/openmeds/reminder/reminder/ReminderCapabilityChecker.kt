package org.openmeds.reminder.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class ReminderCapabilitySnapshot(
    val notifications: Boolean,
    val exactAlarms: Boolean,
    val fullScreen: Boolean,
    val batteryRestricted: Boolean
)

class ReminderCapabilityChecker(private val context: Context) : ReminderCapabilitySource {

    private val mutableSnapshot = MutableStateFlow(computeSnapshot())

    override val snapshot: StateFlow<ReminderCapabilitySnapshot> = mutableSnapshot

    fun refresh() {
        mutableSnapshot.value = computeSnapshot()
    }

    fun canScheduleExactAlarms(): Boolean =
        if (Build.VERSION.SDK_INT >= 31) {
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        } else {
            true
        }

    private fun computeSnapshot(): ReminderCapabilitySnapshot {
        val notifications = Build.VERSION.SDK_INT < 33 ||
            context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
        return ReminderCapabilitySnapshot(
            notifications = notifications,
            exactAlarms = canScheduleExactAlarms(),
            fullScreen = context.checkSelfPermission(Manifest.permission.USE_FULL_SCREEN_INTENT) ==
                PackageManager.PERMISSION_GRANTED,
            batteryRestricted = !context.getSystemService(PowerManager::class.java)
                .isIgnoringBatteryOptimizations(context.packageName)
        )
    }
}
