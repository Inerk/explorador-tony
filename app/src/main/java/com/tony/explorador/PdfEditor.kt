package com.tony.explorador

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixNormal
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.hypot

/*
 * Editor de PDF dentro del explorador:
 *  - Anotar: pluma, resaltador, texto, firma y borrador.
 *  - Páginas: girar, mover, borrar, extraer, unir con otro PDF y agregar hojas en blanco.
 *  - Guardar como copia o reemplazar el original.
 */

/** Dibuja páginas de varios PDF (el original y los que se unen) con el renderizador de Android. */
private class PdfSources {
    private val open = HashMap<File, Pair<ParcelFileDescriptor, PdfRenderer>>()
    private val lock = Mutex()
    private fun r(f: File) = open.getOrPut(f) { val fd = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY); fd to PdfRenderer(fd) }.second
    suspend fun count(f: File): Int = lock.withLock { withContext(Dispatchers.IO) { r(f).pageCount } }
    suspend fun ratio(f: File, i: Int): Float = lock.withLock { withContext(Dispatchers.IO) { r(f).openPage(i).use { it.height.toFloat() / it.width.coerceAtLeast(1) } } }
    suspend fun render(f: File, i: Int, w: Int): Bitmap? = lock.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                r(f).openPage(i).use { p ->
                    val h = (w * p.height.toFloat() / p.width.coerceAtLeast(1)).toInt().coerceAtLeast(1)
                    Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { it.eraseColor(android.graphics.Color.WHITE); p.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY) }
                }
            }.getOrNull()
        }
    }
    fun close() { open.values.forEach { runCatching { it.second.close() }; runCatching { it.first.close() } }; open.clear() }
}

private enum class PdfTool(val label: String, val icon: ImageVector) {
    PEN("Pluma", Icons.Filled.Edit), MARKER("Resaltar", Icons.Filled.BorderColor), TEXT("Texto", Icons.Filled.TextFields),
    SIGN("Firma", Icons.Filled.Draw), ERASE("Borrar", Icons.Filled.AutoFixNormal)
}

private val inkColors = listOf(0xFF111111.toInt(), 0xFF1E5BFF.toInt(), 0xFFE53935.toInt(), 0xFF2E9E4F.toInt(), 0xFFFFD600.toInt(), 0xFFFF6FB0.toInt())
private val BarBg = Color(0xFF26282C)

