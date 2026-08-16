package org.openmeds.reminder.reminder

import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.openmeds.reminder.data.repository.MedicationRepository
import org.openmeds.reminder.domain.model.DoseEvent
import org.openmeds.reminder.domain.model.DoseState
import org.openmeds.reminder.domain.schedule.ScheduleEngine

class ReminderOrchestrator(
    private val repository: MedicationRepository,
    private val scheduler: ReminderScheduler,
    private val notifier: ReminderNotifier,
    private val planner: EventPlanner,
    private val zoneProvider: ZoneProvider,
    private val scheduleEngine: ScheduleEngine,
    private val clock: Clock = Clock.systemDefaultZone()
) {

    suspend fun onInitialAlarm(eventId: Long, firedAt: Instant) {
        val event = repository.event(eventId) ?: return
        if (event.state != DoseState.PENDING) return
        notifier.showDoseBatch(event.epochMinute())
        scheduler.scheduleRetry(eventId, FIRST_RETRY_ATTEMPT, firedAt.plus(INTERVAL, ChronoUnit.MINUTES))
        planner.ensureNextEvent(event.scheduleId, event.scheduledAt, zoneProvider.current())
    }

    suspend fun onRetry(eventId: Long, attempt: Int, firedAt: Instant) {
        val event = repository.event(eventId) ?: return
        if (event.state != DoseState.PENDING) return
        if (event.reminderCount >= attempt) return
        repository.setReminderCount(eventId, attempt)
        notifier.showDoseBatch(event.epochMinute())
        when (attempt) {
            1 -> scheduler.scheduleRetry(eventId, 2, firedAt.plus(INTERVAL, ChronoUnit.MINUTES))
            2 -> scheduler.scheduleRetry(eventId, 3, firedAt.plus(INTERVAL, ChronoUnit.MINUTES))
            else -> scheduler.scheduleFinalize(eventId, firedAt.plus(INTERVAL, ChronoUnit.MINUTES))
        }
    }

    suspend fun onSnoozed(eventId: Long, actedAt: Instant) {
        val event = repository.event(eventId) ?: return
        if (event.state != DoseState.SNOOZED) return
        scheduler.cancelEvent(eventId)
        if (event.reminderCount < MAX_RETRIES) {
            scheduler.scheduleRetry(eventId, event.reminderCount + 1, actedAt.plus(INTERVAL, ChronoUnit.MINUTES))
        } else {
            scheduler.scheduleFinalize(eventId, actedAt.plus(INTERVAL, ChronoUnit.MINUTES))
        }
    }

    suspend fun onFinalize(eventId: Long, firedAt: Instant) {
        repository.markUnconfirmedIfActionable(eventId, firedAt)
    }

    suspend fun onHandled(eventId: Long) {
        scheduler.cancelEvent(eventId)
    }

    suspend fun rescheduleAll(reason: ReminderRescheduleReason) {
        val zone = zoneProvider.current()
        val now = clock.instant()
        for (schedule in repository.schedules()) {
            val anchor = schedule.startDate.atStartOfDay(zone).toInstant().minusNanos(1)
            val firstOccurrence = scheduleEngine.nextOccurrence(schedule, anchor, zone)
            if (firstOccurrence != null && firstOccurrence.isBefore(now)) {
                val missed = repository.insertDoseEventIfAbsent(schedule.id, firstOccurrence)
                if (missed != null && missed.state == DoseState.PENDING) {
                    repository.markUnconfirmedIfActionable(missed.id, now)
                }
            }
            val nextFuture = planner.ensureNextEvent(schedule.id, now, zone)
            if (nextFuture != null && nextFuture.state == DoseState.PENDING) {
                scheduler.scheduleDose(nextFuture)
            }
        }
    }

    private fun DoseEvent.epochMinute(): Long = scheduledAt.toEpochMilli() / 60_000L

    private companion object {
        const val FIRST_RETRY_ATTEMPT = 1
        const val MAX_RETRIES = 3
        const val INTERVAL = 10L
    }
}
