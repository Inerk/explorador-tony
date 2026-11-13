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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewHeadline
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
    private val vin get() = LibraryStore.musicSkin == MusicSkin.VINTAGE
    private val org get() = LibraryStore.musicSkin == MusicSkin.ORIGINAL
    private val lav get() = LibraryStore.musicSkin == MusicSkin.LAVANDA
    val isWmp get() = wmp
    val isVintage get() = vin
    val isOriginal get() = org
    val isLavanda get() = lav
    private val now get() = NowPlaying.colors
    private fun pick(walkman: Color, media: Color, vintage: Color, original: Color = walkman, lavanda: Color = walkman) =
        if (lav) lavanda else if (org) original else if (vin) vintage else if (wmp) media else walkman
    val Bg get() = pick(Color(0xFF111114), Color(0xFF0B1630), Color(0xFF1E140D), now.bottom, Color(0xFFF7F4FD))
    /** Fondo: en Original es el degradado de la portada que suena, como en el reproductor. */
    val bgBrush: Brush get() = when {
        org -> Brush.verticalGradient(listOf(now.top, now.bottom, Color(0xFF07080B)))
        lav -> Brush.verticalGradient(listOf(Color(0xFFFBF9FF), Color(0xFFF3EEFC)))
        else -> Brush.verticalGradient(listOf(Bg, Bg))
    }
    val Panel get() = pick(Color(0xFF1C1C22), Color(0xFF14284F), Color(0xFF34231A), Color.White.copy(alpha = 0.07f), Color.White)
    val Orange get() = pick(Color(0xFFFF7A00), Color(0xFF3FA9F5), Vintage.Brass, now.accent, Color(0xFF7C4DDB))
    val OrangeLight get() = pick(Color(0xFFFFA347), Color(0xFF9CDBFF), Vintage.BrassLight, now.accent.copy(alpha = 0.7f), Color(0xFFB79BF2))
    val OnAccent get() = pick(Color.Black, Color.White, Color(0xFF2A1A0E), now.onAccent, Color.White)
    val Text get() = pick(Color(0xFFF2F2F2), Color(0xFFF2F2F2), Vintage.Cream, Color.White, Color(0xFF221B33))
    val SubText get() = pick(Color(0xFF9A9AA5), Color(0xFF8FA6CC), Vintage.CreamDim, Color.White.copy(alpha = 0.6f), Color(0xFF7D7394))
    val HeaderText get() = pick(Color(0xFF1E1E24), Color.White, Vintage.Cream, Color.White, Color(0xFF221B33))
    val HeaderIcon get() = pick(Color(0xFF2B2B33), Color(0xFFCFE3FF), Vintage.BrassLight, Color.White, Color(0xFF6A3FD0))
    /** Resalte de la canción que suena / tocado. */
    val Highlight get() = if (lav) Color(0xFF7C4DDB).copy(alpha = 0.10f) else Color.White.copy(alpha = 0.10f)
    val metal: Brush get() = when {
        lav -> Brush.verticalGradient(listOf(Color(0xFFFBF9FF), Color(0xFFF3EEFC)))
        org -> Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.25f), Color.Transparent))
        vin -> Brush.verticalGradient(listOf(Color(0xFF8B5A2B), Color(0xFF5A3517), Color(0xFF3A2212)))
        wmp -> Brush.verticalGradient(listOf(Color(0xFF4F76B5), Color(0xFF1C3463), Color(0xFF0A1630)))
        else -> Brush.verticalGradient(listOf(Color(0xFFE9EBEF), Color(0xFFB9BDC6), Color(0xFF8C919C)))
    }
    // colores de cada sección (en Lavanda todos son tonos de morado)
    val Red get() = pick(Color(0xFFE53935), Color(0xFF5B8DEF), Color(0xFFB5562A), Color(0xFFFF5A5F), Color(0xFF8E5BE8))
    val Blue get() = pick(Color(0xFF1E88E5), Color(0xFF2D7DD2), Color(0xFF2F6B66), Color(0xFF4D8DFF), Color(0xFF7C4DDB))
    val Lime get() = pick(Color(0xFF7CB342), Color(0xFF2FBF9A), Color(0xFF6B7A2E), Color(0xFF34C759), Color(0xFF9B6CF0))
    val Pink get() = pick(Color(0xFFD81B60), Color(0xFF8E6CEF), Color(0xFF8E2C2C), Color(0xFFFF4FA3), Color(0xFFA77BF3))
    val Yellow get() = pick(Color(0xFFFDD835), Color(0xFFF2C94C), Color(0xFFC9A24A), Color(0xFFFFC83D), Color(0xFF6A3FD0))
    val Teal get() = pick(Color(0xFF00ACC1), Color(0xFF2EC4E6), Color(0xFF3E7A8A), Color(0xFF2FD3E0), Color(0xFF8A62E6))
    val Purple get() = pick(Color(0xFF8E24AA), Color(0xFF7B61FF), Color(0xFF6A3B5C), Color(0xFFAF6BFF), Color(0xFF7C4DDB))
    val Gray get() = pick(Color(0xFF78909C), Color(0xFF6F86A8), Color(0xFF6E5A48), Color(0xFF8E9BB0), Color(0xFF9C8FBF))
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
    data object Covers : LibPage
    data object MusicVideos : LibPage
    data object Nas : LibPage
    data object AnimatedCoversP : LibPage
    data object LyricsP : LibPage
}

