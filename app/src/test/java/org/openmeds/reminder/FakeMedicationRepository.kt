package org.openmeds.reminder

import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.openmeds.reminder.data.repository.HomeData
import org.openmeds.reminder.data.repository.MedicationRepository
import org.openmeds.reminder.domain.model.DoseAction
import org.openmeds.reminder.domain.model.DoseActionResult
import org.openmeds.reminder.domain.model.DoseEvent
import org.openmeds.reminder.domain.model.DoseState
import org.openmeds.reminder.domain.model.Medication
import org.openmeds.reminder.domain.model.MedicationPlanInput
import org.openmeds.reminder.domain.model.MedicationSchedule
import org.openmeds.reminder.domain.model.MilliUnits
import org.openmeds.reminder.domain.model.SignedMilliUnits
import org.openmeds.reminder.domain.model.StockTransaction
import org.openmeds.reminder.domain.model.StockTransactionReason

class FakeMedicationRepository : MedicationRepository {
    val medications = mutableMapOf<Long, Medication>()
    val schedules = mutableMapOf<Long, MedicationSchedule>()
    val events = mutableMapOf<Long, DoseEvent>()
    val stockTransactions = mutableMapOf<Long, MutableList<StockTransaction>>()
    private val homeFlow = MutableStateFlow(HomeData(emptyList(), emptyList(), null))
    private var nextId = 1L

    var consumeCalls = 0
        private set

    fun emitHome(data: HomeData) {
        homeFlow.value = data
    }

    fun seedMedication(medication: Medication) {
        medications[medication.id] = medication
    }

    fun seedSchedule(schedule: MedicationSchedule) {
        schedules[schedule.id] = schedule
    }

    fun seedEvent(event: DoseEvent) {
        events[event.id] = event
    }

    fun nextMedicationId(): Long = nextId++

    fun nextEventId(): Long = nextId++

    override fun observeHome(): Flow<HomeData> = homeFlow

    override suspend fun createMedication(input: MedicationPlanInput): Long {
        val medicationId = nextId++
        medications[medicationId] = Medication(
            id = medicationId,
            name = input.name,
            unit = input.unit,
            stock = input.stock,
            note = input.note,
            isActive = true
        )
        val scheduleId = nextId++
        schedules[scheduleId] = MedicationSchedule(
            id = scheduleId,
            medicationId = medicationId,
            dose = input.dose,
            rule = input.rule,
            startDate = input.startDate,
            endDate = input.endDate
        )
        return medicationId
    }

    override suspend fun updateMedication(id: Long, input: MedicationPlanInput) {
        val medication = medications[id] ?: return
        medications[id] = medication.copy(name = input.name, unit = input.unit, note = input.note)
        val old = schedules.values.firstOrNull { it.medicationId == id } ?: return
        schedules.remove(old.id)
        val scheduleId = nextId++
        schedules[scheduleId] = MedicationSchedule(
            id = scheduleId,
            medicationId = id,
            dose = input.dose,
            rule = input.rule,
            startDate = input.startDate,
            endDate = input.endDate
        )
    }

    override suspend fun deactivate(medicationId: Long) {
        val medication = medications[medicationId] ?: return
        medications[medicationId] = medication.copy(isActive = false)
    }

    override suspend fun addStock(medicationId: Long, amount: SignedMilliUnits, note: String?) {
        val medication = medications[medicationId] ?: return
        medications[medicationId] = medication.copy(stock = SignedMilliUnits(medication.stock.value + amount.value))
        stockTransactions.getOrPut(medicationId) { mutableListOf() }.add(
            StockTransaction(nextId++, medicationId, null, amount, Instant.EPOCH, StockTransactionReason.RESTOCK)
        )
    }

    override suspend fun correctStock(medicationId: Long, amount: SignedMilliUnits, note: String) {
        val medication = medications[medicationId] ?: return
        medications[medicationId] = medication.copy(stock = SignedMilliUnits(medication.stock.value + amount.value))
        stockTransactions.getOrPut(medicationId) { mutableListOf() }.add(
            StockTransaction(nextId++, medicationId, null, amount, Instant.EPOCH, StockTransactionReason.CORRECTION)
        )
    }

