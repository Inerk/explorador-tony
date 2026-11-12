package com.tony.explorador

import android.os.Environment
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

/** Colores de la sección de música; cambian con el tema elegido (Walkman o Media Player). */
private object Retro {
    private val wmp get() = LibraryStore.musicSkin == MusicSkin.MEDIA_PLAYER
    val isWmp get() = wmp
    val Bg get() = if (wmp) Color(0xFF0B1630) else Color(0xFF111114)
    val Panel get() = if (wmp) Color(0xFF14284F) else Color(0xFF1C1C22)
    val Orange get() = if (wmp) Color(0xFF3FA9F5) else Color(0xFFFF7A00)
    val OrangeLight get() = if (wmp) Color(0xFF9CDBFF) else Color(0xFFFFA347)
    val OnAccent get() = if (wmp) Color.White else Color.Black
    val Text get() = Color(0xFFF2F2F2)
    val SubText get() = if (wmp) Color(0xFF8FA6CC) else Color(0xFF9A9AA5)
    val HeaderText get() = if (wmp) Color.White else Color(0xFF1E1E24)
    val HeaderIcon get() = if (wmp) Color(0xFFCFE3FF) else Color(0xFF2B2B33)
    val metal: Brush get() = if (wmp)
        Brush.verticalGradient(listOf(Color(0xFF4F76B5), Color(0xFF1C3463), Color(0xFF0A1630)))
    else Brush.verticalGradient(listOf(Color(0xFFE9EBEF), Color(0xFFB9BDC6), Color(0xFF8C919C)))
    // colores de cada sección
    val Red get() = if (wmp) Color(0xFF5B8DEF) else Color(0xFFE53935)
    val Blue get() = if (wmp) Color(0xFF2D7DD2) else Color(0xFF1E88E5)
    val Lime get() = if (wmp) Color(0xFF2FBF9A) else Color(0xFF7CB342)
    val Pink get() = if (wmp) Color(0xFF8E6CEF) else Color(0xFFD81B60)
    val Yellow get() = if (wmp) Color(0xFFF2C94C) else Color(0xFFFDD835)
    val Teal get() = if (wmp) Color(0xFF2EC4E6) else Color(0xFF00ACC1)
    val Purple get() = if (wmp) Color(0xFF7B61FF) else Color(0xFF8E24AA)
    val Gray get() = if (wmp) Color(0xFF6F86A8) else Color(0xFF78909C)
}

private sealed interface LibPage {
    data object Menu : LibPage
    data object Songs : LibPage
    data object Artists : LibPage
    data class Artist(val name: String) : LibPage
    data object Albums : LibPage
    data class Album(val name: String) : LibPage
    data object Playlists : LibPage
    data class PlaylistPage(val id: String) : LibPage
    data object Favorites : LibPage
    data object MostPlayed : LibPage
    data object Folders : LibPage
}

