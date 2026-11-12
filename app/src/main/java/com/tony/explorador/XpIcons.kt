package com.tony.explorador

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size

/** Íconos dibujados a mano con el estilo brillante de los 2000. */
enum class XpIconKind { COMPUTER, DOCUMENTS, MUSIC, PICTURES, CAMERA, VIDEOS, DOWNLOADS, DRIVE, SD_CARD, FOLDER, SETTINGS }

@Composable
fun XpIcon(kind: XpIconKind, size: Dp = 48.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        // sombra en el piso para que se vea con volumen
        drawOval(
            Brush.radialGradient(
                listOf(Color.Black.copy(alpha = 0.38f), Color.Transparent),
                center = Offset(this.size.width * 0.54f, this.size.height * 0.92f), radius = this.size.width * 0.46f
            ),
            topLeft = Offset(this.size.width * 0.08f, this.size.height * 0.84f),
            size = Size(this.size.width * 0.92f, this.size.height * 0.16f)
        )
        when (kind) {
            XpIconKind.COMPUTER -> drawComputer()
            XpIconKind.DOCUMENTS -> { drawFolder(); drawPaperEmblem() }
            XpIconKind.MUSIC -> { drawFolder(); drawNoteEmblem() }
            XpIconKind.PICTURES -> { drawFolder(); drawPhotoEmblem() }
            XpIconKind.CAMERA -> drawCamera()
            XpIconKind.VIDEOS -> { drawFolder(); drawFilmEmblem() }
            XpIconKind.DOWNLOADS -> { drawFolder(); drawArrowEmblem() }
            XpIconKind.DRIVE -> drawDrive(Color(0xFFDADDE3), Color(0xFF8F96A3))
            XpIconKind.SD_CARD -> drawSdCard()
            XpIconKind.FOLDER -> drawFolder()
            XpIconKind.SETTINGS -> drawControlPanel()
        }
    }
}

private val Outline = Color(0xFF6B4E12)

private fun DrawScope.drawFolder() {
    val w = size.width
    val h = size.height
    // parte de atrás con pestaña
    val back = Path().apply {
        moveTo(w * 0.06f, h * 0.22f)
        lineTo(w * 0.38f, h * 0.22f)
        lineTo(w * 0.45f, h * 0.30f)
        lineTo(w * 0.94f, h * 0.30f)
        lineTo(w * 0.94f, h * 0.86f)
        lineTo(w * 0.06f, h * 0.86f)
        close()
    }
    drawPath(back, Brush.verticalGradient(listOf(Color(0xFFF7D46B), Color(0xFFD9A630)), startY = h * 0.2f, endY = h * 0.86f))
    drawPath(back, Outline.copy(alpha = 0.6f), style = Stroke(w * 0.02f))
    // hoja blanca asomándose
    drawRect(Color.White, Offset(w * 0.14f, h * 0.30f), Size(w * 0.72f, h * 0.2f))
    // frente inclinado
    val front = Path().apply {
        moveTo(w * 0.04f, h * 0.42f)
        lineTo(w * 0.96f, h * 0.42f)
        lineTo(w * 0.90f, h * 0.88f)
        lineTo(w * 0.10f, h * 0.88f)
        close()
    }
    drawPath(front, Brush.verticalGradient(listOf(Color(0xFFFFEFA6), Color(0xFFFFD45A), Color(0xFFE9B23A)), startY = h * 0.42f, endY = h * 0.88f))
    drawPath(front, Outline.copy(alpha = 0.7f), style = Stroke(w * 0.02f))
    // brillo
    drawLine(Color.White.copy(alpha = 0.7f), Offset(w * 0.08f, h * 0.45f), Offset(w * 0.92f, h * 0.45f), strokeWidth = w * 0.02f)
}

private fun DrawScope.emblemBox(): Pair<Offset, Float> {
    val w = size.width
    return Offset(w * 0.52f, size.height * 0.50f) to w * 0.42f
}

