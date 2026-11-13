package com.tony.explorador

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

private val TEXT_EXT = setOf("txt", "md", "log", "json", "xml", "csv", "ini", "cfg", "conf", "yml", "yaml", "html", "htm", "css", "js",
    "kt", "java", "py", "c", "cpp", "h", "sh", "bat", "srt", "lrc", "properties", "gradle", "sql", "nfo")

private fun iconFor(name: String, isDir: Boolean): Pair<ImageVector, Color> {
    if (isDir) return Icons.Filled.Folder to Color(0xFFF2B33D)
    val f = File(name)
    val ext = f.extension.lowercase(Locale.ROOT)
    return when {
        Archives.isArchive(f) -> Icons.Filled.Archive to Color(0xFF8E7CC3)
        ext == "pdf" -> Icons.Filled.PictureAsPdf to Color(0xFFE5533D)
        ext in TEXT_EXT -> Icons.Filled.Description to Color(0xFF6E8CA8)
        else -> when (f.kind()) {
            FileKind.IMAGE -> Icons.Filled.Image to Color(0xFF3FA9F5)
            FileKind.AUDIO -> Icons.Filled.AudioFile to Color(0xFFB45CD6)
            FileKind.VIDEO -> Icons.Filled.VideoFile to Color(0xFFE57373)
            else -> Icons.AutoMirrored.Filled.InsertDriveFile to Color(0xFF90A4AE)
        }
    }
}

/** Una fila de la vista: carpeta (armada a partir de las rutas) o archivo. */
private data class Node(val name: String, val path: String, val isDir: Boolean, val size: Long, val time: Long,
                        val encrypted: Boolean, val count: Int, val entry: Archives.Entry?)

private fun childrenOf(entries: List<Archives.Entry>, prefix: String): List<Node> {
    val dirs = LinkedHashMap<String, MutableList<Archives.Entry>>()
    val files = ArrayList<Node>()
    for (e in entries) {
        if (!e.path.startsWith(prefix)) continue
        val rest = e.path.removePrefix(prefix)
        if (rest.isEmpty()) continue
        val slash = rest.indexOf('/')
        if (slash >= 0) dirs.getOrPut(rest.substring(0, slash)) { ArrayList() }.add(e)
        else if (e.isDir) dirs.getOrPut(rest) { ArrayList() }
        else files += Node(rest, e.path, false, e.size, e.time, e.encrypted, 0, e)
    }
    val dn = dirs.map { (name, list) ->
        val inside = list.filter { !it.isDir }
        Node(name, prefix + name, true, inside.sumOf { it.size }, inside.maxOfOrNull { it.time } ?: 0L,
            inside.any { it.encrypted }, inside.size, null)
    }
    return dn.sortedBy { it.name.lowercase(Locale.ROOT) } + files.sortedBy { it.name.lowercase(Locale.ROOT) }
}

/** Ventana de progreso con botón de cancelar. */
@Composable
fun ArchiveProgress(title: String, progress: Float, onCancel: () -> Unit) {
    Dialog(onDismissRequest = {}, properties = DialogProperties(dismissOnClickOutside = false, dismissOnBackPress = false)) {
        Surface(shape = RoundedCornerShape(20.dp), tonalElevation = 6.dp) {
            Column(Modifier.padding(20.dp).width(280.dp)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Spacer(Modifier.height(14.dp))
                if (progress < 0f) LinearProgressIndicator(Modifier.fillMaxWidth())
                else LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                Text(if (progress < 0f) "Trabajando…" else "${(progress * 100).toInt()} %", fontSize = 13.sp,
                    modifier = Modifier.padding(top = 6.dp))
                Text("Cancelar", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.End).clip(CircleShape).clickable(onClick = onCancel).padding(10.dp))
            }
        }
    }
}