@Composable
fun LibraryScreen(
    onPlay: (List<Song>, Int, Boolean) -> Unit,
    onClose: () -> Unit,
    miniPlayer: @Composable () -> Unit,
    onHome: () -> Unit = {},
    resetSignal: Int = 0,
    openRequest: Pair<String, String>? = null,
    onRequestHandled: () -> Unit = {},
    onOpenFolder: (String) -> Unit = {},
    menuOpenSignal: Int = 0,
    menuCloseSignal: Int = 0,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val stack = remember { mutableStateListOf<LibPage>(LibPage.Menu) }
    val page = stack.last()
    fun push(p: LibPage) { stack.add(p) }
    fun pop() { if (stack.size > 1) stack.removeAt(stack.lastIndex) else onClose() }
    // menú principal que se desliza encima de donde estés (0 = escondido, 1 = abierto)
    val drawer = remember { androidx.compose.animation.core.Animatable(0f) }
    var screenW by remember { androidx.compose.runtime.mutableFloatStateOf(1080f) }
    val drawerSpring = androidx.compose.animation.core.spring<Float>(dampingRatio = 0.8f, stiffness = 320f)
    fun setDrawer(open: Boolean, velocity: Float = 0f) {
        scope.launch { drawer.animateTo(if (open) 1f else 0f, drawerSpring, initialVelocity = velocity) }
    }
    fun openFromDrawer(p: LibPage) {
        if (stack.last() != p) {
            while (stack.size > 1) stack.removeAt(stack.lastIndex)
            if (p != LibPage.Menu) stack.add(p)
        }
        setDrawer(false)
    }
    BackHandler { if (drawer.targetValue > 0f) setDrawer(false) else pop() }
    // al deslizar el reproductor: si estabas en una lista, el menú principal ya aparece encima
    LaunchedEffect(menuOpenSignal) { if (menuOpenSignal > 0 && stack.size > 1) drawer.snapTo(1f) }
    LaunchedEffect(menuCloseSignal) { if (menuCloseSignal > 0) drawer.snapTo(0f) }
    LaunchedEffect(openRequest) {
        openRequest?.let { (kind, value) ->
            while (stack.size > 1) stack.removeAt(stack.lastIndex)
            stack.add(if (kind == "artist") LibPage.Artist(value) else LibPage.Album(value))
            onRequestHandled()
        }
    }
    LaunchedEffect(resetSignal) {
        if (resetSignal > 0) { while (stack.size > 1) stack.removeAt(stack.lastIndex) }
    }

    LaunchedEffect(Unit) {
        LibraryStore.scan()
        if (CoverScanner.auto) CoverScanner.start()
        if (AnimatedScanner.auto) AnimatedScanner.start()
        if (LyricsLocal.auto) LyricsLocal.start()
    }
    val libGoTo: (String, String) -> Unit = { kind, value ->
        when (kind) {
            "artist" -> stack.add(LibPage.Artist(value))
            "album" -> stack.add(LibPage.Album(value))
            else -> onOpenFolder(value)
        }
    }

    val allSongs = LibraryStore.songs
    // si una canción está en audio y en video, en las listas sale una sola vez (el video queda en «Videos musicales»)
    val songs = remember(allSongs) { allSongs.filterNot { isVideoPath(it.path) && LibraryStore.pairFor(it.path) != null } }
    var addTo by remember { mutableStateOf<List<Song>?>(null) }
    var menuQuery by remember { mutableStateOf("") }
    var menuField by remember { mutableStateOf("all") }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .onSizeChanged { screenW = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(Unit) {
                // deslizar a la derecha jala el menú principal encima (sigue tu dedo, como la portada)
                val tracker = androidx.compose.ui.input.pointer.util.VelocityTracker()
                var active = false
                detectHorizontalDragGestures(
                    onDragStart = { tracker.resetTracking(); active = stack.size > 1 || drawer.value > 0f },
                    onDragEnd = {
                        if (!active) return@detectHorizontalDragGestures
                        val v = tracker.calculateVelocity().x
                        val open = when {
                            v > 700f -> true
                            v < -700f -> false
                            else -> drawer.value > 0.4f
                        }
                        setDrawer(open, v / (screenW * 0.86f))
                    },
                    onDragCancel = { if (active) setDrawer(drawer.value > 0.5f) }
                ) { change, drag ->
                    if (!active) return@detectHorizontalDragGestures
                    change.consume()
                    tracker.addPosition(change.uptimeMillis, change.position)
                    scope.launch { drawer.snapTo((drawer.value + drag / (screenW * 0.86f)).coerceIn(0f, 1f)) }
                }
            }
    ) {
    val d = drawer.value.coerceIn(0f, 1f) // el resorte puede pasarse tantito de 0 o de 1
    Column(
        Modifier.fillMaxSize()
            .graphicsLayer {
                // la pantalla de atrás se hace a un lado, se encoge y se ladea un poquito
                translationX = d * 44.dp.toPx()
                val sc = 1f - 0.08f * d
                scaleX = sc; scaleY = sc
                rotationZ = 1.6f * d
                shape = RoundedCornerShape((26 * d).dp)
                clip = d > 0f
            }
            .background(Retro.bgBrush)
    ) {
        // Encabezado: en el menú principal es limpio (el título grande va abajo); en las listas, el metálico
        val clean = page == LibPage.Menu
        if (clean) Box(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))) {
            Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás", tint = Retro.Text) }
                Spacer(Modifier.weight(1f))
                Text("${songs.size} canciones", color = Retro.SubText, fontSize = 13.sp, modifier = Modifier.padding(end = 6.dp))
                HomeButton(onHome, dark = Retro.isWmp)
                IconButton(onClick = { scope.launch { LibraryStore.scan() } }) { Icon(Icons.Filled.Refresh, "Volver a buscar", tint = Retro.Text) }
            }
        } else Box(Modifier.fillMaxWidth().background(Retro.metal).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))) {
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
                        fontFamily = if (Retro.isVintage) FontFamily.Serif else null,
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
        if (!clean) Box(Modifier.fillMaxWidth().height(3.dp).background(Brush.horizontalGradient(listOf(Retro.Orange, Retro.OrangeLight, Retro.Orange))))
        if (LibraryStore.scanning) {
            LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp), color = Retro.Orange, trackColor = Retro.Panel)
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            androidx.compose.animation.AnimatedContent(
                targetState = stack.size to page,
                transitionSpec = {
                    // como la portada: entra con resorte, la de atrás se hace a un lado y se encoge
                    val forward = targetState.first >= initialState.first
                    val dir = if (forward) 1 else -1
                    val move = androidx.compose.animation.core.spring<androidx.compose.ui.unit.IntOffset>(dampingRatio = 0.82f, stiffness = 300f)
                    val grow = androidx.compose.animation.core.spring<Float>(dampingRatio = 0.82f, stiffness = 300f)
                    ((androidx.compose.animation.slideInHorizontally(move) { dir * it * 2 / 3 } +
                        androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(220)) +
                        androidx.compose.animation.scaleIn(grow, initialScale = 0.9f)) togetherWith
                        (androidx.compose.animation.slideOutHorizontally(move) { -dir * it / 5 } +
                            androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(240)) +
                            androidx.compose.animation.scaleOut(grow, targetScale = 0.9f))).apply {
                        targetContentZIndex = if (forward) 1f else -1f
                    }
                },
                label = "pages"
            ) { (_, shownPage) ->
            androidx.compose.runtime.CompositionLocalProvider(LocalLibGoTo provides libGoTo) {
            when (val p = shownPage) {
                LibPage.Menu -> Column(Modifier.fillMaxSize()) {
                    Text("Biblioteca", color = Retro.Text, fontSize = 34.sp, fontWeight = FontWeight.Black,
                        fontFamily = if (Retro.isVintage) FontFamily.Serif else null,
                        modifier = Modifier.padding(start = 20.dp, top = 4.dp, bottom = 8.dp))
                    LibSearchField(menuQuery, { menuQuery = it }, "Buscar canción, artista o álbum")
                    if (menuQuery.isNotBlank()) SearchFieldChips(songs, menuQuery, menuField) { menuField = it }
                    if (menuQuery.isBlank()) MainMenu(songs.size, onOpen = { push(it) }, big = true, onPlay = onPlay)
                    else SongList(songs.filter { songMatches(it, menuQuery, menuField) }, onPlay, onAdd = { addTo = it }, searchable = false,
                        empty = "No encontré nada con «${menuQuery.trim()}»")
                }
                LibPage.Songs -> SongList(songs, onPlay, onAdd = { addTo = it }, alphabet = true)
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
                LibPage.Covers -> CoversPage()
                LibPage.Nas -> NasPage()
                LibPage.MusicVideos -> SongList(allSongs.filter { isVideoPath(it.path) }, onPlay, onAdd = { addTo = it }, alphabet = true,
                    empty = "No hay videos musicales.\nPon tus videos dentro de alguna de tus “Carpetas de música”\ny toca ⟳ para volver a buscar.")
                LibPage.AnimatedCoversP -> AnimatedCoversPage()
                LibPage.LyricsP -> LyricsLocalPage()
            }
            }
            }
        }
        miniPlayer()
    }
    if (d > 0f) {
        // velo oscuro: tocarlo cierra el menú
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f * d))
                .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) { setDrawer(false) }
        )
        // el menú principal, entrando desde la izquierda
        Column(
            Modifier.fillMaxHeight().fillMaxWidth(0.86f)
                .graphicsLayer {
                    translationX = (d - 1f) * size.width
                    shadowElevation = 24.dp.toPx() * d
                    shape = RoundedCornerShape(topEnd = 26.dp, bottomEnd = 26.dp)
                    clip = true
                }
                .background(Retro.bgBrush)
                .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {}
        ) {
            Box(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))) {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 8.dp), verticalAlignment = Alignment.Bottom) {
                    Text("Biblioteca", color = Retro.Text, fontWeight = FontWeight.Black, fontSize = 30.sp,
                        fontFamily = if (Retro.isVintage) FontFamily.Serif else null, modifier = Modifier.weight(1f))
                    Text("${songs.size} canciones", color = Retro.SubText, fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp))
                }
            }
            Box(Modifier.weight(1f)) { MainMenu(songs.size, onOpen = { openFromDrawer(it) }, current = stack.last()) }
        }
    }
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
    LibPage.Covers -> "Carátulas en internet"
    LibPage.MusicVideos -> "Videos musicales"
    LibPage.Nas -> "Sincronizar con NAS"
    LibPage.AnimatedCoversP -> "Carátulas animadas"
    LibPage.LyricsP -> "Letras"
}

