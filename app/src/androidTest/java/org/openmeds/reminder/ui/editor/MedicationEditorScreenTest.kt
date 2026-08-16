package org.openmeds.reminder.ui.editor

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.openmeds.reminder.data.repository.HomeData
import org.openmeds.reminder.data.repository.MedicationRepository
import org.openmeds.reminder.domain.model.DoseAction
import org.openmeds.reminder.domain.model.DoseActionResult
import org.openmeds.reminder.domain.model.DoseEvent
import org.openmeds.reminder.domain.model.Medication
import org.openmeds.reminder.domain.model.MedicationPlanInput
import org.openmeds.reminder.domain.model.MedicationSchedule
import org.openmeds.reminder.domain.model.MilliUnits
import org.openmeds.reminder.domain.model.SignedMilliUnits
import org.openmeds.reminder.domain.model.StockTransaction
import org.openmeds.reminder.reminder.ReminderRescheduleReason
import org.openmeds.reminder.reminder.ScheduleRescheduler
import org.openmeds.reminder.ui.theme.MedicationReminderTheme
import java.time.Instant

class MedicationEditorScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun addMedicationTitleIsVisible() {
        composeTestRule.setContent {
            MedicationReminderTheme {
                MedicationEditorScreen(
                    viewModel = MedicationEditorViewModel(ScreenFakeRepository(), ScreenFakeRescheduler(), null),
                    onDone = {}
                )
            }
        }
        composeTestRule.onNodeWithText("添加药品").assertIsDisplayed()
    }
}

private class ScreenFakeRescheduler : ScheduleRescheduler {
    override suspend fun rescheduleAll(reason: ReminderRescheduleReason) = Unit
}

private class ScreenFakeRepository : MedicationRepository {
    override fun observeHome(): Flow<HomeData> = flowOf(HomeData(emptyList(), emptyList(), null))
    override suspend fun createMedication(input: MedicationPlanInput): Long = 1L
    override suspend fun updateMedication(id: Long, input: MedicationPlanInput) = Unit
    override suspend fun deactivate(medicationId: Long) = Unit
    override suspend fun addStock(medicationId: Long, amount: SignedMilliUnits, note: String?) = Unit
    override suspend fun correctStock(medicationId: Long, amount: SignedMilliUnits, note: String) = Unit
    override suspend fun recordDoseAction(eventId: Long, action: DoseAction, actedAt: Instant): DoseActionResult = DoseActionResult.Applied
    override suspend fun actionableEventsForMinute(epochMinute: Long): List<DoseEvent> = emptyList()
    override suspend fun schedule(id: Long): MedicationSchedule? = null
    override suspend fun event(id: Long): DoseEvent? = null
    override suspend fun pendingEvents(): List<DoseEvent> = emptyList()
    override suspend fun allEvents(): List<DoseEvent> = emptyList()
    override suspend fun eventsForMedicationBetween(medicationId: Long, from: Instant, to: Instant): List<DoseEvent> = emptyList()
    override suspend fun insertDoseEventIfAbsent(scheduleId: Long, scheduledAt: Instant): DoseEvent? = null
    override suspend fun markUnconfirmedIfActionable(eventId: Long, at: Instant) = Unit
    override suspend fun setReminderCount(eventId: Long, count: Int) = Unit
    override suspend fun medication(eventId: Long): Medication? = null
    override suspend fun medicationById(medicationId: Long): Medication? = null
    override suspend fun medications(): List<Medication> = emptyList()
    override suspend fun schedules(): List<MedicationSchedule> = emptyList()
    override suspend fun stockTransactions(eventId: Long): List<StockTransaction> = emptyList()
    override suspend fun stockTransactionsForMedication(medicationId: Long): List<StockTransaction> = emptyList()
    override suspend fun insertFixtureEvent(stock: MilliUnits, dose: MilliUnits): Long = 1L
    override suspend fun replaceAll(
        medications: List<Medication>,
        schedules: List<MedicationSchedule>,
        events: List<DoseEvent>,
        transactions: List<StockTransaction>
    ) = Unit
}