private fun DrawScope.drawPaperEmblem() {
    val w = size.width
    val h = size.height
    drawRect(Color.White, Offset(w * 0.30f, h * 0.10f), Size(w * 0.40f, h * 0.38f))
    drawRect(Color(0xFF7F9DB9), Offset(w * 0.30f, h * 0.10f), Size(w * 0.40f, h * 0.38f), style = Stroke(w * 0.015f))
    for (i in 0..3) {
        drawLine(Color(0xFF6D8FCB), Offset(w * 0.35f, h * (0.17f + i * 0.07f)), Offset(w * 0.65f, h * (0.17f + i * 0.07f)), strokeWidth = w * 0.02f)
    }
}

private fun DrawScope.drawNoteEmblem() {
    val (o, s) = emblemBox()
    drawCircle(Brush.radialGradient(listOf(Color(0xFF7DB8FF), Color(0xFF1E5CC8)), center = Offset(o.x + s / 2, o.y + s / 2), radius = s / 2),
        s / 2, Offset(o.x + s / 2, o.y + s / 2))
    drawCircle(Color.White, s / 2, Offset(o.x + s / 2, o.y + s / 2), style = Stroke(s * 0.06f))
    val cx = o.x + s * 0.42f
    val cy = o.y + s * 0.68f
    drawOval(Color.White, Offset(cx - s * 0.13f, cy - s * 0.09f), Size(s * 0.24f, s * 0.18f))
    drawLine(Color.White, Offset(cx + s * 0.1f, cy), Offset(cx + s * 0.1f, o.y + s * 0.2f), strokeWidth = s * 0.07f)
    drawLine(Color.White, Offset(cx + s * 0.1f, o.y + s * 0.2f), Offset(cx + s * 0.32f, o.y + s * 0.32f), strokeWidth = s * 0.07f, cap = StrokeCap.Round)
}

private fun DrawScope.drawPhotoEmblem() {
    val (o, s) = emblemBox()
    drawRect(Color.White, o, Size(s, s * 0.82f))
    val inner = Offset(o.x + s * 0.08f, o.y + s * 0.08f)
    val isz = Size(s * 0.84f, s * 0.66f)
    drawRect(Brush.verticalGradient(listOf(Color(0xFF4D93F0), Color(0xFFBFE0FF)), startY = inner.y, endY = inner.y + isz.height), inner, isz)
    val hill = Path().apply {
        moveTo(inner.x, inner.y + isz.height)
        cubicTo(inner.x + isz.width * 0.3f, inner.y + isz.height * 0.4f, inner.x + isz.width * 0.7f, inner.y + isz.height * 0.6f, inner.x + isz.width, inner.y + isz.height * 0.55f)
        lineTo(inner.x + isz.width, inner.y + isz.height)
        close()
    }
    drawPath(hill, Color(0xFF4FAE2C))
    drawCircle(Color(0xFFFFE15A), s * 0.08f, Offset(inner.x + isz.width * 0.75f, inner.y + isz.height * 0.28f))
    drawRect(Color(0xFF7F9DB9), o, Size(s, s * 0.82f), style = Stroke(s * 0.04f))
}

private fun DrawScope.drawFilmEmblem() {
    val (o, s) = emblemBox()
    drawRoundRect(Color(0xFF2B2B33), o, Size(s, s * 0.8f), CornerRadius(s * 0.06f))
    val hole = s * 0.09f
    for (i in 0..3) {
        val x = o.x + s * 0.08f + i * s * 0.24f
        drawRect(Color.White, Offset(x, o.y + s * 0.05f), Size(hole, hole * 0.8f))
        drawRect(Color.White, Offset(x, o.y + s * 0.8f - s * 0.05f - hole * 0.8f), Size(hole, hole * 0.8f))
    }
    drawRect(Brush.verticalGradient(listOf(Color(0xFF9B6BE0), Color(0xFF4A2C9E))), Offset(o.x + s * 0.08f, o.y + s * 0.2f), Size(s * 0.84f, s * 0.4f))
    val play = Path().apply {
        moveTo(o.x + s * 0.42f, o.y + s * 0.27f); lineTo(o.x + s * 0.62f, o.y + s * 0.4f); lineTo(o.x + s * 0.42f, o.y + s * 0.53f); close()
    }
    drawPath(play, Color.White)
}

