package com.tony.explorador

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.random.Random

private object Wmp {
    val Body = Brush.verticalGradient(listOf(Color(0xFF1B2D4F), Color(0xFF0A1428)))
    val Strip = Brush.verticalGradient(listOf(Color(0xFF3A5583), Color(0xFF1A2C4E), Color(0xFF0E1A33)))
    val Lcd = Color(0xFF9CF2FF)
    val LcdDim = Color(0xFF5FA6C4)
    val Glow = Color(0xFF39A0FF)
}

/** Ventana de reproductor al estilo de los reproductores de PC de los 2000. Se arrastra por la barra de título. */
@OptIn(ExperimentalFoundationApi::class)
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
    val title = info?.title ?: player.mediaMetadata.title?.toString() ?: ""
    val artist = info?.artist ?: ""
    val album = info?.album ?: ""
    val duration = player.duration.coerceAtLeast(0L)
    val position = player.currentPosition + tick * 0L
    val progress = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val windowShape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 6.dp, bottomEnd = 6.dp)
    val off = DesktopState.playerOffset

    Column(
        modifier
            .offset { IntOffset(off.x.roundToInt(), off.y.roundToInt()) }
            .width(310.dp)
            .shadow(14.dp, windowShape)
            .clip(windowShape)
            .background(XpColors.TitleBottom)
            .padding(start = 3.dp, end = 3.dp, bottom = 3.dp)
    ) {
        // Barra de título (de aquí se arrastra)
        Row(
            Modifier.fillMaxWidth().height(30.dp)
                .background(XpColors.titleBrush)
                .pointerInput(Unit) {
                    detectDragGestures { change, amount ->
                        change.consume()
                        DesktopState.playerOffset += amount
                    }
                }
                .pointerInput(Unit) { detectTapGestures(onDoubleTap = { onOpen() }) }
                .padding(start = 6.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(18.dp).clip(CircleShape)
                    .background(Brush.radialGradient(listOf(Color(0xFFFFB04A), Color(0xFFE86A0A)))),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(13.dp)) }
            Spacer(Modifier.width(6.dp))
            Text(
                "Reproductor multimedia", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
            )
            CaptionButton("_", Brush.verticalGradient(listOf(Color(0xFF5C93F7), Color(0xFF2D63D9))), onMinimize)
            Spacer(Modifier.width(2.dp))
            CaptionButton("□", Brush.verticalGradient(listOf(Color(0xFF5C93F7), Color(0xFF2D63D9))), onOpen)
            Spacer(Modifier.width(2.dp))
            CaptionButton("✕", Brush.verticalGradient(listOf(Color(0xFFE88C6F), Color(0xFFC4401E))), onClose)
        }

        Column(Modifier.fillMaxWidth().background(Wmp.Body)) {
            // Franja "Reproduciendo ahora" con el título corriendo
            Row(
                Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.35f)).padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Reproduciendo ahora", color = Wmp.LcdDim, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (artist.isNotEmpty()) "$title  —  $artist" else title,
                    color = Wmp.Lcd, fontSize = 11.sp, maxLines = 1,
                    modifier = Modifier.weight(1f).basicMarquee(iterations = Int.MAX_VALUE)
                )
            }

            // Zona de visualización: portada + barras de ecualizador
            Row(
                Modifier.fillMaxWidth().height(112.dp).padding(8.dp)
                    .clip(RoundedCornerShape(4.dp)).background(Color.Black)
                    .border(1.dp, Color(0xFF2E4A78), RoundedCornerShape(4.dp))
                    .clickable(onClick = onOpen)
            ) {
                Box(Modifier.size(96.dp).padding(6.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFF14233F)),
                    contentAlignment = Alignment.Center) {
                    val cover = info?.cover
                    if (cover != null) Image(remember(cover) { cover.asImageBitmap() }, null,
                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    else Icon(Icons.Filled.MusicNote, null, tint = Wmp.LcdDim, modifier = Modifier.size(40.dp))
                }
                Spectrum(playing = player.isPlaying, modifier = Modifier.weight(1f).fillMaxSize().padding(end = 6.dp, top = 6.dp, bottom = 6.dp))
            }

            // Pantallita LCD con datos
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (album.isNotEmpty()) album else artist, color = Wmp.LcdDim, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    "${formatTime(position)} / ${formatTime(duration)}",
                    color = Wmp.Lcd, fontFamily = FontFamily.Monospace, fontSize = 11.sp
                )
            }

            // Barra de búsqueda brillante
            Canvas(
                Modifier.fillMaxWidth().height(18.dp).padding(horizontal = 10.dp)
                    .pointerInput(duration) {
                        detectTapGestures { o -> if (duration > 0) player.seekTo((o.x / size.width * duration).toLong()) }
                    }
                    .pointerInput(duration) {
                        detectHorizontalDragGestures { change, _ ->
                            change.consume()
                            if (duration > 0) player.seekTo((change.position.x / size.width).coerceIn(0f, 1f).times(duration).toLong())
                        }
                    }
            ) {
                val y = size.height / 2
                val th = 4.dp.toPx()
                drawRoundRect(Color(0xFF0A1220), Offset(0f, y - th / 2), Size(size.width, th), CornerRadius(th))
                val x = size.width * progress
                drawRoundRect(
                    Brush.horizontalGradient(listOf(Color(0xFF1E6FE0), Wmp.Glow, Color(0xFF9CDBFF)), endX = x.coerceAtLeast(1f)),
                    Offset(0f, y - th / 2), Size(x, th), CornerRadius(th)
                )
                drawCircle(Wmp.Glow.copy(alpha = 0.35f), 8.dp.toPx(), Offset(x, y))
                drawCircle(Brush.radialGradient(listOf(Color.White, Color(0xFF9CC9F5)), center = Offset(x, y), radius = 5.dp.toPx()), 5.dp.toPx(), Offset(x, y))
            }

            // Botonera
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp).background(Wmp.Strip).padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassButton(Icons.Filled.Stop, 30) { player.pause(); player.seekTo(0) }
                Spacer(Modifier.width(6.dp))
                GlassButton(Icons.Filled.SkipPrevious, 34) { player.seekToPreviousMediaItem() }
                Spacer(Modifier.width(6.dp))
                PlayOrb(player.isPlaying) { if (player.isPlaying) player.pause() else player.play() }
                Spacer(Modifier.width(6.dp))
                GlassButton(Icons.Filled.SkipNext, 34) { player.seekToNextMediaItem() }
                Spacer(Modifier.weight(1f))
                VolumeSlider(player)
            }
        }
    }
}

