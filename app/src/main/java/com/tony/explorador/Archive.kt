package com.tony.explorador

import net.sf.sevenzipjbinding.ExtractAskMode
import net.sf.sevenzipjbinding.ExtractOperationResult
import net.sf.sevenzipjbinding.IArchiveExtractCallback
import net.sf.sevenzipjbinding.IArchiveOpenCallback
import net.sf.sevenzipjbinding.ICryptoGetTextPassword
import net.sf.sevenzipjbinding.IInArchive
import net.sf.sevenzipjbinding.ISequentialOutStream
import net.sf.sevenzipjbinding.PropID
import net.sf.sevenzipjbinding.SevenZip
import net.sf.sevenzipjbinding.SevenZipException
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream
import org.apache.commons.compress.archivers.sevenz.SevenZMethod
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.util.Date
import java.util.Locale
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class NeedPasswordException : Exception("Este archivo tiene contraseña")
class WrongPasswordException : Exception("Contraseña incorrecta")
class CancelledException : Exception("Cancelado")

/** Abrir, ver y sacar archivos de comprimidos (con 7-Zip), y crear ZIP, 7Z y TAR.GZ. */
object Archives {
    private val EXT = setOf("zip", "rar", "7z", "tar", "gz", "tgz", "bz2", "tbz", "tbz2", "xz", "txz", "lzma", "cab", "iso",
        "z", "arj", "lzh", "lha", "cpio", "wim", "001", "jar", "zipx")

    fun isArchive(f: File): Boolean {
        val n = f.name.lowercase(Locale.ROOT)
        return f.extension.lowercase(Locale.ROOT) in EXT || n.endsWith(".part1.rar")
    }

    data class Entry(
        val index: Int, val path: String, val isDir: Boolean, val size: Long, val packed: Long,
        val time: Long, val encrypted: Boolean,
    )

    private class OpenCb(val password: String?) : IArchiveOpenCallback, ICryptoGetTextPassword {
        var asked = false
        override fun setTotal(files: Long?, bytes: Long?) {}
        override fun setCompleted(files: Long?, bytes: Long?) {}
        override fun cryptoGetTextPassword(): String { asked = true; return password ?: throw SevenZipException("password") }
    }

    private inline fun <T> open(file: File, password: String?, block: (IInArchive) -> T): T {
        val raf = RandomAccessFile(file, "r")
        val cb = OpenCb(password)
        val arc = try {
            SevenZip.openInArchive(null, RandomAccessFileInStream(raf), cb)
        } catch (e: SevenZipException) {
            raf.close()
            if (cb.asked) throw if (password == null) NeedPasswordException() else WrongPasswordException()
            throw Exception("No se pudo abrir: el archivo está dañado o no es un comprimido compatible")
        }
        try { return block(arc) } finally { runCatching { arc.close() }; runCatching { raf.close() } }
    }

    /** Todo lo que trae el comprimido (sin sacar nada). */
    fun list(file: File, password: String?): List<Entry> = open(file, password) { arc ->
        val n = arc.numberOfItems
        val baseName = file.nameWithoutExtension.removeSuffix(".tar")
        (0 until n).map { i ->
            val raw = (arc.getProperty(i, PropID.PATH) as? String)?.replace('\\', '/')?.trim('/')
            Entry(
                index = i,
                path = raw?.takeIf { it.isNotBlank() } ?: baseName,
                isDir = arc.getProperty(i, PropID.IS_FOLDER) as? Boolean ?: false,
                size = (arc.getProperty(i, PropID.SIZE) as? Number)?.toLong() ?: 0L,
                packed = (arc.getProperty(i, PropID.PACKED_SIZE) as? Number)?.toLong() ?: 0L,
                time = (arc.getProperty(i, PropID.LAST_MODIFICATION_TIME) as? Date)?.time ?: 0L,
                encrypted = arc.getProperty(i, PropID.ENCRYPTED) as? Boolean ?: false,
            )
        }
    }

