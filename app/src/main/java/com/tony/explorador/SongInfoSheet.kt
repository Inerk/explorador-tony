package com.tony.explorador

import android.os.Environment
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

private val SheetBg = Color(0xFF232323)
private val Label = Color.White.copy(alpha = 0.55f)

/** "primary/Music/Álbum/Canción.mp3" como en otros reproductores. */
private fun shortPath(path: String): String {
    val internal = Environment.getExternalStorageDirectory().absolutePath
    return when {
        path.startsWith("$internal/") -> "primary/" + path.removePrefix("$internal/")
        path.startsWith("/storage/") -> path.removePrefix("/storage/")
        else -> path
    }
}

private fun channelsText(ch: String): String = when {
    ch.contains("stereo", true) || ch.trim() == "2" -> "Estéreo"
    ch.contains("mono", true) || ch.trim() == "1" -> "Mono"
    ch.isBlank() -> ""
    else -> "$ch canales"
}

/**
 * Información y etiquetas de la canción, en hoja oscura que sube desde abajo.
 * Se cierra tocando fuera, con atrás o deslizándola hacia abajo.
 */
@Composable
fun SongInfoWindow(
    path: String,
    accent: Color = Color(0xFF9FB4FF),
    onDismiss: () -> Unit,
    onGoTo: ((String, String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var reload by remember { mutableIntStateOf(0) }
    val tags by produceState<FullTags?>(null, path, reload, LibraryStore.tagsVersion) { value = readFullTags(path) }
    val plays = LibraryStore.playCounts[path] ?: 0
    val last = LibraryStore.lastPlayed[path]
    var editing by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    val drag = remember { Animatable(0f) }
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)) }
    val close: () -> Unit = { scope.launch { appear.animateTo(0f, tween(160)); onDismiss() } }
    val file = File(path)
    fun go(kind: String, value: String) { if (onGoTo != null) { onDismiss(); onGoTo(kind, value) } }

    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(
            Modifier.fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f * appear.value))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = close),
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(
                Modifier.fillMaxWidth().fillMaxHeight(0.92f)
                    .graphicsLayer { translationY = drag.value + (1f - appear.value) * size.height }
                    .shadow(24.dp, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(SheetBg)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .navigationBarsPadding()
            ) {
                // encabezado: se arrastra para cerrar
                Column(
                    Modifier.fillMaxWidth().pointerInput(Unit) {
                        detectVerticalDragGestures(onDragEnd = {
                            scope.launch {
                                if (drag.value > 180f) { drag.animateTo(2400f, tween(180)); onDismiss() }
                                else drag.animateTo(0f, spring(dampingRatio = 0.65f))
                            }
                        }) { c, d -> c.consume(); scope.launch { drag.snapTo((drag.value + d).coerceAtLeast(0f)) } }
                    }
                ) {
                    Box(Modifier.fillMaxWidth().padding(top = 10.dp), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(40.dp, 5.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.25f)))
                    }
                    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Info/Etiquetas", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        if (onGoTo != null) {
                            Box(
                                Modifier.size(58.dp, 40.dp).clip(CircleShape).border(1.5.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                                    .clickable { go("folder", file.parent ?: "/") },
                                contentAlignment = Alignment.Center
                            ) { Icon(Icons.Filled.Folder, "Abrir carpeta", tint = Color.White) }
                        }
                    }
                }

                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                    Spacer(Modifier.height(10.dp))
                    // ruta: cada carpeta se puede tocar
                    PathLinks(shortPath(path), path, enabled = onGoTo != null) { go("folder", it) }
                    Spacer(Modifier.height(18.dp))

                    val t = tags
                    if (t == null) {
                        LinearProgressIndicator(Modifier.fillMaxWidth(), color = accent)
                        return@Column
                    }
                    // datos técnicos
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(file.extension.lowercase(Locale.ROOT), color = Color.White, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Black,
                            fontSize = 15.sp, modifier = Modifier.border(1.5.dp, Color.White, RoundedCornerShape(50)).padding(horizontal = 6.dp))
                        Spacer(Modifier.width(14.dp))
                        val parts = buildList {
                            if (t.seconds > 0) add("${t.seconds} seg (${formatTime(t.seconds * 1000L)})")
                            if (t.sampleRate > 0) add(String.format(Locale.US, "%.1f kHz", t.sampleRate / 1000.0))
                            if (t.bits > 0) add("${t.bits} bit")
                            channelsText(t.channels).takeIf { it.isNotBlank() }?.let { add(it) }
                            if (t.kbps > 0) add("${t.kbps} kbps")
                            add("${file.length() / 1024}Kb")
                        }
                        Text(parts.joinToString(", "), color = Color.White.copy(alpha = 0.85f), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                    Spacer(Modifier.height(20.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Field("Veces reproducida", "$plays", Modifier.weight(1f))
                        Field("Última reproducción", last?.let { relativeTime(it) } ?: "Nunca", Modifier.weight(1f))
                        if (plays > 0 || last != null) {
                            Box(Modifier.size(40.dp).clip(CircleShape).clickable { LibraryStore.resetStats(path) }, contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Close, "Borrar conteo", tint = Color.White, modifier = Modifier.size(28.dp))
                            }
                        }
                    }
                    Field("Título", t.title.ifBlank { file.nameWithoutExtension })
                    Row {
                        Field("Pista", t.track, Modifier.weight(1f))
                        Field("Disco", t.disc, Modifier.weight(1f))
                        Field("Año", t.year, Modifier.weight(1f))
                    }
                    Field("Género", t.genre)
                    Field("Artista", t.artist, link = onGoTo != null && t.artist.isNotBlank()) {
                        go("artist", LibraryStore.songFor(path)?.artist ?: t.artist)
                    }
                    Field("Álbum", t.album, link = onGoTo != null && t.album.isNotBlank()) {
                        go("album", LibraryStore.songFor(path)?.album ?: t.album)
                    }
                    Field("Artista del álbum", t.albumArtist, link = onGoTo != null && t.albumArtist.isNotBlank()) { go("artist", t.albumArtist) }
                    Field("Compositor", t.composer)
                    val folderArt = remember(path) { folderCoverFile(file) }
                    Field("Carátula", when {
                        t.artBytes > 0 -> "${t.artWidth}x${t.artHeight} ${t.artMime.substringAfter('/').uppercase(Locale.ROOT).ifBlank { "Imagen" }} Carátula incluida (${t.artBytes} bytes)"
                        folderArt != null -> "De la carpeta (${folderArt.name})"
                        else -> "Sin carátula"
                    })
                    AnimatedCovers.find(path, LibraryStore.songFor(path)?.album)?.let { Field("Carátula animada", it.name) }
                    Field("Letra", findLrc(file)?.name ?: "Sin letra")
                    Field("Comentario", t.comment)
                    Spacer(Modifier.height(20.dp))
                }
                // botones de abajo
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.weight(0.4f))
                    Text("Buscar", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp,
                        modifier = Modifier.clip(CircleShape).clickable { searching = true }.padding(horizontal = 22.dp, vertical = 14.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Editar etiquetas", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp,
                        modifier = Modifier.weight(1f).clip(CircleShape).background(Color.White.copy(alpha = 0.18f))
                            .clickable { editing = true }
                            .padding(vertical = 14.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        }
    }

    if (searching) TagSearchDialog(path, accent, onDismiss = { searching = false; reload++ })
    val t = tags
    if (editing && t != null) TagEditorDialog(path, t, accent, onDismiss = { editing = false }, onSaved = { reload++ })
}

