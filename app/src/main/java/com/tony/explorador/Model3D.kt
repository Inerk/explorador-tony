package com.tony.explorador

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.util.LruCache
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.DataInputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.zip.ZipFile
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

fun is3dModel(f: File) = f.extension.lowercase(Locale.ROOT).let { it == "stl" || it == "3mf" }

/** Malla de triángulos: 9 números por triángulo (x,y,z de sus 3 puntas). */
class Mesh(val tris: FloatArray) {
    val count get() = tris.size / 9
}

object Model3D {
    private val meshes = LruCache<String, Mesh>(3)
    private val thumbs = LruCache<String, Bitmap>(60)
    private const val MAX_TRIS = 600_000

    private fun key(f: File) = f.absolutePath + ":" + f.lastModified() + ":" + f.length()

    fun peekThumb(f: File): Bitmap? = thumbs.get(key(f))

    /** Lee STL (binario o texto) y 3MF. Si es enorme, toma una muestra para no acabarse la memoria. */
    fun load(f: File): Mesh {
        meshes.get(key(f))?.let { return it }
        val m = when (f.extension.lowercase(Locale.ROOT)) { "3mf" -> load3mf(f); else -> loadStl(f) }
        meshes.put(key(f), m)
        return m
    }

    private fun loadStl(f: File): Mesh {
        val len = f.length()
        val head = f.inputStream().use { it.readNBytes(84) }
        val n = if (head.size >= 84) ByteBuffer.wrap(head, 80, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xffffffffL else -1
        val isBinary = n >= 0 && 84 + n * 50 == len
        if (isBinary) {
            val step = ((n + MAX_TRIS - 1) / MAX_TRIS).coerceAtLeast(1).toInt()
            val out = FloatArray(((n / step) + 1).toInt() * 9)
            var o = 0
            DataInputStream(f.inputStream().buffered(1 shl 16)).use { inp ->
                inp.skipBytes(84)
                val rec = ByteArray(50)
                val bb = ByteBuffer.wrap(rec).order(ByteOrder.LITTLE_ENDIAN)
                for (i in 0 until n) {
                    inp.readFully(rec)
                    if (i % step != 0L) continue
                    if (o + 9 > out.size) break
                    for (k in 0 until 9) out[o + k] = bb.getFloat(12 + k * 4)
                    o += 9
                }
            }
            return Mesh(out.copyOf(o))
        }
        // STL de texto
        val list = ArrayList<Float>()
        f.bufferedReader().useLines { lines ->
            for (l in lines) {
                val t = l.trim()
                if (t.startsWith("vertex")) {
                    val p = t.split(Regex("\\s+"))
                    if (p.size >= 4) { list += p[1].toFloat(); list += p[2].toFloat(); list += p[3].toFloat() }
                    if (list.size >= MAX_TRIS * 9) break
                }
            }
        }
        return Mesh(FloatArray(list.size - list.size % 9) { list[it] })
    }

    private fun load3mf(f: File): Mesh = ZipFile(f).use { zip ->
        val out = ArrayList<FloatArray>()
        var total = 0
        val models = zip.entries().toList().filter { it.name.lowercase(Locale.ROOT).endsWith(".model") }
        for (e in models) {
            val p = android.util.Xml.newPullParser().apply { setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false); setInput(zip.getInputStream(e), null) }
            var verts = FloatArrayList()
            val tris = FloatArrayList()
            while (p.next() != XmlPullParser.END_DOCUMENT) {
                if (p.eventType != XmlPullParser.START_TAG) continue
                val name = p.name.substringAfter(':')
                when (name) {
                    "vertices" -> verts = FloatArrayList()
                    "vertex" -> { verts.add(p.getAttributeValue(null, "x")?.toFloatOrNull() ?: 0f); verts.add(p.getAttributeValue(null, "y")?.toFloatOrNull() ?: 0f)
                        verts.add(p.getAttributeValue(null, "z")?.toFloatOrNull() ?: 0f) }
                    "triangle" -> if (total < MAX_TRIS) {
                        val a = p.getAttributeValue(null, "v1")?.toIntOrNull() ?: continue
                        val b = p.getAttributeValue(null, "v2")?.toIntOrNull() ?: continue
                        val c = p.getAttributeValue(null, "v3")?.toIntOrNull() ?: continue
                        for (v in intArrayOf(a, b, c)) { tris.add(verts[v * 3]); tris.add(verts[v * 3 + 1]); tris.add(verts[v * 3 + 2]) }
                        total++
                    }
                }
            }
            out += tris.toArray()
        }
        val all = FloatArray(out.sumOf { it.size }); var o = 0
        out.forEach { System.arraycopy(it, 0, all, o, it.size); o += it.size }
        Mesh(all)
    }

