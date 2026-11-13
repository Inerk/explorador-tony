package com.tony.explorador

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Forward5
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay5
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.Player
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

/** Cosas de la música que se abren desde cualquier pantalla. */
object MusicUi {
    /** Canción cuya letra se está sincronizando (null = editor cerrado). */
    var syncLyricsFor by mutableStateOf<String?>(null)
    /** Ajustes de música abiertos. */
    var settingsOpen by mutableStateOf(false)
    /** Abrir la página de carpetas donde se busca música (desde Ajustes). */
    var openScanFolders by mutableStateOf(false)
    /** La ventana flotante de letra se escondió (vuelve con la siguiente canción). */
    var floatHiddenFor by mutableStateOf<String?>(null)
    var floatOffset by mutableStateOf(Offset.Zero)
}

private val Bg = Color(0xFF15121F)
private val Card = Color(0xFF221D33)
private val Accent = Color(0xFFD64FC4)
private val Soft = Color.White.copy(alpha = 0.6f)

private fun lrcTime(ms: Long): String {
    if (ms < 0) return "--:--.--"
    val m = ms / 60000; val s = (ms / 1000) % 60; val cs = (ms % 1000) / 10
    return String.format(Locale.US, "%02d:%02d.%02d", m, s, cs)
}

private class EditLine(time: Long, text: String) {
    var time by mutableStateOf(time)
    var text by mutableStateOf(text)
}

/**
 * Sincronizador de letras: marca el tiempo de cada línea mientras suena la canción,
 * ajústalo fino (±0.1 s, ±0.5 s), mueve toda la letra a la vez, edita, agrega o borra líneas.
 */
