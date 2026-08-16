package org.openmeds.reminder.ui.reminder

import java.time.Instant
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
import org.openmeds.reminder.domain.model.DoseEvent
import org.openmeds.reminder.domain.model.DoseState
import org.openmeds.reminder.domain.model.Medication
import org.openmeds.reminder.domain.model.MilliUnits
import org.openmeds.reminder.domain.model.SignedMilliUnits
import org.openmeds.reminder.reminder.ReminderActionCoordinator
import org.openmeds.reminder.reminder.ZoneProvider

@OptIn(ExperimentalCoroutinesApi::class)
class ReminderViewModelTest {
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
    fun sameMinuteMedicinesRequireSeparateConfirmation() = runTest(dispatcher.scheduler) {
        val repository = FakeMedicationRepository()
        repository.seedMedication(Medication(1L, "药A", "片", SignedMilliUnits(10_000), null, true))
        repository.seedMedication(Medication(2L, "药B", "片", SignedMilliUnits(10_000), null, true))
        repository.seedEvent(DoseEvent(11L, 1L, 1L, MilliUnits(1_000), SCHEDULED_AT, DoseState.PENDING, null, 0))
        repository.seedEvent(DoseEvent(12L, 2L, 2L, MilliUnits(1_000), SCHEDULED_AT, DoseState.PENDING, null, 0))
        val coordinator = FakeDoseActionCoordinator()
        val viewModel = ReminderViewModel(repository, coordinator, FIXED_ZONE)

        viewModel.load(EPOCH_MINUTE)

        assertEquals(2, viewModel.state.value.items.size)
        viewModel.take(11L)
        assertEquals(DoseState.TAKEN, viewModel.state.value.item(11L)!!.state)
        assertEquals(DoseState.PENDING, viewModel.state.value.item(12L)!!.state)
        assertEquals(listOf(11L), coordinator.handled)
    }

    @Test
    fun snoozeKeepsItemActionable() = runTest(dispatcher.scheduler) {
        val repository = FakeMedicationRepository()
        repository.seedMedication(Medication(1L, "药A", "片", SignedMilliUnits(10_000), null, true))
        repository.seedEvent(DoseEvent(11L, 1L, 1L, MilliUnits(1_000), SCHEDULED_AT, DoseState.PENDING, null, 0))
        val coordinator = FakeDoseActionCoordinator()
        val viewModel = ReminderViewModel(repository, coordinator, FIXED_ZONE)

        viewModel.load(EPOCH_MINUTE)
        viewModel.snooze(11L)

        assertEquals(DoseState.SNOOZED, viewModel.state.value.item(11L)!!.state)
        assertEquals(false, viewModel.state.value.allHandled)
        assertEquals(listOf(11L), coordinator.snoozed)
    }

    @Test
    fun skipMarksAllHandledWhenLastItem() = runTest(dispatcher.scheduler) {
        val repository = FakeMedicationRepository()
        repository.seedMedication(Medication(1L, "药A", "片", SignedMilliUnits(10_000), null, true))
        repository.seedEvent(DoseEvent(11L, 1L, 1L, MilliUnits(1_000), SCHEDULED_AT, DoseState.PENDING, null, 0))
        val coordinator = FakeDoseActionCoordinator()
        val viewModel = ReminderViewModel(repository, coordinator, FIXED_ZONE)

        viewModel.load(EPOCH_MINUTE)
        viewModel.skip(11L)

        assertEquals(DoseState.SKIPPED, viewModel.state.value.item(11L)!!.state)
        assertEquals(true, viewModel.state.value.allHandled)
    }

    private companion object {
        val SCHEDULED_AT: Instant = Instant.parse("2026-08-14T01:00:00Z")
        val EPOCH_MINUTE: Long = SCHEDULED_AT.toEpochMilli() / 60_000L
        val FIXED_ZONE: ZoneProvider = object : ZoneProvider {
            override fun current(): ZoneId = ZoneId.of("Asia/Shanghai")
        }
    }
}

class FakeDoseActionCoordinator : ReminderActionCoordinator {
    val handled = mutableListOf<Long>()
    val snoozed = mutableListOf<Long>()

    override suspend fun onHandled(eventId: Long) {
        handled.add(eventId)
    }

    override suspend fun onSnoozed(eventId: Long, actedAt: Instant) {
        snoozed.add(eventId)
    }
}
