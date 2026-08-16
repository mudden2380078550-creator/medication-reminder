package org.openmeds.reminder.ui.history

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
import org.openmeds.reminder.domain.model.DoseAction
import org.openmeds.reminder.domain.model.DoseEvent
import org.openmeds.reminder.domain.model.DoseState
import org.openmeds.reminder.domain.model.Medication
import org.openmeds.reminder.domain.model.MilliUnits
import org.openmeds.reminder.domain.model.SignedMilliUnits
import org.openmeds.reminder.reminder.ZoneProvider
import org.openmeds.reminder.ui.reminder.FakeDoseActionCoordinator

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {
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
    fun correctingUnconfirmedToTakenConsumesExactlyOnce() = runTest(dispatcher.scheduler) {
        val repository = FakeMedicationRepository()
        repository.seedMedication(Medication(1L, "药A", "片", SignedMilliUnits(10_000), null, true))
        repository.seedEvent(
            DoseEvent(11L, 1L, 1L, MilliUnits(1_000), Instant.parse("2026-08-14T01:00:00Z"), DoseState.UNCONFIRMED, null, 0)
        )
        val viewModel = HistoryViewModel(repository, FakeDoseActionCoordinator(), FIXED_ZONE)

        viewModel.correct(11L, DoseAction.TAKEN)
        viewModel.correct(11L, DoseAction.TAKEN)

        assertEquals(1, repository.consumeCalls)
    }

    @Test
    fun historyLoadsEventsSortedNewestFirst() = runTest(dispatcher.scheduler) {
        val repository = FakeMedicationRepository()
        repository.seedMedication(Medication(1L, "药A", "片", SignedMilliUnits(10_000), null, true))
        repository.seedEvent(
            DoseEvent(11L, 1L, 1L, MilliUnits(1_000), Instant.parse("2026-08-14T01:00:00Z"), DoseState.TAKEN, null, 0)
        )
        val viewModel = HistoryViewModel(repository, FakeDoseActionCoordinator(), FIXED_ZONE)

        assertEquals(1, viewModel.state.value.items.size)
        assertEquals("药A", viewModel.state.value.items.single().medicationName)
    }

    private companion object {
        val FIXED_ZONE: ZoneProvider = object : ZoneProvider {
            override fun current(): ZoneId = ZoneId.of("Asia/Shanghai")
        }
    }
}
