package com.tony.explorador

import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object StoreUi { var open by mutableStateOf(false) }

/** Una app de la tienda: se publica en el NAS (version.json + APK) y se instala desde aquí. */
private class StoreApp(val name: String, val desc: String, val pkg: String, val server: String, val icon: Int?, val emoji: String)

private val STORE_APPS = listOf(
    StoreApp("Nebula Música", "Tu música sola, sin el explorador. Se actualiza desde el NAS.", "com.tony.explorador.musica",
        "http://192.168.3.74:8087", R.mipmap.ic_music_fg, "🎵"),
    StoreApp("Control Nebula", "Control remoto del NAS: bocinas, biblioteca y alarmas.", "com.tony.explorador.control",
        "http://192.168.3.74:8088", R.mipmap.ic_remote_fg, "🔊"),
)

private class Remote(val code: Long, val name: String, val apk: String, val notes: String, val size: Long)

private sealed interface Job {
    data class Downloading(val progress: Float) : Job
    data class Failed(val msg: String) : Job
}

private fun installedCode(c: Context, pkg: String): Long? = runCatching {
    PackageInfoCompat.getLongVersionCode(c.packageManager.getPackageInfo(pkg, 0))
}.getOrNull()

private fun installedName(c: Context, pkg: String): String? = runCatching { c.packageManager.getPackageInfo(pkg, 0).versionName }.getOrNull()

private suspend fun fetchRemote(a: StoreApp): Remote = withContext(Dispatchers.IO) {
    val conn = (URL("${a.server}/version.json").openConnection() as HttpURLConnection).apply { connectTimeout = 5_000; readTimeout = 8_000 }
    try {
        if (conn.responseCode != 200) error("el NAS respondió ${conn.responseCode}")
        val j = JSONObject(conn.inputStream.bufferedReader().readText())
        Remote(j.getLong("code"), j.optString("name", j.getLong("code").toString()), j.getString("apk"), j.optString("notas"), j.optLong("size"))
    } finally { conn.disconnect() }
}

private suspend fun download(c: Context, a: StoreApp, r: Remote, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
    val dir = File(c.cacheDir, "tienda").apply { mkdirs() }
    val out = File(dir, "${a.pkg}.apk"); out.delete()
    val conn = (URL("${a.server}/${r.apk}").openConnection() as HttpURLConnection).apply { connectTimeout = 10_000; readTimeout = 30_000 }
    try {
        if (conn.responseCode != 200) error("el NAS respondió ${conn.responseCode}")
        val total = conn.contentLengthLong.takeIf { it > 0 } ?: r.size
        conn.inputStream.use { input ->
            out.outputStream().use { o ->
                val buf = ByteArray(64 * 1024); var done = 0L; var n: Int
                while (input.read(buf).also { n = it } >= 0) {
                    o.write(buf, 0, n); done += n
                    if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                }
            }
        }
    } finally { conn.disconnect() }
    out
}

