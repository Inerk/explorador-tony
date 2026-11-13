package com.tony.explorador

import android.content.Context
import android.graphics.BitmapFactory
import android.media.ExifInterface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.text.Normalizer
import java.util.Locale

/**
 * Texto que aparece dentro de las fotos (letreros, documentos, capturas…), para poder buscarlo.
 * Se lee en el teléfono, sin mandar las fotos a internet.
 */
object PhotoText {
    private lateinit var app: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private val texts = HashMap<String, String>()      // ruta → texto (en minúsculas, sin acentos)
    var running by mutableStateOf(false)
        private set
    var done by mutableIntStateOf(0)
        private set
    var total by mutableIntStateOf(0)
        private set
    /** Sube cuando hay texto nuevo, para refrescar los resultados. */
    var version by mutableIntStateOf(0)
        private set
    private val file get() = File(app.filesDir, "texto_fotos.json")

    fun init(context: Context) {
        app = context.applicationContext
        scope.launch {
            runCatching {
                val o = JSONObject(file.readText())
                synchronized(texts) { o.keys().forEach { texts[it] = o.getString(it) } }
                version++
            }
        }
    }

    val indexed: Int get() = synchronized(texts) { texts.size }

    fun norm(s: String): String =
        Normalizer.normalize(s.lowercase(Locale.ROOT), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

    fun matches(path: String, query: String): Boolean = synchronized(texts) { texts[path]?.contains(query) == true }

    private fun save() {
        runCatching {
            val o = JSONObject()
            synchronized(texts) { texts.forEach { (k, v) -> o.put(k, v) } }
            file.writeText(o.toString())
        }
    }

    fun stop() { job?.cancel(); job = null; running = false }

    /** Lee el texto de las fotos que falten (las más nuevas primero). */
    fun start(paths: List<String>) {
        if (running) return
        job = scope.launch {
            running = true
            val pending = synchronized(texts) { paths.filter { it !in texts } }
            total = pending.size; done = 0
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            for (p in pending) {
                if (!isActive) break
                val text = runCatching {
                    // se lee una versión reducida: más rápido y suficiente para letras
                    val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(p, b)
                    var sample = 1
                    while (maxOf(b.outWidth, b.outHeight) / sample > 1800) sample *= 2
                    val bmp = BitmapFactory.decodeFile(p, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return@runCatching ""
                    val rot = when (ExifInterface(p).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270
                        else -> 0
                    }
                    val result = Tasks.await(recognizer.process(InputImage.fromBitmap(bmp, rot))).text
                    bmp.recycle()
                    norm(result).replace(Regex("\\s+"), " ").take(1500)
                }.getOrDefault("")
                synchronized(texts) { texts[p] = text }
                done++
                if (done % 25 == 0) { save(); version++ }
            }
            runCatching { recognizer.close() }
            save(); version++
            running = false
        }
    }
}
