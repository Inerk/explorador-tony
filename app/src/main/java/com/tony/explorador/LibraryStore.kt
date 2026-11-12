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

    val musicFolders = mutableStateListOf<String>()
    val favorites = mutableStateMapOf<String, Long>()       // ruta → cuándo se marcó
    val playCounts = mutableStateMapOf<String, Int>()       // ruta → veces
    val lastPlayed = mutableStateMapOf<String, Long>()      // ruta → última vez
    val playlists = mutableStateListOf<Playlist>()

    var theme by mutableStateOf(AppTheme.XP)
        private set

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

        // 2) Recorrer las carpetas para encontrar lo que Android no indexó, y las listas .m3u8
        val lists = mutableListOf<File>()
        for (folder in folders) {
            val root = File(folder)
            if (!root.isDirectory) continue
            root.walkTopDown().maxDepth(12).forEach { f ->
                if (!f.isFile) return@forEach
                val ext = f.extension.lowercase(Locale.ROOT)
                if (ext == "m3u" || ext == "m3u8") lists.add(f)
                else if (f.kind() == FileKind.AUDIO && !byPath.containsKey(f.absolutePath)) {
                    byPath[f.absolutePath] = Song(f.absolutePath, f.nameWithoutExtension, UNKNOWN_ARTIST, UNKNOWN_ALBUM, 0L)
                }
            }
        }
        val sorted = byPath.values.sortedBy { it.title.lowercase(Locale.ROOT) }
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

    // ───── Guardar / cargar ─────

    private fun save() {
        if (!loaded) return
        val json = JSONObject().apply {
            put("folders", JSONArray(musicFolders.toList()))
            put("favorites", JSONObject(favorites.toMap()))
            put("counts", JSONObject(playCounts.toMap()))
            put("last", JSONObject(lastPlayed.toMap()))
            put("theme", theme.name)
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
        o.optString("theme").let { t -> AppTheme.entries.firstOrNull { it.name == t }?.let { theme = it } }
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
