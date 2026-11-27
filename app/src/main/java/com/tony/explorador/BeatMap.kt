package com.tony.explorador

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.nio.ByteOrder
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Mapa del ritmo de cada canción: antes de que suene, se lee el archivo completo y se calcula
 * cuadro por cuadro (cada ~23 ms) cómo suena cada banda y dónde caen los golpes.
 * Así las animaciones siguen la posición exacta de la canción (no un reloj aparte) y las notas
 * de Guitar Nebula pueden salir antes y llegar abajo justo cuando suena el golpe.
 */
object BeatMap {
    const val HOP = 1024
    private const val N = 1024

    class Note(val timeMs: Long, val lane: Int, val strength: Float)

    class Analysis(val frameMs: Double, val bands: Array<FloatArray>, val notes: List<Note>) {
        fun frameAt(posMs: Long): FloatArray? {
            if (bands.isEmpty()) return null
            val i = (posMs / frameMs).toInt()
            return if (i < 0 || i >= bands.size) null else bands[i]
        }
    }

    private val cache = object : LinkedHashMap<String, Analysis>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Analysis>?) = size > 3
    }
    private val pending = HashSet<String>()
    private val failed = HashSet<String>()
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "mapa-ritmo").apply { priority = Thread.MIN_PRIORITY; isDaemon = true } }

    /** El mapa si ya está listo; si no, lo empieza a calcular y regresa null mientras tanto. */
    fun get(path: String?): Analysis? {
        if (path.isNullOrEmpty() || path.startsWith("http")) return null   // la radio en vivo no se analiza
        synchronized(this) {
            cache[path]?.let { return it }
            if (path in pending || path in failed) return null
            pending.add(path)
        }
        worker.execute {
            val a = try { analyze(path) } catch (_: Throwable) { null }
            synchronized(this) {
                pending.remove(path)
                if (a != null) cache[path] = a else failed.add(path)
            }
        }
        return null
    }

    // ───────── análisis ─────────

    private fun analyze(path: String): Analysis? {
        val ex = MediaExtractor()
        try {
            ex.setDataSource(path)
            var track = -1
            for (t in 0 until ex.trackCount) {
                if (ex.getTrackFormat(t).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) { track = t; break }
            }
            if (track < 0) return null
            ex.selectTrack(track)
            val fmt = ex.getTrackFormat(track)
            var sr = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var ch = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val codec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME)!!)
            codec.configure(fmt, null, null, 0)
            codec.start()
            var pcmFloat = false
            val fft = Fft(N)
            val win = FloatArray(N) { (0.5 - 0.5 * cos(2 * PI * it / (N - 1))).toFloat() }
            val buf = FloatArray(N); var filled = 0
            val out = ArrayList<FloatArray>(8000)
            val info = MediaCodec.BufferInfo()
            var inDone = false; var outDone = false
            val started = System.currentTimeMillis()
            while (!outDone) {
                if (System.currentTimeMillis() - started > 60_000) break   // por si un archivo raro tarda demasiado
                if (!inDone) {
                    val ii = codec.dequeueInputBuffer(5000)
                    if (ii >= 0) {
                        val ib = codec.getInputBuffer(ii)!!
                        val n = ex.readSampleData(ib, 0)
                        if (n < 0) { codec.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inDone = true }
                        else { codec.queueInputBuffer(ii, 0, n, ex.sampleTime, 0); ex.advance() }
                    }
                }
                val oi = codec.dequeueOutputBuffer(info, 5000)
                if (oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val of = codec.outputFormat
                    sr = of.getInteger(MediaFormat.KEY_SAMPLE_RATE); ch = of.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
                    pcmFloat = of.containsKey(MediaFormat.KEY_PCM_ENCODING) && of.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                } else if (oi >= 0) {
                    val ob = codec.getOutputBuffer(oi)!!.order(ByteOrder.LITTLE_ENDIAN)
                    ob.position(info.offset); ob.limit(info.offset + info.size)
                    val bps = if (pcmFloat) 4 else 2
                    while (ob.remaining() >= bps * ch) {
                        var s = 0f
                        for (c in 0 until ch) s += if (pcmFloat) ob.float else ob.short / 32768f
                        buf[filled++] = s / ch
                        if (filled == N) {
                            out.add(bandsOf(buf, win, fft, sr))
                            filled = 0
                        }
                    }
                    codec.releaseOutputBuffer(oi, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outDone = true
                }
            }
            codec.stop(); codec.release()
            if (out.size < 20) return null
            val frameMs = HOP * 1000.0 / sr
            val bands = out.toTypedArray()
            return Analysis(frameMs, bands, findNotes(bands, frameMs))
        } finally {
            ex.release()
        }
    }

    private fun bandsOf(samples: FloatArray, win: FloatArray, fft: Fft, sampleRate: Int): FloatArray {
        val re = fft.re; val im = fft.im
        for (i in 0 until N) { re[i] = samples[i] * win[i]; im[i] = 0f }
        fft.run()
        val bands = FloatArray(SpectrumAnalyzer.BANDS)
        val minHz = 40.0
        val maxHz = minOf(16000.0, sampleRate / 2.0)
        for (b in 0 until SpectrumAnalyzer.BANDS) {
            val lo = minHz * (maxHz / minHz).pow(b.toDouble() / SpectrumAnalyzer.BANDS)
            val hi = minHz * (maxHz / minHz).pow((b + 1).toDouble() / SpectrumAnalyzer.BANDS)
            val iLo = (lo * N / sampleRate).toInt().coerceIn(1, N / 2 - 1)
            val iHi = (hi * N / sampleRate).toInt().coerceIn(iLo + 1, N / 2)
            var peak = 0f
            for (i in iLo until iHi) peak = max(peak, sqrt(re[i] * re[i] + im[i] * im[i]))
            val db = 20 * log10(peak / (N / 4f) + 1e-9f)
            bands[b] = ((db + b * 0.9f + 60f) / 60f).coerceIn(0f, 1f)
        }
        return bands
    }

    /** Golpes por carril: donde la energía de su pedazo de sonido brinca por encima de lo normal. */
    private fun findNotes(bands: Array<FloatArray>, frameMs: Double): List<Note> {
        val groups = listOf(0..2, 3..5, 6..8, 9..12, 13 until SpectrumAnalyzer.BANDS)
        val n = bands.size
        val cand = ArrayList<Note>()
        val win = (450 / frameMs).toInt().coerceAtLeast(4)
        val minGap = (190 / frameMs).toInt().coerceAtLeast(2)
        for (lane in 0 until 5) {
            val g = groups[lane]
            val e = FloatArray(n) { k -> var s = 0f; for (b in g) s += bands[k][b]; s / g.count() }
            val flux = FloatArray(n) { k -> if (k < 2) 0f else (e[k] - max(e[k - 1], e[k - 2])).coerceAtLeast(0f) }
            // promedio móvil del flujo para un umbral que se adapta a lo fuerte de la canción
            val pref = DoubleArray(n + 1); for (k in 0 until n) pref[k + 1] = pref[k] + flux[k]
            var last = -minGap
            for (k in 2 until n - 2) {
                val a = (k - win).coerceAtLeast(0); val b = (k + win).coerceAtMost(n)
                val mean = ((pref[b] - pref[a]) / (b - a)).toFloat()
                val thr = mean * 1.9f + 0.035f
                if (flux[k] > thr && flux[k] >= flux[k - 1] && flux[k] >= flux[k + 1] && k - last >= minGap && e[k] > 0.25f) {
                    cand.add(Note((k * frameMs + frameMs / 2).toLong(), lane, flux[k] / (mean + 0.02f)))
                    last = k
                }
            }
        }
        // a lo mucho 2 notas juntas y no más de ~7 por segundo, quedándose con las más fuertes
        cand.sortBy { it.timeMs }
        val res = ArrayList<Note>()
        var i = 0
        while (i < cand.size) {
            var j = i
            while (j < cand.size && cand[j].timeMs - cand[i].timeMs < 70) j++
            val group = cand.subList(i, j).sortedByDescending { it.strength }
            val lastT = res.lastOrNull()?.timeMs ?: -1000
            if (cand[i].timeMs - lastT >= 140) {
                val take = if (group.size >= 2 && group[1].strength > group[0].strength * 0.75f) 2 else 1
                group.take(take).forEach { res.add(Note(cand[i].timeMs, it.lane, it.strength)) }
            }
            i = j
        }
        return res
    }

    private class Fft(val n: Int) {
        val re = FloatArray(n); val im = FloatArray(n)
        fun run() {
            var j = 0
            for (i in 1 until n) {
                var bit = n shr 1
                while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
                j = j xor bit
                if (i < j) { val tr = re[i]; re[i] = re[j]; re[j] = tr; val ti = im[i]; im[i] = im[j]; im[j] = ti }
            }
            var len = 2
            while (len <= n) {
                val ang = -2 * PI / len
                val wr = cos(ang).toFloat(); val wi = sin(ang).toFloat()
                var i = 0
                while (i < n) {
                    var cr = 1f; var ci = 0f
                    for (k in 0 until len / 2) {
                        val a = i + k + len / 2
                        val ar = re[a] * cr - im[a] * ci; val ai = re[a] * ci + im[a] * cr
                        re[a] = re[i + k] - ar; im[a] = im[i + k] - ai
                        re[i + k] += ar; im[i + k] += ai
                        val nr = cr * wr - ci * wi; ci = cr * wi + ci * wr; cr = nr
                    }
                    i += len
                }
                len = len shl 1
            }
        }
    }
}
