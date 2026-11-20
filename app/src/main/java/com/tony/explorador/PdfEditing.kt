package com.tony.explorador

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.geometry.Offset
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.util.Matrix
import java.io.File

/*
 * Motor del editor de PDF (con PdfBox): arma un PDF nuevo con las páginas en el orden elegido,
 * con su giro, y le pinta encima lo que el usuario dibujó, escribió o firmó.
 * Las marcas se guardan en coordenadas de 0 a 1 sobre la página tal como se ve (con su giro original).
 */

/** Una marca sobre una página. */
sealed class PdfMark {
    /** Trazo de pluma o resaltador. [width] es fracción del ancho de la página. */
    data class Stroke(val points: List<Offset>, val color: Int, val width: Float, val alpha: Float) : PdfMark()
    /** Texto; [pos] es la esquina de arriba a la izquierda; [size] es fracción del ancho. */
    data class Text(val pos: Offset, val text: String, val color: Int, val size: Float) : PdfMark()
    /** Firma o imagen; [pos] es el centro; [width] fracción del ancho; [ratio] alto/ancho de la imagen. */
    data class Image(val pos: Offset, val path: String, val width: Float, val ratio: Float) : PdfMark()
    /**
     * Texto corregido con OCR: tapa el renglón original ([l],[t],[r],[b] de 0 a 1) con el color del fondo
     * y escribe encima el texto nuevo. [size] es el alto de letra como fracción del ancho de la página.
     */
    data class Replace(val l: Float, val t: Float, val r: Float, val b: Float, val text: String, val color: Int,
                       val bg: Int, val size: Float, val bold: Boolean, val original: String) : PdfMark()
}

/** Una página del documento final: de qué archivo sale (null = en blanco), cuál es y cuánto se gira de más. */
data class PdfPageRef(val id: Long, val file: File?, val index: Int, val extraRotation: Int = 0, val blankRatio: Float = 1.4142f)

object PdfEditing {
    private var ready = false
    fun init(c: Context) { if (!ready) { PDFBoxResourceLoader.init(c.applicationContext); ready = true } }

    /** Crea el PDF final en [out]. */
    fun save(c: Context, pages: List<PdfPageRef>, marks: Map<Long, List<PdfMark>>, out: File) {
        init(c)
        val sources = HashMap<File, PDDocument>()
        val doc = PDDocument()
        try {
            for (ref in pages) {
                val page: PDPage = if (ref.file == null) {
                    PDPage(PDRectangle(PDRectangle.A4.width, PDRectangle.A4.width * ref.blankRatio)).also { doc.addPage(it) }
                } else {
                    val src = sources.getOrPut(ref.file) { PDDocument.load(ref.file) }
                    doc.importPage(src.getPage(ref.index))
                }
                val baseRot = ((page.rotation % 360) + 360) % 360
                marks[ref.id]?.takeIf { it.isNotEmpty() }?.let { drawMarks(doc, page, baseRot, it) }
                page.rotation = (baseRot + ref.extraRotation) % 360
            }
            val tmp = File(out.parentFile, out.name + ".guardando")
            doc.save(tmp)
            doc.close()
            sources.values.forEach { runCatching { it.close() } }
            if (out.exists()) out.delete()
            if (!tmp.renameTo(out)) { tmp.copyTo(out, overwrite = true); tmp.delete() }
        } finally {
            runCatching { doc.close() }
            sources.values.forEach { runCatching { it.close() } }
        }
    }

    /** Pasa un punto (0..1 sobre la página como se ve con su giro [rot]) a coordenadas del PDF. */
    private fun toPdf(p: Offset, box: PDRectangle, rot: Int): Pair<Float, Float> {
        val x0 = box.lowerLeftX; val y0 = box.lowerLeftY; val w = box.width; val h = box.height
        val u = p.x; val v = p.y
        return when (rot) {
            90 -> Pair(x0 + v * w, y0 + u * h)
            180 -> Pair(x0 + (1 - u) * w, y0 + v * h)
            270 -> Pair(x0 + (1 - v) * w, y0 + (1 - u) * h)
            else -> Pair(x0 + u * w, y0 + (1 - v) * h)
        }
    }

