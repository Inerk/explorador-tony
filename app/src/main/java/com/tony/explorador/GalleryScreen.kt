package com.tony.explorador

import android.content.Context
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TableRows
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class GalleryItem(
    val path: String,
    val album: String,
    val date: Long,
    val isVideo: Boolean,
    val durationMs: Long,
    val size: Long = 0L,
) {
    val file get() = File(path)
    val folder: String get() = File(path).parent ?: ""
}

/** Todo lo que Android tiene registrado (fotos o videos). */
private suspend fun loadMediaStore(context: Context, videos: Boolean): List<GalleryItem> = withContext(Dispatchers.IO) {
    val out = mutableListOf<GalleryItem>()
    val uri = if (videos) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    val proj = buildList {
        add(MediaStore.MediaColumns.DATA)
        add(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
        add(MediaStore.MediaColumns.DATE_TAKEN)
        add(MediaStore.MediaColumns.DATE_MODIFIED)
        add(MediaStore.MediaColumns.SIZE)
        if (videos) add(MediaStore.MediaColumns.DURATION)
    }.toTypedArray()
    runCatching {
        context.contentResolver.query(uri, proj, null, null, null)?.use { c ->
            val iData = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
            val iBucket = c.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
            val iTaken = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN)
            val iMod = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
            val iSize = c.getColumnIndex(MediaStore.MediaColumns.SIZE)
            val iDur = if (videos) c.getColumnIndex(MediaStore.MediaColumns.DURATION) else -1
            while (c.moveToNext()) {
                val path = c.getString(iData) ?: continue
                val taken = c.getLong(iTaken)
                out.add(
                    GalleryItem(
                        path = path,
                        album = c.getString(iBucket) ?: File(path).parentFile?.name ?: "Otros",
                        date = if (taken > 0) taken else c.getLong(iMod) * 1000,
                        isVideo = videos,
                        durationMs = if (iDur >= 0) c.getLong(iDur) else 0L,
                        size = if (iSize >= 0) c.getLong(iSize) else 0L,
                    )
                )
            }
        }
    }
    out.filter { it.file.isFile }
}

/** Lo que se va a mostrar: todo, o sólo lo de las carpetas elegidas (y sus subcarpetas). */
private suspend fun loadMedia(context: Context, videos: Boolean, folders: List<String>): Pair<List<GalleryItem>, List<GalleryItem>> =
    withContext(Dispatchers.IO) {
        val all = loadMediaStore(context, videos)
        if (folders.isEmpty()) return@withContext all.sortedByDescending { it.date } to all
        fun inside(path: String) = folders.any { path.startsWith(it.trimEnd('/') + "/") }
        val picked = all.filter { inside(it.path) }.toMutableList()
        // lo que Android no tenga registrado (por ejemplo carpetas con .nomedia) se busca a mano
        val known = picked.mapTo(HashSet()) { it.path }
        val wanted = if (videos) FileKind.VIDEO else FileKind.IMAGE
        folders.forEach { root ->
            File(root).walkTopDown().maxDepth(6).take(20000).forEach { f ->
                if (f.isFile && f.absolutePath !in known && f.kind() == wanted) {
                    known.add(f.absolutePath)
                    picked.add(GalleryItem(f.absolutePath, f.parentFile?.name ?: "Otros", f.lastModified(), videos, 0L, f.length()))
                }
            }
        }
        picked.sortedByDescending { it.date } to all
    }

private val monthFormat = SimpleDateFormat("MMMM yyyy", Locale("es", "MX"))
private val dayFormat = SimpleDateFormat("d MMM yyyy", Locale("es", "MX"))

/** Galería de fotos. */
@Composable
fun GalleryScreen(
    onOpenImages: (List<File>, Int) -> Unit,
    onOpenVideo: (List<File>, Int) -> Unit,
    onClose: () -> Unit,
    onHome: () -> Unit,
) = MediaGalleryScreen(videos = false, onOpenImages = onOpenImages, onOpenVideo = onOpenVideo, onClose = onClose, onHome = onHome)

/** Videos, con la misma forma de galería. */
@Composable
fun VideosScreen(
    onOpenVideo: (List<File>, Int) -> Unit,
    onPlayAsMusic: (List<File>, Int) -> Unit,
    onClose: () -> Unit,
    onHome: () -> Unit,
) = MediaGalleryScreen(videos = true, onOpenImages = { _, _ -> }, onOpenVideo = onOpenVideo, onClose = onClose, onHome = onHome,
    onPlayAsMusic = onPlayAsMusic)

