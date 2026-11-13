package com.tony.explorador

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { c ->
    addOnSuccessListener { c.resume(it) }
    addOnFailureListener { c.resumeWithException(it) }
    addOnCanceledListener { c.cancel() }
}

/**
 * Traduce letras al español en el teléfono (Google ML Kit, sin mandar la letra a internet).
 * La primera vez de cada idioma baja un paquete de ~30 MB; luego funciona sin internet.
 * Las traducciones se guardan para no repetir el trabajo.
 */
object LyricsTranslate {
    private lateinit var dir: File
    private val lock = Mutex()
    /** Mensaje para mostrar mientras baja el idioma, o null. */
    var status by mutableStateOf<String?>(null)
        private set

    fun init(context: Context) { dir = File(context.filesDir, "traducciones").apply { mkdirs() } }

    private fun key(lines: List<String>): String {
        val md = MessageDigest.getInstance("MD5").digest(lines.joinToString("\n").toByteArray())
        return md.joinToString("") { "%02x".format(it) }
    }

    private val NAMES = mapOf("en" to "inglés", "pt" to "portugués", "fr" to "francés", "it" to "italiano", "de" to "alemán",
        "ko" to "coreano", "ja" to "japonés", "zh" to "chino", "ru" to "ruso")

    /** Una traducción por línea (vacía si no hacía falta), o null si la letra ya está en español. */
    suspend fun translate(lines: List<String>): List<String>? = withContext(Dispatchers.IO) {
        if (lines.isEmpty()) return@withContext null
        val k = key(lines)
        val cache = File(dir, "$k.txt")
        val none = File(dir, "$k.es")
        if (none.exists()) return@withContext null
        if (cache.isFile) return@withContext cache.readText().split('\n').let { if (it.size == lines.size) it else null }
        lock.withLock {
            val sample = lines.filter { it.isNotBlank() }.joinToString(" ").take(1500)
            val tag = runCatching { LanguageIdentification.getClient().identifyLanguage(sample).await() }.getOrDefault("und")
            val src = TranslateLanguage.fromLanguageTag(tag)
            if (tag == "und" || tag == "es" || src == null) { none.writeText(tag); return@withLock null }
            val tr = Translation.getClient(TranslatorOptions.Builder()
                .setSourceLanguage(src).setTargetLanguage(TranslateLanguage.SPANISH).build())
            try {
                status = "Preparando traductor de ${NAMES[tag] ?: tag}…"
                tr.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
                status = "Traduciendo letra…"
                val memo = HashMap<String, String>()
                val out = lines.map { l ->
                    val t = l.trim()
                    if (t.isEmpty()) "" else memo.getOrPut(t) { runCatching { tr.translate(t).await() }.getOrDefault("").replace('\n', ' ') }
                }
                cache.writeText(out.joinToString("\n"))
                out
            } finally {
                status = null
                tr.close()
            }
        }
    }
}

/** Traducción de la letra si está prendida en Ajustes de música (null mientras no hay). */
@Composable
fun rememberLyricsTranslation(lines: List<LyricLine>): State<List<String>?> {
    val on = LibraryStore.lyricsTranslate
    return produceState<List<String>?>(null, lines, on) {
        value = null
        if (on && lines.isNotEmpty()) value = runCatching { LyricsTranslate.translate(lines.map { it.text }) }.getOrNull()
    }
}
