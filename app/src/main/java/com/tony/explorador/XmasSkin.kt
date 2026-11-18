package com.tony.explorador

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/*
 * Tema «Navidad Nebula»: noche nevada con aurora, pueblito con luces, árbol y farol, una serie de foquitos
 * que cambian con cada golpe y nieve que cae al ritmo. La nieve se aparta de tu dedo y al tocar salen destellos.
 */

object Xmas {
    val Sky = Color(0xFF0A1C45)
    val Night = Color(0xFF07122E)
    val Deep = Color(0xFF040A1C)
    val Ice = Color(0xFFCDEBFF)
    val Blue = Color(0xFF3FA9FF)
    val Warm = Color(0xFFFFC56B)
    val Snow = Color(0xFFEAF5FF)
}

val isXmasSkin get() = LibraryStore.musicSkin == MusicSkin.NAVIDAD

fun xmasAccent(cover: Color): Color = lerp(lerp(Xmas.Blue, cover, 0.2f), Color.White, 0.1f)

/** Nieve, destellos y lo que hace tu dedo. Posiciones de 0 a 1 (fracción de la pantalla). */
class XmasFx {
    class Flake(var x: Float, var y: Float, val size: Float, val speed: Float, val phase: Float, val depth: Float, var spin: Float)
    class Sparkle(val x: Float, val y: Float, val vx: Float, val vy: Float, var life: Float, val gold: Boolean)
    private val rnd = Random(2412)
    val flakes = List(170) {
        val d = rnd.nextFloat()
        Flake(rnd.nextFloat(), rnd.nextFloat(), 0.6f + d * 2.4f, 0.04f + d * 0.10f, rnd.nextFloat() * 6.28f, d, rnd.nextFloat() * 360f)
    }
    val sparkles = ArrayList<Sparkle>()
    var touchX = -1f; var touchY = -1f; var touching = false
    var wind = 0f
    var time = 0f
    /** Cuenta los golpes: los foquitos cambian de color con cada uno. */
    var beats = 0
    private var lastKick = 0f

    fun touch(x: Float, y: Float, down: Boolean) { touchX = x; touchY = y; touching = down }

    fun burst(x: Float, y: Float) {
        repeat(16) {
            val a = rnd.nextFloat() * 6.28f; val s = 0.05f + rnd.nextFloat() * 0.18f
            sparkles.add(Sparkle(x, y, kotlin.math.cos(a) * s, kotlin.math.sin(a) * s - 0.05f, 1f, rnd.nextFloat() < 0.35f))
        }
        // la nieve cercana sale volando
        flakes.forEach { f ->
            val dx = f.x - x; val dy = f.y - y; val d = hypot(dx, dy)
            if (d < 0.18f && d > 0.001f) { f.x += dx / d * (0.18f - d) * 0.5f; f.y += dy / d * (0.18f - d) * 0.5f }
        }
    }

    fun update(beat: BocinaBeat, playing: Boolean, dt: Float) {
        time += dt
        if (beat.kick > 0.45f && lastKick <= 0.45f) beats++
        lastKick = beat.kick
        // ráfagas de viento con los golpes y un vaivén suave
        val target = sin(time * 0.25f) * 0.03f + (if (playing) beat.kick * 0.10f * (if (beats % 2 == 0) 1f else -1f) else 0f)
        wind += (target - wind) * (dt * 2.5f)
        val fall = if (playing) 1f + beat.level * 0.8f else 0.7f
        flakes.forEach { f ->
            f.y += f.speed * (0.5f + f.depth) * fall * dt
            f.x += (wind * (0.4f + f.depth) + sin(time * 1.3f + f.phase) * 0.012f) * dt
            f.spin += (20f + f.depth * 40f) * dt
            // se aparta del dedo
            if (touching && touchX >= 0f) {
                val dx = f.x - touchX; val dy = f.y - touchY; val d = hypot(dx, dy)
                if (d < 0.14f && d > 0.0005f) { val push = (0.14f - d) * 3.2f * dt; f.x += dx / d * push; f.y += dy / d * push }
            }
            if (f.y > 1.03f) { f.y = -0.03f; f.x = rnd.nextFloat() }
            if (f.x < -0.03f) f.x += 1.06f
            if (f.x > 1.03f) f.x -= 1.06f
        }
        val it = sparkles.iterator()
        while (it.hasNext()) { val s = it.next(); s.life -= dt * 1.3f; if (s.life <= 0f) it.remove() }
    }
}