@Composable
fun PdfEditorScreen(file: File, onClose: (saved: File?) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val src = remember { PdfSources() }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { src.close() } }

    val pages = remember { mutableStateListOf<PdfPageRef>() }
    val ratios = remember { mutableStateMapOf<Long, Float>() }
    val marks = remember { mutableStateMapOf<Long, List<PdfMark>>() }
    var nextId by remember { mutableStateOf(1L) }
    var loaded by remember { mutableStateOf(false) }
    var dirty by remember { mutableStateOf(false) }
    var tab by remember { mutableIntStateOf(0) }
    var current by remember { mutableIntStateOf(0) }
    var saving by remember { mutableStateOf(false) }
    var askSave by remember { mutableStateOf(false) }
    var askExit by remember { mutableStateOf(false) }

    suspend fun addPagesOf(f: File, at: Int = pages.size) {
        val n = src.count(f)
        val list = (0 until n).map { i -> PdfPageRef(nextId++, f, i) }
        list.forEach { ratios[it.id] = src.ratio(f, it.index) }
        pages.addAll(at.coerceIn(0, pages.size), list)
    }
    androidx.compose.runtime.LaunchedEffect(file) {
        runCatching { addPagesOf(file) }.onFailure { Toast.makeText(ctx, "No pude abrir este PDF para editar.", Toast.LENGTH_LONG).show(); onClose(null) }
        loaded = true
    }

    fun doSave(target: File) {
        saving = true
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { PdfEditing.save(ctx, pages.toList(), marks.toMap(), target) } }
            saving = false
            if (r.isSuccess) { Toast.makeText(ctx, "Guardado: ${target.name}", Toast.LENGTH_SHORT).show(); dirty = false; onClose(target) }
            else Toast.makeText(ctx, "No se pudo guardar: ${r.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
        }
    }

    BackHandler { if (dirty) askExit = true else onClose(null) }

    Surface(Modifier.fillMaxSize().pointerInput(Unit) {}, color = Color(0xFF3A3D42)) {
        Column(Modifier.fillMaxSize()) {
            Surface(color = BarBg) {
                Column(Modifier.statusBarsPadding()) {
                    Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { if (dirty) askExit = true else onClose(null) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás", tint = Color.White) }
                        Column(Modifier.weight(1f)) {
                            Text("Editar PDF", color = Color.White, fontWeight = FontWeight.Bold)
                            Text(file.name, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, maxLines = 1)
                        }
                        Row(Modifier.clip(CircleShape).background(Color(0xFF6C4BE0)).clickable(enabled = loaded && !saving) { askSave = true }
                            .padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Save, null, tint = Color.White, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                            Text("Guardar", color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Anotar y firmar", "Páginas").forEachIndexed { i, t ->
                            Text(t, color = Color.White, fontWeight = if (tab == i) FontWeight.Bold else FontWeight.Normal, fontSize = 14.sp,
                                modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(if (tab == i) Color(0xFF6C4BE0) else Color(0x22FFFFFF))
                                    .clickable { tab = i }.padding(vertical = 8.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                }
            }
            if (!loaded || pages.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (!loaded) CircularProgressIndicator(color = Color.White) else Text("El PDF quedó sin páginas. Agrega una en «Páginas».", color = Color.White)
                }
                if (loaded && tab == 1) PagesTab(pages, ratios, src, onChanged = { dirty = true }, onAddPdf = { f -> scope.launch { addPagesOf(f); dirty = true } },
                    onBlank = { val id = nextId++; ratios[id] = 1.4142f; pages.add(PdfPageRef(id, null, 0)); dirty = true },
                    onExtract = { sel -> extract(ctx, file, pages.filter { it.id in sel }, marks, scope) }, onOpenPage = { current = it; tab = 0 })
            } else if (tab == 0) {
                current = current.coerceIn(0, pages.lastIndex)
                AnnotateTab(pages[current], current, pages.size, ratios[pages[current].id] ?: 1.414f, src,
                    marks[pages[current].id] ?: emptyList(),
                    onMarks = { marks[pages[current].id] = it; dirty = true },
                    onPage = { current = it.coerceIn(0, pages.lastIndex) })
            } else {
                PagesTab(pages, ratios, src, onChanged = { dirty = true }, onAddPdf = { f -> scope.launch { addPagesOf(f); dirty = true } },
                    onBlank = { val id = nextId++; ratios[id] = ratios[pages.lastOrNull()?.id] ?: 1.4142f; pages.add(PdfPageRef(id, null, 0, blankRatio = ratios[id]!!)); dirty = true },
                    onExtract = { sel -> extract(ctx, file, pages.filter { it.id in sel }, marks, scope) }, onOpenPage = { current = it; tab = 0 })
            }
        }
        if (saving) Box(Modifier.fillMaxSize().background(Color(0x88000000)), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) { CircularProgressIndicator(color = Color.White); Text("Guardando…", color = Color.White, modifier = Modifier.padding(top = 10.dp)) }
        }
    }

    if (askSave) AlertDialog(onDismissRequest = { askSave = false },
        title = { Text("Guardar PDF") },
        text = { Text("¿Guardar como un archivo nuevo o reemplazar el original?") },
        confirmButton = { TextButton(onClick = { askSave = false; doSave(copyName(file)) }) { Text("Guardar copia") } },
        dismissButton = { TextButton(onClick = { askSave = false; doSave(file) }) { Text("Reemplazar original") } })
    if (askExit) AlertDialog(onDismissRequest = { askExit = false },
        title = { Text("¿Salir sin guardar?") }, text = { Text("Se perderán los cambios que hiciste.") },
        confirmButton = { TextButton(onClick = { askExit = false; onClose(null) }) { Text("Salir") } },
        dismissButton = { TextButton(onClick = { askExit = false }) { Text("Seguir editando") } })
}

private fun copyName(f: File): File {
    var n = 1
    var out = File(f.parentFile, "${f.nameWithoutExtension} (editado).pdf")
    while (out.exists()) { n++; out = File(f.parentFile, "${f.nameWithoutExtension} (editado $n).pdf") }
    return out
}

private fun extract(ctx: android.content.Context, file: File, sel: List<PdfPageRef>, marks: Map<Long, List<PdfMark>>, scope: kotlinx.coroutines.CoroutineScope) {
    if (sel.isEmpty()) { Toast.makeText(ctx, "Primero toca las páginas que quieres sacar.", Toast.LENGTH_SHORT).show(); return }
    scope.launch {
        var n = 1
        var out = File(file.parentFile, "${file.nameWithoutExtension} (páginas).pdf")
        while (out.exists()) { n++; out = File(file.parentFile, "${file.nameWithoutExtension} (páginas $n).pdf") }
        val r = withContext(Dispatchers.IO) { runCatching { PdfEditing.save(ctx, sel, marks, out) } }
        Toast.makeText(ctx, if (r.isSuccess) "Se creó ${out.name} con ${sel.size} página(s)" else "No se pudo: ${r.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
    }
}

// ───────── Anotar ─────────

@Composable
private fun AnnotateTab(ref: PdfPageRef, index: Int, total: Int, ratio: Float, src: PdfSources, pageMarks: List<PdfMark>,
                        onMarks: (List<PdfMark>) -> Unit, onPage: (Int) -> Unit) {
    val ctx = LocalContext.current
    var tool by remember { mutableStateOf(PdfTool.PEN) }
    var color by remember { mutableIntStateOf(inkColors[1]) }
    var thick by remember { mutableFloatStateOf(0.004f) }
    var live by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var textAt by remember { mutableStateOf<Offset?>(null) }
    var signDialog by remember { mutableStateOf<Offset?>(null) }
    val sigFile = remember { File(ctx.filesDir, "firma.png") }
    var sigVersion by remember { mutableIntStateOf(0) }
    val sigBmp = remember(sigVersion) { if (sigFile.exists()) android.graphics.BitmapFactory.decodeFile(sigFile.absolutePath) else null }
    val bmp by produceState<Bitmap?>(null, ref) { value = if (ref.file == null) null else src.render(ref.file, ref.index, 1400) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onPage(index - 1) }, enabled = index > 0) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Anterior", tint = Color.White) }
            Text("Página ${index + 1} de $total", color = Color.White, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            IconButton(onClick = { onPage(index + 1) }, enabled = index < total - 1) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Siguiente", tint = Color.White) }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
            val pw = minOf(maxWidth, maxHeight / ratio)
            Box(Modifier.width(pw).aspectRatio(1f / ratio).background(Color.White)) {
                bmp?.let { Image(remember(it) { it.asImageBitmap() }, null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize()) }
                val sigImg = remember(sigBmp) { sigBmp?.asImageBitmap() }
                Canvas(Modifier.fillMaxSize().pointerInput(tool, color, thick, pageMarks) {
                    val w = size.width.toFloat(); val h = size.height.toFloat()
                    fun norm(o: Offset) = Offset((o.x / w).coerceIn(0f, 1f), (o.y / h).coerceIn(0f, 1f))
                    when (tool) {
                        PdfTool.PEN, PdfTool.MARKER -> detectDragGestures(
                            onDragStart = { live = listOf(norm(it)) },
                            onDragEnd = {
                                val pts = live; live = emptyList()
                                if (pts.isNotEmpty()) onMarks(pageMarks + PdfMark.Stroke(pts, color,
                                    if (tool == PdfTool.MARKER) thick * 4f else thick, if (tool == PdfTool.MARKER) 0.35f else 1f))
                            },
                            onDragCancel = { live = emptyList() }
                        ) { ch, _ -> ch.consume(); live = live + norm(ch.position) }
                        PdfTool.TEXT -> detectTapGestures { textAt = norm(it) }
                        PdfTool.SIGN -> detectTapGestures { p ->
                            val n = norm(p)
                            val b = sigBmp
                            if (b == null) signDialog = n
                            else onMarks(pageMarks + PdfMark.Image(n, sigFile.absolutePath, 0.32f, b.height.toFloat() / b.width))
                        }
                        PdfTool.ERASE -> detectTapGestures { p ->
                            val n = norm(p)
                            val hit = pageMarks.lastOrNull { m -> hits(m, n, h / w) }
                            if (hit != null) onMarks(pageMarks - hit)
                        }
                    }
                }) {
                    val w = size.width; val h = size.height
                    fun px(o: Offset) = Offset(o.x * w, o.y * h)
                    fun drawStroke(pts: List<Offset>, c: Int, sw: Float, a: Float) {
                        if (pts.isEmpty()) return
                        val path = Path().apply { moveTo(px(pts[0]).x, px(pts[0]).y); pts.drop(1).forEach { lineTo(px(it).x, px(it).y) } }
                        if (pts.size == 1) drawCircle(Color(c).copy(alpha = a), sw * w / 2, px(pts[0]))
                        else drawPath(path, Color(c).copy(alpha = a), style = Stroke(sw * w, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                    pageMarks.forEach { m ->
                        when (m) {
                            is PdfMark.Stroke -> drawStroke(m.points, m.color, m.width, m.alpha)
                            is PdfMark.Text -> drawContext.canvas.nativeCanvas.apply {
                                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                    this.color = m.color; textSize = m.size * w; typeface = android.graphics.Typeface.DEFAULT_BOLD
                                }
                                m.text.split("\n").forEachIndexed { i, line -> drawText(line, m.pos.x * w, m.pos.y * h + m.size * w * (0.85f + 1.2f * i), paint) }
                            }
                            is PdfMark.Image -> sigImg?.let { img ->
                                val iw = m.width * w; val ih = iw * m.ratio
                                drawImage(img, dstOffset = androidx.compose.ui.unit.IntOffset((m.pos.x * w - iw / 2).toInt(), (m.pos.y * h - ih / 2).toInt()),
                                    dstSize = androidx.compose.ui.unit.IntSize(iw.toInt(), ih.toInt()))
                            }
                        }
                    }
                    if (live.isNotEmpty()) drawStroke(live, color, if (tool == PdfTool.MARKER) thick * 4f else thick, if (tool == PdfTool.MARKER) 0.35f else 1f)
                }
            }
        }
        // herramientas
        Surface(color = BarBg) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(8.dp)) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    PdfTool.entries.forEach { t ->
                        Column(Modifier.clip(RoundedCornerShape(12.dp)).background(if (tool == t) Color(0xFF6C4BE0) else Color.Transparent)
                            .clickable { tool = t; if (t == PdfTool.MARKER && color == inkColors[1]) color = inkColors[4] }.padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(t.icon, t.label, tint = Color.White, modifier = Modifier.size(22.dp))
                            Text(t.label, color = Color.White, fontSize = 11.sp)
                        }
                    }
                    Column(Modifier.clip(RoundedCornerShape(12.dp)).clickable(enabled = pageMarks.isNotEmpty()) { onMarks(pageMarks.dropLast(1)) }
                        .padding(horizontal = 10.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.AutoMirrored.Filled.Undo, "Deshacer", tint = if (pageMarks.isNotEmpty()) Color.White else Color.Gray, modifier = Modifier.size(22.dp))
                        Text("Deshacer", color = Color.White, fontSize = 11.sp)
                    }
                }
                when (tool) {
                    PdfTool.PEN, PdfTool.MARKER, PdfTool.TEXT -> Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        inkColors.forEach { c ->
                            Box(Modifier.padding(end = 8.dp).size(26.dp).clip(CircleShape).background(Color(c))
                                .border(if (color == c) 3.dp else 1.dp, if (color == c) Color.White else Color(0x55FFFFFF), CircleShape).clickable { color = c })
                        }
                        if (tool != PdfTool.TEXT) Slider(thick, { thick = it }, valueRange = 0.0015f..0.012f, modifier = Modifier.weight(1f))
                    }
                    PdfTool.SIGN -> Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (sigBmp == null) "Toca la hoja para hacer tu firma." else "Toca la hoja para poner tu firma.", color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = { signDialog = Offset(0.5f, 0.85f) }) { Text(if (sigBmp == null) "Hacer firma" else "Nueva firma") }
                    }
                    PdfTool.ERASE -> Text("Toca un trazo, texto o firma para quitarlo.", color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                }
                // tamaño de la última firma
                val lastSig = pageMarks.lastOrNull() as? PdfMark.Image
                if (tool == PdfTool.SIGN && lastSig != null) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Tamaño", color = Color.White, fontSize = 12.sp)
                    Slider(lastSig.width, { v -> onMarks(pageMarks.dropLast(1) + lastSig.copy(width = v)) }, valueRange = 0.1f..0.7f, modifier = Modifier.weight(1f).padding(start = 8.dp))
                }
            }
        }
    }

    textAt?.let { at ->
        var txt by remember { mutableStateOf("") }
        var size by remember { mutableFloatStateOf(0.035f) }
        AlertDialog(onDismissRequest = { textAt = null }, title = { Text("Escribir texto") },
            text = {
                Column {
                    OutlinedTextField(txt, { txt = it }, label = { Text("Texto") }, minLines = 2)
                    Text("Tamaño", fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                    Slider(size, { size = it }, valueRange = 0.015f..0.08f)
                }
            },
            confirmButton = { TextButton(onClick = { if (txt.isNotBlank()) onMarks(pageMarks + PdfMark.Text(at, txt, color, size)); textAt = null }) { Text("Poner") } },
            dismissButton = { TextButton(onClick = { textAt = null }) { Text("Cancelar") } })
    }
    signDialog?.let { at ->
        SignatureDialog(onDismiss = { signDialog = null }) { b ->
            sigFile.outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
            sigVersion++
            onMarks(pageMarks + PdfMark.Image(at, sigFile.absolutePath, 0.32f, b.height.toFloat() / b.width))
            signDialog = null
        }
    }
}

