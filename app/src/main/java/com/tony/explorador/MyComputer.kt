package com.tony.explorador

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class MediaStat(val count: Int, val bytes: Long)

data class PcStats(
    val music: MediaStat,
    val images: MediaStat,
    val videos: MediaStat,
    val documents: MediaStat,
    val apks: MediaStat,
    val downloads: MediaStat,
)

private fun countMedia(context: Context, uri: Uri, selection: String? = null, args: Array<String>? = null): MediaStat {
    var count = 0
    var bytes = 0L
    runCatching {
        context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.SIZE), selection, args, null)?.use { c ->
            val i = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            while (c.moveToNext()) { count++; bytes += c.getLong(i) }
        }
    }
    return MediaStat(count, bytes)
}

suspend fun loadPcStats(context: Context): PcStats = withContext(Dispatchers.IO) {
    val files = MediaStore.Files.getContentUri("external")
    val docMimes = listOf(
        "application/pdf", "application/msword", "text/plain",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/vnd.ms-powerpoint", "application/vnd.openxmlformats-officedocument.presentationml.presentation",
    )
    val downloads = File(Environment.getExternalStorageDirectory(), Environment.DIRECTORY_DOWNLOADS)
    var dCount = 0
    var dBytes = 0L
    runCatching {
        downloads.walkTopDown().maxDepth(6).forEach { if (it.isFile) { dCount++; dBytes += it.length() } }
    }
    PcStats(
        music = countMedia(context, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, "${MediaStore.Audio.Media.IS_MUSIC} != 0"),
        images = countMedia(context, MediaStore.Images.Media.EXTERNAL_CONTENT_URI),
        videos = countMedia(context, MediaStore.Video.Media.EXTERNAL_CONTENT_URI),
        documents = countMedia(
            context, files,
            "${MediaStore.Files.FileColumns.MIME_TYPE} IN (${docMimes.joinToString(",") { "?" }})",
            docMimes.toTypedArray()
        ),
        apks = countMedia(context, files, "${MediaStore.Files.FileColumns.DATA} LIKE ?", arrayOf("%.apk")),
        downloads = MediaStat(dCount, dBytes),
    )
}

private data class SysInfo(val label: String, val value: String)

private fun systemInfo(context: Context): List<SysInfo> {
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val mem = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
    val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    val battery = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    val dm = context.resources.displayMetrics
    val cpu = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL.takeIf { it.isNotBlank() && it != "unknown" } else null
    return listOf(
        SysInfo("Equipo", "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"),
        SysInfo("Sistema", "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"),
        SysInfo("Procesador", cpu ?: Build.HARDWARE),
        SysInfo("Memoria RAM", "${formatSize(mem.availMem)} libres de ${formatSize(mem.totalMem)}"),
        SysInfo("Batería", if (battery in 0..100) "$battery %" else "—"),
        SysInfo("Pantalla", "${dm.widthPixels} × ${dm.heightPixels}"),
        SysInfo("Explorador Tony", "v${currentVersionName(context)}"),
    )
}

@Composable
fun MyComputerScreen(
    onOpenFolder: (File) -> Unit,
    onOpenMusic: () -> Unit,
    onClose: () -> Unit,
    onHome: () -> Unit,
) {
    BackHandler(onBack = onClose)
    Column(Modifier.fillMaxSize().background(Color.White).pointerInput(Unit) {}) {
        // Barra de título XP
        Box(Modifier.fillMaxWidth().background(XpColors.titleBrush).statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás", tint = Color.White) }
                XpIcon(XpIconKind.COMPUTER, 28.dp)
                Spacer(Modifier.width(8.dp))
                Text("Mi PC", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                HomeButton(onHome, Modifier.padding(end = 8.dp))
            }
        }

        MyComputerContent(onOpenFolder, onOpenMusic)
    }
}

