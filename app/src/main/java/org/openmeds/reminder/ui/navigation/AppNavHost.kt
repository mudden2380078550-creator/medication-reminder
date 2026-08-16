package org.openmeds.reminder.ui.navigation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import org.openmeds.reminder.AppContainer
import org.openmeds.reminder.reminder.ReminderRescheduleReason
import org.openmeds.reminder.ui.editor.MedicationEditorScreen
import org.openmeds.reminder.ui.editor.MedicationEditorViewModel
import org.openmeds.reminder.ui.history.HistoryScreen
import org.openmeds.reminder.ui.history.HistoryViewModel
import org.openmeds.reminder.ui.home.HomeScreen
import org.openmeds.reminder.ui.home.HomeViewModel
import org.openmeds.reminder.ui.settings.SettingsScreen
import org.openmeds.reminder.ui.settings.SettingsViewModel

const val EDITOR_ROUTE = "editor"

@Composable
fun AppNavHost(
    homeViewModel: HomeViewModel,
    container: AppContainer,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    Scaffold(
        modifier = modifier,
        bottomBar = {
            NavigationBar {
                AppDestination.entries.forEach { destination ->
                    NavigationBarItem(
selected = currentRoute == destination.route,
onClick = {
navController.navigate(destination.route) {
popUpTo(navController.graph.findStartDestination().id) { saveState = true }
launchSingleTop = true
restoreState = true
}
},
icon = { Icon(Icons.Filled.Home, contentDescription = null) },
label = { Text(destination.label) }
)
}
}
}
) { padding ->
NavHost(navController, startDestination = AppDestination.HOME.route, modifier = Modifier.padding(padding)) {
composable(AppDestination.HOME.route) {
HomeScreen(state = homeViewModel.state.collectAsState().value, onConfirmNextDose = homeViewModel::confirmNextDose, onAddMedication = { navController.navigate(EDITOR_ROUTE) }, onMedicationClick = { id -> navController.navigate("$EDITOR_ROUTE/$id") })
}
composable(AppDestination.MEDICINES.route) { PlaceholderScreen("药箱") }
composable(AppDestination.HISTORY.route) {
    val viewModel = remember { HistoryViewModel(container.medicationRepository, container.reminderOrchestrator, container.zoneProvider) }
    val historyState by viewModel.state.collectAsState()
    HistoryScreen(state = historyState, onCorrect = viewModel::correct)
}
composable(AppDestination.SETTINGS.route) {
    val viewModel = remember { SettingsViewModel(container.reminderPreferences) }
    val scope = rememberCoroutineScope()
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { scope.launch { container.backupService.export(it) } }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            scope.launch {
                container.backupService.importAndRestore(it)
                container.reminderOrchestrator.rescheduleAll(ReminderRescheduleReason.RESTORE)
            }
        }
    }
    SettingsScreen(
        viewModel = viewModel,
        onExport = { exportLauncher.launch("安心服药备份.json") },
        onImport = { importLauncher.launch(arrayOf("application/json")) }
    )
}
composable(EDITOR_ROUTE) {
    MedicationEditorScreen(
        viewModel = remember {
            MedicationEditorViewModel(container.medicationRepository, container.reminderOrchestrator, null)
        },
        onDone = { navController.popBackStack() }
    )
}
composable("$EDITOR_ROUTE/{medicationId}") { backStackEntry ->
    val medicationId = backStackEntry.arguments?.getString("medicationId")?.toLongOrNull()
    MedicationEditorScreen(
        viewModel = remember {
            MedicationEditorViewModel(container.medicationRepository, container.reminderOrchestrator, medicationId)
        },
        onDone = { navController.popBackStack() }
    )
}
}
}
}
