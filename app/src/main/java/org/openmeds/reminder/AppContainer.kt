package org.openmeds.reminder

import android.content.Context
import androidx.room.Room
import org.openmeds.reminder.data.db.AppDatabase
import org.openmeds.reminder.data.repository.MedicationRepository
import org.openmeds.reminder.data.repository.RoomMedicationRepository
import org.openmeds.reminder.domain.inventory.InventoryForecaster
import org.openmeds.reminder.domain.schedule.ScheduleEngine
import org.openmeds.reminder.reminder.AndroidReminderScheduler
import org.openmeds.reminder.reminder.DoseNotificationController
import org.openmeds.reminder.reminder.EventPlanner
import org.openmeds.reminder.reminder.PendingIntentFactory
import org.openmeds.reminder.reminder.ReminderCapabilityChecker
import org.openmeds.reminder.reminder.ReminderNotifier
import org.openmeds.reminder.reminder.ReminderOrchestrator
import org.openmeds.reminder.reminder.ReminderScheduler
import org.openmeds.reminder.reminder.ScheduleEventPlanner
import org.openmeds.reminder.reminder.SystemZoneProvider
import org.openmeds.reminder.reminder.ZoneProvider
import org.openmeds.reminder.settings.ReminderPreferences

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

    val scheduleEngine: ScheduleEngine = ScheduleEngine()

    val zoneProvider: ZoneProvider = SystemZoneProvider()

    val inventoryForecaster: InventoryForecaster = InventoryForecaster(scheduleEngine)

    val reminderPreferences: ReminderPreferences = ReminderPreferences(context)

    val pendingIntentFactory: PendingIntentFactory = PendingIntentFactory()

    val capabilityChecker: ReminderCapabilityChecker = ReminderCapabilityChecker(context)

    val notifier: ReminderNotifier = DoseNotificationController(context, medicationRepository, capabilityChecker)

    val scheduler: ReminderScheduler = AndroidReminderScheduler(
        context = context,
        capabilityChecker = capabilityChecker,
        pendingIntentFactory = pendingIntentFactory
    )

    val eventPlanner: EventPlanner = ScheduleEventPlanner(medicationRepository, scheduleEngine)

    val reminderOrchestrator: ReminderOrchestrator = ReminderOrchestrator(
        repository = medicationRepository,
        scheduler = scheduler,
        notifier = notifier,
        planner = eventPlanner,
        zoneProvider = zoneProvider,
        scheduleEngine = scheduleEngine
    )
}
