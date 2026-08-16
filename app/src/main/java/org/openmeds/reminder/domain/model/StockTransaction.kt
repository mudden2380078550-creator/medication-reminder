package org.openmeds.reminder.domain.model

import java.time.Instant

enum class StockTransactionReason {
    RESTOCK,
    CONSUME,
    CORRECTION
}

data class StockTransaction(
    val id: Long,
    val medicationId: Long,
    val doseEventId: Long?,
    val delta: SignedMilliUnits,
    val occurredAt: Instant,
    val reason: StockTransactionReason
)
