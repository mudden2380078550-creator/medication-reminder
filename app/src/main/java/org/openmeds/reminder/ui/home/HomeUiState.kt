package org.openmeds.reminder.ui.home

import java.time.LocalDate
import org.openmeds.reminder.domain.model.DoseEvent

data class CapabilityWarning(
    val title: String,
    val body: String
)

data class MedicationCard(
    val medicationId: Long,
    val name: String,
    val unit: String,
    val stockMilliUnits: Long,
    val daysRemaining: Long?,
    val depletesAt: LocalDate?,
    val isLowStock: Boolean,
    val isStockInsufficient: Boolean
)

data class HomeUiState(
    val nextDose: DoseEvent? = null,
    val medicationCards: List<MedicationCard> = emptyList(),
    val capabilityWarnings: List<CapabilityWarning> = emptyList(),
    val isLoading: Boolean = true
)
