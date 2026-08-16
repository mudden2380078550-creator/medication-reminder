package org.openmeds.reminder.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.openmeds.reminder.data.repository.MedicationRepository
import org.openmeds.reminder.domain.model.Medication
import org.openmeds.reminder.domain.model.MedicationSchedule
import org.openmeds.reminder.domain.model.ScheduleRule
import org.openmeds.reminder.reminder.ReminderRescheduleReason
import org.openmeds.reminder.reminder.ScheduleRescheduler

class MedicationEditorViewModel(
    private val repository: MedicationRepository,
    private val rescheduler: ScheduleRescheduler,
    private val medicationId: Long? = null
) : ViewModel() {

    private val _draft = MutableStateFlow(MedicationDraft())
    val draft: StateFlow<MedicationDraft> = _draft.asStateFlow()

    private val _step = MutableStateFlow(1)
    val step: StateFlow<Int> = _step.asStateFlow()

    private val _errors = MutableStateFlow<Map<EditorField, String>>(emptyMap())
    val errors: StateFlow<Map<EditorField, String>> = _errors.asStateFlow()

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    val summary: String
        get() = ScheduleSummaryFormatter.format(_draft.value)

    val isEdit: Boolean
        get() = medicationId != null

    init {
        if (medicationId != null) {
            viewModelScope.launch {
                val medication = repository.medicationById(medicationId) ?: return@launch
                val schedule = repository.schedules().firstOrNull { it.medicationId == medicationId }
                _draft.value = toDraft(medication, schedule)
            }
        }
    }

    fun update(transform: (MedicationDraft) -> MedicationDraft) {
        _draft.update(transform)
    }

    fun next() {
        val current = _draft.value.validate()
        _errors.value = current
        if (current.isEmpty()) _step.update { it + 1 }
    }

    fun back() {
        if (_step.value > 1) _step.update { it - 1 }
    }

    fun save() {
        viewModelScope.launch {
            val input = _draft.value.toPlanInput()
            if (medicationId == null) {
                repository.createMedication(input)
            } else {
                repository.updateMedication(medicationId, input)
            }
            rescheduler.rescheduleAll(ReminderRescheduleReason.SCHEDULE_CHANGED)
            _saved.value = true
        }
    }

    private fun toDraft(medication: Medication, schedule: MedicationSchedule?): MedicationDraft {
        if (schedule == null) {
            return MedicationDraft(
                name = medication.name,
                unit = medication.unit,
                stockText = formatMilli(medication.stock.value),
                note = medication.note.orEmpty()
            )
        }
        val ruleType = when (schedule.rule) {
            is ScheduleRule.Daily -> EditorRuleType.DAILY
            is ScheduleRule.Weekly -> EditorRuleType.WEEKLY
            is ScheduleRule.EveryNDays -> EditorRuleType.EVERY_N_DAYS
        }
        val weekdays = (schedule.rule as? ScheduleRule.Weekly)?.days ?: emptySet()
        val intervalDays = (schedule.rule as? ScheduleRule.EveryNDays)?.intervalDays?.toString() ?: ""
        val times = when (schedule.rule) {
            is ScheduleRule.Daily -> schedule.rule.times
            is ScheduleRule.Weekly -> schedule.rule.times
            is ScheduleRule.EveryNDays -> schedule.rule.times
        }
        return MedicationDraft(
            name = medication.name,
            unit = medication.unit,
            stockText = formatMilli(medication.stock.value),
            note = medication.note.orEmpty(),
            doseText = formatMilli(schedule.dose.value),
            ruleType = ruleType,
            times = times,
            weekdays = weekdays,
            intervalDays = intervalDays,
            startDate = schedule.startDate,
            endDate = schedule.endDate
        )
    }

    private fun formatMilli(value: Long): String {
        val whole = value / 1000
        val fraction = value % 1000
        if (fraction == 0L) return whole.toString()
        val fracText = (fraction + 1000).toString().substring(1).trimEnd('0')
        return whole.toString() + "." + fracText
    }
}
