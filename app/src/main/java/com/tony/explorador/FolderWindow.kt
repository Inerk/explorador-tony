package com.tony.explorador

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.util.Locale

/**
 * Mini ventana para elegir una carpeta navegando por el teléfono
 * (para copiar o mover sin salir de donde estás).
 */
@Composable
fun FolderWindow(title: String, confirm: String, start: File?, onDismiss: () -> Unit, onPick: (File) -> Unit) {
    val context = LocalContext.current
    val roots = remember { storageRoots(context) }
    var dir by remember { mutableStateOf(start?.takeIf { it.isDirectory }) }
    var refresh by remember { mutableIntStateOf(0) }
    var newName by remember { mutableStateOf<String?>(null) }
    val subdirs = remember(dir, refresh) {
        dir?.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") }?.sortedBy { it.name.lowercase(Locale.ROOT) } ?: emptyList()
    }
    val d = dir
    ThemedDialog(title, onDismiss, listOf(
        DialogButton("Cancelar", onClick = onDismiss),
        DialogButton(confirm, true) { if (d != null) onPick(d) },
    )) {
        Column {
            // dónde estás
            val rootOf = d?.let { cur -> roots.firstOrNull { cur.absolutePath.startsWith(it.file.absolutePath) } }
            Text(
                if (d == null) "Elige un almacenamiento" else (rootOf?.name ?: "") + d.absolutePath.removePrefix(rootOf?.file?.absolutePath ?: ""),
                fontSize = 12.sp, color = XpColors.InkSoft, maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            LazyColumn(Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 340.dp)) {
                if (d == null) {
                    items(roots) { r -> Line(r.name, Icons.Filled.Storage, Color(0xFF7A4FE0)) { dir = r.file } }
                } else {
                    item {
                        Line("Subir", Icons.Filled.ArrowUpward, XpColors.InkSoft) {
                            dir = if (roots.any { it.file.absolutePath == d.absolutePath }) null else d.parentFile
                        }
                    }
                    items(subdirs, key = { it.absolutePath }) { f -> Line(f.name, Icons.Filled.Folder, Color(0xFFF5B800)) { dir = f } }
                    if (subdirs.isEmpty()) item { Text("No hay más carpetas aquí", fontSize = 13.sp, color = XpColors.InkSoft, modifier = Modifier.padding(vertical = 10.dp)) }
                }
            }
            if (d != null) {
                val n = newName
                if (n == null) Line("Nueva carpeta aquí…", Icons.Filled.CreateNewFolder, Color(0xFF2E7D32)) { newName = "" }
                else Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(n, { newName = it }, label = { Text("Nombre de la carpeta") }, singleLine = true, modifier = Modifier.weight(1f))
                    TextButton(enabled = n.isNotBlank(), onClick = {
                        val f = File(d, n.trim())
                        if (f.mkdirs() || f.isDirectory) { dir = f }
                        newName = null; refresh++
                    }) { Text("Crear") }
                }
            }
        }
    }
}

@Composable
private fun Line(label: String, icon: ImageVector, tint: Color, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
    }
}
