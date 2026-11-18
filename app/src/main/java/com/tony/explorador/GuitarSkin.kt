package com.tony.explorador

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * Tema «Guitar Nebula»: un escenario de concierto con luces, público, guitarra y amplificador,
 * y una pista de 5 carriles (como Guitar Hero) donde bajan notas al ritmo de la música.
 */

object Guitar {
    val Night = Color(0xFF0A0820)
    val Deep = Color(0xFF05040F)
    val Purple = Color(0xFFA35CFF)
    val PurpleLight = Color(0xFFD3A8FF)
    val Blue = Color(0xFF4C7DFF)
    val lanes = listOf(Color(0xFF3DFF5A), Color(0xFFFF3B4F), Color(0xFFFFD43B), Color(0xFF2FA8FF), Color(0xFFFF8A1F))
}

val isGuitarSkin get() = LibraryStore.musicSkin == MusicSkin.GUITAR

fun guitarAccent(cover: Color): Color = lerp(Guitar.Purple, cover, 0.25f)

/** Notas que bajan por la pista. Cada carril escucha un pedazo del sonido (graves a la izquierda, agudos a la derecha). */
class GuitarSim {
    class Note(val lane: Int, var z: Float)
    val notes = ArrayList<Note>()
    val flash = FloatArray(5)
    val energy = FloatArray(5)
    private val avg = FloatArray(5)
    private val cool = FloatArray(5)
    var scroll = 0f
    private var globalCool = 0f
    private val groups = listOf(0..2, 3..5, 6..8, 9..12, 13 until SpectrumAnalyzer.BANDS)

    fun update(beat: BocinaBeat, playing: Boolean, dt: Float) {
        scroll = (scroll + dt * (0.6f + beat.level * 0.9f) * (if (playing) 1f else 0.15f)) % 1f
        // solo los 1 o 2 carriles que más subieron sueltan nota (así no bajan en filas parejas)
        globalCool -= dt
        val cand = ArrayList<Pair<Int, Float>>()
        for (i in 0 until 5) {
            val g = groups[i]
            var e = 0f; for (b in g) e += beat.bands.getOrElse(b) { 0f }; e /= g.count()
            energy[i] = e
            cool[i] -= dt
            val jump = e - (avg[i] * 1.18f + 0.04f)
            if (playing && cool[i] <= 0f && jump > 0f) cand.add(i to jump / (avg[i] + 0.05f))
            avg[i] = avg[i] * 0.94f + e * 0.06f
            flash[i] = (flash[i] - dt * 3.2f).coerceAtLeast(0f)
        }
        if (cand.isNotEmpty() && globalCool <= 0f && notes.size < 24) {
            cand.sortByDescending { it.second }
            val take = if (cand.size >= 3 && cand[1].second > 0.8f) 2 else 1
            cand.take(take).forEach { (i, _) -> notes.add(Note(i, 0f)); cool[i] = 0.30f }
            globalCool = 0.11f
        }
        val speed = if (playing) 1f / 1.15f else 0.25f
        val it = notes.iterator()
        while (it.hasNext()) {
            val n = it.next()
            n.z += dt * speed
            if (n.z >= 1f) { flash[n.lane] = 1f; it.remove() }
        }
    }
}

@Composable
fun rememberGuitarSim(beat: BocinaBeat, playing: Boolean): GuitarSim {
    val sim = remember { GuitarSim() }
    val isPlaying by rememberUpdatedState(playing)
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) withFrameNanos { now ->
            val dt = if (last == 0L) 0.016f else ((now - last) / 1e9f).coerceIn(0f, 0.05f)
            last = now
            sim.update(beat, isPlaying, dt)
        }
    }
    return sim
}

private fun DrawScope.img(b: ImageBitmap, x: Float, y: Float, w: Float, h: Float, alpha: Float = 1f) =
    drawImage(b, dstOffset = IntOffset(x.roundToInt(), y.roundToInt()), dstSize = IntSize(w.roundToInt().coerceAtLeast(1), h.roundToInt().coerceAtLeast(1)), alpha = alpha)

