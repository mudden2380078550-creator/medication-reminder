package org.openmeds.reminder.ui.history

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.format.DateTimeFormatter
import org.openmeds.reminder.domain.model.DoseAction
import org.openmeds.reminder.domain.model.DoseState

@Composable
fun HistoryScreen(
    state: HistoryUiState,
    onCorrect: (Long, DoseAction) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        if (state.items.isEmpty()) {
            item { Text("暂无服药记录", style = MaterialTheme.typography.bodyLarge) }
        } else {
            items(state.items, key = { it.eventId }) { item ->
                HistoryRow(item, onCorrect)
            }
        }
    }
}

@Composable
private fun HistoryRow(item: HistoryItem, onCorrect: (Long, DoseAction) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(item.medicationName, style = MaterialTheme.typography.headlineSmall)
            Text(
                "${item.scheduledAt.format(DateTimeFormatter.ofPattern("M月d日 HH:mm"))} · ${stateText(item.state)}",
                style = MaterialTheme.typography.bodyMedium
            )
            if (item.state == DoseState.UNCONFIRMED) {
                Row {
                    TextButton(onClick = { onCorrect(item.eventId, DoseAction.TAKEN) }) { Text("更正为已服用") }
                    TextButton(onClick = { onCorrect(item.eventId, DoseAction.SKIPPED) }) { Text("更正为已跳过") }
                }
            }
        }
    }
}

private fun stateText(state: DoseState): String = when (state) {
    DoseState.PENDING -> "待服用"
    DoseState.SNOOZED -> "稍后提醒"
    DoseState.TAKEN -> "已服用"
    DoseState.SKIPPED -> "已跳过"
    DoseState.UNCONFIRMED -> "未确认"
}
