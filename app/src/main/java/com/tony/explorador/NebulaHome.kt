package com.tony.explorador

import android.content.Context
import android.os.Environment
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/** Pantalla de inicio del explorador (antes de entrar a las carpetas). */
object ExplorerHome { var show by mutableStateOf(false) }

private object NB {
    val bg1 = Color(0xFF0B0820); val bg2 = Color(0xFF1C0F3D)
    val card = Color(0xFF17123A); val cardLine = Color(0x33B98CFF)
    val text = Color(0xFFF4EEFF); val sub = Color(0xFFA99FCF); val accent = Color(0xFFB98CFF); val pink = Color(0xFFE07BFF)
}

/** Categorías que se cuentan en el teléfono. */
enum class FileCategory(val label: String, val icon: Int) {
    PHOTOS("Fotos", R.drawable.nb_photos), VIDEOS("Videos", R.drawable.nb_videos), MUSIC("Música", R.drawable.nb_music),
    DOCS("Documentos", R.drawable.nb_docs), APPS("Apps", R.drawable.nb_apps), ZIP("Comprimidos", R.drawable.nb_zip),
    GAMES("Juegos", R.drawable.nb_games), OTHERS("Otros", R.drawable.nb_others)
}

private val docExt = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "odt", "ods", "odp", "rtf", "csv", "md", "epub", "html", "htm", "xml", "json")
private val zipExt = setOf("zip", "rar", "7z", "tar", "gz", "tgz", "bz2", "xz")
private val gameExt = setOf("iso", "nes", "sfc", "smc", "gba", "gbc", "gb", "nds", "3ds", "cia", "xci", "nsp", "n64", "z64", "psx", "pbp", "cso", "chd", "rvz", "wbfs")
private val imgExt = setOf("jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "bmp", "dng")
private val vidExt = setOf("mp4", "mkv", "mov", "avi", "webm", "3gp", "m4v", "ts")
private val audExt = setOf("mp3", "m4a", "flac", "wav", "ogg", "opus", "aac", "wma", "alac")

fun categoryOf(name: String): FileCategory? {
    val e = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
    return when (e) {
        in imgExt -> FileCategory.PHOTOS; in vidExt -> FileCategory.VIDEOS; in audExt -> FileCategory.MUSIC
        in docExt -> FileCategory.DOCS; "apk", "apks", "xapk" -> FileCategory.APPS; in zipExt -> FileCategory.ZIP
        in gameExt -> FileCategory.GAMES
        "lrc", "nomedia", "tmp", "log", "db", "thumbnails" -> null
        else -> FileCategory.OTHERS
    }
}

/** Todos los archivos del teléfono que el índice de Android conoce, por categoría. */
fun categoryFiles(context: Context, cat: FileCategory?, limit: Int = 5000): Map<FileCategory, List<File>> {
    val out = HashMap<FileCategory, MutableList<File>>()
    val col = android.provider.MediaStore.Files.FileColumns.DATA
    runCatching {
        context.contentResolver.query(android.provider.MediaStore.Files.getContentUri("external"), arrayOf(col),
            "${android.provider.MediaStore.Files.FileColumns.SIZE} > 0", null,
            "${android.provider.MediaStore.Files.FileColumns.DATE_MODIFIED} DESC")?.use { c ->
            while (c.moveToNext()) {
                val p = c.getString(0) ?: continue
                if (p.contains("/.")) continue
                val k = categoryOf(p) ?: continue
                if (cat != null && k != cat) continue
                val l = out.getOrPut(k) { ArrayList() }
                if (l.size < limit) l.add(File(p))
            }
        }
    }
    return out
}

@Composable
fun NebulaHome(
    roots: List<StorageRoot>,
    onOpenDir: (File) -> Unit,
    onCategory: (FileCategory) -> Unit,
    onFiles: () -> Unit,
    onRecent: () -> Unit,
    onRemote: () -> Unit,
) {
    val ctx = LocalContext.current
    val internal = roots.first().file
    val counts by produceState(emptyMap<FileCategory, Int>()) {
        value = withContext(Dispatchers.IO) { categoryFiles(ctx, null, Int.MAX_VALUE).mapValues { it.value.size } }
    }
    val downloads = remember { File(internal, Environment.DIRECTORY_DOWNLOADS) }
    val dlCount by produceState(-1) { value = withContext(Dispatchers.IO) { downloads.listFiles()?.count { !it.name.startsWith(".") } ?: 0 } }
    val trashCount = Trash.entries.size

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(NB.bg2, NB.bg1, NB.bg1)))) {
        // estrellitas de fondo
        val stars = remember { List(60) { Triple(Math.random().toFloat(), Math.random().toFloat() * 0.45f, 0.4f + Math.random().toFloat()) } }
        Canvas(Modifier.fillMaxSize()) {
            stars.forEach { (x, y, s) -> drawCircle(Color.White.copy(alpha = 0.25f * s), 1.3.dp.toPx() * s, Offset(x * size.width, y * size.height)) }
            drawCircle(Brush.radialGradient(listOf(Color(0x447A3CFF), Color.Transparent), Offset(size.width * 0.8f, size.height * 0.05f), size.width * 0.7f),
                size.width * 0.7f, Offset(size.width * 0.8f, size.height * 0.05f))
        }
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                // encabezado
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp, bottom = 14.dp)) {
                    Image(painterResource(R.drawable.nb_galaxy), null, Modifier.width(78.dp).height(58.dp))
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(buildAnnotatedString {
                            withStyle(SpanStyle(color = NB.text)) { append("Drive") }
                            withStyle(SpanStyle(brush = Brush.linearGradient(listOf(NB.accent, NB.pink)))) { append("Nebula") }
                        }, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                        Text("Tus archivos. Tu universo.", color = NB.sub, fontSize = 13.sp)
                    }
                }
                // almacenamiento
                val total = internal.totalSpace; val free = internal.freeSpace; val used = total - free
                val pct = if (total > 0) used.toFloat() / total else 0f
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Brush.linearGradient(listOf(Color(0xFF261A55), NB.card)))
                    .border(1.dp, NB.cardLine, RoundedCornerShape(22.dp)).clickable { onOpenDir(internal) }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    IconTile(Icons.Filled.PhoneAndroid)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Almacenamiento interno", color = NB.text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                                Text("${formatSize(used)} de ${formatSize(total)}", color = NB.sub, fontSize = 13.sp)
                            }
                            Text("${(pct * 100).toInt()}%", color = NB.accent, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        }
                        Spacer(Modifier.height(10.dp))
                        Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(Color(0x33FFFFFF))) {
                            Box(Modifier.fillMaxWidth(pct.coerceIn(0.02f, 1f)).fillMaxHeight().clip(CircleShape)
                                .background(Brush.horizontalGradient(listOf(NB.accent, NB.pink))))
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                // accesos
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickCard(R.drawable.nb_star, "Recientes", "Lo último", Modifier.weight(1f), onRecent)
                    QuickCard(R.drawable.nb_download, "Descargas", if (dlCount >= 0) "$dlCount elementos" else "…", Modifier.weight(1f)) { onOpenDir(downloads) }
                    QuickCard(R.drawable.nb_trash, "Papelera", if (trashCount >= 0) "$trashCount elementos" else "…", Modifier.weight(1f)) { AppNav.openTrash() }
                }
                // categorías
                Text("Categorías", color = NB.text, fontWeight = FontWeight.Bold, fontSize = 22.sp, modifier = Modifier.padding(top = 22.dp, bottom = 10.dp))
                FileCategory.values().toList().chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                        row.forEach { c ->
                            Column(Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(NB.card).border(1.dp, NB.cardLine, RoundedCornerShape(18.dp))
                                .clickable { onCategory(c) }.padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Image(painterResource(c.icon), c.label, Modifier.size(52.dp))
                                Text(c.label, color = NB.text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
                                Text(counts[c]?.let { "%,d".format(it) } ?: if (counts.isEmpty()) "…" else "0", color = NB.sub, fontSize = 11.sp)
                            }
                        }
                    }
                }
                // ubicaciones
                Text("Ubicaciones", color = NB.text, fontWeight = FontWeight.Bold, fontSize = 22.sp, modifier = Modifier.padding(top = 12.dp, bottom = 10.dp))
                roots.forEach { r ->
                    val sd = r.name.startsWith("Tarjeta") || r !== roots.first()
                    LocationRow(if (sd) Icons.Filled.SdStorage else Icons.Filled.PhoneAndroid, if (sd) r.name else "Almacenamiento interno",
                        "${formatSize(r.file.freeSpace)} libres de ${formatSize(r.file.totalSpace)}") { onOpenDir(r.file) }
                }
                LocationRow(null, "NAS · Música", "Control remoto de tus bocinas", onRemote)
                LocationRow(Icons.Filled.Home, "Casa", "Focos, aires, teles y aspiradora", onClick = { HomeUi.open = true })
                Spacer(Modifier.height(16.dp))
            }
            // barra de abajo
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).navigationBarsPadding().clip(RoundedCornerShape(26.dp))
                .background(Color(0xCC17123A)).border(1.dp, NB.cardLine, RoundedCornerShape(26.dp)).padding(6.dp)) {
                NavItem(Icons.Filled.Home, "Inicio", true, Modifier.weight(1f)) {}
                NavItem(Icons.Filled.Folder, "Archivos", false, Modifier.weight(1f), onFiles)
                NavItem(Icons.Filled.Schedule, "Recientes", false, Modifier.weight(1f), onRecent)
                NavItem(Icons.Filled.Speaker, "Bocinas", false, Modifier.weight(1f), onRemote)
            }
        }
    }
}

