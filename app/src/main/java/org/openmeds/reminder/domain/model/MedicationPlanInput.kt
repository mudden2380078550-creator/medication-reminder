package org.openmeds.reminder.domain.model

import java.time.LocalDate

data class MedicationPlanInput(
    val name: String,
    val unit: String,
    val stock: MilliUnits,
    val note: String?,
    val dose: MilliUnits,
    val rule: ScheduleRule,
    val startDate: LocalDate,
    val endDate: LocalDate?
) {
    init {
        require(name.isNotBlank()) { "Medication name must not be blank" }
        require(unit.isNotBlank()) { "Medication unit must not be blank" }
        require(endDate == null || endDate >= startDate) { "End date must not precede start date" }
    }
}