// ───────── Menú principal ─────────

@Composable
private fun MainMenu(
    songCount: Int, onOpen: (LibPage) -> Unit, current: LibPage? = null,
    big: Boolean = false, onPlay: ((List<Song>, Int, Boolean) -> Unit)? = null,
) {
    var skinDialog by remember { mutableStateOf(false) }
    if (skinDialog) MusicSkinDialog(onDismiss = { skinDialog = false })
    data class Entry(val label: String, val sub: String, val icon: ImageVector, val page: LibPage?, val action: (() -> Unit)? = null)
    val main = listOf(
        Entry("Listas de reproducción", "${LibraryStore.playlists.size}", Icons.AutoMirrored.Filled.QueueMusic, LibPage.Playlists),
        Entry("Artistas", "${LibraryStore.songs.map { it.artist }.distinct().size}", Icons.Filled.Mic, LibPage.Artists),
        Entry("Álbumes", "${LibraryStore.songs.map { it.album }.distinct().size}", Icons.Filled.Album, LibPage.Albums),
        Entry("Canciones", "$songCount", Icons.Filled.MusicNote, LibPage.Songs),
        Entry("Videos musicales", "${LibraryStore.songs.count { isVideoPath(it.path) }}", Icons.Filled.Movie, LibPage.MusicVideos),
        Entry("Favoritas", "${LibraryStore.favorites.size}", Icons.Filled.StarBorder, LibPage.Favorites),
        Entry("Más escuchadas", "", Icons.Filled.TrendingUp, LibPage.MostPlayed),
        Entry("Carpetas", "${LibraryStore.musicFolders.size}", Icons.Filled.FolderOpen, LibPage.Folders),
    )
    val tools = listOf(
        Entry("Carátulas en internet", if (CoverScanner.running) "buscando…" else CoverScanner.found.size.takeIf { it > 0 }?.toString() ?: "",
            Icons.Filled.ImageSearch, LibPage.Covers),
        Entry("Carátulas animadas", if (AnimatedScanner.running) "buscando…" else "", Icons.Filled.AutoAwesome, LibPage.AnimatedCoversP),
        Entry("Letras", if (LyricsLocal.running) "buscando…" else "", Icons.Filled.Lyrics, LibPage.LyricsP),
        Entry("Sincronizar con NAS", if (NasSync.running) "sincronizando…" else "", Icons.Filled.CloudSync, LibPage.Nas),
        Entry("Tema del reproductor", LibraryStore.musicSkin.label.substringBefore(" ("), Icons.Filled.Palette, null) { skinDialog = true },
        Entry("Ajustes", "", Icons.Filled.Settings, null) { MusicUi.settingsOpen = true },
    )
    val accent = Retro.Orange
    val divider = Retro.Text.copy(alpha = 0.10f)

    @Composable
    fun Row1(e: Entry, last: Boolean) {
        val selected = current != null && current == e.page && e.page != LibPage.Menu
        Box(Modifier.fillMaxWidth().background(if (selected) accent.copy(alpha = 0.12f) else Color.Transparent)
            .clickable { e.action?.invoke() ?: e.page?.let(onOpen) }) {
            Row(Modifier.fillMaxWidth().height(if (big) 60.dp else 54.dp).padding(start = 20.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically) {
                if (Retro.isLavanda) { SoftIcon(e.icon, accent, 34.dp); Spacer(Modifier.width(12.dp)) }
                else { Icon(e.icon, null, tint = accent, modifier = Modifier.size(28.dp)); Spacer(Modifier.width(20.dp)) }
                Text(e.label, color = Retro.Text, fontSize = if (big) 19.sp else 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f))
                if (e.sub.isNotEmpty()) Text(e.sub, color = Retro.SubText, fontSize = 14.sp, modifier = Modifier.padding(end = 6.dp))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Retro.SubText.copy(alpha = 0.7f), modifier = Modifier.size(22.dp))
            }
            if (!last) Box(Modifier.align(Alignment.BottomStart).padding(start = 68.dp, end = 16.dp).fillMaxWidth().height(0.6.dp).background(divider))
        }
    }

    // lo último que escuchaste, un cuadro por álbum
    val recents = remember(LibraryStore.lastPlayed.size, LibraryStore.songs.size) {
        LibraryStore.songs.filter { LibraryStore.lastPlayed.containsKey(it.path) }
            .sortedByDescending { LibraryStore.lastPlayed[it.path] ?: 0L }
            .distinctBy { it.album.ifBlank { it.path } }.take(10)
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        items(main.size) { i -> Row1(main[i], i == main.lastIndex) }
        item {
            Text("Herramientas", color = Retro.SubText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 20.dp, top = 22.dp, bottom = 4.dp))
        }
        items(tools.size) { i -> Row1(tools[i], i == tools.lastIndex) }
        if (big && recents.isNotEmpty() && onPlay != null) {
            item {
                Text("Recientes", color = Retro.Text, fontSize = 24.sp, fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(start = 20.dp, top = 26.dp, bottom = 12.dp))
            }
            items(recents.chunked(2).size) { r ->
                val pair = recents.chunked(2)[r]
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    pair.forEach { s -> RecentCard(s, Modifier.weight(1f)) {
                        val album = LibraryStore.songs.filter { it.album == s.album && s.album.isNotBlank() }.ifEmpty { listOf(s) }
                        onPlay(album, album.indexOf(s).coerceAtLeast(0), false)
                    } }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun RecentCard(song: Song, modifier: Modifier, onClick: () -> Unit) {
    val cover by produceState(peekCover(song.path), song.path, LibraryStore.tagsVersion) { value = loadCover(song.path) }
    Column(modifier.clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).shadow(6.dp, RoundedCornerShape(10.dp)).clip(RoundedCornerShape(10.dp))
            .background(Retro.Panel), contentAlignment = Alignment.Center) {
            val b = cover
            if (b != null) Image(remember(b) { b.asImageBitmap() }, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else Icon(Icons.Filled.Album, null, tint = Retro.SubText, modifier = Modifier.size(48.dp))
        }
        Text(song.album.ifBlank { song.title }, color = Retro.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        Text(song.artist, color = Retro.SubText, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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

/** ¿La canción coincide con lo buscado? (sin importar acentos ni mayúsculas; varias palabras = todas deben estar) */
private fun songMatches(s: Song, q: String, field: String = "all"): Boolean {
    fun norm(t: String) = java.text.Normalizer.normalize(t.lowercase(Locale.ROOT), java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
    val hay = norm(when (field) { "title" -> s.title; "artist" -> s.artist; "album" -> s.album; else -> s.title + " " + s.artist + " " + s.album })
    return norm(q).split(' ').filter { it.isNotBlank() }.all { hay.contains(it) }
}

/** Buscador redondito de la parte de arriba. */
@Composable
private fun LibSearchField(value: String, onChange: (String) -> Unit, hint: String) {
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).height(44.dp).clip(RoundedCornerShape(14.dp))
            .background(if (Retro.isLavanda) Color(0xFFECE5FA) else Retro.Text.copy(alpha = 0.09f)).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Search, null, tint = Retro.SubText, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Text(hint, color = Retro.SubText, fontSize = 16.sp, maxLines = 1)
            androidx.compose.foundation.text.BasicTextField(
                value, onChange, singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(color = Retro.Text, fontSize = 16.sp),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Retro.Orange),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { focus.clearFocus() }),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (value.isNotEmpty()) Icon(Icons.Filled.Close, "Borrar", tint = Retro.SubText,
            modifier = Modifier.size(20.dp).clip(CircleShape).clickable { onChange(""); focus.clearFocus() })
    }
}

private val SEARCH_FIELDS = listOf("all" to "Todo", "title" to "Canción", "artist" to "Artista", "album" to "Álbum")

/** Etiquetas debajo del buscador para buscar solo en el nombre, el artista o el álbum. */
@Composable
private fun SearchFieldChips(songs: List<Song>, q: String, field: String, onField: (String) -> Unit) {
    val counts = remember(songs, q) { SEARCH_FIELDS.associate { (k, _) -> k to songs.count { songMatches(it, q, k) } } }
    androidx.compose.foundation.lazy.LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        items(SEARCH_FIELDS.size) { i ->
            val (k, label) = SEARCH_FIELDS[i]
            val on = field == k
            Row(Modifier.clip(CircleShape)
                .background(if (on) Retro.Orange else Retro.Text.copy(alpha = 0.08f))
                .clickable { onField(k) }.padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = if (on) Retro.OnAccent else Retro.Text, fontSize = 14.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium)
                Spacer(Modifier.width(5.dp))
                Text("${counts[k] ?: 0}", color = if (on) Retro.OnAccent.copy(alpha = 0.75f) else Retro.SubText, fontSize = 12.sp)
            }
        }
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
    alphabet: Boolean = false,
    searchable: Boolean = true,
) {
    var q by remember { mutableStateOf("") }
    var field by remember { mutableStateOf("all") }
    val shown = remember(songs, q, field) { if (q.isBlank()) songs else songs.filter { songMatches(it, q, field) } }
    Column(Modifier.fillMaxSize()) {
        if (searchable && songs.size > 6) {
            LibSearchField(q, { q = it }, "Buscar en ${songs.size} canciones")
            if (q.isNotBlank()) SearchFieldChips(songs, q, field) { field = it }
        }
        SongListInner(shown, onPlay, onAdd, showCounts, if (q.isNotBlank()) "No encontré nada con «${q.trim()}»" else empty,
            onRemove, header, alphabet && q.isBlank())
    }
}

@Composable
private fun SongListInner(
    songs: List<Song>,
    onPlay: (List<Song>, Int, Boolean) -> Unit,
    onAdd: (List<Song>) -> Unit,
    showCounts: Boolean = false,
    empty: String = "No hay canciones.\nRevisa “Carpetas de música”.",
    onRemove: ((Song) -> Unit)? = null,
    header: @Composable () -> Unit = {},
    alphabet: Boolean = false,
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
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val letters = remember(songs) {
        val m = LinkedHashMap<String, Int>()
        songs.forEachIndexed { i, s -> m.putIfAbsent(letterOf(s.title), i + 1) } // +1: el encabezado
        m
    }
    // si una canción está dos veces en una lista, cada una necesita su propia llave
    val keys = remember(songs) {
        val seen = HashMap<String, Int>()
        songs.map { s -> val n = (seen[s.path] ?: 0) + 1; seen[s.path] = n; if (n == 1) s.path else "${s.path}#$n" }
    }
    val view = normalizeSongView(LibraryStore.songView)
    val isGrid = view.startsWith("grid")
    val scope = rememberCoroutineScope()
    // al cambiar de vista se queda en la misma canción
    var anchor by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    fun changeView(v: String) {
        anchor = if (isGrid) gridState.firstVisibleItemIndex else listState.firstVisibleItemIndex
        LibraryStore.changeSongView(v)
    }
    LaunchedEffect(view) {
        if (view.startsWith("grid")) gridState.scrollToItem(anchor) else listState.scrollToItem(anchor)
    }
    // pellizcar con dos dedos: abrir = íconos más grandes, cerrar = más chicos
    val pinch = remember { androidx.compose.animation.core.Animatable(1f) }
    Column(Modifier.fillMaxSize()) {
        // los botones de arriba se quedan fijos aunque bajes la lista
        PlayBar(songs, onPlay) { SongViewButton(view, onPick = { changeView(it) }) }
        Box(
            Modifier.weight(1f).fillMaxWidth()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var zoom = 1f
                        var pinching = false
                        do {
                            val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                            if (event.changes.count { it.pressed } >= 2) {
                                pinching = true
                                zoom *= event.calculateZoom()
                                scope.launch { pinch.snapTo(zoom.coerceIn(0.82f, 1.18f)) }
                                event.changes.forEach { it.consume() }
                                val i = SONG_VIEWS.indexOf(normalizeSongView(LibraryStore.songView))
                                if (zoom > 1.3f && i < SONG_VIEWS.lastIndex) { changeView(SONG_VIEWS[i + 1]); zoom = 1f }
                                else if (zoom < 0.77f && i > 0) { changeView(SONG_VIEWS[i - 1]); zoom = 1f }
                            }
                        } while (event.changes.any { it.pressed })
                        if (pinching) scope.launch { pinch.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 400f)) }
                    }
                }
                .graphicsLayer { scaleX = pinch.value; scaleY = pinch.value }
        ) {
            androidx.compose.animation.AnimatedContent(
                targetState = isGrid,
                transitionSpec = {
                    (androidx.compose.animation.fadeIn(tween(220)) + androidx.compose.animation.scaleIn(spring(dampingRatio = 0.8f, stiffness = 300f), initialScale = 0.92f)) togetherWith
                        androidx.compose.animation.fadeOut(tween(160))
                },
                label = "songView"
            ) { grid ->
                if (grid) {
                    val cols = view.removePrefix("grid").toIntOrNull() ?: 3
                    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                        columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(cols),
                        state = gridState,
                        modifier = Modifier.fillMaxSize().padding(end = if (alphabet && songs.size > 25) 14.dp else 0.dp),
                        contentPadding = PaddingValues(start = 6.dp, end = 6.dp, bottom = 16.dp),
                    ) {
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) { header() }
                        items(songs.size, key = { keys[it] }) { i ->
                            val song = songs[i]
                            // se van acomodando con animación cuando cambia el tamaño
                            Box(Modifier.animateItem()) {
                                SongRow(song, if (showCounts) i + 1 else null, if (showCounts) LibraryStore.playCounts[song.path] ?: 0 else null,
                                    onClick = { onPlay(songs, i, false) }, onAdd = { onAdd(listOf(song)) },
                                    onRemove = onRemove?.let { { it(song) } }, style = "tile")
                            }
                        }
                    }
                } else {
                    LazyColumn(Modifier.fillMaxSize().padding(end = if (alphabet && songs.size > 25) 14.dp else 0.dp), state = listState) {
                        item { header() }
                        itemsIndexed(songs, key = { i, _ -> keys[i] }) { i, song ->
                            SongRow(
                                song = song,
                                rank = if (showCounts) i + 1 else null,
                                count = if (showCounts) LibraryStore.playCounts[song.path] ?: 0 else null,
                                onClick = { onPlay(songs, i, false) },
                                onAdd = { onAdd(listOf(song)) },
                                onRemove = onRemove?.let { { it(song) } },
                                style = if (view == "cards") "cards" else "compact",
                            )
                        }
                        item { Spacer(Modifier.height(16.dp)) }
                    }
                }
            }
            if (alphabet && songs.size > 25) AlphabetScroller(letters) { target ->
                scope.launch { if (isGrid) gridState.scrollToItem(target) else listState.scrollToItem(target) }
            }
        }
    }
}

