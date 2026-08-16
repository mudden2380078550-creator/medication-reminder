package org.openmeds.reminder.backup

import kotlinx.serialization.Serializable

@Serializable
data class BackupMedication(
    val id: Long,
    val name: String,
    val unit: String,
    val stockMilliUnits: Long,
    val note: String?,
    val isActive: Boolean
)

@Serializable
data class BackupSchedule(
    val id: Long,
    val medicationId: Long,
    val doseMilliUnits: Long,
    val ruleType: Int,
    val weekdayMask: Int,
    val intervalDays: Int,
    val anchorEpochDay: Long,
    val startEpochDay: Long,
    val endEpochDay: Long?
)

@Serializable
data class BackupScheduleTime(
    val scheduleId: Long,
    val minuteOfDay: Int
)

@Serializable
data class BackupDoseEvent(
    val id: Long,
    val medicationId: Long,
    val scheduleId: Long,
    val doseMilliUnits: Long,
    val scheduledAtEpochMilli: Long,
    val state: String,
    val actedAtEpochMilli: Long?,
    val reminderCount: Int
)

@Serializable
data class BackupStockTransaction(
    val id: Long,
    val medicationId: Long,
    val doseEventId: Long?,
    val deltaMilliUnits: Long,
    val occurredAtEpochMilli: Long,
    val reason: String
)

@Serializable
data class BackupDocument(
    val schemaVersion: Int = 1,
    val medications: List<BackupMedication> = emptyList(),
    val schedules: List<BackupSchedule> = emptyList(),
    val scheduleTimes: List<BackupScheduleTime> = emptyList(),
    val doseEvents: List<BackupDoseEvent> = emptyList(),
    val stockTransactions: List<BackupStockTransaction> = emptyList()
)
