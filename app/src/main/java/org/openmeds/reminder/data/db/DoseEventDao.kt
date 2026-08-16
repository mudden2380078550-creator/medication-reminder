package org.openmeds.reminder.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DoseEventDao {
    @Query("SELECT * FROM dose_event WHERE id = :id")
    suspend fun byId(id: Long): DoseEventEntity?

    @Insert
    suspend fun insert(event: DoseEventEntity): Long

    @Query("UPDATE dose_event SET state = 'SNOOZED', actedAtEpochMilli = :actedAtEpochMilli WHERE id = :id")
    suspend fun setSnoozed(id: Long, actedAtEpochMilli: Long)

    @Query("UPDATE dose_event SET state = :state, actedAtEpochMilli = :actedAtEpochMilli WHERE id = :id")
    suspend fun setTerminalState(id: Long, state: String, actedAtEpochMilli: Long)

    @Query("UPDATE dose_event SET state = 'UNCONFIRMED', actedAtEpochMilli = :at WHERE id = :id AND state IN ('PENDING','SNOOZED')")
    suspend fun markUnconfirmed(id: Long, at: Long): Int

    @Query("UPDATE dose_event SET reminderCount = :count WHERE id = :id")
    suspend fun setReminderCount(id: Long, count: Int)

    @Query("SELECT * FROM dose_event WHERE state IN ('PENDING','SNOOZED') ORDER BY scheduledAtEpochMilli ASC")
    suspend fun actionablePending(): List<DoseEventEntity>

    @Query("SELECT * FROM dose_event WHERE state IN ('PENDING','SNOOZED') ORDER BY scheduledAtEpochMilli ASC LIMIT 1")
    fun observeEarliestPending(): Flow<DoseEventEntity?>

    @Query(
        "SELECT * FROM dose_event WHERE scheduledAtEpochMilli >= :minuteStart " +
            "AND scheduledAtEpochMilli < :minuteEnd AND state IN ('PENDING','SNOOZED')"
    )
    suspend fun actionableForMinute(minuteStart: Long, minuteEnd: Long): List<DoseEventEntity>

    @Query("SELECT * FROM dose_event WHERE scheduleId = :scheduleId AND scheduledAtEpochMilli = :scheduledAtEpochMilli LIMIT 1")
    suspend fun byScheduleAndTime(scheduleId: Long, scheduledAtEpochMilli: Long): DoseEventEntity?

    @Query("SELECT * FROM dose_event ORDER BY scheduledAtEpochMilli DESC")
    suspend fun all(): List<DoseEventEntity>

    @Query("SELECT * FROM dose_event WHERE medicationId = :medicationId AND scheduledAtEpochMilli >= :fromEpochMilli AND scheduledAtEpochMilli < :toEpochMilli")
    suspend fun forMedicationBetween(medicationId: Long, fromEpochMilli: Long, toEpochMilli: Long): List<DoseEventEntity>

    @Query("DELETE FROM dose_event")
    suspend fun clearAll()
}
