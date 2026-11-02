package com.iclonud.bridge.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iclonud.bridge.App
import com.iclonud.bridge.model.CalEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class CalendarViewModel : ViewModel() {

    data class State(
        val loading: Boolean = false,
        val events: List<CalEvent> = emptyList(),
        val error: String? = null,
        val saving: Boolean = false,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state
    private val repo get() = App.instance.caldav

    fun refresh() {
        val r = repo ?: return
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                val month = 30L * 24 * 3600 * 1000
                _state.value = _state.value.copy(loading = false, events = r.listEvents(now - month, now + 3 * month))
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, error = e.message)
            }
        }
    }

    fun create(summary: String, start: Long, end: Long, location: String, onDone: () -> Unit) {
        val r = repo ?: return
        _state.value = _state.value.copy(saving = true, error = null)
        viewModelScope.launch {
            try {
                r.createEvent(summary, start, end, location)
                _state.value = _state.value.copy(saving = false)
                onDone(); refresh()
            } catch (e: Exception) {
                _state.value = _state.value.copy(saving = false, error = "No se pudo crear: ${e.message}")
            }
        }
    }

    fun delete(e: CalEvent) {
        val r = repo ?: return
        viewModelScope.launch {
            try { r.deleteEvent(e.href); refresh() }
            catch (ex: Exception) { _state.value = _state.value.copy(error = ex.message) }
        }
    }
}
