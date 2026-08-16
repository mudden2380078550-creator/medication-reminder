package org.openmeds.reminder.ui.reminder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.openmeds.reminder.domain.model.DoseState

@Composable
fun ReminderScreen(
    state: ReminderUiState,
    onTake: (Long) -> Unit,
    onSnooze: (Long) -> Unit,
    onSkip: (Long) -> Unit,
    onAllHandled: () -> Unit
) {
    LaunchedEffect(state.allHandled) {
        if (state.allHandled) onAllHandled()
    }
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("服药提醒", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        if (state.items.isEmpty()) {
            Text("暂无待处理的服药提醒", style = MaterialTheme.typography.bodyLarge)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(state.items, key = { it.eventId }) { item ->
                ReminderItemCard(
                    item = item,
                    onTake = { onTake(item.eventId) },
                    onSnooze = { onSnooze(item.eventId) },
                    onSkip = { onSkip(item.eventId) }
                )
            }
        }
    }
}

@Composable
private fun ReminderItemCard(
    item: ReminderItem,
    onTake: () -> Unit,
    onSnooze: () -> Unit,
    onSkip: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(item.medicationName, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                "${item.scheduledTime.format(ReminderViewModel.TIME_FORMAT)} 服用 ${item.doseText}",
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(Modifier.height(8.dp))
            Text(
                when (item.state) {
                    DoseState.TAKEN -> "已服用"
                    DoseState.SKIPPED -> "已跳过"
                    DoseState.SNOOZED -> "稍后提醒"
                    DoseState.UNCONFIRMED -> "未确认"
                    DoseState.PENDING -> "待服用"
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onTake,
                    modifier = Modifier.weight(1f).height(56.dp)
                ) {
                    Text("已服用")
                }
                Button(
                    onClick = onSnooze,
                    modifier = Modifier.weight(1f).height(56.dp)
                ) {
                    Text("10 分钟后提醒")
                }
                OutlinedButton(
                    onClick = onSkip,
                    modifier = Modifier.weight(1f).height(56.dp)
                ) {
                    Text("跳过")
                }
            }
        }
    }
}
