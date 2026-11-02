package com.iclonud.bridge.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.iclonud.bridge.vm.MailViewModel

@Composable
fun MailScreen(modifier: Modifier = Modifier, vm: MailViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    var compose by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.refresh() }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Bandeja de entrada", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = { vm.refresh() }) { Icon(Icons.Filled.Refresh, "Actualizar") }
            }
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let { ErrorBar(it) }

            LazyColumn(Modifier.fillMaxSize()) {
                items(state.messages) { m ->
                    ListItem(
                        headlineContent = { Text(m.from, fontWeight = if (m.seen) FontWeight.Normal else FontWeight.Bold) },
                        overlineContent = { Text(fmtDateTime(m.dateMillis)) },
                        supportingContent = {
                            Column {
                                Text(m.subject, maxLines = 1)
                                Text(m.preview, maxLines = 1, style = MaterialTheme.typography.bodySmall)
                            }
                        },
                        modifier = Modifier.clickable { vm.openMessage(m) },
                    )
                    HorizontalDivider()
                }
                if (!state.loading && state.messages.isEmpty())
                    item { Text("No hay mensajes.", Modifier.padding(24.dp)) }
            }
        }
        FloatingActionButton(
            onClick = { compose = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) { Icon(Icons.Filled.Edit, "Redactar") }
    }

    state.open?.let { m ->
        AlertDialog(
            onDismissRequest = { vm.closeMessage() },
            confirmButton = { TextButton(onClick = { vm.closeMessage() }) { Text("Cerrar") } },
            title = { Text(m.subject) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("De: ${m.from}", style = MaterialTheme.typography.labelMedium)
                    Text(fmtDateTime(m.dateMillis), style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.height(12.dp))
                    Text(m.body)
                }
            },
        )
    }

    if (compose) ComposeDialog(
        sending = state.sending,
        onDismiss = { compose = false },
        onSend = { to, subj, body -> vm.send(to, subj, body) { compose = false } },
    )
}

@Composable
private fun ComposeDialog(sending: Boolean, onDismiss: () -> Unit, onSend: (String, String, String) -> Unit) {
    var to by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuevo correo") },
        text = {
            Column {
                OutlinedTextField(to, { to = it }, label = { Text("Para") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(subject, { subject = it }, label = { Text("Asunto") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(body, { body = it }, label = { Text("Mensaje") }, modifier = Modifier.fillMaxWidth().height(160.dp))
            }
        },
        confirmButton = {
            Button(onClick = { onSend(to, subject, body) }, enabled = !sending && to.isNotBlank()) {
                if (sending) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Enviar")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
fun ErrorBar(msg: String) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
        Text(msg, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall)
    }
}