    override suspend fun recordDoseAction(eventId: Long, action: DoseAction, actedAt: Instant): DoseActionResult {
        val event = events[eventId] ?: return DoseActionResult.NotFound
        if (event.state == DoseState.TAKEN || event.state == DoseState.SKIPPED) return DoseActionResult.AlreadyHandled
        if (action == DoseAction.TAKEN) {
            consumeCalls++
            val medication = medications[event.medicationId]!!
            medications[event.medicationId] = medication.copy(stock = SignedMilliUnits(medication.stock.value - event.dose.value))
            stockTransactions.getOrPut(event.medicationId) { mutableListOf() }.add(StockTransaction(nextId++, event.medicationId, eventId, SignedMilliUnits(-event.dose.value), actedAt, StockTransactionReason.CONSUME))
            events[eventId] = event.copy(state = DoseState.TAKEN, actedAt = actedAt)
        } else if (action == DoseAction.SKIPPED) {
            events[eventId] = event.copy(state = DoseState.SKIPPED, actedAt = actedAt)
        } else {
            events[eventId] = event.copy(state = DoseState.SNOOZED, actedAt = actedAt)
        }
        return DoseActionResult.Applied
    }

    override suspend fun actionableEventsForMinute(epochMinute: Long): List<DoseEvent> {
        val start = epochMinute * 60_000L
        return events.values.filter {
            it.scheduledAt.toEpochMilli() in start until start + 60_000L &&
                (it.state == DoseState.PENDING || it.state == DoseState.SNOOZED)
        }.sortedBy { it.scheduledAt }
    }

    override suspend fun schedule(id: Long): MedicationSchedule? = schedules[id]

    override suspend fun event(id: Long): DoseEvent? = events[id]

    override suspend fun pendingEvents(): List<DoseEvent> =
        events.values.filter { it.state == DoseState.PENDING || it.state == DoseState.SNOOZED }
            .sortedBy { it.scheduledAt }

    override suspend fun allEvents(): List<DoseEvent> =
        events.values.sortedByDescending { it.scheduledAt }

    override suspend fun eventsForMedicationBetween(medicationId: Long, from: Instant, to: Instant): List<DoseEvent> =
        events.values.filter {
            it.medicationId == medicationId && it.scheduledAt.toEpochMilli() >= from.toEpochMilli() &&
                it.scheduledAt.toEpochMilli() < to.toEpochMilli()
        }

    override suspend fun insertDoseEventIfAbsent(scheduleId: Long, scheduledAt: Instant): DoseEvent? {
        val schedule = schedules[scheduleId] ?: return null
        val existing = events.values.firstOrNull { it.scheduleId == scheduleId && it.scheduledAt == scheduledAt }
        if (existing != null) return existing
        val id = nextId++
        val event = DoseEvent(id, schedule.medicationId, scheduleId, schedule.dose, scheduledAt, DoseState.PENDING, null, 0)
        events[id] = event
        return event
    }

    override suspend fun markUnconfirmedIfActionable(eventId: Long, at: Instant) {
        val event = events[eventId] ?: return
        if (event.state == DoseState.PENDING || event.state == DoseState.SNOOZED) {
            events[eventId] = event.copy(state = DoseState.UNCONFIRMED, actedAt = at)
        }
    }

    override suspend fun setReminderCount(eventId: Long, count: Int) {
        val event = events[eventId] ?: return
        events[eventId] = event.copy(reminderCount = count)
    }

    override suspend fun medication(eventId: Long): Medication? =
        events[eventId]?.let { medications[it.medicationId] }

    override suspend fun medicationById(medicationId: Long): Medication? = medications[medicationId]

    override suspend fun medications(): List<Medication> = medications.values.toList()

    override suspend fun schedules(): List<MedicationSchedule> = schedules.values.toList()

    override suspend fun stockTransactions(eventId: Long): List<StockTransaction> =
        events[eventId]?.let { stockTransactions[it.medicationId].orEmpty() } ?: emptyList()

    override suspend fun stockTransactionsForMedication(medicationId: Long): List<StockTransaction> =
        stockTransactions[medicationId].orEmpty()

    override suspend fun insertFixtureEvent(stock: MilliUnits, dose: MilliUnits): Long =
        throw UnsupportedOperationException("Not used in JVM tests")

    override suspend fun replaceAll(
        medications: List<Medication>,
        schedules: List<MedicationSchedule>,
        events: List<DoseEvent>,
        transactions: List<StockTransaction>
    ) {
        this.medications.clear()
        this.schedules.clear()
        this.events.clear()
        this.stockTransactions.clear()
        medications.forEach { this.medications[it.id] = it }
        schedules.forEach { this.schedules[it.id] = it }
        events.forEach { this.events[it.id] = it }
    }
}
