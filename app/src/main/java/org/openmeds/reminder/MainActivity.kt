package org.openmeds.reminder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import org.openmeds.reminder.ui.home.HomeViewModel
import org.openmeds.reminder.ui.navigation.AppNavHost
import org.openmeds.reminder.ui.theme.MedicationReminderTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as MedicationApplication).container
        val homeViewModel = HomeViewModel(
            repository = container.medicationRepository,
            forecaster = container.inventoryForecaster,
            capabilitySource = container.capabilityChecker,
            coordinator = container.reminderOrchestrator,
            zoneProvider = container.zoneProvider
        )
        setContent {
            MedicationReminderTheme {
                AppNavHost(homeViewModel = homeViewModel, container = container)
            }
        }
    }
}
