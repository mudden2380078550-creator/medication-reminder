package org.openmeds.reminder.reminder

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.openmeds.reminder.FakeMedicationRepository
import org.openmeds.reminder.domain.inventory.InventoryForecaster
import org.openmeds.reminder.domain.model.Medication
import org.openmeds.reminder.domain.model.MedicationSchedule
import org.openmeds.reminder.domain.model.MilliUnits
import org.openmeds.reminder.domain.model.ScheduleRule
import org.openmeds.reminder.domain.model.SignedMilliUnits
import org.openmeds.reminder.domain.schedule.ScheduleEngine
import org.openmeds.reminder.ui.settings.FakeReminderPreferences

@OptIn(ExperimentalCoroutinesApi::class)
class LowStockPlannerTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun sevenDaysOfStockSchedulesDailyAlarmAtConfiguredTime() = runTest(dispatcher.scheduler) {
        val repository = FakeMedicationRepository()
        val scheduler = FakeReminderScheduler(NOW)
        emitForecast(repository, daysRemaining = 7)
        val planner = LowStockPlanner(
            repository = repository,
            forecaster = InventoryForecaster(ScheduleEngine()),
            scheduler = scheduler,
            preferences = FakeReminderPreferences(),
            clock = Clock.fixed(NOW, ZONE)
        )

        planner.reconcile(NOW, ZONE)

        assertEquals(LocalTime.of(9, 0), scheduler.lowStockAlarms.values.single().atZone(ZONE).toLocalTime())
    }

    @Test
    fun stockAboveSevenDaysCancelsAlarm() = runTest(dispatcher.scheduler) {
        val repository = FakeMedicationRepository()
        val scheduler = FakeReminderScheduler(NOW)
        emitForecast(repository, daysRemaining = 20)
        val planner = LowStockPlanner(
            repository = repository,
            forecaster = InventoryForecaster(ScheduleEngine()),
            scheduler = scheduler,
            preferences = FakeReminderPreferences(),
            clock = Clock.fixed(NOW, ZONE)
        )

        planner.reconcile(NOW, ZONE)

        assertEquals(listOf(MEDICATION_ID), scheduler.cancelledLowStock)
    }

    @Test
    fun zeroStockSchedulesAlarm() = runTest(dispatcher.scheduler) {
        val repository = FakeMedicationRepository()
        val scheduler = FakeReminderScheduler(NOW)
        emitForecast(repository, daysRemaining = 0)
        val planner = LowStockPlanner(
            repository = repository,
            forecaster = InventoryForecaster(ScheduleEngine()),
            scheduler = scheduler,
            preferences = FakeReminderPreferences(),
            clock = Clock.fixed(NOW, ZONE)
        )

        planner.reconcile(NOW, ZONE)

        assertEquals(1, scheduler.lowStockAlarms.size)
    }

    private fun emitForecast(repository: FakeMedicationRepository, daysRemaining: Long) {
        repository.seedMedication(
            Medication(MEDICATION_ID, "降压药", "片", SignedMilliUnits(daysRemaining * 1_000), null, true)
        )
        repository.seedSchedule(
            MedicationSchedule(
                id = SCHEDULE_ID,
                medicationId = MEDICATION_ID,
                dose = MilliUnits(1_000),
                rule = ScheduleRule.Daily(listOf(LocalTime.of(9, 0))),
                startDate = LocalDate.parse("2026-08-14"),
                endDate = null
            )
        )
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-08-14T00:00:00Z")
        val ZONE: ZoneId = ZoneId.of("Asia/Shanghai")
        const val MEDICATION_ID = 100L
        const val SCHEDULE_ID = 200L
    }
}
