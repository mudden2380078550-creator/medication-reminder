package org.openmeds.reminder.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.openmeds.reminder.data.repository.MedicationRepository
import org.openmeds.reminder.domain.model.DoseState
import org.openmeds.reminder.reminder.ZoneProvider

data class TodayItem(
    val eventId: Long,
    val medicationName: String,
    val scheduledAt: LocalDateTime,
    val state: DoseState
)

data class TodayUiState(
    val items: List<TodayItem> = emptyList()
)

class TodayViewModel(
    private val repository: MedicationRepository,
    private val zoneProvider: ZoneProvider
) : ViewModel() {

    private val _state = MutableStateFlow(TodayUiState())
    val state: StateFlow<TodayUiState> = _state.asStateFlow()

    init {
        refresh()
    }
    fun refresh() {
        viewModelScope.launch {
            val zone = zoneProvider.current()
            val today = LocalDate.now(zone)
            val from = today.atStartOfDay(zone).toInstant()
            val to = today.plusDays(1).atStartOfDay(zone).toInstant()
            val items = repository.allEvents().filter { it.scheduledAt >= from && it.scheduledAt < to }.sortedBy { it.scheduledAt }.map { event ->
                TodayItem(eventId = event.id, medicationName = repository.medication(event.id)?.name ?: "未知药品", scheduledAt = event.scheduledAt.atZone(zone).toLocalDateTime(), state = event.state)
            }
            _state.value = TodayUiState(items)
        }
    }
}