    /**
     * Saca los elementos indicados (null = todos) a [dest]. [strip] se quita del inicio de cada ruta
     * (para sacar una carpeta sin las de arriba). [flat] = poner los archivos sin carpetas.
     * Devuelve los archivos creados.
     */
    fun extract(
        file: File, entries: List<Entry>?, dest: File, password: String?, strip: String = "", flat: Boolean = false,
        progress: (done: Long, total: Long) -> Unit = { _, _ -> }, cancelled: () -> Boolean = { false },
    ): List<File> = open(file, password) { arc ->
        dest.mkdirs()
        val destCanon = dest.canonicalPath
        val byIndex = entries?.associateBy { it.index }
        val all = byIndex == null
        val indices = (entries?.map { it.index } ?: (0 until arc.numberOfItems).toList()).sorted().toIntArray()
        val created = ArrayList<File>()
        var errors = 0
        var wrongPw = false
        var anyEncrypted = false
        val cb = object : IArchiveExtractCallback, ICryptoGetTextPassword {
            var out: OutputStream? = null
            var totalBytes = 0L
            var current: File? = null
            fun closeOut() { runCatching { out?.close() }; out = null }
            override fun getStream(index: Int, mode: ExtractAskMode): ISequentialOutStream? {
                closeOut()
                if (mode != ExtractAskMode.EXTRACT) return null
                if (cancelled()) throw SevenZipException("cancel")
                val isDir = arc.getProperty(index, PropID.IS_FOLDER) as? Boolean ?: false
                if (arc.getProperty(index, PropID.ENCRYPTED) as? Boolean == true) anyEncrypted = true
                var path = ((arc.getProperty(index, PropID.PATH) as? String)?.replace('\\', '/')?.trim('/'))
                    ?.takeIf { it.isNotBlank() } ?: file.nameWithoutExtension.removeSuffix(".tar")
                if (strip.isNotEmpty() && path.startsWith(strip)) path = path.removePrefix(strip).trimStart('/')
                if (flat) path = path.substringAfterLast('/')
                if (path.isBlank()) return null
                val target = File(dest, path)
                // nunca escribir fuera de la carpeta destino (protección contra comprimidos tramposos)
                if (!target.canonicalPath.startsWith(destCanon)) return null
                if (isDir) { target.mkdirs(); return null }
                target.parentFile?.mkdirs()
                val f = if (flat) uniqueFile(target) else target
                current = f
                created += f
                val o = BufferedOutputStream(FileOutputStream(f), 64 * 1024)
                out = o
                return ISequentialOutStream { data ->
                    if (cancelled()) throw SevenZipException("cancel")
                    o.write(data); data.size
                }
            }
            override fun prepareOperation(mode: ExtractAskMode) {}
            override fun setOperationResult(r: ExtractOperationResult) {
                closeOut()
                if (r != ExtractOperationResult.OK) {
                    errors++
                    if (r == ExtractOperationResult.WRONG_PASSWORD || (anyEncrypted && (r == ExtractOperationResult.DATAERROR || r == ExtractOperationResult.CRCERROR)))
                        wrongPw = true
                    current?.let { runCatching { it.delete() }; created.remove(it) }
                }
                current = null
            }
            override fun setTotal(t: Long) { totalBytes = t }
            override fun setCompleted(c: Long) { progress(c, totalBytes) }
            override fun cryptoGetTextPassword(): String {
                anyEncrypted = true
                return password ?: throw SevenZipException("password")
            }
        }
        try {
            arc.extract(if (all) null else indices, false, cb)
        } catch (e: SevenZipException) {
            cb.closeOut()
            cb.current?.let { runCatching { it.delete() } }
            if (cancelled()) throw CancelledException()
            if (anyEncrypted) throw if (password == null) NeedPasswordException() else WrongPasswordException()
            throw Exception("Falló al extraer: ${e.message ?: "archivo dañado"}")
        } finally { cb.closeOut() }
        if (wrongPw) throw if (password == null) NeedPasswordException() else WrongPasswordException()
        if (errors > 0 && created.isEmpty()) throw Exception("No se pudo extraer ($errors con error; el archivo puede estar dañado)")
        created
    }