private fun DrawScope.drawArrowEmblem() {
    val (o, s) = emblemBox()
    val c = Offset(o.x + s / 2, o.y + s / 2)
    drawCircle(Brush.radialGradient(listOf(Color(0xFF9BE37A), Color(0xFF2E8B1E)), center = c, radius = s / 2), s / 2, c)
    drawCircle(Color.White, s / 2, c, style = Stroke(s * 0.06f))
    val arrow = Path().apply {
        moveTo(c.x - s * 0.1f, c.y - s * 0.28f)
        lineTo(c.x + s * 0.1f, c.y - s * 0.28f)
        lineTo(c.x + s * 0.1f, c.y)
        lineTo(c.x + s * 0.24f, c.y)
        lineTo(c.x, c.y + s * 0.28f)
        lineTo(c.x - s * 0.24f, c.y)
        lineTo(c.x - s * 0.1f, c.y)
        close()
    }
    drawPath(arrow, Color.White)
}

private fun DrawScope.drawComputer() {
    val w = size.width
    val h = size.height
    // monitor
    drawRoundRect(
        Brush.verticalGradient(listOf(Color(0xFFF3F0E4), Color(0xFFCFC8B0))),
        Offset(w * 0.08f, h * 0.06f), Size(w * 0.84f, h * 0.62f), CornerRadius(w * 0.06f)
    )
    drawRoundRect(Color(0xFF8C8570), Offset(w * 0.08f, h * 0.06f), Size(w * 0.84f, h * 0.62f), CornerRadius(w * 0.06f), style = Stroke(w * 0.02f))
    // pantalla con paisaje
    val so = Offset(w * 0.16f, h * 0.13f)
    val ss = Size(w * 0.68f, h * 0.45f)
    drawRect(Brush.verticalGradient(listOf(Color(0xFF2E6FD8), Color(0xFF9CC9F5)), startY = so.y, endY = so.y + ss.height), so, ss)
    val hill = Path().apply {
        moveTo(so.x, so.y + ss.height)
        cubicTo(so.x + ss.width * 0.3f, so.y + ss.height * 0.5f, so.x + ss.width * 0.7f, so.y + ss.height * 0.65f, so.x + ss.width, so.y + ss.height * 0.6f)
        lineTo(so.x + ss.width, so.y + ss.height); close()
    }
    drawPath(hill, Color(0xFF5DB83A))
    drawRect(Color(0xFF3B3B3B), so, ss, style = Stroke(w * 0.015f))
    // base
    val base = Path().apply {
        moveTo(w * 0.38f, h * 0.68f); lineTo(w * 0.62f, h * 0.68f); lineTo(w * 0.70f, h * 0.80f); lineTo(w * 0.30f, h * 0.80f); close()
    }
    drawPath(base, Brush.verticalGradient(listOf(Color(0xFFDCD5BE), Color(0xFFB4AC92))))
    drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFE9E4D2), Color(0xFFB4AC92))),
        Offset(w * 0.18f, h * 0.80f), Size(w * 0.64f, h * 0.12f), CornerRadius(w * 0.03f))
    drawCircle(Color(0xFF3CC43C), w * 0.025f, Offset(w * 0.74f, h * 0.86f))
}

private fun DrawScope.drawDrive(top: Color, bottom: Color) {
    val w = size.width
    val h = size.height
    drawRoundRect(Brush.verticalGradient(listOf(top, bottom)), Offset(w * 0.06f, h * 0.32f), Size(w * 0.88f, h * 0.40f), CornerRadius(w * 0.06f))
    drawRoundRect(Color(0xFF5E6470), Offset(w * 0.06f, h * 0.32f), Size(w * 0.88f, h * 0.40f), CornerRadius(w * 0.06f), style = Stroke(w * 0.02f))
    drawLine(Color.White.copy(alpha = 0.8f), Offset(w * 0.1f, h * 0.36f), Offset(w * 0.9f, h * 0.36f), strokeWidth = w * 0.02f)
    drawRect(Color(0xFF4A505C), Offset(w * 0.14f, h * 0.55f), Size(w * 0.5f, h * 0.05f))
    drawCircle(Color(0xFF3CE03C), w * 0.04f, Offset(w * 0.80f, h * 0.58f))
}

