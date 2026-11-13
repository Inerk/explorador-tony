package com.tony.explorador

import android.content.Context
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class FileKind { FOLDER, IMAGE, AUDIO, VIDEO, OTHER }

private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif")
private val AUDIO_EXT = setOf("mp3", "m4a", "aac", "wav", "ogg", "oga", "flac", "opus", "amr", "mid", "midi")
private val VIDEO_EXT = setOf("mp4", "mkv", "webm", "3gp", "mov", "m4v", "ts", "avi")

/** Sólo por la extensión (rápido, sin tocar el disco). */
fun isVideoExtension(ext: String): Boolean = ext.lowercase(Locale.ROOT) in VIDEO_EXT

fun File.kind(): FileKind {
    if (isDirectory) return FileKind.FOLDER
    return when (extension.lowercase(Locale.ROOT)) {
        in IMAGE_EXT -> FileKind.IMAGE
        in AUDIO_EXT -> FileKind.AUDIO
        in VIDEO_EXT -> FileKind.VIDEO
        else -> FileKind.OTHER
    }
}

enum class SortMode(val label: String) {
    NAME("Nombre"), DATE("Fecha"), SIZE("Tamaño")
}

fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)
    return String.format(Locale.US, "%.2f GB", mb / 1024.0)
}

private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("es", "MX"))
fun formatDate(millis: Long): String = dateFormat.format(Date(millis))

fun listFolder(dir: File, sort: SortMode, ascending: Boolean, showHidden: Boolean): List<File> {
    val files = dir.listFiles()?.filter { showHidden || !it.name.startsWith(".") } ?: emptyList()
    val comparator: Comparator<File> = when (sort) {
        SortMode.NAME -> compareBy { it.name.lowercase(Locale.ROOT) }
        SortMode.DATE -> compareBy { it.lastModified() }
        SortMode.SIZE -> compareBy { if (it.isDirectory) 0L else it.length() }
    }
    val ordered = if (ascending) comparator else comparator.reversed()
    // carpetas siempre primero
    return files.sortedWith(compareBy<File> { !it.isDirectory }.then(ordered))
}

data class StorageRoot(val name: String, val file: File)

fun storageRoots(context: Context): List<StorageRoot> {
    val roots = mutableListOf<StorageRoot>()
    val internal = Environment.getExternalStorageDirectory()
    roots.add(StorageRoot("Almacenamiento interno", internal))
    context.getExternalFilesDirs(null).forEach { dir ->
        if (dir == null) return@forEach
        val path = dir.absolutePath
        val idx = path.indexOf("/Android/data")
        if (idx > 0) {
            val root = File(path.substring(0, idx))
            if (root.absolutePath != internal.absolutePath) {
                roots.add(StorageRoot("Tarjeta SD", root))
            }
        }
    }
    return roots
}

/** Nombre libre en la carpeta destino: "foto (1).jpg", "foto (2).jpg"... */
fun uniqueTarget(destDir: File, name: String): File {
    var target = File(destDir, name)
    if (!target.exists()) return target
    val base = name.substringBeforeLast('.', name)
    val ext = if (name.contains('.')) "." + name.substringAfterLast('.') else ""
    var n = 1
    while (target.exists()) {
        target = File(destDir, "$base ($n)$ext")
        n++
    }
    return target
}

suspend fun copyInto(sources: List<File>, destDir: File): Int = withContext(Dispatchers.IO) {
    var ok = 0
    for (src in sources) {
        // no copiar una carpeta dentro de sí misma
        if (src.isDirectory && destDir.absolutePath.startsWith(src.absolutePath + "/")) continue
        if (destDir.absolutePath == src.absolutePath) continue
        val target = uniqueTarget(destDir, src.name)
        val success = runCatching {
            if (src.isDirectory) src.copyRecursively(target) else { src.copyTo(target); true }
        }.getOrDefault(false)
        if (success) ok++
    }
    ok
}

suspend fun moveInto(sources: List<File>, destDir: File): Int = withContext(Dispatchers.IO) {
    var ok = 0
    for (src in sources) {
        if (src.parentFile?.absolutePath == destDir.absolutePath) { ok++; continue }
        if (src.isDirectory && destDir.absolutePath.startsWith(src.absolutePath + "/")) continue
        if (destDir.absolutePath == src.absolutePath) continue
        val target = uniqueTarget(destDir, src.name)
        val moved = src.renameTo(target) || runCatching {
            val copied = if (src.isDirectory) src.copyRecursively(target) else { src.copyTo(target); true }
            copied && src.deleteRecursively()
        }.getOrDefault(false)
        if (moved) {
            ok++
            // lo que acompaña al archivo se va con él: etiquetas (.tony.json) y letra (.lrc)
            if (target.isFile) runCatching {
                File(src.absolutePath + ".tony.json").takeIf { it.isFile }?.renameTo(File(target.absolutePath + ".tony.json"))
                File(src.parentFile, src.nameWithoutExtension + ".lrc").takeIf { it.isFile }
                    ?.renameTo(File(target.parentFile, target.nameWithoutExtension + ".lrc"))
            }
        }
    }
    ok
}

suspend fun deleteAll(files: List<File>): Int = withContext(Dispatchers.IO) {
    files.count { runCatching { it.deleteRecursively() }.getOrDefault(false) }
}

suspend fun searchFiles(root: File, query: String, showHidden: Boolean, limit: Int = 300): List<File> =
    withContext(Dispatchers.IO) {
        val q = query.trim().lowercase(Locale.ROOT)
        if (q.isEmpty()) return@withContext emptyList()
        val results = mutableListOf<File>()
        root.walkTopDown()
            .onEnter { showHidden || !it.name.startsWith(".") || it == root }
            .forEach { f ->
                if (results.size >= limit) return@forEach
                if (f != root && (showHidden || !f.name.startsWith(".")) &&
                    f.name.lowercase(Locale.ROOT).contains(q)
                ) results.add(f)
            }
        results
    }

fun folderItemCount(dir: File, showHidden: Boolean): Int =
    dir.list()?.count { showHidden || !it.startsWith(".") } ?: 0
