package com.tony.explorador

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Recibe una copia del audio que está sonando (antes de llegar a la bocina),
 * calcula sus frecuencias (FFT) y las deja listas para el ecualizador.
 * No necesita permiso de micrófono porque el audio viene directo del reproductor.
 */
@UnstableApi
object SpectrumAnalyzer : TeeAudioProcessor.AudioBufferSink {
    const val BANDS = 18
    private const val N = 1024
    /** El audio se procesa un poquito antes de que suene; este retraso lo empata con lo que escuchas. */
    private const val DELAY_NANOS = 180_000_000L

    private var sampleRate = 44100
    private var channels = 2
    private var encoding = C.ENCODING_PCM_16BIT
    private val window = FloatArray(N) { (0.5 - 0.5 * cos(2 * PI * it / (N - 1))).toFloat() }
    private val samples = FloatArray(N)
    private var filled = 0
    private val re = FloatArray(N)
    private val im = FloatArray(N)

    private class Frame(val time: Long, val bands: FloatArray)
    private val frames = ArrayDeque<Frame>()
    private val lock = Any()

    override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) {
        sampleRate = sampleRateHz
        channels = channelCount.coerceAtLeast(1)
        this.encoding = encoding
        filled = 0
        synchronized(lock) { frames.clear() }
    }

    override fun handleBuffer(buffer: ByteBuffer) {
        val buf = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        val bytesPerSample = when (encoding) {
            C.ENCODING_PCM_16BIT -> 2
            C.ENCODING_PCM_FLOAT -> 4
            else -> return
        }
        val frameBytes = bytesPerSample * channels
        while (buf.remaining() >= frameBytes) {
            // mezclar los canales en mono
            var sum = 0f
            for (c in 0 until channels) {
                sum += if (bytesPerSample == 2) buf.short / 32768f else buf.float
            }
            samples[filled++] = sum / channels
            if (filled == N) {
                analyze()
                // ventanas encimadas a la mitad para que se vea más fluido
                System.arraycopy(samples, N / 2, samples, 0, N / 2)
                filled = N / 2
            }
        }
    }

    private fun analyze() {
        for (i in 0 until N) { re[i] = samples[i] * window[i]; im[i] = 0f }
        fft(re, im)
        val bands = FloatArray(BANDS)
        val minHz = 40.0
        val maxHz = minOf(16000.0, sampleRate / 2.0)
        for (b in 0 until BANDS) {
            // bandas en escala logarítmica, como un ecualizador real
            val lo = minHz * (maxHz / minHz).pow(b.toDouble() / BANDS)
            val hi = minHz * (maxHz / minHz).pow((b + 1).toDouble() / BANDS)
            val iLo = (lo * N / sampleRate).toInt().coerceIn(1, N / 2 - 1)
            val iHi = (hi * N / sampleRate).toInt().coerceIn(iLo + 1, N / 2)
            var peak = 0f
            for (i in iLo until iHi) {
                val mag = sqrt(re[i] * re[i] + im[i] * im[i])
                peak = max(peak, mag)
            }
            // a decibeles y luego a 0..1
            val db = 20 * log10(peak / (N / 4f) + 1e-9f)
            // los agudos naturalmente tienen menos energía: les damos un empujón
            val tilt = b * 0.9f
            bands[b] = ((db + tilt + 60f) / 60f).coerceIn(0f, 1f)
        }
        val now = System.nanoTime()
        synchronized(lock) {
            frames.addLast(Frame(now, bands))
            while (frames.size > 64) frames.removeFirst()
        }
    }

    /** Las bandas que corresponden a lo que se está escuchando justo ahora (o null si no hay audio). */
    fun current(): FloatArray? {
        val target = System.nanoTime() - DELAY_NANOS
        synchronized(lock) {
            var latest: Frame? = null
            while (frames.isNotEmpty() && frames.first().time <= target) {
                latest = frames.removeFirst()
            }
            if (latest != null) lastShown = latest.bands
            return latest?.bands
        }
    }

    @Volatile private var lastShown: FloatArray? = null

    /** Como [current] pero sin consumir nada: varias cosas pueden leerlo a la vez (barra de avance, ecualizador). */
    fun peek(): FloatArray? {
        val target = System.nanoTime() - DELAY_NANOS
        synchronized(lock) {
            var latest: Frame? = null
            for (f in frames) { if (f.time <= target) latest = f else break }
            // tira lo viejo pero deja el último que ya sonó
            while (frames.size > 1 && frames[1].time <= target) frames.removeFirst()
            if (latest != null) { lastShown = latest.bands; return latest.bands }
        }
        return lastShown
    }

    /** FFT clásica (Cooley–Tukey) en el mismo arreglo. */
    private fun fft(re: FloatArray, im: FloatArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }
        var len = 2
        while (len <= n) {
            val ang = -2 * PI / len
            val wr = cos(ang).toFloat()
            val wi = sin(ang).toFloat()
            var i = 0
            while (i < n) {
                var cr = 1f
                var ci = 0f
                for (k in 0 until len / 2) {
                    val ar = re[i + k + len / 2] * cr - im[i + k + len / 2] * ci
                    val ai = re[i + k + len / 2] * ci + im[i + k + len / 2] * cr
                    re[i + k + len / 2] = re[i + k] - ar
                    im[i + k + len / 2] = im[i + k] - ai
                    re[i + k] += ar
                    im[i + k] += ai
                    val nr = cr * wr - ci * wi
                    ci = cr * wi + ci * wr
                    cr = nr
                }
                i += len
            }
            len = len shl 1
        }
    }
}
