package com.tony.explorador

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import java.io.File

private val Panel = Color(0xFF232329)
private val Dim = Color.White.copy(alpha = 0.6f)

/** Marco común de las ventanitas de búsqueda en internet. */
@Composable
private fun OnlineWindow(title: String, onDismiss: () -> Unit, onBack: (() -> Unit)?, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Column(
                Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.86f)
                    .shadow(24.dp, RoundedCornerShape(22.dp))
                    .clip(RoundedCornerShape(22.dp))
                    .background(Panel)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            ) {
                Row(Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás", tint = Color.White) }
                    else Icon(Icons.Filled.TravelExplore, null, tint = Color.White, modifier = Modifier.padding(12.dp))
                    Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "Cerrar", tint = Dim) }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) { content() }
            }
        }
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, label: String, onSearch: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White, unfocusedTextColor = Color.White,
            focusedLabelColor = Color.White, unfocusedLabelColor = Dim,
            focusedBorderColor = Color.White, unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
            cursorColor = Color.White,
        ),
        modifier = modifier,
    )
}

@Composable
private fun BigButton(text: String, accent: Color, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(14.dp))
            .background(if (enabled) accent else Color.White.copy(alpha = 0.12f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (enabled) onColor(accent) else Dim, fontWeight = FontWeight.Bold)
    }
}

private fun onColor(c: Color): Color =
    if (0.299f * c.red + 0.587f * c.green + 0.114f * c.blue > 0.6f) Color(0xFF111111) else Color.White

@Composable
private fun CenterMessage(text: String, loading: Boolean = false) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        if (loading) { CircularProgressIndicator(color = Color.White); Spacer(Modifier.height(14.dp)) }
        Text(text, color = Dim, textAlign = TextAlign.Center)
    }
}

private fun mmss(ms: Long): String = if (ms <= 0) "" else "%d:%02d".format(ms / 60000, (ms / 1000) % 60)

// ───────────────────────── Etiquetar desde internet ─────────────────────────

