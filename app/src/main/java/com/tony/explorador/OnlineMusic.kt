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
    val direct = runCatching {
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
    if (direct.isSuccess) return@withContext direct
    // no se puede escribir dentro de este archivo (pasa con los videos): se guarda en la app
    runCatching {
        val bytes = if (choice.cover && r.artworkUrl != null) URL(r.artworkUrl).openStream().use { it.readBytes() } else null
        TagOverrides.save(
            path,
            title = r.title.takeIf { choice.title }, artist = r.artist.takeIf { choice.artist }, album = r.album.takeIf { choice.album },
            year = r.year.takeIf { choice.year }, genre = r.genre.takeIf { choice.genre },
            track = r.trackNumber?.toString().takeIf { choice.track }, coverBytes = bytes,
        )
        savedInApp(context, path, r.title.takeIf { choice.title }, r.artist.takeIf { choice.artist }, r.album.takeIf { choice.album })
    }
}

private suspend fun savedInApp(context: Context, path: String, title: String?, artist: String?, album: String?) {
    invalidateTrack(path)
    withContext(Dispatchers.Main) {
        LibraryStore.updateSongTags(path, title, artist, album)
        android.widget.Toast.makeText(context, "En este formato no se puede escribir dentro del archivo: lo guardé en la app.",
            android.widget.Toast.LENGTH_LONG).show()
    }
}


/** Todo lo que trae un archivo de música: datos técnicos y etiquetas. */
data class FullTags(
    val seconds: Int = 0,
    val sampleRate: Int = 0,
    val bits: Int = 0,
    val channels: String = "",
    val kbps: Long = 0,
    val format: String = "",
    val title: String = "",
    val track: String = "",
    val disc: String = "",
    val year: String = "",
    val genre: String = "",
    val artist: String = "",
    val album: String = "",
    val albumArtist: String = "",
    val composer: String = "",
    val comment: String = "",
    val artWidth: Int = 0,
    val artHeight: Int = 0,
    val artMime: String = "",
    val artBytes: Int = 0,
    val canWrite: Boolean = true,
)

suspend fun readFullTags(path: String): FullTags = withContext(Dispatchers.IO) {
    runCatching {
        org.jaudiotagger.tag.TagOptionSingleton.getInstance().setAndroid(true)
        java.util.logging.Logger.getLogger("org.jaudiotagger").level = java.util.logging.Level.OFF
        val af = org.jaudiotagger.audio.AudioFileIO.read(File(path))
        val h = af.audioHeader
        val tag = af.tag
        fun get(k: org.jaudiotagger.tag.FieldKey) = runCatching { tag?.getFirst(k).orEmpty() }.getOrDefault("").trim()
        val art = runCatching { tag?.firstArtwork }.getOrNull()
        val bytes = art?.binaryData
        val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        if (bytes != null) android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        FullTags(
            seconds = runCatching { h.trackLength }.getOrDefault(0),
            sampleRate = runCatching { h.sampleRateAsNumber }.getOrDefault(0),
            bits = runCatching { h.bitsPerSample }.getOrDefault(0),
            channels = runCatching { h.channels.orEmpty() }.getOrDefault(""),
            kbps = runCatching { h.bitRateAsNumber }.getOrDefault(0L),
            format = runCatching { h.format.orEmpty() }.getOrDefault(""),
            title = get(org.jaudiotagger.tag.FieldKey.TITLE),
            track = get(org.jaudiotagger.tag.FieldKey.TRACK),
            disc = get(org.jaudiotagger.tag.FieldKey.DISC_NO),
            year = get(org.jaudiotagger.tag.FieldKey.YEAR),
            genre = get(org.jaudiotagger.tag.FieldKey.GENRE),
            artist = get(org.jaudiotagger.tag.FieldKey.ARTIST),
            album = get(org.jaudiotagger.tag.FieldKey.ALBUM),
            albumArtist = get(org.jaudiotagger.tag.FieldKey.ALBUM_ARTIST),
            composer = get(org.jaudiotagger.tag.FieldKey.COMPOSER),
            comment = get(org.jaudiotagger.tag.FieldKey.COMMENT),
            artWidth = opts.outWidth.coerceAtLeast(0),
            artHeight = opts.outHeight.coerceAtLeast(0),
            artMime = art?.mimeType.orEmpty(),
            artBytes = bytes?.size ?: 0,
        )
    }.getOrElse {
        // si jaudiotagger no puede con el formato, lo básico con Android
        val r = android.media.MediaMetadataRetriever()
        try {
            r.setDataSource(path)
            fun m(k: Int) = r.extractMetadata(k).orEmpty()
            val pic = r.embeddedPicture
            val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            if (pic != null) android.graphics.BitmapFactory.decodeByteArray(pic, 0, pic.size, opts)
            FullTags(
                seconds = (m(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION).toLongOrNull() ?: 0L).div(1000).toInt(),
                sampleRate = if (android.os.Build.VERSION.SDK_INT >= 31) m(android.media.MediaMetadataRetriever.METADATA_KEY_SAMPLERATE).toIntOrNull() ?: 0 else 0,
                kbps = (m(android.media.MediaMetadataRetriever.METADATA_KEY_BITRATE).toLongOrNull() ?: 0L) / 1000,
                title = m(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE),
                track = m(android.media.MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER),
                disc = m(android.media.MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER),
                year = m(android.media.MediaMetadataRetriever.METADATA_KEY_YEAR),
                genre = m(android.media.MediaMetadataRetriever.METADATA_KEY_GENRE),
                artist = m(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST),
                album = m(android.media.MediaMetadataRetriever.METADATA_KEY_ALBUM),
                albumArtist = m(android.media.MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST),
                composer = m(android.media.MediaMetadataRetriever.METADATA_KEY_COMPOSER),
                artWidth = opts.outWidth.coerceAtLeast(0), artHeight = opts.outHeight.coerceAtLeast(0),
                artMime = opts.outMimeType.orEmpty(), artBytes = pic?.size ?: 0,
                canWrite = false,
            )
        } catch (_: Exception) {
            FullTags(canWrite = false)
        } finally {
            runCatching { r.release() }
        }
    }.let { TagOverrides.overlay(path, it) }
}

