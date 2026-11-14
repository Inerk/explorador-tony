package com.tony.explorador

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/** Los estilos de barra de avance que se pueden elegir en Ajustes de música. */
val SeekStyles = listOf(
    "galaxy" to "Galaxia (ondas con burbujas)",
    "wave" to "Onda clásica",
    "eq" to "Ecualizador",
    "neon" to "Neón que late",
    "line" to "Línea sencilla",
) + NeonSeekStyles.map { it.first to it.second }

/** Lo que mide la música en cada cuadro: golpe de bajos, volumen general y las bandas. */
private class Beat {
    var bass = 0f       // 0..1, sube rápido con cada golpe y baja suave
    var level = 0f      // volumen general
    var kick = 0f       // pico de bajos "nuevo" (sirve para soltar burbujas)
    var bands = FloatArray(SpectrumAnalyzer.BANDS)
    private var avgBass = 0f

    fun update(data: FloatArray?, playing: Boolean) {
        if (data == null || !playing) {
            bass *= 0.92f; level *= 0.94f; kick = 0f
            for (i in bands.indices) bands[i] *= 0.9f
            return
        }
        val b = (data[0] + data[1] + data[2] + data[3]) / 4f
        val l = data.average().toFloat()
        bass = if (b > bass) bass + (b - bass) * 0.6f else bass * 0.9f + b * 0.1f
        level = level * 0.8f + l * 0.2f
        kick = (b - avgBass - 0.08f).coerceAtLeast(0f)
        avgBass = avgBass * 0.93f + b * 0.07f
        for (i in bands.indices) {
            val t = data.getOrElse(i) { 0f }
            bands[i] = if (t > bands[i]) bands[i] + (t - bands[i]) * 0.7f else (bands[i] - 0.03f).coerceAtLeast(t)
        }
    }
}

private class Bubble(var x: Float, var y: Float, val r: Float, var vx: Float, var vy: Float, var life: Float, val ring: Boolean, val wobble: Float)

