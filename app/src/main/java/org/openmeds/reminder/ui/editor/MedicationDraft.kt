package org.openmeds.reminder.ui.editor

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import org.openmeds.reminder.domain.model.MedicationPlanInput
import org.openmeds.reminder.domain.model.MilliUnits
import org.openmeds.reminder.domain.model.ScheduleRule
import org.openmeds.reminder.domain.model.SignedMilliUnits

enum class EditorField {
    NAME, UNIT, STOCK, DOSE, TIMES, WEEKDAYS, INTERVAL_DAYS, START_DATE, END_DATE
}

enum class EditorRuleType { DAILY, WEEKLY, EVERY_N_DAYS }

data class MedicationDraft(
    val name: String = "",
    val unit: String = "",
    val stockText: String = "",
    val note: String = "",
    val doseText: String = "",
    val ruleType: EditorRuleType = EditorRuleType.DAILY,
    val times: List<LocalTime> = emptyList(),
    val weekdays: Set<DayOfWeek> = emptySet(),
    val intervalDays: String = "",
    val startDate: LocalDate = LocalDate.now(),
    val endDate: LocalDate? = null
) {

    fun validate(): Map<EditorField, String> {
        val errors = mutableMapOf<EditorField, String>()
        if (name.isBlank()) errors[EditorField.NAME] = "请输入药品名称"
        if (unit.isBlank()) errors[EditorField.UNIT] = "请输入单位"
        if (runCatching { SignedMilliUnits.fromDecimal(stockText) }.isFailure) {
            errors[EditorField.STOCK] = "库存数量格式不正确"
        }
        val dose = runCatching { MilliUnits.fromDecimal(doseText) }.getOrNull()
        if (dose == null || dose.value <= 0) {
            errors[EditorField.DOSE] = "单次用量必须大于 0"
        }
        if (times.isEmpty()) errors[EditorField.TIMES] = "至少添加一个提醒时间"
        when (ruleType) {
            EditorRuleType.WEEKLY -> {
                if (weekdays.isEmpty()) errors[EditorField.WEEKDAYS] = "至少选择一天"
            }
            EditorRuleType.EVERY_N_DAYS -> {
                val interval = runCatching { intervalDays.toInt() }.getOrNull()
                if (interval == null || interval < 1) errors[EditorField.INTERVAL_DAYS] = "至少为 1 天"
            }
            EditorRuleType.DAILY -> {}
        }
        if (endDate != null && endDate.isBefore(startDate)) {
            errors[EditorField.END_DATE] = "结束日期不能早于开始日期"
        }
        return errors
    }

    fun toPlanInput(): MedicationPlanInput {
        val rule = when (ruleType) {
            EditorRuleType.DAILY -> ScheduleRule.Daily(times)
            EditorRuleType.WEEKLY -> ScheduleRule.Weekly(weekdays, times)
            EditorRuleType.EVERY_N_DAYS -> ScheduleRule.EveryNDays(intervalDays.toInt(), startDate, times)
        }
        return MedicationPlanInput(
            name = name.trim(),
            unit = unit.trim(),
            stock = SignedMilliUnits.fromDecimal(stockText),
            note = note.trim().ifEmpty { null },
            dose = MilliUnits.fromDecimal(doseText),
            rule = rule,
            startDate = startDate,
            endDate = endDate
        )
    }
}
