package org.openmeds.reminder.data.repository

import androidx.room.withTransaction
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import org.openmeds.reminder.data.db.AppDatabase
import org.openmeds.reminder.data.db.DbConverters
import org.openmeds.reminder.data.db.DoseEventDao
import org.openmeds.reminder.data.db.DoseEventEntity
import org.openmeds.reminder.data.db.MedicationDao
import org.openmeds.reminder.data.db.MedicationEntity
import org.openmeds.reminder.data.db.ScheduleDao
import org.openmeds.reminder.data.db.ScheduleEntity
import org.openmeds.reminder.data.db.ScheduleRuleCode
import org.openmeds.reminder.data.db.ScheduleTimeEntity
import org.openmeds.reminder.data.db.StockTransactionDao
import org.openmeds.reminder.data.db.StockTransactionFactory
import org.openmeds.reminder.data.db.toDomain
import org.openmeds.reminder.data.db.toMedicationEntity
import org.openmeds.reminder.data.db.toScheduleEntity
import org.openmeds.reminder.data.db.times
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

class RoomMedicationRepository(
    private val database: AppDatabase,
    private val medicationDao: MedicationDao,
    private val scheduleDao: ScheduleDao,
    private val doseEventDao: DoseEventDao,
    private val stockTransactionDao: StockTransactionDao,
    private val clock: Clock = Clock.systemDefaultZone()
) : MedicationRepository {

    override fun observeHome(): Flow<HomeData> = combine(
        medicationDao.observeActive(),
        scheduleDao.observeAllWithTimes(),
        doseEventDao.observeEarliestPending()
    ) { medications, schedules, nextEvent ->
        HomeData(
            medications = medications.map { it.toDomain() },
            schedules = schedules.map { it.toDomain() },
            nextDose = nextEvent?.toDomain()
        )
    }

    override suspend fun createMedication(input: MedicationPlanInput): Long = database.withTransaction {
        val medicationId = medicationDao.insert(input.toMedicationEntity())
        insertSchedules(medicationId, input)
        medicationId
    }

    override suspend fun updateMedication(id: Long, input: MedicationPlanInput) {
        database.withTransaction {
            val existing = medicationDao.byId(id) ?: return@withTransaction
            medicationDao.update(
                existing.copy(name = input.name, unit = input.unit, note = input.note)
            )
            scheduleDao.deleteByMedicationId(id)
            insertSchedules(id, input)
        }
    }

    override suspend fun deactivate(medicationId: Long) {
        medicationDao.deactivate(medicationId)
    }

    override suspend fun addStock(medicationId: Long, amount: SignedMilliUnits, note: String?) {
        database.withTransaction {
            medicationDao.adjustStock(medicationId, amount.value)
            stockTransactionDao.insert(
                StockTransactionFactory.restock(medicationId, amount, Instant.now(clock))
            )
        }
    }

    override suspend fun correctStock(medicationId: Long, amount: SignedMilliUnits, note: String) {
        database.withTransaction {
            medicationDao.adjustStock(medicationId, amount.value)
            stockTransactionDao.insert(
                StockTransactionFactory.correction(medicationId, amount, Instant.now(clock))
            )
        }
    }

    override suspend fun recordDoseAction(
        eventId: Long,
        action: DoseAction,
        actedAt: Instant
    ): DoseActionResult = database.withTransaction {
        val event = doseEventDao.byId(eventId) ?: return@withTransaction DoseActionResult.NotFound
        if (event.state in setOf(DoseState.TAKEN.name, DoseState.SKIPPED.name)) {
            return@withTransaction DoseActionResult.AlreadyHandled
        }
        when (action) {
            DoseAction.SNOOZE -> {
                doseEventDao.setSnoozed(eventId, actedAt.toEpochMilli())
                DoseActionResult.Applied
            }
            DoseAction.TAKEN -> {
                medicationDao.adjustStock(event.medicationId, -event.doseMilliUnits)
                stockTransactionDao.insert(StockTransactionFactory.consume(event, actedAt))
                doseEventDao.setTerminalState(eventId, DoseState.TAKEN.name, actedAt.toEpochMilli())
                DoseActionResult.Applied
            }
            DoseAction.SKIPPED -> {
                doseEventDao.setTerminalState(eventId, DoseState.SKIPPED.name, actedAt.toEpochMilli())
                DoseActionResult.Applied
            }
        }
    }

    override suspend fun actionableEventsForMinute(epochMinute: Long): List<DoseEvent> {
        val minuteStart = epochMinute * 60_000L
        return doseEventDao.actionableForMinute(minuteStart, minuteStart + 60_000L)
            .map { it.toDomain() }
    }

    override suspend fun schedule(id: Long): MedicationSchedule? = scheduleDao.byIdWithTimes(id)?.toDomain()

    override suspend fun event(id: Long): DoseEvent? = doseEventDao.byId(id)?.toDomain()

    override suspend fun pendingEvents(): List<DoseEvent> =
        doseEventDao.actionablePending().map { it.toDomain() }

    override suspend fun insertDoseEventIfAbsent(scheduleId: Long, scheduledAt: Instant): DoseEvent? =
        database.withTransaction {
            val existing = doseEventDao.byScheduleAndTime(scheduleId, scheduledAt.toEpochMilli())
            if (existing != null) {
                return@withTransaction existing.toDomain()
            }
            val schedule = scheduleDao.byIdWithTimes(scheduleId)?.toDomain()
                ?: return@withTransaction null
            val id = doseEventDao.insert(
                DoseEventEntity(
                    medicationId = schedule.medicationId,
                    scheduleId = scheduleId,
                    doseMilliUnits = schedule.dose.value,
                    scheduledAtEpochMilli = scheduledAt.toEpochMilli(),
                    state = DoseState.PENDING.name,
                    actedAtEpochMilli = null,
                    reminderCount = 0
                )
            )
            doseEventDao.byId(id)?.toDomain()
        }

    override suspend fun markUnconfirmedIfActionable(eventId: Long, at: Instant) {
        doseEventDao.markUnconfirmed(eventId, at.toEpochMilli())
    }

    override suspend fun medication(eventId: Long): Medication? {
        val event = doseEventDao.byId(eventId) ?: return null
        return medicationDao.byId(event.medicationId)?.toDomain()
    }

    override suspend fun medicationById(medicationId: Long): Medication? =
        medicationDao.byId(medicationId)?.toDomain()

    override suspend fun medications(): List<Medication> =
        medicationDao.all().map { it.toDomain() }

    override suspend fun schedules(): List<MedicationSchedule> =
        scheduleDao.allWithTimes().map { it.toDomain() }

    override suspend fun stockTransactions(eventId: Long): List<StockTransaction> {
        val event = doseEventDao.byId(eventId) ?: return emptyList()
        return stockTransactionDao.forMedication(event.medicationId).map { it.toDomain() }
    }

    override suspend fun stockTransactionsForMedication(medicationId: Long): List<StockTransaction> =
        stockTransactionDao.forMedication(medicationId).map { it.toDomain() }

    override suspend fun insertFixtureEvent(stock: MilliUnits, dose: MilliUnits): Long =
        database.withTransaction {
            val medicationId = medicationDao.insert(
                MedicationEntity(
                    name = "Fixture",
                    unit = "tablet",
                    stockMilliUnits = stock.value,
                    note = null,
                    isActive = true
                )
            )
            val scheduleId = scheduleDao.insert(
                ScheduleEntity(
                    medicationId = medicationId,
                    doseMilliUnits = dose.value,
                    ruleType = ScheduleRuleCode.DAILY,
                    weekdayMask = 0,
                    intervalDays = 0,
                    anchorEpochDay = 0,
                    startEpochDay = LocalDate.ofEpochDay(0).toEpochDay(),
                    endEpochDay = null
                )
            )
            doseEventDao.insert(
                DoseEventEntity(
                    medicationId = medicationId,
                    scheduleId = scheduleId,
                    doseMilliUnits = dose.value,
                    scheduledAtEpochMilli = FIXTURE_EPOCH_MILLI,
                    state = DoseState.PENDING.name,
                    actedAtEpochMilli = null,
                    reminderCount = 0
                )
            )
        }

    private suspend fun insertSchedules(medicationId: Long, input: MedicationPlanInput) {
        val scheduleId = scheduleDao.insert(input.toScheduleEntity(medicationId))
        input.rule.times().forEach { time ->
            scheduleDao.insertTime(
                ScheduleTimeEntity(scheduleId = scheduleId, minuteOfDay = DbConverters.minuteOfDay(time))
            )
        }
    }

    private companion object {
        const val FIXTURE_EPOCH_MILLI = 1_700_000_000_000L
    }
}