    /** La miniatura que guardan las impresoras/slicers dentro del 3MF, si trae. */
    private fun embedded3mfThumb(f: File): Bitmap? = runCatching {
        ZipFile(f).use { zip ->
            val e = zip.entries().toList().firstOrNull { it.name.lowercase(Locale.ROOT).let { n -> n.contains("thumbnail") && (n.endsWith(".png") || n.endsWith(".jpg")) } }
                ?: return null
            zip.getInputStream(e).use { BitmapFactory.decodeStream(it) }
        }
    }.getOrNull()

    /** Miniatura para el explorador (con caché). */
    fun thumb(f: File, size: Int = 256): Bitmap? {
        peekThumb(f)?.let { return it }
        val b = runCatching {
            (if (f.extension.equals("3mf", true)) embedded3mfThumb(f) else null) ?: render(load(f), size, -35f, 28f, 1f, decimate = 60_000)
        }.getOrNull() ?: return null
        thumbs.put(key(f), b)
        return b
    }

    /**
     * Dibuja la malla vista desde (yaw, pitch) con luz suave, del fondo hacia el frente.
     * Sin OpenGL: rápido para miniaturas y suficiente para girar en el visor.
     */
    fun render(m: Mesh, size: Int, yawDeg: Float, pitchDeg: Float, zoom: Float, decimate: Int = 200_000,
               color: Int = 0xFF8E5BE8.toInt()): Bitmap {
        val t = m.tris
        val n = m.count
        val step = ((n + decimate - 1) / decimate).coerceAtLeast(1)
        // caja que lo envuelve
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE; var minZ = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE; var maxZ = -Float.MAX_VALUE
        var i = 0
        while (i < t.size) {
            val x = t[i]; val y = t[i + 1]; val z = t[i + 2]
            if (x < minX) minX = x; if (x > maxX) maxX = x
            if (y < minY) minY = y; if (y > maxY) maxY = y
            if (z < minZ) minZ = z; if (z > maxZ) maxZ = z
            i += 3
        }
        val cx = (minX + maxX) / 2; val cy = (minY + maxY) / 2; val cz = (minZ + maxZ) / 2
        val radius = sqrt(((maxX - minX) * (maxX - minX) + (maxY - minY) * (maxY - minY) + (maxZ - minZ) * (maxZ - minZ)).toDouble()).toFloat() / 2f
        val scale = if (radius > 0) size * 0.46f / radius * zoom else 1f
        val yaw = Math.toRadians(yawDeg.toDouble()); val pitch = Math.toRadians(pitchDeg.toDouble())
        val cyw = cos(yaw).toFloat(); val syw = sin(yaw).toFloat(); val cp = cos(pitch).toFloat(); val sp = sin(pitch).toFloat()
        val cnt = (n + step - 1) / step
        val px = FloatArray(cnt * 3); val py = FloatArray(cnt * 3); val depth = FloatArray(cnt); val shade = FloatArray(cnt)
        var k = 0
        var tri = 0
        // luz desde arriba-izquierda-frente
        val lx = -0.35f; val ly = -0.55f; val lz = 0.76f
        while (tri < n) {
            val b = tri * 9
            var dsum = 0f
            val sx = FloatArray(3); val sy = FloatArray(3); val sz = FloatArray(3)
            for (v in 0 until 3) {
                // las impresoras 3D usan Z hacia arriba
                val x = t[b + v * 3] - cx; val y = t[b + v * 3 + 1] - cy; val z = t[b + v * 3 + 2] - cz
                val x1 = x * cyw - y * syw
                val y1 = x * syw + y * cyw
                // inclinación
                val yy = z * cp - y1 * sp
                val zz = z * sp + y1 * cp
                sx[v] = x1; sy[v] = yy; sz[v] = zz
                px[k * 3 + v] = size / 2f + x1 * scale
                py[k * 3 + v] = size / 2f - yy * scale
                dsum += zz
            }
            // normal en vista
            val ux = sx[1] - sx[0]; val uy = sy[1] - sy[0]; val uz = sz[1] - sz[0]
            val vx = sx[2] - sx[0]; val vy = sy[2] - sy[0]; val vz = sz[2] - sz[0]
            var nx = uy * vz - uz * vy; var ny = uz * vx - ux * vz; var nz = ux * vy - uy * vx
            val len = sqrt(nx * nx + ny * ny + nz * nz).let { if (it == 0f) 1f else it }
            nx /= len; ny /= len; nz /= len
            if (nz > 0) { nx = -nx; ny = -ny; nz = -nz }
            val lambert = kotlin.math.abs(nx * lx + ny * ly + nz * lz)
            shade[k] = 0.28f + 0.72f * lambert
            depth[k] = dsum
            k++
            tri += step
        }
        val order = (0 until k).sortedBy { depth[it] }
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp)
        val paint = Paint().apply { isAntiAlias = n / step < 40_000; style = Paint.Style.FILL_AND_STROKE; strokeWidth = 0.6f }
        val path = android.graphics.Path()
        val r = (color shr 16) and 0xff; val g = (color shr 8) and 0xff; val bl = color and 0xff
        for (j in order) {
            val s = shade[j]
            paint.color = (0xff shl 24) or ((r * s).toInt().coerceIn(0, 255) shl 16) or ((g * s).toInt().coerceIn(0, 255) shl 8) or (bl * s).toInt().coerceIn(0, 255)
            path.reset()
            path.moveTo(px[j * 3], py[j * 3]); path.lineTo(px[j * 3 + 1], py[j * 3 + 1]); path.lineTo(px[j * 3 + 2], py[j * 3 + 2]); path.close()
            c.drawPath(path, paint)
        }
        return bmp
    }
}

