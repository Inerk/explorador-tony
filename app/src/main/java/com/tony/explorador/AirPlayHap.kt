package com.tony.explorador

import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.digests.SHA512Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.modes.ChaCha20Poly1305
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import java.math.BigInteger
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID

/*
 * AirPlay de video para Apple TV (sin nada de Android, para poder probarlo aparte).
 * Pareo HAP con el código de 4 números, verificación en cada conexión y canal cifrado;
 * luego se le pide a la Apple TV que reproduzca una URL del servidor del teléfono.
 */

/** Quién es este teléfono para la Apple TV: llave fija y un id. */
class HapIdentity(val seed: ByteArray, val pairingId: ByteArray) {
    private val key = Ed25519PrivateKeyParameters(seed, 0)
    val publicKey: ByteArray = key.generatePublicKey().encoded
    fun sign(msg: ByteArray): ByteArray = Ed25519Signer().run { init(true, key); update(msg, 0, msg.size); generateSignature() }

    companion object {
        fun create() = HapIdentity(ByteArray(32).also { SecureRandom().nextBytes(it) }, UUID.randomUUID().toString().uppercase().toByteArray())
    }
}

class HapException(msg: String, val notPaired: Boolean = false) : Exception(msg)

object Tlv8 {
    const val METHOD = 0; const val ID = 1; const val SALT = 2; const val PUBKEY = 3; const val PROOF = 4
    const val ENCRYPTED = 5; const val STATE = 6; const val ERROR = 7; const val SIGNATURE = 10

    fun enc(vararg items: Pair<Int, ByteArray>): ByteArray {
        val o = ByteArrayOutputStream()
        for ((t, v) in items) {
            if (v.isEmpty()) { o.write(t); o.write(0); continue }
            var i = 0
            while (i < v.size) { val n = minOf(255, v.size - i); o.write(t); o.write(n); o.write(v, i, n); i += n }
        }
        return o.toByteArray()
    }

    fun dec(b: ByteArray): Map<Int, ByteArray> {
        val m = LinkedHashMap<Int, ByteArrayOutputStream>()
        var i = 0; var last = -1
        while (i + 1 < b.size) {
            val t = b[i].toInt() and 0xff; val n = b[i + 1].toInt() and 0xff
            val bos = if (t == last) m.getValue(t) else ByteArrayOutputStream().also { m[t] = it }
            bos.write(b, i + 2, minOf(n, b.size - i - 2)); i += 2 + n; last = t
        }
        return m.mapValues { it.value.toByteArray() }
    }
}

internal object HapCrypto {
    fun sha512(vararg parts: ByteArray): ByteArray = MessageDigest.getInstance("SHA-512").run { parts.forEach { update(it) }; digest() }

    fun hkdf(ikm: ByteArray, salt: String, info: String): ByteArray {
        val g = HKDFBytesGenerator(SHA512Digest())
        g.init(HKDFParameters(ikm, salt.toByteArray(), info.toByteArray()))
        return ByteArray(32).also { g.generateBytes(it, 0, 32) }
    }

    fun nonce(s: String) = ByteArray(4) + s.toByteArray()
    fun nonce(counter: Long) = ByteArray(12).also { for (i in 0 until 8) it[4 + i] = (counter ushr (8 * i)).toByte() }

    private fun aead(enc: Boolean, key: ByteArray, nonce: ByteArray, data: ByteArray, aad: ByteArray?): ByteArray {
        val c = ChaCha20Poly1305()
        c.init(enc, AEADParameters(KeyParameter(key), 128, nonce, aad))
        val out = ByteArray(c.getOutputSize(data.size))
        var n = c.processBytes(data, 0, data.size, out, 0)
        n += c.doFinal(out, n)
        return out.copyOf(n)
    }
    fun seal(key: ByteArray, nonce: ByteArray, plain: ByteArray, aad: ByteArray? = null) = aead(true, key, nonce, plain, aad)
    fun open(key: ByteArray, nonce: ByteArray, data: ByteArray, aad: ByteArray? = null) = aead(false, key, nonce, data, aad)
}

