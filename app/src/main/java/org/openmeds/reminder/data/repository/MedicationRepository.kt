package org.openmeds.reminder.data.repository

import kotlinx.coroutines.flow.Flow
import org.openmeds.reminder.domain.model.DoseAction
import org.openmeds.reminder.domain.model.DoseActionResult
import org.openmeds.reminder.domain.model.DoseEvent
import org.openmeds.reminder.domain.model.Medication
import org.openmeds.reminder.domain.model.MedicationPlanInput
import org.openmeds.reminder.domain.model.MedicationSchedule
import org.openmeds.reminder.domain.model.MilliUnits
import org.openmeds.reminder.domain.model.SignedMilliUnits
import org.openmeds.reminder.domain.model.StockTransaction
import java.time.Instant

interface MedicationRepository {
    fun observeHome(): Flow<HomeData>

    suspend fun createMedication(input: MedicationPlanInput): Long

    suspend fun updateMedication(id: Long, input: MedicationPlanInput)

    suspend fun deactivate(medicationId: Long)

    suspend fun addStock(medicationId: Long, amount: SignedMilliUnits, note: String?)

    suspend fun correctStock(medicationId: Long, amount: SignedMilliUnits, note: String)

    suspend fun recordDoseAction(eventId: Long, action: DoseAction, actedAt: Instant): DoseActionResult

    suspend fun actionableEventsForMinute(epochMinute: Long): List<DoseEvent>

    suspend fun schedule(id: Long): MedicationSchedule?

    suspend fun event(id: Long): DoseEvent?

    suspend fun pendingEvents(): List<DoseEvent>

    suspend fun allEvents(): List<DoseEvent>

    suspend fun eventsForMedicationBetween(medicationId: Long, from: Instant, to: Instant): List<DoseEvent>

    suspend fun insertDoseEventIfAbsent(scheduleId: Long, scheduledAt: Instant): DoseEvent?

    suspend fun markUnconfirmedIfActionable(eventId: Long, at: Instant)

    suspend fun setReminderCount(eventId: Long, count: Int)

    suspend fun medication(eventId: Long): Medication?

    suspend fun medicationById(medicationId: Long): Medication?

    suspend fun medications(): List<Medication>

    suspend fun schedules(): List<MedicationSchedule>

    suspend fun stockTransactions(eventId: Long): List<StockTransaction>

    suspend fun stockTransactionsForMedication(medicationId: Long): List<StockTransaction>

    suspend fun insertFixtureEvent(stock: MilliUnits, dose: MilliUnits): Long

    suspend fun replaceAll(
        medications: List<Medication>,
        schedules: List<MedicationSchedule>,
        events: List<DoseEvent>,
        transactions: List<StockTransaction>
    )
}
