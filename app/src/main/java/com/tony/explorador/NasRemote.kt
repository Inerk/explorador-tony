package com.tony.explorador

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
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

private data class RAlbum(val id: String, val name: String, val artist: String, val art: String?, val tracks: Int)
private data class RArtist(val id: String, val name: String, val albums: Int)
private data class RTrack(val uri: String, val title: String, val artist: String, val album: String, val lengthMs: Long, val art: String?)
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
    val outputs = mutableStateListOf<ROutput>()
}

private fun api(host: String, method: String, path: String, body: String? = null) = AirPlayBridge.http(method, host, path, body)
private fun artUrl(host: String, rel: String?) = rel?.takeIf { it.isNotBlank() }?.let { if (it.startsWith("http")) it else "http://$host:${AirPlayBridge.PORT}$it" }
private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
private fun fmt(ms: Long): String { val t = (ms / 1000).coerceAtLeast(0); return "%d:%02d".format(t / 60, t % 60) }

private fun JSONObject.items(): JSONArray = optJSONArray("items") ?: JSONArray()
private fun albumsOf(a: JSONArray, host: String) = (0 until a.length()).map { a.getJSONObject(it) }.map {
    RAlbum(it.optString("id"), it.optString("name"), it.optString("artist"), artUrl(host, it.optString("artwork_url")), it.optInt("track_count"))
}
private fun tracksOf(a: JSONArray, host: String) = (0 until a.length()).map { a.getJSONObject(it) }.map {
    RTrack(it.optString("uri"), it.optString("title"), it.optString("artist"), it.optString("album"), it.optLong("length_ms"), artUrl(host, it.optString("artwork_url")))
}

