package com.tony.explorador

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Las apps Nebula que hay en este teléfono, para mandarlas a alguien. */
private data class NebulaApp(val pkg: String, val name: String, val version: String, val apk: File, val icon: android.graphics.Bitmap?)

private val NebulaPkgs = listOf(
    "com.tony.explorador" to "DriveNebula",
    "com.tony.explorador.musica" to "Nebula Música",
    "com.tony.explorador.control" to "Control Nebula",
)

private fun installedNebulaApps(c: android.content.Context): List<NebulaApp> = NebulaPkgs.mapNotNull { (pkg, name) ->
    runCatching {
        val pm = c.packageManager
        val info = pm.getPackageInfo(pkg, 0)
        val ai = info.applicationInfo ?: return@runCatching null
        NebulaApp(pkg, name, info.versionName ?: "", File(ai.sourceDir), runCatching { pm.getApplicationIcon(ai).toBitmap(96, 96) }.getOrNull())
    }.getOrNull()
}

/** Copia el instalador (.apk) a la caché con un nombre bonito y abre «Compartir». */
private fun shareApk(c: android.content.Context, a: NebulaApp) {
    val dir = File(c.cacheDir, "compartir").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
    val out = File(dir, a.name.replace(" ", "-") + "-v" + a.version + ".apk")
    a.apk.inputStream().use { i -> out.outputStream().use { o -> i.copyTo(o) } }
    val uri = FileProvider.getUriForFile(c, "${c.packageName}.files", out)
    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "application/vnd.android.package-archive"
        putExtra(android.content.Intent.EXTRA_STREAM, uri)
        putExtra(android.content.Intent.EXTRA_TEXT, "Te paso ${a.name} (v${a.version}). Ábrelo para instalarlo.")
        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    c.startActivity(android.content.Intent.createChooser(send, "Compartir ${a.name}").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** Ventana para elegir qué app Nebula compartir (por WhatsApp, Bluetooth, correo…). */
@Composable
fun ShareNebulaDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val apps = remember { installedNebulaApps(ctx) }
    var busy by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Compartir apps Nebula") },
        text = {
            Column {
                Text("Elige cuál mandar. Se comparte el instalador para que la otra persona la instale en su teléfono.",
                    fontSize = 13.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 10.dp))
                apps.forEach { a ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0x14888888))
                        .clickable(enabled = busy == null) {
                            busy = a.pkg
                            scope.launch {
                                runCatching { withContext(Dispatchers.IO) { shareApk(ctx, a) } }
                                    .onFailure { android.widget.Toast.makeText(ctx, "No se pudo preparar ${a.name}", android.widget.Toast.LENGTH_SHORT).show() }
                                busy = null
                            }
                        }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        a.icon?.let { Image(it.asImageBitmap(), null, Modifier.size(42.dp).clip(RoundedCornerShape(10.dp))) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(a.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text("v${a.version} · ${a.apk.length() / (1024 * 1024)} MB", fontSize = 12.sp, color = Color.Gray)
                        }
                        if (busy == a.pkg) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Filled.Share, "Compartir", tint = Color(0xFF6A3FD0))
                    }
                    Spacer(Modifier.padding(4.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}
