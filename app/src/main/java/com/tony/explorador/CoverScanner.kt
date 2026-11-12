package com.tony.explorador

import android.content.Context
import android.media.MediaMetadataRetriever
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.Normalizer
import java.util.Locale

/**
 * Revisa la música que no tiene carátula y busca en internet si hay una disponible.
 * Lo que encuentra se marca con un iconito en la canción; tú decides si ponerla.
 * Va despacio a propósito (unas 18 búsquedas por minuto) para que el servicio no nos bloquee.
 */
object CoverScanner {
    private lateinit var app: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    /** ruta → lo encontrado */
    val found = mutableStateMapOf<String, TagResult>()
    /** rutas ya buscadas sin suerte (para no repetir) */
    private val noMatch = HashSet<String>()

    var auto by mutableStateOf(false)
        private set
    var running by mutableStateOf(false)
        private set
    var done by mutableIntStateOf(0)
        private set
    var total by mutableIntStateOf(0)
        private set
    var status by mutableStateOf("")
        private set

    private val file get() = File(app.filesDir, "caratulas.json")

    fun init(context: Context) {
        app = context.applicationContext
        runCatching {
            if (!file.isFile) return@runCatching
            val o = JSONObject(file.readText())
            auto = o.optBoolean("auto", false)
            o.optJSONArray("none")?.let { a -> for (i in 0 until a.length()) noMatch.add(a.getString(i)) }
            o.optJSONObject("found")?.let { m ->
                m.keys().forEach { k ->
                    val r = m.getJSONObject(k)
                    found[k] = TagResult(
                        r.optString("t"), r.optString("a"), r.optString("l"), r.optString("y").ifBlank { null },
                        r.optString("g").ifBlank { null }, r.optInt("n", 0).takeIf { it > 0 }, r.optInt("c", 0).takeIf { it > 0 },
                        r.optString("art").ifBlank { null }, r.optString("thumb").ifBlank { null }, r.optLong("d"),
                    )
                }
            }
        }
    }

    private fun save() {
        val snapshot = found.toMap()
        val none = synchronized(noMatch) { noMatch.toList() }
        scope.launch {
            runCatching {
                val m = JSONObject()
                snapshot.forEach { (k, r) ->
                    m.put(k, JSONObject().put("t", r.title).put("a", r.artist).put("l", r.album).put("y", r.year ?: "")
                        .put("g", r.genre ?: "").put("n", r.trackNumber ?: 0).put("c", r.trackCount ?: 0)
                        .put("art", r.artworkUrl ?: "").put("thumb", r.thumbUrl ?: "").put("d", r.durationMs))
                }
                file.writeText(JSONObject().put("auto", auto).put("none", JSONArray(none)).put("found", m).toString())
            }
        }
    }

    fun changeAuto(on: Boolean) {
        auto = on
        save()
        if (on) start()
    }

    fun stop() {
        job?.cancel(); job = null
        running = false
        status = "Detenido"
    }