@Composable
fun NasRemoteScreen(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val st = remember { RState() }
    var tab by remember { mutableStateOf(0) }           // 0 sonando · 1 biblioteca · 2 bocinas
    var reload by remember { mutableStateOf(0) }
    BackHandler { onClose() }

    // encontrar el NAS
    LaunchedEffect(reload) {
        st.searching = true; st.error = null
        st.host = withContext(Dispatchers.IO) { runCatching { AirPlayBridge.findHost(ctx) }.getOrNull() }
        st.searching = false
        if (st.host == null) st.error = "No encontré el NAS con OwnTone (${AirPlayBridge.lastInfo})."
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
                    st.pos = p.optLong("item_progress_ms"); st.len = p.optLong("item_length_ms")
                    val itemId = p.optLong("item_id", -1)
                    if (itemId >= 0) {
                        val q = JSONObject(api(h, "GET", "/api/queue")).items()
                        for (i in 0 until q.length()) {
                            val it = q.getJSONObject(i)
                            if (it.optLong("id") == itemId) {
                                st.title = it.optString("title"); st.artist = it.optString("artist")
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
    fun playUris(uris: String, from: Int = 0) {
        if (st.outputs.none { it.selected }) { st.error = "Primero elige una bocina en la pestaña «Bocinas»."; tab = 2; return }
        st.error = null
        call("POST", "/api/queue/items/add?clear=true&playback=start&playback_from_position=$from&uris=${enc(uris)}")
        tab = 0
    }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(RC.bg2, RC.bg1)))) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(androidx.compose.foundation.layout.WindowInsetsSides.Top + androidx.compose.foundation.layout.WindowInsetsSides.Horizontal))) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.ArrowBack, "Cerrar", tint = RC.text, modifier = Modifier.size(44.dp).clip(CircleShape).clickable { onClose() }.padding(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Control remoto", color = RC.text, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                    Text(st.host?.let { "Música del NAS · $it" } ?: if (st.searching) "Buscando el NAS…" else "Sin conexión", color = RC.sub, fontSize = 12.sp)
                }
                if (st.searching) CircularProgressIndicator(color = RC.accent, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                else Icon(Icons.Filled.Refresh, "Buscar otra vez", tint = RC.sub, modifier = Modifier.size(44.dp).clip(CircleShape).clickable { reload++ }.padding(10.dp))
            }
            st.error?.let {
                Text(it, color = Color(0xFFFFB4C8), fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(10.dp)).background(Color(0x33FF4F7A)).clickable { st.error = null }.padding(10.dp).fillMaxWidth())
            }
            Box(Modifier.weight(1f)) {
                val h = st.host
                if (h != null) when (tab) {
                    0 -> NowPlayingTab(st, ::call)
                    1 -> LibraryTab(h, ::playUris)
                    2 -> SpeakersTab(st, ::call)
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
        val names = st.outputs.filter { it.selected }.joinToString(", ") { it.name }
        if (names.isNotBlank()) Text("🔊 $names", color = RC.accent, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(12.dp))
        val len = st.len.coerceAtLeast(1)
        Slider(dragging ?: (st.pos.toFloat() / len).coerceIn(0f, 1f), { dragging = it },
            onValueChangeFinished = { dragging?.let { call("PUT", "/api/player/seek?position_ms=${(it * len).toLong()}", null) }; dragging = null },
            colors = SliderDefaults.colors(thumbColor = RC.accent, activeTrackColor = RC.accent, inactiveTrackColor = RC.card))
        Row(Modifier.fillMaxWidth()) {
            Text(fmt(st.pos), color = RC.sub, fontSize = 11.sp); Spacer(Modifier.weight(1f)); Text(fmt(st.len), color = RC.sub, fontSize = 11.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(22.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            Icon(Icons.Filled.SkipPrevious, "Anterior", tint = RC.text, modifier = Modifier.size(52.dp).clip(CircleShape).clickable { call("PUT", "/api/player/previous", null) }.padding(8.dp))
            Box(Modifier.size(76.dp).clip(CircleShape).background(Brush.linearGradient(listOf(RC.accent, RC.accent2)))
                .clickable { call("PUT", if (st.state == "play") "/api/player/pause" else "/api/player/play", null); st.state = if (st.state == "play") "pause" else "play" },
                contentAlignment = Alignment.Center) {
                Icon(if (st.state == "play") Icons.Filled.Pause else Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(40.dp))
            }
            Icon(Icons.Filled.SkipNext, "Siguiente", tint = RC.text, modifier = Modifier.size(52.dp).clip(CircleShape).clickable { call("PUT", "/api/player/next", null) }.padding(8.dp))
        }
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
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        item {
            Text("Toca para encender o apagar cada bocina. Puedes usar varias a la vez.", color = RC.sub, fontSize = 13.sp, modifier = Modifier.padding(vertical = 10.dp))
        }
        items(st.outputs, key = { it.id }) { o ->
            var v by remember(o.id) { mutableStateOf<Float?>(null) }
            Column(Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(16.dp)).background(if (o.selected) Color(0x33B98CFF) else RC.card)
                .clickable {
                    call("PUT", "/api/outputs/${o.id}", JSONObject().put("selected", !o.selected).toString())
                    val i = st.outputs.indexOfFirst { it.id == o.id }; if (i >= 0) st.outputs[i] = o.copy(selected = !o.selected)
                }.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Speaker, null, tint = if (o.selected) RC.accent else RC.sub, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(o.name, color = RC.text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Text(o.type, color = RC.sub, fontSize = 12.sp)
                    }
                    Icon(if (o.selected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked, null, tint = if (o.selected) RC.accent else RC.sub)
                }
                if (o.selected) Slider(v ?: (o.volume / 100f), { v = it },
                    onValueChangeFinished = { v?.let { call("PUT", "/api/outputs/${o.id}", JSONObject().put("volume", (it * 100).toInt()).toString()) }; v = null },
                    colors = SliderDefaults.colors(thumbColor = RC.accent, activeTrackColor = RC.accent, inactiveTrackColor = Color(0x33FFFFFF)))
            }
        }
        if (st.outputs.isEmpty()) item { Text("El NAS no ve bocinas todavía.", color = RC.sub, modifier = Modifier.padding(16.dp)) }
    }
}

@Composable
private fun LibraryTab(host: String, play: (String, Int) -> Unit) {
    var mode by remember { mutableStateOf(0) }           // 0 álbumes · 1 artistas · 2 canciones
    var query by remember { mutableStateOf("") }
    var openAlbum by remember { mutableStateOf<RAlbum?>(null) }
    var openArtist by remember { mutableStateOf<RArtist?>(null) }
    var loading by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    val albums = remember { mutableStateListOf<RAlbum>() }
    val artists = remember { mutableStateListOf<RArtist>() }
    val tracks = remember { mutableStateListOf<RTrack>() }

    LaunchedEffect(mode, query, openAlbum, openArtist) {
        loading = true; msg = null
        if (query.isNotBlank()) delay(350)
        val res = withContext(Dispatchers.IO) {
            runCatching {
                val a = openAlbum; val ar = openArtist
                when {
                    a != null -> Triple(emptyList(), emptyList(), tracksOf(JSONObject(api(host, "GET", "/api/library/albums/${a.id}/tracks")).items(), host))
                    ar != null -> Triple(albumsOf(JSONObject(api(host, "GET", "/api/library/artists/${ar.id}/albums")).items(), host), emptyList(), emptyList())
                    query.isNotBlank() -> {
                        val r = JSONObject(api(host, "GET", "/api/search?type=tracks,albums,artists&query=${enc(query)}"))
                        val art = r.optJSONObject("artists")?.items() ?: JSONArray()
                        Triple(albumsOf(r.optJSONObject("albums")?.items() ?: JSONArray(), host),
                            (0 until art.length()).map { art.getJSONObject(it) }.map { RArtist(it.optString("id"), it.optString("name"), it.optInt("album_count")) },
                            tracksOf(r.optJSONObject("tracks")?.items() ?: JSONArray(), host))
                    }
                    mode == 0 -> Triple(albumsOf(JSONObject(api(host, "GET", "/api/library/albums?media_kind=music")).items(), host), emptyList(), emptyList())
                    mode == 1 -> {
                        val art = JSONObject(api(host, "GET", "/api/library/artists?media_kind=music")).items()
                        Triple(emptyList(), (0 until art.length()).map { art.getJSONObject(it) }.map { RArtist(it.optString("id"), it.optString("name"), it.optInt("album_count")) }, emptyList())
                    }
                    else -> {
                        val r = JSONObject(api(host, "GET", "/api/search?type=tracks&expression=${enc("media_kind is music order by title")}&limit=500"))
                        Triple(emptyList(), emptyList(), tracksOf(r.optJSONObject("tracks")?.items() ?: JSONArray(), host))
                    }
                }
            }
        }
        res.onSuccess { (al, ar, tr) ->
            albums.clear(); albums.addAll(al); artists.clear(); artists.addAll(ar); tracks.clear(); tracks.addAll(tr)
            if (al.isEmpty() && ar.isEmpty() && tr.isEmpty()) msg = if (query.isBlank() && openAlbum == null && openArtist == null)
                "El NAS no tiene música en su biblioteca todavía. Hay que conectarle la carpeta de música del NAS a OwnTone." else "Sin resultados."
        }.onFailure { msg = "No se pudo leer la biblioteca: ${it.message}" }
        loading = false
    }

    Column(Modifier.fillMaxSize()) {
        val back = openAlbum != null || openArtist != null
        if (back) {
            BackHandler { if (openAlbum != null) openAlbum = null else openArtist = null }
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.ArrowBack, "Atrás", tint = RC.text, modifier = Modifier.size(40.dp).clip(CircleShape)
                    .clickable { if (openAlbum != null) openAlbum = null else openArtist = null }.padding(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(openAlbum?.name ?: openArtist?.name.orEmpty(), color = RC.text, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    openAlbum?.let { Text(it.artist, color = RC.sub, fontSize = 12.sp, maxLines = 1) }
                }
                openAlbum?.let { a ->
                    Text("Tocar todo", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.clip(CircleShape)
                        .background(Brush.linearGradient(listOf(RC.accent, RC.accent2))).clickable { play("library:album:${a.id}", 0) }.padding(horizontal = 14.dp, vertical = 8.dp))
                }
            }
        } else {
            // buscador y filtros
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp).clip(RoundedCornerShape(14.dp)).background(RC.card).padding(horizontal = 12.dp, vertical = 10.dp),
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
            if (query.isBlank()) Row(Modifier.padding(horizontal = 14.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Álbumes", "Artistas", "Canciones").forEachIndexed { i, l ->
                    Text(l, color = if (mode == i) Color.White else RC.sub, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                        modifier = Modifier.clip(CircleShape).background(if (mode == i) RC.accent.copy(alpha = 0.55f) else RC.card).clickable { mode = i }
                            .padding(horizontal = 14.dp, vertical = 7.dp))
                }
            }
        }
        if (loading) Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = RC.accent, strokeWidth = 2.dp) }
        msg?.let { Text(it, color = RC.sub, fontSize = 14.sp, modifier = Modifier.padding(20.dp)) }

        val showAlbumsGrid = albums.isNotEmpty() && artists.isEmpty() && tracks.isEmpty() && query.isBlank()
        if (showAlbumsGrid) {
            LazyVerticalGrid(GridCells.Adaptive(150.dp), Modifier.fillMaxSize().padding(horizontal = 10.dp)) {
                items(albums, key = { it.id }) { a -> AlbumCard(a) { openAlbum = a } }
            }
        } else LazyColumn(Modifier.fillMaxSize().padding(horizontal = 10.dp)) {
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
                        .clickable { val a = openAlbum; if (a != null) play("library:album:${a.id}", i) else play(t.uri, 0) }
                        .padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (openAlbum != null) Text("${i + 1}", color = RC.sub, fontSize = 13.sp, modifier = Modifier.width(30.dp)) else { Cover(t.art, 44); Spacer(Modifier.width(12.dp)) }
                        Column(Modifier.weight(1f)) {
                            Text(t.title, color = RC.text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(t.artist, color = RC.sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
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
