package org.openmeds.reminder.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.LocalTime

@Composable
fun MedicationEditorScreen(
    viewModel: MedicationEditorViewModel,
    onDone: () -> Unit
) {
    val draft by viewModel.draft.collectAsState()
    val step by viewModel.step.collectAsState()
    val errors by viewModel.errors.collectAsState()
    val saved by viewModel.saved.collectAsState()
    LaunchedEffect(saved) {
        if (saved) onDone()
    }
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = if (viewModel.isEdit) "编辑药品" else "添加药品",
            style = androidx.compose.material3.MaterialTheme.typography.headlineSmall
        )
        Spacer(Modifier.height(16.dp))
        when (step) {
            1 -> MedicationFields(draft, errors, { newDraft -> viewModel.update { newDraft } })
            2 -> ScheduleFields(draft, errors, { newDraft -> viewModel.update { newDraft } })
            else -> SummaryStep(viewModel.summary)
        }
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (step > 1) {
                OutlinedButton(onClick = viewModel::back, modifier = Modifier.weight(1f).height(56.dp)) {
                    Text("上一步")
                }
            }
            if (step < 3) {
                Button(onClick = viewModel::next, modifier = Modifier.weight(1f).height(56.dp)) {
                    Text("下一步")
                }
            } else {
                Button(onClick = viewModel::save, modifier = Modifier.weight(1f).height(56.dp)) {
                    Text("保存")
                }
            }
        }
    }
}

@Composable
private fun MedicationFields(
    draft: MedicationDraft,
    errors: Map<EditorField, String>,
    update: (MedicationDraft) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = draft.name,
            onValueChange = { update(draft.copy(name = it)) },
            label = { Text("药品名称") },
            modifier = Modifier.fillMaxWidth(),
            isError = errors.containsKey(EditorField.NAME)
        )
        OutlinedTextField(
            value = draft.unit,
            onValueChange = { update(draft.copy(unit = it)) },
            label = { Text("单位（片、粒、毫升…）") },
            modifier = Modifier.fillMaxWidth(),
            isError = errors.containsKey(EditorField.UNIT)
        )
        OutlinedTextField(
            value = draft.stockText,
            onValueChange = { update(draft.copy(stockText = it)) },
            label = { Text("当前库存") },
            modifier = Modifier.fillMaxWidth(),
            isError = errors.containsKey(EditorField.STOCK)
        )
        OutlinedTextField(
            value = draft.note,
            onValueChange = { update(draft.copy(note = it)) },
            label = { Text("备注（可选）") },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ScheduleFields(
    draft: MedicationDraft,
    errors: Map<EditorField, String>,
    update: (MedicationDraft) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = draft.doseText,
            onValueChange = { update(draft.copy(doseText = it)) },
            label = { Text("单次用量") },
            modifier = Modifier.fillMaxWidth(),
            isError = errors.containsKey(EditorField.DOSE)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EditorRuleType.entries.forEach { type ->
                Button(
                    onClick = { update(draft.copy(ruleType = type)) },
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Text(ruleTypeLabel(type))
                }
            }
        }
        TimesField(draft, update)
        WeeklyField(draft, errors, update)
        IntervalField(draft, errors, update)
    }
}

@Composable
private fun TimesField(draft: MedicationDraft, update: (MedicationDraft) -> Unit) {
    OutlinedTextField(
        value = draft.times.joinToString(",") { it.toString() },
        onValueChange = { text ->
            val times = text.split(",").mapNotNull { runCatching { LocalTime.parse(it.trim()) }.getOrNull() }
            update(draft.copy(times = times))
        },
        label = { Text("提醒时间（如 09:00,21:00）") },
        modifier = Modifier.fillMaxWidth(),
        isError = draft.times.isEmpty()
    )
}

@Composable
private fun WeeklyField(draft: MedicationDraft, errors: Map<EditorField, String>, update: (MedicationDraft) -> Unit) {
    if (draft.ruleType == EditorRuleType.WEEKLY) {
        OutlinedTextField(
            value = draft.weekdays.joinToString(",") { it.value.toString() },
            onValueChange = { text ->
                val days = text.split(",").mapNotNull { runCatching { DayOfWeek.of(it.trim().toInt()) }.getOrNull() }.toSet()
                update(draft.copy(weekdays = days))
            },
            label = { Text("星期（1=周一 至 7=周日，逗号分隔）") },
            modifier = Modifier.fillMaxWidth(),
            isError = errors.containsKey(EditorField.WEEKDAYS)
        )
    }
}

@Composable
private fun IntervalField(draft: MedicationDraft, errors: Map<EditorField, String>, update: (MedicationDraft) -> Unit) {
    if (draft.ruleType == EditorRuleType.EVERY_N_DAYS) {
        OutlinedTextField(
            value = draft.intervalDays,
            onValueChange = { update(draft.copy(intervalDays = it)) },
            label = { Text("间隔天数") },
            modifier = Modifier.fillMaxWidth(),
            isError = errors.containsKey(EditorField.INTERVAL_DAYS)
        )
    }
}

@Composable
private fun SummaryStep(summary: String) {
    Text("请确认计划：", style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
    Spacer(Modifier.height(8.dp))
    Text(summary, style = androidx.compose.material3.MaterialTheme.typography.bodyLarge)
}

private fun ruleTypeLabel(type: EditorRuleType): String = when (type) {
    EditorRuleType.DAILY -> "每天"
    EditorRuleType.WEEKLY -> "每周"
    EditorRuleType.EVERY_N_DAYS -> "每 N 天"
}
