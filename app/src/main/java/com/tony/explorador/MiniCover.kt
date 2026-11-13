package com.tony.explorador

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import kotlin.math.roundToInt

/** Dónde dejaste el cuadrito de música (se recuerda mientras la app esté abierta). */
private object MiniCoverPos { var offset by mutableStateOf(Offset.Zero) }

/**
 * Cuadrito flotante de música: la carátula se ve como detrás de un cristal al empezar
 * y se va haciendo más nítida conforme avanza la canción (esa es su «línea de progreso»).
 * Controles semitransparentes encima; al tocarlo aparece el botón para abrir el reproductor.
 * Se puede arrastrar a donde quieras.
 */
@Composable
fun MiniCoverPlayer(player: Player, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val tick = observePlayer(player)
    if (player.mediaItemCount == 0) return
    val path = player.currentMediaItem?.mediaId
    val info by rememberTrackInfo(path)
    val accent = (info?.colors ?: DefaultCoverColors).accent
    LaunchedEffect(info?.colors) { info?.colors?.let { NowPlaying.colors = it } }
    val nowPath = path
    androidx.compose.runtime.SideEffect { NowPlaying.path = nowPath }
    val dur = player.duration.coerceAtLeast(0L)
    val progress = if (dur > 0) ((player.currentPosition + tick * 0L).toFloat() / dur).coerceIn(0f, 1f) else 0f
    var tools by remember { mutableStateOf(false) }
    LaunchedEffect(tools) { if (tools) { kotlinx.coroutines.delay(3500); tools = false } }
    val shape = RoundedCornerShape(22.dp)

    Box(
        modifier
            .offset { IntOffset(MiniCoverPos.offset.x.roundToInt(), MiniCoverPos.offset.y.roundToInt()) }
            .padding(14.dp)
            .size(136.dp)
            .shadow(14.dp, shape)
            .clip(shape)
            // cristal
            .background(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.30f), accent.copy(alpha = 0.25f), Color.White.copy(alpha = 0.12f))))
            .border(1.5.dp, Color.White.copy(alpha = 0.55f), shape)
            .pointerInput(Unit) { detectDragGestures(onDragStart = { tools = true }) { c, d -> c.consume(); MiniCoverPos.offset += d } }
            .pointerInput(Unit) { detectTapGestures(onTap = { tools = !tools }) }
    ) {
        // la carátula se va «revelando»: de cristal borroso a imagen completa
        val cover = info?.cover
        if (cover != null) {
            Image(remember(cover) { cover.asImageBitmap() }, null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 0.18f + 0.82f * progress })
        } else Icon(Icons.Filled.MusicNote, null, tint = Color.White.copy(alpha = 0.3f + 0.7f * progress),
            modifier = Modifier.align(Alignment.Center).size(64.dp))
        // brillo del cristal (se va quitando con el avance)
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = 1f - progress }.background(
            Brush.linearGradient(0f to Color.White.copy(alpha = 0.35f), 0.4f to Color.White.copy(alpha = 0.05f), 0.41f to Color.Transparent,
                start = Offset.Zero, end = Offset(400f, 400f))))
        // nombre abajo
        Text(
            info?.title ?: player.mediaMetadata.title?.toString().orEmpty(),
            color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))))
                .padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 6.dp)
        )
        // controles semitransparentes
        Row(Modifier.align(Alignment.Center).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            @Composable
            fun Ctl(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, big: Boolean = false, onClick: () -> Unit) {
                Box(
                    Modifier.size(if (big) 42.dp else 32.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.32f)).clickable(onClick = onClick),
                    contentAlignment = Alignment.Center
                ) { Icon(icon, desc, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(if (big) 28.dp else 20.dp)) }
            }
            Ctl(Icons.Filled.SkipPrevious, "Anterior") { player.seekToPrevious() }
            Ctl(if (player.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Reproducir", big = true) {
                if (player.isPlaying) player.pause() else player.play()
            }
            Ctl(Icons.Filled.SkipNext, "Siguiente") { player.seekToNext() }
        }
        // al tocar o mover: botón para abrir el reproductor completo (y uno para quitar la música)
        AnimatedVisibility(tools, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut(), modifier = Modifier.align(Alignment.TopEnd)) {
            Box(Modifier.padding(6.dp).size(30.dp).clip(CircleShape).background(accent).clickable(onClick = onOpen), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.OpenInFull, "Abrir reproductor", tint = (info?.colors ?: DefaultCoverColors).onAccent, modifier = Modifier.size(18.dp))
            }
        }
        AnimatedVisibility(tools, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut(), modifier = Modifier.align(Alignment.TopStart)) {
            Box(Modifier.padding(6.dp).size(30.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f))
                .clickable { player.stop(); player.clearMediaItems() }, contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Close, "Quitar música", tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}
