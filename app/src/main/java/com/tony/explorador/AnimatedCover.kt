package com.tony.explorador

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale

/**
 * Carátulas animadas (videos cortos en loop, sin sonido) que pone el usuario.
 * Se buscan en este orden:
 *  1. La que elegiste para esa canción en la app.
 *  2. La que elegiste para todo el álbum.
 *  3. Un video con el mismo nombre junto a la canción: Canción.mp4
 *  4. Un video en la carpeta del álbum: cover.mp4, portada.mp4, animated.mp4 o folder.mp4
 */
object AnimatedCovers {
    private val VIDEO_EXT = listOf("mp4", "m4v", "webm", "mov")
    private val FOLDER_NAMES = listOf("cover", "portada", "animated", "animada", "folder")
    private lateinit var dir: File
    var version by mutableIntStateOf(0)
        private set

    fun init(context: Context) { dir = File(context.filesDir, "animadas").apply { mkdirs() } }

    private fun key(s: String): String =
        MessageDigest.getInstance("MD5").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }.take(20)

    private fun songFile(path: String) = File(dir, "s_${key(path)}.mp4")
    fun albumFile(album: String) = File(dir, "a_${key(album.lowercase(Locale.ROOT))}.mp4")

    fun find(path: String, album: String?): File? {
        if (!::dir.isInitialized) return null
        val audio = File(path)
        if (audio.kind() == FileKind.VIDEO) return null
        songFile(path).takeIf { it.isFile }?.let { return it }
        album?.takeIf { it.isNotBlank() && it != UNKNOWN_ALBUM }?.let { a -> albumFile(a).takeIf { it.isFile }?.let { return it } }
        val folder = audio.parentFile ?: return null
        VIDEO_EXT.forEach { ext -> File(folder, "${audio.nameWithoutExtension}.$ext").takeIf { it.isFile }?.let { return it } }
        FOLDER_NAMES.forEach { n -> VIDEO_EXT.forEach { ext -> File(folder, "$n.$ext").takeIf { it.isFile }?.let { return it } } }
        return null
    }

    /** Si la que aplica es una que guardó la app (y por eso se puede quitar). */
    fun isOwn(file: File?): Boolean = file != null && ::dir.isInitialized && file.parentFile == dir

    private fun target(path: String, album: String?, forAlbum: Boolean): File =
        if (forAlbum && !album.isNullOrBlank() && album != UNKNOWN_ALBUM) albumFile(album) else songFile(path)

    suspend fun importFrom(context: Context, uri: Uri, path: String, album: String?, forAlbum: Boolean): Boolean = withContext(Dispatchers.IO) {
        val out = target(path, album, forAlbum)
        val ok = runCatching {
            context.contentResolver.openInputStream(uri)!!.use { input -> out.outputStream().use { input.copyTo(it) } }
            out.length() > 0
        }.getOrDefault(false)
        if (!ok) out.delete()
        withContext(Dispatchers.Main) { version++ }
        ok
    }

    suspend fun download(url: String, path: String, album: String?, forAlbum: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        val out = target(path, album, forAlbum)
        val tmp = File(out.parentFile, out.name + ".part")
        val r = runCatching {
            val conn = (URL(url.trim()).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000; readTimeout = 30_000
                setRequestProperty("User-Agent", "ExploradorTony")
                instanceFollowRedirects = true
            }
            try {
                if (conn.responseCode !in 200..299) error("El enlace respondió ${conn.responseCode}")
                val type = conn.contentType.orEmpty()
                if (type.contains("mpegurl", true) || url.contains(".m3u8", true))
                    error("Ese enlace es de streaming (.m3u8). Necesito un enlace directo a un video .mp4")
                if (type.startsWith("text/", true)) error("El enlace no es un video")
                conn.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            } finally { conn.disconnect() }
            if (tmp.length() < 10_000) error("El archivo descargado es muy chico para ser video")
            out.delete(); tmp.renameTo(out)
            Unit
        }
        if (r.isFailure) tmp.delete()
        withContext(Dispatchers.Main) { version++ }
        r
    }

    fun bump() { version++ }

    fun remove(file: File) {
        if (isOwn(file)) file.delete()
        version++
    }
}