@Composable
fun rememberXmasFx(beat: BocinaBeat, playing: Boolean): XmasFx {
    val fx = remember { XmasFx() }
    val isPlaying by rememberUpdatedState(playing)
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) withFrameNanos { now ->
            val dt = if (last == 0L) 0.016f else ((now - last) / 1e9f).coerceIn(0f, 0.05f)
            last = now
            fx.update(beat, isPlaying, dt)
        }
    }
    return fx
}

private fun DrawScope.img(b: ImageBitmap, x: Float, y: Float, w: Float, h: Float, alpha: Float = 1f) =
    drawImage(b, dstOffset = IntOffset(x.roundToInt(), y.roundToInt()), dstSize = IntSize(w.roundToInt().coerceAtLeast(1), h.roundToInt().coerceAtLeast(1)), alpha = alpha)

private val stars = List(60) { val r = Random(it * 97 + 3); floatArrayOf(r.nextFloat(), r.nextFloat() * 0.4f, r.nextFloat(), r.nextFloat()) }
private val bulbColors = listOf(Color(0xFFFFC56B), Color(0xFFFF5A5A), Color(0xFF6BFF8E), Color(0xFF6BC8FF), Color(0xFFFFE36B))

/** Fondo: cielo con aurora y estrellas, el pueblito, nieve en el piso, árbol con farol y serie de foquitos. */
@Composable
fun XmasBackground(accent: Color, beat: BocinaBeat, fx: XmasFx, modifier: Modifier = Modifier) {
    val town = ImageBitmap.imageResource(R.drawable.nv_pueblo)
    val tree = ImageBitmap.imageResource(R.drawable.nv_arbol)
    val holly = ImageBitmap.imageResource(R.drawable.nv_acebo)
    val flake = ImageBitmap.imageResource(R.drawable.nv_copo)
    Canvas(modifier.fillMaxSize()) {
        beat.frame
        val w = size.width; val h = size.height; val t = fx.time
        drawRect(Brush.verticalGradient(listOf(Xmas.Night, Xmas.Sky, Color(0xFF12306A), Xmas.Night), 0f, h))
        // estrellas que titilan
        stars.forEach { (x, y, s, p) ->
            val tw = 0.35f + 0.65f * ((sin(t * (1.2f + p * 2f) + p * 20f) + 1f) / 2f)
            drawCircle(Color.White.copy(alpha = 0.7f * tw), (0.7f + s * 1.3f).dp.toPx(), Offset(x * w, y * h))
        }
        // aurora: cortinas de luz suaves que ondulan y brillan más con la música
        val step = 3.dp.toPx()
        for (k in 0 until 2) {
            val base = h * (0.09f + k * 0.05f); val amp = h * 0.025f
            val c = if (k == 0) Color(0xFF3DFFC8) else Color(0xFF7A8CFF)
            val a = (0.10f + beat.level * 0.12f + beat.bass * 0.06f)
            var x = 0f
            while (x <= w) {
                val y = base + sin(x / w * 5f + t * (0.35f + k * 0.2f) + k) * amp + sin(x / w * 11f - t * 0.5f) * amp * 0.3f
                val len = h * (0.07f + 0.04f * (0.5f + 0.5f * sin(x / w * 17f + t * 0.8f + k)))
                val fade = (0.35f + 0.65f * sin(PI.toFloat() * x / w)).coerceAtLeast(0f)
                drawLine(Brush.verticalGradient(0f to Color.Transparent, 0.25f to c.copy(alpha = a * fade), 1f to Color.Transparent, startY = y - len * 0.25f, endY = y + len),
                    Offset(x, y - len * 0.25f), Offset(x, y + len), step * 1.1f, blendMode = BlendMode.Plus)
                x += step
            }
        }
        drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.18f), Color.Transparent), Offset(w / 2, h * 0.3f), w * 0.8f), w * 0.8f, Offset(w / 2, h * 0.3f))

        // el pueblito con la aurora (se funde con el cielo); las ventanas brillan más con la música
        val tw = w * 2.2f; val th = tw * town.height / town.width; val ty = h * 0.40f
        // el pueblito se desvanece hacia arriba con una máscara (sin cuadro de otro color)
        drawContext.canvas.saveLayer(androidx.compose.ui.geometry.Rect(0f, ty, w, ty + th), androidx.compose.ui.graphics.Paint())
        img(town, (w - tw) / 2f, ty, tw, th, 0.95f)
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black), ty, ty + th * 0.5f), Offset(0f, ty), Size(w, th), blendMode = BlendMode.DstIn)
        drawContext.canvas.restore()
        drawRect(Xmas.Warm.copy(alpha = 0.05f + beat.level * 0.10f), Offset(0f, ty + th * 0.5f), Size(w, th * 0.5f), blendMode = BlendMode.Plus)
        // piso nevado y lomas abajo
        val ground = ty + th * 0.92f
        drawRect(Brush.verticalGradient(0f to Color(0xFF6E93C9), 0.12f to Color(0xFF274A86), 0.5f to Color(0xFF13285A), 1f to Xmas.Night, startY = ground, endY = h), Offset(0f, ground), Size(w, h - ground))
        for (k in 0 until 3) {
            val cx = w * (0.15f + k * 0.38f); val cy = h * (1.02f - (k % 2) * 0.02f); val r = w * (0.45f + k * 0.05f)
            withTransform({ scale(1f, 0.22f, Offset(cx, cy)) }) {
                drawCircle(Brush.radialGradient(listOf(Xmas.Snow.copy(alpha = 0.75f), Color(0xFF8FB4E6).copy(alpha = 0.5f), Color.Transparent), Offset(cx, cy - r * 0.2f), r), r, Offset(cx, cy))
            }
        }
        // árbol, regalos y farol a la izquierda; el farol titila con la música
        val trw = w * 0.52f; val trh = trw * tree.height / tree.width
        val trx = -trw * 0.22f; val try_ = ground - trh * 0.80f
        img(tree, trx, try_, trw, trh)
        val lx = trx + trw * 0.775f; val ly = try_ + trh * 0.48f
        val flick = 0.55f + 0.25f * sin(t * 13f) * sin(t * 7.3f) + beat.level * 0.5f
        drawCircle(Brush.radialGradient(listOf(Xmas.Warm.copy(alpha = 0.45f * flick), Color.Transparent), Offset(lx, ly), trw * 0.35f), trw * 0.35f, Offset(lx, ly), blendMode = BlendMode.Plus)
        // árbol chico a la derecha (al revés)
        val tr2 = w * 0.34f; val tr2h = tr2 * tree.height / tree.width
        withTransform({ scale(-1f, 1f, Offset(w, 0f)) }) {
            img(tree, w - tr2 * 0.4f, ground - tr2h * 0.85f, tr2, tr2h, 0.85f)
        }

        // serie de foquitos arriba a la izquierda que cambia con cada golpe
        val p0 = Offset(-w * 0.02f, h * 0.035f); val p1 = Offset(w * 0.42f, h * 0.005f); val ctrl = Offset(w * 0.18f, h * 0.11f)
        val wire = Path().apply { moveTo(p0.x, p0.y); quadraticBezierTo(ctrl.x, ctrl.y, p1.x, p1.y) }
        drawPath(wire, Color(0xFF1E2C22), style = Stroke(2.dp.toPx()))
        val nb = 11
        for (i in 0 until nb) {
            val u = (i + 0.5f) / nb
            val x = (1 - u) * (1 - u) * p0.x + 2 * (1 - u) * u * ctrl.x + u * u * p1.x
            val y = (1 - u) * (1 - u) * p0.y + 2 * (1 - u) * u * ctrl.y + u * u * p1.y + 5.dp.toPx()
            val c = bulbColors[(i + fx.beats) % bulbColors.size]
            val on = ((i + fx.beats) % 3 != 0)
            val glow = if (on) 0.8f + beat.bass * 0.4f else 0.25f
            drawCircle(Brush.radialGradient(listOf(c.copy(alpha = 0.6f * glow), Color.Transparent), Offset(x, y), 13.dp.toPx()), 13.dp.toPx(), Offset(x, y), blendMode = BlendMode.Plus)
            drawCircle(lerp(c, Color.White, if (on) 0.4f else 0f).copy(alpha = if (on) 1f else 0.5f), 3.5.dp.toPx(), Offset(x, y))
        }
        val hw = w * 0.16f; val hh = hw * holly.height / holly.width
        img(holly, p1.x - hw * 0.5f, p1.y - hh * 0.2f, hw, hh)
        // esfera de cristal colgando que se mece con los bajos
        val ang = sin(t * 1.4f) * 8f + beat.bass * 6f
        val anchor = Offset(w * 0.27f, h * 0.045f)
        rotate(ang, anchor) {
            val bc = Offset(anchor.x, anchor.y + h * 0.075f); val br = w * 0.06f
            drawLine(Color.White.copy(alpha = 0.5f), anchor, Offset(bc.x, bc.y - br), 1.dp.toPx())
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.25f), Xmas.Blue.copy(alpha = 0.25f), Color(0xFF0E2A5C).copy(alpha = 0.6f)), bc, br), br, bc)
            drawCircle(Xmas.Ice.copy(alpha = 0.7f), br, bc, style = Stroke(1.2.dp.toPx()))
            val fs = br * 1.2f
            img(flake, bc.x - fs / 2, bc.y - fs / 2, fs, fs, 0.9f)
            drawCircle(Color.White.copy(alpha = 0.5f), br * 0.18f, Offset(bc.x - br * 0.4f, bc.y - br * 0.45f))
        }
    }
}

