package org.openmeds.reminder.data.repository

import org.openmeds.reminder.domain.model.DoseEvent
import org.openmeds.reminder.domain.model.Medication
import org.openmeds.reminder.domain.model.MedicationSchedule

data class HomeData(
    val medications: List<Medication>,
    val schedules: List<MedicationSchedule>,
    val nextDose: DoseEvent?
)
