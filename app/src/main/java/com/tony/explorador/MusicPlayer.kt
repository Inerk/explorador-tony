package com.tony.explorador

import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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

fun playMusic(player: Player, files: List<File>, index: Int) {
    val items = files.map { f ->
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
    player.setMediaItems(items, index, 0L)
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
    val title = info?.title ?: player.mediaMetadata.title?.toString() ?: ""
    val duration = player.duration.coerceAtLeast(1L)
    val progress = (player.currentPosition.toFloat() / duration).coerceIn(0f, 1f) + tick * 0f

    Surface(tonalElevation = 6.dp, modifier = Modifier.fillMaxWidth()) {
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

private enum class PanelMode { COVER, LYRICS, QUEUE }

@Composable
fun MusicPlayerScreen(player: Player, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    var mode by remember { mutableStateOf(PanelMode.COVER) }
    val tick = observePlayer(player, if (mode == PanelMode.COVER) 250L else 100L)

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

    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(top, bottom)))
            .pointerInput(Unit) {} // que los toques no pasen al explorador
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
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("REPRODUCIENDO", fontSize = 11.sp, letterSpacing = 2.sp, color = Color.White.copy(alpha = 0.7f))
                        if (album != null) {
                            Text(album, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    IconButton(onClick = { mode = if (mode == PanelMode.LYRICS) PanelMode.COVER else PanelMode.LYRICS }) {
                        Icon(Icons.Filled.Lyrics, contentDescription = "Letra",
                            tint = if (mode == PanelMode.LYRICS) accent else Color.White)
                    }
                    IconButton(onClick = { mode = if (mode == PanelMode.QUEUE) PanelMode.COVER else PanelMode.QUEUE }) {
                        Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "Lista",
                            tint = if (mode == PanelMode.QUEUE) accent else Color.White)
                    }
                }

                // Panel central: portada / letra / lista
                Box(Modifier.weight(1f).fillMaxWidth().padding(top = 4.dp), contentAlignment = Alignment.TopCenter) {
                    Crossfade(targetState = mode, animationSpec = tween(350), label = "panel") { m ->
                        when (m) {
                            PanelMode.COVER -> CoverArt(
                                cover = info?.cover,
                                playing = player.isPlaying,
                                accent = accent,
                                onTap = { if (lyrics.isNotEmpty()) mode = PanelMode.LYRICS },
                                canNext = player.hasNextMediaItem(),
                                canPrev = player.hasPreviousMediaItem(),
                                onNext = { player.seekToNextMediaItem() },
                                onPrev = { player.seekToPreviousMediaItem() },
                            )
                            PanelMode.LYRICS -> LyricsPanel(
                                lines = lyrics,
                                current = lyricIndex,
                                accent = accent,
                                onSeek = { player.seekTo(it); player.play() }
                            )
                            PanelMode.QUEUE -> QueuePanel(player, accent)
                        }
                    }
                }

                // Título y artista
                Spacer(Modifier.height(12.dp))
                val path = player.currentMediaItem?.mediaId
                val fav = path != null && LibraryStore.isFavorite(path)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (artist.isNotEmpty()) {
                            Text(artist, fontSize = 15.sp, color = Color.White.copy(alpha = 0.75f),
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
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
                if (mode == PanelMode.COVER && lyrics.isNotEmpty()) {
                    val line = lyrics.getOrNull(lyricIndex)?.text?.ifBlank { "♪" } ?: "♪"
                    Text(
                        line, fontSize = 14.sp, color = accent, textAlign = TextAlign.Start,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp).clickable { mode = PanelMode.LYRICS }
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
                            .background(accent)
                            .clickable { if (player.isPlaying) player.pause() else player.play() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (player.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = "Reproducir/pausar",
                            tint = onAccent,
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
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun CoverArt(
    cover: Bitmap?,
    playing: Boolean,
    accent: Color,
    onTap: () -> Unit,
    canNext: Boolean,
    canPrev: Boolean,
    onNext: () -> Unit,
    onPrev: () -> Unit,
) {
    val scale by animateFloatAsState(
        targetValue = if (playing) 1f else 0.86f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow),
        label = "coverScale"
    )
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val next by rememberUpdatedState(onNext)
    val prev by rememberUpdatedState(onPrev)
    val hasNext by rememberUpdatedState(canNext)
    val hasPrev by rememberUpdatedState(canPrev)

    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val side = minOf(maxWidth, maxHeight)
        val shape = RoundedCornerShape(22.dp)
        Box(
            modifier = Modifier.size(side)
                .pointerInput(Unit) {
                    val w = size.width.toFloat()
                    detectHorizontalDragGestures(
                        onDragEnd = {
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
                        onDragCancel = { scope.launch { offsetX.animateTo(0f) } }
                    ) { change, drag ->
                        change.consume()
                        // si no hay a dónde ir, se siente como liga
                        val resist = if ((drag < 0 && !hasNext) || (drag > 0 && !hasPrev)) 0.3f else 1f
                        scope.launch { offsetX.snapTo(offsetX.value + drag * resist) }
                    }
                }
                .graphicsLayer {
                    val frac = (offsetX.value / size.width).coerceIn(-1.5f, 1.5f)
                    translationX = offsetX.value
                    rotationZ = frac * 8f
                    val s = scale * (1f - kotlin.math.abs(frac) * 0.12f)
                    scaleX = s
                    scaleY = s
                    alpha = 1f - kotlin.math.abs(frac) * 0.5f
                }
                .shadow(36.dp, shape, ambientColor = accent, spotColor = accent)
                .clip(shape)
                .background(Color.White.copy(alpha = 0.08f))
                .clickable(onClick = onTap),
            contentAlignment = Alignment.Center
        ) {
            Crossfade(targetState = cover, animationSpec = tween(400), label = "cover") { bmp ->
                if (bmp != null) {
                    Image(remember(bmp) { bmp.asImageBitmap() }, contentDescription = "Portada",
                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.MusicNote, null, modifier = Modifier.size(side * 0.45f),
                            tint = Color.White.copy(alpha = 0.5f))
                    }
                }
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
private fun LyricsPanel(lines: List<LyricLine>, current: Int, accent: Color, onSeek: (Long) -> Unit) {
    if (lines.isEmpty()) {
        Text(
            "Esta canción no tiene letra.\n\nPon un archivo .lrc con el mismo nombre que la canción, en la misma carpeta.\nEjemplo: Canción.mp3 → Canción.lrc",
            textAlign = TextAlign.Center,
            color = Color.White.copy(alpha = 0.75f),
            modifier = Modifier.padding(16.dp)
        )
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
                    .clickable { onSeek(line.timeMs) }
                    .padding(vertical = 9.dp, horizontal = 4.dp)
            )
            if (active) {
                Box(Modifier.width(36.dp).height(3.dp).clip(CircleShape).background(accent))
            }
        }
    }
}

@Composable
private fun QueuePanel(player: Player, accent: Color) {
    val current = player.currentMediaItemIndex
    val count = player.mediaItemCount
    val state = rememberLazyListState()
    LaunchedEffect(Unit) { if (current > 0) state.scrollToItem((current - 1).coerceAtLeast(0)) }
    LazyColumn(state = state, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 12.dp)) {
        itemsIndexed((0 until count).toList()) { _, i ->
            val item = player.getMediaItemAt(i)
            val isCurrent = i == current
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isCurrent) Color.White.copy(alpha = 0.12f) else Color.Transparent)
                    .clickable { player.seekTo(i, 0L); player.play() }
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${i + 1}", fontSize = 13.sp, color = if (isCurrent) accent else Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.width(32.dp)
                )
                Text(
                    item.mediaMetadata.title?.toString() ?: "",
                    color = if (isCurrent) accent else Color.White,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (isCurrent && player.isPlaying) {
                    Icon(Icons.Filled.MusicNote, null, tint = accent, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