/** Atrás de la portada: luz que late, puntitos girando y barras azules a los lados. */
fun DrawScope.drawXmasStageBack(beat: BocinaBeat, accent: Color, alpha: Float, cx: Float, cy: Float, ringR: Float) {
    if (alpha <= 0f) return
    val t = beat.time
    val haloR = ringR * (1.5f + beat.bass * 0.2f)
    drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = (0.30f + beat.bass * 0.3f) * alpha), Color.Transparent), Offset(cx, cy), haloR), haloR, Offset(cx, cy))
    // barras a los lados
    val n = 7; val gap = 6.dp.toPx(); val bw = 3.dp.toPx()
    for (side in listOf(-1f, 1f)) for (i in 0 until n) {
        val x = cx + side * (ringR * 1.10f + i * gap)
        val band = beat.bandAt(i / (n - 1f) * 0.85f)
        val hh = ringR * (0.05f + band * 0.42f) * (1f - i * 0.07f)
        drawLine(Xmas.Blue.copy(alpha = 0.25f * alpha), Offset(x, cy - hh), Offset(x, cy + hh), bw * 2.4f, StrokeCap.Round)
        drawLine(lerp(Xmas.Ice, accent, 0.3f).copy(alpha = 0.95f * alpha), Offset(x, cy - hh), Offset(x, cy + hh), bw, StrokeCap.Round)
    }
    // puntitos de luz girando alrededor del aro
    val dots = 48; val dr = ringR * 1.06f
    for (i in 0 until dots) {
        val a = (i.toFloat() / dots) * 2f * PI.toFloat() + t * 0.15f
        val tw = 0.3f + 0.7f * ((sin(i * 0.9f + t * 3f) + 1f) / 2f) * (0.5f + beat.level)
        drawCircle(Xmas.Ice.copy(alpha = (tw * 0.8f).coerceAtMost(1f) * alpha), 1.4.dp.toPx(), Offset(cx + kotlin.math.cos(a) * dr, cy + sin(a) * dr))
    }
}