    fun uniqueFile(f: File): File {
        if (!f.exists()) return f
        var i = 2
        while (true) {
            val c = File(f.parentFile, if (f.extension.isEmpty()) "${f.name} ($i)" else "${f.nameWithoutExtension} ($i).${f.extension}")
            if (!c.exists()) return c
            i++
        }
    }

    enum class Format(val label: String, val ext: String) { ZIP("ZIP", "zip"), SEVEN_Z("7Z (más pequeño)", "7z"), TAR_GZ("TAR.GZ", "tar.gz") }

    /** Comprime archivos y carpetas en uno solo. Nivel: 0 = sin comprimir, 1 = normal, 2 = máximo. */
    fun compress(
        items: List<File>, out: File, format: Format, level: Int,
        progress: (done: Long, total: Long) -> Unit = { _, _ -> }, cancelled: () -> Boolean = { false },
    ) {
        // qué va adentro y con qué ruta
        val files = ArrayList<Pair<File, String>>()
        fun walk(f: File, rel: String) {
            if (f == out) return
            if (f.isDirectory) {
                files += f to "$rel/"
                f.listFiles()?.sortedBy { it.name.lowercase(Locale.ROOT) }?.forEach { walk(it, "$rel/${it.name}") }
            } else files += f to rel
        }
        items.forEach { walk(it, it.name) }
        val total = files.sumOf { if (it.first.isFile) it.first.length() else 0L }.coerceAtLeast(1)
        var done = 0L
        val buf = ByteArray(64 * 1024)
        fun copy(f: File, o: (ByteArray, Int) -> Unit) {
            f.inputStream().use { inp ->
                while (true) {
                    if (cancelled()) throw CancelledException()
                    val n = inp.read(buf); if (n <= 0) break
                    o(buf, n); done += n; progress(done, total)
                }
            }
        }
        val tmp = File(out.parentFile, ".${out.name}.parcial")
        try {
            when (format) {
                Format.ZIP -> ZipOutputStream(BufferedOutputStream(FileOutputStream(tmp), 64 * 1024)).use { z ->
                    z.setLevel(when (level) { 0 -> Deflater.NO_COMPRESSION; 2 -> Deflater.BEST_COMPRESSION; else -> Deflater.DEFAULT_COMPRESSION })
                    for ((f, rel) in files) {
                        val e = ZipEntry(rel).apply { time = f.lastModified() }
                        z.putNextEntry(e)
                        if (f.isFile) copy(f) { b, n -> z.write(b, 0, n) }
                        z.closeEntry()
                    }
                }
                Format.SEVEN_Z -> SevenZOutputFile(tmp).use { z ->
                    z.setContentCompression(if (level == 0) SevenZMethod.COPY else SevenZMethod.LZMA2)
                    for ((f, rel) in files) {
                        val e = z.createArchiveEntry(f, rel.trimEnd('/'))
                        z.putArchiveEntry(e)
                        if (f.isFile) copy(f) { b, n -> z.write(b, 0, n) }
                        z.closeArchiveEntry()
                    }
                }
                Format.TAR_GZ -> TarArchiveOutputStream(GzipCompressorOutputStream(BufferedOutputStream(FileOutputStream(tmp), 64 * 1024),
                    org.apache.commons.compress.compressors.gzip.GzipParameters().apply {
                        compressionLevel = when (level) { 0 -> 1; 2 -> 9; else -> 6 }
                    })).use { t ->
                    t.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
                    t.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX)
                    for ((f, rel) in files) {
                        val e = TarArchiveEntry(f, rel)
                        t.putArchiveEntry(e)
                        if (f.isFile) copy(f) { b, n -> t.write(b, 0, n) }
                        t.closeArchiveEntry()
                    }
                }
            }
            if (!tmp.renameTo(out)) { tmp.copyTo(out, overwrite = true); tmp.delete() }
        } catch (e: Exception) {
            tmp.delete()
            throw e
        }
    }
}