@Composable
fun NebulaStoreScreen(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val remote = remember { mutableStateMapOf<String, Remote>() }
    val errors = remember { mutableStateMapOf<String, String>() }
    val jobs = remember { mutableStateMapOf<String, Job>() }
    var refresh by remember { mutableIntStateOf(0) }
    var installedTick by remember { mutableIntStateOf(0) }
    BackHandler(onBack = onClose)

    LaunchedEffect(refresh) {
        STORE_APPS.forEach { a ->
            launch {
                runCatching { fetchRemote(a) }
                    .onSuccess { remote[a.pkg] = it; errors.remove(a.pkg) }
                    .onFailure { errors[a.pkg] = "No se pudo hablar con el NAS. ¿Estás en el Wi-Fi de la casa?" }
            }
        }
    }
    // al volver del instalador, se revisa otra vez qué hay instalado
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) { installedTick++; onPauseOrDispose { } }

    fun get(a: StoreApp, r: Remote) {
        if (!canInstallApks(ctx)) { openInstallPermissionSettings(ctx); return }
        jobs[a.pkg] = Job.Downloading(0f)
        scope.launch {
            runCatching { download(ctx, a, r) { p -> jobs[a.pkg] = Job.Downloading(p) } }
                .onSuccess { f -> jobs.remove(a.pkg); installApk(ctx, f) }
                .onFailure { jobs[a.pkg] = Job.Failed("No se pudo bajar: ${it.message}") }
        }
    }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF2A1B4E), Color(0xFF120D26))))) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás", tint = Color.White, modifier = Modifier.size(44.dp).clip(CircleShape).clickable { onClose() }.padding(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Tienda Nebula", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black)
                    Text("Apps de tu NAS", color = Color(0xFFB9AEDB), fontSize = 13.sp)
                }
                Icon(Icons.Filled.Refresh, "Revisar otra vez", tint = Color(0xFFB98CFF), modifier = Modifier.size(44.dp).clip(CircleShape).clickable { refresh++ }.padding(10.dp))
            }
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
                items(STORE_APPS, key = { it.pkg }) { a ->
                    @Suppress("UNUSED_EXPRESSION") installedTick
                    val have = installedCode(ctx, a.pkg)
                    val r = remote[a.pkg]
                    val job = jobs[a.pkg]
                    Column(Modifier.fillMaxWidth().padding(vertical = 7.dp).clip(RoundedCornerShape(24.dp))
                        .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.14f), Color.White.copy(alpha = 0.05f))))
                        .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(24.dp)).padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)).background(Color(0x33FFFFFF)), contentAlignment = Alignment.Center) {
                                if (a.icon != null) {
                                    Image(painterResource(if (a.icon == R.mipmap.ic_music_fg) R.mipmap.ic_music_bg else R.mipmap.ic_launcher_bg), null, Modifier.fillMaxSize())
                                    Image(painterResource(a.icon), null, Modifier.fillMaxSize())
                                }
                                else Text(a.emoji, fontSize = 32.sp)
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(a.name, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                Text(a.desc, color = Color(0xFFB9AEDB), fontSize = 12.sp)
                                Text(buildString {
                                    append(if (have != null) "Instalada: v${installedName(ctx, a.pkg)}" else "No instalada")
                                    r?.let { append(" · En el NAS: v${it.name}"); if (it.size > 0) append(" (${formatSize(it.size)})") }
                                }, color = Color(0xFF8FD3FF), fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                        r?.notes?.takeIf { it.isNotBlank() && have != null && r.code > have }?.let {
                            Text("Novedades: $it", color = Color(0xFFE8E2FF), fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
                        }
                        when (job) {
                            is Job.Downloading -> {
                                LinearProgressIndicator({ job.progress }, Modifier.fillMaxWidth().padding(top = 12.dp).height(6.dp).clip(CircleShape),
                                    color = Color(0xFFB98CFF), trackColor = Color(0x33FFFFFF))
                                Text("Descargando… ${(job.progress * 100).toInt()} %", color = Color(0xFFB9AEDB), fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                            }
                            else -> {
                                (job as? Job.Failed)?.let { Text(it.msg, color = Color(0xFFFFB4C8), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) }
                                errors[a.pkg]?.let { Text(it, color = Color(0xFFFFB4C8), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) }
                                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    val update = r != null && have != null && r.code > have
                                    if (r != null && (have == null || update)) StoreButton(if (have == null) "Instalar" else "Actualizar", true, Modifier.weight(1f)) { get(a, r) }
                                    if (have != null) StoreButton("Abrir", !update, Modifier.weight(1f)) {
                                        ctx.packageManager.getLaunchIntentForPackage(a.pkg)?.let { ctx.startActivity(it) }
                                    }
                                    if (r == null && have == null && errors[a.pkg] == null)
                                        Text("Revisando el NAS…", color = Color(0xFFB9AEDB), fontSize = 13.sp, modifier = Modifier.padding(vertical = 10.dp))
                                }
                            }
                        }
                    }
                }
                item {
                    Text("Las apps se descargan del NAS, así que hay que estar en el Wi-Fi de la casa.", color = Color(0xFF8C82B0), fontSize = 12.sp,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(16.dp))
                }
            }
        }
    }
}

@Composable
private fun StoreButton(label: String, primary: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, textAlign = TextAlign.Center,
        modifier = modifier.clip(RoundedCornerShape(50))
            .background(if (primary) Brush.linearGradient(listOf(Color(0xFFB98CFF), Color(0xFFFF7EB6))) else Brush.linearGradient(listOf(Color(0x33FFFFFF), Color(0x22FFFFFF))))
            .clickable(onClick = onClick).padding(vertical = 12.dp))
}