/**
 * Barra de avance del reproductor. El estilo sale de Ajustes de música;
 * casi todos se mueven al ritmo de lo que suena (leen el audio directo del reproductor).
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun WaveSeekBar(
    progress: Float,
    playing: Boolean,
    accent: Color,
    onSeekChange: (Float) -> Unit,
    onSeekEnd: (Float) -> Unit,
    modifier: Modifier = Modifier,
    style: String = LibraryStore.seekStyle,
    interactive: Boolean = true,
) {
    var dragging by remember { mutableStateOf(false) }
    var dragFrac by remember { mutableFloatStateOf(0f) }
    val amplitude by animateFloatAsState(if (playing && !dragging) 1f else 0f, tween(600), label = "amp")
    val thumb by animateDpAsState(if (dragging) 12.dp else 7.dp, spring(dampingRatio = 0.5f), label = "thumb")
    val glow by animateFloatAsState(if (dragging) 1f else 0.45f, tween(250), label = "glow")
    val change by rememberUpdatedState(onSeekChange)
    val end by rememberUpdatedState(onSeekEnd)
    val isPlaying by rememberUpdatedState(playing)
    val shown = if (dragging) dragFrac else progress

    // reloj y música, cuadro por cuadro
    val beat = remember { Beat() }
    val bubbles = remember { ArrayList<Bubble>() }
    var frame by remember { mutableLongStateOf(0L) }
    var time by remember { mutableFloatStateOf(0f) }
    val animated = style != "line"
    LaunchedEffect(animated) {
        if (!animated) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0.016f else ((now - last) / 1e9f).coerceIn(0f, 0.05f)
                last = now
                beat.update(SpectrumAnalyzer.peek(), isPlaying)
                // la onda corre más rápido cuando la música pega fuerte
                time += dt * (1f + beat.level * 1.5f)
                frame = now
            }
        }
    }

    var canvasMod = modifier.fillMaxWidth().height(40.dp)
    if (interactive) canvasMod = canvasMod
        .pointerInput(Unit) { detectTapGestures { o -> end((o.x / size.width).coerceIn(0f, 1f)) } }
        .pointerInput(Unit) {
            detectHorizontalDragGestures(
                onDragStart = { o ->
                    dragging = true
                    dragFrac = (o.x / size.width).coerceIn(0f, 1f)
                    change(dragFrac)
                },
                onDragEnd = { dragging = false; end(dragFrac) },
                onDragCancel = { dragging = false }
            ) { c, d ->
                c.consume()
                dragFrac = (dragFrac + d / size.width).coerceIn(0f, 1f)
                change(dragFrac)
            }
        }

    Canvas(canvasMod) {
        @Suppress("UNUSED_EXPRESSION") frame // redibuja cada cuadro
        val midY = size.height / 2f
        val x = size.width * shown
        val amp = amplitude
        when (style) {
            "line" -> drawLineStyle(x, midY, accent)
            "eq" -> drawEqStyle(x, midY, accent, beat, amp, time)
            "neon" -> drawNeonStyle(x, midY, accent, beat, amp)
            "wave" -> drawClassicWave(x, midY, accent, amp, time)
            "galaxy" -> drawGalaxy(x, midY, accent, beat, amp, time, bubbles, isPlaying && !dragging)
            else -> if (NeonSeekStyles.any { it.first == style })
                drawNeonStyle(style, x, NeonCtx(size.width, size.height, midY, time, beat.bass, beat.level, amp, beat.bands))
            else drawGalaxy(x, midY, accent, beat, amp, time, bubbles, isPlaying && !dragging)
        }
        // círculo con brillo (más grande con los bajos)
        val pulse = if (style == "line") 1f else 1f + beat.bass * 0.35f * amp
        val tr = thumb.toPx() * pulse
        drawCircle(accent.copy(alpha = (0.22f * glow * 2f).coerceAtMost(1f)), radius = tr * 2.4f, center = Offset(x, midY))
        drawCircle(accent, radius = tr, center = Offset(x, midY))
        drawCircle(Color.White, radius = tr * 0.38f, center = Offset(x, midY))
    }
}

private fun DrawScope.drawRest(x: Float, midY: Float, width: Float = 4.dp.toPx()) {
    drawLine(Color.White.copy(alpha = 0.22f), Offset(x, midY), Offset(size.width, midY), strokeWidth = width, cap = StrokeCap.Round)
}

private fun DrawScope.drawLineStyle(x: Float, midY: Float, accent: Color) {
    drawRest(x, midY, 3.dp.toPx())
    if (x > 1f) drawLine(accent, Offset(0f, midY), Offset(x, midY), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
}

private fun DrawScope.drawClassicWave(x: Float, midY: Float, accent: Color, amp: Float, time: Float) {
    val stroke = 4.dp.toPx()
    drawRest(x, midY)
    if (x <= 1f) return
    val path = Path()
    val waveLen = 26.dp.toPx()
    val a = 3.5.dp.toPx() * amp
    val phase = time * (2 * PI / 1.4).toFloat()
    path.moveTo(0f, midY)
    var px = 0f
    while (px <= x) {
        val fade = ((x - px) / (waveLen * 0.6f)).coerceIn(0f, 1f)
        path.lineTo(px, midY + a * fade * sin(px / waveLen * 2f * PI.toFloat() - phase))
        px += 2f
    }
    path.lineTo(x, midY)
    drawPath(path, Brush.horizontalGradient(listOf(accent.copy(alpha = 0.55f), accent), 0f, x), style = Stroke(stroke, cap = StrokeCap.Round))
}

/** Estilo Galaxy: dos ondas que respiran con la música y burbujas sutiles que salen de la onda y flotan por la pantalla. */
private fun DrawScope.drawGalaxy(
    x: Float, midY: Float, accent: Color, beat: Beat, amp: Float, time: Float,
    bubbles: ArrayList<Bubble>, emit: Boolean,
) {
    val stroke = 4.dp.toPx()
    drawRest(x, midY)
    val waveLen = 34.dp.toPx()
    val baseA = (2.5.dp.toPx() + 7.dp.toPx() * beat.bass) * amp
    val phase = time * 4.2f
    fun yAt(px: Float, k: Float, a: Float, ph: Float): Float {
        val fade = ((x - px) / (waveLen * 0.7f)).coerceIn(0f, 1f) * (px / (waveLen * 0.5f)).coerceIn(0f, 1f)
        return midY + a * fade * (sin(px / (waveLen * k) * 2f * PI.toFloat() - ph) * 0.75f + sin(px / (waveLen * k * 0.47f) * 2f * PI.toFloat() + ph * 0.6f) * 0.25f)
    }
    if (x > 1f) {
        // onda de atrás, más suave y transparente
        val back = Path().apply {
            moveTo(0f, midY); var px = 0f
            while (px <= x) { lineTo(px, yAt(px, 1.35f, baseA * 0.8f, -phase * 0.8f + 1.3f)); px += 2f }
            lineTo(x, midY)
        }
        drawPath(back, accent.copy(alpha = 0.3f), style = Stroke(stroke * 0.7f, cap = StrokeCap.Round))
        // onda principal
        val main = Path().apply {
            moveTo(0f, midY); var px = 0f
            while (px <= x) { lineTo(px, yAt(px, 1f, baseA, phase)); px += 2f }
            lineTo(x, midY)
        }
        drawPath(main, Brush.horizontalGradient(listOf(accent.copy(alpha = 0.5f), accent, Color.White.copy(alpha = 0.9f)), 0f, x),
            style = Stroke(stroke, cap = StrokeCap.Round))
    }

    // burbujas: salen de las crestas, más con cada golpe de bajo
    if (emit && x > waveLen) {
        val chance = 0.025f + beat.kick * 2.2f
        var n = if (Random.nextFloat() < chance) 1 else 0
        if (beat.kick > 0.12f) n += 1 + (beat.kick * 6).toInt()
        repeat(n.coerceAtMost(4)) {
            if (bubbles.size < 40) {
                val bx = Random.nextFloat() * x
                bubbles += Bubble(
                    x = bx, y = yAt(bx, 1f, baseA, phase),
                    r = (1.2f + Random.nextFloat() * 3.3f).dp.toPx(),
                    vx = (-14f + Random.nextFloat() * 28f).dp.toPx(),
                    vy = -(18f + Random.nextFloat() * 40f + beat.bass * 50f).dp.toPx(),
                    life = 1f, ring = Random.nextFloat() < 0.55f, wobble = Random.nextFloat() * 6f,
                )
            }
        }
    }
    val dt = 1f / 60f
    val it = bubbles.iterator()
    while (it.hasNext()) {
        val b = it.next()
        b.life -= dt / (2.2f + b.r / 4.dp.toPx())
        b.x += (b.vx + sin(b.wobble + b.life * 9f) * 10.dp.toPx()) * dt
        b.y += b.vy * dt
        b.vy *= 0.995f
        if (b.life <= 0f) { it.remove(); continue }
        val alpha = (b.life * 1.4f).coerceAtMost(1f) * 0.55f
        val c = Offset(b.x, b.y)
        if (b.ring) {
            drawCircle(accent.copy(alpha = alpha), b.r, c, style = Stroke(1.dp.toPx()))
            drawCircle(Color.White.copy(alpha = alpha * 0.8f), b.r * 0.28f, Offset(b.x - b.r * 0.35f, b.y - b.r * 0.35f))
        } else {
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = alpha), accent.copy(alpha = alpha * 0.4f), Color.Transparent), c, b.r * 1.6f), b.r * 1.6f, c)
        }
    }
}

