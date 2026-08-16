package org.openmeds.reminder.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun BackupUi(
    onExport: () -> Unit,
    onImport: () -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        Text("本地备份", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onExport,
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text("导出备份")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onImport,
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text("导入备份")
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "备份文件包含敏感的用药信息，请妥善保管。",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
