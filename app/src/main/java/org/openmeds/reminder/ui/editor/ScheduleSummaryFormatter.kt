package org.openmeds.reminder.ui.editor

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

object ScheduleSummaryFormatter {

    fun format(draft: MedicationDraft): String {
        val timeText = draft.times.joinToString("、") { it.format(TIME_FORMAT) }
        val doseText = "${draft.doseText} ${draft.unit}"
        return when (draft.ruleType) {
            EditorRuleType.DAILY ->
                "从 ${formatDate(draft.startDate)}开始，每天 $timeText 服用 $doseText"
            EditorRuleType.WEEKLY -> {
                val dayNames = draft.weekdays.sortedBy { it.value }
                    .joinToString("、") { CHINESE_DAYS[it] ?: it.name }
                "每周 $dayNames $timeText 服用 $doseText"
            }
            EditorRuleType.EVERY_N_DAYS ->
                "从 ${formatDate(draft.startDate)}开始，每 ${draft.intervalDays} 天 $timeText 服用 $doseText"
        }
    }

    fun formatDate(date: LocalDate): String =
        "${date.monthValue} 月 ${date.dayOfMonth} 日"

    private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    private val CHINESE_DAYS: Map<DayOfWeek, String> = mapOf(
        DayOfWeek.MONDAY to "周一",
        DayOfWeek.TUESDAY to "周二",
        DayOfWeek.WEDNESDAY to "周三",
        DayOfWeek.THURSDAY to "周四",
        DayOfWeek.FRIDAY to "周五",
        DayOfWeek.SATURDAY to "周六",
        DayOfWeek.SUNDAY to "周日"
    )
}