/** SRP-6a (3072 bits, SHA-512) tal como lo usa Apple para el pareo con código. */
class SrpClient(private val user: String, private val pass: String, aSeed: ByteArray? = null) {
    companion object {
        val N = BigInteger(
            "FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD129024E088A67CC74020BBEA63B139B22514A08798E3404DD" +
            "EF9519B3CD3A431B302B0A6DF25F14374FE1356D6D51C245E485B576625E7EC6F44C42E9A637ED6B0BFF5CB6F406B7ED" +
            "EE386BFB5A899FA5AE9F24117C4B1FE649286651ECE45B3DC2007CB8A163BF0598DA48361C55D39A69163FA8FD24CF5F" +
            "83655D23DCA3AD961C62F356208552BB9ED529077096966D670C354E4ABC9804F1746C08CA18217C32905E462E36CE3B" +
            "E39E772C180E86039B2783A2EC07A28FB5C55DF06F4C52C9DE2BCBF6955817183995497CEA956AE515D2261898FA0510" +
            "15728E5A8AAAC42DAD33170D04507A33A85521ABDF1CBA64ECFB850458DBEF0A8AEA71575D060C7DB3970F85A6E1E4C7" +
            "ABF5AE8CDB0933D71E8C94E04A25619DCEE3D2261AD2EE6BF12FFA06D98A0864D87602733EC86A64521F2B18177B200C" +
            "BBE117577A615D6C770988C0BAD946E208E24FA074E5AB3143DB5BFCE0FD108E4B82D120A93AD2CAFFFFFFFFFFFFFFFF", 16)
        val G: BigInteger = BigInteger.valueOf(5)
        private val N_LEN = (N.bitLength() + 7) / 8

        fun bytes(x: BigInteger): ByteArray { val b = x.toByteArray(); return if (b.size > 1 && b[0] == 0.toByte()) b.copyOfRange(1, b.size) else b }
        fun pad(x: BigInteger): ByteArray { val b = bytes(x); return if (b.size >= N_LEN) b else ByteArray(N_LEN - b.size) + b }
    }

    private val a = BigInteger(1, aSeed ?: ByteArray(32).also { SecureRandom().nextBytes(it) })
    val publicA: BigInteger = G.modPow(a, N)
    lateinit var sessionKey: ByteArray; private set
    lateinit var proof: ByteArray; private set

    fun process(salt: ByteArray, serverB: ByteArray) {
        val b = BigInteger(1, serverB)
        if (b.mod(N) == BigInteger.ZERO) throw HapException("Respuesta inválida de la Apple TV")
        fun h(vararg p: ByteArray) = HapCrypto.sha512(*p)
        val k = BigInteger(1, h(bytes(N), pad(G)))
        val u = BigInteger(1, h(pad(publicA), pad(b)))
        val x = BigInteger(1, h(salt, h("$user:$pass".toByteArray())))
        val s = b.subtract(k.multiply(G.modPow(x, N))).mod(N).modPow(a.add(u.multiply(x)), N)
        sessionKey = h(bytes(s))
        val hn = h(bytes(N)); val hg = h(bytes(G))
        val xor = ByteArray(hn.size) { (hn[it].toInt() xor hg[it].toInt()).toByte() }
        proof = h(xor, h(user.toByteArray()), salt, bytes(publicA), bytes(b), sessionKey)
    }
}

/** Una conexión HTTP con la Apple TV que se puede volver cifrada después de verificar. */
class AirPlayConn(host: String, port: Int) : Closeable {
    private val sock = Socket().apply { connect(InetSocketAddress(host, port), 5000); soTimeout = 15000; tcpNoDelay = true }
    private val rawIn: InputStream = sock.getInputStream().buffered()
    private val rawOut: OutputStream = sock.getOutputStream()
    private var writeKey: ByteArray? = null; private var readKey: ByteArray? = null
    private var wCount = 0L; private var rCount = 0L
    private var plainBuf = ByteArray(0); private var plainPos = 0

