package org.openmeds.reminder.reminder

import org.openmeds.reminder.data.repository.MedicationRepository
import org.openmeds.reminder.domain.model.DoseEvent
import org.openmeds.reminder.domain.schedule.ScheduleEngine
import java.time.Instant
import java.time.ZoneId

interface EventPlanner {
    suspend fun ensureNextEvent(
        scheduleId: Long,
        afterExclusive: Instant,
        zoneId: ZoneId
    ): DoseEvent?
}

class ScheduleEventPlanner(
    private val repository: MedicationRepository,
    private val scheduleEngine: ScheduleEngine
) : EventPlanner {

    override suspend fun ensureNextEvent(
        scheduleId: Long,
        afterExclusive: Instant,
        zoneId: ZoneId
    ): DoseEvent? {
        val schedule = repository.schedule(scheduleId) ?: return null
        val next = scheduleEngine.nextOccurrence(schedule, afterExclusive, zoneId) ?: return null
        return repository.insertDoseEventIfAbsent(scheduleId, next)
    }
}
