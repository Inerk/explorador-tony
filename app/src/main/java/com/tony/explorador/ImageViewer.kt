package com.tony.explorador

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.io.File

@Composable
fun ImageViewerScreen(files: List<File>, startIndex: Int, onClose: () -> Unit, onHome: () -> Unit = {}) {
    BackHandler(onBack = onClose)
    val pager = rememberPagerState(initialPage = startIndex) { files.size }
    var showBar by remember { mutableStateOf(true) }
    var infoOpen by remember { mutableStateOf(false) }
    BackHandler(enabled = infoOpen) { infoOpen = false }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 1) { page ->
            ZoomableImage(files[page], onTap = { showBar = !showBar }, onSwipeUp = { infoOpen = true }, onSwipeDown = onClose)
        }
        if (showBar) {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .safeDrawingPadding(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cerrar", tint = Color.White)
                }
                Column(Modifier.padding(end = 16.dp)) {
                    val f = files[pager.currentPage]
                    Text(f.name, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${pager.currentPage + 1} de ${files.size} · ${formatSize(f.length())}",
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                IconButton(onClick = { infoOpen = true }) {
                    Icon(androidx.compose.material.icons.Icons.Filled.Info, contentDescription = "Información", tint = Color.White)
                }
                HomeButton(onHome, Modifier.padding(end = 10.dp))
            }
        }
        // deslizar la foto hacia arriba (o el botón ⓘ) muestra su información
        if (infoOpen) MediaInfoSheet(files[pager.currentPage], isVideo = false, onDismiss = { infoOpen = false })
    }
}

@Composable
private fun ZoomableImage(file: File, onTap: () -> Unit, onSwipeUp: () -> Unit = {}, onSwipeDown: () -> Unit = {}) {
    // mientras jalas la foto hacia abajo te sigue el dedo y se va encogiendo
    var pull by remember { mutableFloatStateOf(0f) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier.fillMaxSize()
            .pointerInput(file) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = {
                        if (scale > 1f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f
                    }
                )
            }
            .pointerInput(file) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var dx = 0f; var dy = 0f; var single = true
                    do {
                        val event = awaitPointerEvent()
                        val multiTouch = event.changes.count { it.pressed } > 1
                        if (multiTouch || scale > 1f) single = false
                        else event.changes.firstOrNull()?.let { c ->
                            val p = c.position - c.previousPosition; dx += p.x; dy += p.y
                            pull = if (dy > 0f && kotlin.math.abs(dy) > kotlin.math.abs(dx) * 1.2f) dy else 0f
                        }
                        if (multiTouch || scale > 1f) {
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            scale = (scale * zoom).coerceIn(1f, 6f)
                            offset = if (scale <= 1f) Offset.Zero else {
                                val maxX = size.width * (scale - 1f) / 2f
                                val maxY = size.height * (scale - 1f) / 2f
                                Offset(
                                    (offset.x + pan.x).coerceIn(-maxX, maxX),
                                    (offset.y + pan.y).coerceIn(-maxY, maxY)
                                )
                            }
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                    // un dedo, hacia arriba y casi vertical → información
                    if (single && dy < -140f && kotlin.math.abs(dy) > kotlin.math.abs(dx) * 1.5f) onSwipeUp()
                    // hacia abajo → cerrar y volver a la galería
                    if (single && dy > 170f && kotlin.math.abs(dy) > kotlin.math.abs(dx) * 1.2f) onSwipeDown() else pull = 0f
                }
            },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = file,
            contentDescription = file.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y + pull
                if (pull > 0f) {
                    val k = (1f - pull / (size.height * 1.6f)).coerceIn(0.6f, 1f)
                    scaleX = scale * k; scaleY = scale * k
                    alpha = (1f - pull / size.height).coerceIn(0.3f, 1f)
                }
            }
        )
    }
}
