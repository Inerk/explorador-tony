package com.tony.explorador

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * Letras (.lrc) que tengas sueltas en el teléfono (por ejemplo en Descargas):
 * las encuentra, ve de qué canción son y las MUEVE junto a la canción con su mismo nombre.
 * Si la canción ya tenía la misma letra, la copia sobrante se manda a la Papelera (sin duplicados).
 */
object LyricsLocal {
    private lateinit var app: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    var auto by mutableStateOf(false)
        private set
    var running by mutableStateOf(false)
        private set
    var status by mutableStateOf("")
        private set
    var duplicates by mutableIntStateOf(0)
        private set
    /** canción ← archivo, lo último que se acomodó */
    val moved = mutableStateListOf<Pair<String, String>>()
    private val prefs get() = app.getSharedPreferences("letras", Context.MODE_PRIVATE)

    fun init(context: Context) {
        app = context.applicationContext
        auto = prefs.getBoolean("auto", false)
    }

    fun changeAuto(on: Boolean) {
        auto = on
        prefs.edit().putBoolean("auto", on).apply()
        if (on) start()
    }

    /** ¿Tiene tiempos (se mueve al ritmo) o es sólo texto? */
    private fun isSynced(text: String) = Regex("""\[\d{1,2}:\d{2}""").containsMatchIn(text)

    private fun tag(text: String, name: String) =
        Regex("""^\[$name:(.*)]\s*$""", setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE)).find(text)?.groupValues?.get(1)?.trim().orEmpty()

    /**
     * Pone una letra junto a su canción.
     * Regresa: "moved" (se acomodó), "dup" (ya estaba igual y se quitó la copia), "kept" (la canción ya tenía otra letra), "fail".
     */
    private suspend fun place(lrc: File, songPath: String): String {
        val dest = File(File(songPath).parentFile, File(songPath).nameWithoutExtension + ".lrc")
        if (dest.absolutePath == lrc.absolutePath) return "kept"
        val newText = runCatching { readLrcText(lrc) }.getOrDefault("")
        if (dest.exists()) {
            val oldText = runCatching { readLrcText(dest) }.getOrDefault("")
            return when {
                // la misma letra: la copia sobra
                oldText.trim() == newText.trim() -> { Trash.moveToTrash(listOf(lrc)); "dup" }
                // la nueva tiene tiempos y la que había no: se queda la nueva
                isSynced(newText) && !isSynced(oldText) -> {
                    Trash.moveToTrash(listOf(dest))
                    if (lrc.renameTo(dest) || runCatching { lrc.copyTo(dest); lrc.delete(); true }.getOrDefault(false)) "moved" else "fail"
                }
                else -> "kept"
            }
        }
        val ok = lrc.renameTo(dest) || runCatching { lrc.copyTo(dest); lrc.delete(); true }.getOrDefault(false)
        if (ok) MediaScannerConnection.scanFile(app, arrayOf(lrc.absolutePath, dest.absolutePath), null, null)
        return if (ok) "moved" else "fail"
    }

    private fun roots(): List<File> {
        val base = Environment.getExternalStorageDirectory()
        val list = mutableListOf(
            File(base, Environment.DIRECTORY_DOWNLOADS), File(base, Environment.DIRECTORY_DOCUMENTS),
            File(base, Environment.DIRECTORY_MUSIC), File(base, "Letras"), File(base, "Lyrics"), File(base, "Lyric"),
        )
        LibraryStore.musicFolders.forEach { list.add(File(it)) }
        return list.filter { it.isDirectory }.distinctBy { it.absolutePath }
    }

    fun start() {
        if (running) return
        scope.launch {
            running = true
            status = "Buscando letras en tu teléfono…"
            val songs = withContext(Dispatchers.Main) { LibraryStore.songs.toList() }
            val byFile = songs.groupBy { CoverScanner.norm(File(it.path).nameWithoutExtension) }
            val byTitleArtist = songs.groupBy { CoverScanner.norm(it.title + " " + it.artist) }
            val byArtistTitle = songs.groupBy { CoverScanner.norm(it.artist + " " + it.title) }
            val lrcs = roots().flatMap { r ->
                r.walkTopDown().maxDepth(6).onEnter { !it.name.startsWith(".") }
                    .filter { it.isFile && it.extension.equals("lrc", true) }.take(5000).toList()
            }.distinctBy { it.absolutePath }
            var n = 0; var dups = 0; var kept = 0
            for (f in lrcs) {
                // ¿ya está junto a su canción?
                if (songs.any { File(it.path).parentFile == f.parentFile && File(it.path).nameWithoutExtension.equals(f.nameWithoutExtension, true) }) continue
                val text = runCatching { readLrcText(f) }.getOrDefault("")
                val key = CoverScanner.norm(f.nameWithoutExtension)
                val ti = tag(text, "ti"); val ar = tag(text, "ar")
                val song = byFile[key]?.firstOrNull()
                    ?: byTitleArtist[key]?.firstOrNull() ?: byArtistTitle[key]?.firstOrNull()
                    ?: if (ti.isNotBlank()) songs.firstOrNull { s ->
                        CoverScanner.similar(s.title, ti) && (ar.isBlank() || s.artist == UNKNOWN_ARTIST || CoverScanner.similar(s.artist, ar))
                    } else null
                if (song == null) continue
                when (place(f, song.path)) {
                    "moved" -> {
                        n++
                        invalidateTrack(song.path)
                        withContext(Dispatchers.Main) { moved.add(0, song.title to f.name) }
                    }
                    "dup" -> dups++
                    "kept" -> kept++
                }
            }
            withContext(Dispatchers.Main) {
                duplicates += dups
                if (n > 0) LibraryStore.bumpTags()
                running = false
                status = buildString {
                    append(if (n > 0) "Listo: acomodé $n letras junto a sus canciones" else "Listo: no encontré letras nuevas")
                    if (dups > 0) append(" · $dups copias repetidas a la Papelera")
                    if (kept > 0) append(" · $kept se quedaron donde estaban (la canción ya tenía otra letra)")
                    append(" (${lrcs.size} revisadas)")
                }
                if (n > 0) android.widget.Toast.makeText(app, "Acomodé $n letras junto a sus canciones 🎤", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    /** Importa un .lrc elegido del teléfono para una canción: lo MUEVE junto a ella. */
    suspend fun importFrom(context: Context, uri: Uri, songPath: String): String = withContext(Dispatchers.IO) {
        val source = runCatching {
            context.contentResolver.query(uri, arrayOf(android.provider.MediaStore.MediaColumns.DATA), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0)?.let { File(it) } else null
            }
        }.getOrNull()?.takeIf { it.isFile }
        val result = if (source != null) place(source, songPath) else {
            // no se sabe de qué archivo viene: se copia y se intenta quitar el original
            val tmp = File(app.cacheDir, "importada.lrc")
            runCatching { context.contentResolver.openInputStream(uri)!!.use { i -> tmp.outputStream().use { i.copyTo(it) } } }
            val r = if (tmp.length() > 0) place(tmp, songPath) else "fail"
            if (r == "moved" || r == "dup") runCatching { context.contentResolver.delete(uri, null, null) }
            tmp.delete()
            r
        }
        invalidateTrack(songPath)
        withContext(Dispatchers.Main) { LibraryStore.bumpTags() }
        when (result) {
            "moved" -> "Letra puesta ✔"
            "dup" -> "La canción ya tenía esa misma letra; quité la copia repetida"
            "kept" -> "La canción ya tiene otra letra; no la reemplacé"
            else -> "No se pudo poner la letra"
        }
    }
}
