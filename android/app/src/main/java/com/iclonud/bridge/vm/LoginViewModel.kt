package com.iclonud.bridge.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iclonud.bridge.App
import com.iclonud.bridge.net.DavClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
            _state.value = State(error = "Escribe el Apple ID y la contraseña de aplicación.")
            return
        }
        _state.value = State(loading = true)
        viewModelScope.launch {
            try {
                val cleanPw = appPassword.trim().replace(" ", "")
                // Verificamos las credenciales con CalDAV, que funciona con cualquier Apple ID.
                // IMAP solo acepta cuentas con dirección @icloud.com activa.
                withContext(Dispatchers.IO) { DavClient(appleId.trim(), cleanPw).discoverCalendars() }
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
        msg.contains(" 401 ") || msg.contains("AUTHENTICATE", true) || msg.contains("Invalid", true) ->
            "Apple rechazó el acceso. Revisa el Apple ID y usa una contraseña de aplicación (no tu clave normal)."
        msg.contains("timeout", true) || msg.contains("Unable to connect", true) ->
            "No hay conexión con los servidores de iCloud. Revisa tu red."
        else -> "No se pudo iniciar sesión: $msg"
    }
}