/** Fondo: el escenario con reflectores que laten, la guitarra, el amplificador y el público. */
@Composable
fun GuitarBackground(accent: Color, beat: BocinaBeat, modifier: Modifier = Modifier) {
    val stage = ImageBitmap.imageResource(R.drawable.gn_escenario)
    val crowd = ImageBitmap.imageResource(R.drawable.gn_publico)
    val guitar = ImageBitmap.imageResource(R.drawable.gn_guitarra)
    val amp = ImageBitmap.imageResource(R.drawable.gn_ampli)
    Canvas(modifier.fillMaxSize()) {
        beat.frame
        val w = size.width; val h = size.height
        drawRect(Brush.verticalGradient(listOf(Color(0xFF120C34), Guitar.Night, Guitar.Deep)))
        // escenario arriba, a todo lo ancho
        val sh = w * stage.height / stage.width * 1.25f
        img(stage, -w * 0.12f, -sh * 0.04f, w * 1.24f, sh, 0.95f)
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Guitar.Night), sh * 0.55f, sh * 1.0f), Offset(0f, sh * 0.5f), Size(w, h))
        // reflectores que laten con la música
        val pulse = 0.35f + beat.bass * 0.65f
        fun beam(x: Float, c: Color, ang: Float) {
            val p = Path().apply {
                moveTo(x, h * 0.05f); lineTo(x + ang - w * 0.10f, h * 0.55f); lineTo(x + ang + w * 0.10f, h * 0.55f); close()
            }
            drawPath(p, Brush.verticalGradient(listOf(c.copy(alpha = 0.22f * pulse), Color.Transparent), h * 0.05f, h * 0.55f), blendMode = BlendMode.Plus)
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.9f * pulse), c.copy(alpha = 0.5f * pulse), Color.Transparent), Offset(x, h * 0.05f), 18.dp.toPx()),
                18.dp.toPx(), Offset(x, h * 0.05f), blendMode = BlendMode.Plus)
        }
        val sway = sin(beat.time * 0.8f) * w * 0.06f
        beam(w * 0.08f, Guitar.Blue, w * 0.18f + sway)
        beam(w * 0.22f, Guitar.Purple, w * 0.10f - sway)
        beam(w * 0.78f, Guitar.Purple, -w * 0.10f + sway)
        beam(w * 0.92f, Guitar.Blue, -w * 0.18f - sway)
        // tinte con el color de la canción
        drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.18f), Color.Transparent), Offset(w / 2, h * 0.3f), w * 0.8f), w * 0.8f, Offset(w / 2, h * 0.3f))
        // guitarra a la izquierda y amplificador a la derecha
        val gw = w * 0.30f; val gh = gw * guitar.height / guitar.width
        img(guitar, -gw * 0.30f, h * 0.24f, gw, gh, 0.85f)
        val aw = w * 0.26f; val ah = aw * amp.height / amp.width
        img(amp, w - aw * 0.62f, h * 0.27f - beat.bass * 3.dp.toPx(), aw, ah, 0.85f)
        // público a los lados (brinca con los bajos)
        val cw = w * 0.62f; val ch = cw * crowd.height / crowd.width
        val jump = beat.bass * 7.dp.toPx() + beat.kick * 4.dp.toPx()
        img(crowd, -cw * 0.12f, h * 0.50f - jump, cw, ch, 0.9f)
        img(crowd, w - cw * 0.88f, h * 0.51f - jump * 0.8f, cw, ch, 0.9f)
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Guitar.Deep.copy(alpha = 0.85f)), h * 0.55f, h * 0.75f), Offset(0f, h * 0.55f), Size(w, h * 0.45f))
    }
}

/**
 * La pista con perspectiva. [y0] arriba (lejos) y [y1] abajo (cerca), en pixeles del Canvas.
 */
