package com.tony.explorador

import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlinx.coroutines.launch
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import kotlinx.coroutines.delay
import java.io.File
import java.util.Locale

fun mediaItemsFor(files: List<File>): List<MediaItem> = files.map { f ->
    MediaItem.Builder()
        .setMediaId(f.absolutePath)
        .setUri(Uri.fromFile(f))
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(f.nameWithoutExtension)
                .setArtist(f.parentFile?.name ?: "")
                .build()
        )
        .build()
}

fun playMusic(player: Player, files: List<File>, index: Int) {
    player.setMediaItems(mediaItemsFor(files), index, 0L)
    player.prepare()
    player.play()
}

/** Fuerza a redibujar cuando cambia el reproductor y cada [intervalMs] para la barra y la letra. */
@Composable
fun observePlayer(player: Player?, intervalMs: Long = 500): Int {
    var tick by remember { mutableIntStateOf(0) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) { tick++ }
        }
        player?.addListener(listener)
        onDispose { player?.removeListener(listener) }
    }
    LaunchedEffect(player, intervalMs) {
        while (true) {
            delay(intervalMs)
            if (player?.isPlaying == true) tick++
        }
    }
    return tick
}

fun formatTime(ms: Long): String {
    if (ms < 0) return "0:00"
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%d:%02d", m, s)
}

// ───────────────────────── Mini reproductor (abajo del explorador) ─────────────────────────

@Composable
fun MiniPlayer(player: Player, onOpen: () -> Unit) {
    val tick = observePlayer(player)
    if (player.mediaItemCount == 0) return
    val info by rememberTrackInfo(player.currentMediaItem?.mediaId)
    val colors = info?.colors ?: DefaultCoverColors
    val accent by animateColorAsState(colors.accent, tween(600), label = "miniAccent")
    androidx.compose.runtime.LaunchedEffect(info?.colors) { info?.colors?.let { NowPlaying.colors = it } }
    val nowPath = player.currentMediaItem?.mediaId
    androidx.compose.runtime.SideEffect { NowPlaying.path = nowPath }
    val title = info?.title ?: player.mediaMetadata.title?.toString() ?: ""
    val duration = player.duration.coerceAtLeast(1L)
    val progress = (player.currentPosition.toFloat() / duration).coerceIn(0f, 1f) + tick * 0f

    val vintage = LibraryStore.musicSkin == MusicSkin.VINTAGE
    Surface(
        tonalElevation = 6.dp, modifier = Modifier.fillMaxWidth(),
        color = if (vintage) Vintage.Bakelite else MaterialTheme.colorScheme.surface,
        contentColor = if (vintage) Vintage.Cream else MaterialTheme.colorScheme.onSurface,
    ) {
        Column {
            LinearProgressIndicator(
                progress = { progress },
                color = accent,
                modifier = Modifier.fillMaxWidth().height(2.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(42.dp).clip(RoundedCornerShape(8.dp)).background(colors.top),
                    contentAlignment = Alignment.Center
                ) {
                    val cover = info?.cover
                    if (cover != null) {
                        Image(remember(cover) { cover.asImageBitmap() }, null,
                            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else {
                        Icon(Icons.Filled.MusicNote, null, tint = Color.White.copy(alpha = 0.8f))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                    info?.artist?.let {
                        Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                IconButton(onClick = { if (player.isPlaying) player.pause() else player.play() }) {
                    Icon(if (player.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = "Reproducir/pausar")
                }
                IconButton(onClick = { player.seekToNextMediaItem() }, enabled = player.hasNextMediaItem()) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "Siguiente")
                }
            }
        }
    }
}

// ───────────────────────── Reproductor completo ─────────────────────────

private val QueueThumb = 56.dp
private val QueueThumbX = 6.dp
private val QueueThumbY = 8.dp

private fun lerpDp(a: androidx.compose.ui.unit.Dp, b: androidx.compose.ui.unit.Dp, t: Float) = a + (b - a) * t

/** Bloquea los toques a una capa que está escondida (para no tocar cosas invisibles). */
private fun Modifier.blockTouches(block: Boolean): Modifier = if (!block) this else this.pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial).changes.forEach { it.consume() }
        }
    }
}

