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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.sin
import kotlin.random.Random

/** Colores del tema Nebula: noche morada con rosa neón. */
object Nebula {
    val Pink = Color(0xFFFF4F9A)
    val PinkLight = Color(0xFFFF8CC6)
    val Purple = Color(0xFF8B5CF6)
    val Violet = Color(0xFFB57BFF)
    val Blue = Color(0xFF5B7CFF)
    val Night = Color(0xFF120B26)
    val Deep = Color(0xFF080512)
    val Glass = Color.White.copy(alpha = 0.07f)
    val GlassLine = Color.White.copy(alpha = 0.12f)
    val Text = Color.White
    val Sub = Color(0xFFB9ABDA)
}

val isNebulaSkin get() = LibraryStore.musicSkin == MusicSkin.NEBULA

/** Fondo: degradado nocturno, nubes de color suaves y estrellitas que titilan. */
@Composable
fun NebulaBackground(modifier: Modifier = Modifier) {
    val stars = remember { List(70) { Triple(Random.nextFloat(), Random.nextFloat(), Random.nextFloat()) } }
    val t by rememberInfiniteTransition(label = "neb").animateFloat(0f, 1f, infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "t")
    Canvas(modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(listOf(Color(0xFF1B1038), Nebula.Night, Nebula.Deep)))
        val w = size.width; val h = size.height
        fun blob(cx: Float, cy: Float, r: Float, c: Color) =
            drawCircle(Brush.radialGradient(listOf(c, Color.Transparent), Offset(cx, cy), r), r, Offset(cx, cy))
        val wob = sin(t * 2f * Math.PI.toFloat())
        blob(w * (0.95f + 0.03f * wob), h * 0.08f, w * 0.7f, Nebula.Purple.copy(alpha = 0.35f))
        blob(w * 0.05f, h * (0.62f - 0.02f * wob), w * 0.65f, Nebula.Pink.copy(alpha = 0.22f))
        blob(w * 0.9f, h * 0.95f, w * 0.7f, Nebula.Blue.copy(alpha = 0.22f))
        stars.forEachIndexed { i, (x, y, s) ->
            val tw = 0.35f + 0.65f * ((sin((t * 6.28f * (1 + i % 3)) + i) + 1f) / 2f)
            drawCircle(Color.White.copy(alpha = 0.55f * tw * s), (0.6f + s * 1.4f).dp.toPx(), Offset(x * w, y * h))
        }
    }
}

/** Gatito con anillo de planeta (logo chiquito de arriba). */
@Composable
fun NebulaCatLogo(size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        val ring = Brush.horizontalGradient(listOf(Nebula.Pink, Nebula.Violet, Nebula.Blue))
        // anillo de atrás
        rotate(-14f) { drawOval(ring, Offset(w * 0.02f, h * 0.48f), Size(w * 0.96f, h * 0.3f), style = Stroke(w * 0.06f)) }
        val head = Path().apply {
            moveTo(w * 0.2f, h * 0.42f)
            lineTo(w * 0.24f, h * 0.1f); lineTo(w * 0.42f, h * 0.27f)
            quadraticBezierTo(w * 0.5f, h * 0.24f, w * 0.58f, h * 0.27f)
            lineTo(w * 0.76f, h * 0.1f); lineTo(w * 0.8f, h * 0.42f)
            quadraticBezierTo(w * 0.86f, h * 0.82f, w * 0.5f, h * 0.84f)
            quadraticBezierTo(w * 0.14f, h * 0.82f, w * 0.2f, h * 0.42f)
            close()
        }
        drawPath(head, Brush.verticalGradient(listOf(Color(0xFF7B5CFF), Color(0xFF3B1E8C))))
        drawPath(head, Color.White.copy(alpha = 0.35f), style = Stroke(w * 0.025f))
        drawOval(Color.White, Offset(w * 0.36f, h * 0.48f), Size(w * 0.07f, h * 0.12f))
        drawOval(Color.White, Offset(w * 0.57f, h * 0.48f), Size(w * 0.07f, h * 0.12f))
        drawArc(Color.White, 20f, 140f, false, Offset(w * 0.44f, h * 0.6f), Size(w * 0.12f, h * 0.08f), style = Stroke(w * 0.025f, cap = StrokeCap.Round))
        // anillo de enfrente (mitad de abajo)
        rotate(-14f) { drawArc(ring, 0f, 180f, false, Offset(w * 0.02f, h * 0.48f), Size(w * 0.96f, h * 0.3f), style = Stroke(w * 0.06f, cap = StrokeCap.Round)) }
    }
}

