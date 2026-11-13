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
                runCatching {
                    val name = URLUtil.guessFileName(url, contentDisposition, mimeType)
                    val req = DownloadManager.Request(Uri.parse(url))
                        .setMimeType(mimeType)
                        .addRequestHeader("User-Agent", userAgent)
                        .addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url).orEmpty())
                        .setTitle(name)
                        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
                    (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
                    Toast.makeText(context, "Descargando «$name» en Descargas", Toast.LENGTH_LONG).show()
                }.onFailure { Toast.makeText(context, "No se pudo descargar", Toast.LENGTH_SHORT).show() }
            }
            loadUrl(startUrl ?: WEB_HOME)
        }
    }
    DisposableEffect(Unit) { onDispose { web.stopLoading(); web.destroy() } }
    BackHandler { if (web.canGoBack()) web.goBack() else onClose() }

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