@Composable
fun rememberAnimatedCover(path: String?, album: String?): State<File?> =
    produceState<File?>(null, path, album, AnimatedCovers.version) {
        value = if (path == null) null else withContext(Dispatchers.IO) { AnimatedCovers.find(path, album) }
    }

/** La carátula animada: en loop, sin sonido; se pausa cuando la música se pausa. */
@Composable
fun AnimatedCoverView(file: File, playing: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val player = remember(file) {
        ExoPlayer.Builder(context).build().apply {
            // que nunca le quite el sonido a la música
            trackSelectionParameters = trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true).build()
            volume = 0f
            repeatMode = Player.REPEAT_MODE_ONE
            setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    LaunchedEffect(player, playing) { player.playWhenReady = playing }
    VideoView(player, modifier, crop = true, background = Color.Transparent)
}

/** Ventanita para poner o quitar la carátula animada de una canción. */
@Composable
fun AnimatedCoverDialog(path: String, album: String?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val current by rememberAnimatedCover(path, album)
    var forAlbum by remember { mutableStateOf(false) }
    var linkMode by remember { mutableStateOf(false) }
    var link by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            busy = true
            scope.launch {
                val ok = AnimatedCovers.importFrom(context, uri, path, album, forAlbum)
                busy = false
                Toast.makeText(context, if (ok) "Carátula animada lista ✔" else "No se pudo copiar el video", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val buttons = buildList {
        if (linkMode) {
            add(DialogButton("Cancelar") { linkMode = false })
            add(DialogButton("Descargar", true) {
                if (link.isBlank() || busy) return@DialogButton
                busy = true
                scope.launch {
                    val r = AnimatedCovers.download(link, path, album, forAlbum)
                    busy = false
                    if (r.isSuccess) { linkMode = false; link = "" }
                    Toast.makeText(context, if (r.isSuccess) "Carátula animada lista ✔"
                        else (r.exceptionOrNull()?.message ?: "No se pudo descargar"), Toast.LENGTH_LONG).show()
                }
            })
        } else add(DialogButton("Cerrar", true, onDismiss))
    }

    ThemedDialog("Carátula animada", onDismiss, buttons) {
        Column {
            val cur = current
            Box(
                Modifier.fillMaxWidth(0.6f).aspectRatio(1f).align(Alignment.CenterHorizontally)
                    .clip(RoundedCornerShape(14.dp)).then(Modifier),
                contentAlignment = Alignment.Center
            ) {
                when {
                    busy -> CircularProgressIndicator()
                    cur != null -> AnimatedCoverView(cur, playing = true, modifier = Modifier.fillMaxSize())
                    else -> Text("Sin carátula animada", fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
            if (cur != null) {
                Text(if (AnimatedCovers.isOwn(cur)) "Puesta desde la app" else "Encontrada junto a la canción: ${cur.name}", fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
            }
            Row(Modifier.fillMaxWidth().clickable { forAlbum = !forAlbum }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(forAlbum, { forAlbum = it })
                Text("Usarla para todo el álbum${album?.takeIf { it != UNKNOWN_ALBUM }?.let { " «$it»" } ?: ""}", fontSize = 14.sp)
            }
            if (linkMode) {
                OutlinedTextField(link, { link = it }, label = { Text("Enlace directo al video (.mp4)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
            } else {
                @Composable
                fun Opt(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(enabled = !busy, onClick = onClick)
                        .padding(vertical = 11.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, null, modifier = Modifier.size(22.dp)); Spacer(Modifier.width(12.dp))
                        Text(label, fontWeight = FontWeight.Bold)
                    }
                }
                Opt(Icons.Filled.VideoLibrary, "Elegir un video del teléfono") { picker.launch("video/*") }
                Opt(Icons.Filled.Link, "Descargar desde un enlace") { linkMode = true }
                if (cur != null && AnimatedCovers.isOwn(cur)) Opt(Icons.Filled.Delete, "Quitar carátula animada") { AnimatedCovers.remove(cur) }
            }
            Spacer(Modifier.height(6.dp))
            Text("Tip: también funciona si pones «Canción.mp4» junto a la canción, o «cover.mp4» en la carpeta del álbum.",
                fontSize = 11.sp)
        }
    }
}

/**
 * Búsqueda automática de carátulas animadas EN TU TELÉFONO: revisa Descargas, Películas, tus carpetas de música
 * y cualquier carpeta llamada «Carátulas animadas», y si un video se llama como un álbum
 * (por ejemplo «Coldplay - Ghost Stories.mp4», como los guardan las páginas que las descargan) se lo pone.
 */
object AnimatedScanner {
    private lateinit var app: Context
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)
    var auto by mutableStateOf(false)
        private set
    var running by mutableStateOf(false)
        private set
    var status by mutableStateOf("")
        private set
    /** álbum → video que se le puso (lo último encontrado) */
    val assigned = androidx.compose.runtime.mutableStateListOf<Pair<String, String>>()
    private val prefs get() = File(app.filesDir, "animadas.json")

    fun init(context: Context) {
        app = context.applicationContext
        runCatching { auto = org.json.JSONObject(prefs.readText()).optBoolean("auto", false) }
    }

    fun changeAuto(on: Boolean) {
        auto = on
        runCatching { prefs.writeText(org.json.JSONObject().put("auto", on).toString()) }
        if (on) start()
    }

    private fun roots(): List<File> {
        val base = android.os.Environment.getExternalStorageDirectory()
        val list = mutableListOf(
            File(base, android.os.Environment.DIRECTORY_DOWNLOADS),
            File(base, android.os.Environment.DIRECTORY_MOVIES),
            File(base, "Carátulas animadas"),
            File(base, "${android.os.Environment.DIRECTORY_MUSIC}/Carátulas animadas"),
        )
        LibraryStore.musicFolders.forEach { list.add(File(it)) }
        return list.filter { it.isDirectory }.distinctBy { it.absolutePath }
    }

    fun start() {
        if (running) return
        scope.launch {
            running = true
            status = "Buscando videos en tu teléfono…"
            val videos = roots().flatMap { r ->
                r.walkTopDown().maxDepth(4)
                    .filter { it.isFile && it.kind() == FileKind.VIDEO && it.length() in 50_000L..80_000_000L }
                    .take(3000).toList()
            }.distinctBy { it.absolutePath }
            val songs = withContext(Dispatchers.Main) { LibraryStore.songs.toList() }
            val albums = songs.filter { it.album != UNKNOWN_ALBUM }
                .groupBy { it.album }
                .mapValues { (_, l) -> l.first().artist }
            var n = 0
            albums.forEach { (album, artist) ->
                if (AnimatedCovers.albumFile(album).isFile) return@forEach
                val name = CoverScanner.norm(album)
                if (name.length < 2) return@forEach
                val match = videos.firstOrNull { v ->
                    val vn = CoverScanner.norm(v.nameWithoutExtension)
                    // el nombre del video debe traer el álbum completo y, si se sabe, el artista
                    (" $vn ").contains(" $name ") &&
                        (artist == UNKNOWN_ARTIST || CoverScanner.norm(artist).split(' ').filter { it.length > 2 }
                            .let { parts -> parts.isEmpty() || parts.any { vn.contains(it) } })
                } ?: return@forEach
                val ok = runCatching { match.copyTo(AnimatedCovers.albumFile(album), overwrite = true); true }.getOrDefault(false)
                if (ok) {
                    n++
                    withContext(Dispatchers.Main) { assigned.add(0, album to match.name) }
                }
            }
            withContext(Dispatchers.Main) {
                AnimatedCovers.bump()
                running = false
                status = if (n > 0) "Listo: puse $n carátulas animadas" else "Listo: no encontré videos nuevos que coincidan (${videos.size} revisados)"
                if (n > 0) Toast.makeText(app, "Puse $n carátulas animadas ✨", Toast.LENGTH_LONG).show()
            }
        }
    }
}