@Composable
private fun PasswordDialog(wrong: Boolean, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var pw by remember { mutableStateOf("") }
    ThemedDialog(if (wrong) "Contraseña incorrecta" else "Este archivo tiene contraseña", onDismiss, listOf(
        DialogButton("Cancelar") { onDismiss() },
        DialogButton("Abrir", true) { if (pw.isNotEmpty()) onOk(pw) })) {
        Column {
            Text(if (wrong) "Inténtalo otra vez." else "Escribe la contraseña del comprimido.", fontSize = 13.sp)
            OutlinedTextField(pw, { pw = it }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        }
    }
}

/**
 * Visor de comprimidos: navega por dentro como en el explorador, abre cualquier archivo
 * sin extraer todo (solo saca ese a un temporal) y extrae todo o lo que elijas.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArchiveViewer(file: File, onClose: () -> Unit, onOpen: (File) -> Unit, onExtracted: (File) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf<String?>(null) }
    var askPw by remember { mutableStateOf(false) }
    var wrongPw by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    var entries by remember { mutableStateOf<List<Archives.Entry>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var prefix by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var busy by remember { mutableStateOf<String?>(null) }
    var prog by remember { mutableFloatStateOf(-1f) }
    var cancel by remember { mutableStateOf(false) }
    var pickDest by remember { mutableStateOf(false) }
    var textPreview by remember { mutableStateOf<Pair<String, String>?>(null) }
    var menu by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(file, reload) {
        error = null
        try {
            entries = withContext(Dispatchers.IO) { Archives.list(file, password) }
        } catch (e: NeedPasswordException) { askPw = true; pending = { reload++ }
        } catch (e: WrongPasswordException) { wrongPw = true; askPw = true; pending = { reload++ }
        } catch (e: Throwable) { error = e.message ?: "No se pudo abrir" }
    }

    val all = entries.orEmpty()
    val nodes = remember(all, prefix) { childrenOf(all, prefix) }
    val totalSize = remember(all) { all.filter { !it.isDir }.sumOf { it.size } }
    val totalPacked = remember(all) { all.filter { !it.isDir }.sumOf { it.packed } }
    val nFiles = remember(all) { all.count { !it.isDir } }

    fun entriesFor(paths: Set<String>): List<Archives.Entry> =
        all.filter { e -> paths.any { p -> e.path == p || e.path.startsWith("$p/") } }

    /** Corre algo que puede pedir contraseña: si la pide, pregunta y lo vuelve a intentar. */
    fun withPassword(title: String, work: suspend () -> Unit) {
        scope.launch {
            busy = title; prog = -1f; cancel = false
            try { work() }
            catch (e: NeedPasswordException) { pending = { withPassword(title, work) }; askPw = true }
            catch (e: WrongPasswordException) { pending = { withPassword(title, work) }; wrongPw = true; askPw = true }
            catch (e: CancelledException) { Toast.makeText(context, "Cancelado", Toast.LENGTH_SHORT).show() }
            catch (e: Throwable) { Toast.makeText(context, e.message ?: "Algo falló", Toast.LENGTH_LONG).show() }
            finally { busy = null }
        }
    }

    fun preview(n: Node) {
        val e = n.entry ?: return
        withPassword("Abriendo ${n.name}…") {
            val dir = File(context.cacheDir, "vista_previa/${file.nameWithoutExtension.hashCode()}")
            val out = withContext(Dispatchers.IO) {
                File(dir, n.name).takeIf { it.isFile && it.length() == e.size && e.size > 0 }
                    ?: Archives.extract(file, listOf(e), dir, password, flat = true,
                        progress = { d, t -> if (t > 0) prog = d.toFloat() / t }, cancelled = { cancel }).firstOrNull()
            } ?: return@withPassword
            val ext = out.extension.lowercase(Locale.ROOT)
            if (ext in TEXT_EXT) {
                val txt = withContext(Dispatchers.IO) {
                    val bytes = out.inputStream().use { it.readNBytes(300_000) }
                    String(bytes, Charsets.UTF_8) + if (out.length() > 300_000) "\n\n… (solo se muestran los primeros 300 KB)" else ""
                }
                textPreview = n.name to txt
            } else onOpen(out)
        }
    }

    fun extractTo(dest: File, paths: Set<String>?) {
        withPassword("Extrayendo…") {
            val list = paths?.let { entriesFor(it) }
            val created = withContext(Dispatchers.IO) {
                Archives.extract(file, list, dest, password, strip = prefix,
                    progress = { d, t -> if (t > 0) prog = d.toFloat() / t }, cancelled = { cancel })
            }
            selected = emptySet()
            Toast.makeText(context, "Listo: ${created.size} archivo(s) en ${dest.name}", Toast.LENGTH_LONG).show()
            onExtracted(dest)
        }
    }

    fun defaultDest(): File {
        val base = file.name.replace(Regex("""(\.tar)?\.[^.]+$""", RegexOption.IGNORE_CASE), "").replace(Regex("""\.part\d+$"""), "")
        return Archives.uniqueFile(File(file.parentFile, base))
    }

    BackHandler {
        when {
            selected.isNotEmpty() -> selected = emptySet()
            prefix.isNotEmpty() -> prefix = prefix.trimEnd('/').substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }
            else -> onClose()
        }
    }

    // va dentro de la pantalla (no en ventana aparte) para que las fotos, videos y PDF que abras salgan encima
    run {
        Surface(Modifier.fillMaxSize().clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {},
            color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                // barra de arriba
                Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (selected.isNotEmpty()) {
                        IconButton(onClick = { selected = emptySet() }) { Icon(Icons.Filled.Close, "Cancelar") }
                        Text("${selected.size} seleccionados", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                        IconButton(onClick = { selected = nodes.map { it.path }.toSet() }) { Icon(Icons.Filled.SelectAll, "Todo") }
                    } else {
                        IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Cerrar") }
                        Column(Modifier.weight(1f)) {
                            Text(file.name, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (entries != null) Text(
                                "$nFiles archivos · ${formatSize(totalSize)}" +
                                    if (totalPacked in 1 until totalSize) " → ${formatSize(totalPacked)} (${100 - totalPacked * 100 / totalSize.coerceAtLeast(1)}% menos)" else "",
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                        IconButton(onClick = { extractTo(defaultDest(), null) }, enabled = entries != null) {
                            Icon(Icons.Filled.Unarchive, "Extraer todo")
                        }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Más") }
                            DropdownMenu(menu, { menu = false }) {
                                DropdownMenuItem(text = { Text("Extraer todo en «${defaultDest().name}»") }, onClick = { menu = false; extractTo(defaultDest(), null) })
                                DropdownMenuItem(text = { Text("Extraer todo aquí (sin carpeta nueva)") }, onClick = { menu = false; extractTo(file.parentFile!!, null) })
                                DropdownMenuItem(text = { Text("Extraer todo en…") }, onClick = { menu = false; pickDest = true })
                            }
                        }
                    }
                }
                // ruta dentro del comprimido
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    val parts = prefix.trimEnd('/').split('/').filter { it.isNotEmpty() }
                    Crumb(file.name, prefix.isEmpty()) { prefix = ""; selected = emptySet() }
                    parts.forEachIndexed { i, p ->
                        Text(" › ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Crumb(p, i == parts.lastIndex) { prefix = parts.take(i + 1).joinToString("/") + "/"; selected = emptySet() }
                    }
                }
                when {
                    error != null -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(error!!, color = MaterialTheme.colorScheme.error)
                    }
                    entries == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    else -> LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                        items(nodes, key = { it.path }) { n ->
                            val sel = n.path in selected
                            val (icon, color) = iconFor(n.name, n.isDir)
                            Row(
                                Modifier.fillMaxWidth()
                                    .background(if (sel) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent)
                                    .combinedClickable(
                                        onClick = {
                                            when {
                                                selected.isNotEmpty() -> selected = if (sel) selected - n.path else selected + n.path
                                                n.isDir -> prefix = n.path + "/"
                                                else -> preview(n)
                                            }
                                        },
                                        onLongClick = { selected = if (sel) selected - n.path else selected + n.path }
                                    )
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box {
                                    Icon(icon, null, tint = color, modifier = Modifier.size(38.dp))
                                    if (sel) Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp).align(Alignment.BottomEnd).background(MaterialTheme.colorScheme.background, CircleShape))
                                }
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(n.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        (if (n.isDir) "${n.count} archivos · " else "") + formatSize(n.size) +
                                            (if (n.time > 0) " · ${formatDate(n.time)}" else ""),
                                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (n.encrypted) Icon(Icons.Filled.Lock, "Con contraseña", tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
                // acciones de la selección
                if (selected.isNotEmpty()) Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BigAction("Extraer aquí", Modifier.weight(1f)) { extractTo(defaultDest(), selected) }
                    BigAction("Extraer en…", Modifier.weight(1f)) { pickDest = true }
                }
            }
        }
    }

    if (pickDest) FolderWindow("Extraer en…", "Extraer aquí", file.parentFile, onDismiss = { pickDest = false }) { dest ->
        pickDest = false
        extractTo(dest, selected.takeIf { it.isNotEmpty() })
    }
    busy?.let { ArchiveProgress(it, prog) { cancel = true } }
    if (askPw) PasswordDialog(wrongPw, onDismiss = { askPw = false; wrongPw = false; if (entries == null) onClose() }) { pw ->
        password = pw; askPw = false; wrongPw = false
        pending?.invoke(); pending = null
    }
    textPreview?.let { (name, txt) ->
        Dialog(onDismissRequest = { textPreview = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxWidth(0.94f).heightIn(max = 640.dp), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        IconButton(onClick = { textPreview = null }) { Icon(Icons.Filled.Close, "Cerrar") }
                    }
                    Text(txt, fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                        modifier = Modifier.verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState()))
                }
            }
        }
    }
}

