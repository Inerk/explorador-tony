package com.tony.explorador

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

/** Los 20 estilos neón nuevos (id, nombre y sus colores). */
val NeonSeekStyles: List<Triple<String, String, List<Color>>> = listOf(
    Triple("mirror", "Espejo de luz", listOf(Color(0xFF7DF9FF), Color(0xFFB45CFF), Color(0xFFFF5FD2))),
    Triple("ledcols", "Columnas LED", listOf(Color(0xFF2BFF88), Color(0xFFB6FF3B), Color(0xFF2BFF88))),
    Triple("plasma", "Plasma azul", listOf(Color(0xFF2E5BFF), Color(0xFFBFD7FF), Color(0xFF2E5BFF))),
    Triple("rainbow", "Barras arcoíris", listOf(Color(0xFFFF3B6B), Color(0xFFFFC83D), Color(0xFF3BFFB0), Color(0xFF3B8BFF), Color(0xFFC03BFF))),
    Triple("neonflow", "Ondas neón", listOf(Color(0xFFFF2BD6), Color(0xFFB026FF), Color(0xFFFF2BD6))),
    Triple("spikes", "Púas de voz", listOf(Color(0xFFE15BFF), Color(0xFFFF7AC8), Color(0xFFE15BFF))),
    Triple("ribbon", "Listón de colores", listOf(Color(0xFFFF4F9A), Color(0xFFFFB13B), Color(0xFF4FC3FF), Color(0xFFB45CFF))),
    Triple("ledmatrix", "Matriz de puntos", listOf(Color(0xFFD8FF3B), Color(0xFF52FF6A), Color(0xFF00D4A0))),
    Triple("burst", "Ráfaga cian", listOf(Color(0xFF00E5FF), Color(0xFF2E7DFF), Color(0xFF00E5FF))),
    Triple("fire", "Fuego", listOf(Color(0xFFFF3D00), Color(0xFFFFB300), Color(0xFFFFE082), Color(0xFFFF6D00))),
    Triple("lens", "Gota de luz", listOf(Color(0xFF00C6FF), Color(0xFFB66BFF), Color(0xFFFF6FD8))),
    Triple("scribble", "Garabato", listOf(Color(0xFFFF4FA0), Color(0xFF8A7BFF), Color(0xFF3BE8FF))),
    Triple("silk", "Seda", listOf(Color(0xFFFFFFFF), Color(0xFF9AD8FF), Color(0xFF6E7BFF))),
    Triple("hills", "Colinas", listOf(Color(0xFFD23BFF), Color(0xFFFF6BD6), Color(0xFF8B3BFF))),
    Triple("dual", "Doble onda", listOf(Color(0xFFFF4FD8), Color(0xFF3BD8FF))),
    Triple("beam", "Rayo", listOf(Color(0xFF1E3BFF), Color(0xFF7FA8FF), Color(0xFF1E3BFF))),
    Triple("petals", "Pétalos", listOf(Color(0xFFFF5FD2), Color(0xFF9B6BFF), Color(0xFF5FD2FF), Color(0xFFFF9F5F))),
    Triple("mesh", "Malla 3D", listOf(Color(0xFF8A5BFF), Color(0xFF3B9BFF), Color(0xFFFF5FD2))),
    Triple("orbit", "Partículas", listOf(Color(0xFFFFFFFF), Color(0xFFB6A8FF), Color(0xFF6BD6FF))),
    Triple("aurora", "Aurora", listOf(Color(0xFF00E1FF), Color(0xFF7B5CFF), Color(0xFFFF5FC8))),
)

private fun rnd(i: Int): Float { val v = sin(i * 12.9898f + 78.233f) * 43758.547f; return v - kotlin.math.floor(v) }

