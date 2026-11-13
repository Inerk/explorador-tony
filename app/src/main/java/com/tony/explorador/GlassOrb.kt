package com.tony.explorador

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Esfera de cristal en 3D: sombra abajo, luz desde arriba a la izquierda,
 * brillo reflejado arriba y un rebote de luz en la parte de abajo.
 */
@Composable
fun GlassOrb(icon: ImageVector, color: Color, size: Dp = 46.dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size + 6.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.toPx() / 2f
            val c = Offset(this.size.width / 2f, this.size.height / 2f - 2.dp.toPx())
            val light = lerp(color, Color.White, 0.55f)
            val dark = lerp(color, Color.Black, 0.55f)

            // sombra suave debajo
            drawOval(
                Brush.radialGradient(
                    listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent),
                    center = Offset(c.x, c.y + r * 0.95f), radius = r * 0.9f
                ),
                topLeft = Offset(c.x - r * 0.9f, c.y + r * 0.62f), size = Size(r * 1.8f, r * 0.66f)
            )
            // cuerpo de la esfera (luz arriba-izquierda)
            drawCircle(
                Brush.radialGradient(
                    0f to light, 0.45f to color, 1f to dark,
                    center = Offset(c.x - r * 0.3f, c.y - r * 0.35f), radius = r * 1.45f
                ),
                radius = r, center = c
            )
            // rebote de luz abajo
            drawCircle(
                Brush.radialGradient(
                    listOf(lerp(color, Color.White, 0.35f).copy(alpha = 0.75f), Color.Transparent),
                    center = Offset(c.x, c.y + r * 0.85f), radius = r * 0.75f
                ),
                radius = r, center = c
            )
            // borde
            drawCircle(dark.copy(alpha = 0.8f), radius = r, center = c, style = Stroke(1.2.dp.toPx()))
            // brillo de cristal (óvalo dentro de la esfera)
            drawOval(
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.85f), Color.White.copy(alpha = 0.08f)),
                    startY = c.y - r * 0.92f, endY = c.y - r * 0.05f
                ),
                topLeft = Offset(c.x - r * 0.62f, c.y - r * 0.92f),
                size = Size(r * 1.24f, r * 0.82f)
            )
            // destello pequeño
            drawCircle(Color.White.copy(alpha = 0.9f), radius = r * 0.09f, center = Offset(c.x - r * 0.42f, c.y - r * 0.5f))
        }
        // sombra del ícono + ícono
        Icon(icon, null, tint = Color.Black.copy(alpha = 0.3f),
            modifier = Modifier.size(size * 0.5f).offset(1.dp, (-1).dp))
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(size * 0.5f).offset(0.dp, (-2).dp))
    }
}

/** Medallón de latón para el tema Vintage: aro dorado y centro esmaltado del color de la sección. */
@Composable
fun BrassMedallion(icon: ImageVector, color: Color, size: Dp = 46.dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size + 6.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.toPx() / 2f
            val c = Offset(this.size.width / 2f, this.size.height / 2f - 1.dp.toPx())
            drawCircle(Color.Black.copy(alpha = 0.45f), r, c + Offset(2.dp.toPx(), 3.dp.toPx()))
            drawCircle(
                Brush.sweepGradient(listOf(Vintage.BrassDark, Vintage.BrassLight, Vintage.Brass, Vintage.BrassDark, Vintage.BrassLight, Vintage.BrassDark), center = c),
                r, c
            )
            drawCircle(
                Brush.radialGradient(listOf(lerp(color, Color.White, 0.25f), color, lerp(color, Color.Black, 0.5f)),
                    center = c - Offset(r * 0.25f, r * 0.3f), radius = r),
                r * 0.76f, c
            )
            drawCircle(Vintage.BrassDark, r * 0.76f, c, style = Stroke(1.5.dp.toPx()))
            drawOval(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.3f), Color.Transparent), startY = c.y - r * 0.7f, endY = c.y),
                Offset(c.x - r * 0.45f, c.y - r * 0.7f), Size(r * 0.9f, r * 0.55f))
        }
        Icon(icon, null, tint = Vintage.Cream, modifier = Modifier.size(size * 0.48f))
    }
}

/**
 * Ícono plano en 2D pero con relieve (tema Original): cuadrito redondeado con bisel,
 * brillo arriba, sombra abajo y el dibujo como "estampado" encima.
 */
@Composable
fun ReliefIcon(icon: ImageVector, color: Color, size: Dp = 46.dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size + 6.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val s = size.toPx()
            val o = Offset((this.size.width - s) / 2f, (this.size.height - s) / 2f - 1.dp.toPx())
            val r = androidx.compose.ui.geometry.CornerRadius(s * 0.28f)
            val sz = androidx.compose.ui.geometry.Size(s, s)
            // sombra en el piso
            drawRoundRect(Color.Black.copy(alpha = 0.35f), o + Offset(0f, 3.dp.toPx()), sz, r)
            // canto de abajo (más oscuro): da el grosor
            drawRoundRect(lerp(color, Color.Black, 0.45f), o + Offset(0f, 2.dp.toPx()), sz, r)
            // cara
            drawRoundRect(
                Brush.verticalGradient(listOf(lerp(color, Color.White, 0.35f), color, lerp(color, Color.Black, 0.18f)),
                    startY = o.y, endY = o.y + s),
                o, sz, r
            )
            // brillo del bisel arriba
            drawRoundRect(
                Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.55f), Color.Transparent), startY = o.y, endY = o.y + s * 0.5f),
                o, sz, r, style = androidx.compose.ui.graphics.drawscope.Stroke(1.4.dp.toPx())
            )
            // reflejo suave en la mitad de arriba
            drawRoundRect(
                Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.Transparent), startY = o.y, endY = o.y + s * 0.55f),
                o + Offset(s * 0.06f, s * 0.05f), androidx.compose.ui.geometry.Size(s * 0.88f, s * 0.45f),
                androidx.compose.ui.geometry.CornerRadius(s * 0.22f)
            )
        }
        val iconSize = size * 0.56f
        // dibujo "estampado": sombra oscura abajo, luz arriba y el ícono blanco
        Icon(icon, null, tint = lerp(color, Color.Black, 0.55f).copy(alpha = 0.8f),
            modifier = Modifier.size(iconSize).offset(y = 1.2.dp))
        Icon(icon, null, tint = Color.White.copy(alpha = 0.35f), modifier = Modifier.size(iconSize).offset(y = (-0.8).dp))
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(iconSize))
    }
}

