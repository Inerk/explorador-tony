package com.tony.explorador

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.util.Xml
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Sincroniza la música con un NAS por WebDAV (por ejemplo un UGREEN):
 * sube lo que falta allá y baja lo que falta aquí. Compara por nombre y tamaño.
 * NUNCA borra ni reemplaza nada, en ningún lado.
 */
object NasSync {
    private lateinit var app: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private val prefs get() = app.getSharedPreferences("nas", Context.MODE_PRIVATE)

    var url by mutableStateOf("")          // dirección WebDAV de la carpeta de música del NAS
    var user by mutableStateOf("")
    var password by mutableStateOf("")
    var localFolder by mutableStateOf("")

    var running by mutableStateOf(false)
        private set
    var status by mutableStateOf("")
        private set
    var done by mutableIntStateOf(0)
        private set
    var total by mutableIntStateOf(0)
        private set
    /** Resumen de la última sincronización. */
    val uploaded = mutableStateListOf<String>()
    val downloaded = mutableStateListOf<String>()
    val different = mutableStateListOf<String>()   // mismo nombre en la misma carpeta pero distinto tamaño: no se tocan
    val failed = mutableStateListOf<String>()
    var finished by mutableStateOf(false)
        private set

    fun init(context: Context) {
        app = context.applicationContext
        url = prefs.getString("url", "").orEmpty()
        user = prefs.getString("user", "").orEmpty()
        password = prefs.getString("pass", "").orEmpty()
        localFolder = prefs.getString("local", null)
            ?: File(Environment.getExternalStorageDirectory(), Environment.DIRECTORY_MUSIC).absolutePath
    }

    fun saveSettings() {
        prefs.edit().putString("url", url.trim()).putString("user", user.trim()).putString("pass", password).putString("local", localFolder).apply()
    }

    fun stop() { job?.cancel(); job = null; running = false; status = "Detenido" }

    private data class Remote(val rel: String, val size: Long)