/** Vistas de canciones de la más chica a la más grande (en ese orden se pasa al pellizcar). */
private val SONG_VIEWS = listOf("compact", "cards", "grid4", "grid3", "grid2")
private fun normalizeSongView(v: String) = if (v == "grid") "grid3" else if (v in SONG_VIEWS) v else "compact"

/** Para abrir artista, álbum o carpeta desde la información de una canción. */
private val LocalLibGoTo = androidx.compose.runtime.staticCompositionLocalOf<(String, String) -> Unit> { { _, _ -> } }

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun SongRow(
    song: Song,
    rank: Int?,
    count: Int?,
    onClick: () -> Unit,
    onAdd: () -> Unit,
    onRemove: (() -> Unit)?,
    style: String = "compact",
) {
    val thumb by produceState<android.graphics.Bitmap?>(null, song.path, LibraryStore.tagsVersion) { value = loadThumb(song.path) }
    val fav = LibraryStore.isFavorite(song.path)
    val playing = NowPlaying.path == song.path
    val webCover = CoverScanner.found[song.path]
    val hasAnimated = rememberHasAnimatedCover(song.path, song.album)
    val goTo = LocalLibGoTo.current
    var menu by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    var tagOnline by remember { mutableStateOf(false) }
    var lyricsOnline by remember { mutableStateOf(false) }
    var coverOffer by remember { mutableStateOf(false) }
    if (showInfo) SongInfoWindow(song.path, Retro.Orange, onDismiss = { showInfo = false }, onGoTo = { k, v -> showInfo = false; goTo(k, v) })
    if (tagOnline) TagSearchDialog(song.path, Retro.Orange, onDismiss = { tagOnline = false })
    if (lyricsOnline) LyricsSearchDialog(song.path, Retro.Orange, onDismiss = { lyricsOnline = false })
    if (coverOffer && webCover != null) WebCoverDialog(song, webCover, onDismiss = { coverOffer = false })

    @Composable
    fun Menu() {
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            if (webCover != null) {
                DropdownMenuItem(text = { Text("Poner carátula encontrada") }, leadingIcon = { Icon(Icons.Filled.CloudDownload, null) },
                    onClick = { menu = false; coverOffer = true })
            }
            DropdownMenuItem(text = { Text("Agregar a lista") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null) },
                onClick = { menu = false; onAdd() })
            DropdownMenuItem(text = { Text(if (fav) "Quitar de favoritas" else "Favorita") },
                leadingIcon = { Icon(if (fav) Icons.Filled.Star else Icons.Filled.StarBorder, null) },
                onClick = { menu = false; LibraryStore.toggleFavorite(song.path) })
            DropdownMenuItem(text = { Text("Información") }, leadingIcon = { Icon(Icons.Filled.Info, null) },
                onClick = { menu = false; showInfo = true })
            DropdownMenuItem(text = { Text("Etiquetar desde internet") }, leadingIcon = { Icon(Icons.Filled.TravelExplore, null) },
                onClick = { menu = false; tagOnline = true })
            DropdownMenuItem(text = { Text("Buscar letra") }, leadingIcon = { Icon(Icons.Filled.Search, null) },
                onClick = { menu = false; lyricsOnline = true })
            if (onRemove != null) {
                DropdownMenuItem(text = { Text("Quitar de esta lista") }, leadingIcon = { Icon(Icons.Filled.RemoveCircleOutline, null) },
                    onClick = { menu = false; onRemove() })
            }
        }
    }

    @Composable
    fun Cover(size: androidx.compose.ui.unit.Dp, corner: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
        Box(modifier.size(size)) {
            Box(
                Modifier.fillMaxSize().clip(RoundedCornerShape(corner)).background(if (Retro.isLavanda) Color(0xFFECE5FA) else Retro.Panel),
                contentAlignment = Alignment.Center
            ) {
                val b = thumb
                if (b != null) Image(remember(b) { b.asImageBitmap() }, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else Icon(Icons.Filled.MusicNote, null, tint = Retro.Orange.copy(alpha = 0.7f), modifier = Modifier.size(size * 0.45f))
                if (playing) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.GraphicEq, null, tint = Retro.Orange, modifier = Modifier.size(size * 0.45f))
                }
            }
            if (hasAnimated) DiamondStar(if (size > 60.dp) 24.dp else 18.dp,
                Modifier.align(Alignment.BottomEnd).offset(x = 3.dp, y = 3.dp), backdrop = true)
            // hay carátula disponible en internet: iconito para ponerla
            if (webCover != null) {
                Box(
                    Modifier.align(Alignment.TopEnd).offset(x = 5.dp, y = (-5).dp).size(24.dp)
                        .shadow(4.dp, CircleShape).clip(CircleShape).background(Retro.Orange)
                        .clickable { coverOffer = true },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.CloudDownload, "Carátula disponible", tint = Retro.OnAccent, modifier = Modifier.size(15.dp)) }
            }
        }
    }

    when (style) {
        // tarjetas grandes: portada redondeada, título, «artista - álbum» y duración | formato
        "cards" -> Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp)
                .clip(RoundedCornerShape(30.dp))
                .background(if (playing) Retro.Highlight else Color.Transparent)
                .combinedClickable(onClick = onClick, onLongClick = { menu = true })
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (rank != null) {
                Text("$rank", color = if (rank <= 3) Retro.Orange else Retro.SubText, fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.width(30.dp))
            }
            Cover(86.dp, 20.dp, Modifier.shadow(10.dp, RoundedCornerShape(20.dp)))
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(song.title, color = if (playing) Retro.Orange else Retro.Text, fontWeight = FontWeight.Bold, fontSize = 19.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (hasAnimated) { Spacer(Modifier.width(6.dp)); DiamondStar(16.dp) }
                }
                Text("${song.artist} - ${song.album}", color = Retro.Text.copy(alpha = 0.92f), fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(5.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.QueueMusic, null, tint = Retro.SubText, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(5.dp))
                    val ext = song.path.substringAfterLast('.', "").lowercase(Locale.ROOT)
                    val extra = if (count != null) " | $count ${if (count == 1) "vez" else "veces"}" else ""
                    Text(listOf(if (song.durationMs > 0) formatTime(song.durationMs) else "", ext).filter { it.isNotBlank() }.joinToString(" | ") + extra,
                        color = Retro.SubText, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    if (fav) { Spacer(Modifier.width(6.dp)); Icon(Icons.Filled.Star, null, tint = Retro.Yellow, modifier = Modifier.size(15.dp)) }
                }
            }
            Box { Menu() }
        }
        // cuadrito para la vista en cuadrícula
        "tile" -> Column(
            Modifier.clip(RoundedCornerShape(16.dp))
                .background(if (playing) Retro.Highlight else Color.Transparent)
                .combinedClickable(onClick = onClick, onLongClick = { menu = true })
                .padding(6.dp)
        ) {
            BoxWithConstraints(Modifier.fillMaxWidth()) { Cover(maxWidth, 14.dp) }
            Text(song.title, color = if (playing) Retro.Orange else Retro.Text, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp))
            Text(song.artist, color = Retro.SubText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Box { Menu() }
        }
        else -> Row(
            Modifier.fillMaxWidth()
                .background(if (playing) Retro.Highlight else Color.Transparent)
                .combinedClickable(onClick = onClick, onLongClick = { menu = true })
                .padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (rank != null) {
                Text(
                    "$rank", color = if (rank <= 3) Retro.Orange else Retro.SubText, fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold, fontSize = if (rank <= 3) 18.sp else 14.sp, modifier = Modifier.width(32.dp)
                )
            }
            Cover(48.dp, 6.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(song.title, color = if (playing) Retro.Orange else Retro.Text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f, fill = false))
                    if (hasAnimated) { Spacer(Modifier.width(6.dp)); DiamondStar(14.dp) }
                }
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
                Menu()
            }
        }
    }
}

