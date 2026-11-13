package com.tony.explorador

import android.content.Intent
import android.location.Geocoder
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class MediaDetails(
    val date: String,
    val folder: String,
    val device: String = "",
    val lens: String = "",
    val line1: List<String> = emptyList(),   // tamaño | resolución | MP
    val line2: List<String> = emptyList(),   // ISO | mm | ev | F | velocidad   (o datos del video)
    val lat: Double? = null,
    val lng: Double? = null,
    val address: String = "",
)

private val longDate = SimpleDateFormat("EEEE, d 'de' MMMM 'de' yyyy · h:mm a", Locale("es", "MX"))

private fun prettyFolder(f: File): String {
    val internal = Environment.getExternalStorageDirectory().absolutePath
    val p = f.parent ?: ""
    return if (p.startsWith(internal)) "/Almacenamiento interno" + p.removePrefix(internal) else p
}

private fun mb(bytes: Long) = String.format(Locale.US, "%.2f MB", bytes / 1048576.0)

private fun loadDetails(context: android.content.Context, file: File, isVideo: Boolean): MediaDetails {
    var date = file.lastModified()
    var device = ""; var lens = ""
    val l1 = mutableListOf(mb(file.length()))
    val l2 = mutableListOf<String>()
    var lat: Double? = null; var lng: Double? = null
    if (isVideo) {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(file.absolutePath)
            fun m(k: Int) = r.extractMetadata(k)
            val w = m(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val h = m(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            if (w > 0 && h > 0) l1.add("${w}x$h")
            m(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.let { l1.add(formatTime(it)) }
            m(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toFloatOrNull()?.let { l2.add("${it.toInt()} fps") }
            m(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull()?.let { l2.add(String.format(Locale.US, "%.1f Mbps", it / 1_000_000.0)) }
            l2.add(file.extension.uppercase(Locale.ROOT))
            // ubicación tipo "+20.6767-101.3563/"
            m(MediaMetadataRetriever.METADATA_KEY_LOCATION)?.let { loc ->
                Regex("""([+-]\d+\.?\d*)([+-]\d+\.?\d*)""").find(loc)?.let { mt ->
                    lat = mt.groupValues[1].toDoubleOrNull(); lng = mt.groupValues[2].toDoubleOrNull()
                }
            }
        } catch (_: Exception) {} finally { runCatching { r.release() } }
    } else runCatching {
        val e = ExifInterface(file.absolutePath)
        val w = e.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0).takeIf { it > 0 } ?: e.getAttributeInt(ExifInterface.TAG_PIXEL_X_DIMENSION, 0)
        val h = e.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0).takeIf { it > 0 } ?: e.getAttributeInt(ExifInterface.TAG_PIXEL_Y_DIMENSION, 0)
        var ww = w; var hh = h
        if (ww <= 0 || hh <= 0) {
            val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(file.absolutePath, o); ww = o.outWidth; hh = o.outHeight
        }
        if (ww > 0 && hh > 0) { l1.add("${ww}x$hh"); l1.add("${Math.round(ww.toLong() * hh / 1_000_000.0)}MP") }
        e.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)?.let { s ->
            runCatching { SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).parse(s)?.time }.getOrNull()?.let { date = it }
        }
        val model = e.getAttribute(ExifInterface.TAG_MODEL).orEmpty().trim()
        val make = e.getAttribute(ExifInterface.TAG_MAKE).orEmpty().trim()
        device = if (model.isBlank()) make else if (make.isNotBlank() && !model.contains(make, true)) "$make $model" else model
        val focal35 = e.getAttributeInt(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, 0)
        val focal = e.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, 0.0)
        val mm = if (focal35 > 0) "${focal35}mm" else if (focal > 0) String.format(Locale.US, "%.1fmm", focal) else ""
        if (focal35 > 0) lens = when {
            focal35 <= 16 -> "Lente ultra gran angular - $mm"
            focal35 <= 35 -> "Lente gran angular - $mm"
            focal35 <= 60 -> "Lente normal - $mm"
            else -> "Teleobjetivo - $mm"
        }
        (e.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY) ?: e.getAttribute("ISOSpeedRatings"))?.takeIf { it.isNotBlank() }?.let { l2.add("ISO $it") }
        if (mm.isNotBlank()) l2.add(mm)
        if (e.getAttribute(ExifInterface.TAG_EXPOSURE_BIAS_VALUE) != null)
            l2.add(String.format(Locale.US, "%.1fev", e.getAttributeDouble(ExifInterface.TAG_EXPOSURE_BIAS_VALUE, 0.0)))
        e.getAttributeDouble(ExifInterface.TAG_F_NUMBER, 0.0).takeIf { it > 0 }?.let { l2.add(String.format(Locale.US, "F%.1f", it)) }
        e.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, 0.0).takeIf { it > 0 }?.let { t ->
            l2.add(if (t < 1) "1/${Math.round(1 / t)} s" else String.format(Locale.US, "%.1f s", t))
        }
        val ll = FloatArray(2)
        @Suppress("DEPRECATION")
        if (e.getLatLong(ll)) { lat = ll[0].toDouble(); lng = ll[1].toDouble() }
    }
    var address = ""
    val la = lat; val lo = lng
    if (la != null && lo != null) runCatching {
        @Suppress("DEPRECATION")
        address = Geocoder(context, Locale("es", "MX")).getFromLocation(la, lo, 1)?.firstOrNull()?.getAddressLine(0).orEmpty()
    }
    return MediaDetails(
        date = longDate.format(Date(date)).replace("AM", "a.m.").replace("PM", "p.m.").replace("a. m.", "a.m.").replace("p. m.", "p.m."),
        folder = prettyFolder(file), device = device, lens = lens, line1 = l1, line2 = l2, lat = lat, lng = lng, address = address,
    )
}

