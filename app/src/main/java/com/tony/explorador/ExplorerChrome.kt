package com.tony.explorador

import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material.icons.outlined.AddToHomeScreen
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.OpenInFull
import androidx.media3.common.Player
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.Code
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/** Colores del explorador: blanco con morado (como el tema Lavanda); en modo oscuro, morado oscuro con lila. */
object ExpTheme {
    private val d get() = XpColors.dark
    val bar get() = if (d) Color(0xFF1C1828) else Color.White
    val onBar get() = if (d) Color(0xFFCDB8FF) else Color(0xFF6A3FD0)
    val text get() = if (d) Color(0xFFEDE8FA) else Color(0xFF221B33)
    val sub get() = if (d) Color(0xFFA79CC4) else Color(0xFF7D7394)
    val crumbBg get() = if (d) Color(0xFF2A2440) else Color(0xFFF1ECFB)
    val paneBg get() = if (d) Color(0xFF171320) else Color(0xFFFAF8FE)
    val line get() = if (d) Color(0xFF332C4A) else Color(0xFFE6DEF7)
}

/** Carpeta "semi relieve": pestaña de atrás, frente con luz arriba y sombra sólida abajo (plana, sin difuminar). */
@Composable
fun FolderGlyph(size: Dp, modifier: Modifier = Modifier, color: Color = Color(0xFFFFB627)) {
    Canvas(modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        val top = h * 0.16f; val bottom = h * 0.86f
        val r = w * 0.09f
        val back = lerp(color, Color(0xFF8A4B00), 0.25f)
        // sombra sólida
        drawRoundRect(lerp(color, Color(0xFF7A3E00), 0.45f).copy(alpha = 0.55f), Offset(w * 0.06f, top + h * 0.1f), Size(w * 0.88f, bottom - top),
            CornerRadius(r))
        // parte de atrás con pestaña
        val tab = Path().apply {
            moveTo(w * 0.06f + r, top)
            lineTo(w * 0.40f, top)
            lineTo(w * 0.48f, top + h * 0.09f)
            lineTo(w * 0.94f - r, top + h * 0.09f)
            quadraticBezierTo(w * 0.94f, top + h * 0.09f, w * 0.94f, top + h * 0.09f + r)
            lineTo(w * 0.94f, bottom - r)
            lineTo(w * 0.06f, bottom - r)
            lineTo(w * 0.06f, top + r)
            quadraticBezierTo(w * 0.06f, top, w * 0.06f + r, top)
            close()
        }
        drawPath(tab, back)
        // hoja de papel asomando
        drawRoundRect(Color.White.copy(alpha = 0.95f), Offset(w * 0.14f, top + h * 0.14f), Size(w * 0.72f, h * 0.3f), CornerRadius(r * 0.6f))
        // frente
        val fTop = top + h * 0.24f
        drawRoundRect(Brush.verticalGradient(listOf(lerp(color, Color.White, 0.28f), color), startY = fTop, endY = bottom),
            Offset(w * 0.06f, fTop), Size(w * 0.88f, bottom - fTop), CornerRadius(r))
        // filo de luz
        drawRoundRect(Color.White.copy(alpha = 0.55f), Offset(w * 0.12f, fTop + h * 0.025f), Size(w * 0.76f, h * 0.03f), CornerRadius(h * 0.015f))
    }
}

private fun typeStyle(file: File, kind: FileKind): Pair<ImageVector, Color> {
    val ext = file.extension.lowercase(Locale.ROOT)
    return when {
        kind == FileKind.IMAGE -> Icons.Filled.Image to Color(0xFF2E9BEA)
        kind == FileKind.AUDIO -> Icons.Filled.AudioFile to Color(0xFFB04BD8)
        kind == FileKind.VIDEO -> Icons.Filled.VideoFile to Color(0xFFE5533D)
        ext == "pdf" -> Icons.Filled.PictureAsPdf to Color(0xFFE0453A)
        ext == "apk" -> Icons.Filled.Android to Color(0xFF34A853)
        ext in setOf("xls", "xlsx", "csv", "ods") -> Icons.Filled.TableChart to Color(0xFF1E8E4E)
        ext in setOf("ppt", "pptx", "odp") -> Icons.Filled.Slideshow to Color(0xFFE8710A)
        ext in setOf("doc", "docx", "txt", "md", "rtf", "odt", "log") -> Icons.Filled.Description to Color(0xFF3B6FE0)
        ext in setOf("html", "htm", "json", "xml", "js", "kt", "java", "py", "css", "sh") -> Icons.Filled.Code to Color(0xFF5C6BC0)
        Archives.isArchive(file) -> Icons.Filled.Archive to Color(0xFF8E5BE8)
        else -> Icons.AutoMirrored.Filled.InsertDriveFile to Color(0xFF8C93A8)
    }
}

