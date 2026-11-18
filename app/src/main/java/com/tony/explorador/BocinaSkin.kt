package com.tony.explorador

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
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
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/*
 * Tema «Bocina»: una bocina de verdad detrás de la portada que late con los bajos, un aro de luz,
 * la onda de barras que cruza por en medio y ondas de luz en el fondo. Todo toma el color de cada carátula.
 */

object Bocina {
    val Top = Color(0xFF0D1E3A)
    val Mid = Color(0xFF11264A)
    val Deep = Color(0xFF060D1B)
    val Blue = Color(0xFF3FA0FF)
    val Ice = Color(0xFFBFE2FF)
    val Glass = Color(0xFF26354F)
    val GlassLine = Color.White.copy(alpha = 0.16f)
}

val isBocinaSkin get() = LibraryStore.musicSkin == MusicSkin.BOCINA

/** Mezcla el azul del tema con el color de la carátula (y lo aclara para que brille). */
fun bocinaAccent(cover: Color): Color {
    val mixed = lerp(Bocina.Blue, cover, 0.55f)
    return lerp(mixed, Color.White, 0.12f)
}

/** Lo que mide la música cuadro por cuadro. Solo [frame] es estado: así solo se redibuja, no se recompone. */
class BocinaBeat {
    var frame by mutableLongStateOf(0L)
    var bass = 0f
    var level = 0f
    /** Posición de la canción (ms) en este cuadro y su mapa de ritmo, si ya está listo. */
    var posMs = 0L
    var path: String? = null
    var map: BeatMap.Analysis? = null
    var kick = 0f
    var time = 0f
    val bands = FloatArray(SpectrumAnalyzer.BANDS)
    private var avgBass = 0f

    fun update(data: FloatArray?, playing: Boolean, dt: Float) {
        if (data == null || !playing) {
            bass *= 0.92f; level *= 0.94f; kick *= 0.85f
            for (i in bands.indices) bands[i] *= 0.9f
        } else {
            val b = (data[0] + data[1] + data[2] + data[3]) / 4f
            val l = data.average().toFloat()
            bass = if (b > bass) bass + (b - bass) * 0.65f else bass * 0.88f + b * 0.12f
            level = level * 0.8f + l * 0.2f
            val k = (b - avgBass - 0.06f).coerceAtLeast(0f)
            kick = maxOf(kick * 0.86f, k * 2.2f).coerceAtMost(1f)
            avgBass = avgBass * 0.93f + b * 0.07f
            for (i in bands.indices) {
                val t = data.getOrElse(i) { 0f }
                bands[i] = if (t > bands[i]) bands[i] + (t - bands[i]) * 0.7f else (bands[i] - 0.03f).coerceAtLeast(t)
            }
        }
        time += dt * (0.6f + level * 1.6f)
    }

    /** Banda suavizada en una posición 0..1 (0 = graves). */
    fun bandAt(f: Float): Float {
        val x = f.coerceIn(0f, 1f) * (bands.size - 1)
        val i = x.toInt().coerceAtMost(bands.size - 2)
        val t = x - i
        return bands[i] * (1 - t) + bands[i + 1] * t
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun rememberBocinaBeat(playing: Boolean, player: androidx.media3.common.Player? = null): BocinaBeat {
    val beat = remember { BocinaBeat() }
    val isPlaying by rememberUpdatedState(playing)
    val pl by rememberUpdatedState(player)
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) withFrameNanos { now ->
            val dt = if (last == 0L) 0.016f else ((now - last) / 1e9f).coerceIn(0f, 0.05f)
            last = now
            // con el mapa de ritmo se lee lo que suena en la posición exacta de la canción (no con reloj aparte)
            val p = pl
            val path = p?.currentMediaItem?.mediaId
            val map = BeatMap.get(path)
            if (p != null) beat.posMs = p.currentPosition
            beat.path = path; beat.map = map
            if (map != null && p != null && p.hasNextMediaItem()) BeatMap.get(p.getMediaItemAt(p.nextMediaItemIndex).mediaId)
            val data = map?.frameAt(beat.posMs) ?: SpectrumAnalyzer.peek()
            beat.update(data, isPlaying, dt)
            beat.frame = now
        }
    }
    return beat
}

