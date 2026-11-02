package com.iclonud.bridge.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iclonud.bridge.App
import com.iclonud.bridge.model.MailMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MailViewModel : ViewModel() {

    data class State(
        val loading: Boolean = false,
        val messages: List<MailMessage> = emptyList(),
        val error: String? = null,
        val open: MailMessage? = null,
        val sending: Boolean = false,
        val sent: Boolean = false,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state
    private val repo get() = App.instance.mail

    fun refresh() {
        val r = repo ?: return
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(loading = false, messages = r.inbox())
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, error = traducir(e.message))
            }
        }
    }

    private fun traducir(msg: String?): String =
        if (msg != null && (msg.contains("AUTHENTICATE", true) || msg.contains("Authentication", true)))
            "iCloud no permite el acceso al correo con esta cuenta. Si el calendario y los contactos " +
                "funcionan, tu Apple ID no tiene una dirección @icloud.com activa (iPhone: Ajustes → " +
                "tu nombre → iCloud → Correo de iCloud)."
        else msg ?: "No se pudo cargar el correo."

    fun openMessage(m: MailMessage) {
        val r = repo ?: return
        _state.value = _state.value.copy(open = m.copy(body = "Cargando…"))
        viewModelScope.launch {
            try {
                val body = r.body(m.uid.toInt())
                _state.value = _state.value.copy(open = m.copy(body = body))
            } catch (e: Exception) {
                _state.value = _state.value.copy(open = m.copy(body = "Error: ${e.message}"))
            }
        }
    }

    fun closeMessage() { _state.value = _state.value.copy(open = null) }

    fun send(to: String, subject: String, body: String, onDone: () -> Unit) {
        val r = repo ?: return
        _state.value = _state.value.copy(sending = true, error = null)
        viewModelScope.launch {
            try {
                r.send(to, subject, body)
                _state.value = _state.value.copy(sending = false, sent = true)
                onDone()
            } catch (e: Exception) {
                _state.value = _state.value.copy(sending = false, error = "No se pudo enviar: ${e.message}")
            }
        }
    }

    fun clearSent() { _state.value = _state.value.copy(sent = false) }
}
