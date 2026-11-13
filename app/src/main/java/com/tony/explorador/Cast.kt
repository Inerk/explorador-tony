package com.tony.explorador

import android.content.Context
import android.net.Uri
import android.net.wifi.WifiManager
import android.webkit.MimeTypeMap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.SessionProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.OutputStream
import java.io.RandomAccessFile
import java.net.DatagramPacket
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/** Opciones de Google Cast: el receptor estándar de Google (no hace falta una app en la TV). */
class CastOptionsProvider : OptionsProvider {
    override fun getCastOptions(context: Context): CastOptions = CastOptions.Builder()
        .setReceiverApplicationId(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)
        .setStopReceiverApplicationWhenEndingSession(true)
        .build()
    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? = null
}

fun mimeOf(f: File): String {
    val ext = f.extension.lowercase(Locale.ROOT)
    return when (ext) {
        "mkv" -> "video/x-matroska"; "mp4", "m4v" -> "video/mp4"; "webm" -> "video/webm"; "avi" -> "video/x-msvideo"
        "mov" -> "video/quicktime"; "ts" -> "video/mp2t"; "3gp" -> "video/3gpp"
        "mp3" -> "audio/mpeg"; "m4a", "aac" -> "audio/mp4"; "flac" -> "audio/flac"; "wav" -> "audio/wav"; "ogg", "opus" -> "audio/ogg"
        "jpg", "jpeg" -> "image/jpeg"; "png" -> "image/png"; "gif" -> "image/gif"; "webp" -> "image/webp"; "heic" -> "image/heic"
        else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }
}

/**
 * Servidor web chiquito dentro de la app: la TV pide el archivo por wifi.
 * Solo sirve los archivos que tú mandas (cada uno con su clave), con saltos (para adelantar video).
 */
object MediaServer {
    private var server: ServerSocket? = null
    private val pool = Executors.newCachedThreadPool()
    private val files = ConcurrentHashMap<String, File>()

    fun localIp(): String? {
        val all = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty().filter { it.isUp && !it.isLoopback }
        val sorted = all.sortedBy { if (it.name.startsWith("wlan")) 0 else if (it.name.startsWith("eth")) 1 else 2 }
        for (ni in sorted) for (a in ni.inetAddresses) if (a is Inet4Address && a.isSiteLocalAddress) return a.hostAddress
        return null
    }

    @Synchronized private fun start(): Int {
        server?.let { if (!it.isClosed) return it.localPort }
        val s = ServerSocket(0)
        server = s
        pool.execute {
            while (!s.isClosed) {
                val c = runCatching { s.accept() }.getOrNull() ?: break
                pool.execute { runCatching { handle(c) }; runCatching { c.close() } }
            }
        }
        return s.localPort
    }

    fun urlFor(f: File): String {
        val ip = localIp() ?: throw Exception("Conéctate al wifi (el mismo de la TV)")
        val port = start()
        val token = UUID.randomUUID().toString().take(10)
        files[token] = f
        val name = URLEncoder.encode(f.name, "UTF-8").replace("+", "%20")
        return "http://$ip:$port/m/$token/$name"
    }