private class FloatArrayList {
    private var a = FloatArray(1024); var size = 0; private set
    fun add(v: Float) { if (size == a.size) a = a.copyOf(a.size * 2); a[size++] = v }
    operator fun get(i: Int) = a[i]
    fun toArray() = a.copyOf(size)
}

/** Miniatura del modelo en el explorador. */
@Composable
fun Model3DThumb(f: File, iconSize: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    val b by produceState(Model3D.peekThumb(f), f) { if (value == null) value = withContext(Dispatchers.Default) { runCatching { Model3D.thumb(f) }.getOrNull() } }
    Box(modifier.background(Brush.radialGradient(listOf(Color(0xFFF4F0FC), Color(0xFFDCD3F0)))), contentAlignment = Alignment.Center) {
        val bmp = b
        if (bmp != null) Image(remember(bmp) { bmp.asImageBitmap() }, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(4.dp))
        else FriendlyIcon(f, FileKind.OTHER, iconSize)
        Text(f.extension.uppercase(Locale.ROOT), color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black,
            modifier = Modifier.align(Alignment.BottomStart).padding(3.dp).clip(CircleShape).background(Color(0xFF8E5BE8)).padding(horizontal = 5.dp, vertical = 1.dp))
    }
}

/** Vista que se gira con el dedo y se acerca con dos dedos (panel y visor). */
@Composable
fun Model3DView(f: File, modifier: Modifier = Modifier) {
    val mesh by produceState<Result<Mesh>?>(null, f) { value = withContext(Dispatchers.IO) { runCatching { Model3D.load(f) } } }
    var yaw by remember { mutableFloatStateOf(-35f) }
    var pitch by remember { mutableFloatStateOf(28f) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var moving by remember { mutableStateOf(false) }
    var lastMove by remember { mutableStateOf(0L) }
    val m = mesh
    val img by produceState<Bitmap?>(null, m, yaw, pitch, zoom, moving) {
        val mm = m?.getOrNull() ?: return@produceState
        value = withContext(Dispatchers.Default) {
            // mientras giras: más rápido (menos detalle); al soltar: con todo el detalle
            Model3D.render(mm, if (moving) 420 else 900, yaw, pitch, zoom, decimate = if (moving) 25_000 else 300_000)
        }
    }
    LaunchedEffect(lastMove) { delay(250); moving = false }
    Box(modifier.background(Brush.radialGradient(listOf(Color(0xFFF7F4FD), Color(0xFFD9CFF0))))
        .pointerInput(Unit) {
            detectTransformGestures { _, pan, z, _ ->
                yaw += pan.x * 0.4f; pitch = (pitch + pan.y * 0.4f).coerceIn(-89f, 89f); zoom = (zoom * z).coerceIn(0.3f, 6f)
                moving = true; lastMove = System.nanoTime()
            }
        }, contentAlignment = Alignment.Center) {
        when {
            m == null -> CircularProgressIndicator(color = Color(0xFF8E5BE8))
            m.isFailure -> Text("No se pudo leer el modelo", color = Color(0xFFD93A3A))
            img != null -> Image(remember(img) { img!!.asImageBitmap() }, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        }
        m?.getOrNull()?.let {
            Text("${"%,d".format(it.count)} triángulos · gira con el dedo, acerca con dos", color = Color(0xFF6A5A8E), fontSize = 11.sp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(6.dp))
        }
    }
}

/** Visor a pantalla completa de STL / 3MF. */
@Composable
fun Model3DViewer(f: File, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    Surface(Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}, color = Color(0xFFF4F0FC)) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().background(Color(0xFF6A3FD0)).statusBarsPadding().height(54.dp).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Cerrar", tint = Color.White, modifier = Modifier.size(22.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(f.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Modelo 3D (${f.extension.uppercase(Locale.ROOT)}) · ${formatSize(f.length())}", color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp)
                }
            }
            Model3DView(f, Modifier.weight(1f).fillMaxWidth().navigationBarsPadding())
        }
    }
}
