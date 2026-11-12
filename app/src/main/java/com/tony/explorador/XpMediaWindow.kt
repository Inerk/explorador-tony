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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.Stroke
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
    XpWindowFrame(
        state = DesktopState.player,
        title = "Reproductor multimedia",
        icon = {
            Box(
                Modifier.size(18.dp).clip(CircleShape)
                    .background(Brush.radialGradient(listOf(Color(0xFFFFB04A), Color(0xFFE86A0A)))),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(13.dp)) }
        },
        onClose = onClose,
        onMaximize = onOpen,
        modifier = modifier,
        extraCaption = {
            // cambiar el aspecto de la ventana
            XpCaptionButton("◐", XpBlueButton) {
                LibraryStore.changeWindowSkin(
                    if (LibraryStore.windowSkin == WindowSkin.MEDIA) WindowSkin.AMP else WindowSkin.MEDIA
                )
            }
        },
    ) {
        if (LibraryStore.windowSkin == WindowSkin.AMP) {
            AmpSkin(player, title, artist, position, duration, progress, onOpen)
            return@XpWindowFrame
        }
        Column(Modifier.fillMaxSize().background(Wmp.Body)) {
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
                Modifier.fillMaxWidth().weight(1f).padding(8.dp)
                    .clip(RoundedCornerShape(4.dp)).background(Color.Black)
                    .border(1.dp, Color(0xFF2E4A78), RoundedCornerShape(4.dp))
                    .clickable(onClick = onOpen)
            ) {
                Box(Modifier.fillMaxHeight().aspectRatio(1f, matchHeightConstraintsFirst = true).padding(6.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFF14233F)),
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

/** Ecualizador con las frecuencias reales de la canción (graves a la izquierda, agudos a la derecha). */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun Spectrum(playing: Boolean, modifier: Modifier, amp: Boolean = false) {
    val bars = SpectrumAnalyzer.BANDS
    val levels = remember { mutableStateListOf<Float>().apply { repeat(bars) { add(0f) } } }
    val peaks = remember { mutableStateListOf<Float>().apply { repeat(bars) { add(0f) } } }
    val peakHold = remember { IntArray(bars) }
    val isPlaying by androidx.compose.runtime.rememberUpdatedState(playing)
    LaunchedEffect(Unit) {
        while (true) {
            androidx.compose.runtime.withFrameNanos { }
            val data = if (isPlaying) SpectrumAnalyzer.current() else null
            for (i in 0 until bars) {
                val target = data?.get(i)
                val cur = levels[i]
                // sube rápido, baja con "gravedad" como los ecualizadores de verdad
                levels[i] = when {
                    target == null -> (cur - 0.025f).coerceAtLeast(0f)
                    target > cur -> cur + (target - cur) * 0.7f
                    else -> (cur - 0.035f).coerceAtLeast(target)
                }
                if (levels[i] >= peaks[i]) { peaks[i] = levels[i]; peakHold[i] = 18 }
                else if (peakHold[i] > 0) peakHold[i]--
                else peaks[i] = (peaks[i] - 0.012f).coerceAtLeast(0f)
            }
        }
    }
    Canvas(modifier) {
        val gap = 2.dp.toPx()
        val bw = (size.width - gap * (bars - 1)) / bars
        val seg = 3.dp.toPx()
        if (amp) {
            // barras sólidas verde → amarillo → naranja, con picos grises
            val grad = Brush.verticalGradient(
                listOf(Color(0xFFFF6A00), Color(0xFFFFD000), Color(0xFF7CFF00), Color(0xFF00B800)),
                startY = 0f, endY = size.height
            )
            for (i in 0 until bars) {
                val x = i * (bw + gap)
                val h = size.height * levels[i]
                if (h > 0.5f) drawRect(grad, Offset(x, size.height - h), Size(bw, h))
                if (peaks[i] > 0.01f) {
                    drawRect(Color(0xFFB0B0B0), Offset(x, size.height - size.height * peaks[i] - 2f), Size(bw, 2f))
                }
            }
            return@Canvas
        }
        for (i in 0 until bars) {
            val x = i * (bw + gap)
            val h = size.height * levels[i]
            // bloques: verde → amarillo → rojo
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
            if (peaks[i] > 0.01f) {
                val py = size.height - size.height * peaks[i]
                drawRect(Color.White.copy(alpha = 0.85f), Offset(x, py - 2f), Size(bw, 2f))
            }
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


// ───────────── Aspecto clásico: amplificador gris con pantalla verde ─────────────

private object Amp {
    val Body = Brush.verticalGradient(listOf(Color(0xFF4A4C5C), Color(0xFF2C2D38), Color(0xFF23242D)))
    val Green = Color(0xFF00E83A)
    val GreenDim = Color(0xFF0E7A2A)
    val Lcd = Color(0xFF000000)
}

@Composable
private fun Bevel(modifier: Modifier, pressed: Boolean = false, content: @Composable () -> Unit) {
    val light = Color(0xFF8A8D9E)
    val dark = Color(0xFF15161C)
    Box(
        modifier
            .background(Brush.verticalGradient(
                if (pressed) listOf(Color(0xFF2A2B33), Color(0xFF3C3E4B)) else listOf(Color(0xFF6B6E80), Color(0xFF3B3D4A))
            ))
            .drawBehindBevel(if (pressed) dark else light, if (pressed) light else dark),
        contentAlignment = Alignment.Center
    ) { content() }
}

private fun Modifier.drawBehindBevel(topLeft: Color, bottomRight: Color) = this.then(
    Modifier.drawWithContent {
        drawContent()
        val w = size.width; val h = size.height; val t = 1.5.dp.toPx()
        drawRect(topLeft, Offset.Zero, Size(w, t))
        drawRect(topLeft, Offset.Zero, Size(t, h))
        drawRect(bottomRight, Offset(0f, h - t), Size(w, t))
        drawRect(bottomRight, Offset(w - t, 0f), Size(t, h))
    }
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AmpSkin(
    player: Player,
    title: String,
    artist: String,
    position: Long,
    duration: Long,
    progress: Float,
    onOpen: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(Amp.Body).padding(6.dp)) {
        // Pantalla LCD
        Row(
            Modifier.fillMaxWidth().weight(1f)
                .background(Amp.Lcd)
                .drawBehindBevel(Color(0xFF0B0B0F), Color(0xFF6B6E80))
                .clickable(onClick = onOpen)
                .padding(8.dp)
        ) {
            Column(Modifier.width(110.dp)) {
                Text(if (player.isPlaying) "▶" else "❚❚", color = Amp.Green, fontSize = 12.sp)
                Text(
                    formatTime(position),
                    color = Amp.Green, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 30.sp,
                    style = androidx.compose.ui.text.TextStyle(
                        shadow = androidx.compose.ui.graphics.Shadow(Amp.Green.copy(alpha = 0.7f), Offset.Zero, 10f)
                    )
                )
                Spacer(Modifier.weight(1f))
                Spectrum(player.isPlaying, Modifier.fillMaxWidth().height(36.dp), amp = true)
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    (if (artist.isNotEmpty()) "$artist - $title" else title) + "   ***   ",
                    color = Amp.Green, fontFamily = FontFamily.Monospace, fontSize = 12.sp, maxLines = 1,
                    modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                )
                Spacer(Modifier.height(6.dp))
                Row {
                    LcdTag("${formatTime(duration)}")
                    Spacer(Modifier.width(6.dp))
                    LcdTag("STEREO")
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Led(player.shuffleModeEnabled); Text(" ALEATORIO", color = Amp.GreenDim, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Led(player.repeatMode != Player.REPEAT_MODE_OFF); Text(" REPETIR", color = Amp.GreenDim, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        // Barra de posición
        Canvas(
            Modifier.fillMaxWidth().height(14.dp)
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
            drawRect(Color(0xFF15161C), Offset(0f, y - 3f), Size(size.width, 6f))
            drawRect(Color(0xFF6B6E80), Offset(0f, y + 3f), Size(size.width, 1f))
            val tw = 26.dp.toPx()
            val x = (size.width - tw) * progress
            drawRect(Brush.verticalGradient(listOf(Color(0xFFE8D27A), Color(0xFFB08A2A))), Offset(x, 1f), Size(tw, size.height - 2f))
            drawRect(Color(0xFF5A4410), Offset(x, 1f), Size(tw, size.height - 2f), style = Stroke(1f))
        }

        Spacer(Modifier.height(6.dp))
        // Botones
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AmpButton("⏮") { player.seekToPreviousMediaItem() }
            AmpButton("▶") { player.play() }
            AmpButton("❚❚") { player.pause() }
            AmpButton("■") { player.pause(); player.seekTo(0) }
            AmpButton("⏭") { player.seekToNextMediaItem() }
            Spacer(Modifier.width(6.dp))
            AmpToggle("ALE", player.shuffleModeEnabled) { player.shuffleModeEnabled = !player.shuffleModeEnabled }
            AmpToggle("REP", player.repeatMode != Player.REPEAT_MODE_OFF) {
                player.repeatMode = if (player.repeatMode == Player.REPEAT_MODE_OFF) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
            }
        }
        Spacer(Modifier.height(6.dp))
        AmpVolume(player)
    }
}

@Composable
private fun LcdTag(text: String) {
    Text(text, color = Amp.Green, fontSize = 10.sp, fontFamily = FontFamily.Monospace,
        modifier = Modifier.border(1.dp, Amp.GreenDim).padding(horizontal = 4.dp, vertical = 1.dp))
}

@Composable
private fun Led(on: Boolean) {
    Box(Modifier.size(7.dp).clip(CircleShape).background(if (on) Amp.Green else Color(0xFF14361E)))
}

@Composable
private fun AmpButton(glyph: String, onClick: () -> Unit) {
    var down by remember { androidx.compose.runtime.mutableStateOf(false) }
    Bevel(
        Modifier.padding(end = 3.dp).size(34.dp, 26.dp)
            .pointerInput(Unit) {
                detectTapGestures(onPress = { down = true; tryAwaitRelease(); down = false }, onTap = { onClick() })
            },
        pressed = down
    ) {
        Text(glyph, color = Color(0xFFE6E6EE), fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AmpToggle(label: String, on: Boolean, onClick: () -> Unit) {
    Bevel(Modifier.padding(end = 3.dp).height(20.dp).clickable(onClick = onClick), pressed = on) {
        Row(Modifier.padding(horizontal = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Led(on)
            Text(" $label", color = Color(0xFFD0D0DA), fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun AmpVolume(player: Player) {
    var vol by remember { mutableFloatStateOf(player.volume) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("VOL", color = Color(0xFFD0D0DA), fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(6.dp))
        Canvas(
            Modifier.weight(1f).height(14.dp)
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
            // la barra cambia de verde a rojo según el volumen
            val c = androidx.compose.ui.graphics.lerp(Color(0xFF00C030), Color(0xFFE02020), vol)
            drawRect(Color(0xFF15161C), Offset(0f, size.height / 2 - 3f), Size(size.width, 6f))
            drawRect(c, Offset(0f, size.height / 2 - 3f), Size(size.width * vol, 6f))
            val tw = 14.dp.toPx()
            val x = (size.width - tw) * vol
            drawRect(Brush.verticalGradient(listOf(Color(0xFFCFD2E0), Color(0xFF7A7D90))), Offset(x, 0f), Size(tw, size.height))
        }
    }
}