@Composable
fun LyricsSyncEditor(path: String, player: Player, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val file = File(path)
    val lrcFile = remember { findLrc(file) }
    val original = remember { lrcFile?.let { runCatching { readLrcText(it) }.getOrNull() }.orEmpty() }
    val lines = remember {
        mutableStateListOf<EditLine>().apply { addAll(parseLrc(original).map { EditLine(it.timeMs, it.text) }) }
    }
    var selected by remember { mutableIntStateOf(lines.indexOfFirst { it.time < 0 }.coerceAtLeast(0)) }
    var changed by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Int?>(null) }
    var pasting by remember { mutableStateOf(lines.isEmpty()) }
    var confirmClose by remember { mutableStateOf(false) }
    var speed by remember { mutableStateOf(1f) }
    val tick = observePlayer(player, 100)
    val isThisSong = player.currentMediaItem?.mediaId == path
    val pos = if (isThisSong) player.currentPosition + tick * 0L else 0L
    val playingIdx = lines.indexOfLast { it.time in 0..pos }
    val listState = rememberLazyListState()
    LaunchedEffect(selected) { if (selected in lines.indices) listState.animateScrollToItem((selected - 2).coerceAtLeast(0)) }
    fun close() { player.setPlaybackSpeed(1f); onClose() }
    BackHandler { if (changed) confirmClose = true else close() }

    fun save() {
        scope.launch {
            // tiempos en orden; las líneas sin tiempo toman el de la anterior
            var last = 0L
            val body = lines.joinToString("\n") { l ->
                val t = if (l.time >= 0) l.time.also { last = it } else last
                "[${lrcTime(t)}]${l.text}"
            }
            val header = original.lineSequence().map { it.trim() }
                .filter { Regex("""^\[(ti|ar|al|au|by|re|ve|length):.*]$""", RegexOption.IGNORE_CASE).matches(it) }
                .toList()
                .ifEmpty { listOfNotNull(LibraryStore.songFor(path)?.let { "[ti:${it.title}]" }, LibraryStore.songFor(path)?.artist?.let { "[ar:$it]" }) }
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val dest = lrcFile ?: File(file.parentFile, file.nameWithoutExtension + ".lrc")
                    // la versión anterior se guarda en la Papelera, por si acaso
                    if (dest.exists()) {
                        val backup = File(context.cacheDir, dest.nameWithoutExtension + " (antes).lrc")
                        dest.copyTo(backup, overwrite = true)
                        Trash.moveToTrash(listOf(backup))
                    }
                    dest.writeText((header + "[re:Explorador Tony]" + body).joinToString("\n"))
                    true
                }.getOrDefault(false)
            }
            if (ok) {
                invalidateTrack(path)
                LibraryStore.bumpTags()
                changed = false
                Toast.makeText(context, "Letra guardada ✔", Toast.LENGTH_SHORT).show()
            } else Toast.makeText(context, "No se pudo guardar la letra", Toast.LENGTH_SHORT).show()
        }
    }

    Dialog(onDismissRequest = { if (changed) confirmClose = true else close() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(Bg).statusBarsPadding().navigationBarsPadding()) {
            // encabezado
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Sincronizar letra", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Text(LibraryStore.songFor(path)?.title ?: file.nameWithoutExtension, color = Soft, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                TextButton(onClick = { pasting = true }) { Text("Pegar letra", color = Soft) }
                Text("Guardar", color = Color.Black, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(CircleShape).background(if (changed) Accent else Color.White.copy(alpha = 0.5f))
                        .clickable { save() }.padding(horizontal = 16.dp, vertical = 8.dp))
                IconButton(onClick = { if (changed) confirmClose = true else close() }) { Icon(Icons.Filled.Close, "Cerrar", tint = Color.White) }
            }
            if (!isThisSong) Text("Para sincronizar, pon a sonar esta canción.", color = Color(0xFFFFC83D), fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))

            // reproductor
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).clip(RoundedCornerShape(16.dp)).background(Card).padding(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(lrcTime(pos), color = Color.White, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 20.sp,
                    modifier = Modifier.padding(horizontal = 8.dp))
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { player.seekTo((player.currentPosition - 5000).coerceAtLeast(0)) }) { Icon(Icons.Filled.Replay5, "−5 s", tint = Color.White) }
                Box(Modifier.size(48.dp).clip(CircleShape).background(Accent).clickable { if (player.isPlaying) player.pause() else player.play() },
                    contentAlignment = Alignment.Center) {
                    Icon(if (player.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Reproducir", tint = Color.White)
                }
                IconButton(onClick = { player.seekTo(player.currentPosition + 5000) }) { Icon(Icons.Filled.Forward5, "+5 s", tint = Color.White) }
                Text("${if (speed == 1f) "1" else speed.toString()}x", color = Color.White, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(CircleShape).border(1.dp, Soft, CircleShape)
                        .clickable { speed = when (speed) { 1f -> 0.75f; 0.75f -> 0.5f; else -> 1f }; player.setPlaybackSpeed(speed) }
                        .padding(horizontal = 10.dp, vertical = 6.dp))
            }
            // mover toda la letra
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Toda la letra:", color = Soft, fontSize = 13.sp, modifier = Modifier.weight(1f))
                listOf(-500L, -100L, 100L, 500L).forEach { d ->
                    Chip((if (d > 0) "+" else "−") + "${kotlin.math.abs(d) / 1000.0}s") {
                        lines.forEach { if (it.time >= 0) it.time = (it.time + d).coerceAtLeast(0) }; changed = true
                    }
                }
            }
            // líneas
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)) {
                itemsIndexed(lines) { i, l ->
                    val sel = i == selected
                    val now = i == playingIdx
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp).clip(RoundedCornerShape(12.dp))
                            .background(if (sel) Accent.copy(alpha = 0.22f) else Color.Transparent)
                            .border(if (sel) 1.dp else 0.dp, if (sel) Accent else Color.Transparent, RoundedCornerShape(12.dp))
                            .clickable { selected = i }.padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(lrcTime(l.time), color = if (l.time < 0) Color(0xFFFF8A80) else Soft, fontFamily = FontFamily.Monospace, fontSize = 13.sp,
                            modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(enabled = l.time >= 0 && isThisSong) {
                                selected = i; player.seekTo(l.time); player.play()
                            }.padding(4.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(l.text.ifBlank { "♪" }, color = if (now) Accent else Color.White, fontWeight = if (now || sel) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 16.sp, modifier = Modifier.weight(1f))
                    }
                }
            }
            // controles de la línea elegida
            val l = lines.getOrNull(selected)
            Column(Modifier.fillMaxWidth().background(Card).padding(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Línea ${selected + 1}:", color = Soft, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    listOf(-500L, -100L, 100L, 500L).forEach { d ->
                        Chip((if (d > 0) "+" else "−") + "${kotlin.math.abs(d) / 1000.0}s") {
                            l?.let { it.time = ((if (it.time < 0) pos else it.time) + d).coerceAtLeast(0); changed = true
                                if (isThisSong) player.seekTo((it.time - 1500).coerceAtLeast(0)) }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                    IconButton(onClick = { if (l != null) editing = selected }) { Icon(Icons.Filled.Edit, "Editar texto", tint = Color.White) }
                    IconButton(onClick = { lines.add((selected + 1).coerceAtMost(lines.size), EditLine(-1, "")); selected++; editing = selected; changed = true }) {
                        Icon(Icons.Filled.Add, "Agregar línea", tint = Color.White) }
                    IconButton(onClick = { if (l != null) { lines.removeAt(selected); selected = selected.coerceAtMost(lines.lastIndex).coerceAtLeast(0); changed = true } }) {
                        Icon(Icons.Filled.Delete, "Borrar línea", tint = Color(0xFFFF8A80)) }
                    Spacer(Modifier.weight(1f))
                    // botón grande: pone el tiempo de ahora a la línea y pasa a la siguiente
                    Text("MARCAR ⏎", color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp,
                        modifier = Modifier.shadow(8.dp, CircleShape).clip(CircleShape)
                            .background(Brush.horizontalGradient(listOf(Accent, Color(0xFF7A4FE0))))
                            .clickable(enabled = l != null && isThisSong) {
                                l?.time = player.currentPosition
                                changed = true
                                if (selected < lines.lastIndex) selected++
                            }.padding(horizontal = 26.dp, vertical = 14.dp))
                }
                Text("Pon play y toca MARCAR justo cuando empiece cada línea. Luego afina con ±0.1 s; toca el tiempo de una línea para escucharla.",
                    color = Soft, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }

    editing?.let { i ->
        var text by remember(i) { mutableStateOf(lines.getOrNull(i)?.text.orEmpty()) }
        ThemedDialog("Texto de la línea ${i + 1}", { editing = null }, listOf(
            DialogButton("Cancelar") { editing = null },
            DialogButton("Listo", true) { lines.getOrNull(i)?.text = text; changed = true; editing = null })) {
            OutlinedTextField(text, { text = it }, modifier = Modifier.fillMaxWidth())
        }
    }
    if (pasting) {
        var text by remember { mutableStateOf(lines.joinToString("\n") { it.text }) }
        ThemedDialog("Pegar o escribir la letra", { pasting = false }, listOf(
            DialogButton("Cancelar") { pasting = false },
            DialogButton("Usar", true) {
                // conserva los tiempos de las líneas que siguen igual
                val old = lines.toList()
                lines.clear()
                text.lines().map { it.trim() }.filter { it.isNotEmpty() }.forEachIndexed { idx, t ->
                    lines.add(EditLine(old.getOrNull(idx)?.takeIf { it.text == t }?.time ?: -1, t))
                }
                selected = lines.indexOfFirst { it.time < 0 }.coerceAtLeast(0)
                changed = true; pasting = false
            })) {
            Column {
                Text("Una línea por renglón. Después las vas marcando con la canción.", fontSize = 12.sp)
                OutlinedTextField(text, { text = it }, modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 320.dp))
            }
        }
    }
    if (confirmClose) ThemedDialog("¿Salir sin guardar?", { confirmClose = false }, listOf(
        DialogButton("Seguir editando") { confirmClose = false },
        DialogButton("Salir", true) { confirmClose = false; close() })) {
        Text("Los cambios a la letra se van a perder.")
    }
}

