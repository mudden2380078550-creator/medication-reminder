package org.openmeds.reminder.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.Instant
import java.time.LocalDateTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.openmeds.reminder.data.repository.MedicationRepository
import org.openmeds.reminder.domain.model.DoseAction
import org.openmeds.reminder.domain.model.DoseState
import org.openmeds.reminder.reminder.ReminderActionCoordinator
import org.openmeds.reminder.reminder.ZoneProvider

data class HistoryItem(
    val eventId: Long,
    val medicationName: String,
    val scheduledAt: LocalDateTime,
    val state: DoseState
)

data class HistoryUiState(
    val items: List<HistoryItem> = emptyList()
)

class HistoryViewModel(
    private val repository: MedicationRepository,
    private val coordinator: ReminderActionCoordinator,
    private val zoneProvider: ZoneProvider
) : ViewModel() {

    private val _state = MutableStateFlow(HistoryUiState())
    val state: StateFlow<HistoryUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val items = repository.allEvents().map { event ->
                HistoryItem(
                    eventId = event.id,
                    medicationName = repository.medication(event.id)?.name ?: "未知药品",
                    scheduledAt = event.scheduledAt.atZone(zoneProvider.current()).toLocalDateTime(),
                    state = event.state
                )
            }
            _state.value = HistoryUiState(items)
        }
    }

    fun correct(eventId: Long, action: DoseAction) {
        viewModelScope.launch {
            repository.recordDoseAction(eventId, action, Instant.now())
            coordinator.onHandled(eventId)
            refresh()
        }
    }
}