private val dust = List(46) { val r = Random(it * 4099 + 7); floatArrayOf(r.nextFloat(), r.nextFloat(), r.nextFloat(), r.nextFloat()) }

/** Fondo: azul noche, una estela de luz arriba y ondas de luz que corren abajo al ritmo de la música. */
@Composable
fun BocinaBackground(accent: Color, beat: BocinaBeat, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        beat.frame // se redibuja cada cuadro
        val w = size.width; val h = size.height; val t = beat.time
        drawRect(Brush.verticalGradient(listOf(Bocina.Top, Bocina.Mid, Bocina.Deep)))
        fun blob(cx: Float, cy: Float, r: Float, c: Color) =
            drawCircle(Brush.radialGradient(listOf(c, Color.Transparent), Offset(cx, cy), r), r, Offset(cx, cy))
        blob(w * 0.95f, h * 0.06f, w * 0.75f, accent.copy(alpha = 0.22f))
        blob(w * 0.0f, h * 0.55f, w * 0.7f, accent.copy(alpha = 0.10f + beat.level * 0.10f))
        blob(w * 0.85f, h * 0.92f, w * 0.7f, Bocina.Blue.copy(alpha = 0.14f))

        // estela de luz que baja por la esquina de arriba
        val streak = Path().apply {
            moveTo(w * 0.38f, -h * 0.01f)
            quadraticBezierTo(w * 0.62f, h * 0.10f, w * 1.02f, h * 0.135f)
        }
        val glowC = lerp(Bocina.Ice, accent, 0.4f)
        listOf(26f to 0.05f, 12f to 0.10f, 5f to 0.25f, 1.6f to 0.9f).forEach { (sw, a) ->
            drawPath(streak, glowC.copy(alpha = a), style = Stroke(sw.dp.toPx(), cap = StrokeCap.Round))
        }

        // una sola onda de luz arriba, como un listón suave que se mece con la música
        drawBocinaRibbon(accent, beat, h * 0.115f)

        // polvito de luz que sube despacio
        dust.forEach { (x, y, s, sp) ->
            val yy = ((y - t * 0.015f * (0.5f + sp)) % 1f + 1f) % 1f
            val tw = 0.4f + 0.6f * ((sin(t * 2f * (1 + sp) + x * 20f) + 1f) / 2f)
            drawCircle(lerp(Color.White, accent, 0.3f).copy(alpha = 0.35f * tw * s), (0.6f + s * 1.3f).dp.toPx(), Offset(x * w, yy * h))
        }
    }
}

