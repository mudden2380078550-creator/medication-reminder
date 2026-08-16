package org.openmeds.reminder.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicationDao {
    @Query("SELECT * FROM medication WHERE isActive = 1 ORDER BY name COLLATE NOCASE")
    fun observeActive(): Flow<List<MedicationEntity>>

    @Query("SELECT * FROM medication WHERE id = :id")
    fun observeById(id: Long): Flow<MedicationEntity?>

    @Query("SELECT * FROM medication WHERE id = :id")
    suspend fun byId(id: Long): MedicationEntity?

    @Query("SELECT * FROM medication WHERE isActive = 1")
    suspend fun active(): List<MedicationEntity>

    @Query("SELECT * FROM medication")
    suspend fun all(): List<MedicationEntity>

    @Insert
    suspend fun insert(medication: MedicationEntity): Long

    @Update
    suspend fun update(medication: MedicationEntity)

    @Query("UPDATE medication SET stockMilliUnits = stockMilliUnits + :delta WHERE id = :id")
    suspend fun adjustStock(id: Long, delta: Long)

    @Query("UPDATE medication SET isActive = 0 WHERE id = :id")
    suspend fun deactivate(id: Long)

    @Query("DELETE FROM medication")
    suspend fun clearAll()
}