@Composable
private fun GroupList(groups: Map<String, List<Song>>, icon: ImageVector, color: Color, onOpen: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val letters = remember(groups) {
        val m = LinkedHashMap<String, Int>()
        groups.keys.forEachIndexed { i, k -> m.putIfAbsent(letterOf(k), i) }
        m
    }
    Box(Modifier.fillMaxSize()) {
    LazyColumn(Modifier.fillMaxSize().padding(end = if (groups.size > 25) 14.dp else 0.dp), state = listState, contentPadding = PaddingValues(vertical = 6.dp)) {
        items(groups.entries.toList(), key = { it.key }) { (name, list) ->
            val thumb by produceState<android.graphics.Bitmap?>(null, name, LibraryStore.tagsVersion) { value = loadThumb(list.first().path) }
            Row(
                Modifier.fillMaxWidth().clickable { onOpen(name) }.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(52.dp).clip(if (icon == Icons.Filled.Person) CircleShape else RoundedCornerShape(6.dp))
                        .background(if ((Retro.isOriginal || Retro.isLavanda) && thumb == null) Color.Transparent else color.copy(alpha = 0.85f)),
                    contentAlignment = Alignment.Center
                ) {
                    val b = thumb
                    if (b != null) Image(remember(b) { b.asImageBitmap() }, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    else if (Retro.isLavanda) SoftIcon(icon, color, 44.dp)
                    else if (Retro.isOriginal) ReliefIcon(icon, color, 42.dp)
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
    if (groups.size > 25) AlphabetScroller(letters) { t -> scope.launch { listState.scrollToItem(t) } }
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
fun AddToPlaylistDialog(onDismiss: () -> Unit, onPick: (String) -> Unit, onNew: (String) -> Unit) {
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

// ───────── Barra de letras para buscar rápido ─────────

private val LETTERS = listOf("#") + ('A'..'Z').map { it.toString() }

fun letterOf(text: String): String {
    val c = java.text.Normalizer.normalize(text.trim(), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .firstOrNull()?.uppercaseChar() ?: return "#"
    return if (c in 'A'..'Z') c.toString() else "#"
}

@Composable
fun AlphabetScroller(firstIndex: Map<String, Int>, onJump: (Int) -> Unit) {
    var active by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    fun jump(y: Float, h: Int) {
        val i = (y / h * LETTERS.size).toInt().coerceIn(0, LETTERS.lastIndex)
        active = LETTERS[i]
        // si esa letra no tiene canciones, se va a la siguiente que sí
        val target = LETTERS.drop(i).firstNotNullOfOrNull { firstIndex[it] }
            ?: LETTERS.take(i).reversed().firstNotNullOfOrNull { firstIndex[it] } ?: return
        onJump(target)
    }
    Box(Modifier.fillMaxSize()) {
        Column(
            // tira delgada; el área para tocar es un poquito más ancha que lo que se ve
            Modifier.align(Alignment.CenterEnd).padding(top = 8.dp, bottom = 8.dp)
                .fillMaxHeight(0.92f).width(22.dp)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        jump(down.position.y, size.height)
                        do {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.first()
                            if (ch.pressed) { jump(ch.position.y, size.height); ch.consume() }
                        } while (ch.pressed)
                        active = null
                    }
                },
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.End
        ) {
            LETTERS.forEach { l ->
                Text(
                    l, fontSize = 8.sp, lineHeight = 9.sp, fontWeight = FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.width(12.dp).padding(end = 2.dp)
                        .background(if (active != null) Retro.Panel.copy(alpha = 0.7f) else Color.Transparent),
                    color = when {
                        l == active -> Retro.Orange
                        l in firstIndex -> Retro.Text
                        else -> Retro.SubText.copy(alpha = 0.4f)
                    }
                )
            }
        }
        // letra grande mientras deslizas
        active?.let { l ->
            Box(
                Modifier.align(Alignment.Center).padding(start = 80.dp).size(110.dp)
                    .clip(RoundedCornerShape(24.dp)).background(Color(0xCC3A3A44)),
                contentAlignment = Alignment.Center
            ) {
                Text(l, color = Color.White, fontSize = 64.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}


/** Botón para elegir cómo se ven las canciones (también se cambia pellizcando la lista). */
@Composable
private fun SongViewButton(current: String, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val options = listOf(
        Triple("compact", "Lista compacta", Icons.Filled.ViewHeadline),
        Triple("cards", "Tarjetas con portada", Icons.Filled.ViewAgenda),
        Triple("grid4", "Cuadrícula chica", Icons.Filled.GridView),
        Triple("grid3", "Cuadrícula mediana", Icons.Filled.GridView),
        Triple("grid2", "Cuadrícula grande", Icons.Filled.GridView),
    )
    val cur = options.firstOrNull { it.first == current } ?: options.first()
    Box {
        IconButton(onClick = { open = true }) { Icon(cur.third, "Vista", tint = Retro.Orange) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (id, label, icon) ->
                DropdownMenuItem(
                    text = { Text(label, fontWeight = if (id == cur.first) FontWeight.Bold else null) },
                    leadingIcon = { Icon(icon, null) },
                    onClick = { onPick(id); open = false }
                )
            }
            Text("Tip: pellizca la lista para agrandar o achicar", fontSize = 11.sp, color = Color.Gray,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        }
    }
}

/** Muestra la carátula encontrada en internet y deja ponerla o descartarla. */
@Composable
private fun WebCoverDialog(song: Song, r: TagResult, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    ThemedDialog(
        "Carátula disponible", onDismiss,
        listOf(
            DialogButton("No es esta") { CoverScanner.dismiss(song.path); onDismiss() },
            DialogButton(if (busy) "Poniendo…" else "Ponerla", true) {
                if (busy) return@DialogButton
                busy = true
                scope.launch {
                    val ok = CoverScanner.apply(context, song.path)
                    busy = false
                    Toast.makeText(context, if (ok) "Carátula puesta ✔" else "No se pudo escribir en el archivo", Toast.LENGTH_SHORT).show()
                    onDismiss()
                }
            }
        )
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            coil.compose.AsyncImage(r.artworkUrl, null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(200.dp).clip(RoundedCornerShape(14.dp)))
            Spacer(Modifier.height(10.dp))
            Text(song.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Encontrada: ${r.artist} — ${r.album}", fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("Sólo se agrega la imagen; el nombre y las etiquetas no cambian.", fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp))
        }
    }
}

/** Página del buscador de carátulas: búsqueda automática, revisar todo y lo encontrado. */
@Composable
private fun CoversPage() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val foundList = CoverScanner.found.entries.toList()
    val byPath = LibraryStore.songs.associateBy { it.path }
    var applyingAll by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Retro.Panel).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Búsqueda automática", color = Retro.Text, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text("Busca en internet la portada de la música que no tiene. Cada vez que abras la app revisa lo nuevo y te avisa.",
                            color = Retro.SubText, fontSize = 13.sp)
                    }
                    androidx.compose.material3.Switch(
                        checked = CoverScanner.auto, onCheckedChange = { CoverScanner.changeAuto(it) },
                        colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = Retro.Orange, checkedThumbColor = Retro.OnAccent)
                    )
                }
                Spacer(Modifier.height(12.dp))
                if (CoverScanner.running) {
                    val t = CoverScanner.total
                    LinearProgressIndicator(
                        progress = { if (t > 0) CoverScanner.done.toFloat() / t else 0f },
                        modifier = Modifier.fillMaxWidth(), color = Retro.Orange, trackColor = Retro.Bg
                    )
                    Text("${CoverScanner.done} de $t álbumes · ${CoverScanner.status}", color = Retro.SubText, fontSize = 12.sp,
                        maxLines = 2, modifier = Modifier.padding(top = 6.dp))
                    Spacer(Modifier.height(8.dp))
                    RetroButton("Detener", Icons.Filled.Stop, Modifier.fillMaxWidth()) { CoverScanner.stop() }
                } else {
                    if (CoverScanner.status.isNotBlank()) Text(CoverScanner.status, color = Retro.SubText, fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 8.dp))
                    RetroButton("Revisar toda mi música", Icons.Filled.ImageSearch, Modifier.fillMaxWidth()) { CoverScanner.start() }
                    Text("Volver a revisar desde cero", color = Retro.Orange, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 10.dp).clickable { CoverScanner.resetChecked(); CoverScanner.start() })
                }
                Text("Va despacio a propósito (unas 18 búsquedas por minuto) para que el servicio no lo bloquee. Puedes seguir usando la app mientras.",
                    color = Retro.SubText, fontSize = 11.sp, modifier = Modifier.padding(top = 10.dp))
            }
        }
        if (foundList.isNotEmpty()) {
            item {
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Encontradas (${foundList.size})", color = Retro.Text, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.weight(1f))
                    Text(if (applyingAll) "Poniendo…" else "Poner todas", color = Retro.OnAccent, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(CircleShape).background(Retro.Orange).clickable(enabled = !applyingAll) {
                            applyingAll = true
                            scope.launch {
                                var n = 0
                                foundList.map { it.key }.forEach { p -> if (CoverScanner.apply(context, p)) n++ }
                                applyingAll = false
                                Toast.makeText(context, "Listo: $n carátulas puestas", Toast.LENGTH_LONG).show()
                            }
                        }.padding(horizontal = 16.dp, vertical = 9.dp))
                }
            }
            items(foundList, key = { it.key }) { (path, r) ->
                val song = byPath[path] ?: Song(path, File(path).nameWithoutExtension, UNKNOWN_ARTIST, UNKNOWN_ALBUM, 0)
                var offer by remember { mutableStateOf(false) }
                if (offer) WebCoverDialog(song, r, onDismiss = { offer = false })
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Retro.Panel).clickable { offer = true }.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    coil.compose.AsyncImage(r.thumbUrl, null, contentScale = ContentScale.Crop,
                        modifier = Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(song.title, color = Retro.Text, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${song.artist} · encontrada en «${r.album}»", color = Retro.SubText, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(Icons.Filled.CloudDownload, null, tint = Retro.Orange)
                }
            }
        }
    }
}

