package org.openmeds.reminder.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StockTransactionDao {
    @Insert
    suspend fun insert(transaction: StockTransactionEntity): Long

    @Query("SELECT * FROM stock_transaction WHERE medicationId = :medicationId ORDER BY occurredAtEpochMilli ASC")
    suspend fun forMedication(medicationId: Long): List<StockTransactionEntity>

    @Query("SELECT * FROM stock_transaction WHERE medicationId = :medicationId ORDER BY occurredAtEpochMilli ASC")
    fun observeForMedication(medicationId: Long): Flow<List<StockTransactionEntity>>

    @Query("DELETE FROM stock_transaction")
    suspend fun clearAll()
}
