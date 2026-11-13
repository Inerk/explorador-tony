package com.tony.explorador

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Text
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.ui.PlayerView
import java.io.File

@Composable
fun VideoPlayerScreen(
    files: List<File>,
    startIndex: Int,
    onClose: () -> Unit,
    onHome: () -> Unit = {},
    resume: Boolean = false,
    onPlayAsMusic: ((List<File>, Int, Long) -> Unit)? = null,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val view = LocalView.current
    var controlsVisible by remember { mutableStateOf(true) }
    var landscape by remember { mutableStateOf(false) }
    var infoOpen by remember { mutableStateOf(false) }

    // el reproductor vive aparte para que el video siga en la ventanita al ir al inicio
    val player = remember { VideoSession.obtain(context, files, startIndex, resume).also { VideoSession.floating = false } }

    DisposableEffect(Unit) {
        val window = activity?.window
        val insets = window?.let { WindowCompat.getInsetsController(it, view) }
        insets?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insets?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            insets?.show(WindowInsetsCompat.Type.systemBars())
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            // si no se fue a la ventanita, se cierra el video
            if (!VideoSession.floating) VideoSession.release()
        }
    }

    BackHandler(onBack = onClose)

    val tick = observePlayer(player, 300)
    var hud by remember { mutableStateOf<String?>(null) }      // letrerito del gesto (+10 s, 2x, brillo…)
    var scrub by remember { mutableStateOf<Float?>(null) }
    val current = VideoSession.files.getOrNull(player.currentMediaItemIndex.coerceAtLeast(0))
    // los controles se esconden solos
    androidx.compose.runtime.LaunchedEffect(controlsVisible, player.isPlaying, scrub) {
        if (controlsVisible && player.isPlaying && scrub == null) { kotlinx.coroutines.delay(3500); controlsVisible = false }
    }
    androidx.compose.runtime.LaunchedEffect(hud) { if (hud != null) { kotlinx.coroutines.delay(900); hud = null } }
    DisposableEffect(Unit) {
        onDispose { activity?.window?.let { w -> w.attributes = w.attributes.apply { screenBrightness = -1f } } }   // brillo normal al salir
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false          // controles propios, abajo y sin oscurecer la pantalla
                    this.player = player
                    keepScreenOn = true
                }
            },
            onRelease = { it.player = null },
            modifier = Modifier.fillMaxSize()
        )
        // capa de gestos
        Box(
            Modifier.fillMaxSize().pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    var dx = 0f; var dy = 0f
                    val slop = viewConfiguration.touchSlop
                    // ¿toque, arrastre o dejar presionado?
                    val released: Boolean? = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        var r: Boolean? = null
                        while (r == null) {
                            val c = awaitPointerEvent().changes.first()
                            if (!c.pressed) r = true
                            else {
                                dx += c.position.x - c.previousPosition.x; dy += c.position.y - c.previousPosition.y
                                if (kotlin.math.abs(dx) > slop || kotlin.math.abs(dy) > slop) r = false
                            }
                        }
                        r
                    }
                    when (released) {
                        true -> controlsVisible = !controlsVisible
                        null -> {
                            // dejar presionado: izquierda = lento, derecha = rápido
                            val fast = down.position.x > size.width / 2f
                            player.setPlaybackSpeed(if (fast) 2f else 0.5f)
                            hud = if (fast) "Rápido 2x ▶▶" else "Lento 0.5x"
                            do {
                                val e = awaitPointerEvent()
                                hud = if (fast) "Rápido 2x ▶▶" else "Lento 0.5x"
                            } while (e.changes.any { it.pressed })
                            player.setPlaybackSpeed(1f)
                            hud = null
                        }
                        false -> {
                            val horizontal = kotlin.math.abs(dx) > kotlin.math.abs(dy)
                            val w = activity?.window
                            val startBright = w?.attributes?.screenBrightness?.takeIf { it >= 0f } ?: 0.5f
                            do {
                                val e = awaitPointerEvent()
                                val c = e.changes.first()
                                dx += c.position.x - c.previousPosition.x; dy += c.position.y - c.previousPosition.y
                                c.consume()
                                if (horizontal) {
                                    val secs = if (kotlin.math.abs(dx) > size.width * 0.3f) 10 else 5
                                    hud = if (dx > 0) "+$secs s ▶▶" else "◀◀ −$secs s"
                                } else if (w != null) {
                                    // subir = más brillo, bajar = menos
                                    val b = (startBright - dy / size.height).coerceIn(0.02f, 1f)
                                    w.attributes = w.attributes.apply { screenBrightness = b }
                                    hud = "Brillo ${(b * 100).toInt()}%"
                                }
                            } while (e.changes.any { it.pressed })
                            if (horizontal) {
                                val secs = if (kotlin.math.abs(dx) > size.width * 0.3f) 10 else 5
                                val target = player.currentPosition + (if (dx > 0) secs else -secs) * 1000L
                                player.seekTo(target.coerceIn(0L, player.duration.coerceAtLeast(0L)))
                            }
                        }
                    }
                }
            }
        )
        // letrerito del gesto (chico, sin oscurecer el video)
        hud?.let {
            Text(it, color = Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.55f), androidx.compose.foundation.shape.RoundedCornerShape(50))
                    .padding(horizontal = 18.dp, vertical = 9.dp))
        }
        if (controlsVisible) {
            Row(
                modifier = Modifier.align(Alignment.TopStart).safeDrawingPadding().padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cerrar", tint = Color.White)
                }
                // ir al inicio deja el video en una ventanita
                HomeButton({
                    VideoSession.floating = true
                    VideoSession.window.minimized = false
                    onHome()
                })
            }
            Row(Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(4.dp)) {
                if (current != null) {
                    val fav = LibraryStore.isFavorite(current.absolutePath)
                    IconButton(onClick = { LibraryStore.toggleFavorite(current.absolutePath) }) {
                        Icon(if (fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, contentDescription = "Favorito",
                            tint = if (fav) Color(0xFFFF4F7B) else Color.White)
                    }
                }
                if (onPlayAsMusic != null) {
                    IconButton(onClick = {
                        val i = player.currentMediaItemIndex.coerceAtLeast(0)
                        onPlayAsMusic(VideoSession.files, i, player.currentPosition)
                    }) { Icon(Icons.Filled.LibraryMusic, contentDescription = "Reproducir como música", tint = Color.White) }
                }
                IconButton(onClick = { infoOpen = true }) {
                    Icon(Icons.Filled.Info, contentDescription = "Información", tint = Color.White)
                }
                IconButton(onClick = {
                    landscape = !landscape
                    activity?.requestedOrientation =
                        if (landscape) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }) { Icon(Icons.Filled.ScreenRotation, contentDescription = "Girar", tint = Color.White) }
            }
            // controles abajo
            val dur = player.duration.coerceAtLeast(0L)
            val pos = (player.currentPosition + tick * 0L).coerceAtLeast(0L)
            androidx.compose.foundation.layout.Column(
                Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(horizontal = 10.dp, vertical = 8.dp)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.45f), androidx.compose.foundation.shape.RoundedCornerShape(22.dp))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                androidx.compose.material3.Slider(
                    value = scrub ?: if (dur > 0) pos.toFloat() / dur else 0f,
                    onValueChange = { scrub = it },
                    onValueChangeFinished = { scrub?.let { player.seekTo((it * dur).toLong()) }; scrub = null },
                    colors = androidx.compose.material3.SliderDefaults.colors(
                        thumbColor = Color.White, activeTrackColor = Color(0xFFD64FC4), inactiveTrackColor = Color.White.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth().height(30.dp)
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(formatTime(scrub?.let { (it * dur).toLong() } ?: pos), color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = { player.seekToPreviousMediaItem() }, enabled = player.hasPreviousMediaItem()) {
                        Icon(Icons.Filled.SkipPrevious, "Anterior", tint = Color.White.copy(alpha = if (player.hasPreviousMediaItem()) 1f else 0.3f))
                    }
                    IconButton(onClick = { if (player.isPlaying) player.pause() else player.play() }) {
                        Icon(if (player.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Reproducir", tint = Color.White,
                            modifier = Modifier.size(36.dp))
                    }
                    IconButton(onClick = { player.seekToNextMediaItem() }, enabled = player.hasNextMediaItem()) {
                        Icon(Icons.Filled.SkipNext, "Siguiente", tint = Color.White.copy(alpha = if (player.hasNextMediaItem()) 1f else 0.3f))
                    }
                    Text(formatTime(dur), color = Color.White, fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.End,
                        modifier = Modifier.weight(1f))
                }
            }
        }
        if (infoOpen && current != null) MediaInfoSheet(current, isVideo = true, onDismiss = { infoOpen = false })
    }
}