/** Listón de luz: dos ondas que se cruzan con el espacio entre ellas relleno y un hilo brillante al centro. */
fun DrawScope.drawBocinaRibbon(accent: Color, beat: BocinaBeat, baseY: Float) {
    val w = size.width; val t = beat.time
    val amp = 14.dp.toPx() * (1f + beat.level * 1.6f + beat.bass * 0.8f)
    val freq = 1.15f * 2f * PI.toFloat() / w
    val steps = 64
    val upper = Path(); val lower = Path(); val core = Path(); val band = Path()
    val ys1 = FloatArray(steps + 1); val ys2 = FloatArray(steps + 1)
    for (s in 0..steps) {
        val x = w * s / steps
        val fade = sin(PI.toFloat() * s / steps).coerceAtLeast(0f)   // más delgada en las orillas
        ys1[s] = baseY + (sin(x * freq + t * 1.3f) * amp + sin(x * freq * 2.1f - t * 0.8f) * amp * 0.25f) * (0.35f + 0.65f * fade)
        ys2[s] = baseY + (sin(x * freq + t * 1.3f + 2.4f) * amp * 0.8f) * (0.35f + 0.65f * fade)
        if (s == 0) { upper.moveTo(x, ys1[s]); lower.moveTo(x, ys2[s]); core.moveTo(x, (ys1[s] + ys2[s]) / 2) }
        else { upper.lineTo(x, ys1[s]); lower.lineTo(x, ys2[s]); core.lineTo(x, (ys1[s] + ys2[s]) / 2) }
    }
    band.moveTo(0f, ys1[0]); for (s in 1..steps) band.lineTo(w * s / steps, ys1[s])
    for (s in steps downTo 0) band.lineTo(w * s / steps, ys2[s]); band.close()
    val c1 = lerp(Bocina.Blue, accent, 0.5f); val c2 = lerp(Bocina.Ice, accent, 0.3f)
    val edge = Brush.horizontalGradient(listOf(Color.Transparent, c1, c2, c1, Color.Transparent))
    drawPath(band, Brush.horizontalGradient(listOf(Color.Transparent, c1.copy(alpha = 0.22f), c2.copy(alpha = 0.30f), c1.copy(alpha = 0.22f), Color.Transparent)))
    drawPath(upper, edge, alpha = 0.18f, style = Stroke(10.dp.toPx()))
    drawPath(lower, edge, alpha = 0.18f, style = Stroke(10.dp.toPx()))
    drawPath(upper, edge, alpha = 0.75f, style = Stroke(1.4.dp.toPx()))
    drawPath(lower, edge, alpha = 0.55f, style = Stroke(1.1.dp.toPx()))
    drawPath(core, Brush.horizontalGradient(listOf(Color.Transparent, Color.White, Color.Transparent)), alpha = 0.35f + beat.bass * 0.4f, style = Stroke(1.dp.toPx()))
}

/**
 * Escenario detrás de la portada: onda de barras que cruza, la bocina (el cono se empuja con cada golpe),
 * el aro de luz y un destello abajo. [cy] es el centro de la portada y [radius] el radio de la bocina.
 */
