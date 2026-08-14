package org.openmeds.reminder

import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectSmokeTest {
    @Test fun packageNameIsStable() {
        assertEquals("org.openmeds.reminder", MedicationApplication.PACKAGE_ID)
    }
}
