package org.openmeds.reminder.reminder

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReminderCapabilityCheckerTest {

    @Test
    fun snapshotReportsAllCapabilitiesWithoutCrashing() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val snapshot = ReminderCapabilityChecker(context).snapshot.value
        assertNotNull(snapshot.notifications)
        assertNotNull(snapshot.exactAlarms)
        assertNotNull(snapshot.fullScreen)
        assertNotNull(snapshot.batteryRestricted)
    }
}
