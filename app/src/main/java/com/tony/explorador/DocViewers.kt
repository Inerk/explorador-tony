package com.tony.explorador

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.WrapText
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FormatAlignLeft
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.TextDecrease
import androidx.compose.material.icons.outlined.TextIncrease
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.util.Locale
import java.util.zip.ZipFile

// ───────── qué se abre con qué ─────────

val TEXT_EXTS = setOf("txt", "md", "log", "json", "csv", "ini", "cfg", "conf", "yml", "yaml", "css", "js", "ts", "kt", "java", "py",
    "c", "cpp", "h", "sh", "bat", "srt", "lrc", "properties", "gradle", "sql", "nfo", "tsv", "toml", "m3u", "m3u8", "xml", "svg", "rtf")
val HTML_EXTS = setOf("html", "htm", "xhtml", "mht")
val OFFICE_EXTS = setOf("docx", "xlsx", "pptx", "doc", "xls", "ppt")

fun isDocViewable(f: File): Boolean { val e = f.extension.lowercase(Locale.ROOT); return e in TEXT_EXTS || e in HTML_EXTS || e in OFFICE_EXTS }

/** Abre el visor correcto según el tipo de documento. */
@Composable
fun DocumentViewer(file: File, onClose: () -> Unit) {
    val ext = file.extension.lowercase(Locale.ROOT)
    when (ext) {
        in HTML_EXTS -> HtmlViewer(file, onClose)
        "docx" -> OfficeShell(file, "Documento de Word", Color(0xFF2B579A), onClose) { WordBody(file) }
        "xlsx" -> OfficeShell(file, "Hoja de Excel", Color(0xFF217346), onClose) { ExcelBody(file) }
        "pptx" -> OfficeShell(file, "Presentación de PowerPoint", Color(0xFFD24726), onClose) { PowerPointBody(file) }
        "doc", "xls", "ppt" -> OfficeShell(file, "Formato viejo de Office", Color(0xFF6E6E6E), onClose) {
            OldOffice(file)
        }
        else -> TextViewer(file, onClose)
    }
}

// ───────── barra de arriba común ─────────

private object DocC {
    private val d get() = XpColors.dark
    val bg get() = if (d) Color(0xFF15121D) else Color(0xFFF6F4FA)
    val page get() = if (d) Color(0xFF1F1B2B) else Color.White
    val text get() = if (d) Color(0xFFEDE8FA) else Color(0xFF1F1A2E)
    val sub get() = if (d) Color(0xFFA79CC4) else Color(0xFF7D7394)
    val line get() = if (d) Color(0xFF332C4A) else Color(0xFFE6DEF7)
}

@Composable
private fun DocTopBar(file: File, subtitle: String, color: Color, onClose: () -> Unit, actions: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().background(color).statusBarsPadding().height(54.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Cerrar", tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f).padding(start = 2.dp)) {
            Text(file.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp, maxLines = 1)
        }
        actions()
    }
}

@Composable
private fun BarIcon(icon: ImageVector, desc: String, on: Boolean = false, onClick: () -> Unit) {
    Box(Modifier.size(40.dp).clip(CircleShape).background(if (on) Color.White.copy(alpha = 0.22f) else Color.Transparent).clickable(onClick = onClick),
        contentAlignment = Alignment.Center) { Icon(icon, desc, tint = Color.White, modifier = Modifier.size(20.dp)) }
}

/** Pantalla completa que tapa el explorador (las fotos/videos que abras salen encima). */
@Composable
private fun FullScreen(content: @Composable () -> Unit) {
    Surface(Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}, color = DocC.bg) {
        content()
    }
}

// ───────── texto ─────────

private fun prettyJson(s: String): String = runCatching {
    val t = s.trim()
    if (t.startsWith("[")) org.json.JSONArray(t).toString(2) else org.json.JSONObject(t).toString(2)
}.getOrDefault(s)