fun DrawScope.drawGuitarHighway(sim: GuitarSim, beat: BocinaBeat, alpha: Float, y0: Float, y1: Float,
                                gems: List<ImageBitmap>) {
    if (alpha <= 0f) return
    val w = size.width; val cx = w / 2f
    val wt = w * 0.30f; val wb = w * 0.98f
    fun proj(z: Float): Float { val d = 4f + (1f - 4f) * z; return (1f / d - 0.25f) / 0.75f }
    fun yAt(z: Float) = y0 + (y1 - y0) * proj(z)
    fun widthAt(z: Float) = wt + (wb - wt) * proj(z)
    fun laneX(lane: Int, z: Float) = cx + (lane - 2) * widthAt(z) / 5f

    // piso de la pista
    val road = Path().apply {
        moveTo(cx - wt / 2, y0); lineTo(cx + wt / 2, y0); lineTo(cx + wb / 2, y1); lineTo(cx - wb / 2, y1); close()
    }
    drawPath(road, Brush.verticalGradient(listOf(Color(0xFF0E0B26).copy(alpha = 0.85f * alpha), Color(0xFF15102F).copy(alpha = 0.95f * alpha)), y0, y1))
    // rayas que bajan (se siente que la pista avanza)
    for (k in 0 until 8) {
        val z = ((k / 8f) + sim.scroll) % 1f
        val y = yAt(z); val hw = widthAt(z) / 2
        drawLine(Color.White.copy(alpha = 0.10f * z * alpha), Offset(cx - hw, y), Offset(cx + hw, y), (0.6f + z * 1.4f).dp.toPx())
    }
    // separaciones de carriles
    for (i in 0..5) {
        val xa = cx + (i - 2.5f) * wt / 5f; val xb = cx + (i - 2.5f) * wb / 5f
        drawLine(Color.White.copy(alpha = 0.06f * alpha), Offset(xa, y0), Offset(xb, y1), 1.dp.toPx())
    }
    // bordes de neón
    for (side in listOf(-1f, 1f)) {
        val a = Offset(cx + side * wt / 2, y0); val b = Offset(cx + side * wb / 2, y1)
        listOf(14f to 0.08f, 6f to 0.22f, 2f to 0.9f).forEach { (sw, al) ->
            drawLine(Brush.linearGradient(listOf(Guitar.Blue.copy(alpha = al * alpha), Guitar.Purple.copy(alpha = al * alpha)), a, b), a, b, sw.dp.toPx(), StrokeCap.Round)
        }
    }
    // cuerdas de colores (brillan más con su sonido)
    val padY = y1 - (y1 - y0) * 0.075f
    for (i in 0 until 5) {
        val c = Guitar.lanes[i]
        val top = Offset(laneX(i, 0f), y0); val bot = Offset(laneX(i, 0.96f), padY)
        val glow = (0.45f + sim.energy[i] * 0.9f + sim.flash[i] * 0.5f).coerceAtMost(1f)
        drawLine(c.copy(alpha = 0.10f * glow * alpha), top, bot, 9.dp.toPx(), StrokeCap.Round)
        drawLine(c.copy(alpha = 0.95f * glow * alpha), top, bot, 1.8.dp.toPx(), StrokeCap.Round)
    }
    // notas que bajan (de lejos a cerca)
    val padW = wb / 5f * 0.86f
    for (n in sim.notes.sortedBy { it.z }) {
        val z = n.z.coerceIn(0f, 1f)
        val nw = padW * 0.72f * widthAt(z) / wb
        val nh = nw * gems[n.lane].height / gems[n.lane].width
        val x = laneX(n.lane, z); val y = yAt(z) * 1f
        if (y > padY) continue
        drawCircle(Brush.radialGradient(listOf(Guitar.lanes[n.lane].copy(alpha = 0.45f * alpha), Color.Transparent), Offset(x, y), nw * 0.8f), nw * 0.8f, Offset(x, y))
        img(gems[n.lane], x - nw / 2, y - nh / 2, nw, nh, alpha * (0.4f + 0.6f * z))
    }
    // botones de abajo: se prenden y sueltan fuego cuando les llega una nota
    for (i in 0 until 5) {
        val c = Guitar.lanes[i]
        val x = laneX(i, 1f)
        val ph = padW * gems[i].height / gems[i].width
        val f = sim.flash[i]
        val lit = (sim.energy[i] * 0.6f + f).coerceAtMost(1f)
        // fuego
        if (f > 0.02f) {
            val fh = padW * (0.9f + f * 0.9f)
            val flame = Path().apply {
                moveTo(x - padW * 0.34f, padY); quadraticBezierTo(x - padW * 0.2f, padY - fh * 0.6f, x, padY - fh)
                quadraticBezierTo(x + padW * 0.2f, padY - fh * 0.6f, x + padW * 0.34f, padY); close()
            }
            drawPath(flame, Brush.verticalGradient(listOf(Color.Transparent, c.copy(alpha = 0.55f * f * alpha), Color.White.copy(alpha = 0.8f * f * alpha)), padY - fh, padY),
                blendMode = BlendMode.Plus)
        }
        drawCircle(Brush.radialGradient(listOf(c.copy(alpha = (0.25f + lit * 0.55f) * alpha), Color.Transparent), Offset(x, padY), padW * 0.85f), padW * 0.85f, Offset(x, padY))
        img(gems[i], x - padW / 2, padY - ph / 2, padW, ph, alpha)
        if (lit > 0.05f) drawOval(Color.White.copy(alpha = 0.35f * lit * alpha), Offset(x - padW * 0.3f, padY - ph * 0.22f), Size(padW * 0.6f, ph * 0.3f), blendMode = BlendMode.Plus)
    }
}

