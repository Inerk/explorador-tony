package com.tony.explorador

import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.UUID

data class Song(
    val path: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
) {
    val file: File get() = File(path)
}

data class Playlist(val id: String, val name: String, val paths: List<String>)

data class SavedPlayback(val paths: List<String>, val index: Int, val position: Long, val shuffle: Boolean, val repeat: Int)

const val UNKNOWN_ARTIST = "Artista desconocido"
const val UNKNOWN_ALBUM = "Álbum desconocido"

/**
 * Todo lo que la app recuerda de la música: canciones encontradas, carpetas a escanear,
 * favoritas, veces reproducidas y listas. Se guarda en archivos JSON dentro de la app.
 */
object LibraryStore {
    private lateinit var appContext: Context
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    var songs by mutableStateOf<List<Song>>(emptyList())
        private set
    var scanning by mutableStateOf(false)
        private set
    var playlistFiles by mutableStateOf<List<File>>(emptyList())
        private set
    /** Sube cada vez que cambian etiquetas o letras, para que las pantallas se recarguen. */
    var tagsVersion by mutableStateOf(0)
        private set

    fun bumpTags() { tagsVersion++ }

    /** Actualiza los datos de una canción después de etiquetarla. */
    fun updateSongTags(path: String, title: String?, artist: String?, album: String?) {
        songs = songs.map { s ->
            if (s.path != path) s else s.copy(
                title = title?.takeIf { it.isNotBlank() } ?: s.title,
                artist = artist?.takeIf { it.isNotBlank() } ?: s.artist,
                album = album?.takeIf { it.isNotBlank() } ?: s.album,
            )
        }
        val list = songs
        io.launch { runCatching { saveSongsCache(list) } }
        tagsVersion++
    }

    val musicFolders = mutableStateListOf<String>()
    val favorites = mutableStateMapOf<String, Long>()       // ruta → cuándo se marcó
    val playCounts = mutableStateMapOf<String, Int>()       // ruta → veces
    val lastPlayed = mutableStateMapOf<String, Long>()      // ruta → última vez
    val playlists = mutableStateListOf<Playlist>()

    var theme by mutableStateOf(AppTheme.XP)
        private set
    var userName by mutableStateOf("Tony")
        private set
    /** Imagen de usuario: 0..n = prediseñada, -1 = foto propia. */
    var avatar by mutableStateOf(0)
        private set
    var avatarVersion by mutableStateOf(0)
        private set
    var musicSkin by mutableStateOf(MusicSkin.WALKMAN)
        private set

    val avatarFile get() = File(appContext.filesDir, "avatar.jpg")
    val wallpaperFile get() = File(appContext.filesDir, "fondo.jpg")
    var wallpaper by mutableStateOf("nieve")
        private set
    var wallpaperVersion by mutableStateOf(0)
        private set
    fun changeWallpaper(id: String) { wallpaper = id; wallpaperVersion++; save() }

    fun changeUserName(name: String) { userName = name; save() }
    fun changeAvatar(index: Int) { avatar = index; avatarVersion++; save() }
    fun changeMusicSkin(s: MusicSkin) {
        musicSkin = s
        // el tema Vintage también viste la ventanita del escritorio
        if (s == MusicSkin.VINTAGE) windowSkin = WindowSkin.VINTAGE
        else if (windowSkin == WindowSkin.VINTAGE) windowSkin = WindowSkin.MEDIA
        save()
    }
    var windowSkin by mutableStateOf(WindowSkin.MEDIA)
        private set
    fun changeWindowSkin(s: WindowSkin) { windowSkin = s; save() }

    // ───── Vistas del explorador, galería y videos ─────
    /** "list", "compact", "grid", "big" */
    var explorerView by mutableStateOf("list")
        private set
    fun changeExplorerView(v: String) { explorerView = v; save() }
    /** "auto" (según el teléfono), "light" o "dark". */
    var darkMode by mutableStateOf("auto")
        private set
    fun changeDarkMode(v: String) { darkMode = v; save() }
    /** Cómo se ven las listas de canciones: "compact", "cards" o "grid". */
    var songView by mutableStateOf("compact")
        private set
    fun changeSongView(v: String) { songView = v; save() }