@Composable
fun LibraryScreen(
    onPlay: (List<Song>, Int, Boolean) -> Unit,
    onClose: () -> Unit,
    miniPlayer: @Composable () -> Unit,
    onHome: () -> Unit = {},
    resetSignal: Int = 0,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val stack = remember { mutableStateListOf<LibPage>(LibPage.Menu) }
    val page = stack.last()
    fun push(p: LibPage) { stack.add(p) }
    fun pop() { if (stack.size > 1) stack.removeAt(stack.lastIndex) else onClose() }
    BackHandler { pop() }
    LaunchedEffect(resetSignal) {
        if (resetSignal > 0) { while (stack.size > 1) stack.removeAt(stack.lastIndex) }
    }

    LaunchedEffect(Unit) { LibraryStore.scan() }

    val songs = LibraryStore.songs
    var addTo by remember { mutableStateOf<List<Song>?>(null) }

    Column(
        Modifier.fillMaxSize().background(Retro.Bg).pointerInput(Unit) {
            // deslizar rápido a la derecha en cualquier parte → regresa al menú principal
            val tracker = androidx.compose.ui.input.pointer.util.VelocityTracker()
            var total = 0f
            detectHorizontalDragGestures(
                onDragStart = { tracker.resetTracking(); total = 0f },
                onDragEnd = {
                    val v = tracker.calculateVelocity().x
                    if (v > 1500f && total > 70f) {
                        while (stack.size > 1) stack.removeAt(stack.lastIndex)
                    }
                }
            ) { change, drag ->
                tracker.addPosition(change.uptimeMillis, change.position)
                total += drag
            }
        }
    ) {
        // Encabezado metálico
        Box(Modifier.fillMaxWidth().background(Retro.metal).safeDrawingPadding()) {
            Row(
                Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { pop() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás", tint = Retro.HeaderIcon)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        pageTitle(page), color = Retro.HeaderText, fontWeight = FontWeight.Black,
                        fontSize = 19.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "♪ ${songs.size} canciones", color = Retro.Orange, fontSize = 11.sp,
                        fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace
                    )
                }
                HomeButton(onHome, dark = Retro.isWmp)
                IconButton(onClick = { scope.launch { LibraryStore.scan() } }) {
                    Icon(Icons.Filled.Refresh, "Volver a buscar", tint = Retro.HeaderIcon)
                }
            }
        }
        // franja naranja
        Box(Modifier.fillMaxWidth().height(3.dp).background(Brush.horizontalGradient(listOf(Retro.Orange, Retro.OrangeLight, Retro.Orange))))
        if (LibraryStore.scanning) {
            LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp), color = Retro.Orange, trackColor = Retro.Panel)
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (val p = page) {
                LibPage.Menu -> MainMenu(songs.size, onOpen = { push(it) })
                LibPage.Songs -> SongList(songs, onPlay, onAdd = { addTo = it })
                LibPage.Artists -> GroupList(
                    groups = songs.groupBy { it.artist }.toSortedMap(compareBy { it.lowercase(Locale.ROOT) }),
                    icon = Icons.Filled.Person, color = Retro.Blue,
                    onOpen = { push(LibPage.Artist(it)) }
                )
                is LibPage.Artist -> SongList(songs.filter { it.artist == p.name }.sortedWith(compareBy({ it.album }, { it.title })), onPlay, onAdd = { addTo = it })
                LibPage.Albums -> GroupList(
                    groups = songs.groupBy { it.album }.toSortedMap(compareBy { it.lowercase(Locale.ROOT) }),
                    icon = Icons.Filled.Album, color = Retro.Lime,
                    onOpen = { push(LibPage.Album(it)) }
                )
                is LibPage.Album -> SongList(songs.filter { it.album == p.name }, onPlay, onAdd = { addTo = it })
                LibPage.Favorites -> {
                    val favs = songs.filter { LibraryStore.isFavorite(it.path) }
                        .sortedByDescending { LibraryStore.favorites[it.path] ?: 0L }
                    SongList(favs, onPlay, onAdd = { addTo = it },
                        empty = "Aún no tienes favoritas.\nToca la ☆ en cualquier canción.")
                }
                LibPage.MostPlayed -> {
                    val top = songs.filter { (LibraryStore.playCounts[it.path] ?: 0) > 0 }
                        .sortedByDescending { LibraryStore.playCounts[it.path] ?: 0 }
                        .take(100)
                    SongList(top, onPlay, onAdd = { addTo = it }, showCounts = true,
                        empty = "Todavía no hay registro.\nConforme escuches música se irá llenando.")
                }
                LibPage.Playlists -> PlaylistsPage(onOpen = { push(LibPage.PlaylistPage(it)) })
                is LibPage.PlaylistPage -> {
                    val pl = LibraryStore.playlists.firstOrNull { it.id == p.id }
                    if (pl == null) { LaunchedEffect(Unit) { pop() } } else {
                        PlaylistDetail(pl, onPlay, onAdd = { addTo = it }, onDeleted = { pop() })
                    }
                }
                LibPage.Folders -> FoldersPage()
            }
        }
        miniPlayer()
    }

    addTo?.let { toAdd ->
        AddToPlaylistDialog(
            onDismiss = { addTo = null },
            onPick = { id ->
                LibraryStore.addToPlaylist(id, toAdd.map { it.path })
                addTo = null
                Toast.makeText(context, "Agregada a la lista", Toast.LENGTH_SHORT).show()
            },
            onNew = { name ->
                LibraryStore.createPlaylist(name, toAdd.map { it.path })
                addTo = null
                Toast.makeText(context, "Lista \"$name\" creada", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

private fun pageTitle(p: LibPage): String = when (p) {
    LibPage.Menu -> "Música"
    LibPage.Songs -> "Todas las canciones"
    LibPage.Artists -> "Artistas"
    is LibPage.Artist -> p.name
    LibPage.Albums -> "Álbumes"
    is LibPage.Album -> p.name
    LibPage.Playlists -> "Listas de reproducción"
    is LibPage.PlaylistPage -> LibraryStore.playlists.firstOrNull { it.id == p.id }?.name ?: "Lista"
    LibPage.Favorites -> "Favoritas"
    LibPage.MostPlayed -> "Más escuchadas"
    LibPage.Folders -> "Carpetas de música"
}

// ───────── Menú principal ─────────

@Composable
private fun MainMenu(songCount: Int, onOpen: (LibPage) -> Unit) {
    var skinDialog by remember { mutableStateOf(false) }
    if (skinDialog) MusicSkinDialog(onDismiss = { skinDialog = false })
    data class Entry(val label: String, val sub: String, val icon: ImageVector, val color: Color, val page: LibPage)
    val entries = listOf(
        Entry("Canciones", "$songCount", Icons.Filled.MusicNote, Retro.Orange, LibPage.Songs),
        Entry("Artistas", "${LibraryStore.songs.map { it.artist }.distinct().size}", Icons.Filled.Person, Retro.Blue, LibPage.Artists),
        Entry("Álbumes", "${LibraryStore.songs.map { it.album }.distinct().size}", Icons.Filled.Album, Retro.Lime, LibPage.Albums),
        Entry("Listas de reproducción", "${LibraryStore.playlists.size}", Icons.AutoMirrored.Filled.QueueMusic, Retro.Pink, LibPage.Playlists),
        Entry("Favoritas", "${LibraryStore.favorites.size}", Icons.Filled.Star, Retro.Yellow, LibPage.Favorites),
        Entry("Más escuchadas", "", Icons.Filled.TrendingUp, Retro.Teal, LibPage.MostPlayed),
        Entry("Carpetas de música", "${LibraryStore.musicFolders.size}", Icons.Filled.FolderOpen, Retro.Gray, LibPage.Folders),
        Entry("Tema del reproductor", if (Retro.isWmp) "Media Player" else "Walkman", Icons.Filled.Palette, Retro.Purple, LibPage.Menu),
    )
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(entries) { e ->
            Row(
                Modifier.fillMaxWidth().height(64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Retro.Panel)
                    .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
                    .clickable { if (e.label == "Tema del reproductor") skinDialog = true else onOpen(e.page) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                // bloque de color a la izquierda
                if (Retro.isWmp) {
                    Box(Modifier.width(66.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        GlassOrb(e.icon, e.color, 44.dp)
                    }
                } else {
                    Box(
                        Modifier.width(64.dp).fillMaxHeight()
                            .background(Brush.verticalGradient(listOf(e.color.copy(alpha = 0.95f), e.color.copy(alpha = 0.65f)))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(e.icon, null, tint = if (e.color == Retro.Yellow) Color(0xFF3A2E00) else Color.White, modifier = Modifier.size(30.dp))
                    }
                }
                Text(e.label, color = Retro.Text, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f).padding(horizontal = 16.dp))
                if (e.sub.isNotEmpty()) {
                    Text(e.sub, color = e.color, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 8.dp))
                }
                Text("›", color = Retro.SubText, fontSize = 26.sp, modifier = Modifier.padding(end = 14.dp))
            }
        }
    }
}

// ───────── Listas de canciones ─────────

@Composable
private fun PlayBar(songs: List<Song>, onPlay: (List<Song>, Int, Boolean) -> Unit, extra: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.clip(CircleShape).background(Retro.Orange).clickable(enabled = songs.isNotEmpty()) { onPlay(songs, 0, false) }
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.PlayArrow, null, tint = Retro.OnAccent)
            Spacer(Modifier.width(4.dp))
            Text("Reproducir", color = Retro.OnAccent, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        Row(
            Modifier.clip(CircleShape).border(2.dp, Retro.Orange, CircleShape)
                .clickable(enabled = songs.isNotEmpty()) { onPlay(songs, (songs.indices).random(), true) }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Shuffle, null, tint = Retro.Orange)
            Spacer(Modifier.width(4.dp))
            Text("Aleatorio", color = Retro.Orange, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.weight(1f))
        extra()
    }
}

@Composable
private fun SongList(
    songs: List<Song>,
    onPlay: (List<Song>, Int, Boolean) -> Unit,
    onAdd: (List<Song>) -> Unit,
    showCounts: Boolean = false,
    empty: String = "No hay canciones.\nRevisa “Carpetas de música”.",
    onRemove: ((Song) -> Unit)? = null,
    header: @Composable () -> Unit = {},
) {
    if (songs.isEmpty()) {
        Column(Modifier.fillMaxSize()) {
            header()
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(empty, color = Retro.SubText, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        item { header() }
        item { PlayBar(songs, onPlay) }
        itemsIndexed(songs, key = { _, s -> s.path }) { i, song ->
            SongRow(
                song = song,
                rank = if (showCounts) i + 1 else null,
                count = if (showCounts) LibraryStore.playCounts[song.path] ?: 0 else null,
                onClick = { onPlay(songs, i, false) },
                onAdd = { onAdd(listOf(song)) },
                onRemove = onRemove?.let { { it(song) } }
            )
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun SongRow(
    song: Song,
    rank: Int?,
    count: Int?,
    onClick: () -> Unit,
    onAdd: () -> Unit,
    onRemove: (() -> Unit)?,
) {
    val thumb by produceState<android.graphics.Bitmap?>(null, song.path) { value = loadThumb(song.path) }
    val fav = LibraryStore.isFavorite(song.path)
    var menu by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    if (showInfo) SongInfoWindow(song.path, Retro.Orange, onDismiss = { showInfo = false })
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (rank != null) {
            Text(
                "$rank", color = if (rank <= 3) Retro.Orange else Retro.SubText, fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold, fontSize = if (rank <= 3) 18.sp else 14.sp, modifier = Modifier.width(32.dp)
            )
        }
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)).background(Retro.Panel),
            contentAlignment = Alignment.Center
        ) {
            val b = thumb
            if (b != null) {
                Image(remember(b) { b.asImageBitmap() }, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Icon(Icons.Filled.MusicNote, null, tint = Retro.Orange.copy(alpha = 0.7f))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, color = Retro.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            val sub = buildString {
                append(song.artist)
                if (song.durationMs > 0) append(" · ").append(formatTime(song.durationMs))
                if (count != null) append(" · ").append("$count ${if (count == 1) "vez" else "veces"}")
            }
            Text(sub, color = Retro.SubText, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = { LibraryStore.toggleFavorite(song.path) }) {
            Icon(if (fav) Icons.Filled.Star else Icons.Filled.StarBorder, "Favorita",
                tint = if (fav) Retro.Yellow else Retro.SubText)
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Más", tint = Retro.SubText) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Agregar a lista") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null) },
                    onClick = { menu = false; onAdd() })
                DropdownMenuItem(text = { Text("Información") }, leadingIcon = { Icon(Icons.Filled.Info, null) },
                    onClick = { menu = false; showInfo = true })
                if (onRemove != null) {
                    DropdownMenuItem(text = { Text("Quitar de esta lista") }, leadingIcon = { Icon(Icons.Filled.RemoveCircleOutline, null) },
                        onClick = { menu = false; onRemove() })
                }
            }
        }
    }
}

@Composable
private fun GroupList(groups: Map<String, List<Song>>, icon: ImageVector, color: Color, onOpen: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 6.dp)) {
        items(groups.entries.toList(), key = { it.key }) { (name, list) ->
            val thumb by produceState<android.graphics.Bitmap?>(null, name) { value = loadThumb(list.first().path) }
            Row(
                Modifier.fillMaxWidth().clickable { onOpen(name) }.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(52.dp).clip(if (icon == Icons.Filled.Person) CircleShape else RoundedCornerShape(6.dp))
                        .background(color.copy(alpha = 0.85f)),
                    contentAlignment = Alignment.Center
                ) {
                    val b = thumb
                    if (b != null) Image(remember(b) { b.asImageBitmap() }, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    else Icon(icon, null, tint = Color.White)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, color = Retro.Text, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${list.size} ${if (list.size == 1) "canción" else "canciones"}", color = Retro.SubText, fontSize = 12.sp)
                }
                Text("›", color = color, fontSize = 24.sp)
            }
        }
    }
}

