package org.openmeds.reminder.reminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingIntentFactoryTest {
    private val factory = PendingIntentFactory()

    @Test
    fun retryKindsHaveDistinctStableRequestCodes() {
        val values = (0..4).map { factory.requestCode(42L, AlarmKind.retryOrFinalize(it)) }
        assertEquals(values.size, values.toSet().size)
        assertEquals(values, (0..4).map { factory.requestCode(42L, AlarmKind.retryOrFinalize(it)) })
    }

    @Test
    fun differentEventsHaveDistinctCodes() {
        val a = factory.requestCode(1L, AlarmKind.DOSE)
        val b = factory.requestCode(2L, AlarmKind.DOSE)
        assertTrue(a != b)
    }

    @Test
    fun lowStockUsesDistinctSlotFromDose() {
        val lowStock = factory.requestCode(7L, AlarmKind.LOW_STOCK)
        val dose = factory.requestCode(7L, AlarmKind.DOSE)
        assertTrue(lowStock != dose)
    }
}