    /** Estilo de la barra de avance del reproductor: wave, galaxy, eq, neon, line. */
    var seekStyle by mutableStateOf("galaxy")
        private set
    fun changeSeekStyle(v: String) { seekStyle = v; save() }
    /** "grid" o "list" */
    var galleryView by mutableStateOf("grid")
        private set
    var videoView by mutableStateOf("grid")
        private set
    /** Carpetas elegidas (vacío = todas las del teléfono). */
    val galleryFolders = mutableStateListOf<String>()
    val videoFolders = mutableStateListOf<String>()
    fun changeMediaView(videos: Boolean, v: String) { if (videos) videoView = v else galleryView = v; save() }
    fun setMediaFolders(videos: Boolean, folders: Collection<String>) {
        val l = if (videos) videoFolders else galleryFolders
        l.clear(); l.addAll(folders.distinct()); save()
    }

    // ───── Escritorio ─────
    val desktopIds = mutableStateListOf<String>()           // íconos visibles
    val desktopPos = mutableStateMapOf<String, String>()    // id → "columna,fila"
    val desktopShortcuts = mutableStateListOf<String>()     // los que se agregaron como acceso directo
    private var desktopConfigured = false

    fun ensureDesktopDefaults(defaults: List<String>) {
        if (!desktopConfigured) {
            desktopIds.clear(); desktopIds.addAll(defaults)
            desktopConfigured = true
            save()
        }
    }
    fun setDesktopVisible(id: String, visible: Boolean) {
        if (visible && id !in desktopIds) desktopIds.add(id)
        if (!visible) { desktopIds.remove(id); desktopPos.remove(id); desktopShortcuts.remove(id) }
        save()
    }
    /** Acceso directo en el escritorio a un archivo o carpeta. */
    fun addFileShortcut(path: String) = addDesktopShortcut("file:$path")
    fun addDesktopShortcut(id: String) {
        if (id !in desktopIds) { desktopIds.add(id); if (id !in desktopShortcuts) desktopShortcuts.add(id) }
        save()
    }
    fun moveDesktopIcon(id: String, col: Int, row: Int) {
        val target = "$col,$row"
        val other = desktopPos.entries.firstOrNull { it.value == target && it.key != id }?.key
        val old = desktopPos[id]
        if (other != null) { if (old != null) desktopPos[other] = old else desktopPos.remove(other) }
        desktopPos[id] = target
        save()
    }
    fun resetDesktopPositions() { desktopPos.clear(); save() }

    private val dataFile get() = File(appContext.filesDir, "biblioteca.json")
    private val songsCache get() = File(appContext.filesDir, "canciones.json")
    private var loaded = false

    fun init(context: Context) {
        if (loaded) return
        appContext = context.applicationContext
        loaded = true
        runCatching { load() }
        runCatching { loadSongsCache() }
        if (musicFolders.isEmpty()) {
            val root = Environment.getExternalStorageDirectory()
            musicFolders.add(File(root, Environment.DIRECTORY_MUSIC).absolutePath)
            musicFolders.add(File(root, Environment.DIRECTORY_DOWNLOADS).absolutePath)
        }
    }

    // ───── Favoritas, conteos y listas ─────

    fun isFavorite(path: String) = favorites.containsKey(path)

    fun toggleFavorite(path: String) {
        if (favorites.containsKey(path)) favorites.remove(path) else favorites[path] = System.currentTimeMillis()
        save()
    }

    // Parejas: la misma canción en audio y en video
    private var pairsFor: List<Song>? = null
    private var pairs: Map<String, String> = emptyMap()