// ───────── Listas de reproducción ─────────

@Composable
private fun PlaylistsPage(onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var creating by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RetroButton("Nueva lista", Icons.AutoMirrored.Filled.PlaylistAdd, Modifier.weight(1f)) { creating = true }
                RetroButton("Importar M3U8", Icons.Filled.FileDownload, Modifier.weight(1f)) { importing = true }
            }
        }
        if (LibraryStore.playlists.isEmpty()) {
            item {
                Text("No tienes listas todavía.", color = Retro.SubText, modifier = Modifier.padding(top = 24.dp).fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        items(LibraryStore.playlists.toList(), key = { it.id }) { pl ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Retro.Panel)
                    .clickable { onOpen(pl.id) }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(46.dp).clip(RoundedCornerShape(8.dp)).background(Retro.Pink), contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.QueueMusic, null, tint = Color.White)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(pl.name, color = Retro.Text, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${pl.paths.size} canciones", color = Retro.SubText, fontSize = 12.sp)
                }
                Text("›", color = Retro.Pink, fontSize = 24.sp)
            }
        }
    }

    if (creating) {
        NameInputDialog("Nueva lista", "", onDismiss = { creating = false }) { name ->
            creating = false
            LibraryStore.createPlaylist(name)
        }
    }
    if (importing) {
        ImportDialog(
            files = LibraryStore.playlistFiles,
            onDismiss = { importing = false },
            onPick = { f ->
                importing = false
                scope.launch {
                    val (_, n) = LibraryStore.importM3u(f)
                    Toast.makeText(context, "Importada \"${f.nameWithoutExtension}\" con $n canciones", Toast.LENGTH_LONG).show()
                }
            }
        )
    }
}