@Composable
private fun Field(label: String, value: String, modifier: Modifier = Modifier, link: Boolean = false, onClick: () -> Unit = {}) {
    Column(modifier.padding(top = 18.dp)) {
        Text(label, color = Label, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            value.ifBlank { " " }, color = Color.White, fontSize = 18.sp,
            textDecoration = if (link) TextDecoration.Underline else null,
            modifier = if (link) Modifier.clip(RoundedCornerShape(4.dp)).clickable(onClick = onClick) else Modifier
        )
    }
}

/** La ruta con cada carpeta subrayada (tocarla abre esa carpeta). */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun PathLinks(shown: String, real: String, enabled: Boolean, onFolder: (String) -> Unit) {
    val shownParts = shown.split('/')
    val realParts = real.split('/')
    val offset = realParts.size - shownParts.size   // cuántas partes reales se "comió" el prefijo
    androidx.compose.foundation.layout.FlowRow {
        shownParts.forEachIndexed { i, part ->
            val isFile = i == shownParts.lastIndex
            val realFolder = realParts.take((i + offset + 1).coerceAtLeast(1)).joinToString("/")
            Text(
                part + if (isFile) "" else "/",
                color = Color.White, fontSize = 18.sp,
                textDecoration = if (!isFile && enabled && i > 0) TextDecoration.Underline else null,
                modifier = if (!isFile && enabled && i > 0) Modifier.clickable { onFolder(realFolder) } else Modifier
            )
        }
    }
}