/** Lo que dibuja cada estilo necesita: tamaño, música y tiempo. */
class NeonCtx(val w: Float, val h: Float, val mid: Float, val t: Float, val bass: Float, val level: Float, val amp: Float, val bands: FloatArray) {
    /** Energía de la música en esa parte de la barra (0..1), en espejo para que se vea pareja. */
    fun band(f: Float): Float {
        if (bands.isEmpty()) return 0f
        val pos = (1f - abs(f.coerceIn(0f, 1f) * 2f - 1f)) * (bands.size - 1)
        val lo = pos.toInt().coerceIn(0, bands.size - 1); val hi = (lo + 1).coerceAtMost(bands.size - 1)
        return (bands[lo] + (bands[hi] - bands[lo]) * (pos - lo)) * amp
    }
    /** Movimiento base aunque la música esté baja, para que nunca se vea muerta. */
    fun idle(f: Float, k: Float = 1f) = (0.18f + 0.12f * sin(t * 2.2f * k + f * 9f)) * (0.4f + 0.6f * amp)
    /** Envolvente con jorobas (como las ondas de la imagen). */
    fun hump(f: Float, n: Float = 2f) = (sin(f * PI.toFloat() * n).let { abs(it) } * 0.7f + 0.3f) * (sin(f * PI.toFloat()).coerceAtLeast(0f) * 0.6f + 0.4f)
}

/** Dibuja un estilo neón: lo que ya sonó con sus colores y lo que falta apagado. */
fun DrawScope.drawNeonStyle(id: String, x: Float, c: NeonCtx) {
    val colors = NeonSeekStyles.firstOrNull { it.first == id }?.third ?: return
    val vivid = Brush.horizontalGradient(colors, 0f, c.w)
    val dim = Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.17f), Color.White.copy(alpha = 0.17f)))
    clipRect(x, -c.h * 2, c.w, c.h * 3) { neonShape(id, dim, c, true) }
    clipRect(-10f, -c.h * 2, x, c.h * 3) { neonShape(id, vivid, c, false) }
}

private fun DrawScope.glowLine(path: Path, b: Brush, width: Float, dim: Boolean) {
    if (!dim) {
        drawPath(path, b, alpha = 0.18f, style = Stroke(width * 5f, cap = StrokeCap.Round))
        drawPath(path, b, alpha = 0.35f, style = Stroke(width * 2.4f, cap = StrokeCap.Round))
    }
    drawPath(path, b, style = Stroke(width, cap = StrokeCap.Round))
}

private fun wavePath(c: NeonCtx, step: Float = 3f, y: (Float) -> Float): Path = Path().apply {
    var px = 0f; moveTo(0f, y(0f))
    while (px <= c.w) { lineTo(px, y(px / c.w)); px += step }
}

