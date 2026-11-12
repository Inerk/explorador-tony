package com.tony.explorador

import android.content.Context
import android.media.MediaScannerConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Resultado de buscar una canción en internet (catálogo público de iTunes). */
data class TagResult(
    val title: String,
    val artist: String,
    val album: String,
    val year: String?,
    val genre: String?,
    val trackNumber: Int?,
    val trackCount: Int?,
    val artworkUrl: String?,     // portada grande (600x600)
    val thumbUrl: String?,       // portada chica para la lista
    val durationMs: Long,
)

/** Letra encontrada en LRCLIB. */
data class LyricsResult(
    val id: Long,
    val track: String,
    val artist: String,
    val album: String,
    val durationSec: Int,
    val instrumental: Boolean,
    val synced: String?,
    val plain: String?,
)

private const val UA = "ExploradorTony/1.0 (https://github.com/Inerk/explorador-tony)"

private fun httpGet(url: String): String {
    val conn = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 12_000
        readTimeout = 15_000
        setRequestProperty("User-Agent", UA)
        setRequestProperty("Accept", "application/json")
    }
    try {
        val code = conn.responseCode
        if (code == 404) return "[]"
        if (code != 200) error("El servidor respondió $code")
        return conn.inputStream.bufferedReader().readText()
    } finally {
        conn.disconnect()
    }
}

private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

/** Texto de búsqueda a partir de lo que ya sabemos de la canción (o del nombre del archivo). */
fun suggestedQuery(path: String): String {
    val song = LibraryStore.songFor(path)
    val file = File(path)
    val artist = song?.artist?.takeIf { it != UNKNOWN_ARTIST }
    val title = song?.title ?: file.nameWithoutExtension
    return listOfNotNull(artist, title).joinToString(" ")
        .replace(Regex("""\(.*?(official|video|audio|lyric|letra|hd|4k).*?\)""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""[_]+"""), " ")
        .trim()
}

suspend fun searchSongOnline(term: String): List<TagResult> = withContext(Dispatchers.IO) {
    val url = "https://itunes.apple.com/search?term=${enc(term)}&media=music&entity=song&limit=20&country=MX"
    val arr = JSONObject(httpGet(url)).optJSONArray("results") ?: JSONArray()
    (0 until arr.length()).map { arr.getJSONObject(it) }.map { o ->
        val art100 = o.optString("artworkUrl100").takeIf { it.isNotBlank() }
        TagResult(
            title = o.optString("trackName"),
            artist = o.optString("artistName"),
            album = o.optString("collectionName"),
            year = o.optString("releaseDate").takeIf { it.length >= 4 }?.substring(0, 4),
            genre = o.optString("primaryGenreName").takeIf { it.isNotBlank() },
            trackNumber = o.optInt("trackNumber", 0).takeIf { it > 0 },
            trackCount = o.optInt("trackCount", 0).takeIf { it > 0 },
            artworkUrl = art100?.replace("100x100bb", "600x600bb"),
            thumbUrl = art100,
            durationMs = o.optLong("trackTimeMillis", 0L),
        )
    }
}

suspend fun searchLyricsOnline(track: String, artist: String): List<LyricsResult> = withContext(Dispatchers.IO) {
    fun parse(json: String): List<LyricsResult> {
        val arr = JSONArray(json)
        return (0 until arr.length()).map { arr.getJSONObject(it) }.map { o ->
            LyricsResult(
                id = o.optLong("id"),
                track = o.optString("trackName"),
                artist = o.optString("artistName"),
                album = o.optString("albumName"),
                durationSec = o.optDouble("duration", 0.0).toInt(),
                instrumental = o.optBoolean("instrumental"),
                synced = o.optString("syncedLyrics").takeIf { it.isNotBlank() && it != "null" },
                plain = o.optString("plainLyrics").takeIf { it.isNotBlank() && it != "null" },
            )
        }
    }
    val structured = if (track.isNotBlank())
        parse(httpGet("https://lrclib.net/api/search?track_name=${enc(track)}" + (if (artist.isNotBlank()) "&artist_name=${enc(artist)}" else "")))
    else emptyList()
    val results = structured.ifEmpty { parse(httpGet("https://lrclib.net/api/search?q=${enc("$artist $track".trim())}")) }
    // primero las sincronizadas
    results.sortedByDescending { it.synced != null }
}

