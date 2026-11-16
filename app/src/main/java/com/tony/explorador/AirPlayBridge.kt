package com.tony.explorador

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/**
 * Bocinas AirPlay (HomePod, Bose, etc.) a través de OwnTone en el NAS.
 * El teléfono le da al NAS la dirección de la canción (el mismo mini servidor
 * que usa la tele) y OwnTone la toca en la bocina elegida.
 */
object AirPlayBridge {
    const val PORT = 3689
    /** Pista para diagnosticar: IP del teléfono y cuántos equipos revisó. */
    @Volatile var lastInfo = ""

    private fun prefs(c: Context) = c.getSharedPreferences("airplay", Context.MODE_PRIVATE)

    /** Busca OwnTone: la última dirección que funcionó, la del NAS (sincronizar), lo que anuncie la red y, si no, toda la red. */
    suspend fun findHost(c: Context): String? {
        val hosts = LinkedHashSet<String>()
        prefs(c).getString("host", null)?.let { hosts += it }
        runCatching { URI(NasSync.url.trim()).host }.getOrNull()?.takeIf { it.isNotBlank() }?.let { hosts += it }
        hosts += nsdHosts(c)
        for (h in hosts) if (isOwnTone(h)) { remember(c, h); return h }
        val open = scanSubnet()
        for (h in open) if (h !in hosts && isOwnTone(h)) { remember(c, h); return h }
        lastInfo = "tu IP ${MediaServer.localIp() ?: "desconocida"}, ${open.size} con puerto 3689"
        return null
    }

    suspend fun discover(c: Context): List<CastTarget.AirPlay> {
        val h = findHost(c) ?: return emptyList()
        return runCatching { outputs(h) }.getOrDefault(emptyList())
    }

    fun savedHost(c: Context): String? = prefs(c).getString("host", null)
    fun saveHost(c: Context, h: String) = remember(c, h)
    private fun remember(c: Context, h: String) { prefs(c).edit().putString("host", h).apply() }
    private fun isOwnTone(h: String) = runCatching { JSONObject(http("GET", h, "/api/config")).has("version") }.getOrDefault(false)

    private suspend fun scanSubnet(): List<String> = coroutineScope {
        val ip = MediaServer.localIp() ?: return@coroutineScope emptyList()
        val base = ip.substringBeforeLast('.')
        (1..254).map { i ->
            async(Dispatchers.IO) {
                val h = "$base.$i"
                if (h == ip) null else runCatching {
                    Socket().use { s -> s.connect(InetSocketAddress(h, PORT), 400) }; h
                }.getOrNull()
            }
        }.awaitAll().filterNotNull()
    }

    private suspend fun nsdHosts(c: Context): List<String> {
        val nsd = c.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return emptyList()
        val found = ConcurrentHashMap.newKeySet<String>()
        val listener = object : NsdManager.DiscoveryListener {
            override fun onServiceFound(s: NsdServiceInfo) {
                @Suppress("DEPRECATION")
                runCatching {
                    nsd.resolveService(s, object : NsdManager.ResolveListener {
                        override fun onServiceResolved(r: NsdServiceInfo) { r.host?.hostAddress?.let { if (!it.contains(':')) found += it } }
                        override fun onResolveFailed(r: NsdServiceInfo, e: Int) {}
                    })
                }
            }
            override fun onDiscoveryStarted(t: String) {}
            override fun onDiscoveryStopped(t: String) {}
            override fun onServiceLost(s: NsdServiceInfo) {}
            override fun onStartDiscoveryFailed(t: String, e: Int) {}
            override fun onStopDiscoveryFailed(t: String, e: Int) {}
        }
        runCatching { nsd.discoverServices("_daap._tcp", NsdManager.PROTOCOL_DNS_SD, listener) }
        withTimeoutOrNull(3500) { while (true) { delay(300); if (found.isNotEmpty()) { delay(600); break } } }
        runCatching { nsd.stopServiceDiscovery(listener) }
        return found.toList()
    }

    private fun outputs(host: String): List<CastTarget.AirPlay> {
        val arr = JSONObject(http("GET", host, "/api/outputs")).optJSONArray("outputs") ?: JSONArray()
        val list = mutableListOf<CastTarget.AirPlay>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val type = o.optString("type")
            val name = o.optString("name")
            // solo AirPlay; los que terminan en «+» son copias que crea AirConnect
            if (!type.startsWith("AirPlay") || name.endsWith("+")) continue
            list += CastTarget.AirPlay(name, "vía NAS", host, o.optString("id"))
        }
        return list
    }

    // ───── controles ─────

    fun play(t: CastTarget.AirPlay, f: File) {
        http("PUT", t.host, "/api/player/stop")
        http("PUT", t.host, "/api/outputs/set", JSONObject().put("outputs", JSONArray().put(t.outputId)).toString())
        val url = URLEncoder.encode(MediaServer.urlFor(f), "UTF-8")
        http("POST", t.host, "/api/queue/items/add?clear=true&playback=start&uris=$url")
    }

    fun pause(t: CastTarget.AirPlay) { http("PUT", t.host, "/api/player/pause") }
    fun resume(t: CastTarget.AirPlay) { http("PUT", t.host, "/api/player/play") }
    fun stop(t: CastTarget.AirPlay) { http("PUT", t.host, "/api/player/stop") }
    fun seek(t: CastTarget.AirPlay, ms: Long) { http("PUT", t.host, "/api/player/seek?position_ms=$ms") }

    fun getVolume(t: CastTarget.AirPlay): Int =
        JSONObject(http("GET", t.host, "/api/outputs/${t.outputId}")).optInt("volume", 50)

    fun setVolume(t: CastTarget.AirPlay, v: Int) {
        http("PUT", t.host, "/api/player/volume?volume=${v.coerceIn(0, 100)}&output_id=${t.outputId}")
    }

    /** (estado, posición, duración): estado = play / pause / stop */
    fun status(t: CastTarget.AirPlay): Triple<String, Long, Long> {
        val p = JSONObject(http("GET", t.host, "/api/player"))
        return Triple(p.optString("state"), p.optLong("item_progress_ms"), p.optLong("item_length_ms"))
    }

    /** Llamada directa a la API de OwnTone (la usa también el control remoto). */
    fun http(method: String, host: String, path: String, body: String? = null): String {
        val c = URL("http://$host:$PORT$path").openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = 2500; c.readTimeout = 6000
        if (body != null) {
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.outputStream.use { it.write(body.toByteArray()) }
        }
        val code = c.responseCode
        val text = (if (code < 400) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        c.disconnect()
        if (code >= 400) throw Exception("el NAS respondió $code")
        return text.ifBlank { "{}" }
    }
}