@Composable
private fun PlaylistDetail(pl: Playlist, onPlay: (List<Song>, Int, Boolean) -> Unit, onAdd: (List<Song>) -> Unit, onDeleted: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val byPath = LibraryStore.songs.associateBy { it.path }
    val songs = pl.paths.mapNotNull { path -> byPath[path] ?: File(path).takeIf { it.isFile }?.let { Song(path, it.nameWithoutExtension, UNKNOWN_ARTIST, UNKNOWN_ALBUM, 0) } }

    SongList(
        songs = songs,
        onPlay = onPlay,
        onAdd = onAdd,
        empty = "Lista vacía.\nAgrega canciones con ⋮ → Agregar a lista.",
        onRemove = { LibraryStore.removeFromPlaylist(pl.id, it.path) },
        header = {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = { renaming = true }) { Icon(Icons.Filled.Edit, "Renombrar", tint = Retro.SubText) }
                IconButton(onClick = {
                    scope.launch {
                        val f = runCatching { LibraryStore.exportM3u(pl) }.getOrNull()
                        Toast.makeText(context, if (f != null) "Guardada en ${f.parentFile?.name}/${f.name}" else "No se pudo exportar", Toast.LENGTH_LONG).show()
                    }
                }) { Icon(Icons.Filled.FileUpload, "Exportar M3U8", tint = Retro.SubText) }
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Borrar lista", tint = Retro.SubText) }
            }
        }
    )

    if (renaming) {
        NameInputDialog("Renombrar lista", pl.name, onDismiss = { renaming = false }) { name ->
            renaming = false
            LibraryStore.renamePlaylist(pl.id, name)
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("¿Borrar \"${pl.name}\"?") },
            text = { Text("Solo se borra la lista, tus canciones no se tocan.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; LibraryStore.deletePlaylist(pl.id); onDeleted() }) { Text("Borrar") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun AddToPlaylistDialog(onDismiss: () -> Unit, onPick: (String) -> Unit, onNew: (String) -> Unit) {
    var newName by remember { mutableStateOf<String?>(null) }
    if (newName != null) {
        NameInputDialog("Nueva lista", "", onDismiss = { newName = null }) { onNew(it) }
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Agregar a lista") },
        text = {
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                item {
                    Row(Modifier.fillMaxWidth().clickable { newName = "" }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null, tint = Retro.Orange)
                        Spacer(Modifier.width(12.dp))
                        Text("Nueva lista…", fontWeight = FontWeight.Bold)
                    }
                }
                items(LibraryStore.playlists.toList(), key = { it.id }) { pl ->
                    Row(Modifier.fillMaxWidth().clickable { onPick(pl.id) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Filled.QueueMusic, null)
                        Spacer(Modifier.width(12.dp))
                        Text(pl.name)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun ImportDialog(files: List<File>, onDismiss: () -> Unit, onPick: (File) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Importar lista M3U8") },
        text = {
            Column {
                Text(
                    if (files.isEmpty()) "No encontré archivos .m3u8 ni .m3u en tus carpetas de música.\n\nTambién puedes abrir uno desde el explorador de archivos."
                    else "Encontré estas listas en tus carpetas de música. También puedes abrir cualquier .m3u8 desde el explorador.",
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(files, key = { it.absolutePath }) { f ->
                        Column(Modifier.fillMaxWidth().clickable { onPick(f) }.padding(vertical = 10.dp)) {
                            Text(f.name, fontWeight = FontWeight.Bold)
                            Text(f.parent ?: "", fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } }
    )
}

// ───────── Carpetas a escanear ─────────

@Composable
private fun FoldersPage() {
    val scope = rememberCoroutineScope()
    var picking by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text(
                "La app busca música solo en estas carpetas (y sus subcarpetas).",
                color = Retro.SubText, fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp)
            )
            RetroButton("Agregar carpeta", Icons.Filled.FolderOpen, Modifier.fillMaxWidth()) { picking = true }
        }
        items(LibraryStore.musicFolders.toList(), key = { it }) { path ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Retro.Panel).padding(start = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Folder, null, tint = Retro.Orange)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                    Text(File(path).name, color = Retro.Text, fontWeight = FontWeight.Bold)
                    Text(path.replace(Environment.getExternalStorageDirectory().absolutePath, "Interno"),
                        color = Retro.SubText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = {
                    LibraryStore.removeFolder(path)
                    scope.launch { LibraryStore.scan() }
                }) { Icon(Icons.Filled.RemoveCircleOutline, "Quitar", tint = Retro.SubText) }
            }
        }
    }
    if (picking) {
        FolderPickerDialog(
            onDismiss = { picking = false },
            onPick = { dir ->
                picking = false
                LibraryStore.addFolder(dir.absolutePath)
                scope.launch { LibraryStore.scan() }
            }
        )
    }
}

@Composable
fun FolderPickerDialog(onDismiss: () -> Unit, onPick: (File) -> Unit) {
    val context = LocalContext.current
    val roots = remember { storageRoots(context) }
    var dir by remember { mutableStateOf<File?>(null) }
    val subdirs = remember(dir) {
        dir?.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") }?.sortedBy { it.name.lowercase(Locale.ROOT) } ?: emptyList()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(dir?.name ?: "Elige dónde buscar", maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            LazyColumn(Modifier.heightIn(max = 380.dp)) {
                val d = dir
                if (d == null) {
                    items(roots) { r ->
                        PickerRow(r.name, Icons.Filled.LibraryMusic) { dir = r.file }
                    }
                } else {
                    item {
                        PickerRow("Subir", Icons.Filled.ArrowUpward) {
                            dir = if (roots.any { it.file.absolutePath == d.absolutePath }) null else d.parentFile
                        }
                    }
                    items(subdirs, key = { it.absolutePath }) { f -> PickerRow(f.name, Icons.Filled.Folder) { dir = f } }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = dir != null, onClick = { dir?.let(onPick) }) { Text("Elegir esta carpeta") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun PickerRow(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Retro.Orange)
        Spacer(Modifier.width(12.dp))
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ───────── Piezas comunes ─────────

@Composable
private fun RetroButton(label: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.clip(RoundedCornerShape(10.dp))
            .background(Brush.verticalGradient(listOf(Retro.OrangeLight, Retro.Orange)))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, tint = Retro.OnAccent)
        Spacer(Modifier.width(8.dp))
        Text(label, color = Retro.OnAccent, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun NameInputDialog(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true, label = { Text("Nombre") }) },
        confirmButton = { TextButton(enabled = text.isNotBlank(), onClick = { onConfirm(text.trim()) }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

private fun Color.darken(f: Float = 0.55f) = Color(red * f, green * f, blue * f, alpha)
private fun Color.compositeOverWhite() = Color(
    red * alpha + (1 - alpha), green * alpha + (1 - alpha), blue * alpha + (1 - alpha), 1f
)