private fun DrawScope.neonShape(id: String, b: Brush, c: NeonCtx, dim: Boolean) {
    val d1 = 1.dp.toPx()
    val maxA = c.h * 0.48f
    when (id) {
        // líneas verticales finas en espejo con jorobas que respiran
        "mirror" -> {
            val n = 70; val gap = c.w / n
            for (i in 0 until n) {
                val f = i / (n - 1f)
                val a = maxA * c.hump(f, 3f) * (0.35f + c.band(f) * 0.9f + c.idle(f) * 0.6f)
                val xx = i * gap + gap / 2
                drawLine(b, Offset(xx, c.mid - a), Offset(xx, c.mid + a), strokeWidth = gap * 0.42f, cap = StrokeCap.Round)
            }
            if (!dim) drawLine(b, Offset(0f, c.mid), Offset(c.w, c.mid), strokeWidth = d1, alpha = 0.7f)
        }
        // columnas hechas de cuadritos (LED) que suben con la música
        "ledcols" -> {
            val n = 32; val gap = c.w / n; val cell = gap * 0.7f; val rows = 6
            for (i in 0 until n) {
                val f = i / (n - 1f)
                val lvl = ((c.band(f) * 1.1f + c.idle(f, 1.3f) * 0.8f) * rows).toInt().coerceIn(1, rows)
                for (r in 0 until lvl) {
                    val top = c.mid + c.h * 0.42f - (r + 1) * (c.h * 0.84f / rows)
                    drawRect(b, Offset(i * gap + (gap - cell) / 2, top), Size(cell, c.h * 0.84f / rows - d1 * 1.5f), alpha = if (r == lvl - 1) 1f else 0.75f)
                }
            }
        }
        // listón de plasma con grosor que cambia, muy brillante en medio
        "plasma" -> {
            val p = wavePath(c) { f -> c.mid + sin(f * 14f - c.t * 3f) * maxA * 0.15f * (0.5f + c.bass) }
            val n = 40
            for (i in 0 until n) {
                val f = i / (n - 1f)
                val r = maxA * (0.12f + c.hump(f, 4f) * (0.25f + c.band(f) * 0.5f))
                drawCircle(b, r, Offset(f * c.w, c.mid + sin(f * 14f - c.t * 3f) * maxA * 0.15f * (0.5f + c.bass)), alpha = if (dim) 0.6f else 0.35f)
            }
            glowLine(p, b, 2.2f * d1, dim)
        }
        // barritas de colores de distintas alturas, desde abajo
        "rainbow" -> {
            val n = 48; val gap = c.w / n
            for (i in 0 until n) {
                val f = i / (n - 1f)
                val a = c.h * 0.85f * (0.15f + rnd(i) * 0.35f + c.band(f) * 0.6f + c.idle(f, 1.5f) * 0.3f).coerceAtMost(1f)
                drawRoundRect(b, Offset(i * gap + gap * 0.15f, c.mid + c.h * 0.42f - a), Size(gap * 0.7f, a),
                    androidx.compose.ui.geometry.CornerRadius(gap * 0.3f))
            }
        }
        // varias ondas finas que se cruzan y se rellenan
        "neonflow" -> {
            for (k in 0 until 4) {
                val p = wavePath(c) { f -> c.mid + sin(f * (10f + k * 3f) - c.t * (2f + k * 0.6f) + k) * maxA * c.hump(f, 2f) * (0.4f + c.bass * 0.8f) }
                glowLine(p, b, (1.4f - k * 0.2f) * d1, dim || k > 0)
            }
        }
        // líneas finas de distintas alturas alrededor del centro (como nota de voz)
        "spikes" -> {
            val n = 90; val gap = c.w / n
            for (i in 0 until n) {
                val f = i / (n - 1f)
                val a = maxA * (0.08f + rnd(i * 3) * 0.5f * (0.5f + c.band(f)) + c.bass * 0.3f * rnd(i + 7)) * (0.5f + 0.5f * c.amp)
                drawLine(b, Offset(i * gap, c.mid - a), Offset(i * gap, c.mid + a), strokeWidth = d1 * 1.3f, cap = StrokeCap.Round)
            }
            drawLine(b, Offset(0f, c.mid), Offset(c.w, c.mid), strokeWidth = d1 * 0.8f)
        }
        // dos ondas desfasadas con relleno entre ellas, como listón que se tuerce
        "ribbon" -> {
            val y1 = { f: Float -> c.mid + sin(f * 9f - c.t * 2.4f) * maxA * (0.55f + c.bass * 0.5f) * c.hump(f, 1.5f) }
            val y2 = { f: Float -> c.mid + sin(f * 9f - c.t * 2.4f + 2.2f) * maxA * (0.55f + c.bass * 0.5f) * c.hump(f, 1.5f) }
            val fill = Path().apply {
                var px = 0f; moveTo(0f, y1(0f)); while (px <= c.w) { lineTo(px, y1(px / c.w)); px += 3f }
                px = c.w; while (px >= 0f) { lineTo(px, y2(px / c.w)); px -= 3f }; close()
            }
            drawPath(fill, b, alpha = if (dim) 0.5f else 0.45f)
            glowLine(wavePath(c, 3f, y1), b, 1.6f * d1, dim)
            glowLine(wavePath(c, 3f, y2), b, 1.2f * d1, true)
        }
        // matriz de puntitos que se encienden con la música
        "ledmatrix" -> {
            val n = 30; val rows = 7; val gx = c.w / n; val gy = c.h * 0.9f / rows; val r = minOf(gx, gy) * 0.28f
            for (i in 0 until n) {
                val f = i / (n - 1f)
                val lvl = ((c.band(f) + c.idle(f, 0.8f) * 0.7f + rnd(i) * 0.15f) * rows).toInt().coerceIn(1, rows)
                for (j in 0 until lvl) drawCircle(b, r, Offset(i * gx + gx / 2, c.mid + c.h * 0.45f - gy * (j + 0.5f)), alpha = 0.55f + 0.45f * (j + 1f) / lvl)
            }
        }
        // ráfaga: muchas líneas cian en espejo, más largas al centro
        "burst" -> {
            val n = 110; val gap = c.w / n
            for (i in 0 until n) {
                val f = i / (n - 1f)
                val env = exp(-((f - 0.5f) * (f - 0.5f)) * 3f)
                val a = maxA * env * (0.2f + rnd(i * 5) * 0.8f) * (0.45f + c.band(f) * 0.8f + c.bass * 0.4f)
                drawLine(b, Offset(i * gap, c.mid - a), Offset(i * gap, c.mid + a), strokeWidth = d1, alpha = 0.6f + rnd(i) * 0.4f)
            }
        }
        // montañas de fuego en espejo, llenas
        "fire" -> {
            val top = Path(); val bot = Path()
            top.moveTo(0f, c.mid); bot.moveTo(0f, c.mid)
            var px = 0f
            while (px <= c.w) {
                val f = px / c.w
                val a = maxA * (0.2f + c.hump(f, 3f) * (0.4f + c.band(f) * 0.6f) + 0.12f * sin(f * 40f + c.t * 6f) * c.amp)
                top.lineTo(px, c.mid - a); bot.lineTo(px, c.mid + a * 0.6f); px += 4f
            }
            top.lineTo(c.w, c.mid); top.close(); bot.lineTo(c.w, c.mid); bot.close()
            drawPath(top, b); drawPath(bot, b, alpha = 0.55f)
        }
        // gota de luz: lente lisa que se infla con los bajos
        "lens" -> {
            val p = Path(); var px = 0f
            p.moveTo(0f, c.mid)
            while (px <= c.w) { val f = px / c.w; p.lineTo(px, c.mid - maxA * c.hump(f, 2f) * (0.25f + c.band(f) * 0.6f + c.bass * 0.3f) * sin(f * PI.toFloat())); px += 3f }
            px = c.w
            while (px >= 0f) { val f = px / c.w; p.lineTo(px, c.mid + maxA * c.hump(f, 2f) * (0.25f + c.band(f) * 0.6f + c.bass * 0.3f) * sin(f * PI.toFloat())); px -= 3f }
            p.close()
            drawPath(p, b, alpha = if (dim) 0.6f else 0.85f)
            if (!dim) drawLine(Color.White.copy(alpha = 0.9f), Offset(0f, c.mid), Offset(c.w, c.mid), strokeWidth = d1)
        }
        // garabato: línea delgada con varias frecuencias, nerviosa
        "scribble" -> {
            val p = wavePath(c, 2f) { f ->
                c.mid + maxA * c.hump(f, 2.5f) * (0.35f + c.bass * 0.6f) *
                    (sin(f * 30f - c.t * 4f) * 0.6f + sin(f * 63f + c.t * 2.5f) * 0.3f + sin(f * 11f - c.t) * 0.4f)
            }
            glowLine(p, b, 1.4f * d1, dim)
        }
        // seda: muchas líneas finas con fase distinta
        "silk" -> {
            for (k in 0 until 12) {
                val ph = k * 0.22f
                val p = wavePath(c, 4f) { f -> c.mid + sin(f * 7f - c.t * 1.8f + ph) * maxA * (0.3f + c.bass * 0.6f) * c.hump(f, 1f) * (0.5f + k / 24f) }
                drawPath(p, b, alpha = if (dim) 0.5f else 0.25f + k / 24f, style = Stroke(d1 * 0.8f))
            }
        }
        // colinas translúcidas encimadas sobre la base
        "hills" -> {
            for (k in 0 until 3) {
                val p = Path(); p.moveTo(0f, c.mid + c.h * 0.4f); var px = 0f
                while (px <= c.w) {
                    val f = px / c.w
                    val a = c.h * 0.8f * (0.15f + 0.5f * abs(sin(f * (3f + k) * PI.toFloat() + k + c.t * 0.4f)) * (0.4f + c.band(f) * 0.8f + c.bass * 0.3f))
                    p.lineTo(px, c.mid + c.h * 0.4f - a); px += 4f
                }
                p.lineTo(c.w, c.mid + c.h * 0.4f); p.close()
                drawPath(p, b, alpha = if (dim) 0.4f else 0.35f + k * 0.15f)
            }
        }
        // dos ondas que se cruzan (rosa y cian)
        "dual" -> {
            val p1 = wavePath(c) { f -> c.mid + sin(f * 12f - c.t * 3f) * maxA * (0.45f + c.bass * 0.5f) * c.hump(f, 2f) }
            val p2 = wavePath(c) { f -> c.mid + sin(f * 12f - c.t * 3f + PI.toFloat()) * maxA * (0.45f + c.band(f) * 0.6f) * c.hump(f, 2f) }
            glowLine(p1, b, 2f * d1, dim); glowLine(p2, b, 2f * d1, dim)
        }
        // rayo: línea con perlas de luz que laten
        "beam" -> {
            drawLine(b, Offset(0f, c.mid), Offset(c.w, c.mid), strokeWidth = 2.5f * d1, alpha = if (dim) 1f else 0.9f)
            val n = 9
            for (i in 0 until n) {
                val f = (i + 0.5f) / n
                val r = maxA * (0.1f + c.hump(f, 1f) * (0.2f + c.band(f) * 0.45f + c.bass * 0.2f))
                val cx = f * c.w
                if (!dim) drawCircle(b, r * 2.4f, Offset(cx, c.mid), alpha = 0.18f)
                drawOval(b, Offset(cx - r * 1.8f, c.mid - r), Size(r * 3.6f, r * 2f))
            }
        }
        // pétalos: lentes encimadas de colores
        "petals" -> {
            val n = 7
            for (i in 0 until n) {
                val f = (i + 0.5f) / n
                val r = maxA * (0.35f + c.band(f) * 0.6f + c.bass * 0.2f) * (0.7f + 0.3f * sin(c.t * 2f + i))
                val cx = f * c.w; val half = c.w / n * 0.75f
                val p = Path().apply {
                    moveTo(cx - half, c.mid); quadraticBezierTo(cx, c.mid - r * 2f, cx + half, c.mid); quadraticBezierTo(cx, c.mid + r * 2f, cx - half, c.mid); close()
                }
                drawPath(p, b, alpha = if (dim) 0.5f else 0.6f, blendMode = if (dim) BlendMode.SrcOver else BlendMode.Plus)
            }
        }
        // malla 3D: muchas ondas con perspectiva
        "mesh" -> {
            for (k in 0 until 16) {
                val z = k / 15f
                val p = wavePath(c, 4f) { f ->
                    c.mid + (sin(f * 6f + z * 3f - c.t * 1.6f) * (0.6f + c.bass * 0.6f) + (z - 0.5f) * 0.8f) * maxA * 0.8f * c.hump(f, 1f)
                }
                drawPath(p, b, alpha = if (dim) 0.45f else 0.2f + 0.6f * (1f - abs(z - 0.5f) * 2f), style = Stroke(d1 * 0.9f))
            }
        }
        // línea suave con partículas que flotan alrededor
        "orbit" -> {
            val y = { f: Float -> c.mid + sin(f * 8f - c.t * 2f) * maxA * 0.35f * (0.5f + c.bass) * c.hump(f, 1f) }
            glowLine(wavePath(c, 3f, y), b, 1.5f * d1, dim)
            for (i in 0 until 40) {
                val f = rnd(i)
                val off = (rnd(i + 99) - 0.5f) * maxA * 1.6f * (0.6f + c.level)
                val r = d1 * (0.8f + rnd(i + 3) * 1.8f) * (1f + c.bass)
                drawCircle(b, r, Offset(f * c.w, y(f) + off + sin(c.t * 2f + i) * d1 * 3f), alpha = 0.5f + 0.5f * rnd(i + 5))
            }
        }
        // aurora: ondas anchas translúcidas
        else -> {
            for (k in 0 until 4) {
                val p = Path(); var px = 0f
                p.moveTo(0f, c.mid)
                while (px <= c.w) { val f = px / c.w; p.lineTo(px, c.mid - maxA * (0.3f + c.band(f) * 0.6f) * (0.5f + 0.5f * sin(f * (4f + k) - c.t * (1f + k * 0.4f) + k))); px += 4f }
                px = c.w
                while (px >= 0f) { val f = px / c.w; p.lineTo(px, c.mid + maxA * 0.25f * (0.5f + 0.5f * sin(f * (5f + k) + c.t + k))); px -= 4f }
                p.close()
                drawPath(p, b, alpha = if (dim) 0.35f else 0.28f, blendMode = if (dim) BlendMode.SrcOver else BlendMode.Plus)
            }
        }
    }
}
