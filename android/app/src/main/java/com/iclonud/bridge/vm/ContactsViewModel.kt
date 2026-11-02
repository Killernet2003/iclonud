package com.iclonud.bridge.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iclonud.bridge.App
import com.iclonud.bridge.model.Contact
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ContactsViewModel : ViewModel() {

    data class State(
        val loading: Boolean = false,
        val contacts: List<Contact> = emptyList(),
        val error: String? = null,
        val saving: Boolean = false,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state
    private val repo get() = App.instance.carddav

    fun refresh() {
        val r = repo ?: return
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(loading = false, contacts = r.listContacts())
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, error = e.message)
            }
        }
    }

    fun create(fullName: String, emails: List<String>, phones: List<String>, onDone: () -> Unit) {
        val r = repo ?: return
        _state.value = _state.value.copy(saving = true, error = null)
        viewModelScope.launch {
            try {
                r.createContact(fullName, emails, phones)
                _state.value = _state.value.copy(saving = false)
                onDone(); refresh()
            } catch (e: Exception) {
                _state.value = _state.value.copy(saving = false, error = "No se pudo crear: ${e.message}")
            }
        }
    }

    fun delete(c: Contact) {
        val r = repo ?: return
        viewModelScope.launch {
            try { r.deleteContact(c.href); refresh() }
            catch (ex: Exception) { _state.value = _state.value.copy(error = ex.message) }
        }
    }
}
