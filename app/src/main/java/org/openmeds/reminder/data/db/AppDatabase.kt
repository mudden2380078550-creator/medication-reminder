package org.openmeds.reminder.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        MedicationEntity::class,
        ScheduleEntity::class,
        ScheduleTimeEntity::class,
        DoseEventEntity::class,
        StockTransactionEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun medicationDao(): MedicationDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun doseEventDao(): DoseEventDao
    abstract fun stockTransactionDao(): StockTransactionDao

    companion object {
        const val NAME = "medication-reminder.db"
    }
}
