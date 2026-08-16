package org.openmeds.reminder.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.openmeds.reminder.settings.ReminderSettings

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onExport: () -> Unit = {},
    onImport: () -> Unit = {}
) {
    val settings by viewModel.settings.collectAsState()
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("设置", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        SettingSwitchRow("提醒声音", settings.soundEnabled, viewModel::setSound)
        SettingSwitchRow("振动提醒", settings.vibrationEnabled, viewModel::setVibration)
        Spacer(Modifier.height(12.dp))
        Text("补药提醒时间：${settings.lowStockTime}", style = MaterialTheme.typography.bodyLarge)
        Text("（库存预计不足 7 天时，每天在该时间提醒补药）", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(24.dp))
        BackupUi(onExport = onExport, onImport = onImport)
    }
}

@Composable
private fun SettingSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
