package org.openmeds.reminder.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import org.openmeds.reminder.domain.model.DoseState
import org.openmeds.reminder.domain.model.SignedMilliUnits
import org.openmeds.reminder.domain.model.StockTransactionReason
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

@Entity(tableName = "medication")
data class MedicationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val unit: String,
    val stockMilliUnits: Long,
    val note: String?,
    val isActive: Boolean
)

@Entity(
    tableName = "schedule",
    foreignKeys = [
        ForeignKey(
            entity = MedicationEntity::class,
            parentColumns = ["id"],
            childColumns = ["medicationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("medicationId")]
)
data class ScheduleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medicationId: Long,
    val doseMilliUnits: Long,
    val ruleType: Int,
    val weekdayMask: Int,
    val intervalDays: Int,
    val anchorEpochDay: Long,
    val startEpochDay: Long,
    val endEpochDay: Long?
)

@Entity(
    tableName = "schedule_time",
    foreignKeys = [
        ForeignKey(
            entity = ScheduleEntity::class,
            parentColumns = ["id"],
            childColumns = ["scheduleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("scheduleId")]
)
data class ScheduleTimeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scheduleId: Long,
    val minuteOfDay: Int
)

@Entity(
    tableName = "dose_event",
    foreignKeys = [
        ForeignKey(
            entity = ScheduleEntity::class,
            parentColumns = ["id"],
            childColumns = ["scheduleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("scheduleId"), Index("state"), Index("scheduledAtEpochMilli")]
)
data class DoseEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medicationId: Long,
    val scheduleId: Long,
    val doseMilliUnits: Long,
    val scheduledAtEpochMilli: Long,
    val state: String,
    val actedAtEpochMilli: Long?,
    val reminderCount: Int
)

@Entity(
    tableName = "stock_transaction",
    foreignKeys = [
        ForeignKey(
            entity = MedicationEntity::class,
            parentColumns = ["id"],
            childColumns = ["medicationId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = DoseEventEntity::class,
            parentColumns = ["id"],
            childColumns = ["doseEventId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("medicationId"), Index("doseEventId")]
)
data class StockTransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medicationId: Long,
    val doseEventId: Long?,
    val deltaMilliUnits: Long,
    val occurredAtEpochMilli: Long,
    val reason: String
)

object ScheduleRuleCode {
    const val DAILY = 1
    const val WEEKLY = 2
    const val EVERY_N_DAYS = 3
}

object WeekdayMask {
    fun toMask(days: Set<DayOfWeek>): Int = days.fold(0) { mask, day -> mask or (1 shl (day.value - 1)) }

    fun fromMask(mask: Int): Set<DayOfWeek> = DayOfWeek.entries
        .filter { mask and (1 shl (it.value - 1)) != 0 }
        .toSet()
}

object DbConverters {
    fun epochDay(date: LocalDate): Long = date.toEpochDay()

    fun toLocalDate(epochDay: Long): LocalDate = LocalDate.ofEpochDay(epochDay)

    fun minuteOfDay(time: LocalTime): Int = time.hour * 60 + time.minute

    fun toLocalTime(minuteOfDay: Int): LocalTime = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)

    fun epochMilli(instant: Instant): Long = instant.toEpochMilli()

    fun toInstant(epochMilli: Long): Instant = Instant.ofEpochMilli(epochMilli)

    fun stateName(state: DoseState): String = state.name

    fun doseState(name: String): DoseState = DoseState.valueOf(name)

    fun reasonName(reason: StockTransactionReason): String = reason.name

    fun stockReason(name: String): StockTransactionReason = StockTransactionReason.valueOf(name)
}

object StockTransactionFactory {
    fun consume(event: DoseEventEntity, actedAt: Instant): StockTransactionEntity = StockTransactionEntity(
        medicationId = event.medicationId,
        doseEventId = event.id,
        deltaMilliUnits = -event.doseMilliUnits,
        occurredAtEpochMilli = actedAt.toEpochMilli(),
        reason = StockTransactionReason.CONSUME.name
    )

    fun restock(medicationId: Long, amount: SignedMilliUnits, actedAt: Instant): StockTransactionEntity =
        StockTransactionEntity(
            medicationId = medicationId,
            doseEventId = null,
            deltaMilliUnits = amount.value,
            occurredAtEpochMilli = actedAt.toEpochMilli(),
            reason = StockTransactionReason.RESTOCK.name
        )

    fun correction(medicationId: Long, amount: SignedMilliUnits, actedAt: Instant): StockTransactionEntity =
        StockTransactionEntity(
            medicationId = medicationId,
            doseEventId = null,
            deltaMilliUnits = amount.value,
            occurredAtEpochMilli = actedAt.toEpochMilli(),
            reason = StockTransactionReason.CORRECTION.name
        )
}
