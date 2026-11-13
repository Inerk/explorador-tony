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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size

/** Íconos dibujados a mano con el estilo brillante de los 2000. */
enum class XpIconKind { COMPUTER, DOCUMENTS, MUSIC, PICTURES, CAMERA, VIDEOS, DOWNLOADS, DRIVE, SD_CARD, FOLDER, SETTINGS, PLAYER, GALLERY, VIDEO_PLAYER, TRASH }

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
            XpIconKind.PLAYER -> drawPlayer()
            XpIconKind.GALLERY -> drawGallery()
            XpIconKind.VIDEO_PLAYER -> drawVideoPlayer()
            XpIconKind.TRASH -> drawTrash()
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

/** Reproductor: disco brillante detrás y botón de play naranja de cristal al frente. */
private fun DrawScope.drawPlayer() {
    val w = size.width
    val h = size.height
    // disco
    val dc = Offset(w * 0.58f, h * 0.44f)
    val dr = w * 0.36f
    drawCircle(Brush.sweepGradient(listOf(Color(0xFFE6E9F0), Color(0xFFB8E0FF), Color(0xFFF5D0FF), Color(0xFFFFF2B8),
        Color(0xFFC8F5D0), Color(0xFFE6E9F0)), center = dc), dr, dc)
    drawCircle(Color(0xFF9AA3B5), dr, dc, style = Stroke(w * 0.015f))
    drawCircle(Color.White.copy(alpha = 0.7f), dr * 0.32f, dc)
    drawCircle(Color(0xFF9AA3B5), dr * 0.12f, dc)
    // botón de play
    val c = Offset(w * 0.38f, h * 0.58f)
    val r = w * 0.27f
    drawCircle(Brush.radialGradient(listOf(Color(0xFFFFD08A), Color(0xFFFF8A1E), Color(0xFFC24E00)),
        center = Offset(c.x - r * 0.3f, c.y - r * 0.35f), radius = r * 1.5f), r, c)
    drawCircle(Color(0xFF8A3A00), r, c, style = Stroke(w * 0.015f))
    val tri = Path().apply {
        moveTo(c.x - r * 0.28f, c.y - r * 0.42f); lineTo(c.x + r * 0.48f, c.y); lineTo(c.x - r * 0.28f, c.y + r * 0.42f); close()
    }
    drawPath(tri, Color.White)
    drawOval(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.8f), Color.Transparent), startY = c.y - r, endY = c.y),
        Offset(c.x - r * 0.62f, c.y - r * 0.92f), Size(r * 1.24f, r * 0.8f))
}

/** Galería: fotos apiladas un poco giradas. */
private fun DrawScope.drawGallery() {
    val w = size.width
    val h = size.height
    fun photo(angle: Float, dx: Float, dy: Float, sky: Color) {
        rotate(angle, Offset(w * 0.5f + dx, h * 0.5f + dy)) {
            val o = Offset(w * 0.16f + dx, h * 0.2f + dy)
            val s = Size(w * 0.68f, h * 0.56f)
            drawRect(Color.Black.copy(alpha = 0.18f), o + Offset(w * 0.02f, w * 0.03f), s)
            drawRect(Color.White, o, s)
            val io = o + Offset(s.width * 0.07f, s.height * 0.08f)
            val isz = Size(s.width * 0.86f, s.height * 0.72f)
            drawRect(Brush.verticalGradient(listOf(sky, Color(0xFFDDEEFF)), startY = io.y, endY = io.y + isz.height), io, isz)
            val hill = Path().apply {
                moveTo(io.x, io.y + isz.height)
                cubicTo(io.x + isz.width * 0.3f, io.y + isz.height * 0.45f, io.x + isz.width * 0.6f, io.y + isz.height * 0.6f, io.x + isz.width, io.y + isz.height * 0.5f)
                lineTo(io.x + isz.width, io.y + isz.height); close()
            }
            drawPath(hill, Color(0xFF4FAE2C))
            drawCircle(Color(0xFFFFD84D), isz.width * 0.08f, Offset(io.x + isz.width * 0.78f, io.y + isz.height * 0.28f))
            drawRect(Color(0xFFB8C2D4), o, s, style = Stroke(w * 0.012f))
        }
    }
    photo(-14f, -w * 0.04f, h * 0.02f, Color(0xFFFF9A5A))
    photo(8f, w * 0.05f, -h * 0.02f, Color(0xFF9B7BEA))
    photo(-2f, 0f, h * 0.06f, Color(0xFF3D8BFF))
}