@Composable
private fun IconTile(icon: ImageVector) {
    Box(Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(Color(0xFF3A2E73), Color(0xFF231A4D)))),
        contentAlignment = Alignment.Center) { Icon(icon, null, tint = NB.text, modifier = Modifier.size(28.dp)) }
}

@Composable
private fun QuickCard(img: Int, title: String, sub: String, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(NB.card).border(1.dp, NB.cardLine, RoundedCornerShape(18.dp))
        .clickable(onClick = onClick).padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Image(painterResource(img), null, Modifier.size(44.dp))
        Text(title, color = NB.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
        Text(sub, color = NB.sub, fontSize = 11.sp)
    }
}

@Composable
private fun LocationRow(icon: ImageVector?, title: String, sub: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(18.dp)).background(NB.card).border(1.dp, NB.cardLine, RoundedCornerShape(18.dp))
        .clickable(onClick = onClick).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) IconTile(icon) else Image(painterResource(R.drawable.nb_cloud), null, Modifier.size(52.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = NB.text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Text(sub, color = NB.sub, fontSize = 13.sp)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = NB.sub)
    }
}

@Composable
private fun NavItem(icon: ImageVector, label: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(20.dp)).background(if (on) Color(0x553A2580) else Color.Transparent).clickable(onClick = onClick)
        .padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, label, tint = if (on) NB.accent else NB.sub, modifier = Modifier.size(24.dp))
        Text(label, color = if (on) NB.text else NB.sub, fontSize = 12.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
    }
}