/** Busca la canción en internet y escribe título, artista, álbum y portada en el archivo. */
@Composable
fun TagSearchDialog(path: String, accent: Color, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf(suggestedQuery(path)) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf<List<TagResult>?>(null) }
    var picked by remember { mutableStateOf<TagResult?>(null) }
    var choice by remember { mutableStateOf(TagChoice()) }
    var saving by remember { mutableStateOf(false) }

    fun search() {
        if (query.isBlank()) return
        loading = true; error = null
        scope.launch {
            runCatching { searchSongOnline(query.trim()) }
                .onSuccess { results = it }
                .onFailure { error = "No se pudo buscar. Revisa tu conexión a internet." }
            loading = false
        }
    }
    LaunchedEffect(Unit) { search() }

    OnlineWindow(
        title = if (picked == null) "Buscar en internet" else "Etiquetar canción",
        onDismiss = { if (!saving) onDismiss() },
        onBack = if (picked != null && !saving) ({ picked = null }) else null,
    ) {
        AnimatedContent(picked, label = "tag") { r ->
            if (r == null) {
                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        SearchField(query, { query = it }, "Canción y artista", ::search, Modifier.weight(1f))
                        IconButton(onClick = ::search) { Icon(Icons.Filled.Search, "Buscar", tint = Color.White) }
                    }
                    Text("Archivo: ${File(path).name}", color = Dim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp))
                    Box(Modifier.weight(1f)) {
                        val list = results
                        when {
                            loading -> CenterMessage("Buscando…", loading = true)
                            error != null -> CenterMessage(error!!)
                            list != null && list.isEmpty() -> CenterMessage("No encontré nada.\nPrueba escribiendo solo el nombre de la canción y el artista.")
                            list != null -> LazyColumn(Modifier.fillMaxSize()) {
                                items(list) { item ->
                                    Row(
                                        Modifier.fillMaxWidth().clickable { picked = item; choice = TagChoice() }
                                            .padding(horizontal = 14.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AsyncImage(item.thumbUrl, null, contentScale = ContentScale.Crop,
                                            modifier = Modifier.size(54.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.08f)))
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(item.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(item.artist, color = Dim, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(listOfNotNull(item.album, item.year).joinToString(" · "), color = Dim, fontSize = 12.sp,
                                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                        Text(mmss(item.durationMs), color = Dim, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(r.artworkUrl, null, contentScale = ContentScale.Crop,
                            modifier = Modifier.size(130.dp).shadow(10.dp, RoundedCornerShape(10.dp)).clip(RoundedCornerShape(10.dp))
                                .background(Color.White.copy(alpha = 0.08f)))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(r.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text(r.artist, color = Color.White.copy(alpha = 0.8f))
                            Text(r.album, color = Dim, fontSize = 13.sp)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text("¿Qué quieres guardar en la canción?", color = Color.White, fontWeight = FontWeight.Bold)
                    @Composable
                    fun Opt(label: String, value: String?, checked: Boolean, set: (Boolean) -> Unit) {
                        if (value.isNullOrBlank()) return
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { set(!checked) }.padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked, set, colors = CheckboxDefaults.colors(checkedColor = accent, checkmarkColor = onColor(accent), uncheckedColor = Dim))
                            Text(label, color = Dim, fontSize = 13.sp, modifier = Modifier.width(76.dp))
                            Text(value, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        }
                    }
                    Opt("Portada", if (r.artworkUrl != null) "Imagen 600×600" else null, choice.cover) { choice = choice.copy(cover = it) }
                    Opt("Título", r.title, choice.title) { choice = choice.copy(title = it) }
                    Opt("Artista", r.artist, choice.artist) { choice = choice.copy(artist = it) }
                    Opt("Álbum", r.album, choice.album) { choice = choice.copy(album = it) }
                    Opt("Año", r.year, choice.year) { choice = choice.copy(year = it) }
                    Opt("Género", r.genre, choice.genre) { choice = choice.copy(genre = it) }
                    Opt("Pista", r.trackNumber?.let { n -> r.trackCount?.let { "$n de $it" } ?: "$n" }, choice.track) { choice = choice.copy(track = it) }
                    Spacer(Modifier.height(8.dp))
                    Text("Se guarda dentro del archivo de música, así se verá igual en otras apps.", color = Dim, fontSize = 12.sp)
                    Spacer(Modifier.height(14.dp))
                    if (saving) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(12.dp))
                            Text("Guardando etiquetas…", color = Color.White)
                        }
                    } else {
                        BigButton("Guardar en la canción", accent, modifier = Modifier.fillMaxWidth()) {
                            saving = true
                            scope.launch {
                                val res = writeTags(context, path, r, choice)
                                saving = false
                                if (res.isSuccess) {
                                    Toast.makeText(context, "Canción etiquetada ✔", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                } else {
                                    Toast.makeText(context, "No se pudo escribir en el archivo (${File(path).extension.uppercase()}): " +
                                        (res.exceptionOrNull()?.message ?: "error"), Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                }
            }
        }
    }
}

// ───────────────────────── Buscar letra ─────────────────────────

@Composable
fun LyricsSearchDialog(path: String, accent: Color, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val info by rememberTrackInfo(path)
    val song = LibraryStore.songFor(path)
    var track by remember { mutableStateOf(song?.title ?: File(path).nameWithoutExtension) }
    var artist by remember { mutableStateOf(song?.artist?.takeIf { it != UNKNOWN_ARTIST } ?: "") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf<List<LyricsResult>?>(null) }
    var picked by remember { mutableStateOf<LyricsResult?>(null) }
    var saving by remember { mutableStateOf(false) }
    var filled by remember { mutableStateOf(false) }
    val lrcPicker = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            val msg = LyricsLocal.importFrom(context, uri, path)
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            if (msg.startsWith("Letra puesta")) onDismiss()
        }
    }

    fun search() {
        if (track.isBlank() && artist.isBlank()) return
        loading = true; error = null
        scope.launch {
            runCatching { searchLyricsOnline(track.trim(), artist.trim()) }
                .onSuccess { results = it }
                .onFailure { error = "No se pudo buscar. Revisa tu conexión a internet." }
            loading = false
        }
    }
    // espera a leer las etiquetas del archivo para buscar con los datos correctos
    LaunchedEffect(info) {
        if (filled) return@LaunchedEffect
        val i = info ?: return@LaunchedEffect
        i.title?.takeIf { it.isNotBlank() }?.let { track = it }
        i.artist?.takeIf { it.isNotBlank() && it != UNKNOWN_ARTIST }?.let { artist = it }
        filled = true
        search()
    }

    OnlineWindow(
        title = if (picked == null) "Buscar letra" else "Vista previa",
        onDismiss = { if (!saving) onDismiss() },
        onBack = if (picked != null && !saving) ({ picked = null }) else null,
    ) {
        AnimatedContent(picked, label = "lyr") { r ->
            if (r == null) {
                Column(Modifier.fillMaxSize()) {
                    Column(Modifier.padding(horizontal = 14.dp)) {
                        SearchField(track, { track = it }, "Canción", ::search, Modifier.fillMaxWidth())
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SearchField(artist, { artist = it }, "Artista", ::search, Modifier.weight(1f))
                            IconButton(onClick = ::search) { Icon(Icons.Filled.Search, "Buscar", tint = Color.White) }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.weight(1f)) {
                        val list = results
                        when {
                            loading || (!filled && list == null) -> CenterMessage("Buscando letras…", loading = true)
                            error != null -> CenterMessage(error!!)
                            list != null && list.isEmpty() -> CenterMessage("No encontré la letra.\nPrueba quitando cosas como \"(En vivo)\" o \"feat.\" del nombre.")
                            list != null -> LazyColumn(Modifier.fillMaxSize()) {
                                items(list) { item ->
                                    Row(
                                        Modifier.fillMaxWidth().clickable { picked = item }.padding(horizontal = 16.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(item.track, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(listOf(item.artist, item.album).filter { it.isNotBlank() }.joinToString(" · "),
                                                color = Dim, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        Column(horizontalAlignment = Alignment.End) {
                                            val (badge, col) = when {
                                                item.synced != null -> "Sincronizada" to accent
                                                item.plain != null -> "Solo texto" to Color(0xFF9AA0A6)
                                                else -> "Instrumental" to Color(0xFF6B6B70)
                                            }
                                            Text(badge, color = col, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                                modifier = Modifier.border(1.dp, col, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
                                            Text(mmss(item.durationSec * 1000L), color = Dim, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Text("📂 Usar un archivo .lrc de mi teléfono", color = accent, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                        modifier = Modifier.align(Alignment.CenterHorizontally).clip(RoundedCornerShape(50))
                            .clickable { lrcPicker.launch("*/*") }.padding(horizontal = 14.dp, vertical = 8.dp))
                    Text("Letras de LRCLIB (lrclib.net)", color = Color.White.copy(alpha = 0.35f), fontSize = 11.sp,
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(6.dp))
                }
            } else {
                val lines = remember(r) { parseLrc(r.synced ?: r.plain ?: "") }
                Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
                    Text(r.track, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(r.artist, color = Dim)
                    Spacer(Modifier.height(10.dp))
                    Column(
                        Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(Color.Black.copy(alpha = 0.25f)).verticalScroll(rememberScrollState()).padding(14.dp)
                    ) {
                        if (lines.isEmpty()) Text("Es instrumental, no tiene letra.", color = Dim)
                        lines.forEach { l ->
                            Text(l.text.ifBlank { "♪" }, color = Color.White.copy(alpha = 0.9f), fontSize = 16.sp,
                                modifier = Modifier.padding(vertical = 3.dp))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    val exists = findLrc(File(path)) != null
                    Text(
                        (if (r.synced != null) "Se moverá al ritmo de la canción. " else "Esta letra no trae tiempos, se mostrará completa. ") +
                            (if (exists) "Reemplaza la letra que ya tenía." else "Se guarda como .lrc junto a la canción."),
                        color = Dim, fontSize = 12.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    if (saving) {
                        Row(Modifier.fillMaxWidth().padding(bottom = 18.dp), horizontalArrangement = Arrangement.Center) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                        }
                    } else {
                        BigButton("Guardar letra", accent, enabled = lines.isNotEmpty(), modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp)) {
                            saving = true
                            scope.launch {
                                val ok = saveLyricsFile(context, path, r)
                                saving = false
                                Toast.makeText(context, if (ok) "Letra guardada ✔" else "No se pudo guardar la letra", Toast.LENGTH_SHORT).show()
                                if (ok) onDismiss()
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Lo que se ve cuando una canción no tiene letra: un aviso y el botón para buscarla. */
@Composable
fun NoLyricsPrompt(path: String?, textColor: Color, accent: Color, serif: Boolean) {
    var open by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().heightIn(min = 120.dp).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Icon(Icons.Filled.MusicNote, null, tint = textColor, modifier = Modifier.size(36.dp))
        Spacer(Modifier.height(8.dp))
        Text("Esta canción no tiene letra.", color = textColor, textAlign = TextAlign.Center,
            fontFamily = if (serif) FontFamily.Serif else null, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))
        if (path != null) {
            BigButton("Buscar letra en internet", accent) { open = true }
        }
    }
    if (open && path != null) LyricsSearchDialog(path, accent, onDismiss = { open = false })
}