/** Guarda a mano las etiquetas que escribiste (vacío = se borra ese dato). */
suspend fun writeManualTags(context: Context, path: String, t: FullTags): Result<Unit> = withContext(Dispatchers.IO) {
    val direct = runCatching {
        org.jaudiotagger.tag.TagOptionSingleton.getInstance().setAndroid(true)
        val af = org.jaudiotagger.audio.AudioFileIO.read(File(path))
        val tag = af.tagOrCreateAndSetDefault
        fun put(k: org.jaudiotagger.tag.FieldKey, v: String) {
            if (v.isBlank()) runCatching { tag.deleteField(k) } else tag.setField(k, v.trim())
        }
        put(org.jaudiotagger.tag.FieldKey.TITLE, t.title)
        put(org.jaudiotagger.tag.FieldKey.ARTIST, t.artist)
        put(org.jaudiotagger.tag.FieldKey.ALBUM, t.album)
        put(org.jaudiotagger.tag.FieldKey.ALBUM_ARTIST, t.albumArtist)
        put(org.jaudiotagger.tag.FieldKey.YEAR, t.year)
        put(org.jaudiotagger.tag.FieldKey.GENRE, t.genre)
        put(org.jaudiotagger.tag.FieldKey.TRACK, t.track.substringBefore('/'))
        put(org.jaudiotagger.tag.FieldKey.DISC_NO, t.disc.substringBefore('/'))
        put(org.jaudiotagger.tag.FieldKey.COMPOSER, t.composer)
        put(org.jaudiotagger.tag.FieldKey.COMMENT, t.comment)
        af.commit()
        invalidateTrack(path)
        withContext(Dispatchers.Main) { LibraryStore.updateSongTags(path, t.title, t.artist, t.album) }
        MediaScannerConnection.scanFile(context, arrayOf(path), null, null)
        Unit
    }
    if (direct.isSuccess) return@withContext direct
    runCatching {
        TagOverrides.save(path, t.title, t.artist, t.album, t.year, t.genre, t.track)
        savedInApp(context, path, t.title, t.artist, t.album)
    }
}
