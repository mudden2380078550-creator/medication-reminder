package org.openmeds.reminder.ui.home

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.openmeds.reminder.FakeMedicationRepository
import org.openmeds.reminder.data.repository.HomeData
import org.openmeds.reminder.domain.inventory.InventoryForecaster
import org.openmeds.reminder.domain.model.Medication
import org.openmeds.reminder.domain.model.MedicationSchedule
import org.openmeds.reminder.domain.model.MilliUnits
import org.openmeds.reminder.domain.model.ScheduleRule
import org.openmeds.reminder.domain.model.SignedMilliUnits
import org.openmeds.reminder.domain.schedule.ScheduleEngine
import org.openmeds.reminder.reminder.ReminderCapabilitySnapshot
import org.openmeds.reminder.reminder.ReminderCapabilitySource
import org.openmeds.reminder.reminder.ZoneProvider
import org.openmeds.reminder.ui.reminder.FakeDoseActionCoordinator

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
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
    fun lowStockCardsSortBeforeHealthyStock() = runTest(dispatcher.scheduler) {
        val repository = FakeMedicationRepository()
        val lowStock = Medication(1L, "降压药", "片", SignedMilliUnits(3_000), null, true)
        val healthy = Medication(2L, "维生素", "片", SignedMilliUnits(30_000), null, true)
        val schedule = MedicationSchedule(
            id = 1L,
            medicationId = 1L,
            dose = MilliUnits(1_000),
            rule = ScheduleRule.Daily(listOf(LocalTime.of(9, 0))),
            startDate = LocalDate.parse("2026-08-14"),
            endDate = null
        )
        repository.emitHome(HomeData(listOf(lowStock, healthy), listOf(schedule), null))
        val viewModel = HomeViewModel(
            repository = repository,
            forecaster = InventoryForecaster(ScheduleEngine()),
            capabilitySource = FakeCapabilitySource(),
            coordinator = FakeDoseActionCoordinator(),
            zoneProvider = FIXED_ZONE,
            clock = Clock.fixed(NOW, ZONE)
        )
        assertEquals(true, viewModel.state.value.medicationCards.first().isLowStock)
    }

    @Test
    fun deniedExactAlarmShowsPersistentWarning() = runTest(dispatcher.scheduler) {
        val repository = FakeMedicationRepository()
        val capability = FakeCapabilitySource()
        capability.emit(exactAlarms = false)
        val viewModel = HomeViewModel(
            repository = repository,
            forecaster = InventoryForecaster(ScheduleEngine()),
            capabilitySource = capability,
            coordinator = FakeDoseActionCoordinator(),
            zoneProvider = FIXED_ZONE,
            clock = Clock.fixed(NOW, ZONE)
        )
        assertEquals("提醒时间可能延迟", viewModel.state.value.capabilityWarnings.single().title)
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-08-14T00:00:00Z")
        val ZONE: ZoneId = ZoneId.of("Asia/Shanghai")
        val FIXED_ZONE: ZoneProvider = object : ZoneProvider {
            override fun current(): ZoneId = ZONE
        }
    }
}

class FakeCapabilitySource : ReminderCapabilitySource {
    override val snapshot = MutableStateFlow(ReminderCapabilitySnapshot(true, true, true, false))

    fun emit(
        exactAlarms: Boolean = true,
        notifications: Boolean = true,
        fullScreen: Boolean = true,
        batteryRestricted: Boolean = false
    ) {
        snapshot.value = ReminderCapabilitySnapshot(notifications, exactAlarms, fullScreen, batteryRestricted)
    }
}
