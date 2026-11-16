package com.tony.explorador

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlin.math.cos
import kotlin.math.sin

/** Descarga con estilo nebulosa: anillo de luz que se llena, estrellitas orbitando y el núcleo que late. */
@Composable
fun NebulaDownloadDialog(tag: String, progress: Float, totalBytes: Long, onBackground: () -> Unit) {
    Dialog(onDismissRequest = onBackground) {
        val shown by animateFloatAsState(progress.coerceIn(0f, 1f), tween(400), label = "p")
        val t = rememberInfiniteTransition(label = "neb")
        val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "spin")
        val pulse by t.animateFloat(0.85f, 1.15f, infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "pulse")
        val stars = remember { List(18) { Triple(Math.random().toFloat(), 0.55f + Math.random().toFloat() * 0.45f, 0.5f + Math.random().toFloat()) } }
        Column(
            Modifier.clip(RoundedCornerShape(28.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF241245), Color(0xFF0D0820))))
                .border(1.dp, Brush.linearGradient(listOf(Color(0xFFB98CFF), Color(0xFF5AC8FF))), RoundedCornerShape(28.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Actualizando a $tag", color = Color(0xFFF4EEFF), fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(16.dp))
            Box(Modifier.size(200.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(200.dp)) {
                    val c = Offset(size.width / 2, size.height / 2)
                    val r = size.minDimension / 2 - 16.dp.toPx()
                    // brillo del fondo
                    drawCircle(Brush.radialGradient(listOf(Color(0x557A3CFF), Color.Transparent), c, r * 1.3f), r * 1.3f, c)
                    // pista
                    drawCircle(Color(0x22FFFFFF), r, c, style = Stroke(10.dp.toPx()))
                    // anillo de avance con degradado que gira
                    rotate(spin, c) {
                        drawArc(Brush.sweepGradient(listOf(Color(0xFF5AC8FF), Color(0xFFB98CFF), Color(0xFFFF7AC6), Color(0xFF5AC8FF)), c),
                            -90f - spin, 360f * shown, false, Offset(c.x - r, c.y - r), Size(r * 2, r * 2), style = Stroke(10.dp.toPx(), cap = StrokeCap.Round))
                    }
                    // punta brillante
                    val ang = Math.toRadians((-90f + 360f * shown).toDouble())
                    val tip = Offset(c.x + r * cos(ang).toFloat(), c.y + r * sin(ang).toFloat())
                    drawCircle(Brush.radialGradient(listOf(Color.White, Color(0x00FFFFFF)), tip, 16.dp.toPx()), 16.dp.toPx(), tip)
                    // estrellitas en órbita
                    stars.forEach { (a0, rr, sp) ->
                        val a = Math.toRadians((a0 * 360f + spin * sp).toDouble())
                        val p = Offset(c.x + r * rr * 0.8f * cos(a).toFloat(), c.y + r * rr * 0.8f * 0.55f * sin(a).toFloat())
                        drawCircle(Color.White.copy(alpha = 0.35f + 0.5f * rr), 1.6.dp.toPx() * sp, p)
                    }
                    // núcleo de la nebulosa
                    drawCircle(Brush.radialGradient(listOf(Color(0xFFFFFFFF), Color(0xFFB98CFF), Color.Transparent), c, 34.dp.toPx() * pulse), 34.dp.toPx() * pulse, c)
                }
                Text("${(shown * 100).toInt()}%", color = Color(0xFF1A0F3A), fontWeight = FontWeight.Black, fontSize = 22.sp)
            }
            Spacer(Modifier.height(12.dp))
            val mb = totalBytes / 1048576f
            Text(if (totalBytes > 0) "%.1f MB de %.1f MB".format(mb * shown, mb) else "Bajando…", color = Color(0xFFB4A8D6), fontSize = 13.sp)
            Text("Puedes salir de la app: sigue bajando y te avisa.", color = Color(0xFFB4A8D6), fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(16.dp))
            Text("Seguir en segundo plano", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                modifier = Modifier.clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFFB98CFF), Color(0xFFFF7AC6))))
                    .clickable(onClick = onBackground).padding(horizontal = 20.dp, vertical = 10.dp))
        }
    }
}
