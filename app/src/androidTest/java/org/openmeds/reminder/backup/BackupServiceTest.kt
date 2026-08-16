package org.openmeds.reminder.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupServiceTest {

    @Test
    fun currentVersionIsOne() {
        assertEquals(1, BackupCodec.CURRENT_VERSION)
    }

    @Test
    fun codecRoundTripsDocument() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val document = BackupDocument(
            medications = listOf(BackupMedication(1L, "药", "片", 1_500L, null, true))
        )
        val bytes = BackupCodec().encode(document)
        val decoded = BackupCodec().decode(bytes)
        assertEquals(1_500L, decoded.medications.single().stockMilliUnits)
    }
}