@Composable
fun GuitarStage(sim: GuitarSim, beat: BocinaBeat, alpha: Float, y0: Dp, y1: Dp, coverRect: Pair<Offset, Float>?, accent: Color, modifier: Modifier = Modifier) {
    val gems = listOf(
        ImageBitmap.imageResource(R.drawable.gn_gema_verde), ImageBitmap.imageResource(R.drawable.gn_gema_roja),
        ImageBitmap.imageResource(R.drawable.gn_gema_amarilla), ImageBitmap.imageResource(R.drawable.gn_gema_azul),
        ImageBitmap.imageResource(R.drawable.gn_gema_naranja),
    )
    Canvas(modifier.fillMaxSize()) {
        beat.frame
        // barras a los lados de la portada
        coverRect?.let { (tl, side) ->
            val s = side.dp.toPx(); val cy = tl.y.dp.toPx() + s * 0.62f
            val n = 7; val gap = 9.dp.toPx(); val bw = 5.dp.toPx()
            for (dir in listOf(-1f, 1f)) for (i in 0 until n) {
                val x = if (dir < 0) tl.x.dp.toPx() - 10.dp.toPx() - i * gap else tl.x.dp.toPx() + s + 10.dp.toPx() + i * gap
                val band = beat.bandAt(i / (n - 1f) * 0.8f)
                val hh = s * (0.10f + band * 0.55f) * (1f - i * 0.09f)
                val c = lerp(Guitar.Blue, Guitar.Purple, i / (n - 1f))
                drawLine(c.copy(alpha = 0.25f * alpha), Offset(x, cy - hh), Offset(x, cy + hh * 0.15f), bw * 2.2f, StrokeCap.Round)
                drawLine(lerp(c, Color.White, 0.35f).copy(alpha = 0.95f * alpha), Offset(x, cy - hh), Offset(x, cy + hh * 0.15f), bw, StrokeCap.Round)
            }
            // aro grande detrás de la portada
            val ccx = tl.x.dp.toPx() + s / 2; val ccy = tl.y.dp.toPx() + s / 2
            drawCircle(Brush.sweepGradient(listOf(Guitar.Blue, Guitar.Purple, accent, Guitar.Blue), Offset(ccx, ccy)), s * (0.95f + beat.bass * 0.05f),
                Offset(ccx, ccy), alpha = 0.35f * alpha, style = Stroke(1.5.dp.toPx()))
        }
        drawGuitarHighway(sim, beat, alpha, y0.toPx(), y1.toPx(), gems)
    }
}

// ───────── controles ─────────

@Composable
fun GuitarSquareButton(icon: ImageVector, description: String, tint: Color = Color.White, size: Dp = 52.dp, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(size * 0.30f)
    Box(
        Modifier.size(size).clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF2A2650), Color(0xFF15122E))))
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), Guitar.Purple.copy(alpha = 0.15f))), shape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, description, tint = if (enabled) tint else tint.copy(alpha = 0.35f), modifier = Modifier.size(size * 0.48f)) }
}

@Composable
fun GuitarPlayButton(icon: ImageVector, beat: BocinaBeat?, size: Dp = 82.dp, onClick: () -> Unit) {
    Box(
        Modifier.size(size)
            .drawBehind {
                beat?.frame
                val b = beat?.bass ?: 0f
                val r = this.size.minDimension / 2f
                val gr = r * (1.5f + b * 0.2f)
                drawCircle(Brush.radialGradient(0.55f to Guitar.Purple.copy(alpha = 0.45f + b * 0.3f), 1f to Color.Transparent, center = center, radius = gr), gr)
            }
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(Color(0xFF2B1F5C), Color(0xFF120D2C))))
            .drawWithContent {
                drawContent()
                val r = this.size.minDimension / 2f
                drawCircle(Brush.sweepGradient(listOf(Guitar.PurpleLight, Guitar.Purple, Guitar.Blue, Guitar.Purple, Guitar.PurpleLight)), r - 2.5.dp.toPx(),
                    style = Stroke(4.dp.toPx()))
                drawCircle(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.18f), Color.Transparent), 0f, this.size.height * 0.5f), r)
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(size * 0.46f)) }
}

@Composable
fun GuitarTopBar(title: String, artist: String, onClose: () -> Unit, onMenu: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(42.dp).clip(CircleShape).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.KeyboardArrowDown, "Cerrar", tint = Color.White, modifier = Modifier.size(30.dp))
        }
        Column(Modifier.weight(1f).padding(horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("REPRODUCIENDO", fontSize = 11.sp, letterSpacing = 4.sp, color = Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.Medium)
            Text(title, fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center, modifier = Modifier.padding(top = 2.dp))
            if (artist.isNotEmpty()) Text(artist, fontSize = 14.sp, color = Color(0xFFBDB4E6), maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        }
        Box(Modifier.size(42.dp).clip(CircleShape).clickable(onClick = onMenu), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.MoreVert, "Más", tint = Color.White, modifier = Modifier.size(24.dp))
        }
    }
}
