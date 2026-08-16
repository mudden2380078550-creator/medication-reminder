package org.openmeds.reminder.reminder

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.openmeds.reminder.FakeMedicationRepository
import org.openmeds.reminder.domain.model.DoseAction
import org.openmeds.reminder.domain.model.DoseEvent
import org.openmeds.reminder.domain.model.DoseState
import org.openmeds.reminder.domain.model.Medication
import org.openmeds.reminder.domain.model.MedicationSchedule
import org.openmeds.reminder.domain.model.MilliUnits
import org.openmeds.reminder.domain.model.ScheduleRule
import org.openmeds.reminder.domain.model.SignedMilliUnits
import org.openmeds.reminder.domain.schedule.ScheduleEngine

class ReminderOrchestratorTest {
    private lateinit var repository: FakeMedicationRepository
    private lateinit var scheduler: FakeReminderScheduler
    private lateinit var notifier: FakeReminderNotifier
    private lateinit var planner: FakeEventPlanner
    private lateinit var orchestrator: ReminderOrchestrator

    @Before
    fun setUp() {
        repository = FakeMedicationRepository()
        repository.seedMedication(Medication(MEDICATION_ID, "Med", "tablet", SignedMilliUnits(10_000), null, true))
        repository.seedSchedule(MedicationSchedule(
            id = SCHEDULE_ID,
            medicationId = MEDICATION_ID,
            dose = MilliUnits(1_000),
            rule = ScheduleRule.Daily(listOf(LocalTime.of(9, 0))),
            startDate = LocalDate.parse("2026-08-14"),
            endDate = null
        ))
        scheduler = FakeReminderScheduler(NOW)
        notifier = FakeReminderNotifier()
        planner = FakeEventPlanner(ScheduleEventPlanner(repository, ScheduleEngine()))
        orchestrator = ReminderOrchestrator(
            repository = repository,
            scheduler = scheduler,
            notifier = notifier,
            planner = planner,
            zoneProvider = FIXED_ZONE,
            scheduleEngine = ScheduleEngine(),
            clock = Clock.fixed(NOW, ZONE)
        )
    }

    @Test
    fun initialAlarmSchedulesOnlyNextRetryAndNextDose() = runTest {
        seedPendingEvent()
        orchestrator.onInitialAlarm(EVENT_ID, NOW)
        assertEquals(listOf(10L), scheduler.retryMinutesAfterNow)
        assertTrue(scheduler.finalizeMinutesAfterNow.isEmpty())
        assertEquals(1, planner.ensureNextCalls)
        assertEquals(listOf(NOW.epochMinute()), notifier.shownBatches)
    }

    @Test
    fun initialAlarmSkipsAlreadyHandledEvent() = runTest {
        seedEvent(state = DoseState.TAKEN)
        orchestrator.onInitialAlarm(EVENT_ID, NOW)
        assertTrue(scheduler.retries.isEmpty())
        assertTrue(notifier.shownBatches.isEmpty())
        assertEquals(0, planner.ensureNextCalls)
    }

    @Test
    fun retryChainReachesFinalizeAtFortyMinutes() = runTest {
        seedPendingEvent()
        orchestrator.onInitialAlarm(EVENT_ID, NOW)
        orchestrator.onRetry(EVENT_ID, 1, NOW.plus(10, ChronoUnit.MINUTES))
        orchestrator.onRetry(EVENT_ID, 2, NOW.plus(20, ChronoUnit.MINUTES))
        orchestrator.onRetry(EVENT_ID, 3, NOW.plus(30, ChronoUnit.MINUTES))
        assertEquals(listOf(10L, 20L, 30L), scheduler.retryMinutesAfterNow)
        assertEquals(listOf(40L), scheduler.finalizeMinutesAfterNow)
        assertEquals(3, repository.event(EVENT_ID)!!.reminderCount)
    }

    @Test
    fun finalizeMarksEventUnconfirmed() = runTest {
        seedPendingEvent()
        orchestrator.onFinalize(EVENT_ID, NOW.plus(40, ChronoUnit.MINUTES))
        assertEquals(DoseState.UNCONFIRMED, repository.event(EVENT_ID)!!.state)
    }

    @Test
    fun actionBeforeRetrySkipsAlertAndSchedulesNothing() = runTest {
        seedEvent(state = DoseState.TAKEN)
        orchestrator.onRetry(EVENT_ID, 1, NOW.plus(10, ChronoUnit.MINUTES))
        assertTrue(notifier.shownBatches.isEmpty())
        assertTrue(scheduler.retries.isEmpty())
        assertTrue(scheduler.finalizes.isEmpty())
    }

    @Test
    fun duplicateRetryBroadcastIsIgnored() = runTest {
        seedPendingEvent()
        orchestrator.onRetry(EVENT_ID, 1, NOW.plus(10, ChronoUnit.MINUTES))
        orchestrator.onRetry(EVENT_ID, 1, NOW.plus(10, ChronoUnit.MINUTES))
        assertEquals(1, scheduler.retries.size)
        assertEquals(setOf(2), scheduler.retries.keys.map { it.second }.toSet())
    }