    private fun readLine(inp: BufferedInputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val b = inp.read()
            if (b < 0) return if (sb.isEmpty()) null else sb.toString()
            if (b == '\n'.code) return sb.toString().trimEnd('\r')
            sb.append(b.toChar())
            if (sb.length > 8192) return sb.toString()
        }
    }

    private fun handle(c: Socket) {
        c.soTimeout = 30_000
        val inp = BufferedInputStream(c.getInputStream())
        val out = c.getOutputStream()
        val req = readLine(inp) ?: return
        val headers = HashMap<String, String>()
        while (true) {
            val l = readLine(inp) ?: break
            if (l.isEmpty()) break
            val i = l.indexOf(':'); if (i > 0) headers[l.substring(0, i).trim().lowercase(Locale.ROOT)] = l.substring(i + 1).trim()
        }
        val parts = req.split(' ')
        val method = parts.getOrNull(0) ?: return
        val path = parts.getOrNull(1) ?: return
        val token = path.split('/').getOrNull(2)
        val f = token?.let { files[it] }
        if (f == null || !f.isFile) { write(out, "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"); return }
        val len = f.length()
        var start = 0L; var end = len - 1; var partial = false
        headers["range"]?.let { r ->
            Regex("""bytes=(\d*)-(\d*)""").find(r)?.let { m ->
                val a = m.groupValues[1]; val b = m.groupValues[2]
                if (a.isNotEmpty()) { start = a.toLong(); if (b.isNotEmpty()) end = b.toLong().coerceAtMost(len - 1) }
                else if (b.isNotEmpty()) { start = (len - b.toLong()).coerceAtLeast(0) }
                partial = true
            }
        }
        if (start > end || start >= len) {
            write(out, "HTTP/1.1 416 Range Not Satisfiable\r\nContent-Range: bytes */$len\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"); return
        }
        val mime = mimeOf(f)
        val isImage = mime.startsWith("image/")
        val count = end - start + 1
        val sb = StringBuilder()
        sb.append(if (partial) "HTTP/1.1 206 Partial Content\r\n" else "HTTP/1.1 200 OK\r\n")
        sb.append("Content-Type: $mime\r\n")
        sb.append("Content-Length: $count\r\n")
        sb.append("Accept-Ranges: bytes\r\n")
        if (partial) sb.append("Content-Range: bytes $start-$end/$len\r\n")
        sb.append("transferMode.dlna.org: ${if (isImage) "Interactive" else "Streaming"}\r\n")
        sb.append("contentFeatures.dlna.org: DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000\r\n")
        sb.append("Access-Control-Allow-Origin: *\r\n")
        sb.append("Connection: close\r\n\r\n")
        write(out, sb.toString())
        if (method == "HEAD") return
        RandomAccessFile(f, "r").use { raf ->
            raf.seek(start)
            val buf = ByteArray(64 * 1024)
            var left = count
            while (left > 0) {
                val n = raf.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                if (n <= 0) break
                out.write(buf, 0, n); left -= n
            }
            out.flush()
        }
    }

    private fun write(o: OutputStream, s: String) { o.write(s.toByteArray(Charsets.UTF_8)); o.flush() }
}

/** Una pantalla a la que se puede mandar: TV con DLNA (LG, Samsung, Sony…) o Chromecast/Google TV. */
sealed class CastTarget(val name: String, val detail: String) {
    class Dlna(name: String, detail: String, val avControl: String, val rcControl: String?) : CastTarget(name, detail)
    class Chromecast(name: String, detail: String, val routeId: String) : CastTarget(name, detail)
}

object CastManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var app: Context

    val targets = mutableStateListOf<CastTarget>()
    var searching by mutableStateOf(false); private set

    // lo que se está mandando
    var target by mutableStateOf<CastTarget?>(null); private set
    var file by mutableStateOf<File?>(null); private set
    var siblings: List<File> = emptyList(); private set
    var playing by mutableStateOf(false); private set
    var position by mutableStateOf(0L); private set
    var duration by mutableStateOf(0L); private set
    var volume by mutableStateOf(-1); private set
    var status by mutableStateOf<String?>(null); private set
    var expanded by mutableStateOf(false)
    val active get() = target != null && file != null

    private var poll: Job? = null
    private var router: MediaRouter? = null
    private var pendingCast: (() -> Unit)? = null

    fun init(c: Context) { app = c.applicationContext }

    // ───── buscar pantallas ─────

    private val routerCb = object : MediaRouter.Callback() {
        override fun onRouteAdded(r: MediaRouter, route: MediaRouter.RouteInfo) = refreshCastRoutes()
        override fun onRouteChanged(r: MediaRouter, route: MediaRouter.RouteInfo) = refreshCastRoutes()
        override fun onRouteRemoved(r: MediaRouter, route: MediaRouter.RouteInfo) = refreshCastRoutes()
    }

    private fun castSelector() = MediaRouteSelector.Builder()
        .addControlCategory(CastMediaControlIntent.categoryForCast(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)).build()

    private fun refreshCastRoutes() {
        val r = router ?: return
        val sel = castSelector()
        val found = r.routes.filter { !it.isDefault && it.matchesSelector(sel) && it.isEnabled }
            .map { CastTarget.Chromecast(it.name, it.description ?: "Chromecast / Google TV", it.id) }
        targets.removeAll { it is CastTarget.Chromecast }
        targets.addAll(found)
    }

    fun startDiscovery() {
        if (searching) return
        searching = true
        targets.clear()
        // Chromecast (Google Cast)
        runCatching {
            CastContext.getSharedInstance(app)
            val r = MediaRouter.getInstance(app); router = r
            r.addCallback(castSelector(), routerCb, MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN)
            refreshCastRoutes()
        }
        // DLNA (LG y casi todas las smart TV)
        scope.launch {
            repeat(2) {
                val found = withContext(Dispatchers.IO) { runCatching { Dlna.discover(app) }.getOrDefault(emptyList()) }
                for (t in found) if (targets.none { it is CastTarget.Dlna && it.avControl == t.avControl }) targets += t
            }
            searching = false
        }
    }

    fun stopDiscovery() {
        runCatching { router?.removeCallback(routerCb) }
        searching = false
    }

    // ───── mandar ─────

    fun send(t: CastTarget, f: File, list: List<File> = listOf(f)) {
        stopDiscovery()
        siblings = list
        status = "Conectando con ${t.name}…"
        val old = target
        target = t; file = f; playing = false; position = 0; duration = 0; expanded = true
        // en el teléfono se pausa lo que sonaba
        runCatching { VideoSession.player?.pause() }
        AppNav.pauseMusic?.invoke()
        scope.launch {
            try {
                if (old is CastTarget.Dlna && old !== t) runCatching { withContext(Dispatchers.IO) { Dlna.stop(old) } }
                when (t) {
                    is CastTarget.Dlna -> withContext(Dispatchers.IO) { Dlna.play(t, f) }
                    is CastTarget.Chromecast -> castLoad(t, f)
                }
                status = null
                startPolling()
            } catch (e: Throwable) {
                status = "No se pudo: ${e.message ?: "la pantalla no respondió"}"
            }
        }
    }

    /** Fotos: manda la siguiente o la anterior de la misma carpeta. */
    fun step(delta: Int) {
        val t = target ?: return; val f = file ?: return
        val i = siblings.indexOf(f); val n = siblings.getOrNull(i + delta) ?: return
        send(t, n, siblings)
    }

    fun togglePlay() {
        val t = target ?: return
        scope.launch {
            runCatching {
                when (t) {
                    is CastTarget.Dlna -> withContext(Dispatchers.IO) { if (playing) Dlna.action(t, "Pause") else Dlna.action(t, "Play", "<Speed>1</Speed>") }
                    is CastTarget.Chromecast -> currentCast()?.remoteMediaClient?.let { if (it.isPlaying) it.pause() else it.play() }
                }
                playing = !playing
            }
        }
    }

    fun seek(ms: Long) {
        val t = target ?: return
        position = ms
        scope.launch {
            runCatching {
                when (t) {
                    is CastTarget.Dlna -> withContext(Dispatchers.IO) { Dlna.seek(t, ms) }
                    is CastTarget.Chromecast -> currentCast()?.remoteMediaClient?.seek(
                        com.google.android.gms.cast.MediaSeekOptions.Builder().setPosition(ms).build())
                }
            }
        }
    }

    fun changeVolume(delta: Int) {
        val t = target ?: return
        scope.launch {
            runCatching {
                when (t) {
                    is CastTarget.Dlna -> withContext(Dispatchers.IO) {
                        val v = (if (volume < 0) Dlna.getVolume(t) else volume) + delta
                        Dlna.setVolume(t, v.coerceIn(0, 100)); volume = v.coerceIn(0, 100)
                    }
                    is CastTarget.Chromecast -> currentCast()?.let { s ->
                        val v = (s.volume + delta / 100.0).coerceIn(0.0, 1.0); s.volume = v; volume = (v * 100).toInt()
                    }
                }
            }
        }
    }

    fun stop() {
        val t = target
        poll?.cancel()
        target = null; file = null; playing = false; status = null; expanded = false
        scope.launch {
            runCatching {
                when (t) {
                    is CastTarget.Dlna -> withContext(Dispatchers.IO) { Dlna.stop(t) }
                    is CastTarget.Chromecast -> CastContext.getSharedInstance(app).sessionManager.endCurrentSession(true)
                    null -> {}
                }
            }
        }
    }

    private fun startPolling() {
        poll?.cancel()
        poll = scope.launch {
            var wasPlaying = false
            while (isActive && target != null) {
                val t = target ?: break
                runCatching {
                    when (t) {
                        is CastTarget.Dlna -> {
                            val (state, pos, dur) = withContext(Dispatchers.IO) { Dlna.status(t) }
                            playing = state == "PLAYING" || state == "TRANSITIONING"
                            if (dur > 0) duration = dur
                            position = pos
                            if (playing) wasPlaying = true
                            // terminó: no se manda nada más (solo lo que tú elegiste)
                            if (state == "STOPPED" && wasPlaying) { playing = false; position = duration }
                        }
                        is CastTarget.Chromecast -> currentCast()?.remoteMediaClient?.let { c ->
                            playing = c.isPlaying; position = c.approximateStreamPosition; duration = c.streamDuration.coerceAtLeast(0)
                        }
                    }
                }
                delay(1000)
            }
        }
    }

    // ───── Chromecast ─────

    private fun currentCast(): CastSession? = runCatching { CastContext.getSharedInstance(app).sessionManager.currentCastSession }.getOrNull()

    private val sessionListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarted(s: CastSession, id: String) { pendingCast?.invoke(); pendingCast = null }
        override fun onSessionResumed(s: CastSession, wasSuspended: Boolean) { pendingCast?.invoke(); pendingCast = null }
        override fun onSessionStartFailed(s: CastSession, error: Int) { status = "La pantalla no aceptó la conexión ($error)"; pendingCast = null }
        override fun onSessionEnded(s: CastSession, error: Int) { if (target is CastTarget.Chromecast) { target = null; file = null; poll?.cancel() } }
        override fun onSessionStarting(s: CastSession) {}
        override fun onSessionEnding(s: CastSession) {}
        override fun onSessionResuming(s: CastSession, id: String) {}
        override fun onSessionResumeFailed(s: CastSession, error: Int) {}
        override fun onSessionSuspended(s: CastSession, reason: Int) {}
    }
    private var listenerAdded = false

    private fun castLoad(t: CastTarget.Chromecast, f: File) {
        val ctx = CastContext.getSharedInstance(app)
        if (!listenerAdded) { ctx.sessionManager.addSessionManagerListener(sessionListener, CastSession::class.java); listenerAdded = true }
        val load = {
            val session = ctx.sessionManager.currentCastSession
            val client = session?.remoteMediaClient
            if (client == null) status = "No se pudo conectar"
            else {
                val mime = mimeOf(f)
                val kind = when { mime.startsWith("image/") -> MediaMetadata.MEDIA_TYPE_PHOTO; mime.startsWith("audio/") -> MediaMetadata.MEDIA_TYPE_MUSIC_TRACK
                    else -> MediaMetadata.MEDIA_TYPE_MOVIE }
                val meta = MediaMetadata(kind).apply { putString(MediaMetadata.KEY_TITLE, f.nameWithoutExtension) }
                val info = MediaInfo.Builder(MediaServer.urlFor(f)).setContentType(mime)
                    .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED).setMetadata(meta).build()
                // un solo elemento, sin cola: termina y ya
                client.load(MediaLoadRequestData.Builder().setMediaInfo(info).setAutoplay(true).build())
                status = null
            }
        }
        val r = router ?: MediaRouter.getInstance(app).also { router = it }
        val route = r.routes.firstOrNull { it.id == t.routeId }
        if (ctx.sessionManager.currentCastSession?.castDevice?.friendlyName == t.name) load()
        else {
            pendingCast = load
            if (route != null) r.selectRoute(route) else throw Exception("La pantalla ya no aparece")
        }
    }
}

