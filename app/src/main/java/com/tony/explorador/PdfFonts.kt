package com.tony.explorador

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface

/*
 * Letras para editar texto de PDF. Son equivalentes libres de las más comunes en documentos
 * (mismo ancho de cada letra): Arial, Calibri, Times New Roman, Cambria y Courier.
 * Se usan igual en pantalla y dentro del PDF guardado, para que se vea idéntico.
 */
object PdfFonts {
    data class Family(val id: Int, val name: String, val file: String)
    val all = listOf(
        Family(0, "Arial", "sans"), Family(1, "Calibri", "calibri"), Family(2, "Times", "serif"),
        Family(3, "Cambria", "cambria"), Family(4, "Courier", "mono")
    )
    fun asset(id: Int, bold: Boolean) = "fuentes/" + (all.getOrNull(id) ?: all[0]).file + (if (bold) "_b" else "") + ".ttf"

    private val cache = HashMap<String, Typeface>()
    fun typeface(c: Context, id: Int, bold: Boolean): Typeface = synchronized(cache) {
        val key = asset(id, bold)
        cache.getOrPut(key) {
            runCatching { Typeface.createFromAsset(c.assets, key) }.getOrElse {
                Typeface.create(if (id == 2 || id == 3) Typeface.SERIF else if (id == 4) Typeface.MONOSPACE else Typeface.SANS_SERIF,
                    if (bold) Typeface.BOLD else Typeface.NORMAL)
            }
        }
    }

    /**
     * Adivina la letra de un renglón: dibuja el mismo texto con cada letra (normal y negrita)
     * y se queda con la que más se parece a la imagen original (forma y ancho).
     */
    fun detect(c: Context, bmp: Bitmap, rc: Rect, text: String, bg: Int): Pair<Int, Boolean> {
        if (text.isBlank()) return 0 to false
        // un poco de margen: el recuadro del OCR a veces corta las orillas de las letras
        val pad = rc.height() / 4
        val r = Rect((rc.left - pad).coerceAtLeast(0), (rc.top - pad).coerceAtLeast(0), (rc.right + pad).coerceAtMost(bmp.width), (rc.bottom + pad).coerceAtMost(bmp.height))
        if (r.width() < 4 || r.height() < 4) return 0 to false
        val hh = 40
        val ww = (r.width() * hh / r.height()).coerceIn(8, 1600)
        val crop = Bitmap.createScaledBitmap(Bitmap.createBitmap(bmp, r.left, r.top, r.width(), r.height()), ww, hh, true)
        fun far(p: Int) = kotlin.math.abs(((p shr 16) and 255) - ((bg shr 16) and 255)) +
            kotlin.math.abs(((p shr 8) and 255) - ((bg shr 8) and 255)) + kotlin.math.abs((p and 255) - (bg and 255)) > 300
        val px = IntArray(ww * hh); crop.getPixels(px, 0, ww, 0, 0, ww, hh)
        var x0 = ww; var y0 = hh; var x1 = -1; var y1 = -1
        for (y in 0 until hh) for (x in 0 until ww) if (far(px[y * ww + x])) { if (x < x0) x0 = x; if (x > x1) x1 = x; if (y < y0) y0 = y; if (y > y1) y1 = y }
        if (x1 < x0 || y1 < y0) return 0 to false
        val bw = x1 - x0 + 1; val bh = y1 - y0 + 1
        val orig = BooleanArray(bw * bh) { i -> far(px[(y0 + i / bw) * ww + x0 + i % bw]) }
        val origInk = orig.count { it }.coerceAtLeast(1)
        val cand = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val cv = Canvas(cand)
        val cpx = IntArray(bw * bh)
        var best = 0 to false; var bestScore = Float.MAX_VALUE
        for (f in all) for (bold in listOf(false, true)) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = typeface(c, f.id, bold); textSize = 100f; color = 0xFF000000.toInt() }
            val tb = Rect(); paint.getTextBounds(text, 0, text.length, tb)
            if (tb.width() <= 0 || tb.height() <= 0) continue
            val sx = bw.toFloat() / tb.width(); val sy = bh.toFloat() / tb.height()
            cand.eraseColor(0)
            cv.save(); cv.scale(sx, sy); cv.translate(-tb.left.toFloat(), -tb.top.toFloat()); cv.drawText(text, 0f, 0f, paint); cv.restore()
            cand.getPixels(cpx, 0, bw, 0, 0, bw, bh)
            var inter = 0; var uni = 0; var ink = 0
            for (i in 0 until bw * bh) {
                val a = orig[i]; val b = ((cpx[i] ushr 24) and 255) > 110
                if (b) ink++
                if (a && b) inter++
                if (a || b) uni++
            }
            // parecido de forma + qué tanto hubo que estirar a lo ancho + grosor del trazo
            val shape = 1f - inter.toFloat() / uni.coerceAtLeast(1)
            val stretch = kotlin.math.abs(kotlin.math.ln(sx / sy))
            val weight = kotlin.math.abs(kotlin.math.ln(ink.coerceAtLeast(1).toFloat() / origInk))
            val score = shape + 1.2f * stretch + 0.35f * weight
            android.util.Log.d("PdfFonts", "'$text' ${f.name}${if (bold) " bold" else ""} forma=$shape ancho=$stretch grosor=$weight total=$score")
            if (score < bestScore) { bestScore = score; best = f.id to bold }
        }
        return best
    }
}