    /** Si esta canción tiene su gemela (el video de un audio, o el audio de un video), regresa su ruta. */
    fun pairFor(path: String): String? {
        val list = songs
        if (pairsFor !== list) {
            fun key(s: Song) = CoverScanner.norm(s.title)
            val videos = list.filter { isVideoPath(it.path) }.groupBy { key(it) }
            val m = HashMap<String, String>()
            list.forEach { a ->
                if (isVideoPath(a.path)) return@forEach
                val k = key(a)
                if (k.length < 2) return@forEach
                val v = videos[k]?.firstOrNull { v ->
                    a.artist == UNKNOWN_ARTIST || v.artist == UNKNOWN_ARTIST || CoverScanner.similar(a.artist, v.artist)
                } ?: return@forEach
                if (a.path !in m && v.path !in m) { m[a.path] = v.path; m[v.path] = a.path }
            }
            pairs = m; pairsFor = list
        }
        return pairs[path]
    }

    /** Dónde se ve la letra: "below" debajo, "big" debajo y grande, "over" encima de la portada, "float" ventana flotante. */
    var lyricsPlace by mutableStateOf("below")
        private set
    fun changeLyricsPlace(v: String) { lyricsPlace = v; save() }
    val lyricsOverCover: Boolean get() = lyricsPlace == "over"

    /** Borra cuántas veces y cuándo se escuchó una canción. */
    fun resetStats(path: String) {
        playCounts.remove(path); lastPlayed.remove(path); save()
    }

    fun recordPlay(path: String) {
        playCounts[path] = (playCounts[path] ?: 0) + 1
        lastPlayed[path] = System.currentTimeMillis()
        save()
    }

    fun createPlaylist(name: String, paths: List<String> = emptyList()): Playlist {
        val p = Playlist(UUID.randomUUID().toString(), name, paths)
        playlists.add(p)
        save()
        return p
    }

    fun renamePlaylist(id: String, name: String) = updatePlaylist(id) { it.copy(name = name) }
    fun deletePlaylist(id: String) { playlists.removeAll { it.id == id }; save() }

    fun addToPlaylist(id: String, paths: List<String>) =
        updatePlaylist(id) { p -> p.copy(paths = p.paths + paths.filter { it !in p.paths }) }

    fun removeFromPlaylist(id: String, path: String) =
        updatePlaylist(id) { p -> p.copy(paths = p.paths - path) }

    private fun updatePlaylist(id: String, change: (Playlist) -> Playlist) {
        val i = playlists.indexOfFirst { it.id == id }
        if (i >= 0) { playlists[i] = change(playlists[i]); save() }
    }

    fun changeTheme(t: AppTheme) { theme = t; save() }

    fun addFolder(path: String) { if (path !in musicFolders) { musicFolders.add(path); save() } }
    fun removeFolder(path: String) { musicFolders.remove(path); save() }

    // ───── Escaneo ─────

    fun songFor(path: String): Song? = songs.firstOrNull { it.path == path }

    /** Quita una canción borrada de la biblioteca, favoritas y listas. */
    /** Un archivo cambió de nombre: se actualiza en la biblioteca, favoritas, conteos y listas. */
    fun renameSong(old: String, new: String) {
        songs = songs.map { if (it.path == old) it.copy(path = new) else it }
        favorites.remove(old)?.let { favorites[new] = it }
        playCounts.remove(old)?.let { playCounts[new] = it }
        lastPlayed.remove(old)?.let { lastPlayed[new] = it }
        for (i in playlists.indices) {
            val p = playlists[i]
            if (old in p.paths) playlists[i] = p.copy(paths = p.paths.map { if (it == old) new else it })
        }
        val list = songs
        io.launch { runCatching { saveSongsCache(list) } }
        save()
        tagsVersion++
    }

    fun removeSong(path: String) {
        songs = songs.filter { it.path != path }
        favorites.remove(path)
        for (i in playlists.indices) {
            val p = playlists[i]
            if (path in p.paths) playlists[i] = p.copy(paths = p.paths - path)
        }
        save()
    }

