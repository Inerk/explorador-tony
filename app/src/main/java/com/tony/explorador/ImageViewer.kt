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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import kotlinx.coroutines.launch
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
import androidx.compose.foundation.layout.size
import coil.compose.AsyncImage
import java.io.File

@Composable
fun ImageViewerScreen(allFiles: List<File>, startIndex: Int, onClose: () -> Unit, onHome: () -> Unit = {}) {
    BackHandler(onBack = onClose)
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // lista propia para poder quitar la foto que se manda a la papelera
    val files = remember { androidx.compose.runtime.mutableStateListOf<File>().apply { addAll(allFiles) } }
    var busy by remember { mutableStateOf(false) }
    val pager = rememberPagerState(initialPage = startIndex) { files.size }
    if (files.isEmpty()) { androidx.compose.runtime.LaunchedEffect(Unit) { onClose() }; return }
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
                    val f = files[pager.currentPage.coerceIn(0, files.lastIndex)]
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
        // menú de abajo: favorito, editar, mejorar, compartir, eliminar
        if (showBar && !infoOpen) {
            val f = files[pager.currentPage.coerceIn(0, files.lastIndex)]
            val fav = LibraryStore.isFavorite(f.absolutePath)
            Row(
                Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(bottom = 14.dp)
                    .background(Color(0xFF232326), androidx.compose.foundation.shape.RoundedCornerShape(50))
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                @Composable
                fun Btn(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, tint: Color = Color.White, onClick: () -> Unit) {
                    IconButton(onClick = onClick, enabled = !busy, modifier = Modifier.padding(horizontal = 6.dp)) {
                        Icon(icon, desc, tint = tint, modifier = Modifier.size(26.dp))
                    }
                }
                Btn(if (fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "Favorito", if (fav) Color(0xFFFF4F7B) else Color.White) {
                    LibraryStore.toggleFavorite(f.absolutePath)
                }
                Btn(Icons.Filled.Edit, "Editar") {
                    // abre el editor de fotos del teléfono
                    runCatching {
                        val uri = androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".files", f)
                        context.startActivity(android.content.Intent.createChooser(
                            android.content.Intent(android.content.Intent.ACTION_EDIT).setDataAndType(uri, "image/*")
                                .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION),
                            "Editar con"))
                    }.onFailure { android.widget.Toast.makeText(context, "No encontré un editor de fotos", android.widget.Toast.LENGTH_SHORT).show() }
                }
                Btn(Icons.Filled.AutoAwesome, "Mejorar") {
                    busy = true
                    scope.launch {
                        val out = enhancePhoto(f)
                        busy = false
                        if (out != null) {
                            android.media.MediaScannerConnection.scanFile(context, arrayOf(out.absolutePath), null, null)
                            files.add(pager.currentPage + 1, out)
                            pager.animateScrollToPage(pager.currentPage + 1)
                            android.widget.Toast.makeText(context, "Guardé una copia mejorada ✨", android.widget.Toast.LENGTH_SHORT).show()
                        } else android.widget.Toast.makeText(context, "No se pudo mejorar esta foto", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
                Btn(Icons.Filled.Share, "Compartir") { shareFiles(context, listOf(f)) }
                Btn(Icons.Filled.Delete, "Eliminar") {
                    busy = true
                    scope.launch {
                        val ok = Trash.moveToTrash(listOf(f)) == 1
                        busy = false
                        if (ok) {
                            files.remove(f)
                            android.widget.Toast.makeText(context, "Enviada a la papelera", android.widget.Toast.LENGTH_SHORT).show()
                        } else android.widget.Toast.makeText(context, "No se pudo eliminar", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        // deslizar la foto hacia arriba (o el botón ⓘ) muestra su información
        if (infoOpen) MediaInfoSheet(files[pager.currentPage.coerceIn(0, files.lastIndex)], isVideo = false, onDismiss = { infoOpen = false })
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


/** «Mejorar»: un poco más de contraste, color y brillo. Guarda una copia junto a la original. */
private suspend fun enhancePhoto(file: File): File? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    runCatching {
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 4096) sample *= 2
        val src = android.graphics.BitmapFactory.decodeFile(file.absolutePath,
            android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }) ?: return@runCatching null
        // respetar la orientación con que se tomó
        val rot = when (android.media.ExifInterface(file.absolutePath).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1)) {
            android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        val base = if (rot == 0f) src else android.graphics.Bitmap.createBitmap(src, 0, 0, src.width, src.height,
            android.graphics.Matrix().apply { postRotate(rot) }, true)
        val out = android.graphics.Bitmap.createBitmap(base.width, base.height, android.graphics.Bitmap.Config.ARGB_8888)
        val contrast = 1.12f
        val shift = (-0.5f * contrast + 0.5f) * 255f + 6f
        val m = android.graphics.ColorMatrix().apply { setSaturation(1.22f) }
        m.postConcat(android.graphics.ColorMatrix(floatArrayOf(
            contrast, 0f, 0f, 0f, shift,
            0f, contrast, 0f, 0f, shift,
            0f, 0f, contrast, 0f, shift,
            0f, 0f, 0f, 1f, 0f)))
        android.graphics.Canvas(out).drawBitmap(base, 0f, 0f, android.graphics.Paint().apply { colorFilter = android.graphics.ColorMatrixColorFilter(m) })
        val target = uniqueTarget(file.parentFile ?: return@runCatching null, file.nameWithoutExtension + "_mejorada.jpg")
        target.outputStream().use { out.compress(android.graphics.Bitmap.CompressFormat.JPEG, 94, it) }
        target
    }.getOrNull()
}