fun DrawScope.drawBocinaStage(
    beat: BocinaBeat, accent: Color, alpha: Float, cx: Float, cy: Float, radius: Float,
    marco: ImageBitmap, cono: ImageBitmap,
) {
    if (alpha <= 0f) return
    val w = size.width; val t = beat.time
    val bass = beat.bass; val kick = beat.kick
    val light = lerp(Bocina.Ice, accent, 0.5f)

    // halo detrás de todo
    val haloR = radius * (1.55f + bass * 0.25f)
    drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = (0.30f + bass * 0.35f) * alpha), Color.Transparent), Offset(cx, cy), haloR), haloR, Offset(cx, cy))

    // onda de barras que sale de la bocina hacia las orillas (graves junto a la bocina, agudos afuera)
    val start = radius * 0.80f
    val span = (w / 2f - start).coerceAtLeast(radius * 0.3f)
    val n = 16
    val gap = span / n
    val maxH = radius * 0.62f
    for (side in listOf(-1f, 1f)) for (i in 0 until n) {
        val d = i / (n - 1f)                         // 0 = junto a la bocina, 1 = orilla
        val x = cx + side * (start + (i + 0.5f) * gap)
        val band = beat.bandAt(d * 0.85f + 0.02f)
        val wig = 0.6f + 0.4f * sin(t * 7f + i * 0.9f + side)
        val env = (1f - d * 0.65f)
        val hh = (maxH * (0.05f + band * 1.6f * wig + beat.bass * 0.25f * (1f - d)) * env).coerceIn(2.dp.toPx(), maxH)
        val a = (0.35f + 0.65f * (1f - d)) * alpha
        drawLine(accent.copy(alpha = 0.18f * a), Offset(x, cy - hh), Offset(x, cy + hh), gap * 0.75f, StrokeCap.Round)
        drawLine(light.copy(alpha = 0.95f * a), Offset(x, cy - hh), Offset(x, cy + hh), gap * 0.28f, StrokeCap.Round)
    }
    drawLine(Brush.horizontalGradient(listOf(Color.Transparent, light.copy(alpha = 0.6f * alpha), Color.Transparent)),
        Offset(0f, cy), Offset(w, cy), 1.dp.toPx())

    // la bocina: el marco late poquito y el cono se empuja con los golpes
    val frameS = 1f + bass * 0.025f + kick * 0.015f
    val r = radius * frameS
    drawImage(marco, dstOffset = IntOffset((cx - r).roundToInt(), (cy - r).roundToInt()),
        dstSize = IntSize((r * 2).roundToInt(), (r * 2).roundToInt()), alpha = alpha)
    val coneS = 1f + bass * 0.07f + kick * 0.05f
    val rc = radius * coneS
    drawImage(cono, dstOffset = IntOffset((cx - rc).roundToInt(), (cy - rc).roundToInt()),
        dstSize = IntSize((rc * 2).roundToInt(), (rc * 2).roundToInt()), alpha = alpha)
    // un poco del color de la canción encima del metal
    drawCircle(accent.copy(alpha = 0.40f * alpha), r * 0.98f, Offset(cx, cy), blendMode = BlendMode.Color)
    // brillo del borde que sube con los bajos
    drawCircle(Brush.radialGradient(listOf(Color.Transparent, Color.Transparent, light.copy(alpha = (0.10f + bass * 0.4f) * alpha)),
        Offset(cx, cy), r), r, Offset(cx, cy))

    // aro de luz alrededor (se abre con cada golpe)
    val ringR = radius * (1.10f + bass * 0.07f + kick * 0.05f)
    listOf(18f to 0.06f, 9f to 0.14f, 4f to 0.35f, 1.8f to 1f).forEach { (sw, a) ->
        drawCircle((if (sw < 2f) lerp(Color.White, light, 0.5f) else accent).copy(alpha = a * alpha * (0.6f + bass * 0.6f).coerceAtMost(1f)),
            ringR, Offset(cx, cy), style = Stroke(sw.dp.toPx()))
    }
    // dos arcos de luz que giran sobre el aro
    rotate(t * 40f, Offset(cx, cy)) {
        val arcR = ringR + 6.dp.toPx()
        val tl = Offset(cx - arcR, cy - arcR); val sz = Size(arcR * 2, arcR * 2)
        drawArc(Brush.sweepGradient(listOf(Color.Transparent, light.copy(alpha = 0.9f * alpha), Color.Transparent), Offset(cx, cy)),
            0f, 360f, false, tl, sz, style = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round))
    }
    // ondas que salen de la bocina cuando pega fuerte
    if (kick > 0.05f) {
        val pr = ringR * (1f + (1f - kick) * 0.35f)
        drawCircle(light.copy(alpha = kick * 0.5f * alpha), pr, Offset(cx, cy), style = Stroke((1f + kick * 3f).dp.toPx()))
    }

    // destello abajo, como un reflejo en el piso
    val fy = cy + ringR + 10.dp.toPx()
    val fw = radius * (1.3f + bass * 0.3f); val fh = 12.dp.toPx()
    withTransform({ scale(1f, fh / fw, Offset(cx, fy)) }) {
        drawCircle(Brush.radialGradient(listOf(light.copy(alpha = 0.55f * alpha), accent.copy(alpha = 0.2f * alpha), Color.Transparent), Offset(cx, fy), fw), fw, Offset(cx, fy))
    }
}

@Composable
fun BocinaStage(beat: BocinaBeat, accent: Color, alpha: Float, cx: Dp, cy: Dp, radius: Dp, modifier: Modifier = Modifier) {
    val marco = ImageBitmap.imageResource(R.drawable.bocina_marco)
    val cono = ImageBitmap.imageResource(R.drawable.bocina_cono)
    Canvas(modifier.fillMaxSize()) {
        beat.frame
        drawBocinaStage(beat, accent, alpha, cx.toPx(), cy.toPx(), radius.toPx(), marco, cono)
    }
}

// ───────── botones de cristal ─────────