@Composable
fun MusicPlayerScreen(
    player: Player,
    onClose: () -> Unit,
    onOpenCoverFlow: () -> Unit = {},
    onHome: () -> Unit = {},
    onMusicMenu: () -> Unit = {},
    onGoTo: (String, String) -> Unit = { _, _ -> },
    onPeekMenu: () -> Unit = {},
    onMenuCancel: () -> Unit = {},
    onMenuDone: () -> Unit = {},
) {
    if (LibraryStore.musicSkin == MusicSkin.VINTAGE) {
        VintagePlayerScreen(player, onClose, onOpenCoverFlow, onHome, onGoTo)
        return
    }
    val peekMenu by rememberUpdatedState(onPeekMenu)
    val menuCancel by rememberUpdatedState(onMenuCancel)
    val menuDone by rememberUpdatedState(onMenuDone)
    // deslizar a la derecha: el reproductor se hace a un lado (como la portada) y deja ver la biblioteca
    val aside = remember { Animatable(0f) }
    val asideScope = rememberCoroutineScope()
    var lyricsMode by remember { mutableStateOf(false) }
    var infoOpen by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    // 0 = portada grande, 1 = lista (la portada se hace chiquita en la esquina)
    val q = remember { Animatable(0f) }
    val qScope = rememberCoroutineScope()
    val qSpring = spring<Float>(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow)
    fun toQueue() { qScope.launch { q.animateTo(1f, qSpring) } }
    fun toCover() { qScope.launch { q.animateTo(0f, qSpring) } }
    val screenH = with(androidx.compose.ui.platform.LocalDensity.current) {
        androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp.toPx()
    }
    BackHandler {
        when {
            lyricsMode -> lyricsMode = false
            q.value > 0.5f -> toCover()
            else -> onClose()
        }
    }
    val tick = observePlayer(player, if (lyricsMode) 100L else 250L)

    val info by rememberTrackInfo(player.currentMediaItem?.mediaId)
    val colors = info?.colors ?: DefaultCoverColors
    val top by animateColorAsState(colors.top, tween(900), label = "top")
    val bottom by animateColorAsState(colors.bottom, tween(900), label = "bottom")
    val accent by animateColorAsState(colors.accent, tween(900), label = "accent")
    val onAccent by animateColorAsState(colors.onAccent, tween(900), label = "onAccent")

    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }

    val title = info?.title ?: player.mediaMetadata.title?.toString() ?: "Sin canción"
    val artist = info?.artist ?: ""
    val album = info?.album
    val duration = player.duration.let { if (it <= 0) 0L else it }
    val position = player.currentPosition + tick * 0L
    val lyrics = info?.lyrics.orEmpty()
    val lyricIndex = currentLyricIndex(lyrics, position)
    val animatedCover by rememberAnimatedCover(player.currentMediaItem?.mediaId,
        player.currentMediaItem?.mediaId?.let { LibraryStore.songFor(it)?.album } ?: album)

    Box(
        Modifier.fillMaxSize()
            .pointerInput(Unit) {
                val tracker = androidx.compose.ui.input.pointer.util.VelocityTracker()
                var peeked = false
                detectHorizontalDragGestures(
                    onDragStart = { tracker.resetTracking(); peeked = false },
                    onDragEnd = {
                        val v = tracker.calculateVelocity().x
                        val w = size.width.toFloat()
                        asideScope.launch {
                            if (peeked && (v > 900f || aside.value > w * 0.33f)) {
                                aside.animateTo(w * 1.15f, spring(dampingRatio = 0.9f, stiffness = 260f), initialVelocity = v)
                                menuDone()
                            } else {
                                aside.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow))
                                if (peeked) menuCancel()
                            }
                        }
                    },
                    onDragCancel = { asideScope.launch { aside.animateTo(0f); if (peeked) menuCancel() } }
                ) { change, drag ->
                    tracker.addPosition(change.uptimeMillis, change.position)
                    val next = (aside.value + drag).coerceAtLeast(0f)
                    if (next > 0f && !peeked) { peeked = true; peekMenu() }
                    change.consume()
                    asideScope.launch { aside.snapTo(next) }
                }
            }
            .graphicsLayer {
                val frac = (aside.value / size.width).coerceIn(0f, 1.2f)
                translationX = aside.value
                rotationZ = frac * 6f
                val sc = 1f - frac * 0.1f
                scaleX = sc; scaleY = sc
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                shape = RoundedCornerShape((frac * 40f).dp)
                clip = frac > 0f
                shadowElevation = if (frac > 0f) 30f else 0f
            }
            .background(Brush.verticalGradient(listOf(top, bottom)))
    ) {
        // Fondo: la misma portada, muy difuminada
        val cover = info?.cover
        if (cover != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Crossfade(targetState = cover, animationSpec = tween(900), label = "bg") { bmp ->
                Image(
                    remember(bmp) { bmp.asImageBitmap() }, null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().blur(90.dp).alpha(0.55f)
                )
            }
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.10f), Color.Black.copy(alpha = 0.60f)))
            )
        )

        CompositionLocalProvider(LocalContentColor provides Color.White) {
            Column(
                modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Barra superior
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Cerrar", modifier = Modifier.size(30.dp))
                    }
                    HomeButton(onHome)
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("REPRODUCIENDO", fontSize = 11.sp, letterSpacing = 2.sp, color = Color.White.copy(alpha = 0.7f))
                        if (album != null) {
                            Text(album, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    IconButton(onClick = { lyricsMode = !lyricsMode }) {
                        Icon(Icons.Filled.Lyrics, contentDescription = "Letra",
                            tint = if (lyricsMode) accent else Color.White)
                    }
                    IconButton(onClick = {
                        if (q.value > 0.5f && !lyricsMode) toCover() else { lyricsMode = false; toQueue() }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "Lista",
                            tint = if (q.value > 0.5f && !lyricsMode) accent else Color.White)
                    }
                }

                // Centro: portada que se encoge hacia la lista, o la letra
                Box(Modifier.weight(1f).fillMaxWidth().padding(top = 4.dp)) {
                    AnimatedContent(
                        targetState = lyricsMode,
                        transitionSpec = {
                            if (!targetState)
                                (fadeIn(tween(240)) + scaleIn(tween(320), initialScale = 0.85f)) togetherWith
                                    (fadeOut(tween(160)) + scaleOut(tween(220), targetScale = 1.08f))
                            else
                                (fadeIn(tween(240)) + scaleIn(tween(320), initialScale = 1.08f)) togetherWith
                                    (fadeOut(tween(160)) + scaleOut(tween(220), targetScale = 0.85f))
                        },
                        label = "panel"
                    ) { lyr ->
                        if (lyr) {
                            LyricsPanel(path = player.currentMediaItem?.mediaId, lines = lyrics, current = lyricIndex, accent = accent,
                                onSeek = { player.seekTo(it); player.play() })
                        } else {
                            BoxWithConstraints(Modifier.fillMaxSize()) {
                                val p = q.value
                                // la lista vive detrás todo el tiempo (así no parpadea al empezar a jalar)
                                Box(
                                    Modifier.fillMaxSize()
                                        .graphicsLayer { alpha = p; translationY = (1f - p) * 70.dp.toPx() }
                                        .blockTouches(p < 0.5f)
                                ) {
                                    QueuePanel(player, accent, title, artist, thumbSpace = QueueThumb + QueueThumbX)
                                }
                                // la portada viaja de su lugar grande a la esquina de la lista
                                val side = minOf(maxWidth, maxHeight)
                                CoverArt(
                                    cover = info?.cover,
                                    video = if (isVideoPath(player.currentMediaItem?.mediaId)) player else null,
                                    animated = animatedCover,
                                    playing = player.isPlaying,
                                    accent = accent,
                                    size = lerpDp(side, QueueThumb, p),
                                    x = lerpDp((maxWidth - side) / 2, QueueThumbX, p),
                                    y = lerpDp(0.dp, QueueThumbY, p),
                                    collapse = p,
                                    onTap = {
                                        if (q.value > 0.5f) toCover() else if (lyrics.isNotEmpty()) lyricsMode = true
                                    },
                                    canNext = player.hasNextMediaItem(),
                                    canPrev = player.hasPreviousMediaItem(),
                                    onNext = { player.seekToNextMediaItem() },
                                    onPrev = { player.seekToPreviousMediaItem() },
                                    onLongPress = onOpenCoverFlow,
                                    onPull = { dy ->
                                        qScope.launch { q.snapTo((q.value + dy / (screenH * 0.5f)).coerceIn(0f, 1f)) }
                                    },
                                    onPullEnd = { vy ->
                                        val goList = if (vy > 900f) true else if (vy < -900f) false else q.value > 0.35f
                                        if (goList) toQueue() else toCover()
                                    },
                                    onMenu = { menuOpen = true },
                                )
                            }
                        }
                    }
                }

                // Abajo: controles grandes ↔ reproductor en cinta (se cruzan con el mismo avance)
                Box(Modifier.fillMaxWidth()) {
                    val p = if (lyricsMode) 0f else q.value
                    if (p < 0.99f) {
                        Column(
                            Modifier.graphicsLayer { alpha = 1f - p; translationY = p * 90.dp.toPx() }
                                .blockTouches(p > 0.5f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                // Título y artista
                Spacer(Modifier.height(12.dp))
                val path = player.currentMediaItem?.mediaId
                val fav = path != null && LibraryStore.isFavorite(path)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    // tocar el nombre cierra la letra y regresa a la portada
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).clickable(enabled = lyricsMode) { lyricsMode = false }) {
                        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (artist.isNotEmpty()) {
                            Text(artist, fontSize = 15.sp, color = Color.White.copy(alpha = 0.75f),
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        rememberAudioQuality(path)?.let { QualityPill(it, accent, Modifier.padding(top = 6.dp)) }
                    }
                    val starScale by animateFloatAsState(if (fav) 1.15f else 1f,
                        spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMedium), label = "star")
                    IconButton(onClick = { path?.let { LibraryStore.toggleFavorite(it) } }) {
                        Icon(
                            if (fav) Icons.Filled.Star else Icons.Filled.StarBorder,
                            contentDescription = "Favorita",
                            tint = if (fav) Color(0xFFFFC83D) else Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(30.dp).graphicsLayer { scaleX = starScale; scaleY = starScale }
                        )
                    }
                }
                // Vistazo de la letra bajo la portada
                if (!lyricsMode && lyrics.isNotEmpty()) {
                    val line = lyrics.getOrNull(lyricIndex)?.text?.ifBlank { "♪" } ?: "♪"
                    Text(
                        line, fontSize = 14.sp, color = accent, textAlign = TextAlign.Start,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp).clickable { lyricsMode = true }
                    )
                }

                // Barra de avance
                Spacer(Modifier.height(10.dp))
                WaveSeekBar(
                    progress = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f,
                    playing = player.isPlaying,
                    accent = accent,
                    onSeekChange = { dragging = true; dragValue = it },
                    onSeekEnd = {
                        player.seekTo((it * duration).toLong())
                        dragging = false
                    },
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatTime(if (dragging) (dragValue * duration).toLong() else position),
                        fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
                    Text(formatTime(duration), fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
                }

                // Controles
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { player.shuffleModeEnabled = !player.shuffleModeEnabled }) {
                        Icon(Icons.Filled.Shuffle, contentDescription = "Aleatorio",
                            tint = if (player.shuffleModeEnabled) accent else Color.White.copy(alpha = 0.6f))
                    }
                    IconButton(onClick = { player.seekToPrevious() }, modifier = Modifier.size(56.dp)) {
                        Icon(Icons.Filled.SkipPrevious, contentDescription = "Anterior", modifier = Modifier.size(40.dp))
                    }
                    Box(
                        modifier = Modifier.size(76.dp)
                            .shadow(18.dp, CircleShape, ambientColor = accent, spotColor = accent)
                            .clip(CircleShape)
                            .background(
                                if (LibraryStore.musicSkin == MusicSkin.MEDIA_PLAYER)
                                    Brush.radialGradient(listOf(Color.White.copy(alpha = 0.9f), accent, Color(0xFF0B2E6E)))
                                else Brush.linearGradient(listOf(accent, accent))
                            )
                            .clickable { if (player.isPlaying) player.pause() else player.play() },
                        contentAlignment = Alignment.Center
                    ) {
                        if (LibraryStore.musicSkin == MusicSkin.MEDIA_PLAYER) {
                            // reflejo de cristal
                            Box(Modifier.align(Alignment.TopCenter).padding(top = 6.dp).size(46.dp, 20.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.6f), Color.Transparent))))
                        }
                        Icon(
                            if (player.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = "Reproducir/pausar",
                            tint = if (LibraryStore.musicSkin == MusicSkin.MEDIA_PLAYER) Color.White else onAccent,
                            modifier = Modifier.size(42.dp)
                        )
                    }
                    IconButton(onClick = { player.seekToNextMediaItem() }, enabled = player.hasNextMediaItem(),
                        modifier = Modifier.size(56.dp)) {
                        Icon(Icons.Filled.SkipNext, contentDescription = "Siguiente", modifier = Modifier.size(40.dp))
                    }
                    IconButton(onClick = {
                        player.repeatMode = when (player.repeatMode) {
                            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                            else -> Player.REPEAT_MODE_OFF
                        }
                    }) {
                        Icon(
                            if (player.repeatMode == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                            contentDescription = "Repetir",
                            tint = if (player.repeatMode != Player.REPEAT_MODE_OFF) accent else Color.White.copy(alpha = 0.6f)
                        )
                    }
                }
                        }
                    }
                    if (p > 0.01f) {
                        TapeBar(
                            cover = info?.cover,
                            title = title,
                            artist = artist,
                            progress = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f,
                            playing = player.isPlaying,
                            accent = accent,
                            onAccent = onAccent,
                            onPlayPause = { if (player.isPlaying) player.pause() else player.play() },
                            onNext = { player.seekToNextMediaItem() },
                            onOpen = { toCover() },
                            modifier = Modifier.align(Alignment.BottomCenter)
                                .graphicsLayer { alpha = p; translationY = (1f - p) * 90.dp.toPx() }
                                .blockTouches(p < 0.5f)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
    if (menuOpen) {
        player.currentMediaItem?.mediaId?.let { path ->
            SongMenuWindow(
                path = path,
                accent = accent,
                onDismiss = { menuOpen = false },
                onInfo = { menuOpen = false; infoOpen = true },
                onLyrics = { menuOpen = false; lyricsMode = true },
                onGoTo = { kind, value -> menuOpen = false; onGoTo(kind, value) },
                onDeleted = {
                    menuOpen = false
                    val i = player.currentMediaItemIndex
                    if (i >= 0 && i < player.mediaItemCount) player.removeMediaItem(i)
                },
            )
        }
    }
    if (infoOpen) {
        player.currentMediaItem?.mediaId?.let { path ->
            SongInfoWindow(path, accent, onDismiss = { infoOpen = false }, onGoTo = { k, v -> infoOpen = false; onGoTo(k, v) })
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun CoverArt(
    cover: Bitmap?,
    video: Player?,
    animated: java.io.File? = null,
    playing: Boolean,
    accent: Color,
    size: androidx.compose.ui.unit.Dp,
    x: androidx.compose.ui.unit.Dp,
    y: androidx.compose.ui.unit.Dp,
    collapse: Float,
    onTap: () -> Unit,
    canNext: Boolean,
    canPrev: Boolean,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onLongPress: () -> Unit,
    onPull: (Float) -> Unit,
    onPullEnd: (Float) -> Unit,
    onMenu: () -> Unit,
) {
    val pauseScale by animateFloatAsState(
        targetValue = if (playing) 1f else 0.86f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow),
        label = "coverScale"
    )
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val pullBy by rememberUpdatedState(onPull)
    val pullEnd by rememberUpdatedState(onPullEnd)
    val next by rememberUpdatedState(onNext)
    val prev by rememberUpdatedState(onPrev)
    val hasNext by rememberUpdatedState(canNext)
    val hasPrev by rememberUpdatedState(canPrev)
    val collapsed by rememberUpdatedState(collapse)
    val corner = 22.dp + (10.dp - 22.dp) * collapse
    val shape = RoundedCornerShape(corner)

    Box(
        modifier = Modifier
            .offset(x, y)
            .size(size)
            .pointerInput(Unit) {
                var axis = 0 // 0 = sin decidir, 1 = horizontal (cambiar canción), 2 = vertical (lista)
                val tracker = androidx.compose.ui.input.pointer.util.VelocityTracker()
                detectDragGestures(
                    onDragStart = { axis = 0; tracker.resetTracking() },
                    onDragEnd = {
                        if (axis == 2) {
                            pullEnd(tracker.calculateVelocity().y)
                            return@detectDragGestures
                        }
                        val w = this.size.width.toFloat()
                        scope.launch {
                            val v = offsetX.value
                            when {
                                v < -w * 0.22f && hasNext -> {
                                    offsetX.animateTo(-w * 1.2f, tween(170))
                                    next()
                                    offsetX.snapTo(w * 1.2f)
                                    offsetX.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow))
                                }
                                v > w * 0.22f && hasPrev -> {
                                    offsetX.animateTo(w * 1.2f, tween(170))
                                    prev()
                                    offsetX.snapTo(-w * 1.2f)
                                    offsetX.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow))
                                }
                                else -> offsetX.animateTo(0f, spring(dampingRatio = 0.5f))
                            }
                        }
                    },
                    onDragCancel = {
                        if (axis == 2) pullEnd(0f)
                        scope.launch { offsetX.animateTo(0f) }
                    }
                ) { change, drag ->
                    change.consume()
                    tracker.addPosition(change.uptimeMillis, change.position)
                    if (axis == 0) {
                        axis = if (collapsed > 0.01f || kotlin.math.abs(drag.y) > kotlin.math.abs(drag.x)) 2 else 1
                    }
                    if (axis == 1) {
                        // si no hay a dónde ir, se siente como liga
                        val resist = if ((drag.x < 0 && !hasNext) || (drag.x > 0 && !hasPrev)) 0.3f else 1f
                        scope.launch { offsetX.snapTo(offsetX.value + drag.x * resist) }
                    } else {
                        // la portada sigue tu dedo y se va encogiendo hacia la lista
                        pullBy(drag.y)
                    }
                }
            }
            .graphicsLayer {
                val frac = (offsetX.value / this.size.width).coerceIn(-1.5f, 1.5f)
                translationX = offsetX.value
                rotationZ = frac * 8f
                val ps = pauseScale + (1f - pauseScale) * collapse
                val s = ps * (1f - kotlin.math.abs(frac) * 0.12f)
                scaleX = s
                scaleY = s
                alpha = 1f - kotlin.math.abs(frac) * 0.5f
            }
            .shadow(36.dp + (6.dp - 36.dp) * collapse, shape, ambientColor = accent, spotColor = accent)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.08f))
            .combinedClickable(onClick = onTap, onLongClick = onLongPress),
        contentAlignment = Alignment.Center
    ) {
        // si es un video, se ve en la portada como a través de un cristal
        val motion = MotionPref.on
        if (video != null && motion) VideoGlass(video, Modifier.fillMaxSize())
        else if (animated != null && motion) Box(Modifier.fillMaxSize()) {
            // carátula animada encima de la normal (así no se ve negro mientras arranca)
            cover?.let { Image(remember(it) { it.asImageBitmap() }, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            AnimatedCoverView(animated, playing, Modifier.fillMaxSize())
        }
        else Crossfade(targetState = cover, animationSpec = tween(400), label = "cover", modifier = Modifier.fillMaxSize()) { bmp ->
            if (bmp != null) {
                Image(remember(bmp) { bmp.asImageBitmap() }, contentDescription = "Portada",
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.MusicNote, null, modifier = Modifier.fillMaxSize(0.45f),
                        tint = Color.White.copy(alpha = 0.5f))
                }
            }
        }
        // botón para ver el video / la animación, o sólo la carátula fija
        if ((video != null || animated != null) && collapse < 0.3f) {
            Box(
                Modifier.align(Alignment.BottomStart).padding(10.dp).size(38.dp)
                    .graphicsLayer { alpha = 1f - collapse / 0.3f }
                    .clip(CircleShape).background(Color.Black.copy(alpha = 0.45f))
                    .clickable { MotionPref.on = !MotionPref.on },
                contentAlignment = Alignment.Center
            ) {
                Icon(if (motion) Icons.Filled.Image else Icons.Filled.Movie,
                    if (motion) "Ver sólo la carátula" else "Ver el video", tint = Color.White)
            }
        }
        // estrella diamante: esta canción tiene carátula animada
        if (animated != null && collapse < 0.3f) {
            DiamondStar(30.dp, Modifier.align(Alignment.TopEnd).padding(10.dp).graphicsLayer { alpha = 1f - collapse / 0.3f }, backdrop = true)
        }
        // ⋮ abajo a la derecha de la portada (se esconde al encogerse)
        if (collapse < 0.3f) {
            Box(
                Modifier.align(Alignment.BottomEnd).padding(10.dp).size(38.dp)
                    .graphicsLayer { alpha = 1f - collapse / 0.3f }
                    .clip(CircleShape).background(Color.Black.copy(alpha = 0.45f))
                    .clickable(onClick = onMenu),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.MoreVert, "Más opciones", tint = Color.White)
            }
        }
    }
}