@Composable
private fun Crumb(label: String, current: Boolean, onClick: () -> Unit) {
    Text(label, fontSize = 13.sp, fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
        color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1, modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick).padding(4.dp))
}

@Composable
private fun BigAction(label: String, modifier: Modifier, onClick: () -> Unit) {
    Text(label, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold,
        modifier = modifier.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primary).clickable(onClick = onClick)
            .padding(vertical = 14.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
}

/** Comprimir lo seleccionado: nombre, formato y nivel. */
@Composable
fun CompressDialog(items: List<File>, destDir: File, onDismiss: () -> Unit, onDone: (File) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(if (items.size == 1) items.first().nameWithoutExtension.ifBlank { items.first().name } else destDir.name.ifBlank { "Comprimido" }) }
    var format by remember { mutableStateOf(Archives.Format.ZIP) }
    var level by remember { mutableIntStateOf(1) }
    var busy by remember { mutableStateOf(false) }
    var prog by remember { mutableFloatStateOf(0f) }
    var cancel by remember { mutableStateOf(false) }
    ThemedDialog("Comprimir ${items.size} elemento(s)", onDismiss, listOf(
        DialogButton("Cancelar") { onDismiss() },
        DialogButton("Comprimir", true) {
            if (name.isBlank()) return@DialogButton
            val out = Archives.uniqueFile(File(destDir, "${name.trim()}.${format.ext}"))
            busy = true; cancel = false
            scope.launch {
                try {
                    withContext(Dispatchers.IO) { Archives.compress(items, out, format, level, progress = { d, t -> prog = d.toFloat() / t }, cancelled = { cancel }) }
                    Toast.makeText(context, "Listo: ${out.name} (${formatSize(out.length())})", Toast.LENGTH_LONG).show()
                    onDone(out)
                } catch (e: CancelledException) {
                    Toast.makeText(context, "Cancelado", Toast.LENGTH_SHORT).show()
                } catch (e: Throwable) {
                    Toast.makeText(context, "No se pudo comprimir: ${e.message}", Toast.LENGTH_LONG).show()
                } finally { busy = false }
            }
        })) {
        Column {
            OutlinedTextField(name, { name = it }, label = { Text("Nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text("Formato", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
            Archives.Format.entries.forEach { f ->
                Row(Modifier.fillMaxWidth().clickable { format = f }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(format == f, { format = f }); Text(f.label)
                }
            }
            Text("Compresión", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
            listOf(0 to "Sin comprimir (más rápido)", 1 to "Normal", 2 to "Máxima (más lento, más pequeño)").forEach { (l, label) ->
                Row(Modifier.fillMaxWidth().clickable { level = l }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(level == l, { level = l }); Text(label)
                }
            }
            Text("Se guarda en «${destDir.name}». Los RAR solo se pueden abrir y extraer (crear RAR requiere el programa de pago de WinRAR).",
                fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
    if (busy) ArchiveProgress("Comprimiendo…", prog) { cancel = true }
}
