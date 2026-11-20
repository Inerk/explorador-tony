package com.tony.explorador

import android.graphics.Bitmap
import android.graphics.Color as AColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

fun File.isPdf(): Boolean = isFile && extension.lowercase(Locale.ROOT) == "pdf"

/** Un PDF abierto: el renderizador de Android sólo deja dibujar una página a la vez. */
private class PdfDoc(file: File) {
    private val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = PdfRenderer(fd)
    private val lock = Mutex()
    val pageCount = renderer.pageCount
    val ratios: List<Float> = (0 until pageCount).map { i ->
        renderer.openPage(i).use { p -> p.height.toFloat() / p.width.coerceAtLeast(1) }
    }

    suspend fun render(index: Int, widthPx: Int): Bitmap? = lock.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                renderer.openPage(index).use { page ->
                    val w = widthPx.coerceIn(200, 2400)
                    val h = (w * page.height.toFloat() / page.width.coerceAtLeast(1)).toInt().coerceAtLeast(1)
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(AColor.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bmp
                }
            }.getOrNull()
        }
    }

    fun close() { runCatching { renderer.close() }; runCatching { fd.close() } }
}

private val pdfThumbs = LruCache<String, Bitmap>(60)
private val pdfThumbLock = Mutex()

/** Primera página del PDF en chiquito, para las vistas de cuadrícula. */
suspend fun loadPdfThumb(file: File, widthPx: Int = 300): Bitmap? {
    val key = file.absolutePath + "@" + file.lastModified()
    pdfThumbs.get(key)?.let { return it }
    return pdfThumbLock.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                    PdfRenderer(fd).use { r ->
                        r.openPage(0).use { page ->
                            val h = (widthPx * page.height.toFloat() / page.width.coerceAtLeast(1)).toInt().coerceAtLeast(1)
                            Bitmap.createBitmap(widthPx, h, Bitmap.Config.ARGB_8888).also {
                                it.eraseColor(AColor.WHITE)
                                page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            }
                        }
                    }
                }
            }.getOrNull()?.also { pdfThumbs.put(key, it) }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PdfViewerScreen(original: File, onClose: () -> Unit, onHome: () -> Unit) {
    val context = LocalContext.current
    // si se guarda una copia editada, el visor pasa a mostrar esa copia
    var file by remember { mutableStateOf(original) }
    var editing by remember { mutableStateOf(false) }
    var version by remember { mutableIntStateOf(0) }
    if (editing) {
        PdfEditorScreen(file) { saved -> if (saved != null) { file = saved; version++ }; editing = false }
        return
    }
    BackHandler(onBack = onClose)
    val doc by produceState<Result<PdfDoc>?>(null, file, version) {
        value = withContext(Dispatchers.IO) { runCatching { PdfDoc(file) } }
    }
    // ojo: hay que cerrar el documento que existía en ese momento, no el que haya después
    DisposableEffect(doc) {
        val opened = doc?.getOrNull()
        onDispose { opened?.close() }
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val listState = rememberLazyListState()
    val widthPx = with(LocalDensity.current) { LocalConfiguration.current.screenWidthDp.dp.roundToPx() }

    Surface(Modifier.fillMaxSize().pointerInput(Unit) {}, color = Color(0xFF3A3D42)) {
        Column(Modifier.fillMaxSize()) {
            Surface(color = Color(0xFF26282C)) {
                Row(Modifier.statusBarsPadding().fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás", tint = Color.White) }
                    Column(Modifier.weight(1f)) {
                        Text(file.nameWithoutExtension, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val d = doc?.getOrNull()
                        if (d != null) {
                            val page by remember { derivedStateOf { listState.firstVisibleItemIndex + 1 } }
                            Text("Página $page de ${d.pageCount}", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                        }
                    }
                    IconButton(onClick = { editing = true }, enabled = doc?.isSuccess == true) { Icon(Icons.Filled.Edit, "Editar PDF", tint = Color.White) }
                    IconButton(onClick = { shareFiles(context, listOf(file)) }) { Icon(Icons.Filled.Share, "Compartir", tint = Color.White) }
                    HomeButton(onHome, Modifier.padding(end = 10.dp))
                }
            }
            val r = doc
            when {
                r == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color.White) }
                r.isFailure -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("No se pudo abrir este PDF.\nPuede estar dañado o protegido con contraseña.", color = Color.White)
                }
                else -> {
                    val d = r.getOrThrow()
                    val scope = rememberCoroutineScope()
                    Box(
                        Modifier.fillMaxSize()
                            .pointerInput(d) {
                                // pellizcar para acercar; ya con zoom, un dedo arrastra la hoja hacia cualquier lado
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                    val tracker = VelocityTracker()
                                    var moved = false
                                    do {
                                        val ev = awaitPointerEvent(PointerEventPass.Initial)
                                        val pressed = ev.changes.count { it.pressed }
                                        if (pressed >= 2 || (scale > 1f && pressed == 1)) {
                                            val zoom = if (pressed >= 2) ev.calculateZoom() else 1f
                                            val pan = ev.calculatePan()
                                            val q = ev.calculateCentroid(useCurrent = true)
                                            val c = Offset(size.width / 2f, size.height / 2f)
                                            val ns = (scale * zoom).coerceIn(1f, 5f)
                                            val z = ns / scale
                                            var t = (q - c) - (q - c - offset) * z + pan
                                            if (q == Offset.Unspecified) t = offset + pan
                                            val maxX = size.width * (ns - 1f) / 2f
                                            val maxY = size.height * (ns - 1f) / 2f
                                            val ty = t.y.coerceIn(-maxY, maxY)
                                            val over = t.y - ty
                                            // lo que sobra de arrastre vertical pasa las páginas
                                            if (over != 0f && ns > 1f) listState.dispatchRawDelta(-over / ns)
                                            else if (over != 0f && pressed >= 2) listState.dispatchRawDelta(-over)
                                            scale = ns
                                            offset = if (ns <= 1f) Offset.Zero else Offset(t.x.coerceIn(-maxX, maxX), ty)
                                            ev.changes.forEach { if (it.positionChanged()) it.consume() }
                                            if (pressed == 1) ev.changes.firstOrNull { it.pressed }?.let { tracker.addPosition(it.uptimeMillis, it.position); moved = true }
                                            else tracker.resetTracking()
                                        }
                                    } while (ev.changes.any { it.pressed })
                                    if (moved && scale > 1f) {
                                        val v = tracker.calculateVelocity()
                                        scope.launch {
                                            listState.scroll {
                                                var prev = 0f
                                                AnimationState(0f, -v.y / scale).animateDecay(exponentialDecay()) { scrollBy(value - prev); prev = value }
                                            }
                                        }
                                    }
                                }
                            }
                            .pointerInput(Unit) {
                                detectTapGestures(onDoubleTap = { p ->
                                    if (scale > 1f) { scale = 1f; offset = Offset.Zero }
                                    else {
                                        scale = 2.5f
                                        val c = Offset(size.width / 2f, size.height / 2f)
                                        val t = (p - c) * (1f - 2.5f)
                                        val maxX = size.width * 1.5f / 2f; val maxY = size.height * 1.5f / 2f
                                        offset = Offset(t.x.coerceIn(-maxX, maxX), t.y.coerceIn(-maxY, maxY))
                                    }
                                })
                            }
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize().graphicsLayer {
                                scaleX = scale; scaleY = scale
                                translationX = offset.x; translationY = offset.y
                            },
                            contentPadding = PaddingValues(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(d.pageCount) { i ->
                                val base by produceState<Bitmap?>(null, d, i) { value = d.render(i, (widthPx * 1.6f).toInt()) }
                                // con mucho zoom se vuelve a dibujar más nítida
                                val sharp = scale > 1.8f
                                val hi by produceState<Bitmap?>(null, d, i, sharp) { value = if (sharp) d.render(i, 2400) else null }
                                val bmp = hi ?: base
                                Box(
                                    Modifier.fillMaxWidth().aspectRatio(1f / d.ratios[i].coerceIn(0.2f, 5f))
                                        .shadow(6.dp, RoundedCornerShape(2.dp)).clip(RoundedCornerShape(2.dp)).background(Color.White),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val b = bmp
                                    if (b != null) Image(remember(b) { b.asImageBitmap() }, "Página ${i + 1}",
                                        contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxSize())
                                    else CircularProgressIndicator(color = Color.Gray)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