@Composable
fun BocinaRoundButton(icon: ImageVector, description: String, tint: Color = Color.White, size: Dp = 52.dp, enabled: Boolean = true,
                      glow: Color? = null, onClick: () -> Unit) {
    Box(
        Modifier.size(size)
            .then(if (glow != null) Modifier.shadow(14.dp, CircleShape, ambientColor = glow, spotColor = glow) else Modifier)
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(Color(0xFF3A4B69), Color(0xFF1D2A41))))
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.30f), Color.White.copy(alpha = 0.06f))), CircleShape)
            .drawWithContent {
                drawContent()
                drawCircle(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.12f), Color.Transparent), 0f, this.size.height * 0.5f),
                    this.size.minDimension / 2f)
            }
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, description, tint = if (enabled) tint else tint.copy(alpha = 0.35f), modifier = Modifier.size(size * 0.46f))
    }
}

@Composable
fun BocinaPlayButton(icon: ImageVector, accent: Color, beat: BocinaBeat?, size: Dp = 84.dp, onClick: () -> Unit) {
    Box(
        Modifier.size(size)
            // brillo que late alrededor: se dibuja por fuera del botón, sin cortarse
            .drawBehind {
                beat?.frame
                val b = beat?.bass ?: 0f
                val r = this.size.minDimension / 2f
                val gr = r * (1.45f + b * 0.2f)
                drawCircle(Brush.radialGradient(0.55f to accent.copy(alpha = 0.40f + b * 0.3f), 1f to Color.Transparent, center = center, radius = gr), gr)
            }
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(lerp(accent, Color.White, 0.30f), lerp(accent, Color(0xFF0B1B3A), 0.35f), Color(0xFF0E1D38)),
                start = Offset.Zero, end = Offset.Infinite))
            .drawWithContent {
                drawContent()
                // reflejo de cristal en la mitad de arriba (dentro del círculo)
                drawCircle(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.30f), Color.Transparent), 0f, this.size.height * 0.55f),
                    this.size.minDimension / 2f)
            }
            .border(1.5.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.75f), accent.copy(alpha = 0.6f))), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(size * 0.46f)) }
}

@Composable
fun BocinaTopBar(album: String?, onClose: () -> Unit, onMenu: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        BocinaRoundButton(Icons.Filled.KeyboardArrowDown, "Cerrar", size = 42.dp, onClick = onClose)
        Column(Modifier.weight(1f).padding(horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("REPRODUCIENDO", fontSize = 11.sp, letterSpacing = 4.sp, color = Color.White.copy(alpha = 0.65f), fontWeight = FontWeight.Medium)
            if (album != null) Text(album, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White,
                maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        }
        BocinaRoundButton(Icons.Filled.MoreVert, "Más", size = 42.dp, onClick = onMenu)
    }
}

/** Barra de abajo de cristal: ajustes, reproductor, lista y ecualizador. */
@Composable
fun BocinaBottomNav(selected: Int, accent: Color, icons: List<Pair<ImageVector, String>>, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().height(60.dp).clip(RoundedCornerShape(30.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF34445F).copy(alpha = 0.85f), Color(0xFF1A2639).copy(alpha = 0.85f))))
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.26f), Color.White.copy(alpha = 0.05f))), RoundedCornerShape(30.dp))
            .padding(6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically
    ) {
        icons.forEachIndexed { i, (ic, label) ->
            val on = i == selected
            Box(
                Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(24.dp))
                    .background(if (on) Brush.verticalGradient(listOf(accent.copy(alpha = 0.40f), Color.White.copy(alpha = 0.06f)))
                        else Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent)))
                    .then(if (on) Modifier.border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(24.dp)) else Modifier)
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center
            ) { Icon(ic, label, tint = if (on) lerp(Color.White, accent, 0.25f) else Color.White.copy(alpha = 0.85f), modifier = Modifier.size(24.dp)) }
        }
    }
}

@Suppress("unused") private fun keep(x: Float) = abs(x)