private fun prettyXml(s: String): String {
    val out = StringBuilder(); var depth = 0
    Regex("""(<[^>]+>)|([^<]+)""").findAll(s).forEach { m ->
        val tag = m.groupValues[1]; val txt = m.groupValues[2].trim()
        if (tag.isNotEmpty()) {
            val closing = tag.startsWith("</"); val selfClose = tag.endsWith("/>") || tag.startsWith("<?") || tag.startsWith("<!")
            if (closing) depth = (depth - 1).coerceAtLeast(0)
            out.append("  ".repeat(depth)).append(tag).append('\n')
            if (!closing && !selfClose) depth++
        } else if (txt.isNotEmpty()) out.append("  ".repeat(depth)).append(txt).append('\n')
    }
    return out.toString()
}

@Composable
fun TextViewer(file: File, onClose: () -> Unit, initialText: String? = null, title: String = "Texto") {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ext = file.extension.lowercase(Locale.ROOT)
    var text by remember { mutableStateOf<String?>(initialText) }
    var truncated by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var changed by remember { mutableStateOf(false) }
    var wrap by remember { mutableStateOf(true) }
    var size by remember { mutableFloatStateOf(14f) }
    var searchOn by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var hit by remember { mutableIntStateOf(0) }
    LaunchedEffect(file) {
        if (initialText != null) return@LaunchedEffect
        text = withContext(Dispatchers.IO) {
            runCatching {
                val max = 2_000_000
                truncated = file.length() > max
                val bytes = file.inputStream().use { it.readNBytes(max) }
                String(bytes, Charsets.UTF_8).let { if (it.contains('�') && !bytes.isEmpty()) String(bytes, Charsets.ISO_8859_1) else it }
            }.getOrElse { "No se pudo leer: ${it.message}" }
        }
    }
    BackHandler(onBack = onClose)
    val lines = remember(text) { text?.lines().orEmpty() }
    val hits = remember(lines, query) {
        if (query.length < 2) emptyList() else lines.indices.filter { lines[it].contains(query, true) }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(hit, hits) { hits.getOrNull(hit)?.let { listState.animateScrollToItem(it) } }

    FullScreen {
        Column(Modifier.fillMaxSize()) {
            DocTopBar(file, "$title · ${lines.size} líneas" + if (truncated) " (solo los primeros 2 MB)" else "", Color(0xFF6A3FD0), onClose) {
                BarIcon(Icons.Outlined.Search, "Buscar", searchOn) { searchOn = !searchOn }
                BarIcon(Icons.Outlined.TextDecrease, "Letra más chica") { size = (size - 1).coerceAtLeast(9f) }
                BarIcon(Icons.Outlined.TextIncrease, "Letra más grande") { size = (size + 1).coerceAtMost(28f) }
                BarIcon(Icons.AutoMirrored.Outlined.WrapText, "Ajustar líneas", wrap) { wrap = !wrap }
                if (!truncated && initialText == null) {
                    if (!editing) BarIcon(Icons.Outlined.Edit, "Editar") { editing = true }
                    else BarIcon(Icons.Outlined.Save, "Guardar", changed) {
                        val t = text ?: return@BarIcon
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) { runCatching { file.writeText(t) }.isSuccess }
                            Toast.makeText(context, if (ok) "Guardado ✔" else "No se pudo guardar", Toast.LENGTH_SHORT).show()
                            if (ok) { changed = false; editing = false }
                        }
                    }
                }
            }
            if (searchOn) Row(Modifier.fillMaxWidth().background(DocC.page).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(DocC.bg).padding(10.dp)) {
                    if (query.isEmpty()) Text("Buscar en el texto", color = DocC.sub, fontSize = 14.sp)
                    BasicTextField(query, { query = it; hit = 0 }, singleLine = true, textStyle = TextStyle(color = DocC.text, fontSize = 14.sp),
                        cursorBrush = SolidColor(Color(0xFF6A3FD0)), modifier = Modifier.fillMaxWidth())
                }
                Text(if (hits.isEmpty()) "0" else "${hit + 1}/${hits.size}", color = DocC.sub, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 8.dp))
                Text("▲", color = Color(0xFF6A3FD0), modifier = Modifier.clip(CircleShape).clickable { if (hits.isNotEmpty()) hit = (hit - 1 + hits.size) % hits.size }.padding(8.dp))
                Text("▼", color = Color(0xFF6A3FD0), modifier = Modifier.clip(CircleShape).clickable { if (hits.isNotEmpty()) hit = (hit + 1) % hits.size }.padding(8.dp))
            }
            if ((ext == "json" || ext == "xml" || ext == "svg") && !editing && text != null) Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                Row(Modifier.clip(CircleShape).background(Color(0xFF6A3FD0).copy(alpha = 0.12f)).clickable {
                    text = if (ext == "json") prettyJson(text!!) else prettyXml(text!!)
                }.padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.FormatAlignLeft, null, tint = Color(0xFF6A3FD0), modifier = Modifier.size(16.dp))
                    Text(" Acomodar (formatear)", color = Color(0xFF6A3FD0), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            val t = text
            when {
                t == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                editing -> BasicTextField(t, { text = it; changed = true },
                    textStyle = TextStyle(color = DocC.text, fontSize = size.sp, fontFamily = FontFamily.Monospace),
                    cursorBrush = SolidColor(Color(0xFF6A3FD0)),
                    modifier = Modifier.fillMaxSize().background(DocC.page).verticalScroll(rememberScrollState()).padding(14.dp).navigationBarsPadding())
                else -> {
                    val hs = rememberScrollState()
                    LazyColumn(Modifier.fillMaxSize().background(DocC.page).then(if (wrap) Modifier else Modifier.horizontalScroll(hs)),
                        state = listState, contentPadding = PaddingValues(vertical = 10.dp)) {
                        itemsIndexed(lines) { i, l ->
                            val isHit = hits.getOrNull(hit) == i
                            Row(Modifier.then(if (wrap) Modifier.fillMaxWidth() else Modifier).background(if (isHit) Color(0xFFFFE082).copy(alpha = 0.5f) else Color.Transparent)) {
                                Text("${i + 1}", color = DocC.sub.copy(alpha = 0.6f), fontSize = (size * 0.75f).sp, fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.width(44.dp).padding(end = 8.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                                Text(
                                    if (query.length >= 2 && l.contains(query, true)) buildAnnotatedString {
                                        var idx = 0
                                        while (true) {
                                            val j = l.indexOf(query, idx, true)
                                            if (j < 0) { append(l.substring(idx)); break }
                                            append(l.substring(idx, j))
                                            withStyle(SpanStyle(background = Color(0xFFFFC83D), color = Color.Black)) { append(l.substring(j, j + query.length)) }
                                            idx = j + query.length
                                        }
                                    } else buildAnnotatedString { append(l) },
                                    color = DocC.text, fontSize = size.sp, fontFamily = FontFamily.Monospace, lineHeight = (size * 1.35f).sp,
                                    softWrap = wrap, modifier = Modifier.padding(end = 12.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ───────── HTML ─────────

@Composable
private fun HtmlViewer(file: File, onClose: () -> Unit) {
    var source by remember { mutableStateOf(false) }
    if (source) { TextViewer(file, onClose = { source = false }, title = "Código HTML"); return }
    BackHandler(onBack = onClose)
    FullScreen {
        Column(Modifier.fillMaxSize()) {
            DocTopBar(file, "Página web (sin scripts, por seguridad)", Color(0xFFE8710A), onClose) {
                BarIcon(Icons.Outlined.Code, "Ver código") { source = true }
            }
            AndroidView(factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = false
                    settings.allowFileAccess = true
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    loadUrl(android.net.Uri.fromFile(file).toString())
                }
            }, modifier = Modifier.fillMaxSize().navigationBarsPadding())
        }
    }
}

// ───────── Office (docx / xlsx / pptx) ─────────

@Composable
private fun OfficeShell(file: File, subtitle: String, color: Color, onClose: () -> Unit, body: @Composable () -> Unit) {
    BackHandler(onBack = onClose)
    FullScreen {
        Column(Modifier.fillMaxSize()) {
            DocTopBar(file, subtitle, color, onClose)
            Box(Modifier.weight(1f).fillMaxWidth()) { body() }
        }
    }
}

@Composable
private fun OldOffice(file: File) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
        FriendlyIcon(file, FileKind.OTHER, 80.dp)
        Text("«.${file.extension}» es el formato viejo de Office (antes de 2007).", color = DocC.text, fontWeight = FontWeight.Bold,
            fontSize = 16.sp, modifier = Modifier.padding(top = 16.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Text("Este lector abre los nuevos (.docx, .xlsx, .pptx). Puedes abrirlo con otra app.", color = DocC.sub, fontSize = 13.sp,
            modifier = Modifier.padding(top = 8.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Text("Abrir con otra app", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 18.dp)
            .clip(CircleShape).background(Color(0xFF6A3FD0)).clickable { openExternally(context, file) }.padding(horizontal = 20.dp, vertical = 10.dp))
    }
}

private fun parser(s: java.io.InputStream): XmlPullParser = android.util.Xml.newPullParser().apply {
    setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false); setInput(s, null)
}

/** Rutas de las relaciones (rId → archivo dentro del zip). */
private fun rels(zip: ZipFile, relsPath: String, baseDir: String): Map<String, String> {
    val e = zip.getEntry(relsPath) ?: return emptyMap()
    val m = HashMap<String, String>()
    zip.getInputStream(e).use { inp ->
        val p = parser(inp)
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            if (p.eventType == XmlPullParser.START_TAG && p.name == "Relationship") {
                val id = p.getAttributeValue(null, "Id") ?: continue
                val target = p.getAttributeValue(null, "Target") ?: continue
                m[id] = if (target.startsWith("/")) target.trimStart('/') else java.net.URI("$baseDir/").resolve(target).normalize().toString()
            }
        }
    }
    return m
}

private fun zipImage(zip: ZipFile, path: String, maxSide: Int = 1400): Bitmap? = runCatching {
    val e = zip.getEntry(path) ?: return null
    val bytes = zip.getInputStream(e).use { it.readBytes() }
    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
    var s = 1; while (o.outWidth / s > maxSide || o.outHeight / s > maxSide) s *= 2
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = s })
}.getOrNull()

// Word

private class Run(val text: String, val bold: Boolean, val italic: Boolean, val underline: Boolean)
private sealed class DocBlock {
    class Para(val runs: List<Run>, val heading: Int, val bullet: Boolean, val center: Boolean) : DocBlock()
    class Table(val rows: List<List<String>>) : DocBlock()
    class Img(val bmp: Bitmap) : DocBlock()
}

private fun parseDocx(f: File): List<DocBlock> = ZipFile(f).use { zip ->
    val r = rels(zip, "word/_rels/document.xml.rels", "word")
    val out = ArrayList<DocBlock>()
    val p = parser(zip.getInputStream(zip.getEntry("word/document.xml") ?: throw Exception("No es un .docx válido")))
    var runs = ArrayList<Run>(); var heading = 0; var bullet = false; var center = false
    var bold = false; var italic = false; var underline = false; var inRun = false
    var tableDepth = 0; var rows = ArrayList<List<String>>(); var row = ArrayList<String>(); var cell = StringBuilder()
    val pendingImgs = ArrayList<Bitmap>()
    while (p.next() != XmlPullParser.END_DOCUMENT) {
        when (p.eventType) {
            XmlPullParser.START_TAG -> when (p.name) {
                "w:tbl" -> { tableDepth++; if (tableDepth == 1) rows = ArrayList() }
                "w:tr" -> if (tableDepth == 1) row = ArrayList()
                "w:tc" -> if (tableDepth == 1) cell = StringBuilder()
                "w:p" -> { runs = ArrayList(); heading = 0; bullet = false; center = false }
                "w:pStyle" -> {
                    val v = p.getAttributeValue(null, "w:val").orEmpty().lowercase(Locale.ROOT)
                    heading = when { v.contains("title") || v == "titulo" -> 1; v.startsWith("heading") || v.startsWith("ttulo") || v.startsWith("titulo") ->
                        v.filter { it.isDigit() }.toIntOrNull()?.coerceIn(1, 4) ?: 2; else -> heading }
                    if (v.contains("list")) bullet = true
                }
                "w:numPr" -> bullet = true
                "w:jc" -> if (p.getAttributeValue(null, "w:val") == "center") center = true
                "w:r" -> { inRun = true; bold = false; italic = false; underline = false }
                "w:b" -> if (inRun) bold = p.getAttributeValue(null, "w:val").let { it == null || it == "1" || it == "true" }
                "w:i" -> if (inRun) italic = p.getAttributeValue(null, "w:val").let { it == null || it == "1" || it == "true" }
                "w:u" -> if (inRun) underline = p.getAttributeValue(null, "w:val") != "none"
                "w:t" -> { val t = p.nextText(); runs.add(Run(t, bold, italic, underline)) }
                "w:tab" -> runs.add(Run("\t", false, false, false))
                "w:br" -> runs.add(Run("\n", false, false, false))
                "a:blip" -> p.getAttributeValue(null, "r:embed")?.let { id -> r[id]?.let { path -> zipImage(zip, path)?.let { pendingImgs += it } } }
            }
            XmlPullParser.END_TAG -> when (p.name) {
                "w:r" -> inRun = false
                "w:p" -> {
                    if (tableDepth > 0) { if (cell.isNotEmpty()) cell.append('\n'); cell.append(runs.joinToString("") { it.text }) }
                    else {
                        if (runs.isNotEmpty() || pendingImgs.isEmpty()) out += DocBlock.Para(runs, heading, bullet, center)
                        pendingImgs.forEach { out += DocBlock.Img(it) }
                    }
                    pendingImgs.clear()
                }
                "w:tc" -> if (tableDepth == 1) row.add(cell.toString())
                "w:tr" -> if (tableDepth == 1) rows.add(row)
                "w:tbl" -> { if (tableDepth == 1) out += DocBlock.Table(rows); tableDepth-- }
            }
        }
    }
    out
}

@Composable
private fun WordBody(file: File) {
    val blocks by produceState<Result<List<DocBlock>>?>(null, file) { value = withContext(Dispatchers.IO) { runCatching { parseDocx(file) } } }
    val b = blocks
    when {
        b == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color(0xFF2B579A)) }
        b.isFailure -> ErrorBox(b.exceptionOrNull()?.message)
        else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp)) {
            item { Spacer(Modifier.height(4.dp)) }
            items(b.getOrThrow()) { blk ->
                Box(Modifier.fillMaxWidth().background(DocC.page).padding(horizontal = 20.dp, vertical = 3.dp)) {
                    when (blk) {
                        is DocBlock.Para -> {
                            val s = when (blk.heading) { 1 -> 24f; 2 -> 20f; 3 -> 17f; 4 -> 16f; else -> 15f }
                            val txt = buildAnnotatedString {
                                if (blk.bullet) append("•  ")
                                blk.runs.forEach { r ->
                                    withStyle(SpanStyle(fontWeight = if (r.bold || blk.heading > 0) FontWeight.Bold else null,
                                        fontStyle = if (r.italic) FontStyle.Italic else null,
                                        textDecoration = if (r.underline) TextDecoration.Underline else null)) { append(r.text) }
                                }
                            }
                            Text(txt, color = if (blk.heading > 0) Color(0xFF2B579A) else DocC.text, fontSize = s.sp, lineHeight = (s * 1.4f).sp,
                                textAlign = if (blk.center) androidx.compose.ui.text.style.TextAlign.Center else null,
                                modifier = Modifier.fillMaxWidth().padding(top = if (blk.heading > 0) 10.dp else 2.dp, start = if (blk.bullet) 8.dp else 0.dp))
                        }
                        is DocBlock.Table -> TableView(blk.rows, Color(0xFF2B579A))
                        is DocBlock.Img -> Image(remember(blk.bmp) { blk.bmp.asImageBitmap() }, null, contentScale = ContentScale.FillWidth,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(6.dp)))
                    }
                }
            }
            item { Spacer(Modifier.height(30.dp).navigationBarsPadding()) }
        }
    }
}