    suspend fun scan() {
        if (scanning) return
        scanning = true
        try {
            val folders = musicFolders.toList()
            val (found, lists) = withContext(Dispatchers.IO) { scanFolders(folders) }
            songs = found
            playlistFiles = lists
            withContext(Dispatchers.IO) { runCatching { saveSongsCache(found) } }
        } finally {
            scanning = false
        }
    }

    private fun scanFolders(folders: List<String>): Pair<List<Song>, List<File>> {
        val prefixes = folders.map { it.trimEnd('/') + "/" }
        val byPath = LinkedHashMap<String, Song>()

        // 1) Lo que Android ya tiene indexado (rápido y con etiquetas)
        val projection = arrayOf(
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
        )
        runCatching {
            appContext.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, null, null, null
            )?.use { c ->
                val iData = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val iTitle = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val iArtist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val iAlbum = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val iDur = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                while (c.moveToNext()) {
                    val path = c.getString(iData) ?: continue
                    if (prefixes.none { path.startsWith(it) }) continue
                    val f = File(path)
                    if (!f.isFile || f.kind() != FileKind.AUDIO) continue
                    byPath[path] = Song(
                        path = path,
                        title = c.getString(iTitle)?.takeIf { it.isNotBlank() } ?: f.nameWithoutExtension,
                        artist = cleanTag(c.getString(iArtist)) ?: UNKNOWN_ARTIST,
                        album = cleanTag(c.getString(iAlbum)) ?: UNKNOWN_ALBUM,
                        durationMs = c.getLong(iDur),
                    )
                }
            }
        }