private fun DrawScope.drawSdCard() {
    val w = size.width
    val h = size.height
    val card = Path().apply {
        moveTo(w * 0.24f, h * 0.10f); lineTo(w * 0.66f, h * 0.10f); lineTo(w * 0.78f, h * 0.22f)
        lineTo(w * 0.78f, h * 0.90f); lineTo(w * 0.24f, h * 0.90f); close()
    }
    drawPath(card, Brush.verticalGradient(listOf(Color(0xFF4A5568), Color(0xFF1E2430))))
    drawPath(card, Color(0xFF0E1218), style = Stroke(w * 0.02f))
    for (i in 0..3) drawRect(Color(0xFFE8C34A), Offset(w * (0.30f + i * 0.11f), h * 0.14f), Size(w * 0.06f, h * 0.14f))
    drawRect(Color.White, Offset(w * 0.30f, h * 0.48f), Size(w * 0.42f, h * 0.24f))
    drawRect(Color(0xFF1E5CC8), Offset(w * 0.30f, h * 0.48f), Size(w * 0.42f, h * 0.08f))
}

private fun DrawScope.drawCamera() {
    val w = size.width
    val h = size.height
    drawRoundRect(Color(0xFF3A3F4A), Offset(w * 0.30f, h * 0.20f), Size(w * 0.24f, h * 0.12f), CornerRadius(w * 0.03f))
    drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF6A7180), Color(0xFF2A2F38))),
        Offset(w * 0.08f, h * 0.28f), Size(w * 0.84f, h * 0.56f), CornerRadius(w * 0.08f))
    drawLine(Color.White.copy(alpha = 0.5f), Offset(w * 0.14f, h * 0.33f), Offset(w * 0.86f, h * 0.33f), strokeWidth = w * 0.02f)
    val c = Offset(w * 0.5f, h * 0.57f)
    drawCircle(Color(0xFFC9CED6), w * 0.21f, c)
    drawCircle(Brush.radialGradient(listOf(Color(0xFF5FA8FF), Color(0xFF0A2A5E)), center = c, radius = w * 0.16f), w * 0.16f, c)
    drawCircle(Color.White.copy(alpha = 0.7f), w * 0.04f, Offset(c.x - w * 0.06f, c.y - w * 0.06f))
    drawCircle(Color(0xFFFFD84D), w * 0.035f, Offset(w * 0.80f, h * 0.38f))
}

private fun DrawScope.drawControlPanel() {
    val w = size.width
    val h = size.height
    // ventanita con controles deslizantes de colores
    drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFF4F6FB), Color(0xFFC9D3E6))),
        Offset(w * 0.08f, h * 0.12f), Size(w * 0.84f, h * 0.76f), CornerRadius(w * 0.06f))
    drawRoundRect(Color(0xFF5E7AA8), Offset(w * 0.08f, h * 0.12f), Size(w * 0.84f, h * 0.76f), CornerRadius(w * 0.06f), style = Stroke(w * 0.02f))
    drawRect(Brush.horizontalGradient(listOf(Color(0xFF3D8BFF), Color(0xFF0042B8))), Offset(w * 0.08f, h * 0.12f), Size(w * 0.84f, h * 0.12f))
    val colors = listOf(Color(0xFFE53935), Color(0xFF43A047), Color(0xFF1E88E5))
    val knobs = listOf(0.62f, 0.38f, 0.72f)
    colors.forEachIndexed { i, c ->
        val y = h * (0.38f + i * 0.16f)
        drawLine(Color(0xFF8A96AC), Offset(w * 0.18f, y), Offset(w * 0.82f, y), strokeWidth = w * 0.03f, cap = StrokeCap.Round)
        drawLine(c, Offset(w * 0.18f, y), Offset(w * (0.18f + 0.64f * knobs[i]), y), strokeWidth = w * 0.03f, cap = StrokeCap.Round)
        drawCircle(Color.White, w * 0.055f, Offset(w * (0.18f + 0.64f * knobs[i]), y))
        drawCircle(c, w * 0.055f, Offset(w * (0.18f + 0.64f * knobs[i]), y), style = Stroke(w * 0.02f))
    }
}
