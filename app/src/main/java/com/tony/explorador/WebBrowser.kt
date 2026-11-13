package com.tony.explorador

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

private const val WEB_HOME = "https://www.google.com"

/** Convierte lo que escribes en una dirección: si no parece página, lo busca en Google. */
private fun toUrl(text: String): String {
    val t = text.trim()
    return when {
        t.startsWith("http://") || t.startsWith("https://") -> t
        t.contains(' ') || !t.contains('.') -> "https://www.google.com/search?q=" + Uri.encode(t)
        else -> "https://$t"
    }
}

/** Navegador web sencillo dentro de la app. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebBrowserScreen(startUrl: String?, onClose: () -> Unit, onHome: () -> Unit) {
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    var address by remember { mutableStateOf(startUrl ?: WEB_HOME) }
    var editing by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var progress by remember { mutableIntStateOf(0) }
    var canBack by remember { mutableStateOf(false) }
    var canForward by remember { mutableStateOf(false) }
    var lastTouch by remember { mutableStateOf(0f to 0f) }
    var pressed by remember { mutableStateOf<PressedItem?>(null) }
    var downloadsOpen by remember { mutableStateOf(false) }

    val web = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.mediaPlaybackRequiresUserGesture = true
            CookieManager.getInstance().setAcceptCookie(true)
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val u = request.url
                    if (u.scheme == "http" || u.scheme == "https") return false
                    // teléfono, correo, WhatsApp, tiendas…: se abren con su app
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, u).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    return true
                }
                override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) { if (!editing) address = url }
                override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                    if (!editing) address = url
                    canBack = view.canGoBack(); canForward = view.canGoForward()
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) { progress = newProgress }
                override fun onReceivedTitle(view: WebView, t: String?) { title = t.orEmpty() }
            }
            setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
                startDownload(context, url, userAgent, URLUtil.guessFileName(url, contentDisposition, mimeType), mimeType, this.url)
            }
            // recordar dónde tocaste, para saber qué elemento dejaste presionado
            setOnTouchListener { _, ev -> lastTouch = ev.x to ev.y; false }
            // dejar presionado = «clic derecho»: guardar imagen, video o enlace
            setOnLongClickListener {
                val hit = hitTestResult
                val extra = hit.extra
                when (hit.type) {
                    WebView.HitTestResult.IMAGE_TYPE, WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE ->
                        if (extra != null) { pressed = PressedItem("image", extra); true } else false
                    WebView.HitTestResult.SRC_ANCHOR_TYPE ->
                        if (extra != null) { pressed = PressedItem("link", extra); true } else false
                    else -> {
                        // ¿hay un video debajo del dedo?
                        val d = resources.displayMetrics.density
                        val js = "(function(){var e=document.elementFromPoint(${lastTouch.first / d},${lastTouch.second / d});" +
                            "while(e&&e.tagName!='VIDEO')e=e.parentElement;if(!e)return '';" +
                            "var s=e.currentSrc||e.src||'';if(!s){var q=e.querySelector('source');if(q)s=q.src;}return s||'blob:';})()"
                        evaluateJavascript(js) { r ->
                            val u = r?.trim('"').orEmpty()
                            if (u.isNotBlank() && u != "null") pressed = PressedItem("video", u)
                        }
                        false
                    }
                }
            }
            loadUrl(startUrl ?: WEB_HOME)
        }
    }
    DisposableEffect(Unit) { onDispose { web.stopLoading(); web.destroy() } }
    BackHandler { if (web.canGoBack()) web.goBack() else onClose() }

    pressed?.let { p ->
        val ua = web.settings.userAgentString
        fun copy() {
            (context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                .setPrimaryClip(android.content.ClipData.newPlainText("Enlace", p.url))
            Toast.makeText(context, "Enlace copiado", Toast.LENGTH_SHORT).show()
        }
        val opts = buildList<Pair<String, () -> Unit>> {
            val direct = p.url.startsWith("http")
            when (p.kind) {
                "image" -> { if (direct) add("Guardar imagen" to { startDownload(context, p.url, ua, null, null, web.url) }); add("Abrir imagen" to { web.loadUrl(p.url) }) }
                "video" -> if (direct) { add("Guardar video" to { startDownload(context, p.url, ua, null, null, web.url) }); add("Abrir video" to { web.loadUrl(p.url) }) }
                else -> { add("Abrir enlace" to { web.loadUrl(p.url) }); add("Descargar lo que hay en el enlace" to { startDownload(context, p.url, ua, null, null, web.url) }) }
            }
            if (direct) add("Copiar enlace" to { copy() })
        }
        ThemedDialog(when (p.kind) { "image" -> "Imagen"; "video" -> "Video"; else -> "Enlace" }, { pressed = null },
            listOf(DialogButton("Cerrar", true) { pressed = null })) {
            Column {
                Text(p.url.take(120), fontSize = 12.sp, maxLines = 2, color = XpColors.InkSoft)
                if (opts.isEmpty()) Text(
                    "Este ${if (p.kind == "video") "video" else "elemento"} se transmite por partes o está protegido por la página, así que no se puede guardar como archivo.",
                    modifier = Modifier.padding(top = 10.dp))
                opts.forEach { (label, action) ->
                    Text(label, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth().clickable { pressed = null; action() }.padding(vertical = 13.dp))
                }
            }
        }
    }
    if (downloadsOpen) DownloadsDialog(web, onDismiss = { downloadsOpen = false })

    Surface(Modifier.fillMaxSize().pointerInput(Unit) {}, color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            // barra de direcciones
            Box(Modifier.fillMaxWidth().background(XpColors.titleBrush).statusBarsPadding()) {
                Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Cerrar", tint = Color.White) }
                    Row(
                        Modifier.weight(1f).height(38.dp).background(Color.White.copy(alpha = 0.95f), RoundedCornerShape(50)).padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!editing && address.startsWith("https://")) {
                            Icon(Icons.Filled.Lock, "Conexión segura", tint = Color(0xFF2E7D32), modifier = Modifier.size(15.dp).padding(end = 2.dp))
                        }
                        BasicTextField(
                            value = if (editing) address else address.removePrefix("https://").removePrefix("www."),
                            onValueChange = { address = it },
                            singleLine = true,
                            textStyle = TextStyle(color = Color.Black, fontSize = 15.sp),
                            cursorBrush = SolidColor(Color(0xFF7A4FE0)),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(onGo = {
                                val u = toUrl(address); address = u; focus.clearFocus(); web.loadUrl(u)
                            }),
                            modifier = Modifier.weight(1f).padding(start = 4.dp).onFocusChanged { f ->
                                if (f.isFocused && !editing) { editing = true; address = web.url ?: address }
                                if (!f.isFocused && editing) { editing = false; address = web.url ?: address }
                            }
                        )
                    }
                    HomeButton(onHome, Modifier.padding(horizontal = 10.dp))
                }
            }
            if (progress in 1..99) LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth().height(3.dp),
                color = Color(0xFFD64FC4))
            AndroidView(factory = { web }, modifier = Modifier.weight(1f).fillMaxWidth())
            // botones de abajo
            Surface(tonalElevation = 3.dp) {
                Row(Modifier.fillMaxWidth().navigationBarsPadding().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                    @Composable
                    fun Btn(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, enabled: Boolean = true, onClick: () -> Unit) {
                        IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.weight(1f)) { Icon(icon, desc) }
                    }
                    Btn(Icons.AutoMirrored.Filled.ArrowBack, "Atrás", canBack) { web.goBack() }
                    Btn(Icons.AutoMirrored.Filled.ArrowForward, "Adelante", canForward) { web.goForward() }
                    Btn(Icons.Filled.Home, "Página de inicio") { web.loadUrl(WEB_HOME) }
                    Btn(Icons.Filled.Refresh, "Recargar") { web.reload() }
                    Btn(Icons.Filled.Download, "Descargas") { downloadsOpen = true }
                    Btn(Icons.Filled.Share, "Compartir") {
                        runCatching {
                            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
                                .putExtra(Intent.EXTRA_SUBJECT, title).putExtra(Intent.EXTRA_TEXT, web.url.orEmpty()), "Compartir página"))
                        }
                    }
                }
            }
        }
    }
}


private data class PressedItem(val kind: String, val url: String)

/** Pone una descarga en la cola (se guarda en la carpeta Descargas). */
fun startDownload(context: Context, url: String, userAgent: String?, name: String?, mime: String?, referer: String?) {
    if (!url.startsWith("http")) { Toast.makeText(context, "Este elemento no se puede descargar", Toast.LENGTH_SHORT).show(); return }
    runCatching {
        val file = name ?: URLUtil.guessFileName(url, null, mime)
        val req = DownloadManager.Request(Uri.parse(url))
            .setTitle(file)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, file)
        if (mime != null) req.setMimeType(mime)
        if (userAgent != null) req.addRequestHeader("User-Agent", userAgent)
        if (referer != null) req.addRequestHeader("Referer", referer)
        CookieManager.getInstance().getCookie(url)?.let { req.addRequestHeader("Cookie", it) }
        (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
        Toast.makeText(context, "Descargando «$file»", Toast.LENGTH_SHORT).show()
    }.onFailure { Toast.makeText(context, "No se pudo descargar", Toast.LENGTH_SHORT).show() }
}