/** Guarda la letra como "Canción.lrc" junto a la canción. */
suspend fun saveLyricsFile(context: Context, path: String, lyrics: LyricsResult): Boolean = withContext(Dispatchers.IO) {
    val audio = File(path)
    val out = File(audio.parentFile, audio.nameWithoutExtension + ".lrc")
    val text = lyrics.synced ?: lyrics.plain ?: return@withContext false
    val header = "[ti:${lyrics.track}]\n[ar:${lyrics.artist}]\n[al:${lyrics.album}]\n"
    val ok = runCatching { out.writeText(header + text); true }.getOrDefault(false)
    if (ok) {
        invalidateTrack(path)
        withContext(Dispatchers.Main) { LibraryStore.bumpTags() }
        MediaScannerConnection.scanFile(context, arrayOf(out.absolutePath), null, null)
    }
    ok
}

/** Qué datos aplicar al etiquetar. */
data class TagChoice(
    val title: Boolean = true,
    val artist: Boolean = true,
    val album: Boolean = true,
    val cover: Boolean = true,
    val year: Boolean = true,
    val genre: Boolean = true,
    val track: Boolean = true,
)

/** Escribe las etiquetas dentro del archivo de música (MP3, M4A, FLAC, OGG…). */
suspend fun writeTags(context: Context, path: String, r: TagResult, choice: TagChoice): Result<Unit> = withContext(Dispatchers.IO) {
    runCatching {
        org.jaudiotagger.tag.TagOptionSingleton.getInstance().setAndroid(true)
        java.util.logging.Logger.getLogger("org.jaudiotagger").level = java.util.logging.Level.OFF
        val file = File(path)
        val af = org.jaudiotagger.audio.AudioFileIO.read(file)
        val tag = af.tagOrCreateAndSetDefault
        fun set(key: org.jaudiotagger.tag.FieldKey, v: String?) { if (!v.isNullOrBlank()) tag.setField(key, v) }
        if (choice.title) set(org.jaudiotagger.tag.FieldKey.TITLE, r.title)
        if (choice.artist) { set(org.jaudiotagger.tag.FieldKey.ARTIST, r.artist); set(org.jaudiotagger.tag.FieldKey.ALBUM_ARTIST, r.artist) }
        if (choice.album) set(org.jaudiotagger.tag.FieldKey.ALBUM, r.album)
        if (choice.year) set(org.jaudiotagger.tag.FieldKey.YEAR, r.year)
        if (choice.genre) set(org.jaudiotagger.tag.FieldKey.GENRE, r.genre)
        if (choice.track) {
            r.trackNumber?.let { set(org.jaudiotagger.tag.FieldKey.TRACK, it.toString()) }
            r.trackCount?.let { runCatching { set(org.jaudiotagger.tag.FieldKey.TRACK_TOTAL, it.toString()) } }
        }
        if (choice.cover && r.artworkUrl != null) {
            val bytes = URL(r.artworkUrl).openStream().use { it.readBytes() }
            val art = org.jaudiotagger.tag.images.ArtworkFactory.getNew()
            art.setBinaryData(bytes)
            art.setMimeType("image/jpeg")
            art.setPictureType(3) // portada de enfrente
            runCatching { tag.deleteArtworkField() }
            tag.setField(art)
        }
        af.commit()
        // que todo se refresque: biblioteca, portadas y el índice de Android
        invalidateTrack(path)
        withContext(Dispatchers.Main) {
            LibraryStore.updateSongTags(
                path,
                title = if (choice.title) r.title else null,
                artist = if (choice.artist) r.artist else null,
                album = if (choice.album) r.album else null,
            )
        }
        MediaScannerConnection.scanFile(context, arrayOf(path), null, null)
        Unit
    }
}

