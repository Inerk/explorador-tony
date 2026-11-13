package com.tony.explorador

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * Etiquetas guardadas en la app, para archivos en los que no se puede escribir
 * (por ejemplo videos MP4, MKV o WEBM). El archivo no se toca; la app recuerda
 * el título, artista, álbum y la portada, y los usa en todas partes.
 */
object TagOverrides {
    data class Entry(val title: String = "", val artist: String = "", val album: String = "",
                     val year: String = "", val genre: String = "", val track: String = "")

    private lateinit var dir: File
    private val map = HashMap<String, Entry>()
    private val file get() = File(dir, "etiquetas.json")

    fun init(context: Context) {
        dir = File(context.filesDir, "etiquetas").apply { mkdirs() }
        runCatching {
            val o = JSONObject(file.readText())
            o.keys().forEach { k ->
                val e = o.getJSONObject(k)
                map[k] = Entry(e.optString("t"), e.optString("a"), e.optString("l"), e.optString("y"), e.optString("g"), e.optString("n"))
            }
        }
    }

    private fun key(path: String) =
        MessageDigest.getInstance("MD5").digest(path.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun coverFile(path: String) = File(dir, key(path) + ".jpg")

    /** Archivo de etiquetas que se guarda junto al video: «Video.mp4.tony.json» (trae también la portada). */
    fun sidecar(path: String) = File("$path.tony.json")
    private val checked = HashSet<String>()

    @Synchronized fun get(path: String): Entry? {
        if (!::dir.isInitialized) return null
        map[path]?.let { return it }
        // si el archivo se movió de carpeta, sus etiquetas viajan con él en el archivo de al lado
        if (!checked.add(path)) return null
        val f = sidecar(path)
        if (!f.isFile) return null
        return runCatching {
            val o = JSONObject(f.readText())
            val e = Entry(o.optString("titulo"), o.optString("artista"), o.optString("album"), o.optString("anio"), o.optString("genero"), o.optString("pista"))
            map[path] = e
            o.optString("portada").takeIf { it.isNotBlank() }?.let { b64 ->
                coverFile(path).writeBytes(android.util.Base64.decode(b64, android.util.Base64.DEFAULT))
            }
            writeIndex()
            e
        }.getOrNull()
    }

    private fun writeIndex() {
        runCatching {
            val o = JSONObject()
            map.forEach { (k, e) ->
                o.put(k, JSONObject().put("t", e.title).put("a", e.artist).put("l", e.album).put("y", e.year).put("g", e.genre).put("n", e.track))
            }
            file.writeText(o.toString())
        }
    }

    fun cover(path: String, maxSize: Int): Bitmap? {
        if (!::dir.isInitialized) return null
        val f = coverFile(path)
        if (!f.isFile) return null
        return runCatching {
            val b = BitmapFactory.decodeFile(f.absolutePath) ?: return null
            if (b.width <= maxSize) b
            else Bitmap.createScaledBitmap(b, maxSize, (maxSize * b.height / b.width.coerceAtLeast(1)).coerceAtLeast(1), true)
        }.getOrNull()
    }

    /** Guarda (mezclando con lo que ya había). Los valores null no se tocan. */
    @Synchronized fun save(path: String, title: String? = null, artist: String? = null, album: String? = null,
                           year: String? = null, genre: String? = null, track: String? = null, coverBytes: ByteArray? = null) {
        val old = map[path] ?: Entry()
        map[path] = Entry(title ?: old.title, artist ?: old.artist, album ?: old.album, year ?: old.year, genre ?: old.genre, track ?: old.track)
        if (coverBytes != null) runCatching { coverFile(path).writeBytes(coverBytes) }
        writeIndex()
        // copia junto al archivo, para que las etiquetas no se pierdan si lo mueves o reinstalas la app
        runCatching {
            val e = map.getValue(path)
            val o = JSONObject().put("titulo", e.title).put("artista", e.artist).put("album", e.album)
                .put("anio", e.year).put("genero", e.genre).put("pista", e.track)
            coverFile(path).takeIf { it.isFile }?.let { o.put("portada", android.util.Base64.encodeToString(it.readBytes(), android.util.Base64.NO_WRAP)) }
            sidecar(path).writeText(o.toString())
        }
    }

    /** Aplica lo guardado a una canción de la biblioteca. */
    fun apply(s: Song): Song {
        val e = get(s.path) ?: return s
        return s.copy(title = e.title.ifBlank { s.title }, artist = e.artist.ifBlank { s.artist }, album = e.album.ifBlank { s.album })
    }

    /** Aplica lo guardado a la pantalla de información. */
    fun overlay(path: String, t: FullTags): FullTags {
        val e = get(path) ?: return t
        return t.copy(
            title = e.title.ifBlank { t.title }, artist = e.artist.ifBlank { t.artist }, album = e.album.ifBlank { t.album },
            year = e.year.ifBlank { t.year }, genre = e.genre.ifBlank { t.genre }, track = e.track.ifBlank { t.track },
        )
    }
}
