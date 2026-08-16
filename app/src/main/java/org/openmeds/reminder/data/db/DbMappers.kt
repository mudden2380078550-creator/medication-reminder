package org.openmeds.reminder.data.db

import org.openmeds.reminder.domain.model.DoseEvent
import org.openmeds.reminder.domain.model.Medication
import org.openmeds.reminder.domain.model.MedicationPlanInput
import org.openmeds.reminder.domain.model.MedicationSchedule
import org.openmeds.reminder.domain.model.MilliUnits
import org.openmeds.reminder.domain.model.ScheduleRule
import org.openmeds.reminder.domain.model.SignedMilliUnits
import org.openmeds.reminder.domain.model.StockTransaction
import java.time.LocalTime

fun MedicationEntity.toDomain(): Medication = Medication(
    id = id,
    name = name,
    unit = unit,
    stock = SignedMilliUnits(stockMilliUnits),
    note = note,
    isActive = isActive
)

fun MedicationPlanInput.toMedicationEntity(): MedicationEntity = MedicationEntity(
    name = name,
    unit = unit,
    stockMilliUnits = stock.value,
    note = note,
    isActive = true
)

fun Medication.toEntity(): MedicationEntity = MedicationEntity(
    id = id,
    name = name,
    unit = unit,
    stockMilliUnits = stock.value,
    note = note,
    isActive = isActive
)

fun MedicationPlanInput.toScheduleEntity(medicationId: Long): ScheduleEntity {
    val ruleType = when (rule) {
        is ScheduleRule.Daily -> ScheduleRuleCode.DAILY
        is ScheduleRule.Weekly -> ScheduleRuleCode.WEEKLY
        is ScheduleRule.EveryNDays -> ScheduleRuleCode.EVERY_N_DAYS
    }
    val weekdayMask = (rule as? ScheduleRule.Weekly)?.let { WeekdayMask.toMask(it.days) } ?: 0
    val intervalDays = (rule as? ScheduleRule.EveryNDays)?.intervalDays ?: 0
    val anchorEpochDay = (rule as? ScheduleRule.EveryNDays)?.anchorDate?.toEpochDay() ?: 0L
    return ScheduleEntity(
        medicationId = medicationId,
        doseMilliUnits = dose.value,
        ruleType = ruleType,
        weekdayMask = weekdayMask,
        intervalDays = intervalDays,
        anchorEpochDay = anchorEpochDay,
        startEpochDay = startDate.toEpochDay(),
        endEpochDay = endDate?.toEpochDay()
    )
}

fun MedicationSchedule.timeEntities(): List<ScheduleTimeEntity> =
    rule.times().map { ScheduleTimeEntity(scheduleId = id, minuteOfDay = DbConverters.minuteOfDay(it)) }

fun MedicationSchedule.toScheduleEntity(): ScheduleEntity {
    val ruleType = when (rule) {
        is ScheduleRule.Daily -> ScheduleRuleCode.DAILY
        is ScheduleRule.Weekly -> ScheduleRuleCode.WEEKLY
        is ScheduleRule.EveryNDays -> ScheduleRuleCode.EVERY_N_DAYS
    }
    val weekdayMask = (rule as? ScheduleRule.Weekly)?.let { WeekdayMask.toMask(it.days) } ?: 0
    val intervalDays = (rule as? ScheduleRule.EveryNDays)?.intervalDays ?: 0
    val anchorEpochDay = (rule as? ScheduleRule.EveryNDays)?.anchorDate?.toEpochDay() ?: 0L
    return ScheduleEntity(
        id = id,
        medicationId = medicationId,
        doseMilliUnits = dose.value,
        ruleType = ruleType,
        weekdayMask = weekdayMask,
        intervalDays = intervalDays,
        anchorEpochDay = anchorEpochDay,
        startEpochDay = startDate.toEpochDay(),
        endEpochDay = endDate?.toEpochDay()
    )
}

fun DoseEvent.toEntity(): DoseEventEntity = DoseEventEntity(
    id = id,
    medicationId = medicationId,
    scheduleId = scheduleId,
    doseMilliUnits = dose.value,
    scheduledAtEpochMilli = scheduledAt.toEpochMilli(),
    state = state.name,
    actedAtEpochMilli = actedAt?.toEpochMilli(),
    reminderCount = reminderCount
)

fun StockTransaction.toEntity(): StockTransactionEntity = StockTransactionEntity(
    id = id,
    medicationId = medicationId,
    doseEventId = doseEventId,
    deltaMilliUnits = delta.value,
    occurredAtEpochMilli = occurredAt.toEpochMilli(),
    reason = reason.name
)

fun ScheduleWithTimes.toDomain(): MedicationSchedule {
    val times = times.map { DbConverters.toLocalTime(it.minuteOfDay) }
    val rule = when (schedule.ruleType) {
        ScheduleRuleCode.DAILY -> ScheduleRule.Daily(times)
        ScheduleRuleCode.WEEKLY -> ScheduleRule.Weekly(WeekdayMask.fromMask(schedule.weekdayMask), times)
        ScheduleRuleCode.EVERY_N_DAYS -> ScheduleRule.EveryNDays(
            intervalDays = schedule.intervalDays,
            anchorDate = DbConverters.toLocalDate(schedule.anchorEpochDay),
            times = times
        )
        else -> error("Unknown schedule rule type ${schedule.ruleType}")
    }
    return MedicationSchedule(
        id = schedule.id,
        medicationId = schedule.medicationId,
        dose = MilliUnits(schedule.doseMilliUnits),
        rule = rule,
        startDate = DbConverters.toLocalDate(schedule.startEpochDay),
        endDate = schedule.endEpochDay?.let { DbConverters.toLocalDate(it) }
    )
}

fun DoseEventEntity.toDomain(): DoseEvent = DoseEvent(
    id = id,
    medicationId = medicationId,
    scheduleId = scheduleId,
    dose = MilliUnits(doseMilliUnits),
    scheduledAt = DbConverters.toInstant(scheduledAtEpochMilli),
    state = DbConverters.doseState(state),
    actedAt = actedAtEpochMilli?.let { DbConverters.toInstant(it) },
    reminderCount = reminderCount
)

fun StockTransactionEntity.toDomain(): StockTransaction = StockTransaction(
    id = id,
    medicationId = medicationId,
    doseEventId = doseEventId,
    delta = SignedMilliUnits(deltaMilliUnits),
    occurredAt = DbConverters.toInstant(occurredAtEpochMilli),
    reason = DbConverters.stockReason(reason)
)

fun ScheduleRule.times(): List<LocalTime> = when (this) {
    is ScheduleRule.Daily -> times
    is ScheduleRule.Weekly -> times
    is ScheduleRule.EveryNDays -> times
}
