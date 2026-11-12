package com.tony.explorador

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

private const val LATEST_URL = "https://api.github.com/repos/Inerk/explorador-tony/releases/latest"

data class UpdateInfo(
    val versionCode: Long,
    val tag: String,
    val notes: String,
    val apkUrl: String,
    val size: Long,
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateState
    data class NeedsPermission(val apk: File) : UpdateState
    data class Error(val message: String) : UpdateState
}

fun currentVersionCode(context: Context): Long =
    PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0))

fun currentVersionName(context: Context): String =
    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"

/** Pregunta a GitHub cuál es la última versión publicada. */
suspend fun fetchLatestRelease(): UpdateInfo = withContext(Dispatchers.IO) {
    val conn = (URL(LATEST_URL).openConnection() as HttpURLConnection).apply {
        connectTimeout = 10_000
        readTimeout = 10_000
        setRequestProperty("Accept", "application/vnd.github+json")
        setRequestProperty("User-Agent", "ExploradorTony")
    }
    try {
        if (conn.responseCode != 200) error("GitHub respondió ${conn.responseCode}")
        val json = JSONObject(conn.inputStream.bufferedReader().readText())
        val tag = json.getString("tag_name")
        val code = tag.substringAfterLast('.').toLongOrNull() ?: error("Versión no reconocida: $tag")
        val assets = json.getJSONArray("assets")
        var url: String? = null
        var size = 0L
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            if (a.getString("name").endsWith(".apk")) {
                url = a.getString("browser_download_url")
                size = a.optLong("size")
                break
            }
        }
        UpdateInfo(
            versionCode = code,
            tag = tag,
            notes = json.optString("body", "").trim(),
            apkUrl = url ?: error("La versión no trae APK"),
            size = size,
        )
    } finally {
        conn.disconnect()
    }
}

suspend fun downloadApk(context: Context, info: UpdateInfo, onProgress: (Float) -> Unit): File =
    withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, "Explorador-Tony-${info.tag}.apk")
        var url = URL(info.apkUrl)
        var conn: HttpURLConnection
        // GitHub redirige a otro servidor; seguimos las redirecciones a mano por si acaso
        var hops = 0
        while (true) {
            conn = (url.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 15_000
                readTimeout = 30_000
                setRequestProperty("User-Agent", "ExploradorTony")
            }
            val code = conn.responseCode
            if (code in 300..399 && hops < 5) {
                url = URL(url, conn.getHeaderField("Location"))
                conn.disconnect()
                hops++
                continue
            }
            if (code != 200) error("Descarga falló ($code)")
            break
        }
        val total = conn.contentLengthLong.takeIf { it > 0 } ?: info.size
        conn.inputStream.use { input ->
            target.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                var read: Int
                var done = 0L
                while (input.read(buffer).also { read = it } >= 0) {
                    output.write(buffer, 0, read)
                    done += read
                    if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                }
            }
        }
        conn.disconnect()
        target
    }

fun canInstallApks(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

fun openInstallPermissionSettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        Uri.parse("package:${context.packageName}")
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

fun installApk(context: Context, apk: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apk)
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, "application/vnd.android.package-archive")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

@Composable
fun UpdateDialog(
    state: UpdateState,
    currentName: String,
    onDownload: (UpdateInfo) -> Unit,
    onInstall: (File) -> Unit,
    onDismiss: () -> Unit,
) {
    when (state) {
        UpdateState.Idle -> {}
        UpdateState.Checking -> ThemedDialog("Buscando actualizaciones…", onDismiss, emptyList()) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        UpdateState.UpToDate -> ThemedDialog("Estás al día", onDismiss, listOf(DialogButton("Aceptar", true, onDismiss)), credits = true) {
            Text("Ya tienes la versión más nueva (v$currentName).")
        }
        is UpdateState.Available -> ThemedDialog(
            "Nueva versión ${state.info.tag}", onDismiss,
            listOf(DialogButton("Después", onClick = onDismiss), DialogButton("Actualizar", true) { onDownload(state.info) }),
            credits = true,
        ) {
            Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                Text("Tienes la v$currentName. Tamaño: ${formatSize(state.info.size)}", fontSize = 13.sp)
                val notes = cleanReleaseNotes(state.info.notes)
                if (notes.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text("Qué cambió:", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    Text(notes, fontSize = 14.sp)
                }
            }
        }
        is UpdateState.Downloading -> XpDownloadDialog(state.info.tag, state.progress, state.info.size)
        is UpdateState.NeedsPermission -> ThemedDialog(
            "Falta un permiso", onDismiss,
            listOf(DialogButton("Cancelar", onClick = onDismiss), DialogButton("Instalar", true) { onInstall(state.apk) }),
        ) {
            Text("La primera vez, Android pide permitir que Explorador Tony instale apps. " +
                "Actívalo en la pantalla que se abre, regresa y toca Instalar.")
        }
        is UpdateState.Error -> ThemedDialog("No se pudo actualizar", onDismiss, listOf(DialogButton("Aceptar", true, onDismiss))) {
            Text(state.message)
        }
    }
}

data class ReleaseNote(val tag: String, val date: String, val notes: String)

/** Lista de todas las versiones publicadas, de la más nueva a la más vieja. */
suspend fun fetchReleaseHistory(): List<ReleaseNote> = withContext(Dispatchers.IO) {
    val conn = (URL("https://api.github.com/repos/Inerk/explorador-tony/releases?per_page=60").openConnection() as HttpURLConnection).apply {
        connectTimeout = 10_000
        readTimeout = 15_000
        setRequestProperty("Accept", "application/vnd.github+json")
        setRequestProperty("User-Agent", "ExploradorTony")
    }
    try {
        if (conn.responseCode != 200) error("GitHub respondió ${conn.responseCode}")
        val arr = org.json.JSONArray(conn.inputStream.bufferedReader().readText())
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val iso = o.optString("published_at", "")
            val date = if (iso.length >= 10) iso.substring(8, 10) + "/" + iso.substring(5, 7) + "/" + iso.substring(0, 4) else ""
            ReleaseNote(o.getString("tag_name"), date, cleanReleaseNotes(o.optString("body", "")))
        }
    } finally {
        conn.disconnect()
    }
}

@Composable
fun VersionHistoryDialog(currentName: String, onDismiss: () -> Unit) {
    val result by androidx.compose.runtime.produceState<Result<List<ReleaseNote>>?>(null) {
        value = runCatching { fetchReleaseHistory() }
    }
    ThemedDialog("Historial de versiones", onDismiss, listOf(DialogButton("Cerrar", true, onDismiss)), credits = true) {
            val r = result
            when {
                r == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
                r.isFailure -> Text("No se pudo cargar el historial. Revisa tu conexión a internet.")
                else -> androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 460.dp)) {
                    val list = r.getOrDefault(emptyList())
                    items(list.size) { i ->
                        val n = list[i]
                        val installed = n.tag.removePrefix("v") == currentName
                        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Text(n.tag, style = MaterialTheme.typography.titleMedium,
                                    color = if (installed) Color(0xFF2D63D9) else androidx.compose.material3.LocalContentColor.current)
                                if (installed) Text("  · la que tienes", style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF2D63D9))
                                Spacer(Modifier.weight(1f))
                                Text(n.date, style = MaterialTheme.typography.bodySmall)
                            }
                            if (n.notes.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(n.notes, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        if (i < list.lastIndex) androidx.compose.material3.HorizontalDivider()
                    }
                }
            }
    }
}
