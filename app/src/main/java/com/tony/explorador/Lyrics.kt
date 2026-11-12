package com.tony.explorador

import java.io.File
import java.nio.charset.Charset
import java.util.Locale

data class LyricLine(val timeMs: Long, val text: String)

/** Busca "Canción.lrc" junto a "Canción.mp3" (sin importar mayúsculas). */
fun findLrc(audio: File): File? {
    val dir = audio.parentFile ?: return null
    val base = audio.nameWithoutExtension.lowercase(Locale.ROOT)
    val exact = File(dir, audio.nameWithoutExtension + ".lrc")
    if (exact.isFile) return exact
    return dir.listFiles()?.firstOrNull {
        it.isFile && it.extension.equals("lrc", ignoreCase = true) &&
            it.nameWithoutExtension.lowercase(Locale.ROOT) == base
    }
}

/** Lee el archivo en UTF-8; si trae acentos rotos, lo intenta como Windows-1252 (común en letras en español). */
fun readLrcText(file: File): String {
    val bytes = file.readBytes()
    val utf8 = String(bytes, Charsets.UTF_8).removePrefix("﻿")
    if (!utf8.contains('�')) return utf8
    return runCatching { String(bytes, Charset.forName("windows-1252")) }.getOrDefault(utf8)
}

private val timeTag = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
private val offsetTag = Regex("""\[offset:\s*([+-]?\d+)\s*]""", RegexOption.IGNORE_CASE)
private val wordTag = Regex("""<\d{1,3}:\d{1,2}(?:[.:]\d{1,3})?>""")

fun parseLrc(text: String): List<LyricLine> {
    val offset = offsetTag.find(text)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
    val lines = mutableListOf<LyricLine>()
    text.lineSequence().forEach { raw ->
        val line = raw.trim()
        var pos = 0
        val times = mutableListOf<Long>()
        // una línea puede tener varias marcas: [00:12.00][01:30.50]Coro
        while (true) {
            val m = timeTag.find(line, pos) ?: break
            if (m.range.first != pos) break
            val min = m.groupValues[1].toLong()
            val sec = m.groupValues[2].toLong()
            val fracStr = m.groupValues[3]
            val frac = when (fracStr.length) {
                0 -> 0L
                1 -> fracStr.toLong() * 100
                2 -> fracStr.toLong() * 10
                else -> fracStr.take(3).toLong()
            }
            times.add(min * 60_000 + sec * 1000 + frac)
            pos = m.range.last + 1
        }
        if (times.isEmpty()) return@forEach
        val lyric = wordTag.replace(line.substring(pos), "").trim()
        times.forEach { t -> lines.add(LyricLine((t - offset).coerceAtLeast(0), lyric)) }
    }
    return lines.sortedBy { it.timeMs }
}

/** Índice de la línea que se está cantando en este momento (-1 si aún no empieza). */
fun currentLyricIndex(lines: List<LyricLine>, positionMs: Long): Int {
    if (lines.isEmpty() || positionMs < lines.first().timeMs) return -1
    var lo = 0
    var hi = lines.lastIndex
    while (lo < hi) {
        val mid = (lo + hi + 1) / 2
        if (lines[mid].timeMs <= positionMs) lo = mid else hi = mid - 1
    }
    return lo
}