@Composable
private fun TableView(rows: List<List<String>>, color: Color) {
    val cols = rows.maxOfOrNull { it.size } ?: 0
    Column(Modifier.padding(vertical = 8.dp).horizontalScroll(rememberScrollState()).border(1.dp, DocC.line, RoundedCornerShape(6.dp))) {
        rows.forEachIndexed { ri, r ->
            Row(Modifier.background(if (ri == 0) color.copy(alpha = 0.12f) else Color.Transparent)) {
                for (ci in 0 until cols) {
                    Text(r.getOrNull(ci).orEmpty(), color = DocC.text, fontSize = 13.sp, fontWeight = if (ri == 0) FontWeight.Bold else null,
                        modifier = Modifier.widthIn(min = 80.dp, max = 220.dp).border(0.5.dp, DocC.line).padding(6.dp))
                }
            }
        }
    }
}

@Composable
private fun ErrorBox(msg: String?) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text("No se pudo abrir: ${msg ?: "el archivo está dañado o protegido con contraseña"}", color = Color(0xFFD93A3A),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

// Excel

private class Sheet(val name: String, val cells: Map<Int, Map<Int, String>>, val maxRow: Int, val maxCol: Int)

private fun colIndex(ref: String): Int { var n = 0; for (ch in ref) { if (!ch.isLetter()) break; n = n * 26 + (ch.uppercaseChar() - 'A' + 1) }; return n - 1 }
private fun colName(i: Int): String { var n = i + 1; val sb = StringBuilder(); while (n > 0) { val r = (n - 1) % 26; sb.insert(0, ('A' + r)); n = (n - 1) / 26 }; return sb.toString() }

private fun parseXlsx(f: File): List<Sheet> = ZipFile(f).use { zip ->
    val shared = ArrayList<String>()
    zip.getEntry("xl/sharedStrings.xml")?.let { e ->
        val p = parser(zip.getInputStream(e)); var cur: StringBuilder? = null
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            if (p.eventType == XmlPullParser.START_TAG && p.name == "si") cur = StringBuilder()
            else if (p.eventType == XmlPullParser.START_TAG && p.name == "t") cur?.append(p.nextText())
            else if (p.eventType == XmlPullParser.END_TAG && p.name == "si") { shared += cur.toString(); cur = null }
        }
    }
    val r = rels(zip, "xl/_rels/workbook.xml.rels", "xl")
    val sheets = ArrayList<Pair<String, String>>()
    parser(zip.getInputStream(zip.getEntry("xl/workbook.xml") ?: throw Exception("No es un .xlsx válido"))).let { p ->
        while (p.next() != XmlPullParser.END_DOCUMENT) if (p.eventType == XmlPullParser.START_TAG && p.name == "sheet") {
            val name = p.getAttributeValue(null, "name") ?: "Hoja"; val id = p.getAttributeValue(null, "r:id")
            r[id]?.let { sheets += name to it }
        }
    }
    sheets.map { (name, path) ->
        val cells = HashMap<Int, HashMap<Int, String>>(); var maxR = 0; var maxC = 0
        zip.getEntry(path)?.let { e ->
            val p = parser(zip.getInputStream(e))
            var ref = ""; var type = ""; var value: String? = null; var count = 0
            while (p.next() != XmlPullParser.END_DOCUMENT && count < 60_000) {
                if (p.eventType == XmlPullParser.START_TAG) when (p.name) {
                    "c" -> { ref = p.getAttributeValue(null, "r") ?: ""; type = p.getAttributeValue(null, "t") ?: ""; value = null }
                    "v" -> value = p.nextText()
                    "t" -> value = (value ?: "") + p.nextText()
                } else if (p.eventType == XmlPullParser.END_TAG && p.name == "c") {
                    val v = value
                    if (v != null && ref.isNotEmpty()) {
                        val text = when (type) {
                            "s" -> shared.getOrNull(v.toIntOrNull() ?: -1).orEmpty()
                            "b" -> if (v == "1") "VERDADERO" else "FALSO"
                            else -> v.toDoubleOrNull()?.let { d -> if (d == Math.floor(d) && kotlin.math.abs(d) < 1e15) d.toLong().toString() else "%.4f".format(d).trimEnd('0').trimEnd('.') } ?: v
                        }
                        val rowN = ref.dropWhile { it.isLetter() }.toIntOrNull()?.minus(1) ?: 0
                        val colN = colIndex(ref)
                        if (rowN < 5000 && colN < 80) {
                            cells.getOrPut(rowN) { HashMap() }[colN] = text
                            maxR = maxOf(maxR, rowN); maxC = maxOf(maxC, colN); count++
                        }
                    }
                }
            }
        }
        Sheet(name, cells, maxR, maxC)
    }
}

