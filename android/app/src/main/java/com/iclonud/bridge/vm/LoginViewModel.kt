package com.iclonud.bridge.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iclonud.bridge.App
import com.iclonud.bridge.net.MailRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class LoginViewModel : ViewModel() {

    data class State(
        val loading: Boolean = false,
        val error: String? = null,
        val done: Boolean = false,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    fun login(appleId: String, appPassword: String) {
        if (appleId.isBlank() || appPassword.isBlank()) {
            _state.value = State(error = "Escribe el Apple ID y la contraseña específica de app.")
            return
        }
        _state.value = State(loading = true)
        viewModelScope.launch {
            try {
                val cleanPw = appPassword.trim().replace(" ", "")
                // Verificamos las credenciales con un login IMAP real antes de guardarlas.
                MailRepository(appleId.trim(), cleanPw).verify()
                App.instance.credentials.save(appleId, cleanPw)
                App.instance.buildRepos()
                _state.value = State(done = true)
            } catch (e: Exception) {
                _state.value = State(error = traducir(e.message))
            }
        }
    }

    private fun traducir(msg: String?): String = when {
        msg == null -> "No se pudo iniciar sesión."
        msg.contains("AUTHENTICATE", true) || msg.contains("Invalid", true) || msg.contains("login", true) ->
            "Apple rechazó el acceso. Revisa el Apple ID y usa una contraseña específica de app (no tu clave normal)."
        msg.contains("timeout", true) || msg.contains("Unable to connect", true) ->
            "No hay conexión con los servidores de iCloud. Revisa tu red."
        else -> "No se pudo iniciar sesión: $msg"
    }
}
