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
import androidx.compose.ui.draw.clip
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

@OptIn(ExperimentalMaterial3Api::class)
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
    onOpenMyPc: () -> Unit,
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

    var showNewFolder by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<File?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var importCandidate by remember { mutableStateOf<File?>(null) }
    val skin = skinFor(LibraryStore.theme)

    val files by produceState(initialValue = emptyList<File>(), currentDir, sortMode, ascending, showHidden, refreshKey) {
        val dir = currentDir
        value = if (dir == null) emptyList() else withContext(Dispatchers.IO) {
            listFolder(dir, sortMode, ascending, showHidden)
        }
    }

    LaunchedEffect(query, searchActive, showHidden, currentDir) {
        if (!searchActive || query.isBlank()) { searchResults = null; return@LaunchedEffect }
        delay(400)
        val base = currentDir ?: roots.first().file
        searchResults = searchFiles(base, query, showHidden)
    }

    fun refresh() { refreshKey++ }
    fun goTo(dir: File?) {
        currentDir = dir
        selection = emptySet()
        searchActive = false
        query = ""
    }

    LaunchedEffect(homeSignal) { if (homeSignal > 0) goTo(null) }
    LaunchedEffect(folderRequest) { folderRequest?.let { goTo(it); onFolderHandled() } }

    fun openFile(file: File, siblings: List<File>) {
        when (file.kind()) {
            FileKind.FOLDER -> goTo(file)
            FileKind.IMAGE -> {
                val list = siblings.filter { it.kind() == FileKind.IMAGE }
                onOpenImages(list, list.indexOf(file).coerceAtLeast(0))
            }
            FileKind.VIDEO -> {
                val list = siblings.filter { it.kind() == FileKind.VIDEO }
                onOpenVideo(list, list.indexOf(file).coerceAtLeast(0))
            }
            FileKind.AUDIO -> {
                val list = siblings.filter { it.kind() == FileKind.AUDIO }
                onPlayMusic(list, list.indexOf(file).coerceAtLeast(0))
            }
            FileKind.OTHER -> {
                val ext = file.extension.lowercase(Locale.ROOT)
                if (ext == "m3u8" || ext == "m3u") importCandidate = file else openExternally(context, file)
            }
        }
    }

    fun toggleSelect(file: File) {
        selection = if (file in selection) selection - file else selection + file
    }

    // Botón atrás: selección → búsqueda → carpeta anterior → salir
    val isRoot = currentDir != null && roots.any { it.file.absolutePath == currentDir?.absolutePath }
    BackHandler(enabled = selection.isNotEmpty() || searchActive || currentDir != null) {
        when {
            selection.isNotEmpty() -> selection = emptySet()
            searchActive -> { searchActive = false; query = "" }
            isRoot -> goTo(null)
            else -> goTo(currentDir?.parentFile)
        }
    }

    val shownFiles = searchResults ?: files

    if (skin.isRetro && currentDir == null && !searchActive) {
        XpDesktop(
            roots = roots,
            onOpenFolder = { goTo(it) },
            onOpenMusic = onOpenLibrary,
            onOpenMyPc = onOpenMyPc,
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
                selection.isNotEmpty() -> TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { selection = emptySet() }) { Icon(Icons.Filled.Close, "Cancelar") }
                    },
                    title = { Text("${selection.size} seleccionados") },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    actions = {
                        IconButton(onClick = { selection = shownFiles.toSet() }) { Icon(Icons.Filled.SelectAll, "Seleccionar todo") }
                        IconButton(onClick = {
                            clipboard = selection.toList(); clipboardMove = false; selection = emptySet()
                        }) { Icon(Icons.Filled.ContentCopy, "Copiar") }
                        IconButton(onClick = {
                            clipboard = selection.toList(); clipboardMove = true; selection = emptySet()
                        }) { Icon(Icons.Filled.ContentCut, "Mover") }
                        var more by remember { mutableStateOf(false) }
                        IconButton(onClick = { more = true }) { Icon(Icons.Filled.MoreVert, "Más") }
                        DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                            if (selection.size == 1) {
                                DropdownMenuItem(text = { Text("Renombrar") }, leadingIcon = { Icon(Icons.Filled.Edit, null) },
                                    onClick = { more = false; renameTarget = selection.first() })
                            }
                            DropdownMenuItem(text = { Text("Compartir") }, leadingIcon = { Icon(Icons.Filled.Share, null) },
                                onClick = { more = false; shareFiles(context, selection.filter { it.isFile }) })
                            DropdownMenuItem(text = { Text("Eliminar") }, leadingIcon = { Icon(Icons.Filled.Delete, null) },
                                onClick = { more = false; confirmDelete = true })
                        }
                    }
                )
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
                else -> Box(if (skin.titleBar != null) Modifier.background(skin.titleBar) else Modifier) { TopAppBar(
                    colors = if (skin.titleBar != null) TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White,
                        actionIconContentColor = Color.White,
                    ) else TopAppBarDefaults.topAppBarColors(),
                    navigationIcon = {
                        if (currentDir != null) {
                            IconButton(onClick = { if (isRoot) goTo(null) else goTo(currentDir?.parentFile) }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás")
                            }
                        }
                    },
                    title = {
                        Text(
                            when {
                                currentDir == null -> "Explorador Tony"
                                isRoot -> roots.first { it.file.absolutePath == currentDir?.absolutePath }.name
                                else -> currentDir!!.name
                            },
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    },
                    actions = {
                        if (currentDir != null) {
                            IconButton(onClick = { goTo(null) }) { Icon(Icons.Filled.Home, "Inicio") }
                        }
                        IconButton(onClick = { searchActive = true }) { Icon(Icons.Filled.Search, "Buscar") }
                        if (currentDir != null) {
                            var sortMenu by remember { mutableStateOf(false) }
                            IconButton(onClick = { sortMenu = true }) { Icon(Icons.AutoMirrored.Filled.Sort, "Ordenar") }
                            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                                SortMode.entries.forEach { mode ->
                                    DropdownMenuItem(
                                        text = { Text(mode.label + if (mode == sortMode) (if (ascending) "  ↑" else "  ↓") else "") },
                                        onClick = {
                                            if (mode == sortMode) ascending = !ascending
                                            else { sortMode = mode; ascending = mode == SortMode.NAME }
                                            sortMenu = false
                                        }
                                    )
                                }
                            }
                        }
                        var more by remember { mutableStateOf(false) }
                        IconButton(onClick = { more = true }) { Icon(Icons.Filled.MoreVert, "Más") }
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
                if (controller != null) {
                    MiniPlayer(controller, onOpen = onOpenMusicPlayer)
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                currentDir == null && searchResults == null -> HomeContent(roots, onOpen = { goTo(it) }, onOpenMusic = onOpenLibrary)
                else -> {
                    Column(Modifier.fillMaxSize()) {
                        val dir = currentDir
                        if (searchResults == null && dir != null) {
                            PathBar(dir, roots, onNavigate = { goTo(it) })
                        }
                        if (shownFiles.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    if (searchResults != null) "Sin resultados" else "Carpeta vacía",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            val listState = rememberLazyListState()
                            LaunchedEffect(currentDir) { listState.scrollToItem(0) }
                            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                                items(shownFiles, key = { it.absolutePath }) { file ->
                                    FileRow(
                                        file = file,
                                        selected = file in selection,
                                        showPath = searchResults != null,
                                        showHidden = showHidden,
                                        onClick = {
                                            if (selection.isNotEmpty()) toggleSelect(file)
                                            else {
                                                val siblings = if (searchResults != null)
                                                    file.parentFile?.let { listFolder(it, sortMode, ascending, showHidden) } ?: listOf(file)
                                                else shownFiles
                                                openFile(file, siblings)
                                            }
                                        },
                                        onLongClick = { toggleSelect(file) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

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
            text = { Text("Se borran para siempre, no van a una papelera.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        val n = deleteAll(toDelete)
                        selection = emptySet()
                        refresh()
                        snackbar.showSnackbar(if (n == toDelete.size) "Eliminados: $n" else "Se eliminaron $n de ${toDelete.size}")
                    }
                }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun HomeContent(roots: List<StorageRoot>, onOpen: (File) -> Unit, onOpenMusic: () -> Unit) {
    val internal = roots.first().file
    val shortcuts = listOf(
        Triple("Descargas", Icons.Filled.Download, File(internal, Environment.DIRECTORY_DOWNLOADS)),
        Triple("Cámara", Icons.Filled.PhotoCamera, File(internal, Environment.DIRECTORY_DCIM)),
        Triple("Imágenes", Icons.Filled.PhotoLibrary, File(internal, Environment.DIRECTORY_PICTURES)),
        Triple("Música", Icons.Filled.LibraryMusic, File(internal, Environment.DIRECTORY_MUSIC)),
        Triple("Películas", Icons.Filled.Movie, File(internal, Environment.DIRECTORY_MOVIES)),
        Triple("Documentos", Icons.Filled.Drafts, File(internal, Environment.DIRECTORY_DOCUMENTS)),
    ).filter { it.third.exists() }

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
                        .clickable { if (label == "Música") onOpenMusic() else onOpen(dir) }.padding(12.dp),
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
                FileKind.IMAGE, FileKind.VIDEO -> {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
                    AsyncImage(model = file, contentDescription = null, contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize())
                    if (kind == FileKind.VIDEO) {
                        Icon(Icons.Filled.VideoFile, null, tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(18.dp).align(Alignment.BottomEnd))
                    }
                }
                else -> Icon(kindIcon(kind), null, modifier = Modifier.size(32.dp), tint = kindColor(kind))
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
    }
}

private fun kindIcon(kind: FileKind): ImageVector = when (kind) {
    FileKind.FOLDER -> Icons.Filled.Folder
    FileKind.IMAGE -> Icons.Filled.Image
    FileKind.AUDIO -> Icons.Filled.AudioFile
    FileKind.VIDEO -> Icons.Filled.VideoFile
    FileKind.OTHER -> Icons.AutoMirrored.Filled.InsertDriveFile
}

private fun kindColor(kind: FileKind): Color = when (kind) {
    FileKind.FOLDER -> Color(0xFFF5B800)
    FileKind.IMAGE -> Color(0xFF43A047)
    FileKind.AUDIO -> Color(0xFFE53935)
    FileKind.VIDEO -> Color(0xFF8E24AA)
    FileKind.OTHER -> Color(0xFF757575)
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