@Composable
private fun ExcelBody(file: File) {
    val data by produceState<Result<List<Sheet>>?>(null, file) { value = withContext(Dispatchers.IO) { runCatching { parseXlsx(file) } } }
    var tab by remember { mutableIntStateOf(0) }
    val d = data
    when {
        d == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color(0xFF217346)) }
        d.isFailure -> ErrorBox(d.exceptionOrNull()?.message)
        else -> {
            val sheets = d.getOrThrow()
            val sh = sheets.getOrNull(tab)
            Column(Modifier.fillMaxSize()) {
                if (sh == null || sh.cells.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text("Hoja vacía", color = DocC.sub) }
                else {
                    val hs = rememberScrollState()
                    val colW = 110.dp
                    LazyColumn(Modifier.weight(1f).fillMaxWidth().background(DocC.page)) {
                        // encabezado de columnas
                        item {
                            Row(Modifier.horizontalScroll(hs).background(Color(0xFF217346).copy(alpha = 0.12f))) {
                                Box(Modifier.width(44.dp).border(0.5.dp, DocC.line).padding(4.dp))
                                for (c in 0..sh.maxCol) Text(colName(c), color = Color(0xFF217346), fontWeight = FontWeight.Bold, fontSize = 12.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.width(colW).border(0.5.dp, DocC.line).padding(4.dp))
                            }
                        }
                        items(sh.maxRow + 1) { r ->
                            val row = sh.cells[r]
                            Row(Modifier.horizontalScroll(hs)) {
                                Text("${r + 1}", color = DocC.sub, fontSize = 11.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.width(44.dp).background(Color(0xFF217346).copy(alpha = 0.06f)).border(0.5.dp, DocC.line).padding(5.dp))
                                for (c in 0..sh.maxCol) {
                                    val v = row?.get(c).orEmpty()
                                    Text(v, color = DocC.text, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                        textAlign = if (v.toDoubleOrNull() != null) androidx.compose.ui.text.style.TextAlign.End else null,
                                        modifier = Modifier.width(colW).border(0.5.dp, DocC.line).padding(5.dp))
                                }
                            }
                        }
                    }
                }
                // pestañas de hojas
                LazyRow(Modifier.fillMaxWidth().background(DocC.bg).navigationBarsPadding(), contentPadding = PaddingValues(8.dp)) {
                    itemsIndexed(sheets) { i, s ->
                        val on = i == tab
                        Text(s.name, color = if (on) Color.White else Color(0xFF217346), fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                            modifier = Modifier.padding(end = 6.dp).clip(RoundedCornerShape(10.dp))
                                .background(if (on) Color(0xFF217346) else Color(0xFF217346).copy(alpha = 0.1f))
                                .clickable { tab = i }.padding(horizontal = 14.dp, vertical = 7.dp))
                    }
                }
            }
        }
    }
}