    private fun client() = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS).readTimeout(120, TimeUnit.SECONDS).writeTimeout(0, TimeUnit.SECONDS)
        .build()

    private fun base(): String = url.trim().trimEnd('/')
    private fun enc(rel: String) = rel.split('/').joinToString("/") { Uri.encode(it) }
    private fun req(path: String) = Request.Builder().url(base() + "/" + enc(path).trimStart('/'))
        .header("Authorization", Credentials.basic(user.trim(), password, Charsets.UTF_8))

    /** Lista una carpeta del NAS (un nivel). Regresa (subcarpetas, archivos). */
    private fun list(http: OkHttpClient, dir: String): Pair<List<String>, List<Remote>> {
        val body = """<?xml version="1.0" encoding="utf-8"?><d:propfind xmlns:d="DAV:"><d:prop><d:getcontentlength/><d:resourcetype/></d:prop></d:propfind>"""
            .toRequestBody("application/xml; charset=utf-8".toMediaTypeOrNull())
        val r = req(if (dir.isEmpty()) "" else "$dir/").method("PROPFIND", body).header("Depth", "1").build()
        http.newCall(r).execute().use { resp ->
            if (resp.code == 401) error("Usuario o contraseña incorrectos")
            if (resp.code == 404) error("No existe esa carpeta en el NAS (revisa la dirección)")
            if (resp.code !in 200..299) error("El NAS respondió ${resp.code}")
            val folders = mutableListOf<String>()
            val files = mutableListOf<Remote>()
            val p = Xml.newPullParser()
            p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            p.setInput(resp.body!!.charStream())
            var href = ""; var size = 0L; var isDir = false; var tag = ""
            val self = Uri.decode(Uri.parse(base()).path.orEmpty()).trimEnd('/') + (if (dir.isEmpty()) "" else "/$dir")
            var ev = p.eventType
            while (ev != XmlPullParser.END_DOCUMENT) {
                when (ev) {
                    XmlPullParser.START_TAG -> {
                        tag = p.name
                        when (tag) { "response" -> { href = ""; size = 0; isDir = false }; "collection" -> isDir = true }
                    }
                    XmlPullParser.TEXT -> when (tag) {
                        "href" -> href += p.text.trim()
                        "getcontentlength" -> p.text.trim().toLongOrNull()?.let { size = it }
                    }
                    XmlPullParser.END_TAG -> {
                        if (p.name == "response") {
                            val path = Uri.decode(Uri.parse(href).path ?: href).trimEnd('/')
                            if (path != self && path.isNotBlank()) {
                                val name = path.substringAfterLast('/')
                                val rel = if (dir.isEmpty()) name else "$dir/$name"
                                if (isDir) folders.add(rel) else files.add(Remote(rel, size))
                            }
                        }
                        tag = ""
                    }
                }
                ev = p.next()
            }
            return folders to files
        }
    }

    private fun wanted(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val f = File(name)
        return f.kind().let { it == FileKind.AUDIO || it == FileKind.VIDEO } || ext == "lrc" || name.endsWith(".tony.json") ||
            ext in setOf("jpg", "jpeg", "png") && f.nameWithoutExtension.lowercase(Locale.ROOT) in setOf("cover", "folder", "front")
    }

    private fun key(name: String, size: Long) = name.lowercase(Locale.ROOT) + "|" + size

    /** Sólo prueba la conexión. */
    fun test(onResult: (String) -> Unit) {
        saveSettings()
        scope.launch {
            val msg = runCatching { val (d, f) = list(client(), ""); "Conexión correcta ✔  (${d.size} carpetas y ${f.size} archivos en esa carpeta)" }
                .getOrElse { "No se pudo conectar: ${it.message ?: it.javaClass.simpleName}" }
            withContext(Dispatchers.Main) { onResult(msg) }
        }
    }

    fun start() {
        if (running) return
        saveSettings()
        uploaded.clear(); downloaded.clear(); different.clear(); failed.clear(); finished = false
        job = scope.launch {
            running = true; done = 0; total = 0
            val http = client()
            val localRoot = File(localFolder)
            try {
                // 1) qué hay en el NAS
                status = "Revisando el NAS…"
                val remote = mutableListOf<Remote>()
                val remoteDirs = HashSet<String>()
                val queue = ArrayDeque(listOf(""))
                while (queue.isNotEmpty() && isActive) {
                    val d = queue.removeFirst()
                    status = "Revisando el NAS… ${d.ifEmpty { "/" }}"
                    val (dirs, files) = list(http, d)
                    dirs.filterNot { n -> val b = n.substringAfterLast('/'); (b.startsWith(".") && b != ".animadas") || b.startsWith("@") }
                        .forEach { remoteDirs.add(it); queue.add(it) }
                    remote.addAll(files.filter { wanted(it.rel.substringAfterLast('/')) })
                }
                // 2) qué hay en el teléfono
                status = "Revisando el teléfono…"
                val local = localRoot.walkTopDown().onEnter { it == localRoot || !it.name.startsWith(".") || it.name == ".animadas" }
                    .filter { it.isFile && wanted(it.name) }
                    .map { Remote(it.relativeTo(localRoot).path.replace(File.separatorChar, '/'), it.length()) }.toList()

                // 3) comparar por nombre y tamaño
                val remoteKeys = remote.mapTo(HashSet()) { key(it.rel.substringAfterLast('/'), it.size) }
                val localKeys = local.mapTo(HashSet()) { key(it.rel.substringAfterLast('/'), it.size) }
                val remoteByRel = remote.associateBy { it.rel.lowercase(Locale.ROOT) }
                val localByRel = local.associateBy { it.rel.lowercase(Locale.ROOT) }
                val toUpload = local.filter { key(it.rel.substringAfterLast('/'), it.size) !in remoteKeys }
                val toDownload = remote.filter { key(it.rel.substringAfterLast('/'), it.size) !in localKeys }
                total = toUpload.size + toDownload.size
                val scanPaths = mutableListOf<String>()

                // 4) subir lo que falta en el NAS
                for (f in toUpload) {
                    if (!isActive) break
                    status = "Subiendo: ${f.rel.substringAfterLast('/')}"
                    if (remoteByRel[f.rel.lowercase(Locale.ROOT)] != null) {
                        // ya hay uno con ese nombre en esa carpeta pero de otro tamaño: no se reemplaza
                        withContext(Dispatchers.Main) { different.add(f.rel) }; done++; continue
                    }
                    val ok = runCatching {
                        // crear las carpetas que falten
                        var acc = ""
                        f.rel.substringBeforeLast('/', "").split('/').filter { it.isNotBlank() }.forEach { part ->
                            acc = if (acc.isEmpty()) part else "$acc/$part"
                            if (remoteDirs.add(acc)) http.newCall(req("$acc/").method("MKCOL", null).build()).execute().close()
                        }
                        val body: RequestBody = File(localRoot, f.rel).asRequestBody("application/octet-stream".toMediaTypeOrNull())
                        http.newCall(req(f.rel).header("If-None-Match", "*").put(body).build()).execute().use { it.code in 200..299 }
                    }.getOrDefault(false)
                    withContext(Dispatchers.Main) { if (ok) uploaded.add(f.rel) else failed.add("Subir: ${f.rel}") }
                    done++
                }
                // 5) bajar lo que falta en el teléfono
                for (f in toDownload) {
                    if (!isActive) break
                    status = "Descargando: ${f.rel.substringAfterLast('/')}"
                    val target = File(localRoot, f.rel)
                    if (target.exists() || localByRel[f.rel.lowercase(Locale.ROOT)] != null) {
                        withContext(Dispatchers.Main) { different.add(f.rel) }; done++; continue
                    }
                    val ok = runCatching {
                        target.parentFile?.mkdirs()
                        val part = File(target.parentFile, target.name + ".descargando")
                        val fine = http.newCall(req(f.rel).get().build()).execute().use { resp ->
                            if (resp.code !in 200..299) false
                            else { resp.body!!.byteStream().use { input -> part.outputStream().use { input.copyTo(it) } }; true }
                        }
                        if (fine && !target.exists()) part.renameTo(target) else { part.delete(); false }
                    }.getOrDefault(false)
                    if (ok) scanPaths.add(target.absolutePath)
                    withContext(Dispatchers.Main) { if (ok) downloaded.add(f.rel) else failed.add("Descargar: ${f.rel}") }
                    done++
                }
                if (scanPaths.isNotEmpty()) MediaScannerConnection.scanFile(app, scanPaths.toTypedArray(), null, null)
                status = if (isActive) "Listo" else "Detenido"
                if (downloaded.isNotEmpty()) runCatching { LibraryStore.scan() }
            } catch (e: Exception) {
                status = "Error: ${e.message ?: e.javaClass.simpleName}"
            }
            running = false
            finished = true
        }
    }
}