/** Orejitas de gato, brillo y anillo alrededor de la portada (se dibuja detrás de ella). */
fun DrawScope.nebulaCoverBack(x: Float, y: Float, s: Float, alpha: Float) {
    if (alpha <= 0f) return
    val ear = Brush.verticalGradient(listOf(Nebula.Violet.copy(alpha = alpha), Nebula.Pink.copy(alpha = 0.85f * alpha)), y - s * 0.16f, y + s * 0.2f)
    fun earPath(left: Boolean): Path = Path().apply {
        val bx = if (left) x + s * 0.04f else x + s * 0.96f
        val tip = if (left) x + s * 0.1f else x + s * 0.9f
        val inner = if (left) x + s * 0.36f else x + s * 0.64f
        moveTo(bx, y + s * 0.18f)
        quadraticBezierTo(bx - (if (left) s * 0.03f else -s * 0.03f), y - s * 0.12f, tip, y - s * 0.13f)
        quadraticBezierTo(inner - (if (left) s * 0.04f else -s * 0.04f), y - s * 0.12f, inner, y + s * 0.05f)
        close()
    }
    drawPath(earPath(true), ear); drawPath(earPath(false), ear)
    // resplandor
    val r = s * 0.12f
    for (k in 3 downTo 1) {
        val g = k * s * 0.018f
        drawRoundRect(Nebula.Purple.copy(alpha = 0.12f * alpha), Offset(x - g, y - g), Size(s + 2 * g, s + 2 * g), CornerRadius(r + g))
    }
    // anillo de planeta (parte de atrás)
    rotate(-12f, Offset(x + s / 2, y + s / 2)) {
        drawOval(Brush.horizontalGradient(listOf(Nebula.Blue.copy(alpha = 0.8f * alpha), Nebula.Violet.copy(alpha = 0.7f * alpha), Nebula.Pink.copy(alpha = 0.9f * alpha))),
            Offset(x - s * 0.14f, y + s * 0.5f), Size(s * 1.28f, s * 0.36f), style = Stroke(s * 0.022f))
    }
}

/** Lo que va encima de la portada: borde neón, parte de enfrente del anillo y la estrella. */
fun DrawScope.nebulaCoverFront(x: Float, y: Float, s: Float, alpha: Float, twinkle: Float) {
    if (alpha <= 0f) return
    val r = s * 0.12f
    drawRoundRect(Brush.linearGradient(listOf(Nebula.Violet.copy(alpha = alpha), Nebula.Pink.copy(alpha = alpha), Nebula.Blue.copy(alpha = alpha)),
        Offset(x, y), Offset(x + s, y + s)), Offset(x, y), Size(s, s), CornerRadius(r), style = Stroke(s * 0.012f))
    // frente del anillo: solo los lados (para que se vea que pasa por delante)
    rotate(-12f, Offset(x + s / 2, y + s / 2)) {
        val ringBrush = Brush.horizontalGradient(listOf(Nebula.Blue.copy(alpha = alpha), Nebula.Pink.copy(alpha = alpha)))
        drawArc(ringBrush, 100f, 50f, false, Offset(x - s * 0.14f, y + s * 0.5f), Size(s * 1.28f, s * 0.36f), style = Stroke(s * 0.024f, cap = StrokeCap.Round))
        drawArc(ringBrush, 25f, 45f, false, Offset(x - s * 0.14f, y + s * 0.5f), Size(s * 1.28f, s * 0.36f), style = Stroke(s * 0.024f, cap = StrokeCap.Round))
    }
    // estrella de 4 picos
    val cx = x + s * 0.9f; val cy = y + s * 0.1f; val a = s * (0.06f + 0.015f * twinkle)
    val star = Path().apply {
        moveTo(cx, cy - a); quadraticBezierTo(cx, cy, cx + a, cy); quadraticBezierTo(cx, cy, cx, cy + a)
        quadraticBezierTo(cx, cy, cx - a, cy); quadraticBezierTo(cx, cy, cx, cy - a); close()
    }
    drawCircle(Brush.radialGradient(listOf(Nebula.PinkLight.copy(alpha = 0.6f * alpha), Color.Transparent), Offset(cx, cy), a * 2f), a * 2f, Offset(cx, cy))
    drawPath(star, Color.White.copy(alpha = alpha))
}

