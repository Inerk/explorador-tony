package com.tony.explorador

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player

private object Mod {
    val Navy = Color(0xFF0E1F3D)
    val NavyDeep = Color(0xFF0A1730)
    val Panel = Color(0xFF13284B)
    val Line = Color(0xFF2C4A78)
    val Orange = Color(0xFFFF8A1F)
    val OrangeDeep = Color(0xFFE85D04)
    val Text = Color.White
    val Sub = Color(0xFFB9C8E2)
}

/** Cuadritos de luz azules (como pixeles de ecualizador) para el fondo. */
private fun DrawScope.pixelBlocks(x0: Float, y0: Float, cols: Int, rows: Int, cell: Float, seed: Int, fromRight: Boolean) {
    for (c in 0 until cols) {
        val h = ((kotlin.math.sin((c + seed) * 1.7f) + 1f) / 2f * rows).toInt() + 1
        for (r in 0 until h) {
            val a = 0.10f + 0.18f * (r.toFloat() / rows)
            val x = if (fromRight) x0 - (c + 1) * cell else x0 + c * cell
            drawRect(Color(0xFF2F6FD8).copy(alpha = a), Offset(x, y0 - (r + 1) * cell), Size(cell * 0.82f, cell * 0.82f))
        }
    }
}

/** Ventanita de música «Moderno»: azul marino con naranja, pestañas, portada grande y controles en cápsula. */
@Composable
fun ModernWindowContent(player: Player, onOpen: () -> Unit) {
    val tick = observePlayer(player)
    val info by rememberTrackInfo(player.currentMediaItem?.mediaId)
    val path = player.currentMediaItem?.mediaId
    val title = info?.title ?: player.mediaMetadata.title?.toString() ?: ""
    val artist = info?.artist ?: ""
    val album = info?.album ?: ""
    val duration = player.duration.coerceAtLeast(0L)
    val position = player.currentPosition + tick * 0L
    val progress = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val fav = path != null && LibraryStore.isFavorite(path)

    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Mod.Navy, Mod.NavyDeep)))
            .drawBehind {
                // cuadritos azules arriba a la derecha y el trazo naranja
                pixelBlocks(size.width - 4.dp.toPx(), size.height * 0.42f, 9, 5, 10.dp.toPx(), 3, true)
                val p = Path().apply {
                    moveTo(size.width * 0.62f, size.height * 0.44f)
                    quadraticBezierTo(size.width * 0.85f, size.height * 0.33f, size.width, size.height * 0.27f)
                }
                drawPath(p, Brush.horizontalGradient(listOf(Color.Transparent, Mod.Orange)), style = Stroke(3.dp.toPx()))
            }
            .padding(10.dp)
    ) {
        // pestañas
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.shadow(6.dp, CircleShape, spotColor = Mod.Orange).clip(CircleShape)
                .background(Brush.verticalGradient(listOf(Color(0xFFFFA040), Mod.OrangeDeep)))
                .clickable(onClick = onOpen).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(15.dp))
                Text(" Reproduciendo", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            Tab(Icons.Filled.MusicNote, "Biblioteca") { AppNav.musicLaunch++ }
            Tab(Icons.AutoMirrored.Filled.QueueMusic, "Lista") { onOpen() }
        }
        Box(Modifier.fillMaxWidth().padding(vertical = 8.dp).height(1.dp).background(Mod.Line))

        // portada + datos
        Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.fillMaxHeight().aspectRatio(1f, matchHeightConstraintsFirst = true)
                .shadow(10.dp, RoundedCornerShape(10.dp)).clip(RoundedCornerShape(10.dp))
                .border(2.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(10.dp))
                .background(Mod.Panel).clickable(onClick = onOpen), contentAlignment = Alignment.Center) {
                val c = info?.cover
                if (c != null) Image(remember(c) { c.asImageBitmap() }, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else Icon(Icons.Filled.MusicNote, null, tint = Mod.Sub, modifier = Modifier.size(36.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Mod.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 21.sp)
                if (artist.isNotEmpty()) Text(artist, color = Mod.Orange, fontSize = 14.sp, fontStyle = FontStyle.Italic, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (album.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                    Icon(Icons.Filled.Album, null, tint = Mod.Sub, modifier = Modifier.size(13.dp))
                    Text(" $album", color = Mod.Sub, fontSize = 11.sp, fontStyle = FontStyle.Italic, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Square(if (fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, if (fav) Mod.Orange else Color.White) {
                        path?.let { LibraryStore.toggleFavorite(it) } }
                    Square(Icons.AutoMirrored.Filled.PlaylistAdd) { onOpen() }
                    Square(Icons.Filled.Shuffle, if (player.shuffleModeEnabled) Mod.Orange else Color.White) {
                        player.shuffleModeEnabled = !player.shuffleModeEnabled }
                    Square(Icons.Filled.MoreHoriz) { onOpen() }
                }
            }
        }

        // barra de avance naranja con botón plateado
        Canvas(
            Modifier.fillMaxWidth().height(22.dp).padding(top = 6.dp)
                .pointerInput(duration) { detectTapGestures { o -> if (duration > 0) player.seekTo((o.x / size.width * duration).toLong()) } }
                .pointerInput(duration) {
                    detectHorizontalDragGestures { ch, _ ->
                        ch.consume()
                        if (duration > 0) player.seekTo((ch.position.x / size.width).coerceIn(0f, 1f).times(duration).toLong())
                    }
                }
        ) { orangeTrack(progress) }
        Row(Modifier.fillMaxWidth()) {
            Text(formatTime(position), color = Mod.Sub, fontSize = 11.sp)
            Spacer(Modifier.weight(1f))
            Text(formatTime(duration), color = Mod.Sub, fontSize = 11.sp)
        }

        // cápsula de controles
        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(18.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF1B3763), Mod.Panel)))
                .border(1.dp, Mod.Line, RoundedCornerShape(18.dp))
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically
        ) {
            Plain(Icons.Filled.Shuffle, player.shuffleModeEnabled) { player.shuffleModeEnabled = !player.shuffleModeEnabled }
            Pill(Icons.Filled.SkipPrevious) { player.seekToPrevious() }
            Box(Modifier.size(54.dp).shadow(10.dp, CircleShape, spotColor = Color(0xFF8FB4E8)).clip(CircleShape)
                .background(Brush.radialGradient(listOf(Color(0xFF1D355E), Mod.NavyDeep)))
                .border(3.dp, Brush.verticalGradient(listOf(Color.White, Color(0xFF8FA6CC))), CircleShape)
                .clickable { if (player.isPlaying) player.pause() else player.play() }, contentAlignment = Alignment.Center) {
                Icon(if (player.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Reproducir", tint = Mod.Orange, modifier = Modifier.size(32.dp))
            }
            Pill(Icons.Filled.SkipNext) { player.seekToNextMediaItem() }
            Plain(if (player.repeatMode == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat, player.repeatMode != Player.REPEAT_MODE_OFF) {
                player.repeatMode = when (player.repeatMode) {
                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                    else -> Player.REPEAT_MODE_OFF
                }
            }
        }

        // volumen
        var vol by remember { mutableFloatStateOf(player.volume) }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(14.dp)).background(Mod.Panel.copy(alpha = 0.7f))
            .padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Filled.VolumeUp, null, tint = Mod.Sub, modifier = Modifier.size(18.dp))
            Canvas(Modifier.weight(1f).height(20.dp).padding(horizontal = 8.dp)
                .pointerInput(Unit) { detectTapGestures { o -> vol = (o.x / size.width).coerceIn(0f, 1f); player.volume = vol } }
                .pointerInput(Unit) { detectHorizontalDragGestures { ch, _ -> ch.consume(); vol = (ch.position.x / size.width).coerceIn(0f, 1f); player.volume = vol } }
            ) { orangeTrack(vol, thumb = 7f) }
            // barritas naranjas que se mueven con la música
            Spectrum(playing = player.isPlaying, modifier = Modifier.size(width = 26.dp, height = 16.dp))
        }
    }
}