@Composable
private fun MediaGalleryScreen(
    videos: Boolean,
    onOpenImages: (List<File>, Int) -> Unit,
    onOpenVideo: (List<File>, Int) -> Unit,
    onClose: () -> Unit,
    onHome: () -> Unit,
    onPlayAsMusic: ((List<File>, Int) -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val selected = remember { mutableStateListOf<String>() }
    var reload by remember { mutableIntStateOf(0) }
    var pickFor by remember { mutableStateOf<String?>(null) }   // "copy" o "move"
    var confirmTrash by remember { mutableStateOf(false) }
    val folders = (if (videos) LibraryStore.videoFolders else LibraryStore.galleryFolders).toList()
    val data by produceState<Pair<List<GalleryItem>, List<GalleryItem>>?>(null, folders, reload) {
        value = loadMedia(context, videos, folders)
    }
    var tab by remember { mutableIntStateOf(0) }
    var album by remember { mutableStateOf<String?>(null) }
    var settingsOpen by remember { mutableStateOf(false) }
    val listView = (if (videos) LibraryStore.videoView else LibraryStore.galleryView) == "list"
    val appName = if (videos) "Videos" else "Galería"

    BackHandler { if (selected.isNotEmpty()) selected.clear() else if (album != null) album = null else onClose() }
    fun toggle(item: GalleryItem) { if (item.path in selected) selected.remove(item.path) else selected.add(item.path) }
    fun chosenFiles() = selected.map { File(it) }

    fun open(list: List<GalleryItem>, item: GalleryItem) {
        if (item.isVideo) {
            val vids = list.filter { it.isVideo }
            onOpenVideo(vids.map { it.file }, vids.indexOf(item).coerceAtLeast(0))
        } else {
            val imgs = list.filter { !it.isVideo }
            onOpenImages(imgs.map { it.file }, imgs.indexOf(item).coerceAtLeast(0))
        }
    }

    Surface(Modifier.fillMaxSize().pointerInput(Unit) {}, color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            Surface(tonalElevation = 3.dp) {
                Column(Modifier.statusBarsPadding()) {
                    if (selected.isNotEmpty()) Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { selected.clear() }) { Icon(Icons.Filled.Close, "Cancelar") }
                        Text("${selected.size}", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        if (videos && onPlayAsMusic != null) IconButton(onClick = { val f = chosenFiles(); selected.clear(); onPlayAsMusic(f, 0) }) {
                            Icon(Icons.Filled.LibraryMusic, "Reproducir como música") }
                        IconButton(onClick = { shareFiles(context, chosenFiles()) }) { Icon(Icons.Filled.Share, "Compartir") }
                        IconButton(onClick = { pickFor = "copy" }) { Icon(Icons.Filled.ContentCopy, "Copiar") }
                        IconButton(onClick = { pickFor = "move" }) { Icon(Icons.AutoMirrored.Filled.DriveFileMove, "Mover") }
                        IconButton(onClick = { confirmTrash = true }) { Icon(Icons.Filled.Delete, "Eliminar", tint = MaterialTheme.colorScheme.error) }
                    } else Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { if (album != null) album = null else onClose() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás")
                        }
                        if (album == null) {
                            XpIcon(if (videos) XpIconKind.VIDEO_PLAYER else XpIconKind.GALLERY, 30.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(album ?: appName, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        IconButton(onClick = { LibraryStore.changeMediaView(videos, if (listView) "grid" else "list") }) {
                            Icon(if (listView) Icons.Filled.GridView else Icons.Filled.TableRows,
                                if (listView) "Ver en cuadrícula" else "Ver en lista")
                        }
                        IconButton(onClick = { settingsOpen = true }) { Icon(Icons.Filled.Settings, "Ajustes") }
                        HomeButton(onHome, Modifier.padding(end = 10.dp), dark = false)
                    }
                    if (album == null) {
                        TabRow(selectedTabIndex = tab) {
                            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(if (videos) "Videos" else "Fotos") })
                            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(if (videos) "Carpetas" else "Álbumes") })
                        }
                    }
                }
            }

            val items = data?.first
            when {
                items == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                items.isEmpty() -> Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center) {
                    Text(if (videos) "No encontré videos." else "No encontré fotos.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (folders.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        TextButton(onClick = { settingsOpen = true }) { Text("Revisar carpetas elegidas") }
                    }
                }
                album != null -> {
                    val list = items.filter { it.album == album }
                    MediaItems(list, videos, listView, selected, onClick = { if (selected.isNotEmpty()) toggle(it) else open(list, it) }, onLongClick = { toggle(it) })
                }
                tab == 0 -> MediaItems(items, videos, listView, selected, onClick = { if (selected.isNotEmpty()) toggle(it) else open(items, it) }, onLongClick = { toggle(it) })
                else -> AlbumGrid(items, listView, onOpen = { album = it })
            }
        }
    }

    pickFor?.let { mode ->
        FolderPickerDialog(onDismiss = { pickFor = null }, onPick = { dest ->
            val files = chosenFiles(); pickFor = null; selected.clear()
            scope.launch {
                val n = if (mode == "move") moveInto(files, dest) else copyInto(files, dest)
                android.widget.Toast.makeText(context, "${if (mode == "move") "Movidos" else "Copiados"}: $n de ${files.size}", android.widget.Toast.LENGTH_SHORT).show()
                reload++
            }
        })
    }
    if (confirmTrash) {
        val files = chosenFiles()
        ThemedDialog("¿Eliminar ${files.size} elemento(s)?", { confirmTrash = false }, listOf(
            DialogButton("Cancelar") { confirmTrash = false },
            DialogButton("Eliminar", true) {
                confirmTrash = false; selected.clear()
                scope.launch {
                    val n = Trash.moveToTrash(files)
                    android.widget.Toast.makeText(context, "Enviados a la papelera: $n", android.widget.Toast.LENGTH_SHORT).show()
                    reload++
                }
            })) { Text("Se van a la Papelera. Desde ahí los puedes restaurar.") }
    }

    if (settingsOpen) {
        MediaSettingsDialog(
            videos = videos,
            all = data?.second.orEmpty(),
            onDismiss = { settingsOpen = false },
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun MediaItems(items: List<GalleryItem>, videos: Boolean, listView: Boolean, selected: List<String>, onClick: (GalleryItem) -> Unit, onLongClick: (GalleryItem) -> Unit) {
    val groups = remember(items) {
        items.groupBy { monthFormat.format(Date(it.date)).replaceFirstChar { c -> c.uppercase() } }
    }
    if (listView) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            groups.forEach { (month, list) ->
                item(key = "h-$month") { MonthHeader(month) }
                items(list, key = { it.path }) { item -> MediaRow(item, Modifier.selectionOverlay(item.path in selected).combinedClickable(onClick = { onClick(item) }, onLongClick = { onLongClick(item) })) }
            }
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(if (videos) 168.dp else 104.dp),
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(if (videos) 8.dp else 2.dp),
        verticalArrangement = Arrangement.spacedBy(if (videos) 10.dp else 2.dp),
        contentPadding = PaddingValues(start = if (videos) 8.dp else 0.dp, end = if (videos) 8.dp else 0.dp, bottom = 24.dp)
    ) {
        groups.forEach { (month, list) ->
            item(span = { GridItemSpan(maxLineSpan) }, key = "h-$month") { MonthHeader(month) }
            items(list, key = { it.path }) { item ->
                if (videos) {
                    Column(Modifier.clip(RoundedCornerShape(10.dp)).selectionOverlay(item.path in selected).combinedClickable(onClick = { onClick(item) }, onLongClick = { onLongClick(item) })) {
                        Thumb(item, Modifier.clip(RoundedCornerShape(10.dp)), ratio = 16f / 10f)
                        Text(item.file.nameWithoutExtension, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp, start = 2.dp, end = 2.dp))
                    }
                } else Thumb(item, Modifier.selectionOverlay(item.path in selected).combinedClickable(onClick = { onClick(item) }, onLongClick = { onLongClick(item) }))
            }
        }
    }
}