// PowerPoint

private class Slide(val title: String?, val paras: List<Pair<Int, String>>, val images: List<Bitmap>)

private fun parsePptx(f: File): List<Slide> = ZipFile(f).use { zip ->
    val r = rels(zip, "ppt/_rels/presentation.xml.rels", "ppt")
    val order = ArrayList<String>()
    parser(zip.getInputStream(zip.getEntry("ppt/presentation.xml") ?: throw Exception("No es un .pptx válido"))).let { p ->
        while (p.next() != XmlPullParser.END_DOCUMENT) if (p.eventType == XmlPullParser.START_TAG && p.name == "p:sldId")
            r[p.getAttributeValue(null, "r:id")]?.let { order += it }
    }
    order.take(200).map { path ->
        val dir = path.substringBeforeLast('/')
        val sr = rels(zip, "$dir/_rels/${path.substringAfterLast('/')}.rels", dir)
        var title: String? = null
        val paras = ArrayList<Pair<Int, String>>()
        val imgs = ArrayList<Bitmap>()
        zip.getEntry(path)?.let { e ->
            val p = parser(zip.getInputStream(e))
            var isTitle = false; var cur = StringBuilder(); var level = 0
            while (p.next() != XmlPullParser.END_DOCUMENT) {
                if (p.eventType == XmlPullParser.START_TAG) when (p.name) {
                    "p:sp" -> isTitle = false
                    "p:ph" -> { val t = p.getAttributeValue(null, "type"); if (t == "title" || t == "ctrTitle") isTitle = true }
                    "a:p" -> { cur = StringBuilder(); level = 0 }
                    "a:pPr" -> level = p.getAttributeValue(null, "lvl")?.toIntOrNull() ?: 0
                    "a:t" -> cur.append(p.nextText())
                    "a:blip" -> if (imgs.size < 3) p.getAttributeValue(null, "r:embed")?.let { id -> sr[id]?.let { zipImage(zip, it, 1000)?.let { b -> imgs += b } } }
                } else if (p.eventType == XmlPullParser.END_TAG && p.name == "a:p") {
                    val t = cur.toString().trim()
                    if (t.isNotEmpty()) { if (isTitle && title == null) title = t else if (isTitle) title += " " + t else paras += level to t }
                }
            }
        }
        Slide(title, paras, imgs)
    }
}

