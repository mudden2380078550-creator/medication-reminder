package org.openmeds.reminder.domain.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.openmeds.reminder.domain.model.MedicationSchedule
import org.openmeds.reminder.domain.model.MedicationPlanInput
import org.openmeds.reminder.domain.model.MilliUnits
import org.openmeds.reminder.domain.model.ScheduleRule
import org.openmeds.reminder.domain.model.SignedMilliUnits
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class ScheduleEngineTest {
    private val engine = ScheduleEngine()

    @Test
    fun weeklyScheduleSkipsUnselectedDays() {
        val schedule = fixtureWeekly(days = setOf(DayOfWeek.MONDAY), time = LocalTime.of(9, 0))

        val next = engine.nextOccurrence(
            schedule,
            Instant.parse("2026-08-17T01:01:00Z"),
            ZoneId.of("Asia/Shanghai")
        )

        assertEquals(Instant.parse("2026-08-24T01:00:00Z"), next)
    }

    @Test
    fun everyThreeDaysIsAnchoredToStartDate() {
        val schedule = fixtureEveryNDays(
            3,
            LocalDate.parse("2026-08-01"),
            LocalTime.of(8, 0)
        )

        assertEquals(
            Instant.parse("2026-08-07T00:00:00Z"),
            engine.nextOccurrence(
                schedule,
                Instant.parse("2026-08-05T00:00:00Z"),
                ZoneId.of("Asia/Shanghai")
            )
        )
    }

    @Test
    fun everyNDaysDoesNotOccurBeforeItsAnchorDate() {
        val schedule = MedicationSchedule(
            id = 1L,
            medicationId = 2L,
            dose = MilliUnits(1_000L),
            rule = ScheduleRule.EveryNDays(
                intervalDays = 3,
                anchorDate = LocalDate.parse("2026-08-10"),
                times = listOf(LocalTime.of(8, 0))
            ),
            startDate = LocalDate.parse("2026-08-01"),
            endDate = null
        )

        assertEquals(
            Instant.parse("2026-08-10T00:00:00Z"),
            engine.nextOccurrence(
                schedule,
                Instant.parse("2026-08-01T00:00:00Z"),
                ZoneId.of("Asia/Shanghai")
            )
        )
    }

    @Test
    fun halfTabletUsesExactMilliUnits() {
        assertEquals(1_500L, MilliUnits.fromDecimal("1.5").value)
    }

    @Test
    fun signedMilliUnitsAcceptsTheSmallestRepresentableQuantity() {
        assertEquals(Long.MIN_VALUE, SignedMilliUnits.fromDecimal("-9223372036854775.808").value)
    }

    @Test
    fun dailyScheduleReturnsEachSortedDistinctTime() {
        val schedule = fixtureDaily(
            times = listOf(LocalTime.of(21, 0), LocalTime.of(9, 0), LocalTime.of(9, 0))
        )

        val occurrences = engine.occurrencesBetween(
            schedule,
            Instant.parse("2026-08-01T00:00:00Z"),
            Instant.parse("2026-08-02T00:00:00Z"),
            ZoneId.of("Asia/Shanghai")
        )

        assertEquals(
            listOf(
                Instant.parse("2026-08-01T01:00:00Z"),
                Instant.parse("2026-08-01T13:00:00Z")
            ),
            occurrences
        )
    }

    @Test
    fun courseEndDateExcludesLaterOccurrences() {
        val schedule = fixtureDaily(
            times = listOf(LocalTime.of(9, 0)),
            endDate = LocalDate.parse("2026-08-02")
        )

        assertNull(
            engine.nextOccurrence(
                schedule,
                Instant.parse("2026-08-02T01:00:00Z"),
                ZoneId.of("Asia/Shanghai")
            )
        )
    }

    @Test
    fun dailyScheduleIncludesLeapDay() {
        val schedule = fixtureDaily(
            times = listOf(LocalTime.of(8, 0)),
            startDate = LocalDate.parse("2024-02-28")
        )

        assertEquals(
            Instant.parse("2024-02-29T00:00:00Z"),
            engine.nextOccurrence(
                schedule,
                Instant.parse("2024-02-28T00:00:00Z"),
                ZoneId.of("Asia/Shanghai")
            )
        )
    }

    @Test
    fun dstGapMovesLocalTimeToFirstValidInstant() {
        val schedule = fixtureDaily(
            times = listOf(LocalTime.of(2, 30)),
            startDate = LocalDate.parse("2026-03-08")
        )

        assertEquals(
            Instant.parse("2026-03-08T07:30:00Z"),
            engine.nextOccurrence(
                schedule,
                Instant.parse("2026-03-08T00:00:00Z"),
                ZoneId.of("America/New_York")
            )
        )
    }

    @Test
    fun dstOverlapProducesOnlyTheEarlierOffsetOccurrence() {
        val schedule = fixtureDaily(
            times = listOf(LocalTime.of(1, 30)),
            startDate = LocalDate.parse("2026-11-01")
        )

        assertEquals(
            listOf(Instant.parse("2026-11-01T05:30:00Z")),
            engine.occurrencesBetween(
                schedule,
                Instant.parse("2026-11-01T00:00:00Z"),
                Instant.parse("2026-11-01T08:00:00Z"),
                ZoneId.of("America/New_York")
            )
        )
    }

    @Test
    fun dstGapSortsAndDeduplicatesMappedInstants() {
        val schedule = fixtureDaily(
            times = listOf(LocalTime.of(3, 0), LocalTime.of(2, 30), LocalTime.of(2, 0)),
            startDate = LocalDate.parse("2026-03-08")
        )

        assertEquals(
            listOf(
                Instant.parse("2026-03-08T07:00:00Z"),
                Instant.parse("2026-03-08T07:30:00Z")
            ),
            engine.occurrencesBetween(
                schedule,
                Instant.parse("2026-03-08T06:50:00Z"),
                Instant.parse("2026-03-08T07:45:00Z"),
                ZoneId.of("America/New_York")
            )
        )
    }

    @Test
    fun wholeDayDstGapDeduplicatesInstantsAcrossLogicalDates() {
        val schedule = fixtureDaily(
            times = listOf(LocalTime.of(9, 0)),
            startDate = LocalDate.parse("2011-12-30"),
            endDate = LocalDate.parse("2011-12-31")
        )

        assertEquals(
            listOf(Instant.parse("2011-12-30T19:00:00Z")),
            engine.occurrencesBetween(
                schedule,
                Instant.parse("2011-12-30T00:00:00Z"),
                Instant.parse("2011-12-31T00:00:00Z"),
                ZoneId.of("Pacific/Apia")
            )
        )
    }

    @Test
    fun nextOccurrenceStrictlyExcludesAnEqualInstant() {
        val schedule = fixtureDaily(times = listOf(LocalTime.of(9, 0)))

        assertEquals(
            Instant.parse("2026-08-02T01:00:00Z"),
            engine.nextOccurrence(
                schedule,
                Instant.parse("2026-08-01T01:00:00Z"),
                ZoneId.of("Asia/Shanghai")
            )
        )
    }

    @Test
    fun scheduleRuleRejectsAnEmptyTimeList() {
        assertThrows(IllegalArgumentException::class.java) {
            ScheduleRule.Daily(emptyList())
        }
    }

    @Test
    fun everyNDaysRejectsAZeroInterval() {
        assertThrows(IllegalArgumentException::class.java) {
            ScheduleRule.EveryNDays(0, LocalDate.parse("2026-08-01"), listOf(LocalTime.NOON))
        }
    }

    @Test
    fun scheduleRejectsAnEndDateBeforeItsStartDate() {
        assertThrows(IllegalArgumentException::class.java) {
            fixtureDaily(
                times = listOf(LocalTime.NOON),
                startDate = LocalDate.parse("2026-08-02"),
                endDate = LocalDate.parse("2026-08-01")
            )
        }
    }

    @Test
    fun milliUnitsRejectsNegativeQuantities() {
        assertThrows(IllegalArgumentException::class.java) {
            MilliUnits.fromDecimal("-1")
        }
    }

    @Test
    fun milliUnitsRejectsMoreThanThreeDecimalPlaces() {
        assertThrows(IllegalArgumentException::class.java) {
            MilliUnits.fromDecimal("1.0001")
        }
    }

    @Test
    fun milliUnitsRejectsMalformedDecimalText() {
        assertThrows(IllegalArgumentException::class.java) {
            MilliUnits.fromDecimal("one-and-a-half")
        }
    }

    @Test
    fun milliUnitsRejectsPositiveValueAboveLongRange() {
        assertThrows(ArithmeticException::class.java) {
            MilliUnits.fromDecimal("9223372036854775.808")
        }
    }

    @Test
    fun signedMilliUnitsRejectsNegativeValueBelowLongRange() {
        assertThrows(ArithmeticException::class.java) {
            SignedMilliUnits.fromDecimal("-9223372036854775.809")
        }
    }

    @Test
    fun signedMilliUnitsSubtractionRejectsAnUnrepresentableResult() {
        assertThrows(ArithmeticException::class.java) {
            SignedMilliUnits(Long.MIN_VALUE) - MilliUnits(1L)
        }
    }

    @Test
    fun medicationPlanInputPreservesNegativeOpeningStock() {
        val input = MedicationPlanInput(
            name = "Example",
            unit = "tablet",
            stock = SignedMilliUnits(-500L),
            note = null,
            dose = MilliUnits(1_000L),
            rule = ScheduleRule.Daily(listOf(LocalTime.of(9, 0))),
            startDate = LocalDate.parse("2026-08-01"),
            endDate = null
        )

        assertEquals(-500L, input.stock.value)
    }

    private fun fixtureWeekly(days: Set<DayOfWeek>, time: LocalTime): MedicationSchedule =
        MedicationSchedule(
            id = 1L,
            medicationId = 2L,
            dose = MilliUnits(1_000L),
            rule = ScheduleRule.Weekly(days, listOf(time)),
            startDate = LocalDate.parse("2026-08-01"),
            endDate = null
        )

    private fun fixtureEveryNDays(
        intervalDays: Int,
        anchorDate: LocalDate,
        time: LocalTime
    ): MedicationSchedule =
        MedicationSchedule(
            id = 1L,
            medicationId = 2L,
            dose = MilliUnits(1_000L),
            rule = ScheduleRule.EveryNDays(intervalDays, anchorDate, listOf(time)),
            startDate = anchorDate,
            endDate = null
        )

    private fun fixtureDaily(
        times: List<LocalTime>,
        startDate: LocalDate = LocalDate.parse("2026-08-01"),
        endDate: LocalDate? = null
    ): MedicationSchedule =
        MedicationSchedule(
            id = 1L,
            medicationId = 2L,
            dose = MilliUnits(1_000L),
            rule = ScheduleRule.Daily(times),
            startDate = startDate,
            endDate = endDate
        )
}
