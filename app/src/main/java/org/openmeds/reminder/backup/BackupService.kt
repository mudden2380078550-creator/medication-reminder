package org.openmeds.reminder.backup

import android.content.Context
import android.net.Uri
import java.io.IOException
import org.openmeds.reminder.data.repository.MedicationRepository
import org.openmeds.reminder.data.db.times
import org.openmeds.reminder.domain.model.ScheduleRule

sealed interface RestoreResult {
    data object Success : RestoreResult
    data object Invalid : RestoreResult
}

class BackupService(
    private val context: Context,
    private val repository: MedicationRepository,
    private val codec: BackupCodec
) {

    suspend fun export(uri: Uri): Result<Unit> = runCatching {
        val document = buildDocument()
        val bytes = codec.encode(document)
        context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
            ?: throw IOException("Cannot open output stream")
    }

    suspend fun importAndRestore(uri: Uri): RestoreResult {
        val bytes = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull() ?: return RestoreResult.Invalid
        val document = runCatching { codec.decode(bytes) }.getOrNull() ?: return RestoreResult.Invalid
        if (!isValid(document)) return RestoreResult.Invalid
        repository.replaceAll(
            medications = document.medications.map { it.toMedication() },
            schedules = document.schedules.map { it.toSchedule(document.scheduleTimes) },
            events = document.doseEvents.map { it.toDoseEvent() },
            transactions = document.stockTransactions.map { it.toStockTransaction() }
        )
        return RestoreResult.Success
    }

    private fun isValid(document: BackupDocument): Boolean {
        val medicationIds = document.medications.map { it.id }.toSet()
        if (document.schedules.any { it.medicationId !in medicationIds }) return false
        val scheduleIds = document.schedules.map { it.id }.toSet()
        if (document.doseEvents.any { it.scheduleId !in scheduleIds }) return false
        if (document.scheduleTimes.any { it.scheduleId !in scheduleIds }) return false
        return true
    }

    private fun ruleType(rule: ScheduleRule): Int = when (rule) {
        is ScheduleRule.Daily -> 1
        is ScheduleRule.Weekly -> 2
        is ScheduleRule.EveryNDays -> 3
    }

    private suspend fun buildDocument(): BackupDocument {
        val schedules = repository.schedules()
        val medications = repository.medications()
        return BackupDocument(
            schemaVersion = BackupCodec.CURRENT_VERSION,
            medications = medications.map {
                BackupMedication(it.id, it.name, it.unit, it.stock.value, it.note, it.isActive)
            },
            schedules = schedules.map {
                BackupSchedule(
                    id = it.id,
                    medicationId = it.medicationId,
                    doseMilliUnits = it.dose.value,
                    ruleType = ruleType(it.rule),
                    weekdayMask = weekdayMask(it.rule),
                    intervalDays = intervalDays(it.rule),
                    anchorEpochDay = anchorEpochDay(it.rule),
                    startEpochDay = it.startDate.toEpochDay(),
                    endEpochDay = it.endDate?.toEpochDay()
                )
            },
            scheduleTimes = schedules.flatMap { s ->
                s.rule.times().map { BackupScheduleTime(s.id, it.hour * 60 + it.minute) }
            },
            doseEvents = repository.allEvents().map {
                BackupDoseEvent(it.id, it.medicationId, it.scheduleId, it.dose.value, it.scheduledAt.toEpochMilli(), it.state.name, it.actedAt?.toEpochMilli(), it.reminderCount)
            },
            stockTransactions = medications.flatMap { repository.stockTransactionsForMedication(it.id) }.map {
                BackupStockTransaction(it.id, it.medicationId, it.doseEventId, it.delta.value, it.occurredAt.toEpochMilli(), it.reason.name)
            }
        )
    }

    private fun weekdayMask(rule: ScheduleRule): Int =
        (rule as? ScheduleRule.Weekly)?.days?.fold(0) { mask, day -> mask or (1 shl (day.value - 1)) } ?: 0

    private fun intervalDays(rule: ScheduleRule): Int =
        (rule as? ScheduleRule.EveryNDays)?.intervalDays ?: 0

    private fun anchorEpochDay(rule: ScheduleRule): Long =
        (rule as? ScheduleRule.EveryNDays)?.anchorDate?.toEpochDay() ?: 0L
}