@Composable
private fun Chip(label: String, onClick: () -> Unit) {
    Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 6.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f))
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 7.dp))
}

/** Ajustes de la app de música (se abren desde el engrane de Música o del reproductor). */
@Composable
fun MusicSettingsDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxWidth(0.92f).widthIn(max = 460.dp).heightIn(max = 640.dp).clip(RoundedCornerShape(24.dp)).background(Bg)
                .verticalScroll(rememberScrollState()).padding(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Ajustes de música", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "Cerrar", tint = Color.White) }
            }
            Section("Letra en el reproductor")
            listOf(
                "below" to "Debajo de la carátula",
                "big" to "Debajo de la carátula, grande",
                "over" to "Encima de la carátula",
                "float" to "En una ventana flotante",
            ).forEach { (id, label) -> Radio(label, LibraryStore.lyricsPlace == id) { LibraryStore.changeLyricsPlace(id) } }
            Row(Modifier.fillMaxWidth().clickable { LibraryStore.changeLyricsTranslate(!LibraryStore.lyricsTranslate) }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Traducir letras al español", color = Color.White)
                    Text("Si la canción está en otro idioma, sale la traducción debajo de cada línea. Se traduce en el teléfono; la primera vez de cada idioma baja un paquete (~30 MB).",
                        color = Soft, fontSize = 11.sp)
                }
                Switch(LibraryStore.lyricsTranslate, { LibraryStore.changeLyricsTranslate(it) }, colors = SwitchDefaults.colors(checkedTrackColor = Accent))
            }
            if (LibraryStore.lyricsTranslate) {
                Text("Resaltar", color = Soft, fontSize = 13.sp, modifier = Modifier.padding(start = 12.dp, top = 4.dp))
                Radio("La letra original (traducción abajo, chiquita)", LibraryStore.lyricsHighlight == "original") { LibraryStore.changeLyricsHighlight("original") }
                Radio("La traducción (original abajo, chiquita)", LibraryStore.lyricsHighlight == "translation") { LibraryStore.changeLyricsHighlight("translation") }
            }
            Text("La ventana flotante se ve en toda la app y la puedes arrastrar; con la ✕ se esconde hasta la siguiente canción.",
                color = Soft, fontSize = 11.sp, modifier = Modifier.padding(start = 12.dp, bottom = 4.dp))

            Section("Listas en la Biblioteca")
            Text("Escoge cuáles se ven en el menú.", color = Soft, fontSize = 12.sp)
            LibraryListNames.forEach { (id, label) ->
                Row(Modifier.fillMaxWidth().clickable { LibraryStore.toggleListVisible(id) }.padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(id !in LibraryStore.hiddenLists, { LibraryStore.toggleListVisible(id) },
                        colors = androidx.compose.material3.CheckboxDefaults.colors(checkedColor = Accent, uncheckedColor = Soft))
                    Text(label, color = Color.White, fontSize = 15.sp)
                }
            }
            Text("Carpetas donde buscar música", color = Color.White, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Card)
                    .clickable { MusicUi.openScanFolders = true; onDismiss() }.padding(14.dp))

            Section("Barra de avance")
            // vista previa en vivo (se mueve con la música si algo está sonando)
            Box(Modifier.fillMaxWidth().padding(top = 26.dp, bottom = 4.dp).clip(RoundedCornerShape(14.dp)).background(Card).padding(horizontal = 12.dp)) {
                WaveSeekBar(progress = 0.62f, playing = true, accent = Accent, onSeekChange = {}, onSeekEnd = {},
                    style = LibraryStore.seekStyle, interactive = false)
            }
            SeekStyles.forEach { (id, label) -> Radio(label, LibraryStore.seekStyle == id) { LibraryStore.changeSeekStyle(id) } }
            Text("Se mueve al ritmo de la música que está sonando.", color = Soft, fontSize = 11.sp, modifier = Modifier.padding(start = 12.dp))

            Section("Portada")
            Row(Modifier.fillMaxWidth().clickable { MotionPref.on = !MotionPref.on }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Mostrar video o carátula animada", color = Color.White, modifier = Modifier.weight(1f))
                Switch(MotionPref.on, { MotionPref.on = it }, colors = SwitchDefaults.colors(checkedTrackColor = Accent))
            }

            Section("Tema del reproductor")
            MusicSkin.entries.forEach { sk -> Radio(sk.label, LibraryStore.musicSkin == sk) { LibraryStore.changeMusicSkin(sk) } }

            Section("Letras")
            Text("Para ajustar los tiempos de una letra: en el reproductor toca ⋮ → «Sincronizar letra». Toca la carátula para ver la letra completa y desliza la carátula hacia abajo para ver la lista.", color = Soft, fontSize = 13.sp)
        }
    }
}

