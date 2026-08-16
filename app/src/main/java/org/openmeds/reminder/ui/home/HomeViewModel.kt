package org.openmeds.reminder.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.openmeds.reminder.data.repository.HomeData
import org.openmeds.reminder.data.repository.MedicationRepository
import org.openmeds.reminder.domain.inventory.InventoryForecaster
import org.openmeds.reminder.domain.model.DoseAction
import org.openmeds.reminder.reminder.ReminderActionCoordinator
import org.openmeds.reminder.reminder.ReminderCapabilitySnapshot
import org.openmeds.reminder.reminder.ReminderCapabilitySource
import org.openmeds.reminder.reminder.ZoneProvider

class HomeViewModel(
    private val repository: MedicationRepository,
    private val forecaster: InventoryForecaster,
    private val capabilitySource: ReminderCapabilitySource,
    private val coordinator: ReminderActionCoordinator,
    private val zoneProvider: ZoneProvider,
    private val clock: Clock = Clock.systemDefaultZone()
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(repository.observeHome(), capabilitySource.snapshot) { home, capability ->
                buildState(home, capability)
            }.collect { _state.value = it }
        }
    }

    fun confirmNextDose(eventId: Long) {
        viewModelScope.launch {
            repository.recordDoseAction(eventId, DoseAction.TAKEN, clock.instant())
            coordinator.onHandled(eventId)
        }
    }

    private fun buildState(home: HomeData, capability: ReminderCapabilitySnapshot): HomeUiState {
        val now = clock.instant()
        val zone = zoneProvider.current()
        val cards = home.medications.map { medication ->
            val schedules = home.schedules.filter { it.medicationId == medication.id }
            val forecast = forecaster.forecast(medication, schedules, now, zone)
            MedicationCard(
                medicationId = medication.id,
                name = medication.name,
                unit = medication.unit,
                stockMilliUnits = medication.stock.value,
                daysRemaining = forecast.daysRemaining,
                depletesAt = forecast.depletesAt?.atZone(zone)?.toLocalDate(),
                isLowStock = forecaster.needsDailyLowStockReminder(forecast),
                isStockInsufficient = forecast.nextDoseShortfall
            )
        }.sortedWith(compareByDescending<MedicationCard> { it.isLowStock }.thenBy { it.name })
        return HomeUiState(
            nextDose = home.nextDose,
            medicationCards = cards,
            capabilityWarnings = buildWarnings(capability),
            isLoading = false
        )
    }

    private fun buildWarnings(capability: ReminderCapabilitySnapshot): List<CapabilityWarning> {
        val warnings = mutableListOf<CapabilityWarning>()
        if (!capability.exactAlarms) {
            warnings.add(CapabilityWarning("提醒时间可能延迟", "系统未授予精确闹钟权限，服药提醒可能延迟。请前往系统设置开启。"))
        }
        if (!capability.notifications) {
            warnings.add(CapabilityWarning("通知权限未开启", "请允许通知，否则收不到服药提醒。"))
        }
        if (capability.batteryRestricted) {
            warnings.add(CapabilityWarning("电池优化可能延迟提醒", "请在系统设置中为应用关闭电池优化。"))
        }
        return warnings
    }
}
