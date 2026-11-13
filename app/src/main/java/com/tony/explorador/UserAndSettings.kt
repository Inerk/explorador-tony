package com.tony.explorador

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Imágenes de usuario prediseñadas (como las de las cuentas de Windows). */
private data class PresetAvatar(val icon: ImageVector, val top: Color, val bottom: Color)

private val presets = listOf(
    PresetAvatar(Icons.Filled.Person, Color(0xFFFFD27A), Color(0xFFE88A1A)),
    PresetAvatar(Icons.Filled.Pets, Color(0xFFFFB38A), Color(0xFFD9541E)),
    PresetAvatar(Icons.Filled.SportsSoccer, Color(0xFFA8E68A), Color(0xFF2E8B1E)),
    PresetAvatar(Icons.Filled.LocalFlorist, Color(0xFFFFB3D9), Color(0xFFC2185B)),
    PresetAvatar(Icons.Filled.DirectionsCar, Color(0xFFFF9A8A), Color(0xFFC62828)),
    PresetAvatar(Icons.Filled.MusicNote, Color(0xFFD1B3FF), Color(0xFF6A1B9A)),
    PresetAvatar(Icons.Filled.SportsEsports, Color(0xFF9CC9FF), Color(0xFF1E5CC8)),
    PresetAvatar(Icons.Filled.Flight, Color(0xFFB3ECFF), Color(0xFF0288D1)),
    PresetAvatar(Icons.Filled.Star, Color(0xFFFFF0A0), Color(0xFFF9A825)),
    PresetAvatar(Icons.Filled.LocalCafe, Color(0xFFE0C2A8), Color(0xFF6D4C41)),
    PresetAvatar(Icons.Filled.WbSunny, Color(0xFFFFE08A), Color(0xFFFF8F00)),
)

@Composable
fun UserAvatar(size: Dp, index: Int = LibraryStore.avatar) {
    val shape = RoundedCornerShape(size * 0.14f)
    val context = LocalContext.current
    Box(
        Modifier.size(size).clip(shape).border(2.dp, Color.White, shape),
        contentAlignment = Alignment.Center
    ) {
        if (index < 0 && LibraryStore.avatarFile.isFile) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(LibraryStore.avatarFile)
                    .memoryCacheKey("avatar-${LibraryStore.avatarVersion}")
                    .diskCachePolicy(CachePolicy.DISABLED)
                    .build(),
                contentDescription = "Foto de usuario",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            val p = presets[index.coerceIn(0, presets.lastIndex)]
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(p.top, p.bottom))), contentAlignment = Alignment.Center) {
                Icon(p.icon, null, tint = Color.White, modifier = Modifier.size(size * 0.66f))
            }
        }
    }
}

