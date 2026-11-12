package com.tony.explorador

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

data class CoverColors(
    val top: Color,      // parte de arriba del fondo
    val bottom: Color,   // parte de abajo del fondo
    val accent: Color,   // botones, barra de avance
    val onAccent: Color, // ícono dentro del botón de play
)

val DefaultCoverColors = CoverColors(
    top = Color(0xFF2A2F4A),
    bottom = Color(0xFF0B0C12),
    accent = Color(0xFF9FB4FF),
    onAccent = Color(0xFF0B0C12),
)

/** Colores de la canción que suena ahora (el tema Original pinta la biblioteca con ellos). */
object NowPlaying {
    var colors by androidx.compose.runtime.mutableStateOf(DefaultCoverColors)
    /** Ruta de la canción que suena (para marcarla en las listas). */
    var path by androidx.compose.runtime.mutableStateOf<String?>(null)
}

data class TrackInfo(
    val path: String,
    val title: String?,
    val artist: String?,
    val album: String?,
    val cover: Bitmap?,
    val colors: CoverColors,
    val lyrics: List<LyricLine>,
    val trackNumber: Int? = null,   // número de pista en su álbum
    val trackTotal: Int? = null,    // cuántas pistas tiene el álbum (si viene en la canción)
)

private val cache = LruCache<String, TrackInfo>(12)

/** Carga portada, etiquetas, colores y letra de la canción actual (en segundo plano). */
@Composable
fun rememberTrackInfo(path: String?): State<TrackInfo?> =
    produceState<TrackInfo?>(initialValue = path?.let { cache.get(it) }, path, LibraryStore.tagsVersion) {
        if (path == null) { value = null; return@produceState }
        cache.get(path)?.let { value = it; return@produceState }
        val info = loadTrackInfo(path)
        cache.put(path, info)
        value = info
    }

suspend fun loadTrackInfo(path: String): TrackInfo = withContext(Dispatchers.IO) {
    val file = File(path)
    var title: String? = null
    var artist: String? = null
    var album: String? = null
    var cover: Bitmap? = null
    var trackNo: Int? = null
    var trackTot: Int? = null
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(path)
        title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }
        artist = (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST))?.takeIf { it.isNotBlank() }
        album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.takeIf { it.isNotBlank() }
        cover = retriever.embeddedPicture?.let { decodeSampled(it, 1000) }
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)?.let { t ->
            // viene como "3" o "3/12"
            trackNo = t.substringBefore('/').trim().toIntOrNull()
            trackTot = t.substringAfter('/', "").trim().toIntOrNull()
        }
    } catch (_: Exception) {
    } finally {
        runCatching { retriever.release() }
    }
    if (cover == null) cover = folderCover(file)

    val lyrics = findLrc(file)?.let { runCatching { parseLrc(readLrcText(it)) }.getOrNull() } ?: emptyList()

    TrackInfo(
        path = path,
        title = title ?: file.nameWithoutExtension,
        artist = artist,
        album = album,
        cover = cover,
        colors = cover?.let { colorsFrom(it) } ?: DefaultCoverColors,
        lyrics = lyrics,
        trackNumber = trackNo,
        trackTotal = trackTot,
    )
}

private fun decodeSampled(bytes: ByteArray, maxSize: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= maxSize || bounds.outHeight / (sample * 2) >= maxSize) sample *= 2
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
}

private fun decodeFileSampled(file: File, maxSize: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= maxSize || bounds.outHeight / (sample * 2) >= maxSize) sample *= 2
    return BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
}

/** Si la canción no trae portada, busca cover.jpg / folder.jpg / front.jpg en la carpeta. */
fun folderCoverFile(audio: File): File? {
    val dir = audio.parentFile ?: return null
    val names = listOf("cover", "folder", "front", "albumart", "album", audio.nameWithoutExtension.lowercase(Locale.ROOT))
    return dir.listFiles()?.firstOrNull { f ->
        f.isFile && f.extension.lowercase(Locale.ROOT) in setOf("jpg", "jpeg", "png", "webp") &&
            f.nameWithoutExtension.lowercase(Locale.ROOT).let { n -> names.any { n == it || n.startsWith("albumart") } }
    }
}

private fun folderCover(audio: File): Bitmap? {
    val candidate = folderCoverFile(audio) ?: return null
    return runCatching { decodeFileSampled(candidate, 1000) }.getOrNull()
}