    /** Si la canción ya tiene carátula (dentro del archivo o en la carpeta). */
    private fun hasCover(path: String): Boolean {
        val f = File(path)
        if (folderCoverFile(f) != null) return true
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(path); r.embeddedPicture != null
        } catch (_: Exception) { true } // si no se puede leer, no la molestamos
        finally { runCatching { r.release() } }
    }

    fun norm(s: String): String =
        Normalizer.normalize(s.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("""\(.*?\)|\[.*?]"""), " ")
            .replace(Regex("feat\\.?|ft\\.?|official|video|audio|lyrics?|letra|hd"), " ")
            .replace(Regex("[^a-z0-9]+"), " ").trim()

    fun similar(a: String, b: String): Boolean {
        val x = norm(a); val y = norm(b)
        if (x.isBlank() || y.isBlank()) return false
        if (x.contains(y) || y.contains(x)) return true
        val tx = x.split(' ').filter { it.length > 1 }.toSet()
        val ty = y.split(' ').filter { it.length > 1 }.toSet()
        if (tx.isEmpty() || ty.isEmpty()) return false
        return tx.intersect(ty).size.toFloat() / minOf(tx.size, ty.size) >= 0.6f
    }

    /** Elige el resultado que de verdad sea esa canción (mismo artista y título o álbum). */
    private fun pick(song: Song, results: List<TagResult>): TagResult? {
        val knownArtist = song.artist != UNKNOWN_ARTIST
        return results.firstOrNull { r ->
            r.artworkUrl != null &&
                (!knownArtist || similar(song.artist, r.artist)) &&
                (similar(song.title, r.title) || (song.album != UNKNOWN_ALBUM && similar(song.album, r.album)))
        }
    }

    /** Revisa toda la música (o sólo lo nuevo si ya se había revisado). */
    fun start() {
        if (running) return
        job = scope.launch {
            running = true
            done = 0; total = 0
            status = "Revisando qué canciones no tienen carátula…"
            val songs = withContext(Dispatchers.Main) { LibraryStore.songs.toList() }
            val missing = songs.filter { s ->
                isActive && s.path !in found && synchronized(noMatch) { s.path !in noMatch } &&
                    File(s.path).kind() == FileKind.AUDIO && !hasCover(s.path)
            }
            // una búsqueda por álbum (las canciones sueltas, una por una)
            val groups = missing.groupBy { s ->
                if (s.album != UNKNOWN_ALBUM) "${s.artist.lowercase(Locale.ROOT)}|${s.album.lowercase(Locale.ROOT)}" else s.path
            }.values.toList()
            total = groups.size
            var newOnes = 0
            if (groups.isEmpty()) status = "Todas tus canciones ya tienen carátula o ya se revisaron"
            for (group in groups) {
                if (!isActive) break
                val first = group.first()
                status = "Buscando: ${first.artist} — ${first.album.takeIf { it != UNKNOWN_ALBUM } ?: first.title}"
                val query = suggestedQuery(first.path)
                val results = try {
                    searchSongOnline(query)
                } catch (e: Exception) {
                    // el servicio pide calma: esperar un minuto y seguir
                    status = "El servicio pidió una pausa, sigo en un minuto…"
                    delay(60_000)
                    runCatching { searchSongOnline(query) }.getOrDefault(emptyList())
                }
                val hit = pick(first, results)
                withContext(Dispatchers.Main) {
                    group.forEach { s ->
                        if (hit != null) { found[s.path] = hit; newOnes++ } else synchronized(noMatch) { noMatch.add(s.path) }
                    }
                    done++
                }
                if (done % 10 == 0) save()
                delay(3300)
            }
            save()
            running = false
            if (isActive) {
                status = if (newOnes > 0) "Listo: encontré carátula para $newOnes canciones" else "Listo: no encontré carátulas nuevas"
                withContext(Dispatchers.Main) {
                    Toast.makeText(app, if (newOnes > 0) "Encontré carátula para $newOnes canciones 🎨" else "Revisión de carátulas terminada",
                        Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    /** Pone la carátula encontrada dentro del archivo (sólo la imagen, no cambia nombres). */
    suspend fun apply(context: Context, path: String): Boolean {
        val r = found[path] ?: return false
        val ok = writeTags(context, path, r, TagChoice(title = false, artist = false, album = false, cover = true,
            year = false, genre = false, track = false)).isSuccess
        if (ok) withContext(Dispatchers.Main) { found.remove(path); save() }
        return ok
    }

    /** Descarta lo encontrado (no era la carátula correcta). */
    fun dismiss(path: String) {
        found.remove(path)
        synchronized(noMatch) { noMatch.add(path) }
        save()
    }

    /** Vuelve a revisar todo desde cero (por si agregaste música o cambiaste etiquetas). */
    fun resetChecked() {
        synchronized(noMatch) { noMatch.clear() }
        save()
    }
}
