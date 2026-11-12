package com.tony.explorador

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.sin

/** Ventana estilo XP de "Descargando actualizaciones", con hojas volando del globo a la computadora. */
@Composable
fun XpDownloadDialog(tag: String, progress: Float, totalBytes: Long) {
    val start = remember { System.currentTimeMillis() }
    Dialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)) {
        val shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 3.dp, bottomEnd = 3.dp)
        Column(
            Modifier.width(320.dp).shadow(16.dp, shape).clip(shape)
                .background(XpColors.TitleBottom).padding(start = 3.dp, end = 3.dp, bottom = 3.dp)
        ) {
            // barra de título
            Row(
                Modifier.fillMaxWidth().height(30.dp).background(XpColors.titleBrush).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Actualizaciones automáticas", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Column(Modifier.fillMaxWidth().background(XpColors.Window).padding(14.dp)) {
                Text("Descargando actualizaciones…", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.Black)
                Spacer(Modifier.height(10.dp))
                FlyingPapers(Modifier.fillMaxWidth().height(70.dp))
                Spacer(Modifier.height(10.dp))
                Text("Explorador Tony $tag", fontSize = 13.sp, color = Color.Black)
                Spacer(Modifier.height(6.dp))
                XpBlocksBar(progress)
                Spacer(Modifier.height(6.dp))
                val done = (totalBytes * progress).toLong()
                Text(
                    if (totalBytes > 0) "${formatSize(done)} de ${formatSize(totalBytes)} (${(progress * 100).toInt()} %)"
                    else "${(progress * 100).toInt()} %",
                    fontSize = 12.sp, color = Color(0xFF333333)
                )
                val elapsed = (System.currentTimeMillis() - start) / 1000f
                val remaining = if (progress > 0.03f) ((elapsed / progress) * (1 - progress)).toInt() else -1
                Text(
                    when {
                        remaining < 0 -> "Calculando tiempo restante…"
                        remaining < 60 -> "Tiempo restante: $remaining segundos"
                        else -> "Tiempo restante: ${remaining / 60} min ${remaining % 60} s"
                    },
                    fontSize = 12.sp, color = Color(0xFF333333)
                )
            }
        }
    }
}

/** Globo terráqueo a la izquierda, computadora a la derecha y hojas volando entre los dos. */
@Composable
private fun FlyingPapers(modifier: Modifier) {
    val t by rememberInfiniteTransition(label = "papers").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart), label = "t"
    )
    val spin by rememberInfiniteTransition(label = "globe").animateFloat(
        0f, 1f, infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Restart), label = "spin"
    )
    Box(modifier) {
        Canvas(Modifier.size(1.dp)) {} // espacio reservado
        Canvas(Modifier.matchParentSize()) {
            val h = size.height
            val globeC = Offset(h * 0.5f + 8f, h * 0.5f)
            drawGlobe(globeC, h * 0.42f, spin)
            val pcX = size.width - h * 1.0f
            drawMiniComputer(Offset(pcX, h * 0.08f), h * 0.86f)
            // hojas en vuelo (3, desfasadas)
            val from = Offset(globeC.x + h * 0.3f, h * 0.5f)
            val to = Offset(pcX + h * 0.2f, h * 0.45f)
            for (k in 0..2) {
                val p = (t + k / 3f) % 1f
                val x = from.x + (to.x - from.x) * p
                val y = from.y + (to.y - from.y) * p - sin(p * Math.PI).toFloat() * h * 0.45f
                val alpha = when {
                    p < 0.1f -> p / 0.1f
                    p > 0.9f -> (1 - p) / 0.1f
                    else -> 1f
                }
                rotate(-25f + p * 50f, Offset(x, y)) {
                    drawPaper(Offset(x, y), h * 0.22f, alpha)
                }
            }
        }
    }
}

