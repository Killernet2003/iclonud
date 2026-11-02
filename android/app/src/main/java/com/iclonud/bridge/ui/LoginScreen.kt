package com.iclonud.bridge.ui

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import com.iclonud.bridge.data.ICloudEndpoints
import com.iclonud.bridge.vm.LoginViewModel

@Composable
fun LoginScreen(onLoggedIn: () -> Unit, vm: LoginViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val ctx = LocalContext.current
    var appleId by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }

    LaunchedEffect(state.done) { if (state.done) onLoggedIn() }

    Column(
        Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(32.dp))
        Text("iClonud", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
        Text(
            "Tu iCloud en Android por CalDAV, CardDAV e IMAP",
            style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))

        OutlinedTextField(
            value = appleId, onValueChange = { appleId = it },
            label = { Text("Apple ID (correo de tu cuenta de Apple)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = pass, onValueChange = { pass = it },
            label = { Text("Contraseña de aplicación") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Apple exige una contraseña de aplicación para clientes externos. " +
                "No se acepta tu contraseña normal del Apple ID.",
            style = MaterialTheme.typography.bodySmall,
        )
        TextButton(onClick = {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, ICloudEndpoints.APP_PASSWORD_HELP.toUri()))
        }) { Text("¿Cómo creo una? (account.apple.com)") }

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { vm.login(appleId, pass) },
            enabled = !state.loading,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            if (state.loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            else Text("Iniciar sesión")
        }

        state.error?.let {
            Spacer(Modifier.height(16.dp))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Text(it, Modifier.padding(14.dp), color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "La contraseña se guarda cifrada en el dispositivo (Android Keystore) y solo " +
                "viaja por TLS hacia los servidores de Apple.",
            style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
        )
    }
}
