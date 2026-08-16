package org.openmeds.reminder.domain.inventory

import org.openmeds.reminder.domain.model.Medication
import org.openmeds.reminder.domain.model.MedicationSchedule
import org.openmeds.reminder.domain.model.MilliUnits
import org.openmeds.reminder.domain.model.SignedMilliUnits
import org.openmeds.reminder.domain.schedule.ScheduleEngine
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

private data class DoseAtTime(val time: Instant, val dose: MilliUnits)

class InventoryForecaster(private val scheduleEngine: ScheduleEngine) {

    fun forecast(
        medication: Medication,
        schedules: List<MedicationSchedule>,
        now: Instant,
        zoneId: ZoneId
    ): InventoryForecast {
        val openEnded = schedules.any { it.endDate == null }
        val horizon = now.plus(SEARCH_DAYS.toLong(), ChronoUnit.DAYS)
        val doses = schedules
            .flatMap { schedule ->
                scheduleEngine.occurrencesBetween(schedule, now, horizon, zoneId)
                    .map { DoseAtTime(it, schedule.dose) }
            }
            .sortedBy { it.time }

        var remaining = medication.stock
        var depletesAt: Instant? = null
        for (dose in doses) {
            if (remaining.value < dose.dose.value) {
                depletesAt = dose.time
                break
            }
            remaining = remaining.minus(dose.dose)
        }

        val depletesDate = depletesAt?.atZone(zoneId)?.toLocalDate()
        val nowDate = now.atZone(zoneId).toLocalDate()
        val daysRemaining = if (depletesAt != null) {
            ChronoUnit.DAYS.between(nowDate, depletesDate)
        } else if (schedules.isNotEmpty() && openEnded) {
            SEARCH_DAYS.toLong()
        } else {
            null
        }
        val remainingAtCourseEnd = if (depletesAt == null && schedules.isNotEmpty() && !openEnded) {
            remaining
        } else {
            null
        }

        return InventoryForecast(
            depletesAt = depletesAt,
            remainingAtCourseEnd = remainingAtCourseEnd,
            nextDoseShortfall = depletesAt != null,
            daysRemaining = daysRemaining
        )
    }

    fun needsDailyLowStockReminder(value: InventoryForecast): Boolean =
        value.daysRemaining != null && value.daysRemaining <= SEVEN_DAY_THRESHOLD

    private companion object {
        const val SEARCH_DAYS = 366
        const val SEVEN_DAY_THRESHOLD = 7L
    }
}