/** Ícono amigable de cualquier archivo: carpeta con relieve o cuadrito suave del color del tipo. */
@Composable
fun FriendlyIcon(file: File, kind: FileKind, size: Dp, modifier: Modifier = Modifier) {
    if (kind == FileKind.FOLDER) { FolderGlyph(size, modifier); return }
    val (icon, color) = typeStyle(file, kind)
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        SoftIcon(icon, color, size * 0.86f)
        val ext = file.extension.uppercase(Locale.ROOT)
        if (ext.isNotEmpty() && ext.length <= 4 && size >= 40.dp) Text(ext, color = Color.White, fontSize = (size.value * 0.16f).sp,
            fontWeight = FontWeight.Black, modifier = Modifier.align(Alignment.BottomCenter).offset(y = (-1).dp)
                .clip(RoundedCornerShape(4.dp)).background(color).padding(horizontal = 4.dp))
    }
}

/** Cinta con la ruta: casita + carpetas en pastillas, la actual resaltada. */
@Composable
fun ExplorerCrumbs(dir: File, roots: List<StorageRoot>, onNavigate: (File) -> Unit) {
    val root = roots.firstOrNull { dir.absolutePath.startsWith(it.file.absolutePath) }
    val crumbs = remember(dir) {
        val l = mutableListOf<Pair<String, File>>()
        var f: File? = dir
        while (f != null) {
            val isRoot = root != null && f.absolutePath == root.file.absolutePath
            l.add(0, (if (isRoot) root!!.name else f.name) to f)
            if (isRoot) break
            f = f.parentFile
        }
        l
    }
    val scroll = rememberScrollState()
    LaunchedEffect(dir) { scroll.scrollTo(scroll.maxValue) }
    Column(Modifier.fillMaxWidth().background(ExpTheme.bar)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(scroll).padding(horizontal = 12.dp).padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            crumbs.forEachIndexed { i, (name, file) ->
                val last = i == crumbs.lastIndex
                if (i > 0) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = ExpTheme.sub, modifier = Modifier.size(15.dp))
                Row(Modifier.clip(CircleShape).background(if (last) ExpTheme.onBar else ExpTheme.crumbBg)
                    .clickable { onNavigate(file) }.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    if (i == 0) {
                        Icon(Icons.Outlined.Home, null, tint = if (last) Color.White else ExpTheme.onBar, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(name, fontSize = 12.sp, fontWeight = if (last) FontWeight.Bold else FontWeight.Medium,
                        color = if (last) Color.White else ExpTheme.onBar, maxLines = 1)
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(ExpTheme.line))
    }
}

// ───────── Panel de vista previa / detalles (como Windows) ─────────

private val TEXT_PREVIEW = setOf("txt", "md", "log", "json", "xml", "csv", "ini", "cfg", "conf", "yml", "yaml", "html", "htm", "css",
    "js", "kt", "java", "py", "c", "cpp", "h", "sh", "bat", "srt", "lrc", "properties", "sql", "nfo", "m3u", "m3u8")

/** La barrita para cambiar el tamaño del panel. */
@Composable
fun PaneHandle(vertical: Boolean, onDrag: (Float) -> Unit) {
    Box(
        (if (vertical) Modifier.fillMaxWidth().height(16.dp) else Modifier.fillMaxHeight().width(16.dp))
            .background(ExpTheme.paneBg)
            .pointerInput(vertical) { detectDragGestures { c, d -> c.consume(); onDrag(if (vertical) d.y else d.x) } },
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.clip(CircleShape).background(ExpTheme.sub.copy(alpha = 0.5f))
            .then(if (vertical) Modifier.width(44.dp).height(4.dp) else Modifier.width(4.dp).height(44.dp)))
    }
}

@Composable
fun PreviewPane(
    file: File?, dir: File?, mode: String, onMode: (String) -> Unit, onOpen: (File) -> Unit, onClose: () -> Unit,
    modifier: Modifier = Modifier, controller: Player? = null, onExpandVideo: () -> Unit = {},
) {
    val target = file ?: dir
    // al cambiar de archivo o cerrar el panel se pausa el video del panel (cambiar de pestaña no lo pausa)
    androidx.compose.runtime.DisposableEffect(file) {
        onDispose {
            val pl = VideoSession.player
            if (pl != null && file != null && VideoSession.files.size == 1 && VideoSession.files.first() == file && !VideoSession.floating) pl.pause()
        }
    }
    Column(modifier.background(ExpTheme.paneBg)) {
        // pestañas
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf("preview" to "Vista previa", "details" to "Detalles").forEach { (id, label) ->
                val on = mode == id
                Text(label, fontSize = 13.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                    color = if (on) Color.White else ExpTheme.onBar,
                    modifier = Modifier.padding(end = 6.dp).clip(CircleShape).background(if (on) ExpTheme.onBar else ExpTheme.crumbBg)
                        .clickable { onMode(id) }.padding(horizontal = 14.dp, vertical = 6.dp))
            }
            Spacer(Modifier.weight(1f))
            if (file != null && !file.isDirectory) Text("Abrir", color = ExpTheme.onBar, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                modifier = Modifier.clip(CircleShape).clickable { onOpen(file) }.padding(horizontal = 12.dp, vertical = 6.dp))
            Box(Modifier.size(32.dp).clip(CircleShape).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Close, "Cerrar panel", tint = ExpTheme.sub, modifier = Modifier.size(18.dp))
            }
        }
        if (target == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Toca un archivo para verlo aquí.\nTócalo otra vez para abrirlo.", color = ExpTheme.sub, fontSize = 13.sp)
            }
        } else if (mode == "details") Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) { DetailsBody(target, isCurrentDir = file == null) }
            if (file != null) NowPlayingStrip(file, controller)
        }
        else PreviewBody(target, onOpen, controller, onExpandVideo)
    }
}