/** Contenido de Mi PC (sirve igual en ventana o en pantalla completa). */
@Composable
fun MyComputerContent(onOpenFolder: (File) -> Unit, onOpenMusic: () -> Unit) {
    val context = LocalContext.current
    val roots = remember { storageRoots(context) }
    val info = remember { systemInfo(context) }
    val stats by produceState<PcStats?>(null) { value = loadPcStats(context) }
    val internal = roots.first().file
    fun dir(name: String) = File(internal, name)

    Box(Modifier.fillMaxSize().background(Color.White)) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            // Panel azul de la izquierda (aquí arriba): información del sistema
            item {
                Column(
                    Modifier.fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(XpColors.SideTop, XpColors.SideBottom)))
                        .padding(10.dp)
                ) {
                    XpTaskBox("Información del sistema") {
                        info.forEach { (label, value) ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                Text("$label:", fontSize = 12.sp, color = Color(0xFF215DC6), fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(110.dp))
                                Text(value, fontSize = 12.sp, color = Color.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    XpTaskBox("Otros sitios") {
                        PlaceLink("Mis documentos", XpIconKind.DOCUMENTS) { onOpenFolder(dir(Environment.DIRECTORY_DOCUMENTS)) }
                        PlaceLink("Mi música", XpIconKind.MUSIC) { onOpenMusic() }
                        PlaceLink("Descargas", XpIconKind.DOWNLOADS) { onOpenFolder(dir(Environment.DIRECTORY_DOWNLOADS)) }
                    }
                }
            }

            item { SectionHeader("Unidades de almacenamiento") }
            roots.forEach { r ->
                item(key = r.file.absolutePath) {
                    val total = r.file.totalSpace
                    val free = r.file.freeSpace
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpenFolder(r.file) }.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        XpIcon(if (r.name.startsWith("Tarjeta")) XpIconKind.SD_CARD else XpIconKind.DRIVE, 48.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(if (r.name.startsWith("Tarjeta")) "Tarjeta SD (E:)" else "Disco local (C:)",
                                fontWeight = FontWeight.Bold, color = Color.Black)
                            Spacer(Modifier.height(4.dp))
                            UsageBar(if (total > 0) (total - free).toFloat() / total else 0f)
                            Spacer(Modifier.height(2.dp))
                            Text("${formatSize(free)} libres de ${formatSize(total)}", fontSize = 12.sp, color = Color(0xFF555555))
                        }
                    }
                }
            }

            item { SectionHeader("Tu contenido") }
            val s = stats
            val rows = listOf(
                Triple("Música", XpIconKind.MUSIC, s?.music) to { onOpenMusic() },
                Triple("Imágenes", XpIconKind.PICTURES, s?.images) to { onOpenFolder(dir(Environment.DIRECTORY_DCIM)) },
                Triple("Videos", XpIconKind.VIDEOS, s?.videos) to { onOpenFolder(dir(Environment.DIRECTORY_MOVIES)) },
                Triple("Documentos", XpIconKind.DOCUMENTS, s?.documents) to { onOpenFolder(dir(Environment.DIRECTORY_DOCUMENTS)) },
                Triple("Descargas", XpIconKind.DOWNLOADS, s?.downloads) to { onOpenFolder(dir(Environment.DIRECTORY_DOWNLOADS)) },
                Triple("Instaladores (APK)", XpIconKind.FOLDER, s?.apks) to { onOpenFolder(dir(Environment.DIRECTORY_DOWNLOADS)) },
            )
            rows.forEach { (data, action) ->
                val (label, icon, stat) = data
                item(key = label) {
                    Row(
                        Modifier.fillMaxWidth().clickable(onClick = action).padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        XpIcon(icon, 44.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(label, fontWeight = FontWeight.Bold, color = Color.Black)
                            Text(
                                when {
                                    stat == null -> "Contando…"
                                    else -> "${stat.count} ${if (stat.count == 1) "archivo" else "archivos"} · ${formatSize(stat.bytes)}"
                                },
                                fontSize = 12.sp, color = Color(0xFF555555)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun XpTaskBox(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))) {
        Row(
            Modifier.fillMaxWidth()
                .background(Brush.horizontalGradient(listOf(Color.White, Color(0xFFC6D3F7))))
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = XpColors.SideHeader)
        }
        Column(Modifier.fillMaxWidth().background(XpColors.SidePanel).padding(10.dp)) { content() }
    }
}

@Composable
private fun PlaceLink(label: String, icon: XpIconKind, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        XpIcon(icon, 22.dp)
        Spacer(Modifier.width(8.dp))
        Text(label, fontSize = 13.sp, color = XpColors.SideHeader)
    }
}

@Composable
private fun SectionHeader(text: String) {
    Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 16.dp, bottom = 4.dp)) {
        Text(text, fontWeight = FontWeight.Bold, color = Color(0xFF0A246A), fontSize = 14.sp)
        Box(
            Modifier.fillMaxWidth().height(2.dp)
                .background(Brush.horizontalGradient(listOf(Color(0xFF7BA2E7), Color.Transparent)))
        )
    }
}

/** Barra de uso del disco con bloques, como en las propiedades de disco. */
@Composable
private fun UsageBar(fraction: Float) {
    Canvas(
        Modifier.fillMaxWidth().height(14.dp)
            .border(1.dp, Color(0xFF7F9DB9))
            .background(Color.White)
    ) {
        val pad = 2.dp.toPx()
        val block = 6.dp.toPx()
        val gap = 2.dp.toPx()
        val filled = (size.width - pad * 2) * fraction.coerceIn(0f, 1f)
        val color = if (fraction > 0.9f) Color(0xFFD9381E) else Color(0xFF2E7BE6)
        var x = pad
        while (x + block <= pad + filled) {
            drawRect(
                Brush.verticalGradient(listOf(color.copy(alpha = 0.55f), color, color.copy(alpha = 0.8f))),
                topLeft = Offset(x, pad), size = Size(block, size.height - pad * 2)
            )
            x += block + gap
        }
    }
}
