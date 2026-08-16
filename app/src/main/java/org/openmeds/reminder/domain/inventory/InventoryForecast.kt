package org.openmeds.reminder.domain.inventory

import org.openmeds.reminder.domain.model.SignedMilliUnits
import java.time.Instant

data class InventoryForecast(
    val depletesAt: Instant?,
    val remainingAtCourseEnd: SignedMilliUnits?,
    val nextDoseShortfall: Boolean,
    val daysRemaining: Long?
)