@Composable
private fun PowerPointBody(file: File) {
    val data by produceState<Result<List<Slide>>?>(null, file) { value = withContext(Dispatchers.IO) { runCatching { parsePptx(file) } } }
    val d = data
    when {
        d == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color(0xFFD24726)) }
        d.isFailure -> ErrorBox(d.exceptionOrNull()?.message)
        else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(14.dp)) {
            itemsIndexed(d.getOrThrow()) { i, s ->
                Column(Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
                    Text("Diapositiva ${i + 1}", color = DocC.sub, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
                    Column(Modifier.fillMaxWidth().heightIn(min = 190.dp).clip(RoundedCornerShape(12.dp)).background(DocC.page)
                        .border(1.dp, DocC.line, RoundedCornerShape(12.dp)).padding(18.dp)) {
                        s.title?.let { Text(it, color = Color(0xFFD24726), fontSize = 21.sp, fontWeight = FontWeight.Bold, lineHeight = 26.sp) }
                        s.paras.forEach { (lvl, t) ->
                            Text("•  $t", color = DocC.text, fontSize = (15 - lvl).coerceAtLeast(12).sp, lineHeight = 21.sp,
                                modifier = Modifier.padding(start = (lvl * 16).dp, top = 6.dp))
                        }
                        s.images.forEach { b ->
                            Image(remember(b) { b.asImageBitmap() }, null, contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp).padding(top = 10.dp).clip(RoundedCornerShape(8.dp)))
                        }
                        if (s.title == null && s.paras.isEmpty() && s.images.isEmpty()) Text("(sin texto)", color = DocC.sub)
                    }
                }
            }
            item { Spacer(Modifier.height(20.dp).navigationBarsPadding()) }
        }
    }
}

@Suppress("unused") private val keepLang = Icons.Outlined.Language
