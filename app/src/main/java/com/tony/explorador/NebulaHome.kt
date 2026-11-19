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
import androidx.compose.material.icons.filled.Storefront
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale

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

/** Colores del estilo «primavera» (claro y oscuro). */
internal class PvColors(val dark: Boolean) {
    val text = if (dark) Color(0xFFF2F5FF) else Color(0xFF0E1A3A)
    val sub = if (dark) Color(0xFFA9B6D8) else Color(0xFF4A5878)
    val accent = if (dark) Color(0xFF4FA8FF) else Color(0xFF1E6FFF)
    val card = if (dark) Color(0xFF141B3D).copy(alpha = 0.78f) else Color.White.copy(alpha = 0.62f)
    val cardLine = if (dark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.95f)
    val tile = if (dark) Color(0xFF1E6FFF).copy(alpha = 0.16f) else Color(0xFF1E6FFF).copy(alpha = 0.10f)
}

/** Las carpetas rápidas que eligió el usuario (rutas), guardadas en el teléfono. */
object QuickFolders {
    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("carpetas_rapidas", Context.MODE_PRIVATE)
    fun load(c: Context, internal: File): List<File> {
        val saved = prefs(c).getString("lista", null)
        if (saved != null) return saved.split("\n").filter { it.isNotBlank() }.map { File(it) }.filter { it.isDirectory }
        return listOf("Pictures", "Movies", "Music", "Documents", "DCIM").map { File(internal, it) }.filter { it.isDirectory }
    }
    fun save(c: Context, list: List<File>) { prefs(c).edit().putString("lista", list.joinToString("\n") { it.absolutePath }).apply() }
}

private enum class FolderLook(val c1: Color, val c2: Color, val glyph: ImageVector?) {
    IMAGES(Color(0xFFC77BFF), Color(0xFF8B3CFF), Icons.Filled.Image),
    VIDEOS(Color(0xFF5CC4FF), Color(0xFF1E74FF), Icons.Filled.PlayArrow),
    MUSIC(Color(0xFFFF7DB8), Color(0xFFFF3D7F), Icons.Filled.MusicNote),
    DOCS(Color(0xFFFFDD55), Color(0xFFFFAA22), Icons.Filled.Description),
    GAMES(Color(0xFF8BE36F), Color(0xFF34B84A), Icons.Filled.SportsEsports),
    PLAIN(Color(0xFF5CC4FF), Color(0xFF1E74FF), null),
}

private fun folderLabel(f: File) = when (f.name.lowercase(Locale.ROOT)) {
    "pictures" -> "Imágenes"; "movies" -> "Videos"; "music" -> "Música"; "documents" -> "Documentos"
    "dcim" -> "Cámara"; "download" -> "Descargas"; "podcasts" -> "Podcasts"; "ringtones" -> "Tonos"; "recordings" -> "Grabaciones"
    else -> f.name
}

private fun folderLook(f: File): FolderLook {
    val n = f.name.lowercase(Locale.ROOT)
    return when {
        Regex("pictur|imag|foto|photo|dcim|camera|screenshot|captur").containsMatchIn(n) -> FolderLook.IMAGES
        Regex("movie|video|pel").containsMatchIn(n) -> FolderLook.VIDEOS
        Regex("music|m[uú]sic|audio|podcast|ringtone|recording").containsMatchIn(n) -> FolderLook.MUSIC
        Regex("doc|proyect|project|trabajo|escuela|pdf").containsMatchIn(n) -> FolderLook.DOCS
        Regex("game|juego|rom|emul").containsMatchIn(n) -> FolderLook.GAMES
        else -> FolderLook.PLAIN
    }
}

/** Ícono de un color degradado (como los de la plantilla). */
@Composable
internal fun GradIcon(icon: ImageVector, colors: List<Color>, size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    Icon(icon, null, tint = Color.White, modifier = modifier.size(size)
        .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
        .drawWithCache {
            val b = Brush.linearGradient(colors, Offset(0f, 0f), Offset(this.size.width, this.size.height))
            onDrawWithContent { drawContent(); drawRect(b, blendMode = BlendMode.SrcAtop) }
        })
}