/** Barritas de ecualizador a lo largo de la barra; lo que ya sonó se pinta del color de la portada. */
private fun DrawScope.drawEqStyle(x: Float, midY: Float, accent: Color, beat: Beat, amp: Float, time: Float) {
    val count = 46
    val gap = 2.dp.toPx()
    val bw = (size.width - gap * (count - 1)) / count
    val maxH = size.height * 0.9f
    val minH = 3.dp.toPx()
    val nb = beat.bands.size
    for (i in 0 until count) {
        // reparte las bandas en espejo para que se vea parejo de lado a lado
        val f = i.toFloat() / (count - 1)
        val bandPos = (1f - kotlin.math.abs(f * 2f - 1f)) * (nb - 1)
        val lo = bandPos.toInt().coerceIn(0, nb - 1)
        val hi = (lo + 1).coerceAtMost(nb - 1)
        val v = beat.bands[lo] + (beat.bands[hi] - beat.bands[lo]) * (bandPos - lo)
        val idle = 0.15f + 0.08f * sin(time * 2f + i * 0.6f)
        val h = (minH + (maxH - minH) * (v * amp).coerceAtLeast(idle * (1f - amp) + 0.05f)).coerceAtMost(maxH)
        val left = i * (bw + gap)
        val played = left + bw / 2 <= x
        drawRoundRect(
            if (played) accent else Color.White.copy(alpha = 0.22f),
            topLeft = Offset(left, midY - h / 2), size = androidx.compose.ui.geometry.Size(bw, h),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(bw / 2, bw / 2)
        )
    }
}

/** Línea de neón que brilla más fuerte con cada golpe. */
private fun DrawScope.drawNeonStyle(x: Float, midY: Float, accent: Color, beat: Beat, amp: Float) {
    drawRest(x, midY, 3.dp.toPx())
    if (x <= 1f) return
    val p = beat.bass * amp
    for (k in 3 downTo 1) {
        drawLine(accent.copy(alpha = (0.08f + 0.12f * p) * (4 - k) / 3f), Offset(0f, midY), Offset(x, midY),
            strokeWidth = (3f + k * (4f + 8f * p)).dp.toPx(), cap = StrokeCap.Round)
    }
    drawLine(Brush.horizontalGradient(listOf(accent, Color.White), 0f, x), Offset(0f, midY), Offset(x, midY),
        strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
}
