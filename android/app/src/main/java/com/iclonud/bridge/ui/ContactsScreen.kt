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
import com.iclonud.bridge.model.Contact
import com.iclonud.bridge.vm.ContactsViewModel

@Composable
fun ContactsScreen(modifier: Modifier = Modifier, vm: ContactsViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    var adding by remember { mutableStateOf(false) }
    var porBorrar by remember { mutableStateOf<Contact?>(null) }
    LaunchedEffect(Unit) { vm.refresh() }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Contactos", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = { vm.refresh() }) { Icon(Icons.Filled.Refresh, "Actualizar") }
            }
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let { ErrorBar(it) }
            LazyColumn(Modifier.fillMaxSize()) {
                items(state.contacts) { c ->
                    ListItem(
                        headlineContent = { Text(c.fullName) },
                        supportingContent = {
                            Column {
                                c.emails.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                                c.phones.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                            }
                        },
                        trailingContent = {
                            IconButton(onClick = { porBorrar = c }) { Icon(Icons.Filled.Delete, "Eliminar") }
                        },
                    )
                    HorizontalDivider()
                }
                if (!state.loading && state.contacts.isEmpty())
                    item { Text("No hay contactos.", Modifier.padding(24.dp)) }
            }
        }
        FloatingActionButton(onClick = { adding = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
            Icon(Icons.Filled.Add, "Nuevo contacto")
        }
    }

    porBorrar?.let { x ->
        AlertDialog(
            onDismissRequest = { porBorrar = null },
            title = { Text("¿Eliminar este contacto?") },
            text = { Text("Se borrará «${x.fullName}» de iCloud y desaparecerá también de tu iPhone, iPad y Mac.") },
            confirmButton = {
                TextButton(onClick = { vm.delete(x); porBorrar = null }) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { porBorrar = null }) { Text("Cancelar") } },
        )
    }

    if (adding) NewContactDialog(
        saving = state.saving,
        onDismiss = { adding = false },
        onCreate = { name, email, phone -> vm.create(name, listOf(email), listOf(phone)) { adding = false } },
    )
}

@Composable
private fun NewContactDialog(saving: Boolean, onDismiss: () -> Unit, onCreate: (String, String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuevo contacto") },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text("Nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(email, { email = it }, label = { Text("Correo") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(phone, { phone = it }, label = { Text("Teléfono") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = { onCreate(name, email, phone) }, enabled = !saving && name.isNotBlank()) {
                if (saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Crear")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