@Composable
private fun FolderGlyph(look: FolderLook, size: androidx.compose.ui.unit.Dp) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = this.size.width; val h = this.size.height
            val r = CornerRadius(w * 0.10f)
            // pestaña de atrás
            drawRoundRect(look.c2, Offset(w * 0.06f, h * 0.14f), Size(w * 0.42f, h * 0.30f), r)
            drawRoundRect(look.c2, Offset(w * 0.06f, h * 0.22f), Size(w * 0.88f, h * 0.62f), r)
            // frente
            drawRoundRect(Brush.verticalGradient(listOf(look.c1, look.c2), h * 0.30f, h * 0.88f), Offset(w * 0.04f, h * 0.32f), Size(w * 0.92f, h * 0.56f), r)
            drawRoundRect(Color.White.copy(alpha = 0.28f), Offset(w * 0.04f, h * 0.32f), Size(w * 0.92f, h * 0.06f), r)
        }
        look.glyph?.let { Icon(it, null, tint = Color.White, modifier = Modifier.padding(top = size * 0.22f).size(size * 0.32f)) }
    }
}

@Composable
fun NebulaHome(
    roots: List<StorageRoot>,
    onOpenDir: (File) -> Unit,
    onCategory: (FileCategory) -> Unit,
    onFiles: () -> Unit,
    onRecent: () -> Unit,
    onFavorites: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
) {
    val ctx = LocalContext.current
    val dark = XpColors.dark
    val pv = remember(dark) { PvColors(dark) }
    val internal = roots.first().file
    val counts by produceState(emptyMap<FileCategory, Int>()) {
        value = withContext(Dispatchers.IO) { categoryFiles(ctx, null, Int.MAX_VALUE).mapValues { it.value.size } }
    }
    val downloads = remember { File(internal, Environment.DIRECTORY_DOWNLOADS) }
    var quick by remember { mutableStateOf(QuickFolders.load(ctx, internal)) }
    var managing by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(if (dark) Color(0xFF05071E) else Color(0xFF8CC4F5))) {
        Image(painterResource(if (dark) R.drawable.pv_fondo_oscuro else R.drawable.pv_fondo_claro), null,
            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        FallingPetals(Modifier.fillMaxSize(), dark)
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            // encabezado
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)) {
                Image(painterResource(R.drawable.nb_galaxy), null, Modifier.width(64.dp).height(48.dp))
                Spacer(Modifier.width(6.dp))
                Column(Modifier.weight(1f)) {
                    Text(buildAnnotatedString {
                        withStyle(SpanStyle(color = pv.text)) { append("Drive") }
                        withStyle(SpanStyle(brush = Brush.linearGradient(listOf(Color(0xFF1E6FFF), Color(0xFF3FA9FF))))) { append("Nebula") }
                    }, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                    Text("Tus archivos. Tu universo.", color = pv.sub, fontSize = 12.sp)
                }
                RoundGlass(Icons.Filled.Search, "Buscar", pv, onSearch)
                Spacer(Modifier.width(8.dp))
                RoundGlass(Icons.Filled.Settings, "Ajustes", pv, onSettings)
            }
            // accesos
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickCard(Icons.Filled.Star, listOf(Color(0xFF3FA9FF), Color(0xFF1E5BFF)), "Favoritos", pv, Modifier.weight(1f), onFavorites)
                QuickCard(Icons.Filled.Schedule, listOf(Color(0xFF3FA9FF), Color(0xFF1E5BFF)), "Recientes", pv, Modifier.weight(1f), onRecent)
                QuickCard(Icons.Filled.Download, listOf(Color(0xFF3FC3FF), Color(0xFF1E74FF)), "Descargas", pv, Modifier.weight(1f)) { onOpenDir(downloads) }
                QuickCard(Icons.Filled.Delete, listOf(Color(0xFFB57BFF), Color(0xFF7A3CFF)), "Papelera", pv, Modifier.weight(1f)) { AppNav.openTrash() }
            }
            // carpetas rápidas
            SectionTitle("Carpetas rápidas", "Gestionar", pv) { managing = true }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                quick.forEach { f ->
                    Column(Modifier.width(70.dp).glassCard(pv, 18.dp).clickable { onOpenDir(f) }.padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        FolderGlyph(folderLook(f), 46.dp)
                        Text(folderLabel(f), color = pv.text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 4.dp))
                    }
                }
                if (quick.isEmpty()) Text("Toca «Gestionar» para elegir tus carpetas.", color = pv.sub, fontSize = 13.sp)
            }
            // categorías
            SectionTitle("Categorías", "Ver todas", pv, onFiles)
            FileCategory.values().toList().chunked(4).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                    row.forEach { c ->
                        Column(Modifier.weight(1f).glassCard(pv, 18.dp).clickable { onCategory(c) }.padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            CategoryGlyph(c)
                            Text(c.label, color = pv.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
                            Text(counts[c]?.let { "%,d".format(it) } ?: if (counts.isEmpty()) "…" else "0", color = pv.sub, fontSize = 12.sp)
                        }
                    }
                }
            }
            // explorar
            Text("Explorar", color = pv.text, fontWeight = FontWeight.Bold, fontSize = 22.sp, modifier = Modifier.padding(top = 10.dp, bottom = 8.dp))
            roots.forEach { r ->
                val sd = r !== roots.first()
                LocationRow(if (sd) Icons.Filled.SdCard else Icons.Filled.PhoneAndroid, if (sd) r.name.ifBlank { "Tarjeta SD" } else "Almacenamiento interno",
                    "${formatSize(r.file.freeSpace)} libres de ${formatSize(r.file.totalSpace)}", pv,
                    if (sd) listOf(Color(0xFF6A5CFF), Color(0xFF2E3BFF)) else listOf(Color(0xFF3FA9FF), Color(0xFF1E5BFF))) { onOpenDir(r.file) }
            }
            LocationRow(Icons.Filled.Home, "Casa", "Focos, aires, teles y aspiradora", pv, listOf(Color(0xFF5CD6A0), Color(0xFF1FA870))) { HomeUi.open = true }
            LocationRow(Icons.Filled.Storefront, "Tienda Nebula", "Nebula Música y Control Nebula", pv, listOf(Color(0xFFC77BFF), Color(0xFF7A3CFF))) { StoreUi.open = true }
            Spacer(Modifier.height(140.dp))
            Spacer(Modifier.navigationBarsPadding())
        }
    }
    if (managing) ManageQuickFolders(internal, quick, pv, onDone = { l -> if (l != null) { quick = l; QuickFolders.save(ctx, l) }; managing = false })
}

