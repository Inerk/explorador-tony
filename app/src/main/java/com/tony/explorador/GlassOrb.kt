package com.tony.explorador

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
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
