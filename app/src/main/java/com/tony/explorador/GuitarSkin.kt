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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
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
    /** [timeMs] = cuándo suena el golpe en la canción (con mapa de ritmo); -1 si salió en vivo. */
    class Note(val lane: Int, var z: Float, val timeMs: Long = -1L)
    class Confetti(var x: Float, var y: Float, val vx: Float, val vy: Float, var rot: Float, val vr: Float, val color: Int, val size: Float)
    class Spark(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val lane: Int)
    val notes = ArrayList<Note>()
    val flash = FloatArray(5)
    val energy = FloatArray(5)
    private val avg = FloatArray(5)
    private val cool = FloatArray(5)
    var scroll = 0f
    private var globalCool = 0f
    private val groups = listOf(0..2, 3..5, 6..8, 9..12, 13 until SpectrumAnalyzer.BANDS)

    /** Tiempo que tarda una nota en bajar por toda la pista. */
    val travelMs = 1150L
    private var lastPos = -1L
    private var lastPath: String? = null
    private var spawnedTo = -1L

    // ambientación del concierto
    val confetti = ArrayList<Confetti>()
    val sparks = ArrayList<Spark>()
    val pyro = FloatArray(4)          // columnas de fuego del escenario
    var hype = 0f                     // qué tan prendida está la canción (0..1), sube en los coros
    private var pyroSide = 0
    private val rnd = kotlin.random.Random(7)

    fun update(beat: BocinaBeat, playing: Boolean, dt: Float) {
        scroll = (scroll + dt * (0.6f + beat.level * 0.9f) * (if (playing) 1f else 0.15f)) % 1f
        for (i in 0 until 5) {
            val g = groups[i]
            var e = 0f; for (b in g) e += beat.bands.getOrElse(b) { 0f }; e /= g.count()
            energy[i] = e
            flash[i] = (flash[i] - dt * 3.2f).coerceAtLeast(0f)
        }
        val map = beat.map
        if (map != null) mapNotes(map, beat) else liveNotes(playing, dt)
        ambience(beat, playing, dt)
    }

    /** Con mapa: la nota sale [travelMs] antes de su golpe y llega abajo justo cuando suena. */
    private fun mapNotes(map: BeatMap.Analysis, beat: BocinaBeat) {
        val pos = beat.posMs
        if (beat.path != lastPath || pos < lastPos - 300 || pos > lastPos + 1500) {
            // canción nueva o se adelantó/regresó: se rearma la pista
            notes.clear(); spawnedTo = pos; lastPath = beat.path
        }
        lastPos = pos
        val until = pos + travelMs
        if (until > spawnedTo) {
            var lo = 0; var hi = map.notes.size
            while (lo < hi) { val m = (lo + hi) / 2; if (map.notes[m].timeMs <= spawnedTo) lo = m + 1 else hi = m }
            var k = lo
            while (k < map.notes.size && map.notes[k].timeMs <= until) {
                val n = map.notes[k]
                if (n.timeMs > pos) notes.add(Note(n.lane, 0f, n.timeMs))
                k++
            }
            spawnedTo = until
        }
        val it = notes.iterator()
        while (it.hasNext()) {
            val n = it.next()
            if (n.timeMs < 0) { it.remove(); continue }
            n.z = 1f - (n.timeMs - pos).toFloat() / travelMs
            if (pos >= n.timeMs) { hit(n.lane); it.remove() }
        }
    }

    /** Sin mapa (mientras se calcula, o si el archivo no se puede leer): notas al vuelo. */
    private fun liveNotes(playing: Boolean, dt: Float) {
        globalCool -= dt
        val cand = ArrayList<Pair<Int, Float>>()
        for (i in 0 until 5) {
            val e = energy[i]
            cool[i] -= dt
            val jump = e - (avg[i] * 1.18f + 0.04f)
            if (playing && cool[i] <= 0f && jump > 0f) cand.add(i to jump / (avg[i] + 0.05f))
            avg[i] = avg[i] * 0.94f + e * 0.06f
        }
        if (cand.isNotEmpty() && globalCool <= 0f && notes.size < 24) {
            cand.sortByDescending { it.second }
            val take = if (cand.size >= 3 && cand[1].second > 0.8f) 2 else 1
            cand.take(take).forEach { (i, _) -> notes.add(Note(i, 0f)); cool[i] = 0.30f }
            globalCool = 0.11f
        }
        val speed = if (playing) 1000f / travelMs else 0.25f
        val it = notes.iterator()
        while (it.hasNext()) {
            val n = it.next()
            if (n.timeMs >= 0) { it.remove(); continue }
            n.z += dt * speed
            if (n.z >= 1f) { hit(n.lane); it.remove() }
        }
    }

    private fun hit(lane: Int) {
        flash[lane] = 1f
        // chispas que salen del botón (posición en 0..1 del carril; se dibujan relativo al botón)
        repeat(7) {
            sparks.add(Spark(0f, 0f, (rnd.nextFloat() - 0.5f) * 1.6f, -(0.8f + rnd.nextFloat() * 1.4f), 1f, lane))
        }
    }

    private fun ambience(beat: BocinaBeat, playing: Boolean, dt: Float) {
        hype = hype * 0.985f + (if (playing) beat.level else 0f) * 0.015f
        for (i in pyro.indices) pyro[i] = (pyro[i] - dt * 1.6f).coerceAtLeast(0f)
        // fuego del escenario en los golpes fuertes cuando la canción va arriba
        if (playing && beat.kick > 0.55f && beat.level > 0.42f && pyro.all { it < 0.35f }) {
            pyroSide = (pyroSide + 1) % 3
            when (pyroSide) { 0 -> { pyro[0] = 1f; pyro[3] = 1f }; 1 -> { pyro[1] = 1f; pyro[2] = 1f }; else -> pyro.fill(1f) }
        }
        // confeti cuando la canción está prendida
        val rate = if (playing) ((beat.level - 0.45f) * 60f + beat.kick * 20f).coerceAtLeast(0f) else 0f
        if (confetti.size < 90 && rnd.nextFloat() < rate * dt) {
            val colors = intArrayOf(0xFFFF4FD8.toInt(), 0xFF4FC3FF.toInt(), 0xFFB57BFF.toInt(), 0xFFFFE14F.toInt(), 0xFF5BFF9A.toInt())
            confetti.add(Confetti(rnd.nextFloat(), -0.02f, (rnd.nextFloat() - 0.5f) * 0.05f, 0.09f + rnd.nextFloat() * 0.08f,
                rnd.nextFloat() * 360f, (rnd.nextFloat() - 0.5f) * 400f, colors[rnd.nextInt(colors.size)], 0.6f + rnd.nextFloat() * 0.8f))
        }
        val ci = confetti.iterator()
        while (ci.hasNext()) {
            val c = ci.next()
            c.x += (c.vx + sin(c.rot / 40f) * 0.02f) * dt; c.y += c.vy * dt; c.rot += c.vr * dt
            if (c.y > 0.62f) ci.remove()
        }
        val si = sparks.iterator()
        while (si.hasNext()) {
            val s = si.next()
            s.x += s.vx * dt; s.y += s.vy * dt; s.vy += 2.2f * dt; s.life -= dt * 1.8f
            if (s.life <= 0f) si.remove()
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

/** Fondo: la arena del concierto con reflectores, fuego, confeti, pantallas que laten y el público con manos y barras de luz. */
@Composable
fun GuitarBackground(accent: Color, beat: BocinaBeat, sim: GuitarSim? = null, modifier: Modifier = Modifier) {
    val arena = ImageBitmap.imageResource(R.drawable.gn_arena)
    val crowdA = ImageBitmap.imageResource(R.drawable.gn_publico_a)
    val crowdB = ImageBitmap.imageResource(R.drawable.gn_publico_b)
    val crowdC = ImageBitmap.imageResource(R.drawable.gn_publico_c)
    val crowdFar = ImageBitmap.imageResource(R.drawable.gn_publico_lejos)
    val hands = listOf(R.drawable.gn_mano_rock, R.drawable.gn_mano_paz, R.drawable.gn_mano_rock2, R.drawable.gn_mano_abierta,
        R.drawable.gn_mano_corazon, R.drawable.gn_mano_puno).map { ImageBitmap.imageResource(it) }
    val sticks = listOf(R.drawable.gn_barra_rosa, R.drawable.gn_barra_azul, R.drawable.gn_barra_morada).map { ImageBitmap.imageResource(it) }
    Canvas(modifier.fillMaxSize()) {
        beat.frame
        val w = size.width; val h = size.height; val t = beat.time
        drawRect(Brush.verticalGradient(listOf(Color(0xFF120C34), Guitar.Night, Guitar.Deep)))
        // la arena arriba (centrada en el escenario), un poquito más brillante con cada golpe
        val ah = h * 0.50f; val aw = ah * arena.width / arena.height
        val ax = (w - aw) / 2f
        img(arena, ax, 0f, aw, ah, 0.95f)
        drawRect(accent.copy(alpha = 0.10f * beat.bass + 0.06f * beat.kick), Offset(0f, 0f), Size(w, ah), blendMode = BlendMode.Plus)
        // reflectores que barren al ritmo
        val pulse = 0.35f + beat.bass * 0.65f
        fun beam(x: Float, c: Color, ang: Float) {
            val p = Path().apply {
                moveTo(x, h * 0.02f); lineTo(x + ang - w * 0.10f, h * 0.48f); lineTo(x + ang + w * 0.10f, h * 0.48f); close()
            }
            drawPath(p, Brush.verticalGradient(listOf(c.copy(alpha = 0.20f * pulse), Color.Transparent), h * 0.02f, h * 0.48f), blendMode = BlendMode.Plus)
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.85f * pulse), c.copy(alpha = 0.45f * pulse), Color.Transparent), Offset(x, h * 0.02f), 16.dp.toPx()),
                16.dp.toPx(), Offset(x, h * 0.02f), blendMode = BlendMode.Plus)
        }
        val sway = sin(t * 0.8f) * w * 0.07f
        beam(w * 0.10f, Guitar.Blue, w * 0.20f + sway)
        beam(w * 0.30f, Guitar.Purple, w * 0.06f - sway)
        beam(w * 0.70f, Guitar.Purple, -w * 0.06f + sway)
        beam(w * 0.90f, Guitar.Blue, -w * 0.20f - sway)

        // fuego del escenario (4 columnas) cuando pega fuerte
        if (sim != null) {
            val px = listOf(0.06f, 0.24f, 0.76f, 0.94f)
            for (i in 0 until 4) {
                val f = sim.pyro[i]; if (f <= 0.01f) continue
                val x = w * px[i]; val base = h * 0.40f; val fh = h * 0.22f * (0.4f + f * 0.6f)
                val c = if (i % 2 == 0) Color(0xFF4FC3FF) else Color(0xFFFF4FD8)
                val p = Path().apply {
                    moveTo(x - 9.dp.toPx(), base)
                    quadraticBezierTo(x - 14.dp.toPx(), base - fh * 0.5f, x + sin(t * 9f + i) * 4.dp.toPx(), base - fh)
                    quadraticBezierTo(x + 14.dp.toPx(), base - fh * 0.5f, x + 9.dp.toPx(), base); close()
                }
                drawPath(p, Brush.verticalGradient(listOf(Color.Transparent, c.copy(alpha = 0.7f * f), Color.White.copy(alpha = 0.9f * f)), base - fh, base), blendMode = BlendMode.Plus)
                drawCircle(Brush.radialGradient(listOf(c.copy(alpha = 0.45f * f), Color.Transparent), Offset(x, base - fh * 0.4f), fh * 0.6f), fh * 0.6f, Offset(x, base - fh * 0.4f), blendMode = BlendMode.Plus)
            }
        }
        // humo en el piso del escenario
        for (k in 0 until 3) {
            val sx = w * (0.2f + 0.3f * k) + sin(t * 0.3f + k * 2f) * w * 0.08f
            drawCircle(Brush.radialGradient(listOf(lerp(Guitar.Purple, Guitar.Blue, k / 2f).copy(alpha = 0.16f), Color.Transparent), Offset(sx, h * 0.47f), w * 0.35f),
                w * 0.35f, Offset(sx, h * 0.47f))
        }
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Guitar.Night), ah * 0.80f, ah * 1.02f), Offset(0f, ah * 0.78f), Size(w, h - ah * 0.78f))

        // público: el de atrás quieto, los de enfrente brincan con los bajos
        val jump = beat.bass * 6.dp.toPx() + beat.kick * 5.dp.toPx()
        val fw = w * 1.1f; val fh = fw * crowdFar.height / crowdFar.width
        img(crowdFar, -w * 0.05f, h * 0.40f - jump * 0.3f, fw, fh, 0.55f)
        // manos y barras de luz que se mecen arriba del público
        val people = listOf(0.04f, 0.13f, 0.23f, 0.77f, 0.87f, 0.96f)
        people.forEachIndexed { i, fx ->
            val ph = i * 1.37f
            val up = (sin(t * 3.2f + ph) * 0.5f + 0.5f) * (0.4f + beat.level) * 10.dp.toPx() + beat.kick * 8.dp.toPx() * (if (i % 2 == 0) 1f else 0.6f)
            val x = w * fx; val baseY = h * 0.50f
            if (i % 2 == 0) {
                val hand = hands[i % hands.size]; val hw = w * 0.085f; val hh = hw * hand.height / hand.width
                img(hand, x - hw / 2, baseY - hh - up, hw, hh, 0.95f)
            } else {
                val st = sticks[i % sticks.size]; val sw = w * 0.035f; val sh = sw * st.height / st.width
                val ang = sin(t * 2.6f + ph) * (14f + beat.level * 18f)
                withTransform({ rotate(ang, Offset(x, baseY - up)) }) {
                    drawCircle(Brush.radialGradient(listOf(Guitar.Purple.copy(alpha = 0.35f), Color.Transparent), Offset(x, baseY - up - sh * 0.5f), sh * 0.6f),
                        sh * 0.6f, Offset(x, baseY - up - sh * 0.5f), blendMode = BlendMode.Plus)
                    img(st, x - sw / 2, baseY - up - sh, sw, sh)
                }
            }
        }
        val cw = w * 0.66f
        val chA = cw * crowdA.height / crowdA.width
        img(crowdA, -cw * 0.22f, h * 0.47f - jump, cw, chA, 0.95f)
        val chB = cw * crowdB.height / crowdB.width
        img(crowdB, w - cw * 0.78f, h * 0.475f - jump * 0.8f, cw, chB, 0.95f)
        val chC = w * 0.5f * crowdC.height / crowdC.width
        img(crowdC, w * 0.25f, h * 0.53f - jump * 0.6f, w * 0.5f, chC, 0.5f)
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Guitar.Deep.copy(alpha = 0.9f)), h * 0.56f, h * 0.74f), Offset(0f, h * 0.56f), Size(w, h * 0.44f))

        // confeti que cae cuando la canción está prendida
        sim?.confetti?.forEach { c ->
            withTransform({ rotate(c.rot, Offset(c.x * w, c.y * h)) }) {
                val cs = c.size * 5.dp.toPx()
                drawRect(Color(c.color).copy(alpha = 0.9f), Offset(c.x * w - cs / 2, c.y * h - cs * 0.3f), Size(cs, cs * 0.6f))
            }
        }
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
        sim.sparks.forEach { sp ->
            if (sp.lane != i) return@forEach
            val px = x + sp.x * padW; val py = padY + sp.y * padW
            drawCircle(c.copy(alpha = sp.life * alpha), (1.5f + sp.life * 2f).dp.toPx(), Offset(px, py), blendMode = BlendMode.Plus)
            drawCircle(Color.White.copy(alpha = sp.life * 0.8f * alpha), (0.8f + sp.life).dp.toPx(), Offset(px, py))
        }
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
