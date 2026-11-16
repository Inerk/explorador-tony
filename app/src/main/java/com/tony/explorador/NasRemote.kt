@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.tony.explorador

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/** Abre el control remoto del NAS desde cualquier lado. */
object RemoteUi { var open by mutableStateOf(false) }

private object RC {
    val bg1 = Color(0xFF140F2B)
    val bg2 = Color(0xFF2A1650)
    val card = Color(0x22FFFFFF)
    val accent = Color(0xFFB98CFF)
    val accent2 = Color(0xFFFF7AC6)
    val text = Color(0xFFF4EEFF)
    val sub = Color(0xFFB4A8D6)
}

private data class RAlbum(val id: String, val name: String, val artist: String, val art: String?, val tracks: Int, val year: Int = 0)
private data class RArtist(val id: String, val name: String, val albums: Int)
private data class RPlaylist(val id: String, val name: String, val count: Int)
private data class RTrack(val uri: String, val title: String, val artist: String, val album: String, val lengthMs: Long, val art: String?, val id: String = "", val rating: Int = 0)
private data class ROutput(val id: String, val name: String, val type: String, val selected: Boolean, val volume: Int)

/** Lo que dice el NAS de lo que está sonando. */
private class RState {
    var host by mutableStateOf<String?>(null)
    var searching by mutableStateOf(true)
    var error by mutableStateOf<String?>(null)
    var state by mutableStateOf("stop")
    var volume by mutableStateOf(50)
    var pos by mutableStateOf(0L)
    var len by mutableStateOf(0L)
    var title by mutableStateOf("")
    var artist by mutableStateOf("")
    var art by mutableStateOf<String?>(null)
    var trackId by mutableStateOf("")
    var rating by mutableStateOf(0)
    var shuffle by mutableStateOf(false)
    var repeat by mutableStateOf("off")         // off · all · single
    val outputs = mutableStateListOf<ROutput>()
}

/** Apodos de bocinas y perfiles de volumen, guardados en el teléfono. */
object RemotePrefs {
    private var prefs: android.content.SharedPreferences? = null
    val nicks = androidx.compose.runtime.mutableStateMapOf<String, String>()
    /** perfil → (id de bocina → (encendida, volumen)) */
    val profiles = androidx.compose.runtime.mutableStateMapOf<String, Map<String, Pair<Boolean, Int>>>()

    fun init(c: android.content.Context) {
        if (prefs != null) return
        val p = c.applicationContext.getSharedPreferences("remote", android.content.Context.MODE_PRIVATE); prefs = p
        runCatching { JSONObject(p.getString("nicks", "{}")!!).let { o -> o.keys().forEach { nicks[it] = o.getString(it) } } }
        runCatching {
            val o = JSONObject(p.getString("profiles", "{}")!!)
            o.keys().forEach { name ->
                val m = o.getJSONObject(name)
                profiles[name] = m.keys().asSequence().associateWith { id -> m.getJSONObject(id).let { Pair(it.optBoolean("s"), it.optInt("v")) } }
            }
        }
    }
    fun nameOf(id: String, fallback: String) = nicks[id]?.takeIf { it.isNotBlank() } ?: fallback
    fun setNick(id: String, nick: String) {
        if (nick.isBlank()) nicks.remove(id) else nicks[id] = nick.trim()
        prefs?.edit()?.putString("nicks", JSONObject(nicks.toMap()).toString())?.apply()
    }
    fun saveProfile(name: String, outs: List<Pair<String, Pair<Boolean, Int>>>) { profiles[name.trim()] = outs.toMap(); persist() }
    fun deleteProfile(name: String) { profiles.remove(name); persist() }
    private fun persist() {
        val o = JSONObject()
        profiles.forEach { (n, m) -> o.put(n, JSONObject().apply { m.forEach { (id, sv) -> put(id, JSONObject().put("s", sv.first).put("v", sv.second)) } }) }
        prefs?.edit()?.putString("profiles", o.toString())?.apply()
    }
}

private fun api(host: String, method: String, path: String, body: String? = null) = AirPlayBridge.http(method, host, path, body)
// OwnTone da la portada como «./artwork/item/12»: hay que quitarle el punto
private fun artUrl(host: String, rel: String?) = rel?.takeIf { it.isNotBlank() }?.let {
    if (it.startsWith("http")) it else "http://$host:${AirPlayBridge.PORT}/" + it.removePrefix(".").trimStart('/')
}
private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
private fun fmt(ms: Long): String { val t = (ms / 1000).coerceAtLeast(0); return "%d:%02d".format(t / 60, t % 60) }

private fun JSONObject.items(): JSONArray = optJSONArray("items") ?: JSONArray()
private fun albumsOf(a: JSONArray, host: String) = (0 until a.length()).map { a.getJSONObject(it) }.map {
    RAlbum(it.optString("id"), it.optString("name"), it.optString("artist"), artUrl(host, it.optString("artwork_url")), it.optInt("track_count"), it.optInt("year"))
}
private fun tracksOf(a: JSONArray, host: String) = (0 until a.length()).map { a.getJSONObject(it) }.map {
    RTrack(it.optString("uri"), it.optString("title"), it.optString("artist"), it.optString("album"), it.optLong("length_ms"), artUrl(host, it.optString("artwork_url")), it.optString("id"), it.optInt("rating"))
}