@Composable
private fun MonthHeader(month: String) {
    Text(month, fontWeight = FontWeight.Bold, fontSize = 15.sp,
        modifier = Modifier.padding(start = 12.dp, top = 16.dp, bottom = 8.dp))
}

@Composable
private fun MediaRow(item: GalleryItem, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Thumb(item, Modifier.width(if (item.isVideo) 120.dp else 72.dp).clip(RoundedCornerShape(8.dp)),
            ratio = if (item.isVideo) 16f / 10f else 1f, small = true)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.file.nameWithoutExtension, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val parts = listOfNotNull(
                dayFormat.format(Date(item.date)),
                item.durationMs.takeIf { it > 0 }?.let { formatTime(it) },
                item.size.takeIf { it > 0 }?.let { formatSize(it) },
            )
            Text(parts.joinToString(" · "), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(item.album, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun Thumb(item: GalleryItem, modifier: Modifier = Modifier, ratio: Float = 1f, small: Boolean = false) {
    Box(modifier.aspectRatio(ratio).background(MaterialTheme.colorScheme.surfaceVariant)) {
        AsyncImage(model = item.file, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        if (item.isVideo) {
            if (!small) {
                Box(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))))
                        .padding(4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.PlayCircle, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(3.dp))
                        if (item.durationMs > 0) Text(formatTime(item.durationMs), color = Color.White, fontSize = 11.sp)
                    }
                }
            } else {
                Icon(Icons.Filled.PlayCircle, null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.align(Alignment.Center).size(26.dp))
            }
        }
    }
}

