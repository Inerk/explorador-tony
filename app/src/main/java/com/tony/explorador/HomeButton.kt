package com.tony.explorador

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

/** Avisa en el escritorio cuando hay una versión nueva. */
object UpdateBadge {
    var available by mutableStateOf(false)
}

/** Puntito rojo de aviso (como notificación). */
@Composable
fun NotificationDot(modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 16.dp) {
    val pulse by rememberInfiniteTransition(label = "dot").animateFloat(
        0.85f, 1.15f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "p")
    Canvas(modifier.size(size).graphicsLayer { scaleX = pulse; scaleY = pulse }) {
        val r = this.size.width / 2f
        drawCircle(Color.White, r)
        drawCircle(Brush.radialGradient(listOf(Color(0xFFFF8A80), Color(0xFFE53935)), center = Offset(r * 0.8f, r * 0.7f), radius = r * 1.4f), r * 0.82f)
        drawCircle(Color.White.copy(alpha = 0.7f), r * 0.2f, Offset(r * 0.75f, r * 0.65f))
    }
}

/**
 * Botón que regresa al Inicio desde cualquier pantalla: una pequeña nebulosa
 * que gira despacio, con estrellas que parpadean. Al tocarla da un destello.
 */
@Composable
fun HomeButton(onClick: () -> Unit, modifier: Modifier = Modifier, @Suppress("UNUSED_PARAMETER") dark: Boolean = true) {
    val press = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    Box(
        modifier.size(30.dp)
            .graphicsLayer { scaleX = press.value; scaleY = press.value }
            .clip(CircleShape)
            .semantics { contentDescription = "Inicio" }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                scope.launch {
                    press.animateTo(1.35f, tween(110))
                    press.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium))
                }
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        NebulaOrb(Modifier.fillMaxSize())
    }
}

/** La nebulosa animada (se usa en el botón de regresar y en el botón Inicio). */
@Composable
fun NebulaOrb(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "nebula")
    val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(14000, easing = LinearEasing)), label = "spin")
    val twinkle by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "tw")
        Canvas(modifier) {
            val w = size.width
            val c = Offset(w / 2f, w / 2f)
            val r = w / 2f
            // espacio profundo
            drawCircle(Brush.radialGradient(listOf(Color(0xFF2A1B5E), Color(0xFF0B0820)), center = c, radius = r), r, c)
            // nubes de la nebulosa girando
            rotate(spin, c) {
                drawCircle(Brush.radialGradient(listOf(Color(0xCCFF5FC8), Color.Transparent), center = Offset(c.x - r * 0.28f, c.y - r * 0.18f), radius = r * 0.75f),
                    r * 0.75f, Offset(c.x - r * 0.28f, c.y - r * 0.18f))
                drawCircle(Brush.radialGradient(listOf(Color(0xCC4FC3FF), Color.Transparent), center = Offset(c.x + r * 0.3f, c.y + r * 0.2f), radius = r * 0.75f),
                    r * 0.75f, Offset(c.x + r * 0.3f, c.y + r * 0.2f))
                drawCircle(Brush.radialGradient(listOf(Color(0xAA9B6BFF), Color.Transparent), center = Offset(c.x + r * 0.1f, c.y - r * 0.35f), radius = r * 0.55f),
                    r * 0.55f, Offset(c.x + r * 0.1f, c.y - r * 0.35f))
                // brazos en espiral
                for (arm in 0..1) {
                    rotate(arm * 180f, c) {
                        drawArc(Color.White.copy(alpha = 0.55f), startAngle = 200f, sweepAngle = 150f, useCenter = false,
                            topLeft = Offset(c.x - r * 0.6f, c.y - r * 0.6f), size = Size(r * 1.2f, r * 1.2f), style = Stroke(w * 0.035f))
                    }
                }
            }
            // núcleo brillante
            drawCircle(Brush.radialGradient(listOf(Color.White, Color(0xFFFFE9A8), Color.Transparent), center = c, radius = r * 0.36f), r * 0.36f, c)
            // estrellitas que parpadean
            val stars = listOf(Triple(40f, 0.72f, 0f), Triple(150f, 0.6f, 0.5f), Triple(250f, 0.78f, 0.25f), Triple(320f, 0.55f, 0.8f))
            stars.forEach { (ang, dist, phase) ->
                val a = Math.toRadians((ang - spin * 0.3f).toDouble())
                val p = Offset(c.x + (cos(a) * r * dist).toFloat(), c.y + (sin(a) * r * dist).toFloat())
                val alpha = (0.35f + 0.65f * kotlin.math.abs(((twinkle + phase) % 1f) * 2f - 1f))
                drawCircle(Color.White.copy(alpha = alpha), w * 0.028f, p)
            }
            // orilla
            drawCircle(Color.White.copy(alpha = 0.55f), r - w * 0.02f, c, style = Stroke(w * 0.04f))
        }
}