/** Barra de avance con onda animada mientras suena (se aplana al pausar). */
@Composable
fun WaveSeekBar(
    progress: Float,
    playing: Boolean,
    accent: Color,
    onSeekChange: (Float) -> Unit,
    onSeekEnd: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragging by remember { mutableStateOf(false) }
    var dragFrac by remember { mutableFloatStateOf(0f) }
    val amplitude by animateFloatAsState(if (playing && !dragging) 1f else 0f, tween(600), label = "amp")
    val thumb by animateDpAsState(if (dragging) 12.dp else 7.dp, spring(dampingRatio = 0.5f), label = "thumb")
    val glow by animateFloatAsState(if (dragging) 1f else 0.45f, tween(250), label = "glow")
    val phase by rememberInfiniteTransition(label = "wave").animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)),
        label = "phase"
    )
    val change by rememberUpdatedState(onSeekChange)
    val end by rememberUpdatedState(onSeekEnd)
    val shown = if (dragging) dragFrac else progress

    Canvas(
        modifier = modifier.fillMaxWidth().height(40.dp)
            .pointerInput(Unit) {
                detectTapGestures { o -> end((o.x / size.width).coerceIn(0f, 1f)) }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { o ->
                        dragging = true
                        dragFrac = (o.x / size.width).coerceIn(0f, 1f)
                        change(dragFrac)
                    },
                    onDragEnd = { dragging = false; end(dragFrac) },
                    onDragCancel = { dragging = false }
                ) { c, d ->
                    c.consume()
                    dragFrac = (dragFrac + d / size.width).coerceIn(0f, 1f)
                    change(dragFrac)
                }
            }
    ) {
        val midY = size.height / 2f
        val x = size.width * shown
        val stroke = 4.dp.toPx()
        // lo que falta
        drawLine(Color.White.copy(alpha = 0.22f), Offset(x, midY), Offset(size.width, midY),
            strokeWidth = stroke, cap = StrokeCap.Round)
        // lo que ya sonó: onda
        if (x > 1f) {
            val path = Path()
            val waveLen = 26.dp.toPx()
            val amp = 3.5.dp.toPx() * amplitude
            path.moveTo(0f, midY)
            var px = 0f
            while (px <= x) {
                // la onda se apaga cerca del círculo para que se vea limpia
                val fade = ((x - px) / (waveLen * 0.6f)).coerceIn(0f, 1f)
                path.lineTo(px, midY + amp * fade * kotlin.math.sin(px / waveLen * 2f * Math.PI.toFloat() - phase))
                px += 2f
            }
            path.lineTo(x, midY)
            drawPath(
                path,
                brush = Brush.horizontalGradient(listOf(accent.copy(alpha = 0.55f), accent), startX = 0f, endX = x),
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
        // círculo con brillo
        drawCircle(accent.copy(alpha = 0.22f * glow * 2f), radius = thumb.toPx() * 2.4f, center = Offset(x, midY))
        drawCircle(accent, radius = thumb.toPx(), center = Offset(x, midY))
        drawCircle(Color.White, radius = thumb.toPx() * 0.38f, center = Offset(x, midY))
    }
}

@Composable
private fun LyricsPanel(path: String?, lines: List<LyricLine>, current: Int, accent: Color, onSeek: (Long) -> Unit) {
    if (lines.isEmpty()) {
        NoLyricsPrompt(path, Color.White.copy(alpha = 0.75f), accent, serif = false)
        return
    }
    val state = rememberLazyListState()
    LaunchedEffect(current) {
        if (current >= 0) state.animateScrollToItem((current - 2).coerceAtLeast(0))
    }
    LazyColumn(
        state = state,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 24.dp)
    ) {
        itemsIndexed(lines) { i, line ->
            val active = i == current
            val alpha by animateFloatAsState(if (active) 1f else if (i < current) 0.35f else 0.5f, tween(300), label = "a")
            val size by animateFloatAsState(if (active) 27f else 21f, tween(300), label = "s")
            Text(
                text = line.text.ifBlank { "♪" },
                fontSize = size.sp,
                lineHeight = (size * 1.25f).sp,
                fontWeight = if (active) FontWeight.ExtraBold else FontWeight.Bold,
                color = if (active) Color.White else Color.White,
                modifier = Modifier.fillMaxWidth()
                    .alpha(alpha)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { if (line.timeMs >= 0) onSeek(line.timeMs) }
                    .padding(vertical = 9.dp, horizontal = 4.dp)
            )
            if (active) {
                Box(Modifier.width(36.dp).height(3.dp).clip(CircleShape).background(accent))
            }
        }
    }
}