@Composable
private fun AlbumGrid(items: List<GalleryItem>, listView: Boolean, onOpen: (String) -> Unit) {
    val albums = remember(items) {
        items.groupBy { it.album }.entries
            .sortedWith(compareByDescending<Map.Entry<String, List<GalleryItem>>> { it.key.equals("Camera", true) || it.key.equals("Cámara", true) }
                .thenByDescending { it.value.size })
    }
    if (listView) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 6.dp)) {
            items(albums, key = { it.key }) { (name, list) ->
                Row(Modifier.fillMaxWidth().clickable { onOpen(name) }.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Thumb(list.first(), Modifier.width(64.dp).clip(RoundedCornerShape(8.dp)), small = true)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${list.size} elementos", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(160.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items(albums, key = { it.key }) { (name, list) ->
            Column(Modifier.clip(RoundedCornerShape(12.dp)).clickable { onOpen(name) }) {
                Thumb(list.first(), Modifier.clip(RoundedCornerShape(12.dp)))
                Text(name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp, start = 2.dp))
                Text("${list.size} elementos", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 2.dp))
            }
        }
    }
}

/** Ajustes de la mini app: qué carpetas leer y cómo verlas. */
@Composable
private fun MediaSettingsDialog(videos: Boolean, all: List<GalleryItem>, onDismiss: () -> Unit) {
    val current = if (videos) LibraryStore.videoFolders else LibraryStore.galleryFolders
    var onlyChosen by remember { mutableStateOf(current.isNotEmpty()) }
    val chosen = remember { mutableStateListOf<String>().apply { addAll(current) } }
    var view by remember { mutableStateOf(if (videos) LibraryStore.videoView else LibraryStore.galleryView) }
    var picking by remember { mutableStateOf(false) }
    // carpetas donde hay fotos/videos, más las que se agregaron a mano
    val detected = remember(all) {
        all.groupBy { it.folder }.map { (k, v) -> k to v.size }.sortedByDescending { it.second }
    }
    val extra = chosen.filter { c -> detected.none { it.first == c } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (videos) "Ajustes de Videos" else "Ajustes de Galería") },
        text = {
            LazyColumn(Modifier.heightIn(max = 460.dp)) {
                item {
                    Text("Ver como", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(view == "grid", { view = "grid" }); Text("Cuadrícula", Modifier.clickable { view = "grid" })
                        Spacer(Modifier.width(16.dp))
                        RadioButton(view == "list", { view = "list" }); Text("Lista", Modifier.clickable { view = "list" })
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Text("Carpetas a leer", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Row(Modifier.fillMaxWidth().clickable { onlyChosen = false }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(!onlyChosen, { onlyChosen = false }); Text("Todas las del teléfono")
                    }
                    Row(Modifier.fillMaxWidth().clickable { onlyChosen = true }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(onlyChosen, { onlyChosen = true }); Text("Sólo las que yo elija")
                    }
                }
                if (onlyChosen) {
                    items(detected + extra.map { it to -1 }, key = { it.first }) { (folder, count) ->
                        val checked = folder in chosen
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                .clickable { if (checked) chosen.remove(folder) else chosen.add(folder) }.padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked, { if (it) chosen.add(folder) else chosen.remove(folder) })
                            Icon(Icons.Filled.Folder, null, tint = Color(0xFFF5B800), modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(File(folder).name.ifBlank { folder }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(if (count >= 0) "$count · ${folder.substringAfter("/0/", folder)}" else "Agregada a mano · incluye subcarpetas",
                                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    item {
                        TextButton(onClick = { picking = true }) {
                            Icon(Icons.Filled.CreateNewFolder, null); Spacer(Modifier.width(6.dp)); Text("Agregar otra carpeta…")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                LibraryStore.changeMediaView(videos, view)
                LibraryStore.setMediaFolders(videos, if (onlyChosen) chosen.toList() else emptyList())
                onDismiss()
            }, enabled = !onlyChosen || chosen.isNotEmpty()) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
    if (picking) FolderPickerDialog(onDismiss = { picking = false }, onPick = { f ->
        if (f.absolutePath !in chosen) chosen.add(f.absolutePath)
        picking = false
    })
}