private fun hits(m: PdfMark, p: Offset, aspect: Float): Boolean {
    fun d(a: Offset, b: Offset) = hypot(a.x - b.x, (a.y - b.y) * aspect)
    return when (m) {
        is PdfMark.Stroke -> m.points.any { d(it, p) < 0.03f + m.width }
        is PdfMark.Text -> p.x in (m.pos.x - 0.02f)..(m.pos.x + m.size * 0.6f * m.text.lines().maxOf { it.length } + 0.02f) &&
            (p.y - m.pos.y) * aspect in -0.02f..(m.size * 1.2f * m.text.lines().size + 0.02f)
        is PdfMark.Image -> kotlin.math.abs(p.x - m.pos.x) < m.width / 2 && kotlin.math.abs(p.y - m.pos.y) * aspect < m.width * m.ratio / 2
    }
}

/** Recuadro para dibujar la firma con el dedo; se guarda recortada y con fondo transparente. */
@Composable
private fun SignatureDialog(onDismiss: () -> Unit, onDone: (Bitmap) -> Unit) {
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    var live by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var boxSize by remember { mutableStateOf(Size(1f, 1f)) }
    var ink by remember { mutableIntStateOf(0xFF0D2B8C.toInt()) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Tu firma") },
        text = {
            Column {
                Box(Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(12.dp)).background(Color.White).border(1.dp, Color.LightGray, RoundedCornerShape(12.dp))) {
                    Canvas(Modifier.fillMaxSize().pointerInput(Unit) {
                        detectDragGestures(onDragStart = { live = listOf(it) }, onDragEnd = { if (live.isNotEmpty()) strokes.add(live); live = emptyList() }) { ch, _ ->
                            ch.consume(); live = live + ch.position
                        }
                    }) {
                        boxSize = size
                        (strokes + listOf(live)).forEach { s ->
                            if (s.size > 1) drawPath(Path().apply { moveTo(s[0].x, s[0].y); s.drop(1).forEach { lineTo(it.x, it.y) } }, Color(ink),
                                style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                    }
                    if (strokes.isEmpty() && live.isEmpty()) Text("Firma aquí con el dedo", color = Color.Gray, modifier = Modifier.align(Alignment.Center))
                }
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    listOf(0xFF0D2B8C.toInt(), 0xFF111111.toInt()).forEach { c ->
                        Box(Modifier.padding(end = 8.dp).size(24.dp).clip(CircleShape).background(Color(c)).border(if (ink == c) 3.dp else 0.dp, Color(0xFF6C4BE0), CircleShape).clickable { ink = c })
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { strokes.clear() }) { Text("Borrar") }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = strokes.isNotEmpty(), onClick = {
                val pts = strokes.flatten()
                val minX = pts.minOf { it.x } - 12; val maxX = pts.maxOf { it.x } + 12
                val minY = pts.minOf { it.y } - 12; val maxY = pts.maxOf { it.y } + 12
                val scale = 3f
                val bw = ((maxX - minX) * scale).toInt().coerceAtLeast(10); val bh = ((maxY - minY) * scale).toInt().coerceAtLeast(10)
                val b = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
                val cv = android.graphics.Canvas(b)
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    color = ink; style = android.graphics.Paint.Style.STROKE; strokeWidth = 7f * scale / 2.6f * (boxSize.width / 900f).coerceIn(0.8f, 1.6f)
                    strokeCap = android.graphics.Paint.Cap.ROUND; strokeJoin = android.graphics.Paint.Join.ROUND
                }
                strokes.forEach { s ->
                    val p = android.graphics.Path()
                    s.forEachIndexed { i, o -> val x = (o.x - minX) * scale; val y = (o.y - minY) * scale; if (i == 0) p.moveTo(x, y) else p.lineTo(x, y) }
                    cv.drawPath(p, paint)
                }
                onDone(b)
            }) { Text("Usar firma") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

// ───────── Páginas ─────────

@Composable
private fun PagesTab(pages: MutableList<PdfPageRef>, ratios: Map<Long, Float>, src: PdfSources, onChanged: () -> Unit,
                     onAddPdf: (File) -> Unit, onBlank: () -> Unit, onExtract: (Set<Long>) -> Unit, onOpenPage: (Int) -> Unit) {
    val ctx = LocalContext.current
    var sel by remember { mutableStateOf(setOf<Long>()) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) runCatching {
            val f = File(ctx.cacheDir, "pdf_unir_${System.currentTimeMillis()}.pdf")
            ctx.contentResolver.openInputStream(uri)!!.use { i -> f.outputStream().use { i.copyTo(it) } }
            onAddPdf(f)
        }.onFailure { Toast.makeText(ctx, "No pude abrir ese PDF.", Toast.LENGTH_SHORT).show() }
    }
    fun move(dir: Int) {
        val idx = pages.indices.filter { pages[it].id in sel }.let { if (dir > 0) it.reversed() else it }
        for (i in idx) { val j = i + dir; if (j in pages.indices && pages[j].id !in sel) { val t = pages[i]; pages[i] = pages[j]; pages[j] = t } }
        onChanged()
    }
    Column(Modifier.fillMaxSize()) {
        Text(if (sel.isEmpty()) "Toca las páginas para elegirlas · mantén presionado para anotarla" else "${sel.size} seleccionada(s)",
            color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
        LazyVerticalGrid(GridCells.Adaptive(104.dp), Modifier.weight(1f).padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            itemsIndexed(pages, key = { _, p -> p.id }) { i, p ->
                val on = p.id in sel
                val ratio = ratios[p.id] ?: 1.414f
                val bmp by produceState<ImageBitmap?>(null, p.file, p.index) {
                    value = if (p.file == null) null else src.render(p.file, p.index, 260)?.asImageBitmap()
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.fillMaxWidth().aspectRatio(1f / 1.45f), contentAlignment = Alignment.Center) {
                        val rotated = p.extraRotation % 180 != 0
                        val ar = if (rotated) ratio.coerceIn(0.3f, 3f) else 1f / ratio.coerceIn(0.3f, 3f)
                        Box(Modifier.fillMaxWidth(if (rotated) 0.62f else 0.92f).aspectRatio(if (rotated) 1f / ar else ar)
                            .graphicsLayer { rotationZ = p.extraRotation.toFloat() }
                            .background(Color.White)
                            .border(if (on) 3.dp else 0.dp, Color(0xFF8B6CFF), RoundedCornerShape(2.dp))
                            .pointerInput(p.id) { detectTapGestures(onTap = { sel = if (on) sel - p.id else sel + p.id }, onLongPress = { onOpenPage(i) }) }) {
                            bmp?.let { Image(it, null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize()) }
                        }
                    }
                    Text("${i + 1}", color = if (on) Color(0xFFC9B8FF) else Color.White, fontSize = 12.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
        Surface(color = BarBg) {
            Row(Modifier.fillMaxWidth().navigationBarsPadding().horizontalScroll(rememberScrollState()).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                PageAction(Icons.Filled.RotateRight, "Girar", sel.isNotEmpty()) {
                    for (k in pages.indices) if (pages[k].id in sel) pages[k] = pages[k].copy(extraRotation = (pages[k].extraRotation + 90) % 360)
                    onChanged()
                }
                PageAction(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Antes", sel.isNotEmpty()) { move(-1) }
                PageAction(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Después", sel.isNotEmpty()) { move(1) }
                PageAction(Icons.Filled.Delete, "Borrar", sel.isNotEmpty()) { pages.removeAll { it.id in sel }; sel = emptySet(); onChanged() }
                PageAction(Icons.Filled.CallSplit, "Extraer", sel.isNotEmpty()) { onExtract(sel) }
                PageAction(Icons.Filled.PictureAsPdf, "Unir PDF", true) { pick.launch(arrayOf("application/pdf")) }
                PageAction(Icons.Filled.NoteAdd, "Hoja blanca", true) { onBlank() }
                PageAction(Icons.Filled.Add, if (sel.size == pages.size) "Ninguna" else "Todas", true) { sel = if (sel.size == pages.size) emptySet() else pages.map { it.id }.toSet() }
            }
        }
    }
}

@Composable
private fun PageAction(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(12.dp)).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, label, tint = if (enabled) Color.White else Color.Gray, modifier = Modifier.size(22.dp))
        Text(label, color = if (enabled) Color.White else Color.Gray, fontSize = 11.sp)
    }
}

@Suppress("unused") private fun keepToArgb(c: Color) = c.toArgb()