/** Editar a mano las etiquetas de la canción. */
@Composable
fun TagEditorDialog(path: String, initial: FullTags, accent: Color, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var t by remember { mutableStateOf(initial.copy(title = initial.title.ifBlank { File(path).nameWithoutExtension })) }
    var saving by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = { if (!saving) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.88f).clip(RoundedCornerShape(24.dp)).background(SheetBg).padding(18.dp)
        ) {
            Text("Editar etiquetas", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(File(path).name, color = Label, fontSize = 13.sp, maxLines = 1)
            Spacer(Modifier.height(8.dp))
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                @Composable
                fun F(label: String, value: String, number: Boolean = false, modifier: Modifier = Modifier.fillMaxWidth(), set: (String) -> Unit) {
                    OutlinedTextField(
                        value, set, label = { Text(label) }, singleLine = true, modifier = modifier,
                        keyboardOptions = if (number) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                            focusedBorderColor = accent, unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
                            focusedLabelColor = accent, unfocusedLabelColor = Label, cursorColor = accent,
                        )
                    )
                }
                F("Título", t.title) { t = t.copy(title = it) }
                F("Artista", t.artist) { t = t.copy(artist = it) }
                F("Álbum", t.album) { t = t.copy(album = it) }
                F("Artista del álbum", t.albumArtist) { t = t.copy(albumArtist = it) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    F("Pista", t.track, true, Modifier.weight(1f)) { t = t.copy(track = it) }
                    F("Disco", t.disc, true, Modifier.weight(1f)) { t = t.copy(disc = it) }
                    F("Año", t.year, true, Modifier.weight(1f)) { t = t.copy(year = it) }
                }
                F("Género", t.genre) { t = t.copy(genre = it) }
                F("Compositor", t.composer) { t = t.copy(composer = it) }
                F("Comentario", t.comment) { t = t.copy(comment = it) }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                if (saving) CircularProgressIndicator(Modifier.size(26.dp), color = accent)
                else {
                    Text("Cancelar", color = Color.White, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(CircleShape).clickable(onClick = onDismiss).padding(horizontal = 18.dp, vertical = 12.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Guardar", color = Color.Black, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(CircleShape).background(accent).clickable {
                            saving = true
                            scope.launch {
                                val r = writeManualTags(context, path, t)
                                saving = false
                                Toast.makeText(context, if (r.isSuccess) "Etiquetas guardadas ✔"
                                    else "No se pudo guardar: ${r.exceptionOrNull()?.message ?: "error"}", Toast.LENGTH_LONG).show()
                                if (r.isSuccess) { onSaved(); onDismiss() }
                            }
                        }.padding(horizontal = 22.dp, vertical = 12.dp))
                }
            }
        }
    }
}
