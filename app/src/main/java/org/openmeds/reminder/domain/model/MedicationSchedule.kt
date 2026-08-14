package org.openmeds.reminder.domain.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

sealed interface ScheduleRule {
    @ConsistentCopyVisibility
    data class Daily private constructor(val times: List<LocalTime>) : ScheduleRule {
        companion object {
            operator fun invoke(times: List<LocalTime>): Daily = Daily(normalizeTimes(times))
        }
    }

    @ConsistentCopyVisibility
    data class Weekly private constructor(
        val days: Set<DayOfWeek>,
        val times: List<LocalTime>
    ) : ScheduleRule {
        companion object {
            operator fun invoke(days: Set<DayOfWeek>, times: List<LocalTime>): Weekly {
                require(days.isNotEmpty()) { "Weekly schedules need at least one day" }
                return Weekly(days.toSet(), normalizeTimes(times))
            }
        }
    }

    @ConsistentCopyVisibility
    data class EveryNDays private constructor(
        val intervalDays: Int,
        val anchorDate: LocalDate,
        val times: List<LocalTime>
    ) : ScheduleRule {
        companion object {
            operator fun invoke(
                intervalDays: Int,
                anchorDate: LocalDate,
                times: List<LocalTime>
            ): EveryNDays {
                require(intervalDays >= 1) { "Interval must be at least one day" }
                return EveryNDays(intervalDays, anchorDate, normalizeTimes(times))
            }
        }
    }
}

data class MedicationSchedule(
    val id: Long,
    val medicationId: Long,
    val dose: MilliUnits,
    val rule: ScheduleRule,
    val startDate: LocalDate,
    val endDate: LocalDate?
) {
    init {
        require(endDate == null || endDate >= startDate) { "End date must not precede start date" }
    }
}

private fun normalizeTimes(times: List<LocalTime>): List<LocalTime> {
    require(times.isNotEmpty()) { "Schedules need at least one time" }
    return times.distinct().sorted()
}
