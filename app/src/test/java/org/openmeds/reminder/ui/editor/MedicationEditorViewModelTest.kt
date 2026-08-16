package org.openmeds.reminder.ui.editor

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.openmeds.reminder.FakeMedicationRepository
import org.openmeds.reminder.reminder.ReminderRescheduleReason
import org.openmeds.reminder.reminder.ScheduleRescheduler

@OptIn(ExperimentalCoroutinesApi::class)
class MedicationEditorViewModelTest {
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
    fun everyNDaysRequiresPositiveIntervalAndAtLeastOneTime() {
        val errors = invalidEveryNDaysDraft().validate()
        assertEquals("至少为 1 天", errors[EditorField.INTERVAL_DAYS])
        assertEquals("至少添加一个提醒时间", errors[EditorField.TIMES])
    }

    @Test
    fun weeklyRequiresAtLeastOneDay() {
        val errors = MedicationDraft(ruleType = EditorRuleType.WEEKLY, times = listOf(LocalTime.of(9, 0)))
            .validate()
        assertEquals("至少选择一天", errors[EditorField.WEEKDAYS])
    }

    @Test
    fun summaryUsesPlainChinese() {
        assertEquals(
            "从 8 月 15 日开始，每 3 天 09:00 服用 1 片",
            ScheduleSummaryFormatter.format(validDraft())
        )
    }

    @Test
    fun saveCreatesMedicationAndReschedules() = runTest(dispatcher.scheduler) {
        val repository = FakeMedicationRepository()
        val rescheduler = FakeScheduleRescheduler()
        val viewModel = MedicationEditorViewModel(repository, rescheduler, null)

        viewModel.update {
            it.copy(
                name = "降压药",
                unit = "片",
                stockText = "30",
                doseText = "1",
                times = listOf(LocalTime.of(9, 0))
            )
        }
        viewModel.save()

        assertEquals(1, repository.medications().size)
        assertEquals(1, rescheduler.calls)
        assertTrue(viewModel.saved.value)
    }

    private fun invalidEveryNDaysDraft(): MedicationDraft = MedicationDraft(
        name = "药",
        unit = "片",
        stockText = "30",
        doseText = "1",
        ruleType = EditorRuleType.EVERY_N_DAYS,
        intervalDays = "0",
        startDate = LocalDate.of(2026, 8, 15)
    )

    private fun validDraft(): MedicationDraft = MedicationDraft(
        name = "药",
        unit = "片",
        stockText = "30",
        doseText = "1",
        ruleType = EditorRuleType.EVERY_N_DAYS,
        times = listOf(LocalTime.of(9, 0)),
        weekdays = setOf(DayOfWeek.MONDAY),
        intervalDays = "3",
        startDate = LocalDate.of(2026, 8, 15)
    )
}

class FakeScheduleRescheduler : ScheduleRescheduler {
    var calls = 0

    override suspend fun rescheduleAll(reason: ReminderRescheduleReason) {
        calls++
    }
}
