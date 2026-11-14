package com.tony.explorador

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Drafts
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.VerticalSplit
import androidx.compose.material.icons.outlined.VerticalSplit
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.automirrored.outlined.ArrowBackIos
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.media3.common.Player
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun BrowserScreen(
    controller: Player?,
    onOpenImages: (List<File>, Int) -> Unit,
    onOpenVideo: (List<File>, Int) -> Unit,
    onPlayMusic: (List<File>, Int) -> Unit,
    onOpenMusicPlayer: () -> Unit,
    onOpenLibrary: () -> Unit,
    onChooseTheme: () -> Unit,
    homeSignal: Int,
    onOpenGallery: () -> Unit,
    onOpenVideos: () -> Unit,
    onOpenPdf: (File) -> Unit,
    onPlayAsMusic: (List<File>, Int) -> Unit,
    onExpandVideo: () -> Unit,
    folderRequest: File?,
    onFolderHandled: () -> Unit,
    versionName: String,
    onCheckUpdates: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val roots = remember { storageRoots(context) }

    var currentDir by remember { mutableStateOf<File?>(null) }
    var sortMode by remember { mutableStateOf(SortMode.NAME) }
    var ascending by remember { mutableStateOf(true) }
    var showHidden by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableIntStateOf(0) }

    var selection by remember { mutableStateOf(setOf<File>()) }
    var clipboard by remember { mutableStateOf(listOf<File>()) }
    var clipboardMove by remember { mutableStateOf(false) }

    var searchActive by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<File>?>(null) }
    var archiveFile by remember { mutableStateOf<File?>(null) }
    var focused by remember { mutableStateOf<File?>(null) }
    var castFile by remember { mutableStateOf<File?>(null) }
    var docFile by remember { mutableStateOf<File?>(null) }
    var recentMode by remember { mutableStateOf(false) }
    var lastDir by remember { mutableStateOf<File?>(null) }
    LaunchedEffect(currentDir) { currentDir?.let { lastDir = it } }
    var modelFile by remember { mutableStateOf<File?>(null) }   // archivo que se ve en el panel de vista previa
    var paneFrac by remember { mutableStateOf(0.42f) }
    var compressItems by remember { mutableStateOf<List<File>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searchProgress by remember { mutableStateOf(0f) }
    var searchWhere by remember { mutableStateOf("") }
    var formatFilter by remember { mutableStateOf(setOf<String>()) }

    var showNewFolder by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<File?>(null) }
    var transferFiles by remember { mutableStateOf(listOf<File>()) }   // lo que se va a copiar o mover
    var transferMove by remember { mutableStateOf(false) }
    var menuFile by remember { mutableStateOf<File?>(null) }           // archivo con el menú ⋮ abierto
    var confirmDelete by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var importCandidate by remember { mutableStateOf<File?>(null) }
    var showHistory by remember { mutableStateOf(false) }
    val skin = skinFor(LibraryStore.theme)

    val files by produceState(initialValue = emptyList<File>(), currentDir, sortMode, ascending, showHidden, refreshKey) {
        val dir = currentDir
        value = if (dir == null) emptyList() else withContext(Dispatchers.IO) {
            listFolder(dir, sortMode, ascending, showHidden)
        }
    }

    LaunchedEffect(query, searchActive, showHidden, currentDir) {
        if (!searchActive || query.isBlank()) { if (!recentMode) searchResults = null; return@LaunchedEffect }
        delay(300)
        val base = currentDir ?: roots.first().file
        formatFilter = emptySet()
        searchResults = emptyList(); searchProgress = 0f; searching = true
        try {
            searchFilesProgressive(base, query, showHidden) { found, p, where ->
                searchResults = found; searchProgress = p; searchWhere = where
            }
        } finally { searching = false }
    }

    fun refresh() { refreshKey++ }
    fun goTo(dir: File?) {
        recentMode = false
        currentDir = dir
        focused = null
        selection = emptySet()
        searchActive = false
        query = ""
    }

    LaunchedEffect(homeSignal) { if (homeSignal > 0) goTo(null) }
    LaunchedEffect(folderRequest) { folderRequest?.let { goTo(it); onFolderHandled() } }

    fun openFile(file: File, siblings: List<File>) {
        when (file.kind()) {
            FileKind.FOLDER -> goTo(file)
            FileKind.IMAGE -> scope.launch {
                val list = withContext(Dispatchers.IO) { siblings.filter { it.kind() == FileKind.IMAGE } }
                onOpenImages(list, list.indexOf(file).coerceAtLeast(0))
            }
            FileKind.VIDEO -> scope.launch {
                val list = withContext(Dispatchers.IO) { siblings.filter { it.kind() == FileKind.VIDEO } }
                onOpenVideo(list, list.indexOf(file).coerceAtLeast(0))
            }
            // música: suena ahí mismo con el botón del ícono (como en la Mac), sin cuadrito flotante
            FileKind.AUDIO -> ExplorerAudio.toggle(file)
            FileKind.OTHER -> {
                val ext = file.extension.lowercase(Locale.ROOT)
                when (ext) {
                    "m3u8", "m3u" -> importCandidate = file
                    "pdf" -> onOpenPdf(file)
                    else -> when {
                        Archives.isArchive(file) -> archiveFile = file
                        isDocViewable(file) -> docFile = file   // texto, Word, Excel, PowerPoint, HTML, XML
                        is3dModel(file) -> modelFile = file       // STL / 3MF
                        else -> openExternally(context, file)
                    }
                }
            }
        }
    }

    fun toggleSelect(file: File) {
        selection = if (file in selection) selection - file else selection + file
    }

    // Recientes: lo último que se guardó o cambió en el teléfono
    LaunchedEffect(recentMode, showHidden) {
        if (!recentMode) { if (!searchActive) searchResults = null; return@LaunchedEffect }
        formatFilter = emptySet(); searching = true; searchResults = emptyList()
        searchResults = withContext(Dispatchers.IO) { recentFiles(context, showHidden) }
        searching = false
    }

    // Botón atrás: selección → búsqueda → carpeta anterior → salir
    val isRoot = currentDir != null && roots.any { it.file.absolutePath == currentDir?.absolutePath }
    BackHandler(enabled = selection.isNotEmpty() || searchActive || currentDir != null || recentMode) {
        when {
            selection.isNotEmpty() -> selection = emptySet()
            recentMode -> recentMode = false
            searchActive -> { searchActive = false; query = "" }
            isRoot -> goTo(null)
            else -> goTo(currentDir?.parentFile)
        }
    }

    // los formatos se calculan fuera del hilo principal (revisar miles de archivos congela la pantalla)
    val formatInfo by produceState(Pair(emptyList<Pair<String, Int>>(), emptyMap<String, String>()), searchResults) {
        val r = searchResults
        value = if (r == null) Pair(emptyList(), emptyMap()) else withContext(Dispatchers.IO) {
            val keys = HashMap<String, String>(r.size * 2)
            for (f in r) keys[f.absolutePath] = formatKey(f)
            Pair(keys.values.groupingBy { it }.eachCount().entries
                .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }).map { it.key to it.value }, keys)
        }
    }
    val formatCounts = formatInfo.first
    val shownFiles = searchResults?.let { r ->
        if (formatFilter.isEmpty()) r else r.filter { (formatInfo.second[it.absolutePath] ?: "") in formatFilter }
    }?.distinctBy { it.absolutePath } ?: files

    if (skin.isRetro && currentDir == null && !searchActive) {
        XpDesktop(
            roots = roots,
            onOpenFolder = { goTo(it) },
            onOpenMusic = onOpenLibrary,
            onOpenGallery = onOpenGallery,
            onOpenVideos = onOpenVideos,
            onExpandVideo = onExpandVideo,
            onCheckUpdates = onCheckUpdates,
            onChooseTheme = onChooseTheme,
            versionName = versionName,
            player = controller,
            onOpenPlayer = onOpenMusicPlayer,
        )
        return
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            when {
                selection.isNotEmpty() -> Box(Modifier.background(ExpTheme.bar)) { TopAppBar(
                    expandedHeight = 50.dp,
                    navigationIcon = {
                        IconButton(onClick = { selection = emptySet() }) { Icon(Icons.Outlined.Close, "Cancelar", modifier = Modifier.size(22.dp)) }
                    },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${selection.size}", fontWeight = FontWeight.Black, fontSize = 15.sp, color = Color.White,
                                modifier = Modifier.clip(androidx.compose.foundation.shape.CircleShape).background(ExpTheme.onBar)
                                    .padding(horizontal = 9.dp, vertical = 2.dp))
                            Text(if (selection.size == 1) "  seleccionado" else "  seleccionados", fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold, maxLines = 1)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, titleContentColor = ExpTheme.onBar,
                        navigationIconContentColor = ExpTheme.onBar, actionIconContentColor = ExpTheme.onBar),
                    actions = {
                        val ib = Modifier.size(40.dp); val isz = Modifier.size(21.dp)
                        IconButton(onClick = { selection = shownFiles.toSet() }, modifier = ib) { Icon(Icons.Outlined.SelectAll, "Seleccionar todo", isz) }
                        IconButton(onClick = {
                            transferFiles = selection.toList(); transferMove = false; selection = emptySet()
                        }, modifier = ib) { Icon(Icons.Outlined.ContentCopy, "Copiar", isz) }
                        IconButton(onClick = {
                            transferFiles = selection.toList(); transferMove = true; selection = emptySet()
                        }, modifier = ib) { Icon(Icons.Outlined.ContentCut, "Mover", isz) }
                        IconButton(onClick = { confirmDelete = true }, modifier = ib) { Icon(Icons.Outlined.Delete, "Eliminar", isz) }
                        var more by remember { mutableStateOf(false) }
                        IconButton(onClick = { more = true }, modifier = ib) { Icon(Icons.Outlined.MoreVert, "Más", isz) }
                        DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                            if (selection.size == 1) {
                                DropdownMenuItem(text = { Text("Renombrar") }, leadingIcon = { Icon(Icons.Filled.Edit, null) },
                                    onClick = { more = false; renameTarget = selection.first() })
                            }
                            val playable = selection.filter { it.kind() == FileKind.AUDIO || it.kind() == FileKind.VIDEO }
                                .sortedBy { it.name.lowercase(Locale.ROOT) }
                            if (playable.any { it.kind() == FileKind.VIDEO }) {
                                DropdownMenuItem(text = { Text("Reproducir como música") }, leadingIcon = { Icon(Icons.Filled.LibraryMusic, null) },
                                    onClick = { more = false; selection = emptySet(); onPlayAsMusic(playable, 0) })
                            }
                            DropdownMenuItem(text = { Text("Compartir") }, leadingIcon = { Icon(Icons.Filled.Share, null) },
                                onClick = { more = false; shareFiles(context, selection.filter { it.isFile }) })
                            DropdownMenuItem(text = { Text("Comprimir…") }, leadingIcon = { Icon(Icons.Filled.Archive, null) },
                                onClick = { more = false; compressItems = selection.toList(); selection = emptySet() })
                            DropdownMenuItem(text = { Text("Eliminar") }, leadingIcon = { Icon(Icons.Filled.Delete, null) },
                                onClick = { more = false; confirmDelete = true })
                        }
                    }
                ) }
                searchActive -> TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { searchActive = false; query = "" }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Cerrar búsqueda")
                        }
                    },
                    title = {
                        TextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text("Buscar en ${currentDir?.name ?: "almacenamiento"}") },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                )
                else -> Box(Modifier.background(ExpTheme.bar)) { TopAppBar(
                    expandedHeight = 62.dp,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = ExpTheme.text,
                        navigationIconContentColor = ExpTheme.text,
                        actionIconContentColor = ExpTheme.text,
                    ),
                    navigationIcon = {
                        if (currentDir != null) {
                            IconButton(onClick = { if (isRoot) goTo(null) else goTo(currentDir?.parentFile) }) {
                                Icon(Icons.AutoMirrored.Outlined.ArrowBackIos, "Atrás", modifier = Modifier.size(20.dp))
                            }
                        }
                    },
                    title = {
                        val dir = currentDir
                        if (recentMode) Column {
                            Text("Recientes", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                            Text("Lo último que se guardó en tu teléfono", fontSize = 13.sp, color = ExpTheme.sub, maxLines = 1)
                        }
                        else if (dir == null) Text("Explorador Tony", maxLines = 1, fontWeight = FontWeight.Bold)
                        else {
                            // nombre de la carpeta y cuánto hay adentro
                            val stats by produceState("", dir, files) {
                                value = withContext(Dispatchers.IO) {
                                    val sz = files.sumOf { if (it.isFile) it.length() else 0L }
                                    "${files.size} elementos" + if (sz > 0) " • ${formatSize(sz)}" else ""
                                }
                            }
                            Column {
                                Text(if (isRoot) (roots.firstOrNull { it.file.absolutePath == dir.absolutePath }?.name ?: dir.name) else dir.name,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                                if (stats.isNotEmpty()) Text(stats, fontSize = 13.sp, color = ExpTheme.sub, maxLines = 1)
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { searchActive = true }, modifier = Modifier.size(44.dp)) { Icon(Icons.Outlined.Search, "Buscar", modifier = Modifier.size(24.dp)) }
                        var more by remember { mutableStateOf(false) }
                        IconButton(onClick = { more = true }, modifier = Modifier.size(44.dp)) { Icon(Icons.Filled.MoreHoriz, "Más", modifier = Modifier.size(24.dp)) }
                        DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                            if (currentDir != null) {
                                DropdownMenuItem(text = { Text("Nueva carpeta") }, leadingIcon = { Icon(Icons.Filled.CreateNewFolder, null) },
                                    onClick = { more = false; showNewFolder = true })
                            }
                            DropdownMenuItem(
                                text = { Text(if (showHidden) "Ocultar archivos ocultos" else "Mostrar archivos ocultos") },
                                onClick = { more = false; showHidden = !showHidden })
                            DropdownMenuItem(text = { Text("Inicio") }, leadingIcon = { Icon(Icons.Filled.Home, null) },
                                onClick = { more = false; goTo(null) })
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Buscar actualizaciones\nVersión $versionName") },
                                leadingIcon = { Icon(Icons.Filled.SystemUpdate, null) },
                                onClick = { more = false; onCheckUpdates() })
                            DropdownMenuItem(
                                text = { Text("Historial de versiones") },
                                leadingIcon = { Icon(Icons.Filled.History, null) },
                                onClick = { more = false; showHistory = true })
                            if (false) DropdownMenuItem(
                                text = { Text("Tema: ${LibraryStore.theme.label}") },
                                leadingIcon = { Icon(Icons.Filled.Palette, null) },
                                onClick = { more = false; onChooseTheme() })
                        }
                    }
                ) }
            }
        },
        bottomBar = {
            Column {
                if (clipboard.isNotEmpty()) {
                    Surface(tonalElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${clipboard.size} para ${if (clipboardMove) "mover" else "copiar"}",
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { clipboard = emptyList() }) { Text("Cancelar") }
                            Button(
                                enabled = currentDir != null && !busy,
                                onClick = {
                                    val dest = currentDir ?: return@Button
                                    scope.launch {
                                        busy = true
                                        val n = if (clipboardMove) moveInto(clipboard, dest) else copyInto(clipboard, dest)
                                        val total = clipboard.size
                                        busy = false
                                        clipboard = emptyList()
                                        refresh()
                                        snackbar.showSnackbar(
                                            if (n == total) "Listo: $n ${if (clipboardMove) "movidos" else "copiados"}"
                                            else "Se pudieron pasar $n de $total"
                                        )
                                    }
                                }
                            ) {
                                Icon(Icons.Filled.ContentPaste, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (busy) "Trabajando…" else "Pegar aquí")
                            }
                        }
                    }
                }
                // barra de abajo: Inicio · Archivos · Recientes
                if (selection.isEmpty()) ExplorerBottomNav(
                    selected = when { recentMode -> 2; currentDir == null -> 0; else -> 1 },
                    onSelect = { i ->
                        when (i) {
                            0 -> { recentMode = false; searchActive = false; goTo(null) }
                            1 -> { recentMode = false; searchActive = false; if (currentDir == null) goTo(lastDir ?: roots.first().file) }
                            2 -> { searchActive = false; recentMode = true }
                        }
                    })
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            // cuadrito flotante de música (en lugar de la barra de abajo)
            // con el panel de vista previa abierto, la música se controla desde el panel (sin cuadrito flotante)
            val paneShowing = LibraryStore.previewPane.isNotEmpty() && currentDir != null && searchResults == null
            androidx.compose.runtime.SideEffect {
                ExplorerAudio.controller = controller
                ExplorerVideo.onExpand = { onExpandVideo() }
            }
            @Suppress("UNUSED_VARIABLE") val unusedPane = paneShowing
            when {
                currentDir == null && searchResults == null -> HomeContent(roots, onOpen = { goTo(it) }, onOpenMusic = onOpenLibrary, onOpenGallery = onOpenGallery, onOpenVideos = onOpenVideos)
                else -> {
                    Column(Modifier.fillMaxSize()) {
                        val dir = currentDir
                        if (searchResults == null && dir != null) {
                            ExplorerCrumbs(dir, roots, onNavigate = { goTo(it) })
                            // fila de orden y vista (cuadrícula / lista) y panel de vista previa
                            Row(Modifier.fillMaxWidth().background(ExpTheme.bar).padding(start = 8.dp, end = 12.dp, bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                var sortMenu by remember { mutableStateOf(false) }
                                Box {
                                    Row(Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)).clickable { sortMenu = true }
                                        .padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Outlined.Tune, null, tint = ExpTheme.sub, modifier = Modifier.size(17.dp))
                                        Text("  ${sortMode.label}", color = ExpTheme.text, fontSize = 13.sp)
                                        Icon(if (ascending) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp, null, tint = ExpTheme.sub,
                                            modifier = Modifier.size(18.dp))
                                    }
                                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                                        SortMode.entries.forEach { mode ->
                                            DropdownMenuItem(
                                                text = { Text(mode.label + if (mode == sortMode) (if (ascending) "  ↑" else "  ↓") else "",
                                                    fontWeight = if (mode == sortMode) FontWeight.Bold else null) },
                                                onClick = {
                                                    if (mode == sortMode) ascending = !ascending
                                                    else { sortMode = mode; ascending = mode == SortMode.NAME }
                                                    sortMenu = false
                                                }
                                            )
                                        }
                                    }
                                }
                                Spacer(Modifier.weight(1f))
                                // panel de vista previa
                                var paneMenu by remember { mutableStateOf(false) }
                                val paneOn2 = LibraryStore.previewPane.isNotEmpty()
                                Box {
                                    Box(Modifier.padding(end = 6.dp).size(30.dp).clip(androidx.compose.foundation.shape.CircleShape)
                                        .background(if (paneOn2) ExpTheme.pill else ExpTheme.chipBg).clickable { paneMenu = true },
                                        contentAlignment = Alignment.Center) {
                                        Icon(Icons.Outlined.VerticalSplit, "Vista previa", tint = if (paneOn2) Color.White else ExpTheme.chipText, modifier = Modifier.size(15.dp))
                                    }
                                    DropdownMenu(expanded = paneMenu, onDismissRequest = { paneMenu = false }) {
                                        listOf("preview" to "Panel de vista previa", "details" to "Panel de detalles", "" to "Sin panel").forEach { (id, label) ->
                                            DropdownMenuItem(text = { Text(label, fontWeight = if (LibraryStore.previewPane == id) FontWeight.Bold else null) },
                                                onClick = { LibraryStore.changePreviewPane(id); paneMenu = false })
                                        }
                                    }
                                }
                                // cuadrícula / lista (mantén presionado para más vistas)
                                val view = ExplorerView.of(LibraryStore.explorerView)
                                val isGrid = view == ExplorerView.GRID || view == ExplorerView.BIG
                                var viewMenu by remember { mutableStateOf(false) }
                                Box {
                                    Row(Modifier.clip(androidx.compose.foundation.shape.CircleShape).background(ExpTheme.chipBg).padding(2.dp)) {
                                        listOf(true to Icons.Filled.GridView, false to Icons.Outlined.ViewList).forEach { (g, ic) ->
                                            val on = g == isGrid
                                            Box(Modifier.size(width = 38.dp, height = 28.dp).clip(androidx.compose.foundation.shape.CircleShape)
                                                .background(if (on) ExpTheme.pill else Color.Transparent)
                                                .combinedClickable(onClick = {
                                                    if (g != isGrid) LibraryStore.changeExplorerView(if (g) ExplorerView.GRID.id else ExplorerView.LIST.id)
                                                    else viewMenu = true
                                                }, onLongClick = { viewMenu = true }),
                                                contentAlignment = Alignment.Center) {
                                                Icon(ic, if (g) "Cuadrícula" else "Lista", tint = if (on) Color.White else ExpTheme.chipText, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                    DropdownMenu(expanded = viewMenu, onDismissRequest = { viewMenu = false }) {
                                        ExplorerView.entries.forEach { v ->
                                            DropdownMenuItem(
                                                text = { Text(v.label, fontWeight = if (v == view) FontWeight.Bold else null) },
                                                leadingIcon = { Icon(v.icon, null) },
                                                onClick = { LibraryStore.changeExplorerView(v.id); viewMenu = false }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        if (searchResults != null) {
                            if (!recentMode) SearchStatusBar(searching, searchProgress, searchWhere, searchResults?.size ?: 0)
                            FormatChips(formatCounts, searchResults?.size ?: 0, formatFilter) { formatFilter = it }
                        }
                        if (shownFiles.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    if (searchResults != null) (if (searching) "Buscando…" else "Sin resultados") else "Carpeta vacía",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            val listState = rememberLazyListState()
                            val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
                            LaunchedEffect(currentDir) { listState.scrollToItem(0); gridState.scrollToItem(0) }
                            val paneOn = LibraryStore.previewPane.isNotEmpty() && searchResults == null
                            val clickFile: (File) -> Unit = { file ->
                                if (selection.isNotEmpty()) toggleSelect(file)
                                // con el panel: el primer toque lo muestra, el segundo lo abre (como doble clic)
                                else if (paneOn && !file.isDirectory && focused != file) focused = file
                                else if (searchResults != null) {
                                    // leer la carpeta del resultado en segundo plano (en el hilo principal congelaba la app)
                                    scope.launch {
                                        val siblings = withContext(Dispatchers.IO) {
                                            file.parentFile?.let { listFolder(it, sortMode, ascending, showHidden) } ?: listOf(file)
                                        }
                                        openFile(file, siblings)
                                    }
                                } else openFile(file, shownFiles)
                            }
                            val filesView: @Composable () -> Unit = {
                            when (ExplorerView.of(LibraryStore.explorerView)) {
                                ExplorerView.COMPACT -> CompactFileList(shownFiles, selection, listState, clickFile, onLongClick = { toggleSelect(it) }, onMenu = { menuFile = it }, focused = if (paneOn) focused else null)
                                ExplorerView.GRID -> FileGrid(shownFiles, selection, big = false, showHidden = showHidden, state = gridState, onClick = clickFile, onLongClick = { toggleSelect(it) }, onMenu = { menuFile = it }, focused = if (paneOn) focused else null)
                                ExplorerView.BIG -> FileGrid(shownFiles, selection, big = true, showHidden = showHidden, state = gridState, onClick = clickFile, onLongClick = { toggleSelect(it) }, onMenu = { menuFile = it }, focused = if (paneOn) focused else null)
                                ExplorerView.LIST -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                                items(shownFiles, key = { it.absolutePath }) { file ->
                                    FileRow(
                                        file = file,
                                        selected = file in selection || (paneOn && file == focused),
                                        showPath = searchResults != null,
                                        showHidden = showHidden,
                                        onClick = { clickFile(file) },
                                        onLongClick = { toggleSelect(file) },
                                        onMenu = { menuFile = file }
                                    )
                                }
                                }
                            }
                            }
                            if (!paneOn) filesView()
                            else androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
                                val wide = maxWidth > maxHeight
                                val totalPx = with(androidx.compose.ui.platform.LocalDensity.current) { (if (wide) maxWidth else maxHeight).toPx() }
                                val pane: @Composable (Modifier) -> Unit = { m ->
                                    PreviewPane(focused, currentDir, LibraryStore.previewPane, onMode = { LibraryStore.changePreviewPane(it) },
                                        onOpen = { f -> openFile(f, shownFiles) }, onClose = { LibraryStore.changePreviewPane("") }, modifier = m,
                                        controller = controller, onExpandVideo = onExpandVideo, onCast = { castFile = it })
                                }
                                if (wide) Row(Modifier.fillMaxSize()) {
                                    Box(Modifier.weight(1f - paneFrac).fillMaxHeight()) { filesView() }
                                    PaneHandle(false) { d -> paneFrac = (paneFrac - d / totalPx).coerceIn(0.25f, 0.7f) }
                                    pane(Modifier.weight(paneFrac).fillMaxHeight())
                                } else Column(Modifier.fillMaxSize()) {
                                    Box(Modifier.weight(1f - paneFrac).fillMaxWidth()) { filesView() }
                                    PaneHandle(true) { d -> paneFrac = (paneFrac - d / totalPx).coerceIn(0.2f, 0.75f) }
                                    pane(Modifier.weight(paneFrac).fillMaxWidth())
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showHistory) VersionHistoryDialog(versionName, onDismiss = { showHistory = false })

    importCandidate?.let { f ->
        AlertDialog(
            onDismissRequest = { importCandidate = null },
            title = { Text("Lista de reproducción") },
            text = { Text("¿Quieres importar \"${f.nameWithoutExtension}\" a tus listas de música?") },
            confirmButton = {
                TextButton(onClick = {
                    importCandidate = null
                    scope.launch {
                        if (LibraryStore.songs.isEmpty()) LibraryStore.scan()
                        val (_, n) = LibraryStore.importM3u(f)
                        snackbar.showSnackbar("Lista importada con $n canciones. Está en Música → Listas.")
                    }
                }) { Text("Importar") }
            },
            dismissButton = {
                TextButton(onClick = { importCandidate = null; openExternally(context, f) }) { Text("Abrir con otra app") }
            }
        )
    }

    if (showNewFolder) {
        NameDialog(
            title = "Nueva carpeta",
            initial = "",
            confirmText = "Crear",
            onDismiss = { showNewFolder = false },
            onConfirm = { name ->
                showNewFolder = false
                val dir = currentDir ?: return@NameDialog
                val target = File(dir, name.trim())
                scope.launch {
                    val ok = withContext(Dispatchers.IO) { !target.exists() && target.mkdirs() }
                    refresh()
                    snackbar.showSnackbar(if (ok) "Carpeta creada" else "No se pudo crear (¿ya existe?)")
                }
            }
        )
    }

    // mandar a la TV (solo ese archivo; en fotos puedes pasar a la siguiente de la carpeta)
    castFile?.let { cf ->
        // solo por extensión (sin tocar el disco, para no trabar carpetas grandes)
        val imgExt = setOf("jpg", "jpeg", "png", "webp", "gif", "heic", "bmp")
        val sibs = remember(cf, shownFiles) {
            if (cf.extension.lowercase(Locale.ROOT) in imgExt) shownFiles.filter { it.extension.lowercase(Locale.ROOT) in imgExt } else listOf(cf)
        }
        CastPickerSheet(cf, sibs) { castFile = null }
    }

    // documentos: texto, Word, Excel, PowerPoint, HTML y XML
    docFile?.let { df -> DocumentViewer(df) { docFile = null } }
    modelFile?.let { mf -> Model3DViewer(mf) { modelFile = null } }

    // comprimidos
    archiveFile?.let { af ->
        ArchiveViewer(af, onClose = { archiveFile = null }, onOpen = { f -> openFile(f, listOf(f)) },
            onExtracted = { refresh() })
    }
    if (compressItems.isNotEmpty()) {
        val items = compressItems
        CompressDialog(items, currentDir ?: items.first().parentFile ?: File("/sdcard"), onDismiss = { compressItems = emptyList() }) {
            compressItems = emptyList(); refresh()
        }
    }

    // abrir archivos desde un acceso directo del escritorio
    AppNav.openFile = { f ->
        if (f.exists()) openFile(f, listOf(f))
        else android.widget.Toast.makeText(context, "Ese archivo ya no está ahí", android.widget.Toast.LENGTH_SHORT).show()
    }

    // mini ventana para elegir a dónde copiar o mover, sin salir de donde estás
    if (transferFiles.isNotEmpty()) {
        val files = transferFiles
        val move = transferMove
        FolderWindow(
            title = if (move) "Mover ${files.size} elemento(s) a…" else "Copiar ${files.size} elemento(s) a…",
            confirm = if (move) "Mover aquí" else "Pegar aquí",
            start = currentDir,
            onDismiss = { transferFiles = emptyList() },
            onPick = { dest ->
                transferFiles = emptyList()
                scope.launch {
                    val n = if (move) moveInto(files, dest) else copyInto(files, dest)
                    refresh()
                    snackbar.showSnackbar(if (n == files.size) "Listo: $n ${if (move) "movidos" else "copiados"} a ${dest.name}"
                        else "Se ${if (move) "movieron" else "copiaron"} $n de ${files.size}")
                }
            }
        )
    }

    // menú ⋮ de un archivo
    menuFile?.let { f ->
        val options = listOf<Pair<String, () -> Unit>>(
            "Abrir" to { openFile(f, shownFiles) },
            "Copiar a…" to { transferFiles = listOf(f); transferMove = false },
            "Mover a…" to { transferFiles = listOf(f); transferMove = true },
            "Renombrar" to { renameTarget = f },
            "Compartir" to { shareFiles(context, listOf(f)) },
            *(if (f.isFile && Archives.isArchive(f)) arrayOf<Pair<String, () -> Unit>>("Ver contenido y extraer" to { archiveFile = f }) else emptyArray()),
            "Comprimir…" to { compressItems = listOf(f) },
            *(if (f.isFile && f.kind() == FileKind.AUDIO) arrayOf<Pair<String, () -> Unit>>("Abrir en el reproductor" to {
                if (!ExplorerAudio.isCurrent(f)) ExplorerAudio.toggle(f)
                onOpenMusicPlayer()
            }) else emptyArray()),
            *(if (f.isFile && (f.kind() == FileKind.VIDEO || f.kind() == FileKind.AUDIO || f.kind() == FileKind.IMAGE))
                arrayOf<Pair<String, () -> Unit>>("Mandar a la TV" to { castFile = f }) else emptyArray()),
            "Acceso directo en el escritorio" to {
                LibraryStore.addFileShortcut(f.absolutePath)
                android.widget.Toast.makeText(context, "Acceso directo creado en el escritorio", android.widget.Toast.LENGTH_SHORT).show()
            },
            "Seleccionar" to { toggleSelect(f) },
            "Eliminar (a la Papelera)" to {
                scope.launch {
                    val ok = Trash.moveToTrash(listOf(f)) == 1
                    refresh()
                    snackbar.showSnackbar(if (ok) "Enviado a la papelera" else "No se pudo eliminar")
                }
            },
        ).filter { f.isFile || it.first != "Compartir" }
        FileMenuSheet(f, options, onDismiss = { menuFile = null })
    }

    renameTarget?.let { target ->
        NameDialog(
            title = "Renombrar",
            initial = target.name,
            confirmText = "Guardar",
            onDismiss = { renameTarget = null },
            onConfirm = { name ->
                renameTarget = null
                val newFile = File(target.parentFile, name.trim())
                scope.launch {
                    val ok = withContext(Dispatchers.IO) { !newFile.exists() && target.renameTo(newFile) }
                    selection = emptySet()
                    refresh()
                    snackbar.showSnackbar(if (ok) "Renombrado" else "No se pudo renombrar")
                }
            }
        )
    }

    if (confirmDelete) {
        val toDelete = selection.toList()
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("¿Eliminar ${toDelete.size} elemento(s)?") },
            text = { Text("Se van a la Papelera. Desde ahí los puedes restaurar o borrar para siempre.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        val n = Trash.moveToTrash(toDelete)
                        selection = emptySet()
                        refresh()
                        snackbar.showSnackbar(if (n == toDelete.size) "Enviados a la papelera: $n" else "Se enviaron $n de ${toDelete.size} a la papelera")
                    }
                }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun HomeContent(roots: List<StorageRoot>, onOpen: (File) -> Unit, onOpenMusic: () -> Unit, onOpenGallery: () -> Unit, onOpenVideos: () -> Unit) {
    val internal = roots.first().file
    val shortcuts = listOf(
        Triple("Descargas", Icons.Filled.Download, File(internal, Environment.DIRECTORY_DOWNLOADS)),
        Triple("Cámara", Icons.Filled.PhotoCamera, File(internal, Environment.DIRECTORY_DCIM)),
        Triple("Galería", Icons.Filled.PhotoLibrary, File(internal, Environment.DIRECTORY_PICTURES)),
        Triple("Música", Icons.Filled.LibraryMusic, File(internal, Environment.DIRECTORY_MUSIC)),
        Triple("Videos", Icons.Filled.Movie, File(internal, Environment.DIRECTORY_MOVIES)),
        Triple("Documentos", Icons.Filled.Drafts, File(internal, Environment.DIRECTORY_DOCUMENTS)),
    ).filter { it.first == "Galería" || it.first == "Videos" || it.third.exists() }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item {
            Text("Almacenamiento", style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
        }
        items(roots) { root ->
            val total = root.file.totalSpace
            val free = root.file.freeSpace
            Surface(
                shape = RoundedCornerShape(16.dp),
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(16.dp))
                    .clickable { onOpen(root.file) }
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (root.name.startsWith("Tarjeta")) Icons.Filled.SdStorage else Icons.Filled.PhoneAndroid,
                        null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(root.name, fontWeight = FontWeight.SemiBold)
                        if (total > 0) {
                            Text("Libre ${formatSize(free)} de ${formatSize(total)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.size(6.dp))
                            androidx.compose.material3.LinearProgressIndicator(
                                progress = { ((total - free).toFloat() / total).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
        if (shortcuts.isNotEmpty()) {
            item {
                Text("Accesos rápidos", style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
            }
            items(shortcuts) { (label, icon, dir) ->
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .clickable {
                            when (label) {
                                "Música" -> onOpenMusic()
                                "Galería" -> onOpenGallery()
                                "Videos" -> onOpenVideos()
                                else -> onOpen(dir)
                            }
                        }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(icon, null, tint = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.width(16.dp))
                    Text(label)
                }
            }
        }
    }
}

@Composable
private fun PathBar(dir: File, roots: List<StorageRoot>, onNavigate: (File) -> Unit) {
    val root = roots.firstOrNull { dir.absolutePath.startsWith(it.file.absolutePath) }
    val crumbs = mutableListOf<Pair<String, File>>()
    var f: File? = dir
    while (f != null) {
        val isRoot = root != null && f.absolutePath == root.file.absolutePath
        crumbs.add(0, (if (isRoot) root!!.name else f.name) to f)
        if (isRoot) break
        f = f.parentFile
    }
    val scroll = rememberScrollState()
    LaunchedEffect(dir) { scroll.scrollTo(scroll.maxValue) }
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(scroll).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        crumbs.forEachIndexed { i, (name, file) ->
            if (i > 0) Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 2.dp))
            Text(
                name,
                style = MaterialTheme.typography.bodySmall,
                color = if (i == crumbs.lastIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { onNavigate(file) }.padding(6.dp)
            )
        }
    }
    HorizontalDivider()
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileRow(
    file: File,
    selected: Boolean,
    showPath: Boolean,
    showHidden: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMenu: () -> Unit = {},
) {
    val kind = remember(file) { file.kind() }
    val subtitle by produceState(initialValue = "", file, showHidden) {
        value = withContext(Dispatchers.IO) {
            val info = if (kind == FileKind.FOLDER) "${folderItemCount(file, showHidden)} elementos"
            else formatSize(file.length())
            val base = "$info · ${formatDate(file.lastModified())}"
            if (showPath) "${file.parent ?: ""}\n$base" else base
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
            when (kind) {
                FileKind.IMAGE -> {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
                    AsyncImage(model = file, contentDescription = null, contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize())
                }
                FileKind.VIDEO, FileKind.AUDIO -> FilePreview(file, kind, 32.dp, Modifier.fillMaxSize())
                else -> if (file.isPdf() || is3dModel(file)) FilePreview(file, kind, 32.dp, Modifier.fillMaxSize(), richPdf = true)
                    else FriendlyIcon(file, kind, 40.dp)
            }
            if (selected) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)))
                Icon(Icons.Filled.CheckCircle, null, tint = Color.White)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        MoreDots(onClick = onMenu)
    }
}


@Composable
private fun NameDialog(
    title: String,
    initial: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    val valid = text.isNotBlank() && !text.contains('/')
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true,
                label = { Text("Nombre") }, isError = text.contains('/'))
        },
        confirmButton = { TextButton(enabled = valid, onClick = { onConfirm(text) }) { Text(confirmText) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

private fun uriFor(context: Context, file: File): Uri =
    FileProvider.getUriForFile(context, "${context.packageName}.files", file)

private fun mimeFor(file: File): String =
    MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase(Locale.ROOT)) ?: "*/*"

fun openExternally(context: Context, file: File) {
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uriFor(context, file), mimeFor(file))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        context.startActivity(Intent.createChooser(intent, "Abrir con"))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No hay app para abrir este archivo", Toast.LENGTH_SHORT).show()
    } catch (e: IllegalArgumentException) {
        Toast.makeText(context, "No se puede abrir este archivo", Toast.LENGTH_SHORT).show()
    }
}

fun shareFiles(context: Context, files: List<File>) {
    if (files.isEmpty()) {
        Toast.makeText(context, "Solo se pueden compartir archivos, no carpetas", Toast.LENGTH_SHORT).show()
        return
    }
    val uris = ArrayList(files.map { uriFor(context, it) })
    val intent = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).setType(mimeFor(files.first())).putExtra(Intent.EXTRA_STREAM, uris.first())
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).setType("*/*").putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
    }
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(intent, "Compartir"))
}