@Composable
fun XmasStageBack(beat: BocinaBeat, accent: Color, alpha: Float, cx: Dp, cy: Dp, ringR: Dp) {
    Canvas(Modifier.fillMaxSize()) {
        beat.frame
        drawXmasStageBack(beat, accent, alpha, cx.toPx(), cy.toPx(), ringR.toPx())
    }
}

/** El aro de cristal con el copo, encima de la portada redonda (brilla con los bajos). */
@Composable
fun XmasRingFront(beat: BocinaBeat, alpha: Float, cx: Dp, cy: Dp, ringR: Dp) {
    val ring = ImageBitmap.imageResource(R.drawable.nv_aro)
    Canvas(Modifier.fillMaxSize()) {
        beat.frame
        if (alpha <= 0f) return@Canvas
        val r = ringR.toPx() * (1f + beat.bass * 0.015f)
        val c = Offset(cx.toPx(), cy.toPx())
        img(ring, c.x - r, c.y - r, r * 2, r * 2, alpha)
        drawCircle(Brush.radialGradient(listOf(Color.Transparent, Color.Transparent, Xmas.Ice.copy(alpha = (0.05f + beat.bass * 0.35f) * alpha)), c, r * 1.02f),
            r * 1.02f, c, blendMode = BlendMode.Plus)
    }
}