    @Test
    fun repeatedSnoozeDoesNotAccumulateAlarms() = runTest {
        seedPendingEvent()
        snoozeAndOrchestrate(NOW)
        snoozeAndOrchestrate(NOW.plus(5, ChronoUnit.MINUTES))
        assertEquals(1, scheduler.retries.size)
        assertTrue(scheduler.cancelled.isNotEmpty())
    }

    @Test
    fun twoMedicinesInSameMinuteShareOneBatchMinute() = runTest {
        seedPendingEvent()
        seedEvent(id = EVENT_ID + 1, scheduledAt = NOW, state = DoseState.PENDING)
        orchestrator.onInitialAlarm(EVENT_ID, NOW)
        orchestrator.onInitialAlarm(EVENT_ID + 1, NOW)
        assertEquals(listOf(NOW.epochMinute(), NOW.epochMinute()), notifier.shownBatches)
    }

    @Test
    fun pastOccurrencesCollapseIntoOneUnconfirmedEvent() = runTest {
        val scheduleId = repository.nextEventId()
        repository.seedSchedule(MedicationSchedule(
            id = scheduleId,
            medicationId = MEDICATION_ID,
            dose = MilliUnits(1_000),
            rule = ScheduleRule.Daily(listOf(LocalTime.of(9, 0))),
            startDate = LocalDate.parse("2026-08-10"),
            endDate = null
        ))
        orchestrator.rescheduleAll(ReminderRescheduleReason.TIME_CHANGED)
        val unconfirmed = repository.events.values.filter { it.state == DoseState.UNCONFIRMED }
        assertEquals(1, unconfirmed.size)
        val newScheduleEvents = repository.events.values.filter { it.scheduleId == scheduleId }
        assertEquals(1, newScheduleEvents.count { it.state == DoseState.PENDING })
    }

    private suspend fun snoozeAndOrchestrate(at: Instant) {
        repository.recordDoseAction(EVENT_ID, DoseAction.SNOOZE, at)
        orchestrator.onSnoozed(EVENT_ID, at)
    }

    private fun seedPendingEvent(): DoseEvent = seedEvent(state = DoseState.PENDING)

    private fun seedEvent(id: Long = EVENT_ID, scheduledAt: Instant = NOW, state: DoseState): DoseEvent {
        val event = DoseEvent(
            id = id,
            medicationId = MEDICATION_ID,
            scheduleId = SCHEDULE_ID,
            dose = MilliUnits(1_000),
            scheduledAt = scheduledAt,
            state = state,
            actedAt = null,
            reminderCount = 0
        )
        repository.seedEvent(event)
        return event
    }

    private fun Instant.epochMinute(): Long = toEpochMilli() / 60_000L

    private companion object {
        val NOW: Instant = Instant.parse("2026-08-14T01:00:00Z")
        val ZONE: ZoneId = ZoneId.of("Asia/Shanghai")
        val FIXED_ZONE: ZoneProvider = object : ZoneProvider {
            override fun current(): ZoneId = ZONE
        }
        const val MEDICATION_ID = 100L
        const val SCHEDULE_ID = 200L
        const val EVENT_ID = 300L
    }
}

class FakeReminderScheduler(var now: Instant) : ReminderScheduler {
    val scheduledDoses = mutableListOf<DoseEvent>()
    val retries = mutableMapOf<Pair<Long, Int>, Instant>()
    val finalizes = mutableMapOf<Long, Instant>()
    val cancelled = mutableListOf<Long>()
    val lowStockAlarms = mutableMapOf<Long, Instant>()
    val cancelledLowStock = mutableListOf<Long>()

    override fun scheduleDose(event: DoseEvent) {
        scheduledDoses.add(event)
    }

    override fun scheduleRetry(eventId: Long, attempt: Int, at: Instant) {
        retries[eventId to attempt] = at
    }

    override fun scheduleFinalize(eventId: Long, at: Instant) {
        finalizes[eventId] = at
    }

    override fun cancelEvent(eventId: Long) {
        retries.keys.removeAll { it.first == eventId }
        finalizes.remove(eventId)
        cancelled.add(eventId)
    }

    override fun scheduleLowStock(medicationId: Long, at: Instant) {
        lowStockAlarms[medicationId] = at
    }

    override fun cancelLowStock(medicationId: Long) {
        lowStockAlarms.remove(medicationId)
        cancelledLowStock.add(medicationId)
    }

    val retryMinutesAfterNow: List<Long>
        get() = retries.values.map { ChronoUnit.MINUTES.between(now, it) }.sorted()

    val finalizeMinutesAfterNow: List<Long>
        get() = finalizes.values.map { ChronoUnit.MINUTES.between(now, it) }.sorted()
}

class FakeReminderNotifier : ReminderNotifier {
    val shownBatches = mutableListOf<Long>()
    val dismissedBatches = mutableListOf<Long>()

    override fun showDoseBatch(epochMinute: Long) {
        shownBatches.add(epochMinute)
    }

    override fun dismissDoseBatch(epochMinute: Long) {
        dismissedBatches.add(epochMinute)
    }
}

class FakeEventPlanner(private val delegate: EventPlanner? = null) : EventPlanner {
    var ensureNextCalls = 0

    override suspend fun ensureNextEvent(scheduleId: Long, afterExclusive: Instant, zoneId: ZoneId): DoseEvent? {
        ensureNextCalls++
        return delegate?.ensureNextEvent(scheduleId, afterExclusive, zoneId)
    }
}