@Composable
private fun PreviewBody(f: File, onOpen: (File) -> Unit, controller: Player?, onExpandVideo: () -> Unit) {
    val kind = remember(f) { f.kind() }
    val ext = f.extension.lowercase(Locale.ROOT)
    Box(Modifier.fillMaxSize().padding(10.dp), contentAlignment = Alignment.Center) {
        when {
            kind == FileKind.IMAGE -> AsyncImage(f, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)))
            kind == FileKind.VIDEO -> PaneVideo(f, controller, onExpandVideo)
            kind == FileKind.AUDIO -> {
                val info by rememberTrackInfo(f.absolutePath)
                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.fillMaxHeight().aspectRatio(1f, matchHeightConstraintsFirst = true).clip(RoundedCornerShape(12.dp))
                            .background(ExpTheme.crumbBg), contentAlignment = Alignment.Center) {
                            val c = info?.cover
                            if (c != null) Image(remember(c) { c.asImageBitmap() }, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            else FriendlyIcon(f, kind, 48.dp)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(info?.title ?: f.nameWithoutExtension.replace('_', ' '), color = ExpTheme.text, fontWeight = FontWeight.Bold,
                                fontSize = 15.sp, lineHeight = 19.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            info?.artist?.let { Text(it, color = ExpTheme.sub, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            info?.album?.let { Text(it, color = ExpTheme.sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        }
                    }
                    PaneAudioControls(f, controller) { onOpen(f) }
                }
            }
            ext == "pdf" -> {
                val page by produceState<android.graphics.Bitmap?>(null, f) { value = loadPdfThumb(f) }
                val p = page
                if (p != null) Image(remember(p) { p.asImageBitmap() }, null, contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)).clickable { onOpen(f) })
                else CircularProgressIndicator(color = ExpTheme.onBar)
            }
            ext in TEXT_PREVIEW -> {
                val txt by produceState("", f) {
                    value = withContext(Dispatchers.IO) {
                        runCatching { f.inputStream().use { String(it.readNBytes(40_000), Charsets.UTF_8) } }.getOrDefault("(no se pudo leer)")
                    }
                }
                Text(txt, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = ExpTheme.text,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)).background(ExpTheme.bar)
                        .border(1.dp, ExpTheme.line, RoundedCornerShape(10.dp)).verticalScroll(rememberScrollState()).padding(10.dp))
            }
            kind == FileKind.FOLDER || (Archives.isArchive(f) && f.isFile) -> {
                // lo que hay adentro (de una carpeta o de un comprimido, sin extraer)
                val items by produceState<List<Pair<String, Boolean>>?>(null, f) {
                    value = withContext(Dispatchers.IO) {
                        runCatching {
                            if (f.isDirectory) f.listFiles()?.filter { !it.name.startsWith(".") }
                                ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase(Locale.ROOT) }))?.take(60)?.map { it.name to it.isDirectory }.orEmpty()
                            else Archives.list(f, null).map { it.path.substringBefore('/') to (it.isDir || it.path.contains('/')) }.distinct()
                                .sortedWith(compareBy({ !it.second }, { it.first.lowercase(Locale.ROOT) })).take(60)
                        }.getOrElse { emptyList() }
                    }
                }
                val l = items
                if (l == null) CircularProgressIndicator(color = ExpTheme.onBar)
                else if (l.isEmpty()) Text(if (f.isDirectory) "Carpeta vacía" else "No se pudo ver (¿tiene contraseña?)", color = ExpTheme.sub)
                else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    l.forEach { (name, isDir) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            FriendlyIcon(File(name), if (isDir) FileKind.FOLDER else File(name).let { if (it.extension.isEmpty()) FileKind.OTHER else kindByExt(it) }, 26.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(name, color = ExpTheme.text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FriendlyIcon(f, kind, 72.dp)
                Text("No hay vista previa para este tipo de archivo", color = ExpTheme.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

/** Tipo por la extensión, sin tocar el disco (para nombres dentro de comprimidos). */
private fun kindByExt(f: File): FileKind {
    val tmp = object : java.io.File(f.path) { override fun isDirectory() = false }
    return tmp.kind()
}

@Composable
private fun DetailsBody(f: File, isCurrentDir: Boolean) {
    val kind = remember(f) { f.kind() }
    val rows by produceState<List<Pair<String, String>>?>(null, f) {
        value = withContext(Dispatchers.IO) {
            val l = ArrayList<Pair<String, String>>()
            l += "Nombre" to f.name
            l += "Tipo" to when (kind) {
                FileKind.FOLDER -> "Carpeta"; FileKind.IMAGE -> "Imagen"; FileKind.AUDIO -> "Audio"; FileKind.VIDEO -> "Video"
                else -> if (Archives.isArchive(f)) "Comprimido" else f.extension.uppercase(Locale.ROOT).ifBlank { "Archivo" }
            } + (if (f.isFile && f.extension.isNotEmpty()) " (.${f.extension.lowercase(Locale.ROOT)})" else "")
            if (f.isDirectory) {
                var n = 0; var dirs = 0; var bytes = 0L
                f.walkTopDown().maxDepth(30).forEach { if (it != f) { if (it.isDirectory) dirs++ else { n++; bytes += it.length() } } ; if (n + dirs > 50_000) return@forEach }
                l += "Contiene" to "$n archivos, $dirs carpetas"
                l += "Tamaño" to formatSize(bytes)
            } else l += "Tamaño" to "${formatSize(f.length())} (${"%,d".format(f.length())} bytes)"
            l += "Modificado" to formatDate(f.lastModified())
            when (kind) {
                FileKind.IMAGE -> runCatching {
                    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(f.absolutePath, o)
                    if (o.outWidth > 0) l += "Dimensiones" to "${o.outWidth} × ${o.outHeight} px (${"%.1f".format(o.outWidth * o.outHeight / 1_000_000f)} MP)"
                    val ex = androidx.exifinterface.media.ExifInterface(f.absolutePath)
                    ex.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_DATETIME_ORIGINAL)?.let { l += "Tomada" to it }
                    listOfNotNull(ex.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_MAKE), ex.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_MODEL))
                        .joinToString(" ").takeIf { it.isNotBlank() }?.let { l += "Cámara" to it }
                }
                FileKind.AUDIO, FileKind.VIDEO -> runCatching {
                    val r = MediaMetadataRetriever()
                    try {
                        r.setDataSource(f.absolutePath)
                        r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.let { ms ->
                            l += "Duración" to "%d:%02d".format(ms / 60000, (ms / 1000) % 60)
                        }
                        if (kind == FileKind.VIDEO) {
                            val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                            val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                            if (w != null && h != null) l += "Resolución" to "$w × $h"
                        } else {
                            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.let { l += "Título" to it }
                            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.let { l += "Artista" to it }
                            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.let { l += "Álbum" to it }
                            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)?.let { l += "Año" to it }
                        }
                        r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull()?.let { l += "Calidad" to "${it / 1000} kbps" }
                    } finally { r.release() }
                }
                else -> if (Archives.isArchive(f) && f.isFile) runCatching {
                    val e = Archives.list(f, null)
                    val files = e.filter { !it.isDir }
                    l += "Adentro" to "${files.size} archivos"
                    l += "Sin comprimir" to formatSize(files.sumOf { it.size })
                }.onFailure { l += "Adentro" to "Protegido con contraseña" }
            }
            l += "Ubicación" to (f.parent ?: "/")
            if (isCurrentDir) l.add(0, "Viendo" to "Esta carpeta")
            l
        }
    }
    val r = rows
    if (r == null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = ExpTheme.onBar) }
    else Row(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 6.dp)) {
        FriendlyIcon(f, kind, 54.dp, Modifier.padding(end = 12.dp, top = 4.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            r.forEach { (k, v) ->
                Row {
                    Text(k, color = ExpTheme.sub, fontSize = 12.sp, modifier = Modifier.width(92.dp))
                    Text(v, color = ExpTheme.text, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}


private fun fmtTime(ms: Long): String { val t = (ms / 1000).coerceAtLeast(0); return "%d:%02d".format(t / 60, t % 60) }

/** Controles de música dentro del panel (usa el mismo reproductor de la app, sin el cuadrito flotante). */
@Composable
private fun PaneAudioControls(f: File, controller: Player?, onStart: () -> Unit) {
    val tick = observePlayer(controller, 300)
    val p = controller
    val isThis = p != null && p.currentMediaItem?.mediaId == f.absolutePath
    if (!isThis) {
        Row(Modifier.padding(top = 10.dp).clip(CircleShape).background(ExpTheme.onBar).clickable(onClick = onStart)
            .padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(18.dp))
            Text(" Reproducir", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        return
    }
    p!!
    val pos = p.currentPosition + tick * 0L
    val dur = p.duration.coerceAtLeast(1L)
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(ExpTheme.onBar).clickable { if (p.isPlaying) p.pause() else p.play() },
            contentAlignment = Alignment.Center) {
            Icon(if (p.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Box(Modifier.size(32.dp).clip(CircleShape).clickable(enabled = p.hasNextMediaItem()) { p.seekToNextMediaItem() },
            contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.SkipNext, "Siguiente", tint = ExpTheme.onBar, modifier = Modifier.size(20.dp))
        }
        Text(fmtTime(pos), color = ExpTheme.sub, fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp))
        androidx.compose.material3.Slider(
            value = (pos.toFloat() / dur).coerceIn(0f, 1f), onValueChange = { p.seekTo((it * dur).toLong()) },
            colors = androidx.compose.material3.SliderDefaults.colors(thumbColor = ExpTheme.onBar, activeTrackColor = ExpTheme.onBar,
                inactiveTrackColor = ExpTheme.line),
            modifier = Modifier.weight(1f).height(30.dp).padding(horizontal = 6.dp)
        )
        Text(fmtTime(p.duration), color = ExpTheme.sub, fontSize = 11.sp)
    }
}

/** Video que se reproduce ahí mismo en el panel; el botón de agrandar lo pasa al reproductor normal sin cortarlo. */
@Composable
private fun PaneVideo(f: File, controller: Player?, onExpand: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val vp = VideoSession.player
    val active = vp != null && VideoSession.files.size == 1 && VideoSession.files.first() == f
    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)).background(Color.Black), contentAlignment = Alignment.Center) {
        if (!active) {
            AsyncImage(f, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            Box(Modifier.size(56.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f)).clickable {
                controller?.pause()
                VideoSession.obtain(context, listOf(f), 0, resume = false).play()
            }, contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.PlayArrow, "Reproducir aquí", tint = Color.White, modifier = Modifier.size(34.dp))
            }
        } else {
            val tick = observePlayer(vp, 300)
            VideoView(vp!!, Modifier.fillMaxSize().clickable { if (vp.isPlaying) vp.pause() else { controller?.pause(); vp.play() } }, crop = false)
            // controles chiquitos abajo
            val pos = vp.currentPosition + tick * 0L
            val dur = vp.duration.coerceAtLeast(1L)
            Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))))
                .padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (vp.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, tint = Color.White,
                    modifier = Modifier.size(28.dp).clip(CircleShape).clickable { if (vp.isPlaying) vp.pause() else { controller?.pause(); vp.play() } })
                androidx.compose.material3.Slider(
                    value = (pos.toFloat() / dur).coerceIn(0f, 1f), onValueChange = { vp.seekTo((it * dur).toLong()) },
                    colors = androidx.compose.material3.SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.3f)),
                    modifier = Modifier.weight(1f).height(24.dp).padding(horizontal = 6.dp)
                )
                Text(fmtTime(pos), color = Color.White, fontSize = 11.sp)
            }
            // agrandar: abre el reproductor normal donde va
            Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(34.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f))
                .clickable(onClick = onExpand), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.OpenInFull, "Agrandar", tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** Menú ⋮ de un archivo: hoja que sube desde abajo, con el ícono, nombre y opciones con íconos. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun FileMenuSheet(f: File, options: List<Pair<String, () -> Unit>>, onDismiss: () -> Unit) {
    val kind = remember(f) { f.kind() }
    val info by produceState("", f) {
        value = withContext(Dispatchers.IO) {
            (if (f.isDirectory) "${f.listFiles()?.size ?: 0} elementos" else formatSize(f.length())) + " · " + formatDate(f.lastModified())
        }
    }
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss, containerColor = ExpTheme.bar,
        dragHandle = { Box(Modifier.padding(top = 10.dp, bottom = 4.dp).width(40.dp).height(4.dp).clip(CircleShape).background(ExpTheme.line)) }) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            FriendlyIcon(f, kind, 46.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(f.name, color = ExpTheme.text, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(info, color = ExpTheme.sub, fontSize = 12.sp)
            }
        }
        Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp).height(1.dp).background(ExpTheme.line))
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 18.dp)) {
            options.forEach { (label, action) ->
                val danger = label.startsWith("Eliminar")
                val icon = when {
                    label == "Abrir" -> Icons.AutoMirrored.Outlined.OpenInNew
                    label.startsWith("Copiar") -> Icons.Outlined.ContentCopy
                    label.startsWith("Mover") -> Icons.Outlined.DriveFileMove
                    label == "Renombrar" -> Icons.Outlined.Edit
                    label == "Compartir" -> Icons.Outlined.Share
                    label.startsWith("Comprimir") -> Icons.Outlined.Archive
                    label.startsWith("Ver contenido") -> Icons.Outlined.Unarchive
                    label.startsWith("Acceso directo") -> Icons.Outlined.AddToHomeScreen
                    label == "Seleccionar" -> Icons.Outlined.CheckCircle
                    danger -> Icons.Outlined.Delete
                    else -> Icons.Outlined.MoreHoriz
                }
                val tint = if (danger) Color(0xFFD93A3A) else ExpTheme.onBar
                Row(Modifier.fillMaxWidth().clickable { onDismiss(); action() }.padding(horizontal = 24.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, tint = tint, modifier = Modifier.size(21.dp))
                    Spacer(Modifier.width(18.dp))
                    Text(label, color = if (danger) tint else ExpTheme.text, fontSize = 15.sp)
                }
            }
        }
    }
}


/** En la pestaña Detalles: lo que está sonando sigue con su botón de pausa. */
@Composable
private fun NowPlayingStrip(f: File, controller: Player?) {
    val vp = VideoSession.player
    val videoHere = vp != null && VideoSession.files.size == 1 && VideoSession.files.first() == f
    val tick = observePlayer(if (videoHere) vp else controller, 400)
    val p: Player = (if (videoHere) vp else controller?.takeIf { it.currentMediaItem?.mediaId == f.absolutePath }) ?: return
    @Suppress("UNUSED_EXPRESSION") tick
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).clip(CircleShape).background(ExpTheme.crumbBg)
        .padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(ExpTheme.onBar).clickable { if (p.isPlaying) p.pause() else p.play() },
            contentAlignment = Alignment.Center) {
            Icon(if (p.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
        Text("  ${if (p.isPlaying) "Reproduciendo" else "En pausa"} · ${fmtTime(p.currentPosition)} / ${fmtTime(p.duration)}",
            color = ExpTheme.onBar, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}