private fun DrawScope.drawPaper(c: Offset, s: Float, alpha: Float) {
    val tl = Offset(c.x - s * 0.4f, c.y - s * 0.5f)
    val sz = Size(s * 0.8f, s)
    drawRect(Color.White.copy(alpha = alpha), tl, sz)
    drawRect(Color(0xFF7F9DB9).copy(alpha = alpha), tl, sz, style = Stroke(1.5f))
    for (i in 1..3) {
        val y = tl.y + sz.height * i / 4.5f
        drawLine(Color(0xFF6D8FCB).copy(alpha = alpha), Offset(tl.x + 3f, y), Offset(tl.x + sz.width - 3f, y), strokeWidth = 1.5f)
    }
}

private fun DrawScope.drawGlobe(c: Offset, r: Float, spin: Float) {
    drawCircle(Brush.radialGradient(listOf(Color(0xFF9FD3FF), Color(0xFF1E6FD8), Color(0xFF0A3A8C)),
        center = Offset(c.x - r * 0.3f, c.y - r * 0.3f), radius = r * 1.5f), r, c)
    // "continentes" que giran
    val shift = spin * r * 2f
    for (k in -1..1) {
        val bx = c.x - r + ((shift + k * r * 2f) % (r * 2f) + r * 2f) % (r * 2f) - r * 0.2f
        val land = Path().apply {
            moveTo(bx, c.y - r * 0.5f)
            cubicTo(bx + r * 0.5f, c.y - r * 0.7f, bx + r * 0.6f, c.y - r * 0.1f, bx + r * 0.3f, c.y + r * 0.1f)
            cubicTo(bx + r * 0.5f, c.y + r * 0.5f, bx + r * 0.1f, c.y + r * 0.6f, bx - r * 0.05f, c.y + r * 0.3f)
            close()
        }
        drawPath(land, Color(0xFF4FC24F).copy(alpha = 0.9f))
    }
    // tapar lo que se sale del círculo con un anillo del color de la ventana
    drawCircle(XpColors.Window, r * 1.6f, c, style = Stroke(r * 1.2f))
    drawCircle(Color(0xFF0A3A8C), r, c, style = Stroke(2f))
    drawOval(Color.White.copy(alpha = 0.35f), Offset(c.x - r * 0.55f, c.y - r * 0.85f), Size(r * 0.9f, r * 0.5f))
}

private fun DrawScope.drawMiniComputer(o: Offset, s: Float) {
    drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFF3F0E4), Color(0xFFCFC8B0))),
        Offset(o.x, o.y), Size(s, s * 0.7f), androidx.compose.ui.geometry.CornerRadius(s * 0.06f))
    drawRect(Brush.verticalGradient(listOf(Color(0xFF2E6FD8), Color(0xFF9CC9F5))),
        Offset(o.x + s * 0.09f, o.y + s * 0.08f), Size(s * 0.82f, s * 0.52f))
    drawRect(Brush.verticalGradient(listOf(Color(0xFFDCD5BE), Color(0xFFB4AC92))),
        Offset(o.x + s * 0.3f, o.y + s * 0.7f), Size(s * 0.4f, s * 0.12f))
    drawRect(Brush.verticalGradient(listOf(Color(0xFFE9E4D2), Color(0xFFB4AC92))),
        Offset(o.x + s * 0.12f, o.y + s * 0.82f), Size(s * 0.76f, s * 0.1f))
}

/** Barra de progreso de bloques verdes como la de XP. */
@Composable
fun XpBlocksBar(progress: Float) {
    Canvas(
        Modifier.fillMaxWidth().height(18.dp)
            .border(1.dp, Color(0xFF7F9DB9), RoundedCornerShape(3.dp))
            .background(Color.White, RoundedCornerShape(3.dp))
    ) {
        val pad = 3.dp.toPx()
        val block = 8.dp.toPx()
        val gap = 2.dp.toPx()
        val filled = (size.width - pad * 2) * progress.coerceIn(0f, 1f)
        var x = pad
        while (x + block <= pad + filled + 0.5f) {
            drawRect(
                Brush.verticalGradient(listOf(Color(0xFFB5F0A8), Color(0xFF34C424), Color(0xFF1E9A12)),
                    startY = pad, endY = size.height - pad),
                topLeft = Offset(x, pad), size = Size(block, size.height - pad * 2)
            )
            x += block + gap
        }
    }
}