    fun encrypt(write: ByteArray, read: ByteArray) { writeKey = write; readKey = read; wCount = 0; rCount = 0 }

    private fun send(data: ByteArray) {
        val k = writeKey
        if (k == null) { rawOut.write(data); rawOut.flush(); return }
        var i = 0
        while (i < data.size) {
            val n = minOf(1024, data.size - i)
            val len = byteArrayOf((n and 0xff).toByte(), (n shr 8).toByte())
            rawOut.write(len)
            rawOut.write(HapCrypto.seal(k, HapCrypto.nonce(wCount++), data.copyOfRange(i, i + n), len))
            i += n
        }
        rawOut.flush()
    }

    private fun readFully(n: Int): ByteArray {
        val b = ByteArray(n); var off = 0
        while (off < n) { val r = rawIn.read(b, off, n - off); if (r < 0) throw HapException("La Apple TV cerró la conexión"); off += r }
        return b
    }

    private fun readByte(): Int {
        val k = readKey ?: return rawIn.read()
        if (plainPos >= plainBuf.size) {
            val len = readFully(2)
            val n = (len[0].toInt() and 0xff) or ((len[1].toInt() and 0xff) shl 8)
            plainBuf = HapCrypto.open(k, HapCrypto.nonce(rCount++), readFully(n + 16), len); plainPos = 0
        }
        return plainBuf[plainPos++].toInt() and 0xff
    }

    private fun readLine(): String {
        val sb = StringBuilder()
        while (true) {
            val c = readByte()
            if (c < 0) throw HapException("La Apple TV cerró la conexión")
            if (c == '\n'.code) return sb.toString().trimEnd('\r')
            sb.append(c.toChar())
        }
    }

    class Resp(val code: Int, val headers: Map<String, String>, val body: ByteArray) { val text get() = String(body) }

    @Synchronized
    fun request(method: String, path: String, headers: Map<String, String> = emptyMap(), body: ByteArray = ByteArray(0)): Resp {
        val sb = StringBuilder("$method $path HTTP/1.1\r\n")
        headers.forEach { (k, v) -> sb.append("$k: $v\r\n") }
        sb.append("Content-Length: ${body.size}\r\n\r\n")
        send(sb.toString().toByteArray() + body)
        val status = readLine()
        val code = status.split(' ').getOrNull(1)?.toIntOrNull() ?: throw HapException("Respuesta rara: $status")
        val h = HashMap<String, String>()
        while (true) {
            val l = readLine(); if (l.isEmpty()) break
            val i = l.indexOf(':'); if (i > 0) h[l.substring(0, i).trim().lowercase()] = l.substring(i + 1).trim()
        }
        val len = h["content-length"]?.toIntOrNull() ?: 0
        val out = ByteArray(len)
        for (i in 0 until len) { val c = readByte(); if (c < 0) throw HapException("Respuesta cortada"); out[i] = c.toByte() }
        return Resp(code, h, out)
    }

    override fun close() { runCatching { sock.close() } }
}

object AirPlayHap {
    private val PAIR_HEADERS = mapOf(
        "User-Agent" to "AirPlay/320.20", "Connection" to "keep-alive",
        "X-Apple-HKP" to "3", "Content-Type" to "application/octet-stream")

    private fun AirPlayConn.tlv(path: String, vararg items: Pair<Int, ByteArray>): Map<Int, ByteArray> {
        val r = request("POST", path, PAIR_HEADERS, Tlv8.enc(*items))
        if (r.code != 200) throw HapException("La Apple TV respondió ${r.code} en $path", notPaired = r.code == 470 || r.code == 403)
        return Tlv8.dec(r.body)
    }

    private fun Map<Int, ByteArray>.checkOk(what: String, notPaired: Boolean = false) {
        this[Tlv8.ERROR]?.let { e ->
            val code = e.firstOrNull()?.toInt() ?: 0
            throw HapException(if (code == 2) "El código no es correcto" else "$what (error $code)", notPaired)
        }
    }

