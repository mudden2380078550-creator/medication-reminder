package org.openmeds.reminder.domain.inventory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openmeds.reminder.domain.model.Medication
import org.openmeds.reminder.domain.model.MedicationSchedule
import org.openmeds.reminder.domain.model.MilliUnits
import org.openmeds.reminder.domain.model.ScheduleRule
import org.openmeds.reminder.domain.model.SignedMilliUnits
import org.openmeds.reminder.domain.schedule.ScheduleEngine
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class InventoryForecasterTest {
    private val forecaster = InventoryForecaster(ScheduleEngine())

    @Test
    fun sixDailyTabletsTriggersSevenDayReminder() {
        val result = forecaster.forecast(
            medication = fixtureMedication(stock = "6"),
            schedules = listOf(fixtureDaily(dose = "1", time = "09:00")),
            now = NOW,
            zoneId = ZONE
        )

        assertEquals(6L, result.daysRemaining)
        assertEquals(Instant.parse("2026-08-20T01:00:00Z"), result.depletesAt)
        assertTrue(result.nextDoseShortfall)
        assertTrue(forecaster.needsDailyLowStockReminder(result))
    }

    @Test
    fun courseEndingBeforeDepletionDoesNotNotify() {
        val result = forecaster.forecast(
            fixtureMedication("30"),
            listOf(fixtureThreeDayCourse()),
            NOW,
            ZONE
        )

        assertNull(result.depletesAt)
        assertEquals(27_000L, result.remainingAtCourseEnd?.value)
        assertFalse(result.nextDoseShortfall)
        assertNull(result.daysRemaining)
        assertFalse(forecaster.needsDailyLowStockReminder(result))
    }

    @Test
    fun halfTabletsAreForecastUsingExactMilliUnits() {
        val result = forecaster.forecast(
            fixtureMedication("1.5"),
            listOf(fixtureDaily(dose = "0.5", time = "09:00")),
            NOW,
            ZONE
        )

        assertEquals(3L, result.daysRemaining)
        assertEquals(Instant.parse("2026-08-17T01:00:00Z"), result.depletesAt)
        assertTrue(result.nextDoseShortfall)
    }

    @Test
    fun millilitersAreForecastUsingExactMilliUnits() {
        val result = forecaster.forecast(
            fixtureMedication(stock = "2.5", unit = "mL"),
            listOf(fixtureDaily(dose = "1.25", time = "09:00")),
            NOW,
            ZONE
        )

        assertEquals(2L, result.daysRemaining)
        assertEquals(Instant.parse("2026-08-16T01:00:00Z"), result.depletesAt)
        assertTrue(result.nextDoseShortfall)
    }

    @Test
    fun schedulesAtTheSameInstantEachConsumeOneDose() {
        val result = forecaster.forecast(
            fixtureMedication("2"),
            listOf(
                fixtureDaily(id = 1L, dose = "1", time = "09:00"),
                fixtureDaily(id = 2L, dose = "1", time = "09:00")
            ),
            NOW,
            ZONE
        )

        assertEquals(1L, result.daysRemaining)
        assertEquals(Instant.parse("2026-08-15T01:00:00Z"), result.depletesAt)
        assertTrue(result.nextDoseShortfall)
    }

    @Test
    fun zeroStockShortfallsAtTheNextDose() {
        val result = forecaster.forecast(
            fixtureMedication("0"),
            listOf(fixtureDaily(dose = "1", time = "09:00")),
            NOW,
            ZONE
        )

        assertEquals(0L, result.daysRemaining)
        assertEquals(Instant.parse("2026-08-14T01:00:00Z"), result.depletesAt)
        assertTrue(result.nextDoseShortfall)
        assertTrue(forecaster.needsDailyLowStockReminder(result))
    }

    @Test
    fun negativeStockShortfallsAtTheNextDose() {
        val result = forecaster.forecast(
            fixtureMedication("-0.5"),
            listOf(fixtureDaily(dose = "1", time = "09:00")),
            NOW,
            ZONE
        )

        assertEquals(0L, result.daysRemaining)
        assertEquals(Instant.parse("2026-08-14T01:00:00Z"), result.depletesAt)
        assertTrue(result.nextDoseShortfall)
        assertTrue(forecaster.needsDailyLowStockReminder(result))
    }

    @Test
    fun noScheduleDoesNotCreateALowStockReminder() {
        val result = forecaster.forecast(fixtureMedication("0"), emptyList(), NOW, ZONE)

        assertNull(result.depletesAt)
        assertNull(result.remainingAtCourseEnd)
        assertFalse(result.nextDoseShortfall)
        assertNull(result.daysRemaining)
        assertFalse(forecaster.needsDailyLowStockReminder(result))
    }

    @Test
    fun exactlySevenDaysTriggersSevenDayReminder() {
        val result = forecaster.forecast(
            fixtureMedication("7"),
            listOf(fixtureDaily(dose = "1", time = "09:00")),
            NOW,
            ZONE
        )

        assertEquals(7L, result.daysRemaining)
        assertTrue(forecaster.needsDailyLowStockReminder(result))
    }

    @Test
    fun eightDaysDoesNotTriggerSevenDayReminder() {
        val result = forecaster.forecast(
            fixtureMedication("8"),
            listOf(fixtureDaily(dose = "1", time = "09:00")),
            NOW,
            ZONE
        )

        assertEquals(8L, result.daysRemaining)
        assertFalse(forecaster.needsDailyLowStockReminder(result))
    }

    @Test
    fun openScheduleWithMoreThan366DaysOfStockHasNoInventedDepletionDate() {
        val result = forecaster.forecast(
            fixtureMedication("1000"),
            listOf(fixtureDaily(dose = "1", time = "09:00")),
            NOW,
            ZONE
        )

        assertEquals(366L, result.daysRemaining)
        assertNull(result.depletesAt)
        assertNull(result.remainingAtCourseEnd)
        assertFalse(result.nextDoseShortfall)
    }

    private fun fixtureMedication(stock: String, unit: String = "tablet") = Medication(
        id = MEDICATION_ID,
        name = "Example",
        unit = unit,
        stock = SignedMilliUnits.fromDecimal(stock),
        note = null,
        isActive = true
    )

    private fun fixtureDaily(
        id: Long = 1L,
        dose: String,
        time: String,
        startDate: LocalDate = LocalDate.parse("2026-08-14"),
        endDate: LocalDate? = null
    ) = MedicationSchedule(
        id = id,
        medicationId = MEDICATION_ID,
        dose = MilliUnits.fromDecimal(dose),
        rule = ScheduleRule.Daily(listOf(LocalTime.parse(time))),
        startDate = startDate,
        endDate = endDate
    )

    private fun fixtureThreeDayCourse() = fixtureDaily(
        dose = "1",
        time = "09:00",
        endDate = LocalDate.parse("2026-08-16")
    )

    private companion object {
        const val MEDICATION_ID = 1L
        val NOW: Instant = Instant.parse("2026-08-14T00:00:00Z")
        val ZONE: ZoneId = ZoneId.of("Asia/Shanghai")
    }
}
