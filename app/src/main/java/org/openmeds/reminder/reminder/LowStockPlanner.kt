package org.openmeds.reminder.reminder

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import org.openmeds.reminder.data.repository.MedicationRepository
import org.openmeds.reminder.domain.inventory.InventoryForecaster
import org.openmeds.reminder.settings.ReminderPreferences

class LowStockPlanner(
    private val repository: MedicationRepository,
    private val forecaster: InventoryForecaster,
    private val scheduler: ReminderScheduler,
    private val preferences: ReminderPreferences,
    private val clock: Clock = Clock.systemDefaultZone()
) {

    suspend fun reconcile(now: Instant, zoneId: ZoneId) {
        val settings = preferences.flow.first()
        val today = now.atZone(zoneId).toLocalDate()
        for (medication in repository.medications().filter { it.isActive }) {
            val schedules = repository.schedules().filter { it.medicationId == medication.id }
            val forecast = forecaster.forecast(medication, schedules, now, zoneId)
            if (forecaster.needsDailyLowStockReminder(forecast)) {
                val trigger = today.atTime(settings.lowStockTime).atZone(zoneId).toInstant()
                scheduler.scheduleLowStock(medication.id, trigger)
            } else {
                scheduler.cancelLowStock(medication.id)
            }
        }
    }
}