@Composable
private fun Section(t: String) {
    Text(t, color = Accent, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
}

@Composable
private fun Radio(label: String, on: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically) {
        RadioButton(on, onClick, colors = RadioButtonDefaults.colors(selectedColor = Accent, unselectedColor = Soft))
        Text(label, color = Color.White, fontSize = 15.sp)
    }
}

/** Letra en ventana flotante, encima de toda la app. Se arrastra; ✕ la esconde hasta la siguiente canción. */
@Composable
fun FloatingLyricsWindow(player: Player, modifier: Modifier = Modifier) {
    val tick = observePlayer(player, 250)
    val path = player.currentMediaItem?.mediaId ?: return
    if (MusicUi.floatHiddenFor == path) return
    val info by rememberTrackInfo(path)
    val lyrics = info?.lyrics.orEmpty()
    val translation by rememberLyricsTranslation(lyrics)
    if (lyrics.isEmpty() || player.mediaItemCount == 0) return
    val pos = player.currentPosition + tick * 0L
    val idx = currentLyricIndex(lyrics, pos)
    val accent = (info?.colors ?: DefaultCoverColors).accent
    val synced = lyrics.first().timeMs >= 0
    Box(
        modifier.offset { IntOffset(MusicUi.floatOffset.x.roundToInt(), MusicUi.floatOffset.y.roundToInt()) }
            .padding(12.dp).fillMaxWidth(0.9f)
            .shadow(16.dp, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp))
            .background(Brush.verticalGradient(listOf(Color(0xE61B1538), Color(0xE60B0820))))
            .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
            .pointerInput(Unit) { detectDragGestures { c, d -> c.consume(); MusicUi.floatOffset += d } }
            .padding(start = 16.dp, end = 36.dp, top = 12.dp, bottom = 12.dp)
    ) {
        Column {
            if (synced) {
                val orig = lyrics.getOrNull(idx)?.text?.ifBlank { "♪" } ?: "♪"
                val trl = translation?.getOrNull(idx)?.takeIf { it.isNotBlank() && !it.equals(lyrics.getOrNull(idx)?.text?.trim(), true) }
                val swap = LibraryStore.lyricsHighlight == "translation" && trl != null
                Text(if (swap) trl!! else orig, color = accent, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                    lineHeight = 25.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                (if (swap) orig else trl)?.let {
                    Text(it, color = Color.White.copy(alpha = 0.85f), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                lyrics.getOrNull(idx + 1)?.let {
                    Text(it.text.ifBlank { "♪" }, color = Color.White.copy(alpha = 0.6f), fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            } else Text("Esta letra no tiene tiempos. Ajústala con ⋮ → «Sincronizar letra».", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
        }
        Box(Modifier.align(Alignment.TopEnd).offset(x = 28.dp, y = (-6).dp).size(28.dp).clip(CircleShape)
            .clickable { MusicUi.floatHiddenFor = path }, contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Close, "Esconder", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
        }
    }
}

@Suppress("unused")
private val keepAlign = TextAlign.Center
