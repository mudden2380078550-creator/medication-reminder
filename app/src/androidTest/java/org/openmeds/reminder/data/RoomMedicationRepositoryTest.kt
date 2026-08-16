package org.openmeds.reminder.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.openmeds.reminder.data.db.AppDatabase
import org.openmeds.reminder.data.repository.MedicationRepository
import org.openmeds.reminder.data.repository.RoomMedicationRepository
import org.openmeds.reminder.domain.model.DoseAction
import org.openmeds.reminder.domain.model.DoseActionResult
import org.openmeds.reminder.domain.model.MedicationPlanInput
import org.openmeds.reminder.domain.model.MilliUnits
import org.openmeds.reminder.domain.model.ScheduleRule
import org.openmeds.reminder.domain.model.SignedMilliUnits

@RunWith(AndroidJUnit4::class)
class RoomMedicationRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: MedicationRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomMedicationRepository(
            database = database,
            medicationDao = database.medicationDao(),
            scheduleDao = database.scheduleDao(),
            doseEventDao = database.doseEventDao(),
            stockTransactionDao = database.stockTransactionDao()
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun repeatedTakenActionConsumesStockOnce() = runTest {
        val eventId = repository.insertFixtureEvent(stock = MilliUnits(10_000), dose = MilliUnits(1_000))
        assertEquals(DoseActionResult.Applied, repository.recordDoseAction(eventId, DoseAction.TAKEN, NOW))
        assertEquals(DoseActionResult.AlreadyHandled, repository.recordDoseAction(eventId, DoseAction.TAKEN, NOW))
        assertEquals(9_000L, repository.medication(eventId)!!.stock.value)
        assertEquals(1, repository.stockTransactions(eventId).size)
    }

    @Test
    fun skippedDoseDoesNotConsumeStock() = runTest {
        val eventId = repository.insertFixtureEvent(stock = MilliUnits(10_000), dose = MilliUnits(1_000))
        assertEquals(DoseActionResult.Applied, repository.recordDoseAction(eventId, DoseAction.SKIPPED, NOW))
        assertEquals(10_000L, repository.medication(eventId)!!.stock.value)
        assertTrue(repository.stockTransactions(eventId).isEmpty())
    }

    @Test
    fun snoozeDoesNotConsumeStockAndUpdatesState() = runTest {
        val eventId = repository.insertFixtureEvent(stock = MilliUnits(10_000), dose = MilliUnits(1_000))
        assertEquals(DoseActionResult.Applied, repository.recordDoseAction(eventId, DoseAction.SNOOZE, NOW))
        assertEquals(10_000L, repository.medication(eventId)!!.stock.value)
        assertTrue(repository.stockTransactions(eventId).isEmpty())
        assertEquals("SNOOZED", repository.event(eventId)!!.state.name)
    }

    @Test
    fun unconfirmedDoesNotConsumeStock() = runTest {
        val eventId = repository.insertFixtureEvent(stock = MilliUnits(10_000), dose = MilliUnits(1_000))
        repository.markUnconfirmedIfActionable(eventId, NOW)
        assertEquals("UNCONFIRMED", repository.event(eventId)!!.state.name)
        assertEquals(10_000L, repository.medication(eventId)!!.stock.value)
        assertTrue(repository.stockTransactions(eventId).isEmpty())
    }

    @Test
    fun negativeStockIsPreserved() = runTest {
        val eventId = repository.insertFixtureEvent(stock = MilliUnits(1_000), dose = MilliUnits(5_000))
        repository.recordDoseAction(eventId, DoseAction.TAKEN, NOW)
        assertEquals(-4_000L, repository.medication(eventId)!!.stock.value)
    }

    @Test
    fun unknownEventIsNotFound() = runTest {
        assertEquals(DoseActionResult.NotFound, repository.recordDoseAction(999L, DoseAction.TAKEN, NOW))
    }

    @Test
    fun scheduleReplacementIsAtomic() = runTest {
        val medicationId = repository.createMedication(planInput("First", MilliUnits(500)))
        assertEquals(1, repository.schedules().size)

        repository.updateMedication(medicationId, planInput("Second", MilliUnits(700)))

        val after = repository.schedules()
        assertEquals(1, after.size)
        assertEquals(700L, after.single().dose.value)
        assertEquals("Second", repository.medicationById(medicationId)!!.name)
    }

    @Test
    fun deactivatingPreservesHistory() = runTest {
        val eventId = repository.insertFixtureEvent(stock = MilliUnits(10_000), dose = MilliUnits(1_000))
        repository.recordDoseAction(eventId, DoseAction.TAKEN, NOW)
        val medication = repository.medication(eventId)!!

        repository.deactivate(medication.id)

        val stored = repository.medicationById(medication.id)!!
        assertFalse(stored.isActive)
        assertEquals(9_000L, stored.stock.value)
        assertEquals(1, repository.stockTransactions(eventId).size)
    }

    private fun planInput(name: String, dose: MilliUnits) = MedicationPlanInput(
        name = name,
        unit = "tablet",
        stock = SignedMilliUnits(1_000),
        note = null,
        dose = dose,
        rule = ScheduleRule.Daily(listOf(LocalTime.of(9, 0))),
        startDate = LocalDate.parse("2026-08-14"),
        endDate = null
    )

    private companion object {
        val NOW: Instant = Instant.parse("2026-08-14T01:00:00Z")
    }
}