/** Estrella tipo diamante (4 puntas): marca que la canción tiene carátula animada. */
@Composable
fun DiamondStar(size: Dp = 18.dp, modifier: Modifier = Modifier, backdrop: Boolean = false) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val c = Offset(w / 2f, w / 2f)
        if (backdrop) drawCircle(Color.Black.copy(alpha = 0.45f), w / 2f, c)
        val r = w * (if (backdrop) 0.38f else 0.5f)
        val k = r * 0.22f   // qué tan "metidos" van los lados
        fun star(rad: Float, kk: Float) = Path().apply {
            moveTo(c.x, c.y - rad)
            quadraticBezierTo(c.x + kk, c.y - kk, c.x + rad, c.y)
            quadraticBezierTo(c.x + kk, c.y + kk, c.x, c.y + rad)
            quadraticBezierTo(c.x - kk, c.y + kk, c.x - rad, c.y)
            quadraticBezierTo(c.x - kk, c.y - kk, c.x, c.y - rad)
            close()
        }
        val p = star(r, k)
        drawPath(p, Brush.linearGradient(
            listOf(Color.White, Color(0xFF9FE8FF), Color(0xFFC9A6FF), Color(0xFFFFB0E0)),
            start = Offset(c.x - r, c.y - r), end = Offset(c.x + r, c.y + r)))
        // faceta clara, como diamante
        drawPath(star(r * 0.45f, k * 0.45f), Color.White.copy(alpha = 0.85f))
        drawPath(p, Color.White.copy(alpha = 0.9f), style = Stroke(w * 0.04f))
    }
}

/** Cápsula con el formato y la calidad: «MP3 · 320 kbps». */
@Composable
fun QualityPill(text: String, accent: Color, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(CircleShape)
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.20f), Color.White.copy(alpha = 0.08f))))
            .border(1.dp, Color.White.copy(alpha = 0.28f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(Modifier.size(7.dp)) { drawCircle(accent) }
        Spacer(Modifier.width(6.dp))
        Text(text, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp, maxLines = 1)
    }
}

/** Color que distingue cada formato (para el puntito de las listas). */
fun formatColor(ext: String): Color = when (ext.lowercase()) {
    "mp3" -> Color(0xFF4D9BFF)                       // azul
    "m4a", "aac" -> Color(0xFFFF9F43)                // naranja
    "flac", "wav" -> Color(0xFFFFD84D)               // dorado: sin pérdida
    "ogg", "oga", "opus" -> Color(0xFF2FD3B5)        // turquesa
    "mp4", "m4v", "mkv", "webm", "mov", "avi", "3gp", "ts" -> Color(0xFFFF5FA8)  // rosa: video
    else -> Color(0xFFB0B6C2)
}

/** Si en la portada se ve el video / la carátula animada, o sólo la imagen fija. */
object MotionPref {
    var on by androidx.compose.runtime.mutableStateOf(true)
}

/**
 * Ícono "semi relieve en 2D" (tema Lavanda): cuadrito plano lila con una sombra sólida corrida
 * hacia abajo y un filo de luz arriba; el dibujo va en morado con su sombrita plana.
 */
@Composable
fun SoftIcon(icon: ImageVector, color: Color, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size + 4.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val s = size.toPx()
            val o = Offset((this.size.width - s) / 2f, (this.size.height - s) / 2f - 1.5.dp.toPx())
            val r = androidx.compose.ui.geometry.CornerRadius(s * 0.3f)
            val sz = Size(s, s)
            // sombra sólida (sin difuminar): es lo que da el relieve "de caricatura"
            drawRoundRect(lerp(color, Color.White, 0.55f), o + Offset(0f, 3.dp.toPx()), sz, r)
            // cara plana
            drawRoundRect(lerp(color, Color.White, 0.86f), o, sz, r)
            // filo de luz arriba
            drawRoundRect(Color.White, o + Offset(s * 0.12f, 1.dp.toPx()), Size(s * 0.76f, 1.6.dp.toPx()),
                androidx.compose.ui.geometry.CornerRadius(1.dp.toPx()))
            drawRoundRect(lerp(color, Color.White, 0.65f), o, sz, r, style = Stroke(1.dp.toPx()))
        }
        val iconSize = size * 0.56f
        Box(Modifier.offset(y = (-1.5).dp)) {
            Icon(icon, null, tint = lerp(color, Color.White, 0.55f), modifier = Modifier.size(iconSize).offset(x = 0.dp, y = 1.5.dp))
            Icon(icon, null, tint = color, modifier = Modifier.size(iconSize))
        }
    }
}