/** Página de carátulas animadas (aparte de las normales). */
@Composable
private fun AnimatedCoversPage() {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                DiamondStar(26.dp)
                Spacer(Modifier.width(10.dp))
                Text("Las canciones con esta estrella tienen carátula animada.", color = Retro.Text, fontSize = 14.sp)
            }
        }
        item {
            // carátulas animadas: se buscan en el teléfono
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Retro.Panel).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Búsqueda automática", color = Retro.Text, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text("Revisa Descargas, Películas, tus carpetas de música y cualquier carpeta «Carátulas animadas». " +
                            "Si un video se llama como un álbum (ej. «Coldplay - Ghost Stories.mp4») se lo pone solo.",
                            color = Retro.SubText, fontSize = 13.sp)
                    }
                    androidx.compose.material3.Switch(
                        checked = AnimatedScanner.auto, onCheckedChange = { AnimatedScanner.changeAuto(it) },
                        colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = Retro.Orange, checkedThumbColor = Retro.OnAccent)
                    )
                }
                Spacer(Modifier.height(12.dp))
                if (AnimatedScanner.running) {
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = Retro.Orange, trackColor = Retro.Bg)
                } else {
                    RetroButton("Buscar animadas en mi teléfono", Icons.Filled.Animation, Modifier.fillMaxWidth()) { AnimatedScanner.start() }
                }
                if (AnimatedScanner.status.isNotBlank()) Text(AnimatedScanner.status, color = Retro.SubText, fontSize = 12.sp,
                    modifier = Modifier.padding(top = 8.dp))
                AnimatedScanner.assigned.take(8).forEach { (album, video) ->
                    Text("✨ $album  ←  $video", color = Retro.Text, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp))
                }
                Text("Por internet no se pueden buscar solas: Apple sólo las da con su servicio de paga para desarrolladores.",
                    color = Retro.SubText, fontSize = 11.sp, modifier = Modifier.padding(top = 10.dp))
            }
        }
    }
}