private fun DrawScope.orangeTrack(p: Float, thumb: Float = 8f) {
    val y = size.height / 2; val th = 5.dp.toPx(); val x = size.width * p
    drawRoundRect(Color(0xFF0A1220), Offset(0f, y - th / 2), Size(size.width, th), CornerRadius(th))
    drawRoundRect(Color.White.copy(alpha = 0.25f), Offset(0f, y - th / 2), Size(size.width, th), CornerRadius(th), style = Stroke(1f))
    drawRoundRect(Brush.horizontalGradient(listOf(Mod.OrangeDeep, Mod.Orange)), Offset(0f, y - th / 2), Size(x, th), CornerRadius(th))
    val r = thumb.dp.toPx()
    drawCircle(Color.Black.copy(alpha = 0.3f), r + 1.5f, Offset(x, y + 1.5f))
    drawCircle(Brush.radialGradient(listOf(Color.White, Color(0xFFB9C3D6), Color(0xFF7D8AA3)), Offset(x - r * 0.3f, y - r * 0.3f), r * 1.4f), r, Offset(x, y))
}

@Composable
private fun Tab(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(Modifier.padding(start = 6.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(15.dp))
        Text(" $label", color = Mod.Sub, fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
private fun Square(icon: ImageVector, tint: Color = Color.White, onClick: () -> Unit) {
    Box(Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).background(Mod.Panel).border(1.dp, Mod.Line, RoundedCornerShape(9.dp))
        .clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(17.dp))
    }
}

@Composable
private fun Pill(icon: ImageVector, onClick: () -> Unit) {
    Box(Modifier.size(width = 50.dp, height = 34.dp).clip(CircleShape).background(Mod.NavyDeep.copy(alpha = 0.6f))
        .border(1.5.dp, Color(0xFF6E8DB8), CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun Plain(icon: ImageVector, on: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(34.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = if (on) Mod.Orange else Color.White, modifier = Modifier.size(20.dp))
    }
}
