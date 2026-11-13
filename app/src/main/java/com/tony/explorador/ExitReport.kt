package com.tony.explorador

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Cuando la app se cierra sin pasar por el atrapador de errores (se congeló, falló código nativo,
 * Android la cerró por memoria…), al volver a abrirla le pregunta a Android por qué se cerró
 * y deja el reporte listo para copiar.
 */
object ExitReport {
    fun check(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val prefs = context.getSharedPreferences("exit_report", Context.MODE_PRIVATE)
        val seen = prefs.getLong("last", 0L)
        val am = context.getSystemService(ActivityManager::class.java) ?: return
        val infos = am.getHistoricalProcessExitReasons(context.packageName, 0, 5)
        val newest = infos.maxOfOrNull { it.timestamp } ?: return
        prefs.edit().putLong("last", newest).apply()
        // primera vez: solo cuenta lo de las últimas 6 horas (así sale el cierre que acaba de pasar)
        val since = if (seen == 0L) System.currentTimeMillis() - 6 * 3600_000L else seen
        val bad = infos.filter { it.timestamp > since && it.reason in BAD }.maxByOrNull { it.timestamp } ?: return
        val errFile = File(context.filesDir, "ultimo_error.txt")
        // si el atrapador normal ya guardó un error de Java, ese es más útil
        if (bad.reason == ApplicationExitInfo.REASON_CRASH && errFile.isFile) return
        val sb = StringBuilder()
        sb.append("Explorador Tony v").append(currentVersionName(context)).append(" · Android ").append(Build.VERSION.RELEASE)
            .append(" · ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n')
        sb.append("Cierre inesperado: ").append(reasonName(bad.reason))
        sb.append(" · ").append(SimpleDateFormat("dd/MM HH:mm:ss", Locale.US).format(Date(bad.timestamp))).append('\n')
        bad.description?.let { sb.append("Detalle: ").append(it).append('\n') }
        sb.append("Memoria usada: ").append(bad.pss / 1024).append(" MB (máx. ").append(bad.rss / 1024).append(" MB)\n")
        sb.append("Proceso: ").append(bad.processName).append(" · importancia ").append(bad.importance).append('\n')
        if (bad.reason == ApplicationExitInfo.REASON_ANR) {
            // en una congelada Android guarda qué estaba haciendo cada hilo: nos importa el principal
            val trace = runCatching { bad.traceInputStream?.bufferedReader()?.use { it.readText() } }.getOrNull()
            if (trace != null) {
                val main = trace.indexOf("\"main\"")
                val part = if (main >= 0) trace.substring(main).substringBefore("\n\n\"") else trace
                sb.append("\nHilo principal:\n").append(part.take(5000))
            }
        }
        errFile.writeText(sb.toString())
    }

    private val BAD = setOf(
        ApplicationExitInfo.REASON_CRASH,
        ApplicationExitInfo.REASON_CRASH_NATIVE,
        ApplicationExitInfo.REASON_ANR,
        ApplicationExitInfo.REASON_LOW_MEMORY,
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE,
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE,
        ApplicationExitInfo.REASON_SIGNALED,
    )

    private fun reasonName(r: Int) = when (r) {
        ApplicationExitInfo.REASON_CRASH -> "error de Java/Kotlin"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "falla de código nativo (video, audio o imágenes)"
        ApplicationExitInfo.REASON_ANR -> "la app se congeló (no respondió)"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "Android la cerró por falta de memoria"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "Android la cerró por usar demasiados recursos"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "no pudo arrancar"
        ApplicationExitInfo.REASON_SIGNALED -> "el sistema la detuvo (señal)"
        else -> "motivo $r"
    }
}
