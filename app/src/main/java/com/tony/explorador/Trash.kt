package com.tony.explorador

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Atajos para abrir pantallas desde cualquier parte. */
object AppNav {
    var openTrash: () -> Unit = {}
    var openWeb: () -> Unit = {}
}

/**
 * Papelera: lo que borras en la galería, videos, música o el explorador se guarda aquí
 * un tiempo (tú eliges cuánto) y se puede restaurar.
 */
object Trash {
    data class Entry(val id: String, val original: String, val deletedAt: Long, val isDir: Boolean) {
        val name: String get() = File(original).name
    }

    private lateinit var app: Context
    private val dir: File get() = File(Environment.getExternalStorageDirectory(), ".PapeleraTony")
    private val index get() = File(app.filesDir, "papelera.json")
    val entries = mutableStateListOf<Entry>()
    /** Días que duran las cosas en la papelera (0 = no se borran solas). */
    var days by mutableIntStateOf(30)
        private set
    var iconAdded = false
        private set
    /** Sube cada vez que algo entra o sale de la papelera (para refrescar galería y videos). */
    var changes by mutableIntStateOf(0)
        private set

    fun stored(e: Entry) = File(dir, e.id + "_" + e.name)

    fun init(context: Context) {
        app = context.applicationContext
        runCatching {
            val o = JSONObject(index.readText())
            days = o.optInt("days", 30)
            iconAdded = o.optBoolean("icon", false)
            val a = o.optJSONArray("items") ?: JSONArray()
            for (i in 0 until a.length()) {
                val e = a.getJSONObject(i)
                entries.add(Entry(e.getString("id"), e.getString("o"), e.getLong("t"), e.optBoolean("d")))
            }
        }
        Thread { runCatching { purgeOld() } }.start()
    }

    @Synchronized private fun save() {
        runCatching {
            val a = JSONArray()
            entries.toList().forEach { a.put(JSONObject().put("id", it.id).put("o", it.original).put("t", it.deletedAt).put("d", it.isDir)) }
            index.writeText(JSONObject().put("days", days).put("icon", iconAdded).put("items", a).toString())
        }
    }

    fun markIconAdded() { iconAdded = true; save() }
    fun changeDays(d: Int) { days = d; save(); Thread { runCatching { purgeOld() } }.start() }

    private fun purgeOld() {
        // lo que ya no existe en la carpeta se quita de la lista
        val gone = entries.toList().filter { !stored(it).exists() }
        val limit = if (days > 0) System.currentTimeMillis() - days * 86_400_000L else Long.MIN_VALUE
        val old = entries.toList().filter { it.deletedAt < limit }
        old.forEach { stored(it).deleteRecursively() }
        if (gone.isNotEmpty() || old.isNotEmpty()) {
            android.os.Handler(android.os.Looper.getMainLooper()).post { entries.removeAll((gone + old).toSet()); save() }
        }
    }

    private fun move(from: File, to: File): Boolean {
        if (from.renameTo(to)) return true
        // otro almacenamiento (por ejemplo la tarjeta SD): copiar y borrar
        return runCatching { from.copyRecursively(to, overwrite = true) && from.deleteRecursively() }.getOrDefault(false)
    }

    /** Manda archivos o carpetas a la papelera. Regresa cuántos se pudieron mover. */
    suspend fun moveToTrash(files: List<File>): Int = withContext(Dispatchers.IO) {
        dir.mkdirs()
        runCatching { File(dir, ".nomedia").createNewFile() }
        val added = mutableListOf<Entry>()
        files.forEachIndexed { i, f ->
            if (!f.exists()) return@forEachIndexed
            val e = Entry("${System.currentTimeMillis()}$i", f.absolutePath, System.currentTimeMillis(), f.isDirectory)
            if (move(f, stored(e))) added.add(e)
        }
        if (added.isNotEmpty()) {
            MediaScannerConnection.scanFile(app, added.map { it.original }.toTypedArray(), null, null)
            withContext(Dispatchers.Main) {
                entries.addAll(0, added)
                added.forEach { LibraryStore.removeSong(it.original) }
                changes++
                save()
            }
        }
        added.size
    }

    suspend fun restore(list: List<Entry>): Int = withContext(Dispatchers.IO) {
        val done = mutableListOf<Entry>()
        val paths = mutableListOf<String>()
        list.forEach { e ->
            val orig = File(e.original)
            orig.parentFile?.mkdirs()
            val target = if (orig.exists()) uniqueTarget(orig.parentFile ?: return@forEach, orig.name) else orig
            if (move(stored(e), target)) { done.add(e); paths.add(target.absolutePath) }
        }
        if (paths.isNotEmpty()) MediaScannerConnection.scanFile(app, paths.toTypedArray(), null, null)
        withContext(Dispatchers.Main) { entries.removeAll(done.toSet()); changes++; save() }
        done.size
    }

    suspend fun deleteForever(list: List<Entry>) = withContext(Dispatchers.IO) {
        list.forEach { stored(it).deleteRecursively() }
        withContext(Dispatchers.Main) { entries.removeAll(list.toSet()); save() }
    }

    fun daysLeft(e: Entry): Int? =
        if (days <= 0) null else (days - ((System.currentTimeMillis() - e.deletedAt) / 86_400_000L).toInt()).coerceAtLeast(0)
}