/** Página para sincronizar la música con el NAS por WebDAV. */
@Composable
private fun NasPage() {
    val context = LocalContext.current
    var picking by remember { mutableStateOf(false) }
    var showPass by remember { mutableStateOf(false) }
    val fieldColors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
        focusedTextColor = Retro.Text, unfocusedTextColor = Retro.Text, focusedBorderColor = Retro.Orange,
        unfocusedBorderColor = Retro.SubText, focusedLabelColor = Retro.Orange, unfocusedLabelColor = Retro.SubText, cursorColor = Retro.Orange)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Retro.Panel).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Conexión (WebDAV)", color = Retro.Text, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                androidx.compose.material3.OutlinedTextField(NasSync.url, { NasSync.url = it }, singleLine = true, enabled = !NasSync.running,
                    label = { Text("Dirección de la carpeta de música") }, placeholder = { Text("http://192.168.1.50:5005/Musica") },
                    colors = fieldColors, modifier = Modifier.fillMaxWidth())
                androidx.compose.material3.OutlinedTextField(NasSync.user, { NasSync.user = it }, singleLine = true, enabled = !NasSync.running,
                    label = { Text("Usuario del NAS") }, colors = fieldColors, modifier = Modifier.fillMaxWidth())
                androidx.compose.material3.OutlinedTextField(NasSync.password, { NasSync.password = it }, singleLine = true, enabled = !NasSync.running,
                    label = { Text("Contraseña") }, colors = fieldColors, modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (showPass) androidx.compose.ui.text.input.VisualTransformation.None
                        else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    trailingIcon = { Text(if (showPass) "Ocultar" else "Ver", color = Retro.Orange, fontSize = 12.sp,
                        modifier = Modifier.clickable { showPass = !showPass }.padding(8.dp)) })
                if (NasSync.url.trim().startsWith("http://")) Text(
                    "Con «http://» la contraseña viaja sin cifrar: úsalo sólo en el WiFi de tu casa. Desde fuera usa una dirección «https://».",
                    color = Retro.Yellow, fontSize = 12.sp)
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(enabled = !NasSync.running) { picking = true }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Folder, null, tint = Retro.Orange); Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Carpeta de música en el teléfono", color = Retro.SubText, fontSize = 12.sp)
                        Text(NasSync.localFolder.substringAfter("/0/", NasSync.localFolder), color = Retro.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (!NasSync.running) {
                    Text("Probar conexión", color = Retro.Orange, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { NasSync.test { Toast.makeText(context, it, Toast.LENGTH_LONG).show() } }.padding(vertical = 6.dp))
                    RetroButton("Sincronizar ahora", Icons.Filled.CloudSync, Modifier.fillMaxWidth()) {
                        if (NasSync.url.isBlank()) Toast.makeText(context, "Escribe la dirección del NAS", Toast.LENGTH_SHORT).show() else NasSync.start()
                    }
                } else {
                    val t = NasSync.total
                    if (t > 0) LinearProgressIndicator(progress = { NasSync.done.toFloat() / t }, modifier = Modifier.fillMaxWidth(), color = Retro.Orange, trackColor = Retro.Bg)
                    else LinearProgressIndicator(Modifier.fillMaxWidth(), color = Retro.Orange, trackColor = Retro.Bg)
                    Text((if (t > 0) "${NasSync.done} de $t · " else "") + NasSync.status, color = Retro.SubText, fontSize = 12.sp, maxLines = 2)
                    RetroButton("Detener", Icons.Filled.Stop, Modifier.fillMaxWidth()) { NasSync.stop() }
                }
                Text("Sube al NAS lo que falta allá y baja al teléfono lo que falta aquí. Compara por nombre y tamaño. No borra ni reemplaza nada en ningún lado.",
                    color = Retro.SubText, fontSize = 11.sp)
            }
        }
        if (NasSync.finished || NasSync.uploaded.isNotEmpty() || NasSync.downloaded.isNotEmpty()) {
            item {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Retro.Panel).padding(16.dp)) {
                    Text("Resumen", color = Retro.Text, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text(NasSync.status, color = if (NasSync.status.startsWith("Error")) Retro.Red else Retro.SubText, fontSize = 13.sp)
                    Text("⬆ Subidos al NAS: ${NasSync.uploaded.size}", color = Retro.Text, modifier = Modifier.padding(top = 6.dp))
                    Text("⬇ Descargados al teléfono: ${NasSync.downloaded.size}", color = Retro.Text)
                    if (NasSync.different.isNotEmpty()) Text("≠ Mismo nombre pero distinto tamaño (no se tocaron): ${NasSync.different.size}", color = Retro.Yellow)
                    if (NasSync.failed.isNotEmpty()) Text("✕ Con error: ${NasSync.failed.size}", color = Retro.Red)
                    if (NasSync.finished && NasSync.uploaded.isEmpty() && NasSync.downloaded.isEmpty() && NasSync.failed.isEmpty() && !NasSync.status.startsWith("Error"))
                        Text("Todo estaba ya igual en los dos lados.", color = Retro.SubText, fontSize = 13.sp)
                }
            }
            @Composable
            fun Line(prefix: String, name: String, color: Color) =
                Text("$prefix $name", color = color, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 6.dp))
            items(NasSync.uploaded.toList()) { Line("⬆", it, Retro.Text) }
            items(NasSync.downloaded.toList()) { Line("⬇", it, Retro.Text) }
            items(NasSync.different.toList()) { Line("≠", it, Retro.Yellow) }
            items(NasSync.failed.toList()) { Line("✕", it, Retro.Red) }
        }
    }
    if (picking) FolderWindow("Carpeta de música en el teléfono", "Usar esta carpeta", java.io.File(NasSync.localFolder), { picking = false }) {
        NasSync.localFolder = it.absolutePath; NasSync.saveSettings(); picking = false
    }
}