/** Barras de ecualizador animadas (decorativas, se mueven al ritmo cuando hay música). */
@Composable
private fun Spectrum(playing: Boolean, modifier: Modifier) {
    val bars = 18
    val levels = remember { mutableStateListOf<Float>().apply { repeat(bars) { add(0f) } } }
    val peaks = remember { mutableStateListOf<Float>().apply { repeat(bars) { add(0f) } } }
    LaunchedEffect(playing) {
        while (true) {
            for (i in 0 until bars) {
                val shape = 1f - (i.toFloat() / bars) * 0.55f // graves más altos
                val target = if (playing) (Random.nextFloat() * 0.75f + 0.25f) * shape else 0f
                levels[i] = levels[i] * 0.55f + target * 0.45f
                peaks[i] = if (levels[i] > peaks[i]) levels[i] else (peaks[i] - 0.025f).coerceAtLeast(0f)
            }
            delay(70)
        }
    }
    Canvas(modifier) {
        val gap = 2.dp.toPx()
        val bw = (size.width - gap * (bars - 1)) / bars
        val seg = 3.dp.toPx()
        for (i in 0 until bars) {
            val x = i * (bw + gap)
            val h = size.height * levels[i]
            // bloques tipo ecualizador: verde → amarillo → rojo
            var y = size.height
            while (y > size.height - h) {
                val frac = 1f - y / size.height
                val c = when {
                    frac > 0.8f -> Color(0xFFFF4A3A)
                    frac > 0.55f -> Color(0xFFFFD23A)
                    else -> Color(0xFF3AE06A)
                }
                drawRect(c, Offset(x, y - seg), Size(bw, seg - 1f))
                y -= seg
            }
            val py = size.height - size.height * peaks[i]
            drawRect(Color.White.copy(alpha = 0.8f), Offset(x, py - 2f), Size(bw, 2f))
        }
    }
}

@Composable
private fun PlayOrb(playing: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp)
            .shadow(6.dp, CircleShape, ambientColor = Wmp.Glow, spotColor = Wmp.Glow)
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(Color(0xFF8FD0FF), Color(0xFF1F6FD8), Color(0xFF0B2E6E))))
            .border(2.dp, Brush.verticalGradient(listOf(Color.White, Color(0xFF6E8DB8))), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        // reflejo de vidrio
        Box(Modifier.align(Alignment.TopCenter).padding(top = 4.dp).size(30.dp, 14.dp).clip(RoundedCornerShape(50))
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.7f), Color.Transparent))))
        Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Reproducir/pausar",
            tint = Color.White, modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun GlassButton(icon: ImageVector, sizeDp: Int, onClick: () -> Unit) {
    Box(
        Modifier.size(sizeDp.dp)
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(Color(0xFF6A84AE), Color(0xFF2A3E62), Color(0xFF1A2742))))
            .border(1.dp, Color(0xFF8FA6CC), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size((sizeDp * 0.58f).dp))
    }
}

@Composable
private fun VolumeSlider(player: Player) {
    var vol by remember { mutableFloatStateOf(player.volume) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.AutoMirrored.Filled.VolumeUp, null, tint = Wmp.LcdDim, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Canvas(
            Modifier.width(64.dp).height(20.dp)
                .pointerInput(Unit) {
                    detectTapGestures { o -> vol = (o.x / size.width).coerceIn(0f, 1f); player.volume = vol }
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, _ ->
                        change.consume()
                        vol = (change.position.x / size.width).coerceIn(0f, 1f)
                        player.volume = vol
                    }
                }
        ) {
            // rampa tipo triángulo
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(0f, size.height * 0.8f); lineTo(size.width, size.height * 0.2f); lineTo(size.width, size.height * 0.8f); close()
            }
            drawPath(path, Color(0xFF0A1220))
            clipPath(path) {
                drawRect(Brush.horizontalGradient(listOf(Color(0xFF1E6FE0), Wmp.Glow)), size = Size(size.width * vol, size.height))
            }
            drawCircle(Color.White, 4.dp.toPx(), Offset(size.width * vol, size.height * (0.8f - 0.6f * vol)))
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