@Composable
private fun QueuePanel(player: Player, accent: Color, title: String, artist: String, thumbSpace: androidx.compose.ui.unit.Dp) {
    val current = player.currentMediaItemIndex
    val count = player.mediaItemCount
    val state = rememberLazyListState()
    val qScope = rememberCoroutineScope()
    LaunchedEffect(current) { if (current > 0) state.scrollToItem((current - 1).coerceAtLeast(0)) }
    val letters = remember(count, player.currentMediaItem?.mediaId) {
        val m = LinkedHashMap<String, Int>()
        for (i in 0 until count) {
            val it = player.getMediaItemAt(i)
            m.putIfAbsent(letterOf(LibraryStore.songFor(it.mediaId)?.title ?: it.mediaMetadata.title?.toString() ?: ""), i)
        }
        m
    }
    Column(Modifier.fillMaxSize()) {
        // encabezado: aquí "aterriza" la portada al encogerse
        Row(Modifier.fillMaxWidth().height(QueueThumb + QueueThumbY * 2), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(thumbSpace + 12.dp))
            Column(Modifier.weight(1f)) {
                Text("REPRODUCIENDO AHORA", fontSize = 10.sp, letterSpacing = 1.5.sp, color = accent, fontWeight = FontWeight.Bold)
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (artist.isNotEmpty()) Text(artist, color = Color.White.copy(alpha = 0.65f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.1f)))
        Box(Modifier.fillMaxSize()) {
        LazyColumn(state = state, modifier = Modifier.fillMaxSize().padding(end = if (count > 25) 14.dp else 0.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
            itemsIndexed((0 until count).toList(), key = { _, i -> i }) { _, i ->
                val item = player.getMediaItemAt(i)
                val path = item.mediaId
                val song = remember(path) { LibraryStore.songFor(path) }
                val isCurrent = i == current
                val thumb by androidx.compose.runtime.produceState<Bitmap?>(null, path) { value = loadThumb(path) }
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isCurrent) Color.White.copy(alpha = 0.12f) else Color.Transparent)
                        .clickable { player.seekTo(i, 0L); player.play() }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(46.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.08f)),
                        contentAlignment = Alignment.Center) {
                        val b = thumb
                        if (b != null) Image(remember(b) { b.asImageBitmap() }, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        else Icon(if (isVideoPath(path)) Icons.Filled.Movie else Icons.Filled.MusicNote, null, tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(20.dp))
                        // los videos llevan una cinta de cine en la esquina
                        if (isVideoPath(path)) Box(
                            Modifier.align(Alignment.BottomEnd).size(18.dp).clip(RoundedCornerShape(topStart = 6.dp)).background(Color.Black.copy(alpha = 0.6f)),
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Filled.Movie, null, tint = Color.White, modifier = Modifier.size(13.dp)) }
                        if (isCurrent && player.isPlaying) {
                            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.MusicNote, null, tint = accent, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            song?.title ?: item.mediaMetadata.title?.toString() ?: "",
                            color = if (isCurrent) accent else Color.White,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // puntito de color y formato (MP3, MP4, FLAC…)
                            val ext = path.substringAfterLast('.', "")
                            if (ext.isNotBlank()) {
                                Box(Modifier.size(8.dp).clip(CircleShape).background(formatColor(ext)))
                                Spacer(Modifier.width(5.dp))
                                Text(ext.uppercase(Locale.ROOT), color = formatColor(ext), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(
                                listOfNotNull(song?.artist, song?.durationMs?.takeIf { it > 0 }?.let { formatTime(it) }).joinToString(" · "),
                                color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
        if (count > 25) AlphabetScroller(letters) { t -> qScope.launch { state.scrollToItem(t) } }
        }
    }
}

@Composable
private fun TapeBar(
    cover: Bitmap?,
    title: String,
    artist: String,
    progress: Float,
    playing: Boolean,
    accent: Color,
    onAccent: Color,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp)
            .shadow(10.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.12f))
            .clickable(onClick = onOpen)
    ) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(50.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center) {
                if (cover != null) Image(remember(cover) { cover.asImageBitmap() }, null,
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else Icon(Icons.Filled.MusicNote, null, tint = Color.White.copy(alpha = 0.6f))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (artist.isNotEmpty()) Text(artist, color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(accent).clickable(onClick = onPlayPause),
                contentAlignment = Alignment.Center
            ) {
                Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Reproducir/pausar", tint = onAccent)
            }
            IconButton(onClick = onNext) { Icon(Icons.Filled.SkipNext, "Siguiente", tint = Color.White) }
        }
        Box(Modifier.fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = 0.15f))) {
            Box(Modifier.fillMaxWidth(progress).height(3.dp).background(accent))
        }
    }
}

