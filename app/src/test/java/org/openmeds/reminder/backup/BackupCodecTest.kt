package org.openmeds.reminder.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupCodecTest {
    private val codec = BackupCodec()

    @Test
    fun versionOneRoundTripsWithoutLosingMilliUnits() {
        val bytes = codec.encode(fixtureBackup(stockMilliUnits = 1_500L))
        assertEquals(1_500L, codec.decode(bytes).medications.single().stockMilliUnits)
    }

    @Test
    fun unknownVersionIsRejected() {
        val bytes = codec.encode(BackupDocument(schemaVersion = 99))
        assertThrows(IllegalArgumentException::class.java) { codec.decode(bytes) }
    }

    @Test
    fun corruptJsonIsRejected() {
        assertThrows(Exception::class.java) { codec.decode("not json".toByteArray()) }
    }

    @Test
    fun emptyBackupRoundTrips() {
        val bytes = codec.encode(BackupDocument())
        val decoded = codec.decode(bytes)
        assertEquals(1, decoded.schemaVersion)
        assertEquals(0, decoded.medications.size)
    }

    private fun fixtureBackup(stockMilliUnits: Long): BackupDocument = BackupDocument(
        medications = listOf(
            BackupMedication(
                id = 1L,
                name = "降压药",
                unit = "片",
                stockMilliUnits = stockMilliUnits,
                note = null,
                isActive = true
            )
        )
    )
}
