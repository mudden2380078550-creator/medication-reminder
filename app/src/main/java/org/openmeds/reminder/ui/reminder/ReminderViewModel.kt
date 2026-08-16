package org.openmeds.reminder.ui.reminder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.openmeds.reminder.data.repository.MedicationRepository
import org.openmeds.reminder.domain.model.DoseAction
import org.openmeds.reminder.domain.model.DoseActionResult
import org.openmeds.reminder.domain.model.DoseState
import org.openmeds.reminder.reminder.ReminderActionCoordinator
import org.openmeds.reminder.reminder.ZoneProvider

class ReminderViewModel(
    private val repository: MedicationRepository,
    private val coordinator: ReminderActionCoordinator,
    private val zoneProvider: ZoneProvider
) : ViewModel() {

    private val _state = MutableStateFlow(ReminderUiState())
    val state: StateFlow<ReminderUiState> = _state.asStateFlow()

    fun load(epochMinute: Long) {
        viewModelScope.launch {
            val events = repository.actionableEventsForMinute(epochMinute)
            val items = events.map { event ->
                ReminderItem(
                    eventId = event.id,
                    medicationName = repository.medication(event.id)?.name ?: "未知药品",
                    doseText = formatDose(repository.medication(event.id)?.unit),
                    scheduledTime = event.scheduledAt.atZone(zoneProvider.current()).toLocalTime(),
                    state = event.state
                )
            }
            _state.value = ReminderUiState(items = items, allHandled = items.isEmpty())
        }
    }

    fun take(eventId: Long) = act(eventId, DoseAction.TAKEN, DoseState.TAKEN)

    fun skip(eventId: Long) = act(eventId, DoseAction.SKIPPED, DoseState.SKIPPED)

    fun snooze(eventId: Long) {
        viewModelScope.launch {
            val result = repository.recordDoseAction(eventId, DoseAction.SNOOZE, Instant.now())
            if (result == DoseActionResult.Applied) {
                coordinator.onSnoozed(eventId, Instant.now())
                setItemState(eventId, DoseState.SNOOZED)
            }
        }
    }

    private fun act(eventId: Long, action: DoseAction, target: DoseState) {
        viewModelScope.launch {
            val result = repository.recordDoseAction(eventId, action, Instant.now())
            if (result == DoseActionResult.Applied || result == DoseActionResult.AlreadyHandled) {
                coordinator.onHandled(eventId)
                setItemState(eventId, target)
            }
        }
    }

    private fun setItemState(eventId: Long, state: DoseState) {
        _state.update { s ->
            val items = s.items.map { if (it.eventId == eventId) it.copy(state = state) else it }
            s.copy(items = items, allHandled = items.all { it.state == DoseState.TAKEN || it.state == DoseState.SKIPPED })
        }
    }

    private fun formatDose(unit: String?): String {
        val unitText = unit ?: ""
        return if (unitText.isBlank()) "1" else "1 $unitText"
    }

    companion object {
        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
