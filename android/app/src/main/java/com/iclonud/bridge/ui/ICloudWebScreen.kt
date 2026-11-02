package com.iclonud.bridge.ui

import android.content.Context
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.automirrored.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.StickyNote2
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri

/**
 * Servicios de iCloud sin protocolo abierto (Notas, Fotos, Drive, Recordatorios, Buscar).
 * Se abren en iCloud.com dentro de una pestaña personalizada de Chrome: el inicio de sesión
 * ocurre en la página de Apple, la app nunca ve la contraseña y la sesión queda en el navegador.
 */
private data class Servicio(val nombre: String, val detalle: String, val ruta: String, val icono: ImageVector, val color: Color)

private val servicios = listOf(
    Servicio("Fotos", "Tu fototeca y álbumes", "photos", Icons.Filled.Photo, Color(0xFFE5484D)),
    Servicio("iCloud Drive", "Archivos y carpetas", "iclouddrive", Icons.Filled.Folder, Color(0xFF2F6FED)),
    Servicio("Recordatorios", "Listas y tareas", "reminders", Icons.AutoMirrored.Filled.PlaylistAddCheck, Color(0xFF30A46C)),
    Servicio("Buscar", "Dispositivos de Apple", "find", Icons.Filled.Search, Color(0xFF12A594)),
    Servicio("iCloud.com", "Todos los servicios", "", Icons.Filled.Cloud, Color(0xFF6E56CF)),
)

private fun abrir(ctx: Context, ruta: String) {
    CustomTabsIntent.Builder()
        .setShowTitle(true)
        .setDefaultColorSchemeParams(
            CustomTabColorSchemeParams.Builder().setToolbarColor(Color(0xFF0A6CF0).toArgb()).build()
        )
        .build()
        .launchUrl(ctx, "https://www.icloud.com/$ruta".toUri())
}

@Composable
fun ICloudWebScreen(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    LazyVerticalGrid(
        columns = GridCells.Adaptive(220.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            ElevatedCard(onClick = { abrir(ctx, "notes") }, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.StickyNote2, null, tint = Color(0xFFF5A524), modifier = Modifier.size(48.dp))
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Notas", style = MaterialTheme.typography.headlineSmall)
                        Text("Lee, crea y edita tus notas de iCloud", style = MaterialTheme.typography.bodyMedium)
                    }
                    FilledTonalButton(onClick = { abrir(ctx, "notes") }) { Text("Abrir") }
                }
            }
        }
        items(servicios) { s ->
            OutlinedCard(onClick = { abrir(ctx, s.ruta) }) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(s.icono, null, tint = s.color, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(s.nombre, style = MaterialTheme.typography.titleMedium)
                        Text(s.detalle, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                "Apple no ofrece un acceso abierto a estos servicios, así que se abren en iCloud.com, " +
                    "la web oficial de Apple. La primera vez inicia sesión en la página de Apple; " +
                    "iClonud no ve ni guarda esa contraseña.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
