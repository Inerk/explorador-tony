package com.tony.explorador

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import kotlin.math.roundToInt

/** Ventanita de "Reproductor" estilo XP sobre el escritorio. Se puede arrastrar por la barra de título. */
@Composable
fun XpMediaWindow(
    player: Player,
    onOpen: () -> Unit,
    onMinimize: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tick = observePlayer(player)
    val info by rememberTrackInfo(player.currentMediaItem?.mediaId)
    var drag by remember { mutableStateOf(Offset.Zero) }
    val title = info?.title ?: player.mediaMetadata.title?.toString() ?: ""
    val artist = info?.artist ?: ""
    val album = info?.album ?: ""
    val duration = player.duration.coerceAtLeast(0L)
    val position = player.currentPosition + tick * 0L
    val progress = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val windowShape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 2.dp, bottomEnd = 2.dp)

    Column(
        modifier
            .offset { IntOffset(drag.x.roundToInt(), drag.y.roundToInt()) }
            .width(300.dp)
            .shadow(12.dp, windowShape)
            .clip(windowShape)
            .background(XpColors.TitleBottom)
            .padding(start = 3.dp, end = 3.dp, bottom = 3.dp)
    ) {
        // Barra de título (se arrastra de aquí)
        Row(
            Modifier.fillMaxWidth().height(30.dp)
                .background(XpColors.titleBrush)
                .pointerInput(Unit) {
                    detectDragGestures { change, amount ->
                        change.consume()
                        drag += amount
                    }
                }
                .pointerInput(Unit) { detectTapGestures(onDoubleTap = { onOpen() }) }
                .padding(start = 6.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.MusicNote, null, tint = Color(0xFFFFC94D), modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(5.dp))
            Text(
                "Reproductor de música", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
            )
            CaptionButton("_", Brush.verticalGradient(listOf(Color(0xFF5C93F7), Color(0xFF2D63D9))), onMinimize)
            Spacer(Modifier.width(2.dp))
            CaptionButton("□", Brush.verticalGradient(listOf(Color(0xFF5C93F7), Color(0xFF2D63D9))), onOpen)
            Spacer(Modifier.width(2.dp))
            CaptionButton("✕", Brush.verticalGradient(listOf(Color(0xFFE88C6F), Color(0xFFC4401E))), onClose)
        }

        // Cuerpo de la ventana
        Column(Modifier.fillMaxWidth().background(XpColors.Window).padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable(onClick = onOpen)) {
                Box(
                    Modifier.size(64.dp)
                        .border(1.dp, Color(0xFF7F9DB9))
                        .background(Color(0xFF1C2C4C)),
                    contentAlignment = Alignment.Center
                ) {
                    val cover = info?.cover
                    if (cover != null) {
                        Image(remember(cover) { cover.asImageBitmap() }, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else {
                        Icon(Icons.Filled.MusicNote, null, tint = Color(0xFF9CC9F5), modifier = Modifier.size(32.dp))
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (artist.isNotEmpty()) Text(artist, color = Color(0xFF333333), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (album.isNotEmpty()) Text(album, color = Color(0xFF6D6D6D), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(10.dp))

            // Barra de avance con bloques verdes, como la de copiar archivos
            Canvas(
                Modifier.fillMaxWidth().height(16.dp)
                    .pointerInput(duration) {
                        detectTapGestures { o ->
                            if (duration > 0) player.seekTo((o.x / size.width * duration).toLong())
                        }
                    }
            ) {
                drawRoundRect(Color.White, cornerRadius = CornerRadius(3.dp.toPx()))
                drawRoundRect(Color(0xFF7F9DB9), cornerRadius = CornerRadius(3.dp.toPx()),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
                val pad = 2.dp.toPx()
                val block = 7.dp.toPx()
                val gap = 2.dp.toPx()
                val filled = (size.width - pad * 2) * progress
                var x = pad
                while (x + block <= pad + filled) {
                    drawRect(
                        Brush.verticalGradient(
                            listOf(Color(0xFFB5F0A8), Color(0xFF34C424), Color(0xFF1E9A12)),
                            startY = pad, endY = size.height - pad
                        ),
                        topLeft = Offset(x, pad),
                        size = Size(block, size.height - pad * 2)
                    )
                    x += block + gap
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatTime(position), fontSize = 11.sp, color = Color(0xFF444444))
                Text(formatTime(duration), fontSize = 11.sp, color = Color(0xFF444444))
            }
            Spacer(Modifier.height(6.dp))

            // Controles
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                XpRoundButton(Icons.Filled.SkipPrevious, 36) { player.seekToPreviousMediaItem() }
                Spacer(Modifier.width(12.dp))
                XpRoundButton(if (player.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, 46, highlight = true) {
                    if (player.isPlaying) player.pause() else player.play()
                }
                Spacer(Modifier.width(12.dp))
                XpRoundButton(Icons.Filled.SkipNext, 36) { player.seekToNextMediaItem() }
            }
        }
    }
}

@Composable
private fun CaptionButton(label: String, brush: Brush, onClick: () -> Unit) {
    Box(
        Modifier.size(22.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(brush)
            .border(1.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(3.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

@Composable
private fun XpRoundButton(icon: ImageVector, sizeDp: Int, highlight: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.size(sizeDp.dp)
            .shadow(3.dp, CircleShape)
            .clip(CircleShape)
            .background(
                if (highlight) Brush.verticalGradient(listOf(Color(0xFF9CDB7A), Color(0xFF3C9A3C)))
                else Brush.verticalGradient(listOf(Color.White, Color(0xFFC9D3E6)))
            )
            .border(1.dp, if (highlight) Color(0xFF2E7D2E) else Color(0xFF7F9DB9), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = if (highlight) Color.White else Color(0xFF1E3F7A), modifier = Modifier.size((sizeDp * 0.6f).dp))
    }
}
