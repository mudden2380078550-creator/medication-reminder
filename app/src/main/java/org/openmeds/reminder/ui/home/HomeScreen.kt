package org.openmeds.reminder.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import org.openmeds.reminder.domain.model.DoseEvent
import org.openmeds.reminder.ui.theme.AmberContainer
import org.openmeds.reminder.ui.theme.AmberWarning
import org.openmeds.reminder.ui.theme.TextSecondary

@Composable
fun HomeScreen(
    state: HomeUiState,
    onConfirmNextDose: (Long) -> Unit,
    onAddMedication: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(todayText(), style = MaterialTheme.typography.bodyLarge)
            Text("我的药箱", style = MaterialTheme.typography.headlineSmall)
        }
        if (state.isLoading) {
            item { Text("加载中…", style = MaterialTheme.typography.bodyLarge) }
        } else {
            state.capabilityWarnings.forEach { warning ->
                item { CapabilityWarningCard(warning) }
            }
            item { NextDoseCard(state.nextDose, onConfirmNextDose) }
            items(state.medicationCards, key = { it.medicationId }) { card ->
                MedicationCardView(card)
            }
            item {
                Button(
                    onClick = onAddMedication,
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Text("添加药品")
                }
            }
        }
    }
}

private fun todayText(): String =
    LocalDate.now().format(DateTimeFormatter.ofPattern("M 月 d 日 EEEE", java.util.Locale.CHINA))

@Composable
private fun NextDoseCard(nextDose: DoseEvent?, onConfirm: (Long) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("下一次服药", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            if (nextDose == null) {
                Text("暂无待服用的提醒", style = MaterialTheme.typography.bodyLarge)
            } else {
                val time = nextDose.scheduledAt.atZone(java.time.ZoneId.systemDefault()).toLocalTime()
                Text(time.format(DateTimeFormatter.ofPattern("HH:mm")), style = MaterialTheme.typography.displayMedium)
                Spacer(Modifier.height(4.dp))
                Text("服用 ${formatQuantity(nextDose.dose.value)}", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { onConfirm(nextDose.id) },
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Text("确认已服用")
                }
            }
        }
    }
}

@Composable
private fun MedicationCardView(card: MedicationCard) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp)) {
            if (card.isLowStock) {
                Box(modifier = Modifier.width(4.dp).height(72.dp).background(AmberWarning, RoundedCornerShape(2.dp)))
                Spacer(Modifier.width(12.dp))
            }
            Column {
                Text(card.name, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(4.dp))
                Text("剩余 ${formatQuantity(card.stockMilliUnits)} ${card.unit}", style = MaterialTheme.typography.bodyLarge)
                if (card.daysRemaining != null) {
                    val message = if (card.isStockInsufficient) "库存不足" else "预计还能服用 ${card.daysRemaining} 天"
                    Text(message, style = MaterialTheme.typography.bodyLarge, color = if (card.isLowStock) AmberWarning else TextSecondary)
                }
            }
        }
    }
}

@Composable
private fun CapabilityWarningCard(warning: CapabilityWarning) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = AmberContainer)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(warning.title, style = MaterialTheme.typography.titleLarge)
            Text(warning.body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun formatQuantity(value: Long): String {
    val whole = value / 1000
    val fraction = value % 1000
    if (fraction == 0L) return whole.toString()
    val fracText = (fraction + 1000).toString().substring(1).trimEnd('0')
    return "$whole.$fracText"
}