/** Nieve que cae por encima de todo, y los destellos de cuando tocas. No bloquea los toques. */
@Composable
fun XmasSnowOverlay(fx: XmasFx, beat: BocinaBeat, alpha: Float) {
    val flakeImg = ImageBitmap.imageResource(R.drawable.nv_copo_chico)
    val starBlue = ImageBitmap.imageResource(R.drawable.nv_estrella_azul)
    val starGold = ImageBitmap.imageResource(R.drawable.nv_estrella_oro)
    Canvas(Modifier.fillMaxSize()) {
        beat.frame
        if (alpha <= 0.01f) return@Canvas
        val w = size.width; val h = size.height
        fx.flakes.forEach { f ->
            val x = f.x * w; val y = f.y * h
            if (f.depth > 0.88f) {
                val s = (8f + f.size * 3f).dp.toPx()
                rotate(f.spin, Offset(x, y)) { img(flakeImg, x - s / 2, y - s / 2, s, s, 0.85f * alpha) }
            } else {
                drawCircle(Xmas.Snow.copy(alpha = (0.35f + f.depth * 0.55f) * alpha), f.size.dp.toPx(), Offset(x, y))
            }
        }
        fx.sparkles.forEach { s ->
            val life = s.life
            val px = (s.x + s.vx * (1f - life)) * w; val py = (s.y + s.vy * (1f - life)) * h
            val sz = (10f + 10f * life).dp.toPx()
            img(if (s.gold) starGold else starBlue, px - sz / 2, py - sz / 2, sz, sz, life * alpha)
        }
    }
}

// ───────── controles ─────────

@Composable
fun XmasSquareButton(icon: ImageVector, description: String, tint: Color = Color.White, size: Dp = 52.dp, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(size * 0.32f)
    Box(
        Modifier.size(size)
            .drawBehind { drawRoundRect(Xmas.Blue.copy(alpha = 0.25f), Offset(-3.dp.toPx(), -3.dp.toPx()), Size(this.size.width + 6.dp.toPx(), this.size.height + 6.dp.toPx()),
                androidx.compose.ui.geometry.CornerRadius(size.toPx() * 0.38f)) }
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF1F4E92), Color(0xFF0D2552))))
            .border(1.dp, Brush.verticalGradient(listOf(Xmas.Ice.copy(alpha = 0.55f), Xmas.Blue.copy(alpha = 0.25f))), shape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, description, tint = if (enabled) tint else tint.copy(alpha = 0.35f), modifier = Modifier.size(size * 0.48f)) }
}

@Composable
fun XmasPlayButton(icon: ImageVector, beat: BocinaBeat?, size: Dp = 82.dp, onClick: () -> Unit) {
    val flake = ImageBitmap.imageResource(R.drawable.nv_copo)
    Box(
        Modifier.size(size)
            .drawBehind {
                beat?.frame
                val b = beat?.bass ?: 0f
                val r = this.size.minDimension / 2f
                val gr = r * (1.45f + b * 0.2f)
                drawCircle(Brush.radialGradient(0.55f to Xmas.Blue.copy(alpha = 0.45f + b * 0.3f), 1f to Color.Transparent, center = center, radius = gr), gr)
            }
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(Color(0xFF2A67C0), Color(0xFF0D2552))))
            .drawWithContent {
                val s = this.size.minDimension * 0.9f
                drawImage(flake, dstOffset = IntOffset(((this.size.width - s) / 2).toInt(), ((this.size.height - s) / 2).toInt()),
                    dstSize = IntSize(s.toInt(), s.toInt()), alpha = 0.18f)
                drawContent()
                val r = this.size.minDimension / 2f
                drawCircle(Brush.sweepGradient(listOf(Xmas.Ice, Xmas.Blue, Color.White, Xmas.Blue, Xmas.Ice)), r - 2.dp.toPx(), style = Stroke(3.dp.toPx()))
                drawCircle(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.Transparent), 0f, this.size.height * 0.5f), r)
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(size * 0.46f)) }
}

@Composable
fun XmasTopBar(beat: BocinaBeat?, onClose: () -> Unit, onMenu: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(R.drawable.nv_btn_bajar), "Cerrar", modifier = Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).clickable(onClick = onClose))
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Image(painterResource(R.drawable.nv_logo), "Nebula", contentScale = ContentScale.Fit,
                modifier = Modifier.height(48.dp).graphicsLayer {
                    val b = beat?.kick ?: 0f
                    scaleX = 1f + b * 0.04f; scaleY = 1f + b * 0.04f
                })
        }
        Image(painterResource(R.drawable.nv_btn_mas), "Más", modifier = Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).clickable(onClick = onMenu))
    }
}

/** La barra de abajo con nieve y moño (dibujo de Tony); cada cuarto es un botón. */
@Composable
fun XmasBottomNav(onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val bar = ImageBitmap.imageResource(R.drawable.nv_barra)
    Box(modifier.fillMaxWidth().aspectRatio(bar.width.toFloat() / bar.height)) {
        Image(painterResource(R.drawable.nv_barra), null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
        Row(Modifier.fillMaxSize().padding(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 22.dp)) {
            repeat(4) { i ->
                Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(16.dp))
                    .clickable { onSelect(i) })
            }
        }
    }
}
