package com.tony.explorador

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

private data class AlbumEntry(val name: String, val artist: String, val songs: List<Song>)

/**
 * Vista de álbumes en carrusel 3D (como el Cover Flow de los iPod).
 * Desliza para pasar álbumes, toca uno de los lados para ir a él y toca el del centro para voltearlo y ver sus canciones.
 */
@Composable
fun CoverFlowScreen(
    startPath: String?,
    onPlay: (List<Song>, Int) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    androidx.compose.runtime.LaunchedEffect(Unit) { if (LibraryStore.songs.isEmpty()) LibraryStore.scan() }
    val albums = remember(LibraryStore.songs) {
        LibraryStore.songs.groupBy { it.album }
            .map { (name, list) ->
                val artists = list.map { it.artist }.distinct()
                AlbumEntry(name, if (artists.size == 1) artists.first() else "Varios artistas", list.sortedBy { it.title.lowercase(Locale.ROOT) })
            }
            .sortedBy { it.name.lowercase(Locale.ROOT) }
    }
    val startIndex = remember(albums) {
        val album = startPath?.let { p -> LibraryStore.songFor(p)?.album }
        albums.indexOfFirst { it.name == album }.coerceAtLeast(0)
    }
    val pos = remember { Animatable(startIndex.toFloat()) }
    val scope = rememberCoroutineScope()
    var flipped by remember { mutableIntStateOf(-1) } // índice del álbum volteado (-1 = ninguno)
    val flip by animateFloatAsState(if (flipped >= 0) 180f else 0f, tween(550), label = "flip")

    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF000000), Color(0xFF15151A), Color(0xFF000000))))
            .pointerInput(Unit) {}
    ) {
        if (albums.isEmpty()) {
            Text(
                "No hay álbumes todavía.\nAbre Mi música para que se busquen tus canciones.",
                color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(24.dp)
            )
        } else {
            BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
                val itemSize: Dp = minOf(maxWidth * 0.58f, maxHeight * 0.55f, 300.dp)
                val density = LocalDensity.current
                val itemPx = with(density) { itemSize.toPx() }
                val current = pos.value.roundToInt().coerceIn(0, albums.lastIndex)

                // Zona de gestos: deslizar para mover, tocar para elegir
                Box(
                    Modifier.fillMaxSize()
                        .pointerInput(albums.size) {
                            val tracker = VelocityTracker()
                            detectHorizontalDragGestures(
                                onDragStart = { tracker.resetTracking(); if (flipped >= 0) flipped = -1 },
                                onDragEnd = {
                                    val v = tracker.calculateVelocity().x
                                    val target = (pos.value - v / (itemPx * 1.6f)).roundToInt().coerceIn(0, albums.lastIndex)
                                    scope.launch { pos.animateTo(target.toFloat(), spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow)) }
                                },
                                onDragCancel = {
                                    scope.launch { pos.animateTo(pos.value.roundToInt().toFloat().coerceIn(0f, albums.lastIndex.toFloat())) }
                                }
                            ) { change, drag ->
                                change.consume()
                                tracker.addPosition(change.uptimeMillis, change.position)
                                scope.launch {
                                    pos.snapTo((pos.value - drag / (itemPx * 0.55f)).coerceIn(-0.4f, albums.lastIndex + 0.4f))
                                }
                            }
                        }
                        .pointerInput(albums.size) {
                            detectTapGestures { o ->
                                val current = pos.value.roundToInt().coerceIn(0, albums.lastIndex)
                                val centerX = size.width / 2f
                                val dx = o.x - centerX
                                if (abs(dx) < itemPx / 2f) {
                                    flipped = if (flipped == current) -1 else current
                                } else {
                                    // tocar a un lado: brincar 1 o 2 álbumes según qué tan lejos
                                    val steps = if (abs(dx) > itemPx * 1.0f) 2 else 1
                                    val target = (current + steps * sign(dx).toInt()).coerceIn(0, albums.lastIndex)
                                    flipped = -1
                                    scope.launch { pos.animateTo(target.toFloat(), spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow)) }
                                }
                            }
                        }
                )

                // Las portadas (de las lejanas a la del centro, para que la del centro quede encima)
                val visible = ((current - 6)..(current + 6)).filter { it in albums.indices }
                    .sortedByDescending { abs(it - pos.value) }
                visible.forEach { i ->
                    val off = i - pos.value
                    val isFlipping = i == current && flip > 0f
                    Box(
                        Modifier.align(Alignment.Center)
                            .offset(y = (-40).dp)
                            .zIndex(100f - abs(off) * 10f)
                            .graphicsLayer {
                                val a = off.coerceIn(-1f, 1f)
                                val far = (abs(off) - 1f).coerceAtLeast(0f)
                                translationX = sign(off) * (abs(a) * itemPx * 0.62f + far * itemPx * 0.24f)
                                rotationY = if (isFlipping) flip else -a * 62f
                                val s = 1f - abs(a) * 0.16f
                                scaleX = s
                                scaleY = s
                                alpha = (1f - (abs(off) - 4f).coerceAtLeast(0f)).coerceIn(0f, 1f)
                                cameraDistance = 14f * density.density
                            }
                    ) {
                        val album = albums[i]
                        if (isFlipping && flip > 90f) {
                            // parte de atrás: lista de canciones
                            Box(Modifier.graphicsLayer { rotationY = 180f }) {
                                AlbumBack(album, itemSize, onPlay = { idx -> onPlay(album.songs, idx) })
                            }
                        } else {
                            AlbumFront(album, itemSize)
                        }
                    }
                }

                // Nombre del álbum
                val shown = albums[current]
                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 28.dp, start = 24.dp, end = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(shown.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 19.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                    Text(shown.artist, color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(6.dp))
                    Text("${current + 1} de ${albums.size}", color = Color.White.copy(alpha = 0.35f), fontSize = 12.sp)
                }
            }
        }

        IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(8.dp)) {
            Icon(Icons.Filled.Close, "Cerrar", tint = Color.White.copy(alpha = 0.8f))
        }
    }
}