    /** Pide a la Apple TV que enseñe el código de 4 números. */
    fun pinStart(host: String, port: Int) {
        AirPlayConn(host, port).use { it.request("POST", "/pair-pin-start", PAIR_HEADERS) }
    }

    /** Pareo con el código. Devuelve la llave pública de la Apple TV. */
    fun pairSetup(host: String, port: Int, pin: String, id: HapIdentity): ByteArray = AirPlayConn(host, port).use { c ->
        val m2 = c.tlv("/pair-setup", Tlv8.METHOD to byteArrayOf(0), Tlv8.STATE to byteArrayOf(1))
        m2.checkOk("La Apple TV no quiso parear")
        val srp = SrpClient("Pair-Setup", pin.trim())
        srp.process(m2.getValue(Tlv8.SALT), m2.getValue(Tlv8.PUBKEY))
        val m4 = c.tlv("/pair-setup", Tlv8.STATE to byteArrayOf(3), Tlv8.PUBKEY to SrpClient.bytes(srp.publicA), Tlv8.PROOF to srp.proof)
        m4.checkOk("El código no es correcto")
        val signX = HapCrypto.hkdf(srp.sessionKey, "Pair-Setup-Controller-Sign-Salt", "Pair-Setup-Controller-Sign-Info")
        val key = HapCrypto.hkdf(srp.sessionKey, "Pair-Setup-Encrypt-Salt", "Pair-Setup-Encrypt-Info")
        val sig = id.sign(signX + id.pairingId + id.publicKey)
        val inner = Tlv8.enc(Tlv8.ID to id.pairingId, Tlv8.PUBKEY to id.publicKey, Tlv8.SIGNATURE to sig)
        val m6 = c.tlv("/pair-setup", Tlv8.STATE to byteArrayOf(5), Tlv8.ENCRYPTED to HapCrypto.seal(key, HapCrypto.nonce("PS-Msg05"), inner))
        m6.checkOk("La Apple TV no aceptó el pareo")
        val sub = Tlv8.dec(HapCrypto.open(key, HapCrypto.nonce("PS-Msg06"), m6.getValue(Tlv8.ENCRYPTED)))
        sub[Tlv8.PUBKEY] ?: ByteArray(0)
    }

    /** Abre conexión verificada y cifrada (lista para /play). */
    fun connect(host: String, port: Int, id: HapIdentity): AirPlayConn {
        val c = AirPlayConn(host, port)
        try {
            val eph = X25519PrivateKeyParameters(SecureRandom())
            val ephPub = eph.generatePublicKey().encoded
            val m2 = c.tlv("/pair-verify", Tlv8.STATE to byteArrayOf(1), Tlv8.PUBKEY to ephPub)
            m2.checkOk("Hay que parear otra vez", notPaired = true)
            val accPub = m2.getValue(Tlv8.PUBKEY)
            val shared = ByteArray(32)
            X25519Agreement().apply { init(eph) }.calculateAgreement(X25519PublicKeyParameters(accPub, 0), shared, 0)
            val key = HapCrypto.hkdf(shared, "Pair-Verify-Encrypt-Salt", "Pair-Verify-Encrypt-Info")
            HapCrypto.open(key, HapCrypto.nonce("PV-Msg02"), m2.getValue(Tlv8.ENCRYPTED))
            val sig = id.sign(ephPub + id.pairingId + accPub)
            val inner = Tlv8.enc(Tlv8.ID to id.pairingId, Tlv8.SIGNATURE to sig)
            val m4 = c.tlv("/pair-verify", Tlv8.STATE to byteArrayOf(3), Tlv8.ENCRYPTED to HapCrypto.seal(key, HapCrypto.nonce("PV-Msg03"), inner))
            m4.checkOk("Hay que parear otra vez", notPaired = true)
            c.encrypt(HapCrypto.hkdf(shared, "Control-Salt", "Control-Write-Encryption-Key"),
                HapCrypto.hkdf(shared, "Control-Salt", "Control-Read-Encryption-Key"))
            return c
        } catch (e: Throwable) { c.close(); throw e }
    }

