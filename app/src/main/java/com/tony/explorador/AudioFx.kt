package com.tony.explorador

import android.content.Context
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Ecualizador y efectos de sonido del teléfono (los de Android), pegados al reproductor.
 * Se guardan en preferencias y se vuelven a poner cada vez que el reproductor cambia de sesión de audio.
 */
object AudioFx {
    private var prefs: android.content.SharedPreferences? = null
    private var eq: Equalizer? = null
    private var bass: BassBoost? = null
    private var virt: Virtualizer? = null
    private var loud: LoudnessEnhancer? = null

    var eqOn by mutableStateOf(false); private set
    var preset by mutableStateOf("Plano"); private set
    /** Frecuencias de las bandas del teléfono (Hz) y su nivel en milibeles. */
    val freqs = mutableStateListOf<Int>()
    val levels = mutableStateListOf<Int>()
    var minLevel by mutableStateOf(-1500); private set
    var maxLevel by mutableStateOf(1500); private set
    var bassOn by mutableStateOf(false); private set
    var virtOn by mutableStateOf(false); private set
    var vocalOn by mutableStateOf(false); private set
    var loudOn by mutableStateOf(false); private set
    var available by mutableStateOf(false); private set

    /** Formas de sonar ya listas (porcentaje de -1 a 1 por zona: graves, medios-graves, medios, medios-agudos, agudos). */
    val PRESETS = linkedMapOf(
        "Plano" to floatArrayOf(0f, 0f, 0f, 0f, 0f),
        "Graves" to floatArrayOf(0.6f, 0.35f, 0f, 0f, 0.05f),
        "Pop" to floatArrayOf(-0.1f, 0.2f, 0.4f, 0.2f, -0.1f),
        "Rock" to floatArrayOf(0.4f, 0.2f, -0.15f, 0.25f, 0.45f),
        "Banda / regional" to floatArrayOf(0.35f, 0.15f, 0.1f, 0.25f, 0.2f),
        "Electrónica" to floatArrayOf(0.55f, 0.25f, -0.1f, 0.2f, 0.45f),
        "Voz" to floatArrayOf(-0.2f, 0f, 0.45f, 0.35f, 0f),
        "Personalizado" to floatArrayOf(0f, 0f, 0f, 0f, 0f),
    )

    fun init(c: Context) {
        if (prefs != null) return
        val p = c.applicationContext.getSharedPreferences("efectos_audio", Context.MODE_PRIVATE)
        prefs = p
        eqOn = p.getBoolean("eq", false); preset = p.getString("preset", "Plano") ?: "Plano"
        bassOn = p.getBoolean("bass", false); virtOn = p.getBoolean("virt", false)
        vocalOn = p.getBoolean("vocal", false); loudOn = p.getBoolean("loud", false)
    }

    private fun save() {
        prefs?.edit()?.apply {
            putBoolean("eq", eqOn); putString("preset", preset); putBoolean("bass", bassOn); putBoolean("virt", virtOn)
            putBoolean("vocal", vocalOn); putBoolean("loud", loudOn); putString("niveles", levels.joinToString(","))
        }?.apply()
    }

    /** Se llama desde el servicio de reproducción con su sesión de audio. */
    fun attach(c: Context, session: Int) {
        init(c)
        release()
        if (session == 0) return
        runCatching {
            val e = Equalizer(0, session)
            eq = e
            val (lo, hi) = e.bandLevelRange.let { it[0].toInt() to it[1].toInt() }
            minLevel = lo; maxLevel = hi
            freqs.clear(); levels.clear()
            for (b in 0 until e.numberOfBands) freqs += e.getCenterFreq(b.toShort()) / 1000
            val saved = prefs?.getString("niveles", "")?.split(',')?.mapNotNull { it.toIntOrNull() }.orEmpty()
            if (saved.size == freqs.size) levels.addAll(saved) else levels.addAll(List(freqs.size) { 0 })
            available = true
        }.onFailure { available = false }
        runCatching { bass = BassBoost(0, session) }
        runCatching { virt = Virtualizer(0, session) }
        runCatching { loud = LoudnessEnhancer(session) }
        apply()
    }

    fun release() {
        listOf(eq, bass, virt, loud).forEach { runCatching { it?.release() } }
        eq = null; bass = null; virt = null; loud = null
    }

    private fun apply() {
        runCatching {
            eq?.let { e ->
                val vocal = if (vocalOn) 350 else 0
                for (b in levels.indices) {
                    val f = freqs.getOrNull(b) ?: 0
                    val extra = if (f in 800..4500) vocal else 0
                    e.setBandLevel(b.toShort(), (levels[b] + extra).coerceIn(minLevel, maxLevel).toShort())
                }
                e.enabled = eqOn || vocalOn
            }
        }
        runCatching { bass?.let { if (it.strengthSupported) it.setStrength(800); it.enabled = bassOn } }
        runCatching { virt?.let { if (it.strengthSupported) it.setStrength(900); it.enabled = virtOn } }
        runCatching { loud?.let { it.setTargetGain(600); it.enabled = loudOn } }
    }

    fun setEq(on: Boolean) { eqOn = on; apply(); save() }
    fun setBass(on: Boolean) { bassOn = on; apply(); save() }
    fun setVirt(on: Boolean) { virtOn = on; apply(); save() }
    fun setVocal(on: Boolean) { vocalOn = on; apply(); save() }
    fun setLoud(on: Boolean) { loudOn = on; apply(); save() }

    fun setBand(i: Int, level: Int) {
        if (i !in levels.indices) return
        levels[i] = level.coerceIn(minLevel, maxLevel); preset = "Personalizado"; eqOn = true; apply(); save()
    }

    fun usePreset(name: String) {
        val shape = PRESETS[name] ?: return
        preset = name; eqOn = true
        if (name != "Personalizado") for (i in levels.indices) {
            // reparte las 5 zonas entre las bandas que tenga el teléfono
            val pos = if (levels.size == 1) 0f else i * 4f / (levels.size - 1)
            val a = shape[pos.toInt().coerceAtMost(4)]; val b = shape[(pos.toInt() + 1).coerceAtMost(4)]
            val v = a + (b - a) * (pos - pos.toInt())
            levels[i] = (v * maxLevel).toInt()
        }
        apply(); save()
    }

    fun label(hz: Int) = if (hz >= 1000) "${hz / 1000}${if (hz % 1000 >= 100) "." + (hz % 1000) / 100 else ""}K" else "$hz"
}
