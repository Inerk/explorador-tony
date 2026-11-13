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

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    setShowSubtitleButton(false)
                    this.player = player
                    keepScreenOn = true
                    setShowNextButton(files.size > 1)
                    setShowPreviousButton(files.size > 1)
                    setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
                        controlsVisible = visibility == android.view.View.VISIBLE
                    })
                }
            },
            onRelease = { it.player = null },
            modifier = Modifier.fillMaxSize()
        )
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
            if (onPlayAsMusic != null) {
                IconButton(onClick = {
                    val i = player.currentMediaItemIndex.coerceAtLeast(0)
                    onPlayAsMusic(VideoSession.files, i, player.currentPosition)
                }) { Icon(Icons.Filled.LibraryMusic, contentDescription = "Reproducir como música", tint = Color.White) }
            }
            IconButton(onClick = { infoOpen = true }) {
                Icon(Icons.Filled.Info, contentDescription = "Información", tint = Color.White)
            }
            IconButton(
                onClick = {
                    landscape = !landscape
                    activity?.requestedOrientation =
                        if (landscape) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                },
            ) {
                Icon(Icons.Filled.ScreenRotation, contentDescription = "Girar", tint = Color.White)
            }
            }
        }
        if (infoOpen) VideoSession.files.getOrNull(player.currentMediaItemIndex.coerceAtLeast(0))?.let {
            MediaInfoSheet(it, isVideo = true, onDismiss = { infoOpen = false })
        }
    }
}