    private val MEDIA_HEADERS = mapOf("User-Agent" to "MediaControl/1.0", "X-Apple-ProtocolVersion" to "1")

    fun play(c: AirPlayConn, url: String, startSec: Double = 0.0) {
        val session = UUID.randomUUID().toString().uppercase()
        val body = BPlist.dict(linkedMapOf("Content-Location" to url, "Start-Position" to startSec, "X-Apple-Session-ID" to session))
        var r = c.request("POST", "/play", MEDIA_HEADERS + mapOf("Content-Type" to "application/x-apple-binary-plist", "X-Apple-Session-ID" to session), body)
        if (r.code == 500) { Thread.sleep(800); r = c.request("POST", "/play", MEDIA_HEADERS + mapOf("Content-Type" to "application/x-apple-binary-plist", "X-Apple-Session-ID" to session), body) }
        if (r.code !in 200..299) throw HapException("La Apple TV no quiso reproducir (${r.code})")
    }

    /** (posición, duración) en segundos. */
    fun scrub(c: AirPlayConn): Pair<Double, Double> {
        val t = c.request("GET", "/scrub", MEDIA_HEADERS).text
        fun v(k: String) = Regex("""$k:\s*([\d.]+)""").find(t)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
        return v("position") to v("duration")
    }
    /** Resumen corto de /playback-info (listo, error…) para diagnosticar. */
    fun playbackInfo(c: AirPlayConn): String {
        val r = c.request("GET", "/playback-info", MEDIA_HEADERS)
        if (r.code !in 200..299) return "playback-info ${r.code}"
        val m: Map<String, Any?> = if (r.body.size > 8 && String(r.body, 0, 6) == "bplist") BPlist.read(r.body) as? Map<String, Any?> ?: emptyMap()
            else Regex("""<key>([^<]+)</key>\s*<(\w+)(?:/>|>([^<]*)</\w+>)""").findAll(r.text).associate { it.groupValues[1] to (it.groupValues[3].ifEmpty { it.groupValues[2] }) }
        val keys = listOf("readyToPlay", "playbackBufferEmpty", "playbackLikelyToKeepUp", "rate", "duration", "error", "errorCode", "errorDomain", "status")
        val parts = keys.mapNotNull { k -> m[k]?.let { "$k=${it.toString().take(80)}" } }
        val err = m.entries.filter { it.key.contains("rror", true) && it.key !in keys }.map { "${it.key}=${it.value.toString().take(80)}" }
        return (parts + err).joinToString(" · ").ifBlank { "playback-info vacío" }
    }
    fun seek(c: AirPlayConn, sec: Double) { c.request("POST", "/scrub?position=%.3f".format(java.util.Locale.US, sec), MEDIA_HEADERS) }
    fun rate(c: AirPlayConn, r: Int) { c.request("POST", "/rate?value=$r.000000", MEDIA_HEADERS) }
    fun stop(c: AirPlayConn) { runCatching { c.request("POST", "/stop", MEDIA_HEADERS) }; c.close() }
}