/** Hoja con la información de una foto o video (sube desde abajo; se cierra deslizando hacia abajo o tocando fuera). */
@Composable
fun MediaInfoSheet(file: File, isVideo: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val d by produceState<MediaDetails?>(null, file) { value = withContext(Dispatchers.IO) { loadDetails(context, file, isVideo) } }
    val appear = remember { Animatable(0f) }
    val drag = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, spring(dampingRatio = 0.85f, stiffness = 300f)) }
    val close: () -> Unit = { scope.launch { appear.animateTo(0f, tween(160)); onDismiss() } }
    val soft = Color.White.copy(alpha = 0.6f)

    Box(
        Modifier.fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = close),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            Modifier.fillMaxWidth().fillMaxHeight(0.56f)
                .graphicsLayer { translationY = drag.value + (1f - appear.value) * size.height }
                .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                .background(Color.Black)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .pointerInput(Unit) {
                    detectVerticalDragGestures(onDragEnd = {
                        scope.launch {
                            if (drag.value > 140f) { drag.animateTo(2000f, tween(160)); onDismiss() } else drag.animateTo(0f, spring())
                        }
                    }) { c, dy -> c.consume(); scope.launch { drag.snapTo((drag.value + dy).coerceAtLeast(0f)) } }
                }
                .navigationBarsPadding()
        ) {
            Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(38.dp, 4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.3f)))
            }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 14.dp)) {
                val info = d
                Text(info?.date ?: "…", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(file.name, color = soft, fontSize = 14.sp)
                Text(info?.folder ?: "", color = soft, fontSize = 14.sp)
                if (info == null) return@Column
                Spacer(Modifier.height(20.dp))
                if (info.device.isNotBlank()) Text(info.device, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                else Text(if (isVideo) "Video" else "Imagen", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                if (info.lens.isNotBlank()) Text(info.lens, color = soft, fontSize = 14.sp)
                PipeLine(info.line1, soft)
                PipeLine(info.line2, soft)
                val la = info.lat; val lo = info.lng
                if (la != null && lo != null) {
                    Spacer(Modifier.height(20.dp))
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF1C1C1E))
                            .clickable {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:$la,$lo?q=$la,$lo(${Uri.encode(file.name)})"))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                }
                            }.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Place, null, tint = Color(0xFFFF5A5F), modifier = Modifier.size(30.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(info.address.ifBlank { "Ubicación de la toma" }, color = Color.White, fontSize = 14.sp)
                            Text(String.format(Locale.US, "%.5f, %.5f · toca para ver en el mapa", la, lo), color = soft, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PipeLine(parts: List<String>, color: Color) {
    if (parts.isEmpty()) return
    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        parts.forEachIndexed { i, p ->
            if (i > 0) Box(Modifier.padding(horizontal = 10.dp).width(1.dp).height(14.dp).background(color.copy(alpha = 0.4f)))
            Text(p, color = color, fontSize = 14.sp)
        }
    }
}
