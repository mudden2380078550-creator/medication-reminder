package org.openmeds.reminder.ui.reminder

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import org.openmeds.reminder.MedicationApplication
import org.openmeds.reminder.ui.theme.MedicationReminderTheme

class ReminderActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        keepScreenOnAndShowWhenLocked()
        val epochMinute = intent.getLongExtra(EXTRA_EPOCH_MINUTE, -1L)
        val container = (application as MedicationApplication).container
        val viewModel = ReminderViewModel(
            repository = container.medicationRepository,
            coordinator = container.reminderOrchestrator,
            zoneProvider = container.zoneProvider
        )
        setContent {
            MedicationReminderTheme {
                val state by viewModel.state.collectAsState()
                ReminderScreen(
                    state = state,
                    onTake = viewModel::take,
                    onSnooze = viewModel::snooze,
                    onSkip = viewModel::skip,
                    onAllHandled = { finish() }
                )
            }
        }
        if (epochMinute >= 0) {
            viewModel.load(epochMinute)
        }
    }

    private fun keepScreenOnAndShowWhenLocked() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
    }

    companion object {
        const val EXTRA_EPOCH_MINUTE = "org.openmeds.reminder.extra.EPOCH_MINUTE"
    }
}
