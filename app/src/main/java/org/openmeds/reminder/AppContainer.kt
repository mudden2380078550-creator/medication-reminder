package org.openmeds.reminder

import android.content.Context
import androidx.room.Room
import org.openmeds.reminder.data.db.AppDatabase
import org.openmeds.reminder.data.repository.MedicationRepository
import org.openmeds.reminder.data.repository.RoomMedicationRepository

class AppContainer(context: Context) {
    private val database: AppDatabase = Room.databaseBuilder(
        context,
        AppDatabase::class.java,
        AppDatabase.NAME
    ).build()

    val medicationRepository: MedicationRepository = RoomMedicationRepository(
        database = database,
        medicationDao = database.medicationDao(),
        scheduleDao = database.scheduleDao(),
        doseEventDao = database.doseEventDao(),
        stockTransactionDao = database.stockTransactionDao()
    )
}