@Composable
fun NasRemoteScreen(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val st = remember { RState() }
    remember { RemotePrefs.init(ctx); 0 }
    var tab by remember { mutableStateOf(1) }           // 0 sonando · 1 biblioteca · 2 bocinas
    val nav = remember { LibNav() }
    var reload by remember { mutableStateOf(0) }
    // atrás: dentro de la biblioteca regresa a donde estabas; desde Sonando/Bocinas vuelve a la biblioteca
    fun handleBack() {
        when {
            tab != 1 -> tab = 1
            nav.canBack -> nav.back()
            else -> onClose()
        }
    }
    BackHandler { handleBack() }

    // Android nuevo pide permiso para ver aparatos de la red de la casa
    val localPerm = "android.permission.ACCESS_LOCAL_NETWORK"
    fun hasPerm() = android.os.Build.VERSION.SDK_INT < 36 ||
        androidx.core.content.ContextCompat.checkSelfPermission(ctx, localPerm) == android.content.pm.PackageManager.PERMISSION_GRANTED
    var permAsked by remember { mutableStateOf(false) }
    val permLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { permAsked = true; reload++ }
    var askIp by remember { mutableStateOf(false) }

    // encontrar el NAS
    LaunchedEffect(reload) {
        if (!hasPerm() && !permAsked) { runCatching { permLauncher.launch(localPerm) }.onFailure { permAsked = true; reload++ }; return@LaunchedEffect }
        st.searching = true; st.error = null
        st.host = withContext(Dispatchers.IO) { runCatching { AirPlayBridge.findHost(ctx) }.getOrNull() }
        st.searching = false
        if (st.host == null) st.error = if (!hasPerm())
            "La app no tiene permiso para ver tu red. Ve a Ajustes del teléfono → Apps → DriveNebula → Permisos → «Dispositivos cercanos / red local» y actívalo."
        else "No encontré el NAS con OwnTone (${AirPlayBridge.lastInfo}). Toca aquí para escribir su dirección."
    }

    // estado de lo que suena, cada segundo
    LaunchedEffect(st.host) {
        val h = st.host ?: return@LaunchedEffect
        var tick = 0
        while (isActive) {
            withContext(Dispatchers.IO) {
                runCatching {
                    val p = JSONObject(api(h, "GET", "/api/player"))
                    st.state = p.optString("state"); st.volume = p.optInt("volume", st.volume)
                    st.shuffle = p.optBoolean("shuffle"); st.repeat = p.optString("repeat", "off")
                    st.pos = p.optLong("item_progress_ms"); st.len = p.optLong("item_length_ms")
                    val itemId = p.optLong("item_id", -1)
                    if (itemId >= 0) {
                        val q = JSONObject(api(h, "GET", "/api/queue")).items()
                        for (i in 0 until q.length()) {
                            val it = q.getJSONObject(i)
                            if (it.optLong("id") == itemId) {
                                st.title = it.optString("title"); st.artist = it.optString("artist")
                                val tid = it.optString("track_id")
                                if (tid != st.trackId) {
                                    st.trackId = tid
                                    st.rating = runCatching { JSONObject(api(h, "GET", "/api/library/tracks/$tid")).optInt("rating") }.getOrDefault(0)
                                }
                                st.art = artUrl(h, it.optString("artwork_url")); break
                            }
                        }
                    } else { st.title = ""; st.artist = ""; st.art = null }
                    if (tick % 4 == 0) {
                        val o = JSONObject(api(h, "GET", "/api/outputs")).optJSONArray("outputs") ?: JSONArray()
                        val list = (0 until o.length()).map { o.getJSONObject(it) }
                            .filter { !it.optString("name").endsWith("+") && it.optString("type") != "ALSA" }
                            .map { ROutput(it.optString("id"), it.optString("name"), it.optString("type"), it.optBoolean("selected"), it.optInt("volume")) }
                        withContext(Dispatchers.Main) { st.outputs.clear(); st.outputs.addAll(list) }
                    }
                }
            }
            tick++
            delay(1000)
        }
    }

    fun call(method: String, path: String, body: String? = null) {
        val h = st.host ?: return
        scope.launch(Dispatchers.IO) { runCatching { api(h, method, path, body) }.onFailure { st.error = "El NAS no respondió: ${it.message}" } }
    }
    /** Pone en la cola todo (álbum, lista, artista o varias canciones) y empieza en «from»: al terminar sigue con la siguiente. */
    fun queue(param: String, from: Int, shuffle: Boolean) {
        if (st.outputs.none { it.selected }) { st.error = "Primero elige una bocina en la pestaña «Bocinas»."; tab = 2; return }
        st.error = null
        val h = st.host ?: return
        scope.launch(Dispatchers.IO) {
            runCatching {
                api(h, "PUT", "/api/player/shuffle?state=$shuffle")
                if (st.repeat == "single") api(h, "PUT", "/api/player/repeat?state=all")
                api(h, "POST", "/api/queue/items/add?clear=true&playback=start" + (if (shuffle) "" else "&playback_from_position=$from") + "&$param")
            }.onFailure { st.error = "El NAS no respondió: ${it.message}" }
        }
    }
    fun playUris(uris: String, from: Int, shuffle: Boolean) = queue("uris=${enc(uris)}", from, shuffle)
    fun playExpr(expr: String, from: Int, shuffle: Boolean) = queue("expression=${enc(expr)}", from, shuffle)

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(RC.bg2, RC.bg1)))) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(androidx.compose.foundation.layout.WindowInsetsSides.Top + androidx.compose.foundation.layout.WindowInsetsSides.Horizontal))) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.ArrowBack, "Cerrar", tint = RC.text, modifier = Modifier.size(44.dp).clip(CircleShape).clickable { handleBack() }.padding(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Control remoto", color = RC.text, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                    Text(st.host?.let { "Música del NAS · $it" } ?: if (st.searching) "Buscando el NAS…" else "Sin conexión", color = RC.sub, fontSize = 12.sp)
                }
                if (st.searching) CircularProgressIndicator(color = RC.accent, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                else Icon(Icons.Filled.Refresh, "Buscar otra vez", tint = RC.sub, modifier = Modifier.size(44.dp).clip(CircleShape).clickable { reload++ }.padding(10.dp))
            }
            st.error?.let {
                Text(it, color = Color(0xFFFFB4C8), fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(10.dp)).background(Color(0x33FF4F7A)).clickable { if (st.host == null) askIp = true else st.error = null }.padding(10.dp).fillMaxWidth())
            }
            Box(Modifier.weight(1f)) {
                val h = st.host
                if (h != null) when (tab) {
                    0 -> NowPlayingTab(st, ::call)
                    1 -> LibraryTab(h, nav, ::playUris, ::playExpr)
                    2 -> SpeakersTab(st, ::call)
                }
            }
            if (askIp) TextAsk("Dirección del NAS", "Escribe la IP de tu NAS (la ves en el NAS en Panel de control → Ajustes de red).",
                AirPlayBridge.savedHost(ctx).orEmpty(), "Ej. 192.168.3.74",
                onDone = { ip -> askIp = false; if (ip.isNotBlank()) { AirPlayBridge.saveHost(ctx, ip.trim()); reload++ } }, onDismiss = { askIp = false })
            // lo que suena, chiquito, cuando no estás en «Sonando»
            if (tab != 0 && st.title.isNotBlank()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp).clip(RoundedCornerShape(14.dp)).background(Color(0x553A2580))
                    .clickable { tab = 0 }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(RC.card), contentAlignment = Alignment.Center) {
                        if (st.art != null) AsyncImage(st.art, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        else Icon(Icons.Filled.MusicNote, null, tint = RC.sub)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(st.title, color = RC.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(st.artist, color = RC.sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(if (st.state == "play") Icons.Filled.Pause else Icons.Filled.PlayArrow, "Pausa", tint = RC.text,
                        modifier = Modifier.size(40.dp).clip(CircleShape).clickable {
                            call("PUT", if (st.state == "play") "/api/player/pause" else "/api/player/play"); st.state = if (st.state == "play") "pause" else "play"
                        }.padding(8.dp))
                    Icon(Icons.Filled.SkipNext, "Siguiente", tint = RC.text, modifier = Modifier.size(40.dp).clip(CircleShape).clickable { call("PUT", "/api/player/next") }.padding(8.dp))
                }
            }
            // pestañas
            Row(Modifier.fillMaxWidth().background(Color(0x33000000)).padding(vertical = 6.dp)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(androidx.compose.foundation.layout.WindowInsetsSides.Bottom)),
                horizontalArrangement = Arrangement.SpaceEvenly) {
                TabBtn(Icons.Filled.PlayArrow, "Sonando", tab == 0) { tab = 0 }
                TabBtn(Icons.Filled.Album, "Biblioteca", tab == 1) { tab = 1 }
                TabBtn(Icons.Filled.Speaker, "Bocinas (${st.outputs.count { it.selected }})", tab == 2) { tab = 2 }
            }
        }
    }
}