private fun colorsFrom(bitmap: Bitmap): CoverColors {
    val palette = Palette.from(bitmap).maximumColorCount(24).generate()
    val fallback = 0xFF2A2F4A.toInt()
    val dominant = palette.getDominantColor(fallback)
    val darkBase = palette.getDarkMutedColor(palette.getDarkVibrantColor(dominant))
    val vibrant = palette.getVibrantColor(palette.getLightVibrantColor(palette.getMutedColor(dominant)))

    // Fondo: color de la portada oscurecido para que el texto blanco siempre se lea
    val top = darken(dominant, 0.45f)
    val bottom = darken(darkBase, 0.80f)
    // Acento: el color más vivo, aclarado si es muy oscuro
    var accent = vibrant
    while (Color(accent).luminance() < 0.35f) accent = ColorUtils.blendARGB(accent, 0xFFFFFFFF.toInt(), 0.2f)
    val accentColor = Color(accent)
    return CoverColors(
        top = Color(top),
        bottom = Color(bottom),
        accent = accentColor,
        onAccent = if (accentColor.luminance() > 0.5f) Color(0xFF111111) else Color.White,
    )
}

private fun darken(color: Int, amount: Float): Int =
    ColorUtils.blendARGB(color, 0xFF000000.toInt(), amount)

private val thumbCache = LruCache<String, Bitmap>(120)
private val noThumb = java.util.Collections.synchronizedSet(HashSet<String>())

/** Miniatura de la portada para las listas (pequeña y en caché). */
suspend fun loadThumb(path: String): Bitmap? = withContext(Dispatchers.IO) {
    thumbCache.get(path)?.let { return@withContext it }
    if (path in noThumb) return@withContext null
    val r = MediaMetadataRetriever()
    val bmp = try {
        r.setDataSource(path)
        r.embeddedPicture?.let { decodeSampled(it, 128) }
    } catch (_: Exception) { null } finally { runCatching { r.release() } }
        ?: folderCover(File(path))?.let { Bitmap.createScaledBitmap(it, 128, (128 * it.height / it.width.coerceAtLeast(1)).coerceAtLeast(1), true) }
    if (bmp != null) thumbCache.put(path, bmp) else noThumb.add(path)
    bmp
}

private val bigCoverCache = LruCache<String, Bitmap>(40)
private val noBigCover = java.util.Collections.synchronizedSet(HashSet<String>())

/** Portada mediana para la vista de álbumes (Cover Flow). */
suspend fun loadCover(path: String): Bitmap? = withContext(Dispatchers.IO) {
    bigCoverCache.get(path)?.let { return@withContext it }
    if (path in noBigCover) return@withContext null
    val r = MediaMetadataRetriever()
    val bmp = try {
        r.setDataSource(path)
        r.embeddedPicture?.let { decodeSampled(it, 420) }
    } catch (_: Exception) { null } finally { runCatching { r.release() } }
        ?: folderCover(File(path))
    if (bmp != null) bigCoverCache.put(path, bmp) else noBigCover.add(path)
    bmp
}

/** Portada ya cargada (sin esperar), para que no parpadee al moverse. */
fun peekCover(path: String): Bitmap? = bigCoverCache.get(path)

/** Olvida lo que había en caché de una canción (después de etiquetarla o bajarle letra). */
fun invalidateTrack(path: String) {
    cache.remove(path)
    thumbCache.remove(path)
    bigCoverCache.remove(path)
    noThumb.remove(path)
    noBigCover.remove(path)
}

private val qualityCache = LruCache<String, String>(300)

/** «MP3 · 320 kbps» de una canción (se calcula una vez y se recuerda). */
@Composable
fun rememberAudioQuality(path: String?): String? =
    produceState<String?>(path?.let { qualityCache.get(it) }, path) {
        if (path == null) { value = null; return@produceState }
        qualityCache.get(path)?.let { value = it; return@produceState }
        val q = withContext(Dispatchers.IO) {
            val ext = File(path).extension.uppercase(Locale.ROOT)
            val r = MediaMetadataRetriever()
            val kbps = try {
                r.setDataSource(path)
                r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull()?.let { (it + 500) / 1000 }
            } catch (_: Exception) { null } finally { runCatching { r.release() } }
            if (kbps != null && kbps > 0) "$ext · $kbps kbps" else ext
        }
        qualityCache.put(path, q)
        value = q
    }.value