    private fun drawMarks(doc: PDDocument, page: PDPage, rot: Int, marks: List<PdfMark>) {
        val box = page.cropBox
        val viewW = if (rot == 90 || rot == 270) box.height else box.width   // ancho de la página como se ve
        PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
            for (m in marks) when (m) {
                is PdfMark.Stroke -> {
                    if (m.points.isEmpty()) continue
                    cs.saveGraphicsState()
                    if (m.alpha < 1f) cs.setGraphicsStateParameters(PDExtendedGraphicsState().apply { strokingAlphaConstant = m.alpha; nonStrokingAlphaConstant = m.alpha })
                    cs.setStrokingColor(r(m.color), g(m.color), b(m.color))
                    cs.setLineWidth(m.width * viewW)
                    cs.setLineCapStyle(1); cs.setLineJoinStyle(1)
                    val first = toPdf(m.points.first(), box, rot)
                    cs.moveTo(first.first, first.second)
                    if (m.points.size == 1) cs.lineTo(first.first + 0.1f, first.second)
                    m.points.drop(1).forEach { val q = toPdf(it, box, rot); cs.lineTo(q.first, q.second) }
                    cs.stroke()
                    cs.restoreGraphicsState()
                }
                is PdfMark.Text -> {
                    val font = PDType1Font.HELVETICA_BOLD
                    val size = m.size * viewW
                    m.text.split("\n").forEachIndexed { line, txt ->
                        // la línea baja desde la esquina; se mueve la base del texto hacia abajo
                        val p = toPdf(Offset(m.pos.x, m.pos.y + (size * 1.2f * line + size * 0.85f) / viewW / pageAspect(box, rot)), box, rot)
                        cs.beginText()
                        cs.setFont(font, size)
                        cs.setNonStrokingColor(r(m.color), g(m.color), b(m.color))
                        cs.setTextMatrix(Matrix.getRotateInstance(Math.toRadians(rot.toDouble()), p.first, p.second))
                        cs.showText(safeText(txt))
                        cs.endText()
                    }
                }
                is PdfMark.Replace -> {
                    val aspect = pageAspect(box, rot)
                    val font = if (m.bold) PDType1Font.HELVETICA_BOLD else PDType1Font.HELVETICA
                    val txt = safeText(m.text.replace("\n", " "))
                    var size = m.size * viewW
                    val boxW = (m.r - m.l) * viewW
                    var textW = font.getStringWidth(txt) / 1000f * size
                    // si el texto nuevo es más largo, se estira el parche hasta la orilla y, si no cabe, se achica la letra
                    val roomW = (1f - m.l) * viewW * 0.98f
                    if (textW > roomW) { size *= roomW / textW; textW = roomW }
                    val right = (m.l + maxOf(boxW, textW) / viewW + 0.004f).coerceAtMost(1f)
                    val padY = 0.15f * (m.b - m.t)
                    val corners = listOf(Offset(m.l - 0.003f, m.t - padY), Offset(right, m.t - padY), Offset(right, m.b + padY), Offset(m.l - 0.003f, m.b + padY))
                        .map { toPdf(Offset(it.x.coerceIn(0f, 1f), it.y.coerceIn(0f, 1f)), box, rot) }
                    cs.saveGraphicsState()
                    cs.setNonStrokingColor(r(m.bg), g(m.bg), b(m.bg))
                    cs.moveTo(corners[0].first, corners[0].second)
                    corners.drop(1).forEach { cs.lineTo(it.first, it.second) }
                    cs.closePath(); cs.fill()
                    cs.restoreGraphicsState()
                    if (txt.isNotBlank()) {
                        // la base del texto queda cerca de la orilla de abajo del renglón
                        val baseV = m.b - (m.b - m.t - size / viewW / aspect) / 2f - 0.2f * size / viewW / aspect
                        val p = toPdf(Offset(m.l, baseV), box, rot)
                        cs.beginText()
                        cs.setFont(font, size)
                        cs.setNonStrokingColor(r(m.color), g(m.color), b(m.color))
                        cs.setTextMatrix(Matrix.getRotateInstance(Math.toRadians(rot.toDouble()), p.first, p.second))
                        cs.showText(txt)
                        cs.endText()
                    }
                }
                is PdfMark.Image -> {
                    val bmp: Bitmap = BitmapFactory.decodeFile(m.path) ?: continue
                    val img = LosslessFactory.createFromImage(doc, bmp)
                    val wPdf = m.width * viewW; val hPdf = wPdf * m.ratio
                    val c = toPdf(m.pos, box, rot)
                    // la imagen se dibuja derecha según cómo se ve la página
                    val mtx = Matrix.getRotateInstance(Math.toRadians(rot.toDouble()), c.first, c.second)
                    mtx.translate(-wPdf / 2, -hPdf / 2)
                    mtx.scale(wPdf, hPdf)
                    cs.drawImage(img, mtx)
                }
            }
        }
    }

    /** alto/ancho de la página como se ve. */
    private fun pageAspect(box: PDRectangle, rot: Int) =
        if (rot == 90 || rot == 270) box.width / box.height else box.height / box.width

    /** Helvetica solo trae letras occidentales (incluye acentos y ñ): lo demás se cambia por «?». */
    private fun safeText(t: String) = t.map { ch -> if (ch.code in 32..126 || ch.code in 160..255) ch else '?' }.joinToString("")

    private fun r(c: Int) = ((c shr 16) and 0xFF) / 255f
    private fun g(c: Int) = ((c shr 8) and 0xFF) / 255f
    private fun b(c: Int) = (c and 0xFF) / 255f
}