@Composable
private fun TabBtn(icon: ImageVector, label: String, on: Boolean, onClick: () -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = if (on) RC.accent else RC.sub, modifier = Modifier.size(24.dp))
        Text(label, color = if (on) RC.accent else RC.sub, fontSize = 11.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun NowPlayingTab(st: RState, call: (String, String, String?) -> Unit) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    var volDrag by remember { mutableStateOf<Float?>(null) }
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.fillMaxWidth(0.82f).aspectRatio(1f).clip(RoundedCornerShape(24.dp)).background(RC.card), contentAlignment = Alignment.Center) {
            if (st.art != null) AsyncImage(st.art, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else Icon(Icons.Filled.MusicNote, null, tint = RC.sub, modifier = Modifier.size(80.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(st.title.ifBlank { "Nada sonando" }, color = RC.text, fontWeight = FontWeight.Bold, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(st.artist.ifBlank { "Elige música en «Biblioteca»" }, color = RC.sub, fontSize = 14.sp, maxLines = 1)
        if (st.trackId.isNotBlank()) {
            val fav = st.rating >= 80
            Icon(if (fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "Favorito", tint = if (fav) Color(0xFFFF5A8A) else RC.sub,
                modifier = Modifier.padding(top = 4.dp).size(40.dp).clip(CircleShape).clickable {
                    val nv = if (fav) 0 else 100; st.rating = nv
                    call("PUT", "/api/library/tracks/${st.trackId}?rating=$nv", null)
                }.padding(8.dp))
        }
        val names = st.outputs.filter { it.selected }.joinToString(", ") { RemotePrefs.nameOf(it.id, it.name) }
        if (names.isNotBlank()) Text("🔊 $names", color = RC.accent, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(12.dp))
        val len = st.len.coerceAtLeast(1)
        Slider(dragging ?: (st.pos.toFloat() / len).coerceIn(0f, 1f), { dragging = it },
            onValueChangeFinished = { dragging?.let { call("PUT", "/api/player/seek?position_ms=${(it * len).toLong()}", null) }; dragging = null },
            colors = SliderDefaults.colors(thumbColor = RC.accent, activeTrackColor = RC.accent, inactiveTrackColor = RC.card))
        Row(Modifier.fillMaxWidth()) {
            Text(fmt(st.pos), color = RC.sub, fontSize = 11.sp); Spacer(Modifier.weight(1f)); Text(fmt(st.len), color = RC.sub, fontSize = 11.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            Icon(Icons.Filled.Shuffle, "Aleatorio", tint = if (st.shuffle) RC.accent else RC.sub, modifier = Modifier.size(44.dp).clip(CircleShape)
                .clickable { st.shuffle = !st.shuffle; call("PUT", "/api/player/shuffle?state=${st.shuffle}", null) }.padding(10.dp))
            Icon(Icons.Filled.SkipPrevious, "Anterior", tint = RC.text, modifier = Modifier.size(52.dp).clip(CircleShape).clickable { call("PUT", "/api/player/previous", null) }.padding(8.dp))
            Box(Modifier.size(76.dp).clip(CircleShape).background(Brush.linearGradient(listOf(RC.accent, RC.accent2)))
                .clickable { call("PUT", if (st.state == "play") "/api/player/pause" else "/api/player/play", null); st.state = if (st.state == "play") "pause" else "play" },
                contentAlignment = Alignment.Center) {
                Icon(if (st.state == "play") Icons.Filled.Pause else Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(40.dp))
            }
            Icon(Icons.Filled.SkipNext, "Siguiente", tint = RC.text, modifier = Modifier.size(52.dp).clip(CircleShape).clickable { call("PUT", "/api/player/next", null) }.padding(8.dp))
            Icon(if (st.repeat == "single") Icons.Filled.RepeatOne else Icons.Filled.Repeat, "Repetir",
                tint = if (st.repeat == "off") RC.sub else RC.accent, modifier = Modifier.size(44.dp).clip(CircleShape)
                .clickable { st.repeat = when (st.repeat) { "off" -> "all"; "all" -> "single"; else -> "off" }; call("PUT", "/api/player/repeat?state=${st.repeat}", null) }.padding(10.dp))
        }
        Text(when { st.shuffle && st.repeat != "off" -> "Aleatorio · repetir"; st.shuffle -> "Aleatorio"; st.repeat == "all" -> "Repetir todo"; st.repeat == "single" -> "Repetir esta canción"; else -> "" },
            color = RC.accent, fontSize = 11.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.VolumeUp, null, tint = RC.sub, modifier = Modifier.size(20.dp))
            Slider(volDrag ?: (st.volume / 100f), { volDrag = it },
                onValueChangeFinished = { volDrag?.let { val v = (it * 100).toInt(); st.volume = v; call("PUT", "/api/player/volume?volume=$v", null) }; volDrag = null },
                colors = SliderDefaults.colors(thumbColor = RC.text, activeTrackColor = RC.text, inactiveTrackColor = RC.card), modifier = Modifier.weight(1f).padding(start = 8.dp))
        }
    }
}

@Composable
private fun SpeakersTab(st: RState, call: (String, String, String?) -> Unit) {
    var renaming by remember { mutableStateOf<ROutput?>(null) }
    var saving by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<String?>(null) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        item {
            Text("Perfiles de sonido", color = RC.accent, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RemotePrefs.profiles.keys.sorted().forEach { name ->
                    Text(name, color = RC.text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                        modifier = Modifier.clip(CircleShape).background(Color(0x33B98CFF))
                            .combinedClickable(onLongClick = { deleting = name }) {
                                // aplicar: prender/apagar y poner el volumen guardado de cada bocina
                                val prof = RemotePrefs.profiles[name] ?: return@combinedClickable
                                st.outputs.toList().forEachIndexed { i, o ->
                                    val sv = prof[o.id] ?: Pair(false, o.volume)
                                    val body = JSONObject().put("selected", sv.first).apply { if (sv.first) put("volume", sv.second) }.toString()
                                    call("PUT", "/api/outputs/${o.id}", body)
                                    st.outputs[i] = o.copy(selected = sv.first, volume = if (sv.first) sv.second else o.volume)
                                }
                            }.padding(horizontal = 14.dp, vertical = 8.dp))
                }
                Text("+ Guardar actual", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                    modifier = Modifier.clip(CircleShape).background(Brush.linearGradient(listOf(RC.accent, RC.accent2))).clickable { saving = true }
                        .padding(horizontal = 14.dp, vertical = 8.dp))
            }
            Text("Toca un perfil para aplicarlo · mantén presionado para borrarlo.", color = RC.sub, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
            Text("Bocinas", color = RC.accent, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(top = 14.dp))
            Text("Toca para encender o apagar · ✏️ para ponerle apodo. Puedes usar varias a la vez.", color = RC.sub, fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp))
        }
        items(st.outputs, key = { it.id }) { o ->
            var v by remember(o.id) { mutableStateOf<Float?>(null) }
            val nick = RemotePrefs.nameOf(o.id, o.name)
            Column(Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(16.dp)).background(if (o.selected) Color(0x33B98CFF) else RC.card)
                .clickable {
                    call("PUT", "/api/outputs/${o.id}", JSONObject().put("selected", !o.selected).toString())
                    val i = st.outputs.indexOfFirst { it.id == o.id }; if (i >= 0) st.outputs[i] = o.copy(selected = !o.selected)
                }.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Speaker, null, tint = if (o.selected) RC.accent else RC.sub, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(nick, color = RC.text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Text(if (nick != o.name) "${o.name} · ${o.type}" else o.type, color = RC.sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(Icons.Filled.Edit, "Apodo", tint = RC.sub, modifier = Modifier.size(38.dp).clip(CircleShape).clickable { renaming = o }.padding(9.dp))
                    Icon(if (o.selected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked, null, tint = if (o.selected) RC.accent else RC.sub)
                }
                if (o.selected) Slider(v ?: (o.volume / 100f), { v = it },
                    onValueChangeFinished = { v?.let { f ->
                        val vol = (f * 100).toInt()
                        call("PUT", "/api/outputs/${o.id}", JSONObject().put("volume", vol).toString())
                        val i = st.outputs.indexOfFirst { it.id == o.id }; if (i >= 0) st.outputs[i] = st.outputs[i].copy(volume = vol)
                    }; v = null },
                    colors = SliderDefaults.colors(thumbColor = RC.accent, activeTrackColor = RC.accent, inactiveTrackColor = Color(0x33FFFFFF)))
            }
        }
        if (st.outputs.isEmpty()) item { Text("El NAS no ve bocinas todavía.", color = RC.sub, modifier = Modifier.padding(16.dp)) }
        item { Spacer(Modifier.height(20.dp)) }
    }

    renaming?.let { o ->
        TextAsk("Apodo para la bocina", "Original: ${o.name}", RemotePrefs.nicks[o.id].orEmpty(), "Ej. Bocina de la cocina",
            onDone = { RemotePrefs.setNick(o.id, it); renaming = null }, onDismiss = { renaming = null })
    }
    if (saving) TextAsk("Guardar perfil", "Guarda qué bocinas están prendidas y el volumen de cada una.", "", "Ej. Fiesta, Noche, Comida",
        onDone = { n ->
            if (n.isNotBlank()) RemotePrefs.saveProfile(n, st.outputs.map { it.id to Pair(it.selected, it.volume) })
            saving = false
        }, onDismiss = { saving = false })
    deleting?.let { n ->
        androidx.compose.material3.AlertDialog(onDismissRequest = { deleting = null },
            title = { Text("¿Borrar el perfil «$n»?") },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { RemotePrefs.deleteProfile(n); deleting = null }) { Text("Borrar") } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { deleting = null }) { Text("Cancelar") } })
    }
}

@Composable
private fun TextAsk(title: String, sub: String, initial: String, hint: String, onDone: (String) -> Unit, onDismiss: () -> Unit) {
    var t by remember { mutableStateOf(initial) }
    androidx.compose.material3.AlertDialog(onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(sub, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                androidx.compose.material3.OutlinedTextField(t, { t = it }, singleLine = true, placeholder = { Text(hint) })
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { onDone(t) }) { Text("Guardar") } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

/** Dónde vas dentro de la biblioteca; vive fuera de la pestaña para no perderse al ir a «Sonando». */
private class LibNav {
    var page by mutableStateOf<String?>(null)
    var query by mutableStateOf("")
    var openAlbum by mutableStateOf<RAlbum?>(null)
    var openArtist by mutableStateOf<RArtist?>(null)
    var openPlaylist by mutableStateOf<RPlaylist?>(null)
    var openYear by mutableStateOf<Int?>(null)
    val canBack get() = openAlbum != null || openArtist != null || openPlaylist != null || openYear != null || query.isNotBlank() || page != null
    fun back() {
        when {
            openAlbum != null -> openAlbum = null
            openPlaylist != null -> openPlaylist = null
            openYear != null -> openYear = null
            openArtist != null -> openArtist = null
            query.isNotBlank() -> query = ""
            else -> page = null
        }
    }
}

private data class Shelf(val key: String, val label: String, val icon: ImageVector, val color: Color)

private val SHELVES = listOf(
    Shelf("lists", "Listas", Icons.Filled.QueueMusic, Color(0xFFFF7AC6)),
    Shelf("artists", "Artistas", Icons.Filled.Person, Color(0xFFB98CFF)),
    Shelf("albums", "Álbumes", Icons.Filled.Album, Color(0xFF7FB2FF)),
    Shelf("songs", "Canciones", Icons.Filled.MusicNote, Color(0xFF6EE7B7)),
    Shelf("recent", "Últimas agregadas", Icons.Filled.NewReleases, Color(0xFFFFC94D)),
    Shelf("top", "Más escuchadas", Icons.Filled.TrendingUp, Color(0xFFFF9A6B)),
    Shelf("years", "Por año", Icons.Filled.CalendarMonth, Color(0xFF8FD3FF)),
    Shelf("favs", "Favoritos", Icons.Filled.Favorite, Color(0xFFFF5A8A)),
)

private const val MUSIC = "media_kind is music"

@Composable
private fun LibraryTab(host: String, nav: LibNav, play: (String, Int, Boolean) -> Unit, playExpr: (String, Int, Boolean) -> Unit) {
    var page by nav::page          // null = menú principal
    var query by nav::query
    var openAlbum by nav::openAlbum
    var openArtist by nav::openArtist
    var openPlaylist by nav::openPlaylist
    var openYear by nav::openYear
    var loading by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    var reloadTick by remember { mutableStateOf(0) }
    val albums = remember { mutableStateListOf<RAlbum>() }
    val artists = remember { mutableStateListOf<RArtist>() }
    val tracks = remember { mutableStateListOf<RTrack>() }
    val playlists = remember { mutableStateListOf<RPlaylist>() }
    val years = remember { mutableStateListOf<Pair<Int, Int>>() }      // año → cuántos álbumes
    val recentShelf = remember { mutableStateListOf<RTrack>() }

    fun tracksExpr(e: String, limit: Int = 500) = tracksOf(JSONObject(api(host, "GET", "/api/search?type=tracks&expression=${enc(e)}&limit=$limit")).optJSONObject("tracks")?.items() ?: JSONArray(), host)
    fun albumsExpr(e: String, limit: Int = 500) = albumsOf(JSONObject(api(host, "GET", "/api/search?type=albums&expression=${enc(e)}&limit=$limit")).optJSONObject("albums")?.items() ?: JSONArray(), host)

    LaunchedEffect(page, query, openAlbum, openArtist, openPlaylist, openYear, reloadTick) {
        loading = true; msg = null
        if (query.isNotBlank()) delay(350)
        val res = withContext(Dispatchers.IO) {
            runCatching {
                var al: List<RAlbum> = emptyList(); var ar: List<RArtist> = emptyList(); var tr: List<RTrack> = emptyList()
                var pl: List<RPlaylist> = emptyList(); var ys: List<Pair<Int, Int>> = emptyList(); var recentTr: List<RTrack> = emptyList()
                val a = openAlbum; val art = openArtist; val p = openPlaylist; val y = openYear
                when {
                    p != null -> tr = tracksOf(JSONObject(api(host, "GET", "/api/library/playlists/${p.id}/tracks")).items(), host)
                    a != null -> tr = tracksOf(JSONObject(api(host, "GET", "/api/library/albums/${a.id}/tracks")).items(), host)
                    art != null -> al = albumsOf(JSONObject(api(host, "GET", "/api/library/artists/${art.id}/albums")).items(), host)
                    y != null -> al = albumsExpr("$MUSIC and year = $y order by album")
                    query.isNotBlank() -> {
                        val r = JSONObject(api(host, "GET", "/api/search?type=tracks,albums,artists&query=${enc(query)}"))
                        val aa = r.optJSONObject("artists")?.items() ?: JSONArray()
                        al = albumsOf(r.optJSONObject("albums")?.items() ?: JSONArray(), host)
                        ar = (0 until aa.length()).map { aa.getJSONObject(it) }.map { RArtist(it.optString("id"), it.optString("name"), it.optInt("album_count")) }
                        tr = tracksOf(r.optJSONObject("tracks")?.items() ?: JSONArray(), host)
                    }
                    page == null -> recentTr = tracksExpr("$MUSIC order by time_modified desc", 15)
                    page == "albums" -> al = albumsOf(JSONObject(api(host, "GET", "/api/library/albums?media_kind=music")).items(), host)
                    page == "artists" -> {
                        val aa = JSONObject(api(host, "GET", "/api/library/artists?media_kind=music")).items()
                        ar = (0 until aa.length()).map { aa.getJSONObject(it) }.map { RArtist(it.optString("id"), it.optString("name"), it.optInt("album_count")) }
                    }
                    page == "songs" -> tr = tracksExpr("$MUSIC order by title")
                    page == "lists" -> {
                        val ls = JSONObject(api(host, "GET", "/api/library/playlists")).items()
                        pl = (0 until ls.length()).map { ls.getJSONObject(it) }.filter { it.optString("type") != "folder" }
                            .map { RPlaylist(it.optString("id"), it.optString("name"), it.optInt("item_count")) }
                    }
                    page == "recent" -> tr = tracksExpr("$MUSIC order by time_modified desc", 200)
                    page == "top" -> tr = tracksExpr("$MUSIC and play_count > 0 order by play_count desc", 100)
                    page == "years" -> ys = albumsOf(JSONObject(api(host, "GET", "/api/library/albums?media_kind=music")).items(), host)
                        .filter { it.year > 0 }.groupingBy { it.year }.eachCount().toList().sortedByDescending { it.first }
                    page == "favs" -> tr = tracksExpr("$MUSIC and rating >= 80 order by title")
                }
                listOf(al, ar, tr, pl, ys, recentTr)
            }
        }
        res.onSuccess { l ->
            @Suppress("UNCHECKED_CAST")
            run {
                albums.clear(); albums.addAll(l[0] as List<RAlbum>); artists.clear(); artists.addAll(l[1] as List<RArtist>)
                tracks.clear(); tracks.addAll(l[2] as List<RTrack>); playlists.clear(); playlists.addAll(l[3] as List<RPlaylist>)
                years.clear(); years.addAll(l[4] as List<Pair<Int, Int>>); recentShelf.clear(); recentShelf.addAll(l[5] as List<RTrack>)
            }
            val empty = albums.isEmpty() && artists.isEmpty() && tracks.isEmpty() && playlists.isEmpty() && years.isEmpty()
            if (empty && (page != null || query.isNotBlank())) msg = when {
                query.isNotBlank() -> "Sin resultados."
                page == "lists" -> "No hay listas. Las listas son archivos .m3u dentro de tu carpeta Music."
                page == "favs" -> "Aún no tienes favoritos. Toca el ❤ en «Sonando» para agregar canciones."
                page == "top" -> "Todavía no hay canciones escuchadas desde el NAS."
                else -> "No hay nada aquí todavía."
            }
        }.onFailure { msg = "No se pudo leer la biblioteca: ${it.message}" }
        loading = false
    }

    val inside = openAlbum != null || openArtist != null || openPlaylist != null || openYear != null
    fun goBack() = nav.back()

    Column(Modifier.fillMaxSize()) {
        // encabezado
        if (inside || page != null) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.ArrowBack, "Atrás", tint = RC.text, modifier = Modifier.size(40.dp).clip(CircleShape).clickable { goBack() }.padding(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(openAlbum?.name ?: openPlaylist?.name ?: openYear?.toString() ?: openArtist?.name ?: SHELVES.firstOrNull { it.key == page }?.label.orEmpty(),
                        color = RC.text, fontWeight = FontWeight.Bold, fontSize = 22.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    openAlbum?.let { Text(it.artist + if (it.year > 0) " · ${it.year}" else "", color = RC.sub, fontSize = 12.sp, maxLines = 1) }
                }
                val uri = openAlbum?.let { "library:album:${it.id}" } ?: openPlaylist?.let { "library:playlist:${it.id}" } ?: openArtist?.let { "library:artist:${it.id}" }
                val expr = when { uri != null -> null; page == "songs" -> "$MUSIC order by title"; page == "favs" -> "$MUSIC and rating >= 80 order by title"
                    page == "top" -> "$MUSIC and play_count > 0 order by play_count desc"; page == "recent" -> "$MUSIC order by time_modified desc"; else -> null }
                if (uri != null || expr != null) {
                    Icon(Icons.Filled.Shuffle, "Aleatorio", tint = Color.White, modifier = Modifier.padding(end = 6.dp).size(38.dp).clip(CircleShape)
                        .background(Color(0x33FFFFFF)).clickable { if (uri != null) play(uri, 0, true) else playExpr(expr!!, 0, true) }.padding(9.dp))
                    Icon(Icons.Filled.PlayArrow, "Tocar todo", tint = Color.White, modifier = Modifier.size(38.dp).clip(CircleShape)
                        .background(Brush.linearGradient(listOf(RC.accent, RC.accent2))).clickable { if (uri != null) play(uri, 0, false) else playExpr(expr!!, 0, false) }.padding(7.dp))
                }
            }
        } else {
            Text("Biblioteca", color = RC.text, fontWeight = FontWeight.Black, fontSize = 32.sp, modifier = Modifier.padding(start = 16.dp, top = 4.dp))
        }
        if (!inside && page == null) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp).clip(RoundedCornerShape(14.dp)).background(RC.card).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Search, null, tint = RC.sub, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("Buscar en el NAS", color = RC.sub, fontSize = 15.sp)
                    BasicTextField(query, { query = it }, singleLine = true, textStyle = TextStyle(color = RC.text, fontSize = 15.sp),
                        cursorBrush = SolidColor(RC.accent), modifier = Modifier.fillMaxWidth())
                }
                if (query.isNotEmpty()) Icon(Icons.Filled.Close, "Borrar", tint = RC.sub, modifier = Modifier.size(20.dp).clickable { query = "" })
            }
        }
        if (loading) Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = RC.accent, strokeWidth = 2.dp) }
        msg?.let { Text(it, color = RC.sub, fontSize = 14.sp, modifier = Modifier.padding(20.dp)) }

        // menú principal estilo biblioteca de música
        if (!inside && page == null && query.isBlank()) {
            LazyColumn(Modifier.fillMaxSize()) {
                items(SHELVES, key = { it.key }) { sh ->
                    Row(Modifier.fillMaxWidth().clickable { page = sh.key }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(sh.color.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
                            Icon(sh.icon, null, tint = sh.color, modifier = Modifier.size(20.dp)) }
                        Spacer(Modifier.width(14.dp))
                        Text(sh.label, color = RC.text, fontSize = 18.sp, modifier = Modifier.weight(1f))
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = RC.sub)
                    }
                    Box(Modifier.padding(start = 64.dp).fillMaxWidth().height(1.dp).background(Color(0x14FFFFFF)))
                }
                if (recentShelf.isNotEmpty()) item {
                    Text("Agregadas recientemente", color = RC.text, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp))
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp)) {
                        recentShelf.forEachIndexed { i, t ->
                            Column(Modifier.width(140.dp).padding(6.dp).clip(RoundedCornerShape(14.dp)).clickable { playExpr("$MUSIC order by time_modified desc", i, false) }.padding(4.dp)) {
                                Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(RC.card), contentAlignment = Alignment.Center) {
                                    if (t.art != null) AsyncImage(t.art, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                    else Icon(Icons.Filled.MusicNote, null, tint = RC.sub, modifier = Modifier.size(40.dp))
                                }
                                Text(t.title, color = RC.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                                Text(t.artist, color = RC.sub, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                }
            }
            return@Column
        }

        // años
        if (page == "years" && !inside && query.isBlank()) {
            LazyVerticalGrid(GridCells.Adaptive(110.dp), Modifier.fillMaxSize().padding(horizontal = 10.dp)) {
                items(years, key = { "y" + it.first }) { (y, n) ->
                    Column(Modifier.padding(6.dp).clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(listOf(Color(0x558FD3FF), Color(0x33B98CFF))))
                        .clickable { openYear = y }.padding(vertical = 18.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$y", color = RC.text, fontWeight = FontWeight.Black, fontSize = 24.sp)
                        Text("$n álbumes", color = RC.sub, fontSize = 12.sp)
                    }
                }
            }
            return@Column
        }

        // listas
        if (page == "lists" && !inside && query.isBlank()) {
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 10.dp)) {
                items(playlists, key = { "pl" + it.id }) { pl ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { openPlaylist = pl }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(46.dp).clip(RoundedCornerShape(10.dp)).background(RC.card), contentAlignment = Alignment.Center) { Icon(Icons.Filled.QueueMusic, null, tint = RC.accent) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) { Text(pl.name, color = RC.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1); Text("${pl.count} canciones", color = RC.sub, fontSize = 12.sp) }
                        Icon(Icons.Filled.Shuffle, "Aleatorio", tint = RC.sub, modifier = Modifier.size(38.dp).clip(CircleShape).clickable { play("library:playlist:${pl.id}", 0, true) }.padding(9.dp))
                    }
                }
            }
            return@Column
        }

        // álbumes en cuadrícula (álbumes, recientes, un año, un artista)
        if (albums.isNotEmpty() && artists.isEmpty() && tracks.isEmpty() && query.isBlank()) {
            LazyVerticalGrid(GridCells.Adaptive(150.dp), Modifier.fillMaxSize().padding(horizontal = 10.dp)) {
                items(albums, key = { it.id }) { a -> AlbumCard(a) { openAlbum = a } }
            }
            return@Column
        }

        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 10.dp)) {
            if (artists.isNotEmpty()) {
                if (query.isNotBlank()) item { Section("Artistas") }
                items(artists, key = { "ar" + it.id }) { ar ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { query = ""; openArtist = ar }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(46.dp).clip(CircleShape).background(RC.card), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Person, null, tint = RC.sub) }
                        Spacer(Modifier.width(12.dp))
                        Column { Text(ar.name, color = RC.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold); Text("${ar.albums} álbumes", color = RC.sub, fontSize = 12.sp) }
                    }
                }
            }
            if (albums.isNotEmpty()) {
                if (query.isNotBlank()) item { Section("Álbumes") }
                items(albums, key = { "al" + it.id }) { a ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { query = ""; openArtist = null; openAlbum = a }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Cover(a.art, 50)
                        Spacer(Modifier.width(12.dp))
                        Column { Text(a.name, color = RC.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1); Text(a.artist, color = RC.sub, fontSize = 12.sp, maxLines = 1) }
                    }
                }
            }
            if (tracks.isNotEmpty()) {
                if (query.isNotBlank()) item { Section("Canciones") }
                itemsIndexed(tracks, key = { i, t -> "tr$i" + t.uri }) { i, t ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .clickable {
                            val a = openAlbum; val pl = openPlaylist
                            when {
                                a != null -> play("library:album:${a.id}", i, false)
                                pl != null -> play("library:playlist:${pl.id}", i, false)
                                query.isBlank() && page == "songs" -> playExpr("$MUSIC order by title", i, false)
                                query.isBlank() && page == "favs" -> playExpr("$MUSIC and rating >= 80 order by title", i, false)
                                query.isBlank() && page == "top" -> playExpr("$MUSIC and play_count > 0 order by play_count desc", i, false)
                                query.isBlank() && page == "recent" -> playExpr("$MUSIC order by time_modified desc", i, false)
                                else -> play(tracks.joinToString(",") { it.uri }, i, false)
                            }
                        }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (openAlbum != null || openPlaylist != null) Text("${i + 1}", color = RC.sub, fontSize = 13.sp, modifier = Modifier.width(30.dp))
                        else { Cover(t.art, 44); Spacer(Modifier.width(12.dp)) }
                        Column(Modifier.weight(1f)) {
                            Text(t.title, color = RC.text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(t.artist, color = RC.sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (t.rating >= 80) Icon(Icons.Filled.Favorite, null, tint = Color(0xFFFF5A8A), modifier = Modifier.size(16.dp).padding(end = 4.dp))
                        Text(fmt(t.lengthMs), color = RC.sub, fontSize = 12.sp)
                    }
                }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}

@Composable private fun Section(t: String) = Text(t, color = RC.accent, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(start = 10.dp, top = 12.dp, bottom = 4.dp))

@Composable
private fun Cover(url: String?, size: Int) {
    Box(Modifier.size(size.dp).clip(RoundedCornerShape(8.dp)).background(RC.card), contentAlignment = Alignment.Center) {
        if (url != null) AsyncImage(url, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else Icon(Icons.Filled.MusicNote, null, tint = RC.sub, modifier = Modifier.size((size / 2).dp))
    }
}

@Composable
private fun AlbumCard(a: RAlbum, onClick: () -> Unit) {
    Column(Modifier.padding(6.dp).clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(4.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(RC.card), contentAlignment = Alignment.Center) {
            if (a.art != null) AsyncImage(a.art, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else Icon(Icons.Filled.Album, null, tint = RC.sub, modifier = Modifier.size(48.dp))
        }
        Text(a.name, color = RC.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        Text(a.artist, color = RC.sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