private fun Modifier.glassCard(pv: PvColors, r: androidx.compose.ui.unit.Dp) = this
    .shadow(if (pv.dark) 0.dp else 6.dp, RoundedCornerShape(r), ambientColor = Color(0x331E4B8F), spotColor = Color(0x331E4B8F))
    .clip(RoundedCornerShape(r)).background(pv.card).border(1.dp, pv.cardLine, RoundedCornerShape(r))

@Composable
private fun RoundGlass(icon: ImageVector, label: String, pv: PvColors, onClick: () -> Unit) {
    Box(Modifier.size(46.dp).glassCard(pv, 23.dp).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        GradIcon(icon, listOf(Color(0xFF3FA9FF), Color(0xFF1E5BFF)), 24.dp)
    }
}

@Composable
private fun SectionTitle(title: String, action: String, pv: PvColors, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = pv.text, fontWeight = FontWeight.Bold, fontSize = 22.sp, modifier = Modifier.weight(1f))
        Row(Modifier.clip(CircleShape).clickable(onClick = onAction).padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(action, color = pv.accent, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = pv.accent, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun QuickCard(icon: ImageVector, colors: List<Color>, title: String, pv: PvColors, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.glassCard(pv, 20.dp).clickable(onClick = onClick).padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        GradIcon(icon, colors, 34.dp)
        Text(title, color = pv.text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun CategoryGlyph(c: FileCategory) {
    val s = 40.dp
    when (c) {
        FileCategory.PHOTOS -> GradIcon(Icons.Filled.Image, listOf(Color(0xFF6FD3FF), Color(0xFF1E6FFF)), s)
        FileCategory.VIDEOS -> GradIcon(Icons.Filled.SmartDisplay, listOf(Color(0xFFB57BFF), Color(0xFF6A3CFF)), s)
        FileCategory.MUSIC -> GradIcon(Icons.Filled.MusicNote, listOf(Color(0xFFFF7DB8), Color(0xFFFF3D7F)), s)
        FileCategory.DOCS -> GradIcon(Icons.Filled.Description, listOf(Color(0xFF4FB6FF), Color(0xFF1E5BFF)), s)
        FileCategory.APPS -> GradIcon(Icons.Filled.Android, listOf(Color(0xFF8BE36F), Color(0xFF2FA84A)), s)
        FileCategory.ZIP -> GradIcon(Icons.Filled.FolderZip, listOf(Color(0xFFB57BFF), Color(0xFF5B3CFF)), s)
        FileCategory.GAMES -> GradIcon(Icons.Filled.SportsEsports, listOf(Color(0xFFB57BFF), Color(0xFF4F6BFF)), s)
        FileCategory.OTHERS -> Canvas(Modifier.size(s)) {
            val r = this.size.width * 0.2f
            listOf(0.28f to 0.28f, 0.72f to 0.28f, 0.28f to 0.72f, 0.72f to 0.72f).forEach { (x, y) ->
                drawCircle(Brush.linearGradient(listOf(Color(0xFFB57BFF), Color(0xFF7A3CFF))), r, Offset(x * this.size.width, y * this.size.height))
            }
        }
    }
}

@Composable
private fun LocationRow(icon: ImageVector, title: String, sub: String, pv: PvColors, colors: List<Color>, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp).glassCard(pv, 20.dp).clickable(onClick = onClick).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(pv.tile), contentAlignment = Alignment.Center) { GradIcon(icon, colors, 30.dp) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = pv.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(sub, color = pv.sub, fontSize = 13.sp)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = pv.sub)
    }
}

/** Pétalos que caen despacito sobre el fondo. */
@Composable
private fun FallingPetals(modifier: Modifier, dark: Boolean) {
    val t by androidx.compose.animation.core.rememberInfiniteTransition(label = "petalos").animateFloat(0f, 1f,
        androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(24000, easing = androidx.compose.animation.core.LinearEasing)), label = "t")
    val petals = remember { List(14) { val r = kotlin.random.Random(it * 71 + 5); floatArrayOf(r.nextFloat(), r.nextFloat(), 0.6f + r.nextFloat() * 0.8f, r.nextFloat() * 6.28f) } }
    Canvas(modifier) {
        val w = size.width; val h = size.height
        petals.forEach { (x0, y0, s, ph) ->
            val y = ((y0 + t * (0.6f + s * 0.4f)) % 1f) * h
            val x = (x0 * w + kotlin.math.sin(t * 12f + ph) * 30.dp.toPx() + t * w * 0.2f) % w
            val pw = 7.dp.toPx() * s; val pH = 4.dp.toPx() * s
            rotate(kotlin.math.sin(t * 20f + ph) * 60f, Offset(x, y)) {
                drawOval(Color.White.copy(alpha = if (dark) 0.55f else 0.85f), Offset(x - pw / 2, y - pH / 2), Size(pw, pH))
            }
        }
    }
}

@Composable
private fun ManageQuickFolders(internal: File, current: List<File>, pv: PvColors, onDone: (List<File>?) -> Unit) {
    val all = remember { internal.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") && it.name != "Android" }?.sortedBy { folderLabel(it).lowercase() } ?: emptyList() }
    var sel by remember { mutableStateOf(current.map { it.absolutePath }) }
    androidx.compose.material3.AlertDialog(onDismissRequest = { onDone(null) },
        title = { Text("Carpetas rápidas") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text("Marca las que quieres tener a la mano (en el orden que las marques).", fontSize = 13.sp)
                all.forEach { f ->
                    val on = f.absolutePath in sel
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
                        sel = if (on) sel - f.absolutePath else sel + f.absolutePath
                    }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        FolderGlyph(folderLook(f), 34.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(folderLabel(f), modifier = Modifier.weight(1f))
                        androidx.compose.material3.Checkbox(on, { sel = if (on) sel - f.absolutePath else sel + f.absolutePath })
                    }
                }
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { onDone(sel.map { File(it) }) }) { Text("Listo") } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = { onDone(null) }) { Text("Cancelar") } })
}