/** Marca de «seleccionado» encima de una miniatura. */
fun Modifier.selectionOverlay(on: Boolean): Modifier = if (!on) this else drawWithContent {
    drawContent()
    drawRect(Color(0x667A4FE0))
    val r = 11.dp.toPx()
    val c = Offset(size.width - r - 6.dp.toPx(), r + 6.dp.toPx())
    drawCircle(Color.White, r, c)
    drawCircle(Color(0xFF7A4FE0), r * 0.82f, c)
    drawLine(Color.White, Offset(c.x - r * 0.4f, c.y), Offset(c.x - r * 0.1f, c.y + r * 0.32f), 2.dp.toPx(), StrokeCap.Round)
    drawLine(Color.White, Offset(c.x - r * 0.1f, c.y + r * 0.32f), Offset(c.x + r * 0.42f, c.y - r * 0.3f), 2.dp.toPx(), StrokeCap.Round)
}

private val DAY_OPTIONS = listOf(1 to "1 día", 7 to "7 días", 15 to "15 días", 30 to "30 días", 60 to "60 días", 0 to "No borrar nunca solos")

@Composable
fun TrashScreen(onClose: () -> Unit, onHome: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val selected = remember { mutableStateListOf<String>() }
    var daysMenu by remember { mutableStateOf(false) }
    var confirmEmpty by remember { mutableStateOf(false) }
    val entries = Trash.entries
    BackHandler { if (selected.isNotEmpty()) selected.clear() else onClose() }
    fun chosen() = entries.filter { it.id in selected }

    Surface(Modifier.fillMaxSize().pointerInput(Unit) {}, color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            Surface(tonalElevation = 3.dp) {
                Row(Modifier.statusBarsPadding().fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { if (selected.isNotEmpty()) selected.clear() else onClose() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás") }
                    if (selected.isEmpty()) {
                        XpIcon(XpIconKind.TRASH, 30.dp); Spacer(Modifier.width(8.dp))
                        Text("Papelera", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Box {
                            IconButton(onClick = { daysMenu = true }) { Icon(Icons.Filled.Schedule, "Tiempo en la papelera") }
                            DropdownMenu(daysMenu, { daysMenu = false }) {
                                Text("Guardar lo borrado durante:", fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                                DAY_OPTIONS.forEach { (d, label) ->
                                    DropdownMenuItem(text = { Text(label, fontWeight = if (d == Trash.days) FontWeight.Bold else null) },
                                        onClick = { Trash.changeDays(d); daysMenu = false })
                                }
                            }
                        }
                        HomeButton(onHome, Modifier.padding(end = 10.dp))
                    } else {
                        Text("${selected.size} seleccionados", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        IconButton(onClick = {
                            val list = chosen(); selected.clear()
                            scope.launch { val n = Trash.restore(list); Toast.makeText(context, "Restaurados: $n", Toast.LENGTH_SHORT).show() }
                        }) { Icon(Icons.Filled.RestoreFromTrash, "Restaurar") }
                        IconButton(onClick = { confirmEmpty = true }) { Icon(Icons.Filled.DeleteForever, "Borrar para siempre", tint = MaterialTheme.colorScheme.error) }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (Trash.days > 0) "Lo borrado se guarda ${DAY_OPTIONS.first { it.first == Trash.days }.second} y luego se elimina solo."
                    else "Lo borrado se queda aquí hasta que tú lo elimines.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                if (entries.isNotEmpty() && selected.isEmpty()) TextButton(onClick = { confirmEmpty = true }) { Text("Vaciar") }
            }
            if (entries.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("La papelera está vacía", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else LazyColumn(Modifier.fillMaxSize()) {
                items(entries, key = { it.id }) { e ->
                    val f = Trash.stored(e)
                    val on = e.id in selected
                    Row(
                        Modifier.fillMaxWidth()
                            .background(if (on) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                            .clickable { if (on) selected.remove(e.id) else selected.add(e.id) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val kind = remember(e.id) { if (e.isDir) FileKind.FOLDER else f.kind() }
                        Box(Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)).selectionOverlay(on), contentAlignment = Alignment.Center) {
                            FilePreview(f, kind, 34.dp, Modifier.fillMaxSize())
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(e.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(File(e.original).parent.orEmpty().removePrefix(Environment.getExternalStorageDirectory().absolutePath).ifBlank { "/" },
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(Trash.daysLeft(e)?.let { if (it == 0) "Se elimina hoy" else "Se elimina en $it día${if (it == 1) "" else "s"}" } ?: "Sin fecha de eliminación",
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    if (confirmEmpty) {
        val list = if (selected.isNotEmpty()) chosen() else entries.toList()
        ThemedDialog("¿Borrar para siempre?", { confirmEmpty = false }, listOf(
            DialogButton("Cancelar") { confirmEmpty = false },
            DialogButton("Borrar", true) {
                confirmEmpty = false; selected.clear()
                scope.launch { Trash.deleteForever(list); Toast.makeText(context, "Eliminados para siempre: ${list.size}", Toast.LENGTH_SHORT).show() }
            })) {
            Text("Se van a eliminar ${list.size} elemento(s) y ya no se podrán recuperar.")
        }
    }
}
