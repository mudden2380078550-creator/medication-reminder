package org.openmeds.reminder.backup

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import org.openmeds.reminder.domain.model.DoseEvent
import org.openmeds.reminder.domain.model.DoseState
import org.openmeds.reminder.domain.model.Medication
import org.openmeds.reminder.domain.model.MedicationSchedule
import org.openmeds.reminder.domain.model.MilliUnits
import org.openmeds.reminder.domain.model.ScheduleRule
import org.openmeds.reminder.domain.model.SignedMilliUnits
import org.openmeds.reminder.domain.model.StockTransaction
import org.openmeds.reminder.domain.model.StockTransactionReason

fun BackupMedication.toMedication(): Medication = Medication(
    id = id,
    name = name,
    unit = unit,
    stock = SignedMilliUnits(stockMilliUnits),
    note = note,
    isActive = isActive
)

fun BackupSchedule.toSchedule(times: List<BackupScheduleTime>): MedicationSchedule {
    val localTimes = times.filter { it.scheduleId == id }
        .map { LocalTime.of(it.minuteOfDay / 60, it.minuteOfDay % 60) }
    val rule = when (ruleType) {
        1 -> ScheduleRule.Daily(localTimes)
        2 -> ScheduleRule.Weekly(
            DayOfWeek.entries.filter { weekdayMask and (1 shl (it.value - 1)) != 0 }.toSet(),
            localTimes
        )
        else -> ScheduleRule.EveryNDays(intervalDays, LocalDate.ofEpochDay(anchorEpochDay), localTimes)
    }
    return MedicationSchedule(
        id = id,
        medicationId = medicationId,
        dose = MilliUnits(doseMilliUnits),
        rule = rule,
        startDate = LocalDate.ofEpochDay(startEpochDay),
        endDate = endEpochDay?.let { LocalDate.ofEpochDay(it) }
    )
}

fun BackupDoseEvent.toDoseEvent(): DoseEvent = DoseEvent(
    id = id,
    medicationId = medicationId,
    scheduleId = scheduleId,
    dose = MilliUnits(doseMilliUnits),
    scheduledAt = Instant.ofEpochMilli(scheduledAtEpochMilli),
    state = DoseState.valueOf(state),
    actedAt = actedAtEpochMilli?.let { Instant.ofEpochMilli(it) },
    reminderCount = reminderCount
)

fun BackupStockTransaction.toStockTransaction(): StockTransaction = StockTransaction(
    id = id,
    medicationId = medicationId,
    doseEventId = doseEventId,
    delta = SignedMilliUnits(deltaMilliUnits),
    occurredAt = Instant.ofEpochMilli(occurredAtEpochMilli),
    reason = StockTransactionReason.valueOf(reason)
)