/** DLNA / UPnP: el idioma que hablan las TV LG (webOS), Samsung, Sony, Hisense, etc. */
object Dlna {
    private const val AVT = "urn:schemas-upnp-org:service:AVTransport:1"
    private const val RC = "urn:schemas-upnp-org:service:RenderingControl:1"

    fun discover(context: Context): List<CastTarget.Dlna> {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val lock = wifi.createMulticastLock("tony-cast").apply { setReferenceCounted(false); acquire() }
        val locations = LinkedHashSet<String>()
        try {
            MulticastSocket(null).use { sock ->
                sock.reuseAddress = true
                sock.bind(java.net.InetSocketAddress(0))
                sock.soTimeout = 700
                val group = InetAddress.getByName("239.255.255.250")
                for (st in listOf("urn:schemas-upnp-org:device:MediaRenderer:1", AVT)) {
                    val msg = "M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\nST: $st\r\n\r\n".toByteArray()
                    repeat(2) { sock.send(DatagramPacket(msg, msg.size, group, 1900)) }
                }
                val end = System.currentTimeMillis() + 3500
                val buf = ByteArray(4096)
                while (System.currentTimeMillis() < end) {
                    val pk = DatagramPacket(buf, buf.size)
                    try { sock.receive(pk) } catch (e: SocketTimeoutException) { continue }
                    val txt = String(pk.data, 0, pk.length)
                    Regex("""(?im)^location:\s*(\S+)""").find(txt)?.let { locations += it.groupValues[1] }
                }
            }
        } finally { runCatching { lock.release() } }
        return locations.mapNotNull { loc -> runCatching { describe(loc) }.getOrNull() }
    }