/** "Cuentas de usuario": cambiar nombre e imagen. */
@Composable
fun UserAccountDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(LibraryStore.userName) }
    var selected by remember { mutableStateOf(LibraryStore.avatar) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        LibraryStore.avatarFile.outputStream().use { input.copyTo(it) }
                    }
                    true
                }.getOrDefault(false)
            }
            if (ok) { LibraryStore.changeAvatar(-1); selected = -1 }
            else Toast.makeText(context, "No se pudo usar esa imagen", Toast.LENGTH_SHORT).show()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cuentas de usuario") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    UserAvatar(64.dp, selected)
                    Spacer(Modifier.width(12.dp))
                    OutlinedTextField(value = name, onValueChange = { name = it.take(24) }, singleLine = true,
                        label = { Text("Tu nombre") })
                }
                Spacer(Modifier.height(14.dp))
                Text("Elige una imagen:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.heightIn(max = 220.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(presets) { i, _ ->
                        Box(
                            Modifier.clip(RoundedCornerShape(8.dp))
                                .border(3.dp, if (selected == i) XpColors.SelectBlue else Color.Transparent, RoundedCornerShape(8.dp))
                                .clickable { selected = i }
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) { UserAvatar(52.dp, i) }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(XpColors.SidePanel)
                        .clickable { picker.launch("image/*") }.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.AddPhotoAlternate, null, tint = XpColors.SideHeader)
                    Spacer(Modifier.width(8.dp))
                    Text("Usar una foto mía…", color = XpColors.SideHeader, fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) LibraryStore.changeUserName(name.trim())
                if (selected != LibraryStore.avatar) LibraryStore.changeAvatar(selected)
                onDismiss()
            }) { Text("Aceptar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

/** Contenido de la ventana "Panel de control". */
@Composable
fun ControlPanelContent(
    versionName: String,
    onTheme: () -> Unit,
    onAccount: () -> Unit,
    onMusicFolders: () -> Unit,
    onUpdates: () -> Unit,
    onWallpaper: () -> Unit = {},
) {
    var musicSkinOpen by remember { mutableStateOf(false) }
    var aboutOpen by remember { mutableStateOf(false) }
    var historyOpen by remember { mutableStateOf(false) }
    var windowSkinOpen by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize().background(XpColors.Paper)) {
        item {
            Box(
                Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(XpColors.SideTop, XpColors.SideBottom))).padding(12.dp)
            ) {
                Text("Elige una categoría", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
        item { PanelItem(XpIconKind.PICTURES, "Fondo de pantalla", "Elige el fondo del escritorio o usa una foto tuya") { onWallpaper() } }
        item {
            PanelItem(null, "Cuentas de usuario", "Tu nombre y tu imagen", avatar = true) { onAccount() }
        }
        item { PanelItem(XpIconKind.MUSIC, "Tema del reproductor", LibraryStore.musicSkin.label) { musicSkinOpen = true } }
        item { PanelItem(XpIconKind.COMPUTER, "Ventana del reproductor", LibraryStore.windowSkin.label) { windowSkinOpen = true } }
        item { PanelItem(XpIconKind.FOLDER, "Carpetas de música", "Dónde busca canciones la app") { onMusicFolders() } }
        item {
            val mode = LibraryStore.darkMode
            PanelItem(XpIconKind.SETTINGS, "Modo claro u oscuro", when (mode) {
                "dark" -> "Oscuro · toca para cambiar"
                "light" -> "Claro · toca para cambiar"
                else -> "Automático (según el teléfono) · toca para cambiar"
            }) { LibraryStore.changeDarkMode(when (mode) { "auto" -> "light"; "light" -> "dark"; else -> "auto" }) }
        }
        item {
            val over = LibraryStore.lyricsOverCover
            PanelItem(XpIconKind.MUSIC, "Letra en el reproductor",
                (if (over) "Encima de la carátula" else "Debajo de la carátula") + " · toca para cambiar") {
                LibraryStore.changeLyricsOverCover(!over)
            }
        }
        item { PanelItem(XpIconKind.DOWNLOADS, "Buscar actualizaciones", "Versión instalada: $versionName (beta)") { onUpdates() } }
        item { PanelItem(XpIconKind.DOCUMENTS, "Historial de versiones", "Qué cambió en cada versión") { historyOpen = true } }
        item {
            Text("★ $APP_CREDITS · v$versionName", fontSize = 12.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                color = XpColors.InkSoft, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp))
        }
        item { PanelItem(XpIconKind.COMPUTER, "Acerca de", "Explorador Tony") { aboutOpen = true } }
    }

    if (musicSkinOpen) MusicSkinDialog(onDismiss = { musicSkinOpen = false })
    if (historyOpen) VersionHistoryDialog(versionName, onDismiss = { historyOpen = false })
    if (windowSkinOpen) {
        AlertDialog(
            onDismissRequest = { windowSkinOpen = false },
            title = { Text("Ventana del reproductor") },
            text = {
                Column {
                    WindowSkin.entries.forEach { skin ->
                        Row(
                            Modifier.fillMaxWidth().clickable { LibraryStore.changeWindowSkin(skin); windowSkinOpen = false }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = LibraryStore.windowSkin == skin, onClick = { LibraryStore.changeWindowSkin(skin); windowSkinOpen = false })
                            Text(skin.label)
                        }
                    }
                    Text("También puedes cambiarla con el botón ◐ de la ventana.", fontSize = 12.sp, color = XpColors.InkSoft)
                }
            },
            confirmButton = { TextButton(onClick = { windowSkinOpen = false }) { Text("Cerrar") } }
        )
    }
    if (aboutOpen) {
        AlertDialog(
            onDismissRequest = { aboutOpen = false },
            title = { Text("Explorador Tony") },
            text = { Text("Versión $versionName\nExplorador de archivos con música, fotos y videos.\nHecho a la medida para ${LibraryStore.userName}.") },
            confirmButton = { TextButton(onClick = { aboutOpen = false }) { Text("Aceptar") } }
        )
    }
}

@Composable
private fun PanelItem(icon: XpIconKind?, title: String, subtitle: String, avatar: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (avatar) UserAvatar(40.dp) else if (icon != null) XpIcon(icon, 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = XpColors.SideHeader, fontWeight = FontWeight.Bold)
            Text(subtitle, color = XpColors.InkSoft, fontSize = 12.sp)
        }
    }
}

@Composable
fun MusicSkinDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tema del reproductor") },
        text = {
            Column {
                MusicSkin.entries.forEach { skin ->
                    Row(
                        Modifier.fillMaxWidth().clickable { LibraryStore.changeMusicSkin(skin); onDismiss() }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = LibraryStore.musicSkin == skin, onClick = { LibraryStore.changeMusicSkin(skin); onDismiss() })
                        Text(skin.label)
                    }
                }
                Text("Solo cambia colores e íconos; todo funciona igual.", fontSize = 12.sp, color = XpColors.InkSoft)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } }
    )
}