private data class DownloadRow(val id: Long, val title: String, val status: Int, val done: Long, val total: Long,
                               val url: String, val reason: Int)

private fun queryDownloads(dm: DownloadManager): List<DownloadRow> {
    val out = mutableListOf<DownloadRow>()
    runCatching {
        dm.query(DownloadManager.Query())?.use { c ->
            val iId = c.getColumnIndexOrThrow(DownloadManager.COLUMN_ID)
            val iTitle = c.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE)
            val iStatus = c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
            val iDone = c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            val iTotal = c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            val iUrl = c.getColumnIndexOrThrow(DownloadManager.COLUMN_URI)
            val iReason = c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)
            while (c.moveToNext()) out.add(DownloadRow(c.getLong(iId), c.getString(iTitle).orEmpty(), c.getInt(iStatus),
                c.getLong(iDone), c.getLong(iTotal), c.getString(iUrl).orEmpty(), c.getInt(iReason)))
        }
    }
    return out.sortedByDescending { it.id }
}

/** Gestor de descargas del navegador: avance, cancelar, reintentar, abrir, compartir, quitar. */
@Composable
private fun DownloadsDialog(web: WebView, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val dm = remember { context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager }
    var rows by remember { mutableStateOf(queryDownloads(dm)) }
    var speeds by remember { mutableStateOf(mapOf<Long, Long>()) }
    var pageVideos by remember { mutableStateOf<List<String>?>(null) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(800)
            val fresh = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { queryDownloads(dm) }
            val old = rows.associate { it.id to it.done }
            speeds = fresh.associate { it.id to (((it.done - (old[it.id] ?: it.done)) * 1000 / 800).coerceAtLeast(0)) }
            rows = fresh
        }
    }
    fun open(r: DownloadRow, share: Boolean) {
        runCatching {
            val uri = dm.getUriForDownloadedFile(r.id) ?: return
            val mime = dm.getMimeTypeForDownloadedFile(r.id) ?: "*/*"
            val i = if (share) Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri)
                    else Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(Intent.createChooser(i, if (share) "Compartir" else "Abrir con"))
        }.onFailure { Toast.makeText(context, "No se pudo abrir", Toast.LENGTH_SHORT).show() }
    }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.95f).fillMaxSize(0.86f), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().background(XpColors.titleBrush).padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Descargas", color = Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "Cerrar", tint = Color.White) }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.TextButton(onClick = {
                        // busca los videos que la página tiene como archivo directo
                        web.evaluateJavascript("(function(){var o=[];document.querySelectorAll('video,video source').forEach(function(v){" +
                            "var s=v.currentSrc||v.src;if(s&&s.indexOf('http')==0&&o.indexOf(s)<0)o.push(s);});return o.join('\\n');})()") { r ->
                            pageVideos = r?.trim('"').orEmpty().split("\\n").filter { it.startsWith("http") }
                        }
                    }) { Text("Buscar videos en esta página") }
                    androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                    androidx.compose.material3.TextButton(onClick = {
                        rows.filter { it.status == DownloadManager.STATUS_SUCCESSFUL || it.status == DownloadManager.STATUS_FAILED }
                            .forEach { r -> if (r.status == DownloadManager.STATUS_FAILED) dm.remove(r.id) else hideFinished(context, r.id) }
                        rows = queryDownloads(dm)
                    }) { Text("Limpiar lista") }
                }
                pageVideos?.let { vids ->
                    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
                        if (vids.isEmpty()) Text("No encontré videos que se puedan guardar como archivo en esta página (los de transmisión o protegidos no se pueden).",
                            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        vids.take(6).forEach { v ->
                            Row(Modifier.fillMaxWidth().clickable { startDownload(context, v, web.settings.userAgentString, null, null, web.url) }.padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Download, null, tint = MaterialTheme.colorScheme.primary)
                                Text(" " + v.substringAfterLast('/').substringBefore('?').ifBlank { v }.take(60), maxLines = 1, fontSize = 14.sp)
                            }
                        }
                    }
                }
                val hidden = remember { hiddenDownloads(context) }
                val shown = rows.filter { it.id !in hidden.value }
                if (shown.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Aún no hay descargas", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxSize()) {
                    items(shown.size, key = { shown[it].id }) { idx ->
                        val r = shown[idx]
                        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
                            Text(r.title.ifBlank { r.url.substringAfterLast('/') }, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, maxLines = 1)
                            val state = when (r.status) {
                                DownloadManager.STATUS_SUCCESSFUL -> "Completada · ${formatSize(r.total.coerceAtLeast(r.done))}"
                                DownloadManager.STATUS_FAILED -> "Falló"
                                DownloadManager.STATUS_PAUSED -> "En espera (sin conexión o esperando Wi‑Fi)"
                                DownloadManager.STATUS_PENDING -> "En cola…"
                                else -> "${formatSize(r.done)}" + (if (r.total > 0) " de ${formatSize(r.total)}" else "") +
                                    " · ${formatSize(speeds[r.id] ?: 0)}/s"
                            }
                            Text(state, fontSize = 12.sp, color = if (r.status == DownloadManager.STATUS_FAILED) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant)
                            if (r.status == DownloadManager.STATUS_RUNNING || r.status == DownloadManager.STATUS_PENDING || r.status == DownloadManager.STATUS_PAUSED) {
                                if (r.total > 0) LinearProgressIndicator(progress = { (r.done.toFloat() / r.total).coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp), color = Color(0xFFD64FC4))
                                else LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 4.dp), color = Color(0xFFD64FC4))
                            }
                            Row {
                                @Composable
                                fun Act(label: String, onClick: () -> Unit) =
                                    androidx.compose.material3.TextButton(onClick = onClick, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)) { Text(label, fontSize = 13.sp) }
                                when (r.status) {
                                    DownloadManager.STATUS_SUCCESSFUL -> {
                                        Act("Abrir") { open(r, false) }
                                        Act("Compartir") { open(r, true) }
                                        Act("Quitar de la lista") { hideFinished(context, r.id); hidden.value = hidden.value + r.id }
                                        Act("Borrar archivo") { dm.remove(r.id); rows = queryDownloads(dm) }
                                    }
                                    DownloadManager.STATUS_FAILED -> {
                                        Act("Reintentar") { dm.remove(r.id); startDownload(context, r.url, web.settings.userAgentString, r.title.ifBlank { null }, null, null); rows = queryDownloads(dm) }
                                        Act("Quitar") { dm.remove(r.id); rows = queryDownloads(dm) }
                                    }
                                    else -> Act("Cancelar") { dm.remove(r.id); rows = queryDownloads(dm) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Descargas terminadas que se quitaron de la lista (el archivo se conserva). */
private fun hiddenDownloads(context: Context): androidx.compose.runtime.MutableState<Set<Long>> =
    mutableStateOf(context.getSharedPreferences("descargas", Context.MODE_PRIVATE).getStringSet("ocultas", emptySet())!!
        .mapNotNull { it.toLongOrNull() }.toSet())

private fun hideFinished(context: Context, id: Long) {
    val p = context.getSharedPreferences("descargas", Context.MODE_PRIVATE)
    p.edit().putStringSet("ocultas", (p.getStringSet("ocultas", emptySet())!! + id.toString())).apply()
}