        // 1b) Videos musicales que estén dentro de las carpetas de música
        runCatching {
            appContext.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Video.Media.DATA, MediaStore.Video.Media.TITLE, MediaStore.Video.Media.ARTIST,
                    MediaStore.Video.Media.ALBUM, MediaStore.Video.Media.DURATION), null, null, null
            )?.use { c ->
                while (c.moveToNext()) {
                    val path = c.getString(0) ?: continue
                    if (prefixes.none { path.startsWith(it) }) continue
                    val f = File(path)
                    if (!f.isFile || f.kind() != FileKind.VIDEO || f.name.contains(".animada.", true) || f.parentFile?.name == ".animadas") continue
                    byPath[path] = Song(
                        path = path,
                        title = c.getString(1)?.takeIf { it.isNotBlank() } ?: f.nameWithoutExtension,
                        artist = cleanTag(c.getString(2)) ?: UNKNOWN_ARTIST,
                        album = cleanTag(c.getString(3)) ?: UNKNOWN_ALBUM,
                        durationMs = c.getLong(4),
                    )
                }
            }
        }

        // 2) Recorrer las carpetas para encontrar lo que Android no indexó, y las listas .m3u8
        val lists = mutableListOf<File>()
        for (folder in folders) {
            val root = File(folder)
            if (!root.isDirectory) continue
            root.walkTopDown().maxDepth(12).forEach { f ->
                if (!f.isFile) return@forEach
                val ext = f.extension.lowercase(Locale.ROOT)
                if (ext == "m3u" || ext == "m3u8") lists.add(f)
                else if ((f.kind() == FileKind.AUDIO || f.kind() == FileKind.VIDEO) && !byPath.containsKey(f.absolutePath) &&
                    !f.name.contains(".animada.", true) && f.parentFile?.name != ".animadas") {
                    byPath[f.absolutePath] = Song(f.absolutePath, f.nameWithoutExtension, UNKNOWN_ARTIST, UNKNOWN_ALBUM, 0L)
                }
            }
        }
        val sorted = byPath.values.map { TagOverrides.apply(it) }.sortedBy { it.title.lowercase(Locale.ROOT) }
        return sorted to lists.distinctBy { it.absolutePath }
    }

    private fun cleanTag(s: String?): String? =
        s?.trim()?.takeIf { it.isNotEmpty() && it != "<unknown>" }

    // ───── Listas M3U / M3U8 ─────

    /** Lee una lista .m3u8 y devuelve las rutas que sí existen en el teléfono. */
    suspend fun readM3u(file: File): List<String> = withContext(Dispatchers.IO) {
        val text = readLrcText(file) // mismo truco de codificación que las letras
        val base = file.parentFile
        val byName = songs.groupBy { it.file.name.lowercase(Locale.ROOT) }
        text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .mapNotNull { entry ->
                val clean = entry.removePrefix("file://").replace('\\', '/')
                val direct = File(clean)
                val relative = File(base, clean)
                when {
                    direct.isAbsolute && direct.isFile -> direct.absolutePath
                    relative.isFile -> relative.canonicalPath
                    else -> {
                        // Listas hechas en la PC (C:\Música\...): buscar por nombre de archivo
                        val name = clean.substringAfterLast('/').lowercase(Locale.ROOT)
                        byName[name]?.firstOrNull()?.path
                    }
                }
            }
            .distinct()
            .toList()
    }

    suspend fun importM3u(file: File): Pair<Playlist, Int> {
        val paths = readM3u(file)
        val p = createPlaylist(file.nameWithoutExtension, paths)
        return p to paths.size
    }

    suspend fun exportM3u(playlist: Playlist): File = withContext(Dispatchers.IO) {
        val dir = File(Environment.getExternalStorageDirectory(), "${Environment.DIRECTORY_MUSIC}/Listas").apply { mkdirs() }
        val safe = playlist.name.replace(Regex("""[\\/:*?"<>|]"""), "_")
        val out = File(dir, "$safe.m3u8")
        val sb = StringBuilder("#EXTM3U\n")
        playlist.paths.forEach { path ->
            val s = songFor(path)
            if (s != null) sb.append("#EXTINF:${s.durationMs / 1000},${s.artist} - ${s.title}\n")
            sb.append(path).append('\n')
        }
        out.writeText(sb.toString())
        out
    }

    // ───── Lo último que sonaba ─────

    private val playbackFile get() = File(appContext.filesDir, "reproduccion.json")

    fun savePlayback(p: SavedPlayback) {
        if (!loaded) return
        val json = JSONObject()
            .put("paths", JSONArray(p.paths))
            .put("index", p.index)
            .put("position", p.position)
            .put("shuffle", p.shuffle)
            .put("repeat", p.repeat)
            .toString()
        io.launch { runCatching { playbackFile.writeText(json) } }
    }

    fun clearPlayback() { if (loaded) io.launch { runCatching { playbackFile.delete() } } }

    fun loadPlayback(): SavedPlayback? = runCatching {
        if (!playbackFile.isFile) return@runCatching null
        val o = JSONObject(playbackFile.readText())
        val arr = o.getJSONArray("paths")
        SavedPlayback(
            paths = (0 until arr.length()).map { arr.getString(it) },
            index = o.optInt("index"),
            position = o.optLong("position"),
            shuffle = o.optBoolean("shuffle"),
            repeat = o.optInt("repeat"),
        )
    }.getOrNull()

    // ───── Guardar / cargar ─────

    private fun save() {
        if (!loaded) return
        val json = JSONObject().apply {
            put("folders", JSONArray(musicFolders.toList()))
            put("favorites", JSONObject(favorites.toMap()))
            put("counts", JSONObject(playCounts.toMap()))
            put("last", JSONObject(lastPlayed.toMap()))
            put("theme", theme.name)
            put("user", userName)
            put("avatar", avatar)
            put("musicSkin", musicSkin.name)
            put("windowSkin", windowSkin.name)
            put("wallpaper", wallpaper)
            put("explorerView", explorerView)
            put("darkMode", darkMode)
            put("lyricsPlace", lyricsPlace)
            put("songView", songView)
            put("seekStyle", seekStyle)
            put("galleryView", galleryView)
            put("videoView", videoView)
            put("galleryFolders", JSONArray(galleryFolders.toList()))
            put("videoFolders", JSONArray(videoFolders.toList()))
            put("desktopConfigured", desktopConfigured)
            put("desktopIds", JSONArray(desktopIds.toList()))
            put("desktopPos", JSONObject(desktopPos.toMap()))
            put("desktopShortcuts", JSONArray(desktopShortcuts.toList()))
            put("playlists", JSONArray().apply {
                playlists.forEach { p ->
                    put(JSONObject().put("id", p.id).put("name", p.name).put("paths", JSONArray(p.paths)))
                }
            })
        }.toString()
        io.launch { runCatching { dataFile.writeText(json) } }
    }

    private fun load() {
        if (!dataFile.isFile) return
        val o = JSONObject(dataFile.readText())
        o.optJSONArray("folders")?.let { a -> for (i in 0 until a.length()) musicFolders.add(a.getString(i)) }
        o.optJSONObject("favorites")?.let { m -> m.keys().forEach { k -> favorites[k] = m.getLong(k) } }
        o.optJSONObject("counts")?.let { m -> m.keys().forEach { k -> playCounts[k] = m.getInt(k) } }
        o.optJSONObject("last")?.let { m -> m.keys().forEach { k -> lastPlayed[k] = m.getLong(k) } }
        // por ahora el único tema es el clásico XP (versión beta)
        theme = AppTheme.XP
        o.optString("user").takeIf { it.isNotBlank() }?.let { userName = it }
        avatar = o.optInt("avatar", 0)
        o.optString("musicSkin").let { t -> MusicSkin.entries.firstOrNull { it.name == t }?.let { musicSkin = it } }
        o.optString("windowSkin").let { t -> WindowSkin.entries.firstOrNull { it.name == t }?.let { windowSkin = it } }
        o.optString("wallpaper").takeIf { it.isNotBlank() }?.let { wallpaper = it }
        o.optString("explorerView").takeIf { it.isNotBlank() }?.let { explorerView = it }
        o.optString("darkMode").takeIf { it.isNotBlank() }?.let { darkMode = it }
        lyricsPlace = o.optString("lyricsPlace", if (o.optBoolean("lyricsOverCover", false)) "over" else "below")
        o.optString("songView").takeIf { it.isNotBlank() }?.let { songView = it }
        o.optString("seekStyle").takeIf { it.isNotBlank() }?.let { seekStyle = it }
        o.optString("galleryView").takeIf { it.isNotBlank() }?.let { galleryView = it }
        o.optString("videoView").takeIf { it.isNotBlank() }?.let { videoView = it }
        o.optJSONArray("galleryFolders")?.let { a -> for (i in 0 until a.length()) galleryFolders.add(a.getString(i)) }
        o.optJSONArray("videoFolders")?.let { a -> for (i in 0 until a.length()) videoFolders.add(a.getString(i)) }
        desktopConfigured = o.optBoolean("desktopConfigured", false)
        o.optJSONArray("desktopIds")?.let { a -> for (i in 0 until a.length()) desktopIds.add(a.getString(i)) }
        o.optJSONObject("desktopPos")?.let { m -> m.keys().forEach { k -> desktopPos[k] = m.getString(k) } }
        o.optJSONArray("desktopShortcuts")?.let { a -> for (i in 0 until a.length()) desktopShortcuts.add(a.getString(i)) }
        o.optJSONArray("playlists")?.let { a ->
            for (i in 0 until a.length()) {
                val p = a.getJSONObject(i)
                val paths = p.getJSONArray("paths").let { arr -> (0 until arr.length()).map { arr.getString(it) } }
                playlists.add(Playlist(p.getString("id"), p.getString("name"), paths))
            }
        }
    }

    private fun saveSongsCache(list: List<Song>) {
        val a = JSONArray()
        list.forEach { s ->
            a.put(JSONObject().put("p", s.path).put("t", s.title).put("a", s.artist).put("l", s.album).put("d", s.durationMs))
        }
        songsCache.writeText(a.toString())
    }

    private fun loadSongsCache() {
        if (!songsCache.isFile) return
        val a = JSONArray(songsCache.readText())
        songs = (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            Song(o.getString("p"), o.getString("t"), o.getString("a"), o.getString("l"), o.optLong("d"))
        }
    }
}