@Composable
private fun AlbumFront(album: AlbumEntry, size: Dp) {
    val cover by produceState<Bitmap?>(null, album.name) { value = loadCover(album.songs.first().path) }
    Column {
        CoverSquare(cover, size)
        // reflejo
        Box(Modifier.width(size).height(size * 0.35f).clipToBounds()) {
            Box(
                Modifier.wrapContentHeight(Alignment.Top, unbounded = true)
                    .graphicsLayer { scaleY = -1f; alpha = 0.35f }
            ) { CoverSquare(cover, size) }
            Box(
                Modifier.matchParentSize().background(
                    Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.25f), Color.Black))
                )
            )
        }
    }
}

@Composable
private fun CoverSquare(cover: Bitmap?, size: Dp) {
    Box(
        Modifier.size(size).background(Color(0xFF2A2A30)),
        contentAlignment = Alignment.Center
    ) {
        if (cover != null) {
            Image(remember(cover) { cover.asImageBitmap() }, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(Icons.Filled.MusicNote, null, tint = Color.White.copy(alpha = 0.35f), modifier = Modifier.size(size * 0.4f))
        }
    }
}

@Composable
private fun AlbumBack(album: AlbumEntry, size: Dp, onPlay: (Int) -> Unit) {
    Column(
        Modifier.size(size).background(Brush.verticalGradient(listOf(Color(0xFFF4F4F6), Color(0xFFDADADF))))
    ) {
        Row(
            Modifier.fillMaxWidth().background(Color(0xFF2B2B33)).clickable { onPlay(0) }.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(album.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(album.artist, color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Filled.PlayArrow, "Reproducir álbum", tint = Color(0xFFFF7A00))
        }
        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(album.songs) { i, s ->
                Row(
                    Modifier.fillMaxWidth().clickable { onPlay(i) }.padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${i + 1}", color = Color(0xFF8A8A92), fontSize = 11.sp, modifier = Modifier.width(22.dp))
                    Text(s.title, color = Color(0xFF1E1E24), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f))
                    if (s.durationMs > 0) Text(formatTime(s.durationMs), color = Color(0xFF8A8A92), fontSize = 11.sp)
                }
            }
        }
    }
}