// ───────────── Información de la canción (deslizar la portada hacia arriba) ─────────────

private data class SongDetails(
    val rows: List<Pair<String, String>>,
)

private suspend fun loadSongDetails(path: String): SongDetails = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    val f = File(path)
    val rows = mutableListOf<Pair<String, String>>()
    val r = android.media.MediaMetadataRetriever()
    var mime: String? = null
    var bitrate: String? = null
    var sampleRate: String? = null
    var bits: String? = null
    var year: String? = null
    var genre: String? = null
    var duration: Long = 0
    var track: String? = null
    var composer: String? = null
    try {
        r.setDataSource(path)
        mime = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
        bitrate = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_BITRATE)
        year = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_YEAR)
        genre = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_GENRE)
        track = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
        composer = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_COMPOSER)
        duration = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            sampleRate = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)
            bits = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE)
        }
    } catch (_: Exception) {
    } finally {
        runCatching { r.release() }
    }
    val ext = f.extension.uppercase(Locale.ROOT)
    rows += "Formato" to (if (mime != null) "$ext ($mime)" else ext)
    bitrate?.toLongOrNull()?.let { rows += "Calidad" to "${it / 1000} kbps" }
    sampleRate?.toLongOrNull()?.let { sr ->
        rows += "Frecuencia" to String.format(Locale.US, "%.1f kHz", sr / 1000.0) + (bits?.let { " · $it bits" } ?: "")
    }
    if (duration > 0) rows += "Duración" to formatTime(duration)
    rows += "Tamaño" to formatSize(f.length())
    year?.takeIf { it.isNotBlank() && it != "0" }?.let { rows += "Año" to it }
    genre?.takeIf { it.isNotBlank() }?.let { rows += "Género" to it }
    track?.takeIf { it.isNotBlank() }?.let { rows += "Pista" to it }
    composer?.takeIf { it.isNotBlank() }?.let { rows += "Compositor" to it }
    rows += "Carpeta" to (f.parent ?: "")
    rows += "Archivo" to f.name
    rows += "Modificado" to formatDate(f.lastModified())
    SongDetails(rows)
}

fun relativeTime(millis: Long): String {
    val diff = System.currentTimeMillis() - millis
    val min = diff / 60_000
    return when {
        min < 1 -> "Ahora"
        min < 60 -> "Hace $min min"
        min < 60 * 24 -> "Hace ${min / 60} h"
        min < 60 * 24 * 30 -> "Hace ${min / (60 * 24)} días"
        else -> formatDate(millis).substringBefore(' ')
    }
}