/** Letras sueltas en el teléfono: se buscan y se mueven junto a su canción. */
@Composable
private fun LyricsLocalPage() {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Retro.Panel).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Búsqueda automática", color = Retro.Text, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text("Busca archivos .lrc en Descargas, Documentos, Música y tus carpetas de música, ve de qué canción son " +
                            "y los mueve junto a ella con su mismo nombre. Si la canción ya tenía la misma letra, la copia repetida va a la Papelera.",
                            color = Retro.SubText, fontSize = 13.sp)
                    }
                    androidx.compose.material3.Switch(
                        checked = LyricsLocal.auto, onCheckedChange = { LyricsLocal.changeAuto(it) },
                        colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = Retro.Orange, checkedThumbColor = Retro.OnAccent)
                    )
                }
                Spacer(Modifier.height(12.dp))
                if (LyricsLocal.running) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Retro.Orange, trackColor = Retro.Bg)
                else RetroButton("Buscar letras en mi teléfono", Icons.Filled.Lyrics, Modifier.fillMaxWidth()) { LyricsLocal.start() }
                if (LyricsLocal.status.isNotBlank()) Text(LyricsLocal.status, color = Retro.SubText, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                Text("Cómo reconoce de qué canción es: el mismo nombre que el archivo de la canción, «Título Artista», o lo que diga dentro del .lrc ([ti:] y [ar:]). " +
                    "Si la canción tenía una letra sin tiempos y la nueva sí trae tiempos, se queda la nueva.",
                    color = Retro.SubText, fontSize = 11.sp, modifier = Modifier.padding(top = 10.dp))
            }
        }
        items(LyricsLocal.moved.toList()) { (song, file) ->
            Text("🎤 $song  ←  $file", color = Retro.Text, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 6.dp))
        }
    }
}
