package com.iclonud.bridge.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.iclonud.bridge.vm.CalendarViewModel

@Composable
fun CalendarScreen(modifier: Modifier = Modifier, vm: CalendarViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    var adding by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.refresh() }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Próximos eventos", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = { vm.refresh() }) { Icon(Icons.Filled.Refresh, "Actualizar") }
            }
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let { ErrorBar(it) }
            LazyColumn(Modifier.fillMaxSize()) {
                items(state.events) { e ->
                    ListItem(
                        headlineContent = { Text(e.summary) },
                        overlineContent = { Text(if (e.allDay) fmtDay(e.startMillis) else fmtDateTime(e.startMillis)) },
                        supportingContent = { if (e.location.isNotBlank()) Text(e.location) },
                        trailingContent = {
                            IconButton(onClick = { vm.delete(e) }) { Icon(Icons.Filled.Delete, "Eliminar") }
                        },
                    )
                    HorizontalDivider()
                }
                if (!state.loading && state.events.isEmpty())
                    item { Text("No hay eventos en el rango.", Modifier.padding(24.dp)) }
            }
        }
        FloatingActionButton(onClick = { adding = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
            Icon(Icons.Filled.Add, "Nuevo evento")
        }
    }

    if (adding) NewEventDialog(
        saving = state.saving,
        onDismiss = { adding = false },
        onCreate = { summary, start, end, loc -> vm.create(summary, start, end, loc) { adding = false } },
    )
}

@Composable
private fun NewEventDialog(saving: Boolean, onDismiss: () -> Unit, onCreate: (String, Long, Long, String) -> Unit) {
    var summary by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var hours by remember { mutableStateOf("1") }
    // Para mantener la app enfocada, el evento empieza dentro de una hora y dura lo indicado.
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuevo evento") },
        text = {
            Column {
                OutlinedTextField(summary, { summary = it }, label = { Text("Título") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(location, { location = it }, label = { Text("Lugar (opcional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(hours, { hours = it.filter(Char::isDigit) }, label = { Text("Duración (horas)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Text("Comienza dentro de 1 hora desde ahora.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val start = System.currentTimeMillis() + 3600_000
                    val dur = (hours.toLongOrNull() ?: 1L).coerceAtLeast(1) * 3600_000
                    onCreate(summary, start, start + dur, location)
                },
                enabled = !saving && summary.isNotBlank(),
            ) {
                if (saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Crear")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