/** Botón cuadradito de cristal (aleatorio, anterior, siguiente, repetir). */
@Composable
fun NebulaGlassButton(icon: ImageVector, description: String, tint: Color = Color.White, size: Dp = 54.dp, enabled: Boolean = true,
                      onClick: () -> Unit) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.34f))
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.04f))))
            .border(1.dp, Nebula.GlassLine, RoundedCornerShape(size * 0.34f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, description, tint = if (enabled) tint else tint.copy(alpha = 0.35f), modifier = Modifier.size(size * 0.5f)) }
}

/** Botón grande de play/pausa rosa con brillo. */
@Composable
fun NebulaPlayButton(icon: ImageVector, size: Dp = 84.dp, onClick: () -> Unit) {
    Box(
        Modifier.size(size).shadow(24.dp, CircleShape, ambientColor = Nebula.Pink, spotColor = Nebula.Pink).clip(CircleShape)
            .background(Brush.radialGradient(listOf(Nebula.PinkLight, Nebula.Pink, Color(0xFFD9327C))))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.align(Alignment.TopCenter).padding(top = size * 0.08f).size(size * 0.6f, size * 0.25f).clip(RoundedCornerShape(50))
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.35f), Color.Transparent))))
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(size * 0.5f))
    }
}

/** Barra de abajo: lista, música, ajustes y escritorio. */
@Composable
fun NebulaBottomNav(selected: Int, icons: List<Pair<ImageVector, String>>, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().height(62.dp).clip(RoundedCornerShape(26.dp))
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.04f))))
            .border(1.dp, Nebula.GlassLine, RoundedCornerShape(26.dp)).padding(6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically
    ) {
        icons.forEachIndexed { i, (ic, label) ->
            val on = i == selected
            Box(
                Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(20.dp))
                    .background(if (on) Brush.verticalGradient(listOf(Nebula.Pink.copy(alpha = 0.55f), Nebula.Purple.copy(alpha = 0.35f)))
                        else Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent)))
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center
            ) { Icon(ic, label, tint = if (on) Nebula.PinkLight else Color.White.copy(alpha = 0.85f), modifier = Modifier.size(24.dp)) }
        }
    }
}

private val staticStars = List(60) { val r = Random(it * 7919); Triple(r.nextFloat(), r.nextFloat(), r.nextFloat()) }

/** Nubes de color y estrellas quietas detrás de las listas (tema Nebula). */
fun Modifier.nebulaSky(on: Boolean): Modifier = if (!on) this else this.then(Modifier.drawBehind {
    val w = size.width; val h = size.height
    fun blob(cx: Float, cy: Float, r: Float, c: Color) =
        drawCircle(Brush.radialGradient(listOf(c, Color.Transparent), Offset(cx, cy), r), r, Offset(cx, cy))
    blob(w * 0.95f, h * 0.05f, w * 0.7f, Nebula.Purple.copy(alpha = 0.30f))
    blob(w * 0.0f, h * 0.7f, w * 0.6f, Nebula.Pink.copy(alpha = 0.16f))
    blob(w * 0.9f, h * 1.0f, w * 0.6f, Nebula.Blue.copy(alpha = 0.18f))
    staticStars.forEach { (x, y, s) -> drawCircle(Color.White.copy(alpha = 0.35f * s), (0.6f + s * 1.2f).dp.toPx(), Offset(x * w, y * h)) }
})
