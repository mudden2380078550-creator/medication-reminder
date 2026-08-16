package org.openmeds.reminder.data.db

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

data class ScheduleWithTimes(
    @Embedded val schedule: ScheduleEntity,
    @Relation(parentColumn = "id", entityColumn = "scheduleId")
    val times: List<ScheduleTimeEntity>
)

@Dao
interface ScheduleDao {
    @Transaction
    @Query("SELECT * FROM schedule")
    fun observeAllWithTimes(): Flow<List<ScheduleWithTimes>>

    @Transaction
    @Query("SELECT * FROM schedule")
    suspend fun allWithTimes(): List<ScheduleWithTimes>

    @Transaction
    @Query("SELECT * FROM schedule WHERE id = :id")
    suspend fun byIdWithTimes(id: Long): ScheduleWithTimes?

    @Query("SELECT * FROM schedule WHERE medicationId = :medicationId")
    suspend fun forMedication(medicationId: Long): List<ScheduleEntity>

    @Insert
    suspend fun insert(schedule: ScheduleEntity): Long

    @Insert
    suspend fun insertTime(time: ScheduleTimeEntity): Long

    @Query("DELETE FROM schedule_time WHERE scheduleId = :scheduleId")
    suspend fun deleteTimesForSchedule(scheduleId: Long)

    @Query("DELETE FROM schedule WHERE medicationId = :medicationId")
    suspend fun deleteByMedicationId(medicationId: Long)
}