/** Videos: claqueta de cine con un botón de play azul brillante. */
private fun DrawScope.drawVideoPlayer() {
    val w = size.width
    val h = size.height
    // cuerpo de la claqueta
    val bo = Offset(w * 0.1f, h * 0.36f)
    val bs = Size(w * 0.72f, h * 0.46f)
    drawRoundRect(Color.Black.copy(alpha = 0.2f), bo + Offset(w * 0.02f, h * 0.03f), bs, CornerRadius(w * 0.05f))
    drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF4A4F5C), Color(0xFF1E2128)), startY = bo.y, endY = bo.y + bs.height),
        bo, bs, CornerRadius(w * 0.05f))
    // líneas de texto de la claqueta
    for (i in 0..2) {
        val y = bo.y + bs.height * (0.3f + i * 0.22f)
        drawLine(Color.White.copy(alpha = 0.55f), Offset(bo.x + bs.width * 0.08f, y), Offset(bo.x + bs.width * (0.42f - i * 0.06f), y), w * 0.022f)
    }
    // barra de arriba con rayas, un poco abierta
    rotate(-14f, Offset(bo.x, bo.y)) {
        val to = Offset(bo.x, bo.y - h * 0.13f)
        val ts = Size(bs.width, h * 0.12f)
        drawRect(Color(0xFF1E2128), to, ts)
        val stripes = 5
        for (i in 0 until stripes) {
            val x0 = to.x + ts.width * i / stripes
            val p = Path().apply {
                moveTo(x0 + ts.width * 0.05f, to.y); lineTo(x0 + ts.width / stripes * 0.75f, to.y)
                lineTo(x0 + ts.width / stripes * 0.45f, to.y + ts.height); lineTo(x0 - ts.width * 0.05f, to.y + ts.height); close()
            }
            drawPath(p, Color.White)
        }
        drawRect(Color(0xFF111111), to, ts, style = Stroke(w * 0.012f))
    }
    // franja fija debajo de la barra
    val so = Offset(bo.x, bo.y)
    val ss = Size(bs.width, h * 0.1f)
    drawRect(Color(0xFF1E2128), so, ss)
    for (i in 0 until 5) {
        val x0 = so.x + ss.width * i / 5
        val p = Path().apply {
            moveTo(x0 + ss.width * 0.06f, so.y); lineTo(x0 + ss.width / 5 * 0.8f, so.y)
            lineTo(x0 + ss.width / 5 * 0.5f, so.y + ss.height); lineTo(x0 - ss.width * 0.04f, so.y + ss.height); close()
        }
        drawPath(p, Color.White)
    }
    // botón de play
    val c = Offset(w * 0.7f, h * 0.68f)
    val r = w * 0.22f
    drawCircle(Brush.radialGradient(listOf(Color(0xFF9FD8FF), Color(0xFF2E86FF), Color(0xFF0B3FA8)),
        center = Offset(c.x - r * 0.3f, c.y - r * 0.35f), radius = r * 1.5f), r, c)
    drawCircle(Color(0xFF0A2F7A), r, c, style = Stroke(w * 0.015f))
    val tri = Path().apply {
        moveTo(c.x - r * 0.28f, c.y - r * 0.42f); lineTo(c.x + r * 0.48f, c.y); lineTo(c.x - r * 0.28f, c.y + r * 0.42f); close()
    }
    drawPath(tri, Color.White)
    drawOval(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.8f), Color.Transparent), startY = c.y - r, endY = c.y),
        Offset(c.x - r * 0.62f, c.y - r * 0.92f), Size(r * 1.24f, r * 0.8f))
}

/** Papelera: bote translúcido con tapa y papeles adentro. */
private fun DrawScope.drawTrash() {
    val w = size.width
    val h = size.height
    // papeles arrugados asomando
    drawCircle(Color(0xFFFDFDFD), w * 0.13f, Offset(w * 0.4f, h * 0.3f))
    drawCircle(Color(0xFFE8ECF5), w * 0.11f, Offset(w * 0.58f, h * 0.27f))
    drawCircle(Color(0xFFD6DCEB), w * 0.09f, Offset(w * 0.5f, h * 0.36f))
    // cuerpo del bote (más angosto abajo)
    val body = Path().apply {
        moveTo(w * 0.2f, h * 0.34f); lineTo(w * 0.8f, h * 0.34f)
        lineTo(w * 0.7f, h * 0.88f); lineTo(w * 0.3f, h * 0.88f); close()
    }
    drawPath(body, Brush.horizontalGradient(listOf(Color(0xFF8FA2C8), Color(0xFFDDE6FA), Color(0xFF9FB0D6), Color(0xFF6F82AD)),
        startX = w * 0.2f, endX = w * 0.8f))
    drawPath(body, Color(0xFF4A5A85), style = Stroke(w * 0.02f))
    // costillas
    for (i in 0..3) {
        val t = 0.3f + i * 0.133f
        drawLine(Color(0xFF4A5A85).copy(alpha = 0.55f), Offset(w * (0.2f + t * 0.6f) + (0.5f - t) * w * 0.05f, h * 0.4f),
            Offset(w * (0.3f + t * 0.4f), h * 0.84f), w * 0.02f)
    }
    // aro de arriba
    drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFF2F6FF), Color(0xFF8FA2C8)), startY = h * 0.3f, endY = h * 0.39f),
        Offset(w * 0.16f, h * 0.3f), Size(w * 0.68f, h * 0.085f), CornerRadius(w * 0.04f))
    drawRoundRect(Color(0xFF4A5A85), Offset(w * 0.16f, h * 0.3f), Size(w * 0.68f, h * 0.085f), CornerRadius(w * 0.04f), style = Stroke(w * 0.015f))
    // flechitas de reciclaje (un triángulo verde)
    val tri = Path().apply { moveTo(w * 0.5f, h * 0.52f); lineTo(w * 0.59f, h * 0.68f); lineTo(w * 0.41f, h * 0.68f); close() }
    drawPath(tri, Color(0xFF3FAE5A), style = Stroke(w * 0.035f))
}
