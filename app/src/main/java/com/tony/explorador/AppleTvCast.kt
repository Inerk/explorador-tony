package com.tony.explorador

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Base64
import java.net.Inet4Address
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Apple TV (y teles con AirPlay de video): buscarlas, parearlas una vez y mandarles videos. */
object AppleTvCast {
    private fun prefs(c: Context) = c.getSharedPreferences("airplay_video", Context.MODE_PRIVATE)

    fun identity(c: Context): HapIdentity {
        val p = prefs(c)
        val seed = p.getString("seed", null)?.let { Base64.decode(it, Base64.NO_WRAP) }
        val pid = p.getString("pid", null)
        if (seed != null && pid != null) return HapIdentity(seed, pid.toByteArray())
        val id = HapIdentity.create()
        p.edit().putString("seed", Base64.encodeToString(id.seed, Base64.NO_WRAP)).putString("pid", String(id.pairingId)).apply()
        return id
    }
    fun isPaired(c: Context, dev: String) = prefs(c).getBoolean("paired_$dev", false)
    fun markPaired(c: Context, dev: String) = prefs(c).edit().putBoolean("paired_$dev", true).apply()
    fun forget(c: Context, dev: String) = prefs(c).edit().remove("paired_$dev").apply()

    // ───── buscar ─────

    fun discover(c: Context, waitMs: Long = 3000): List<CastTarget.AppleTv> {
        val nsd = c.getSystemService(Context.NSD_SERVICE) as NsdManager
        val found = java.util.Collections.synchronizedList(ArrayList<NsdServiceInfo>())
        val listener = object : NsdManager.DiscoveryListener {
            override fun onServiceFound(s: NsdServiceInfo) { found += s }
            override fun onServiceLost(s: NsdServiceInfo) {}
            override fun onDiscoveryStarted(t: String) {}
            override fun onDiscoveryStopped(t: String) {}
            override fun onStartDiscoveryFailed(t: String, e: Int) {}
            override fun onStopDiscoveryFailed(t: String, e: Int) {}
        }
        runCatching { nsd.discoverServices("_airplay._tcp", NsdManager.PROTOCOL_DNS_SD, listener) }.onFailure { return emptyList() }
        Thread.sleep(waitMs)
        runCatching { nsd.stopServiceDiscovery(listener) }
        val out = ArrayList<CastTarget.AppleTv>()
        for (s in found.distinctBy { it.serviceName }) {
            val r = resolve(nsd, s) ?: continue
            val attrs = r.attributes.mapValues { String(it.value ?: ByteArray(0)) }
            val model = attrs["model"].orEmpty()
            if (model.contains("AudioAccessory") || model.contains("AirPort")) continue   // HomePod / bocinas
            val feats = attrs["features"].orEmpty().split(',').firstOrNull()?.removePrefix("0x")?.toLongOrNull(16) ?: 0L
            if (feats and 1L == 0L) continue                                                  // sin video
            @Suppress("DEPRECATION")
            val host = (if (Build.VERSION.SDK_INT >= 34) r.hostAddresses.firstOrNull { it is Inet4Address } ?: r.host else r.host)?.hostAddress ?: continue
            val dev = attrs["deviceid"]?.ifBlank { null } ?: r.serviceName
            val pretty = when { model.startsWith("AppleTV") -> "Apple TV"; else -> model.ifBlank { "AirPlay" } }
            out += CastTarget.AppleTv(r.serviceName, pretty, host, r.port, dev)
        }
        return out
    }

    @Suppress("DEPRECATION")
    private fun resolve(nsd: NsdManager, s: NsdServiceInfo): NsdServiceInfo? {
        val latch = CountDownLatch(1); var res: NsdServiceInfo? = null
        runCatching {
            nsd.resolveService(s, object : NsdManager.ResolveListener {
                override fun onResolveFailed(si: NsdServiceInfo, e: Int) { latch.countDown() }
                override fun onServiceResolved(si: NsdServiceInfo) { res = si; latch.countDown() }
            })
        }.onFailure { return null }
        latch.await(3, TimeUnit.SECONDS)
        return res
    }

    // ───── reproducir ─────

    private var conn: AirPlayConn? = null
    private var url: String? = null
    @Volatile var paused = false; private set

    fun start(t: CastTarget.AppleTv, id: HapIdentity, url: String) {
        close()
        val c = AirPlayHap.connect(t.host, t.port, id)
        conn = c; paused = false; this.url = url
        AirPlayHap.play(c, url)
        runCatching { AirPlayHap.rate(c, 1) }
    }
    fun status(): Pair<Long, Long> {
        val c = conn ?: throw HapException("Sin conexión con la Apple TV")
        val (p, d) = AirPlayHap.scrub(c)
        return (p * 1000).toLong() to (d * 1000).toLong()
    }
    /** Qué está pasando mientras la Apple TV carga (para saber dónde se atora). */
    fun loadingReport(secs: Int): String {
        val tok = url?.let { MediaServer.tokenOf(it) }
        val hits = tok?.let { MediaServer.hits[it] } ?: 0
        val mb = (tok?.let { MediaServer.sent[it] } ?: 0L) / 1_000_000.0
        val base = when {
            hits == 0 && secs >= 8 -> "La Apple TV no ha pedido el video al teléfono. Revisa que estén en el mismo wifi."
            hits == 0 -> "Esperando a que la Apple TV pida el video…"
            else -> "La Apple TV pidió el video $hits veces · %.1f MB enviados".format(java.util.Locale.US, mb)
        }
        if (secs < 12 || secs % 6 != 0) return base
        val info = runCatching { conn?.let { AirPlayHap.playbackInfo(it) } }.getOrNull().orEmpty()
        return if (info.isBlank()) base else "$base\n$info"
    }
    fun pause() { conn?.let { AirPlayHap.rate(it, 0) }; paused = true }
    fun resume() { conn?.let { AirPlayHap.rate(it, 1) }; paused = false }
    fun seek(ms: Long) { conn?.let { AirPlayHap.seek(it, ms / 1000.0) } }
    fun stop() { conn?.let { AirPlayHap.stop(it) }; conn = null }
    fun close() { conn?.close(); conn = null }
}
