package org.openmeds.reminder.domain.schedule

import org.openmeds.reminder.domain.model.MedicationSchedule
import org.openmeds.reminder.domain.model.ScheduleRule
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class ScheduleEngine {
    fun nextOccurrence(
        schedule: MedicationSchedule,
        afterExclusive: Instant,
        zoneId: ZoneId
    ): Instant? = occurrencesAfter(schedule, afterExclusive, zoneId).firstOrNull()

    fun occurrencesBetween(
        schedule: MedicationSchedule,
        fromInclusive: Instant,
        toExclusive: Instant,
        zoneId: ZoneId
    ): List<Instant> {
        require(fromInclusive <= toExclusive) { "Range start must not follow range end" }
        if (fromInclusive == toExclusive) return emptyList()
        return occurrencesAfter(schedule, fromInclusive.minusNanos(1), zoneId)
            .takeWhile { it < toExclusive }
            .toList()
    }

    private fun occurrencesAfter(
        schedule: MedicationSchedule,
        afterExclusive: Instant,
        zoneId: ZoneId
    ): Sequence<Instant> = sequence {
        var date = firstCandidateDate(schedule, afterExclusive.atZone(zoneId).toLocalDate())
        val endDate = schedule.endDate
        while (endDate == null || date <= endDate) {
            if (dateMatches(schedule.rule, date)) {
                val dailyOccurrences = schedule.rule.times()
                    .map { time -> LocalDateTime.of(date, time).atZone(zoneId).toInstant() }
                    .distinct()
                    .sorted()
                for (occurrence in dailyOccurrences) {
                    if (occurrence > afterExclusive) {
                        yield(occurrence)
                    }
                }
            }
            date = date.plusDays(1)
        }
    }

    private fun firstCandidateDate(schedule: MedicationSchedule, localAfterDate: LocalDate): LocalDate {
        val firstDate = maxOf(schedule.startDate, localAfterDate)
        val rule = schedule.rule
        if (rule !is ScheduleRule.EveryNDays) return firstDate

        val firstDateOnOrAfterAnchor = maxOf(firstDate, rule.anchorDate)
        val elapsedDays = ChronoUnit.DAYS.between(rule.anchorDate, firstDateOnOrAfterAnchor)
        val remainder = Math.floorMod(elapsedDays, rule.intervalDays.toLong())
        return if (remainder == 0L) {
            firstDateOnOrAfterAnchor
        } else {
            firstDateOnOrAfterAnchor.plusDays(rule.intervalDays - remainder)
        }
    }

    private fun dateMatches(rule: ScheduleRule, date: LocalDate): Boolean = when (rule) {
        is ScheduleRule.Daily -> true
        is ScheduleRule.Weekly -> date.dayOfWeek in rule.days
        is ScheduleRule.EveryNDays ->
            date >= rule.anchorDate && ChronoUnit.DAYS.between(rule.anchorDate, date) % rule.intervalDays == 0L
    }

    private fun ScheduleRule.times() = when (this) {
        is ScheduleRule.Daily -> times
        is ScheduleRule.Weekly -> times
        is ScheduleRule.EveryNDays -> times
    }
}