    private fun tag(xml: String, name: String) = Regex("""<(?:\w+:)?$name>([\s\S]*?)</(?:\w+:)?$name>""").find(xml)?.groupValues?.get(1)?.trim()

    private fun describe(location: String): CastTarget.Dlna? {
        val xml = httpGet(location)
        val name = tag(xml, "friendlyName")?.let { unescape(it) } ?: "TV"
        val model = listOfNotNull(tag(xml, "manufacturer"), tag(xml, "modelName")).joinToString(" ").let(::unescape)
        val base = tag(xml, "URLBase") ?: location
        var av: String? = null; var rc: String? = null
        Regex("""<(?:\w+:)?service>([\s\S]*?)</(?:\w+:)?service>""").findAll(xml).forEach { m ->
            val s = m.groupValues[1]
            val type = tag(s, "serviceType") ?: return@forEach
            val ctl = tag(s, "controlURL") ?: return@forEach
            if (type.contains("AVTransport")) av = resolve(base, ctl)
            if (type.contains("RenderingControl")) rc = resolve(base, ctl)
        }
        return av?.let { CastTarget.Dlna(name, model.ifBlank { "Pantalla DLNA" }, it, rc) }
    }

    private fun resolve(base: String, rel: String) = URL(URL(base), rel).toString()
    private fun unescape(s: String) = s.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'")
    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun httpGet(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 3000; c.readTimeout = 4000
        return c.inputStream.bufferedReader().use { it.readText() }.also { c.disconnect() }
    }