/** Plist binario mínimo (diccionario de textos, números y sí/no). */
object BPlist {
    fun dict(m: Map<String, Any>): ByteArray {
        val objs = ArrayList<ByteArray>()
        val keys = m.keys.toList()
        val count = 1 + keys.size * 2
        objs += ByteArray(0) // lugar del diccionario
        val keyRefs = keys.map { objs += str(it); objs.size - 1 }
        val valRefs = keys.map { k -> objs += value(m.getValue(k)); objs.size - 1 }
        val d = ByteArrayOutputStream()
        if (keys.size < 15) d.write(0xD0 or keys.size) else { d.write(0xDF); d.write(intMarker(keys.size)) }
        keyRefs.forEach { d.write(it) }; valRefs.forEach { d.write(it) }
        objs[0] = d.toByteArray()
        val out = ByteArrayOutputStream(); out.write("bplist00".toByteArray())
        val offsets = objs.map { val o = out.size(); out.write(it); o }
        val tableAt = out.size()
        val offSize = if (tableAt < 256) 1 else if (tableAt < 65536) 2 else 4
        offsets.forEach { o -> for (i in offSize - 1 downTo 0) out.write((o shr (8 * i)) and 0xff) }
        out.write(ByteArray(6)); out.write(offSize); out.write(1)
        out.write(long8(count.toLong())); out.write(long8(0)); out.write(long8(tableAt.toLong()))
        return out.toByteArray()
    }
    /** Lector mínimo de plist binario (dict, arreglos, texto, números, sí/no). */
    fun read(b: ByteArray): Any? {
        val t = b.size - 32
        val offSize = b[t + 6].toInt() and 0xff; val refSize = b[t + 7].toInt() and 0xff
        fun num(at: Int, n: Int): Long { var v = 0L; for (i in 0 until n) v = (v shl 8) or (b[at + i].toLong() and 0xff); return v }
        val count = num(t + 8, 8).toInt(); val top = num(t + 16, 8).toInt(); val table = num(t + 24, 8).toInt()
        fun off(i: Int) = num(table + i * offSize, offSize).toInt()
        fun obj(i: Int, depth: Int): Any? {
            if (depth > 20 || i >= count) return null
            var p = off(i); val m = b[p].toInt() and 0xff; val hi = m shr 4; var lo = m and 0xf
            fun len(): Int { if (lo != 0xf) return lo; val im = b[p + 1].toInt() and 0xf; val n = 1 shl im; val v = num(p + 2, n).toInt(); p += 1 + n; return v }
            return when (hi) {
                0x0 -> when (m) { 0x08 -> false; 0x09 -> true; else -> null }
                0x1 -> num(p + 1, 1 shl lo)
                0x2 -> if (lo == 3) java.lang.Double.longBitsToDouble(num(p + 1, 8)) else java.lang.Float.intBitsToFloat(num(p + 1, 4).toInt()).toDouble()
                0x5 -> { val n = len(); String(b, p + 1, n, Charsets.US_ASCII) }
                0x6 -> { val n = len(); String(b, p + 1, n * 2, Charsets.UTF_16BE) }
                0xA -> { val n = len(); (0 until n).map { obj(num(p + 1 + it * refSize, refSize).toInt(), depth + 1) } }
                0xD -> { val n = len(); (0 until n).associate { k ->
                    obj(num(p + 1 + k * refSize, refSize).toInt(), depth + 1).toString() to obj(num(p + 1 + (n + k) * refSize, refSize).toInt(), depth + 1) } }
                else -> "?"
            }
        }
        return runCatching { obj(top, 0) }.getOrNull()
    }
    private fun long8(v: Long) = ByteArray(8) { ((v ushr (8 * (7 - it))) and 0xff).toByte() }
    private fun intMarker(n: Int) = if (n < 256) byteArrayOf(0x10, n.toByte()) else byteArrayOf(0x11, (n shr 8).toByte(), n.toByte())
    private fun str(s: String): ByteArray {
        val ascii = s.all { it.code < 128 }
        val data = if (ascii) s.toByteArray(Charsets.US_ASCII) else s.toByteArray(Charsets.UTF_16BE)
        val n = s.length
        val head = (if (ascii) 0x50 else 0x60)
        return (if (n < 15) byteArrayOf((head or n).toByte()) else byteArrayOf((head or 0x0F).toByte()) + intMarker(n)) + data
    }
    private fun value(v: Any): ByteArray = when (v) {
        is String -> str(v)
        is Boolean -> byteArrayOf(if (v) 0x09 else 0x08)
        is Double -> byteArrayOf(0x23) + long8(java.lang.Double.doubleToLongBits(v))
        is Float -> value(v.toDouble())
        is Int -> byteArrayOf(0x13) + long8(v.toLong())
        is Long -> byteArrayOf(0x13) + long8(v)
        else -> str(v.toString())
    }
}
