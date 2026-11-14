package com.tony.explorador

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Brightness4
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PictureInPicture
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VerticalSplit
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private object SetC {
    private val d get() = XpColors.dark
    val bg get() = if (d) Color(0xFF110E1B) else Color(0xFFF4F1FB)
    val card get() = if (d) Color(0xFF1D1830) else Color.White
    val text get() = if (d) Color(0xFFEFEAFB) else Color(0xFF1F1A2E)
    val sub get() = if (d) Color(0xFFA79CC4) else Color(0xFF7D7394)
    val line get() = if (d) Color(0xFF2C2542) else Color(0xFFEDE8F7)
    val accent get() = if (d) Color(0xFFB79BFF) else Color(0xFF6A3FD0)
    val chip get() = if (d) Color(0xFF2A2342) else Color(0xFFEFE9FB)
}

/** Ajustes de la app en pantalla completa, por secciones (antes «Panel de control» en ventana). */
@Composable
fun SettingsScreen(
    versionName: String,
    onClose: () -> Unit,
    onTheme: () -> Unit,
    onAccount: () -> Unit,
    onMusicFolders: () -> Unit,
    onUpdates: () -> Unit,
    onWallpaper: () -> Unit,
) {
    BackHandler(onBack = onClose)
    var musicSkinOpen by remember { mutableStateOf(false) }
    var historyOpen by remember { mutableStateOf(false) }
    var windowSkinOpen by remember { mutableStateOf(false) }
    var aboutOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    // cada fila: sección, ícono, título, detalle, acción
    data class Item(val section: String, val title: String, val sub: String, val icon: ImageVector?, val art: Int?, val tint: Color,
                    val trailing: (@Composable () -> Unit)? = null, val action: () -> Unit)
    val darkLabel = when (LibraryStore.darkMode) { "dark" -> "Oscuro"; "light" -> "Claro"; else -> "Automático" }
    val items = listOf(
        Item("Personalización", "Fondo de pantalla", "Elige el fondo del escritorio o una foto tuya", Icons.Outlined.Wallpaper, null, Color(0xFF2E9BEA)) { onWallpaper() },
        Item("Personalización", "Apariencia", "Modo claro, oscuro o automático", Icons.Outlined.Brightness4, null, Color(0xFF6A3FD0), trailing = {
            Segmented(listOf("auto" to "Auto", "light" to "Claro", "dark" to "Oscuro"), LibraryStore.darkMode) { LibraryStore.changeDarkMode(it) }
        }) { LibraryStore.changeDarkMode(when (LibraryStore.darkMode) { "auto" -> "light"; "light" -> "dark"; else -> "auto" }) },
        Item("Personalización", "Tema de la app", "Colores y estilo del escritorio", Icons.Outlined.Palette, null, Color(0xFFE5533D)) { onTheme() },
        Item("Música", "Tema del reproductor", LibraryStore.musicSkin.label, null, R.drawable.ic_cat_music, Color(0xFFB04BD8)) { musicSkinOpen = true },
        Item("Música", "Ventana del reproductor", LibraryStore.windowSkin.label, Icons.Outlined.PictureInPicture, null, Color(0xFF8E5BE8)) { windowSkinOpen = true },
        Item("Música", "Ajustes de música", "Letra, traducción, barra de avance, listas", Icons.Outlined.Tune, null, Color(0xFFFF4F9A)) { MusicUi.settingsOpen = true },
        Item("Música", "Carpetas de música", "Dónde busca canciones la app", Icons.Outlined.LibraryMusic, null, Color(0xFF1E8E4E)) { MusicUi.openScanFolders = true; onMusicFolders() },
        Item("Explorador", "Panel de vista previa", when (LibraryStore.previewPane) { "preview" -> "Vista previa"; "details" -> "Detalles"; else -> "Apagado" },
            Icons.Outlined.VerticalSplit, null, Color(0xFF3B6FE0), trailing = {
                Segmented(listOf("" to "No", "preview" to "Vista", "details" to "Detalles"), LibraryStore.previewPane) { LibraryStore.changePreviewPane(it) }
            }) { },
        Item("Explorador", "Papelera", "Lo que borras se guarda aquí un tiempo", null, R.drawable.ic_cat_trash, Color(0xFFFF8A1F)) { onClose(); AppNav.openTrash() },
        Item("Sistema", "Buscar actualizaciones", "Versión instalada: $versionName (beta)", null, R.drawable.ic_cat_update, Color(0xFF8E5BE8)) { onUpdates() },
        Item("Sistema", "Historial de versiones", "Qué cambió en cada versión", Icons.Outlined.History, null, Color(0xFF5C6BC0)) { historyOpen = true },
        Item("Sistema", "Acerca de", "Explorador Tony", Icons.Outlined.Info, null, Color(0xFF8C93A8)) { aboutOpen = true },
    )
    val shown = if (query.isBlank()) items else items.filter {
        it.title.contains(query, true) || it.sub.contains(query, true) || it.section.contains(query, true)
    }

    Box(Modifier.fillMaxSize().background(SetC.bg).clickable(enabled = false) {}) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
            // encabezado con degradado
            item {
                Box(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xFF3B1E8C), Color(0xFF6A3FD0), SetC.bg)))
                    .statusBarsPadding().padding(bottom = 18.dp)) {
                    Column(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Atrás", tint = Color.White, modifier = Modifier.size(22.dp))
                            }
                        }
                        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Ajustes", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Black)
                                Text("Explorador Tony · v$versionName", color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp)
                            }
                            Image(painterResource(R.drawable.ic_cat_settings), null, Modifier.size(72.dp))
                        }
                        // tarjeta de la cuenta
                        Row(Modifier.padding(horizontal = 16.dp).padding(top = 16.dp).fillMaxWidth().shadow(8.dp, RoundedCornerShape(20.dp))
                            .clip(RoundedCornerShape(20.dp)).background(SetC.card).clickable(onClick = onAccount).padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            UserAvatar(52.dp)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(LibraryStore.userName, color = SetC.text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                Text("Tu nombre y tu imagen", color = SetC.sub, fontSize = 13.sp)
                            }
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = SetC.sub)
                        }
                        // buscador
                        Row(Modifier.padding(horizontal = 16.dp).padding(top = 12.dp).fillMaxWidth().height(44.dp).clip(RoundedCornerShape(14.dp))
                            .background(SetC.card).border(1.dp, SetC.line, RoundedCornerShape(14.dp)).padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Search, null, tint = SetC.sub, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Box(Modifier.weight(1f)) {
                                if (query.isEmpty()) Text("Buscar un ajuste", color = SetC.sub, fontSize = 15.sp)
                                androidx.compose.foundation.text.BasicTextField(query, { query = it }, singleLine = true,
                                    textStyle = androidx.compose.ui.text.TextStyle(color = SetC.text, fontSize = 15.sp),
                                    cursorBrush = androidx.compose.ui.graphics.SolidColor(SetC.accent), modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            }
            shown.groupBy { it.section }.forEach { (section, list) ->
                item(key = "h$section") {
                    Text(section.uppercase(), color = SetC.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp,
                        modifier = Modifier.padding(start = 28.dp, top = 18.dp, bottom = 8.dp))
                }
                item(key = "c$section") {
                    Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(SetC.card)) {
                        list.forEachIndexed { i, it ->
                            Column(Modifier.fillMaxWidth().clickable(onClick = it.action).padding(horizontal = 14.dp, vertical = 12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (it.art != null) Image(painterResource(it.art), null, Modifier.size(42.dp))
                                    else Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                                        .background(Brush.linearGradient(listOf(it.tint.copy(alpha = 0.95f), it.tint.copy(alpha = 0.7f)))),
                                        contentAlignment = Alignment.Center) {
                                        Icon(it.icon!!, null, tint = Color.White, modifier = Modifier.size(22.dp))
                                    }
                                    Spacer(Modifier.width(14.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(it.title, color = SetC.text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                                        Text(it.sub, color = SetC.sub, fontSize = 12.sp, maxLines = 2)
                                    }
                                    if (it.trailing == null) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = SetC.sub)
                                }
                                it.trailing?.let { t -> Box(Modifier.padding(start = 54.dp, top = 8.dp)) { t() } }
                            }
                            if (i < list.lastIndex) Box(Modifier.padding(start = 68.dp).fillMaxWidth().height(1.dp).background(SetC.line))
                        }
                    }
                }
            }
            item {
                Text("★ $APP_CREDITS", fontSize = 12.sp, color = SetC.sub, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp).navigationBarsPadding())
            }
        }
    }

    if (musicSkinOpen) MusicSkinDialog(onDismiss = { musicSkinOpen = false })
    if (historyOpen) VersionHistoryDialog(versionName, onDismiss = { historyOpen = false })
    if (windowSkinOpen) AlertDialog(
        onDismissRequest = { windowSkinOpen = false },
        title = { Text("Ventana del reproductor") },
        text = {
            Column {
                WindowSkin.entries.forEach { skin ->
                    Row(Modifier.fillMaxWidth().clickable { LibraryStore.changeWindowSkin(skin); windowSkinOpen = false }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = LibraryStore.windowSkin == skin, onClick = { LibraryStore.changeWindowSkin(skin); windowSkinOpen = false })
                        Text(skin.label)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { windowSkinOpen = false }) { Text("Cerrar") } }
    )
    if (aboutOpen) AlertDialog(
        onDismissRequest = { aboutOpen = false },
        icon = { Image(painterResource(R.drawable.app_logo), null, Modifier.size(72.dp)) },
        title = { Text("Explorador Tony") },
        text = { Text("Versión $versionName\nExplorador de archivos con música, fotos, videos y documentos.\nHecho a la medida para ${LibraryStore.userName}.\n\n$APP_CREDITS") },
        confirmButton = { TextButton(onClick = { aboutOpen = false }) { Text("Aceptar") } }
    )
}

/** Botones de opción juntos (como en iPhone). */
@Composable
private fun Segmented(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(12.dp)).background(SetC.chip).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        options.forEach { (id, label) ->
            val on = id == selected
            Text(label, fontSize = 13.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                color = if (on) Color.White else SetC.text,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(if (on) SetC.accent else Color.Transparent)
                    .clickable { onSelect(id) }.padding(horizontal = 12.dp, vertical = 6.dp))
        }
    }
}

@Suppress("unused") private val keepFolderIcon = Icons.Outlined.Folder
@Suppress("unused") private val keepDel = Icons.Outlined.Delete