    private fun soap(url: String, service: String, action: String, args: String): String {
        val body = """<?xml version="1.0" encoding="utf-8"?><s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/"><s:Body><u:$action xmlns:u="$service">$args</u:$action></s:Body></s:Envelope>"""
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 4000; c.readTimeout = 8000
        c.requestMethod = "POST"; c.doOutput = true
        c.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"")
        c.setRequestProperty("SOAPACTION", "\"$service#$action\"")
        c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val code = c.responseCode
        val txt = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        c.disconnect()
        if (code !in 200..299) throw Exception("la TV respondió error $code" + (tag(txt, "errorDescription")?.let { ": $it" } ?: ""))
        return txt
    }

    fun action(t: CastTarget.Dlna, name: String, extra: String = "") = soap(t.avControl, AVT, name, "<InstanceID>0</InstanceID>$extra")

    fun play(t: CastTarget.Dlna, f: File) {
        val url = MediaServer.urlFor(f)
        val mime = mimeOf(f)
        val cls = when { mime.startsWith("image/") -> "object.item.imageItem.photo"; mime.startsWith("audio/") -> "object.item.audioItem.musicTrack"
            else -> "object.item.videoItem" }
        val flags = "DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000"
        val didl = """<DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/" xmlns:dlna="urn:schemas-dlna-org:metadata-1-0/"><item id="tony" parentID="-1" restricted="1"><dc:title>${esc(f.nameWithoutExtension)}</dc:title><upnp:class>$cls</upnp:class><res protocolInfo="http-get:*:$mime:$flags" size="${f.length()}">${esc(url)}</res></item></DIDL-Lite>"""
        runCatching { action(t, "Stop") }
        action(t, "SetAVTransportURI", "<CurrentURI>${esc(url)}</CurrentURI><CurrentURIMetaData>${esc(didl)}</CurrentURIMetaData>")
        runCatching { action(t, "Play", "<Speed>1</Speed>") }.onFailure {
            Thread.sleep(800); action(t, "Play", "<Speed>1</Speed>")
        }
    }

    fun stop(t: CastTarget.Dlna) { action(t, "Stop") }

    private fun parseTime(s: String?): Long {
        if (s == null || !s.contains(':')) return 0
        val p = s.split(':'); if (p.size < 3) return 0
        return ((p[0].toLongOrNull() ?: 0) * 3600 + (p[1].toLongOrNull() ?: 0) * 60) * 1000 + ((p[2].toDoubleOrNull() ?: 0.0) * 1000).toLong()
    }
    private fun fmt(ms: Long): String { val s = ms / 1000; return "%d:%02d:%02d".format(s / 3600, (s / 60) % 60, s % 60) }

    fun seek(t: CastTarget.Dlna, ms: Long) { action(t, "Seek", "<Unit>REL_TIME</Unit><Target>${fmt(ms)}</Target>") }

    /** Estado, posición y duración. */
    fun status(t: CastTarget.Dlna): Triple<String, Long, Long> {
        val st = tag(action(t, "GetTransportInfo"), "CurrentTransportState") ?: ""
        val pi = action(t, "GetPositionInfo")
        return Triple(st, parseTime(tag(pi, "RelTime")), parseTime(tag(pi, "TrackDuration")))
    }

    fun getVolume(t: CastTarget.Dlna): Int = t.rcControl?.let {
        tag(soap(it, RC, "GetVolume", "<InstanceID>0</InstanceID><Channel>Master</Channel>"), "CurrentVolume")?.toIntOrNull()
    } ?: 20

    fun setVolume(t: CastTarget.Dlna, v: Int) {
        t.rcControl?.let { soap(it, RC, "SetVolume", "<InstanceID>0</InstanceID><Channel>Master</Channel><DesiredVolume>$v</DesiredVolume>") }
    }
}

@Suppress("unused") private fun keepUri(u: Uri) = u
