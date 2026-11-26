package com.tony.explorador

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.SubcomposeAsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/** Una estación de radio por internet (datos de radio-browser.info). */
data class RadioStation(
    val uuid: String, val name: String, val url: String, val logo: String = "",
    val country: String = "", val cc: String = "", val state: String = "", val tags: String = "",
    val codec: String = "", val bitrate: Int = 0, val homepage: String = "", val language: String = "",
) {
    fun toJson(): JSONObject = JSONObject().put("uuid", uuid).put("name", name).put("url", url).put("logo", logo)
        .put("country", country).put("cc", cc).put("state", state).put("tags", tags).put("codec", codec)
        .put("bitrate", bitrate).put("homepage", homepage).put("language", language)
    /** Ciudad / estado y país, para mostrar. */
    val place: String get() = listOf(state, country).filter { it.isNotBlank() }.distinct().joinToString(", ")
    /** Los primeros géneros, con mayúscula. */
    val genres: List<String> get() = tags.split(',').map { it.trim() }.filter { it.isNotBlank() && it.length < 22 }
        .distinct().take(3).map { it.replaceFirstChar { c -> c.uppercase() } }
    val quality: String get() = listOfNotNull(bitrate.takeIf { it > 0 }?.let { "$it kbps" }, codec.takeIf { it.isNotBlank() && it != "UNKNOWN" }).joinToString(" · ")
    val flag: String get() = if (cc.length == 2) cc.uppercase().map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }.joinToString("") else ""

    companion object {
        fun from(o: JSONObject) = RadioStation(
            uuid = o.optString("stationuuid", o.optString("uuid")), name = o.optString("name").trim(),
            url = o.optString("url_resolved").ifBlank { o.optString("url") },
            logo = o.optString("favicon", o.optString("logo")), country = o.optString("country"),
            cc = o.optString("countrycode", o.optString("cc")), state = o.optString("state"), tags = o.optString("tags"),
            codec = o.optString("codec"), bitrate = o.optInt("bitrate"), homepage = o.optString("homepage"), language = o.optString("language"),
        )
    }
}

/** Buscador de estaciones (radio-browser.info, gratis y sin cuenta). */
object RadioApi {
    private const val BASE = "https://all.api.radio-browser.info/json"
    private fun get(path: String): String = (java.net.URL(BASE + path).openConnection() as java.net.HttpURLConnection).run {
        connectTimeout = 8000; readTimeout = 12000
        setRequestProperty("User-Agent", "ControlNebula/1.0")
        inputStream.bufferedReader().use { it.readText() }
    }
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    private fun list(path: String): List<RadioStation> {
        val a = JSONArray(get(path))
        return (0 until a.length()).map { RadioStation.from(a.getJSONObject(it)) }
            .filter { it.url.startsWith("http") && it.name.isNotBlank() }
            .distinctBy { it.name.lowercase().replace(Regex("[^a-z0-9]"), "") }
    }
    private const val COMMON = "&hidebroken=true&order=clickcount&reverse=true"
    fun popular(mexico: Boolean, limit: Int = 60) = list("/stations/search?limit=$limit$COMMON" + (if (mexico) "&countrycode=MX" else ""))
    fun byTag(tag: String, mexico: Boolean, limit: Int = 60) = list("/stations/search?limit=$limit&tag=${enc(tag)}$COMMON" + (if (mexico) "&countrycode=MX" else ""))
    fun search(q: String, mexico: Boolean, limit: Int = 60) = list("/stations/search?limit=$limit&name=${enc(q)}$COMMON" + (if (mexico) "&countrycode=MX" else ""))
    /** Le avisa al directorio que se escuchó (así ordena las populares). */
    fun click(uuid: String) { if (uuid.isNotBlank()) runCatching { get("/url/$uuid") } }
}

/** Favoritas (se guardan también en el NAS para todos los teléfonos) y las que escuchaste hace poco. */
object RadioStore {
    val favorites = mutableStateListOf<RadioStation>()
    val recent = mutableStateListOf<RadioStation>()
    private var prefs: android.content.SharedPreferences? = null

    fun init(c: android.content.Context) {
        if (prefs != null) return
        val p = c.applicationContext.getSharedPreferences("radio", android.content.Context.MODE_PRIVATE); prefs = p
        favorites.addAll(parse(p.getString("favoritas", "[]")!!))
        recent.addAll(parse(p.getString("recientes", "[]")!!))
    }
    private fun parse(s: String): List<RadioStation> = runCatching {
        val a = JSONArray(s); (0 until a.length()).map { RadioStation.from(a.getJSONObject(it)) }
    }.getOrDefault(emptyList())
    fun favJson(): JSONArray = JSONArray().apply { favorites.forEach { put(it.toJson()) } }
    private fun save() {
        prefs?.edit()?.putString("favoritas", favJson().toString())
            ?.putString("recientes", JSONArray().apply { recent.forEach { put(it.toJson()) } }.toString())?.apply()
    }
    fun isFav(s: RadioStation) = favorites.any { it.url == s.url || (s.uuid.isNotBlank() && it.uuid == s.uuid) }
    fun toggleFav(s: RadioStation) {
        if (isFav(s)) favorites.removeAll { it.url == s.url || (s.uuid.isNotBlank() && it.uuid == s.uuid) } else favorites.add(0, s)
        save(); RemotePrefs.pushAll()
    }
    fun played(s: RadioStation) {
        recent.removeAll { it.url == s.url }; recent.add(0, s)
        while (recent.size > 15) recent.removeAt(recent.lastIndex)
        save()
    }
    /** La estación que corresponde a lo que está sonando en el NAS. */
    fun byUrl(u: String): RadioStation? = favorites.firstOrNull { it.url == u } ?: recent.firstOrNull { it.url == u }
    /** Favoritas que llegan del NAS (de otro teléfono). */
    fun replaceFavorites(a: JSONArray?, merge: Boolean) {
        val remote = parse(a?.toString() ?: "[]")
        val local = favorites.toList()
        favorites.clear(); favorites.addAll(remote)
        if (merge) local.forEach { l -> if (favorites.none { it.url == l.url }) favorites.add(l) }
        save()
    }
}

private val Genres = listOf(
    "Populares" to "", "Pop" to "pop", "Regional" to "regional mexican", "Banda" to "banda", "Rock" to "rock",
    "Baladas" to "ballads", "Noticias" to "news", "Clásicos" to "oldies", "Electrónica" to "electronic",
    "Jazz" to "jazz", "Clásica" to "classical", "Cristiana" to "christian",
)

/** Logo de la estación; si no tiene, sus iniciales sobre un degradado. */
@Composable
fun StationLogo(s: RadioStation?, modifier: Modifier, corner: Int = 16, big: Boolean = false) {
    val shape = RoundedCornerShape(corner.dp)
    val seed = (s?.name ?: "").hashCode()
    val c1 = Color.hsv(((seed and 0xFFFF) % 360).toFloat(), 0.55f, 0.75f)
    val c2 = Color.hsv((((seed ushr 8) and 0xFFFF) % 360).toFloat(), 0.6f, 0.45f)
    Box(modifier.clip(shape).background(Brush.linearGradient(listOf(c1, c2))), contentAlignment = Alignment.Center) {
        val initials = (s?.name ?: "").split(Regex("\\s+")).filter { it.firstOrNull()?.isLetterOrDigit() == true }.take(2).joinToString("") { it.take(1).uppercase() }
        @Composable fun Fallback() {
            if (initials.isBlank()) Icon(Icons.Filled.Radio, null, tint = Color.White, modifier = Modifier.fillMaxSize(0.45f))
            else Text(initials, color = Color.White, fontWeight = FontWeight.Black, fontSize = if (big) 64.sp else 18.sp)
        }
        val logo = s?.logo?.takeIf { it.startsWith("http") }
        if (logo == null) Fallback()
        else {
            // fondo blanco suave: casi todos los logos están hechos para fondo claro
            SubcomposeAsyncImage(logo, s.name, contentScale = ContentScale.Fit,
                loading = { Fallback() }, error = { Fallback() },
                success = {
                    val p = this.painter
                    Box(Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
                        androidx.compose.foundation.Image(p, s.name, contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().padding(if (big) 22.dp else 5.dp))
                    }
                },
                modifier = Modifier.fillMaxSize())
        }
    }
}

/** Barras que bailan junto a la estación que suena. */
@Composable
private fun LiveBars(color: Color, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "bars")
    val a by t.animateFloat(0.2f, 1f, infiniteRepeatable(tween(420), RepeatMode.Reverse), label = "a")
    val b by t.animateFloat(1f, 0.25f, infiniteRepeatable(tween(530), RepeatMode.Reverse), label = "b")
    val c by t.animateFloat(0.35f, 0.9f, infiniteRepeatable(tween(360), RepeatMode.Reverse), label = "c")
    Canvas(modifier.size(18.dp, 16.dp)) {
        val w = size.width / 5f
        listOf(a, b, c).forEachIndexed { i, v ->
            drawRoundRect(color, androidx.compose.ui.geometry.Offset(i * w * 2f, size.height * (1f - v)),
                androidx.compose.ui.geometry.Size(w, size.height * v), androidx.compose.ui.geometry.CornerRadius(w / 2))
        }
    }
}

/** Punto rojo que late con la palabra EN VIVO. */
@Composable
fun LiveBadge(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "live")
    val p by t.animateFloat(0.35f, 1f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "p")
    Row(modifier.clip(RoundedCornerShape(12.dp)).background(Color(0x33FF3B5C)).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFFF3B5C).copy(alpha = p)))
        Spacer(Modifier.width(6.dp))
        Text("EN VIVO", color = Color(0xFFFF8FA3), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
    }
}

@Composable
private fun Chip(text: String, on: Boolean = false, onClick: (() -> Unit)? = null) {
    Text(text, color = if (on) Color.White else RC.text, fontSize = 13.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
        modifier = Modifier.clip(RoundedCornerShape(16.dp))
            .then(if (on) Modifier.background(Brush.linearGradient(listOf(RC.accent, RC.accent2))) else Modifier.glass(RoundedCornerShape(16.dp)))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 7.dp))
}

/** Pestaña «Radio» del control remoto: buscar, géneros, favoritas y estaciones populares. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun RadioTab(nowUrl: String?, playing: Boolean, onPlay: (RadioStation) -> Unit) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { RadioStore.init(ctx) }
    var mexico by remember { mutableStateOf(true) }
    var genre by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var list by remember { mutableStateOf<List<RadioStation>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var detail by remember { mutableStateOf<RadioStation?>(null) }

    LaunchedEffect(mexico, genre, query) {
        if (query.isNotBlank()) delay(450)
        loading = true; error = null
        val r = withContext(Dispatchers.IO) {
            runCatching {
                when {
                    query.isNotBlank() -> RadioApi.search(query.trim(), mexico)
                    genre.isNotBlank() -> RadioApi.byTag(genre, mexico).let { if (it.size < 6 && mexico) it + RadioApi.byTag(genre, false, 30) else it }
                    else -> RadioApi.popular(mexico)
                }
            }
        }
        r.onSuccess { list = it.distinctBy { s -> s.url } }.onFailure { error = "No pude cargar las estaciones. Revisa el internet del teléfono." }
        loading = false
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 12.dp)) {
        item {
            // encabezado
            Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 12.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Radio", color = RC.text, fontSize = 30.sp, fontWeight = FontWeight.Black)
                    Text("En vivo, en tus bocinas", color = RC.sub, fontSize = 13.sp, maxLines = 1)
                }
                Row(Modifier.glass(RoundedCornerShape(18.dp)).padding(3.dp)) {
                    listOf(true to "🇲🇽 México", false to "🌎 Mundo").forEach { (mx, l) ->
                        Text(l, color = if (mexico == mx) Color.White else RC.sub, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clip(RoundedCornerShape(15.dp)).background(if (mexico == mx) RC.accent.copy(alpha = 0.45f) else Color.Transparent)
                                .clickable { mexico = mx }.padding(horizontal = 10.dp, vertical = 6.dp))
                    }
                }
            }
            // buscador
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp).glass(RoundedCornerShape(22.dp)).padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Search, null, tint = RC.sub, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("Busca una estación (ej. «La Mejor», «Los 40»)", color = RC.sub.copy(alpha = 0.7f), fontSize = 14.sp, maxLines = 1)
                    BasicTextField(query, { query = it }, singleLine = true, textStyle = TextStyle(color = RC.text, fontSize = 14.sp),
                        cursorBrush = SolidColor(RC.accent), modifier = Modifier.fillMaxWidth())
                }
                if (query.isNotEmpty()) Icon(Icons.Filled.Close, "Borrar", tint = RC.sub, modifier = Modifier.size(22.dp).clip(CircleShape).clickable { query = "" })
            }
            // géneros
            if (query.isBlank()) Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Genres.forEach { (label, tag) -> Chip(label, genre == tag) { genre = tag } }
            }
        }
        // favoritas, en tarjetas grandes
        if (query.isBlank() && RadioStore.favorites.isNotEmpty()) item {
            RadioSection("Tus favoritas")
            LazyRow(contentPadding = PaddingValues(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(RadioStore.favorites, key = { "f" + it.url }) { s ->
                    StationCard(s, s.url == nowUrl, playing, onClick = { onPlay(s) }, onLong = { detail = s })
                }
            }
        }
        if (query.isBlank() && genre.isBlank() && RadioStore.recent.isNotEmpty()) item {
            RadioSection("Escuchadas hace poco")
            LazyRow(contentPadding = PaddingValues(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(RadioStore.recent.take(10), key = { "r" + it.url }) { s ->
                    StationCard(s, s.url == nowUrl, playing, small = true, onClick = { onPlay(s) }, onLong = { detail = s })
                }
            }
        }
        item {
            RadioSection(when {
                query.isNotBlank() -> "Resultados"
                genre.isNotBlank() -> Genres.first { it.second == genre }.first + if (mexico) " en México" else " en el mundo"
                else -> if (mexico) "Las más escuchadas en México" else "Las más escuchadas del mundo"
            })
            if (loading && list.isEmpty()) Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = RC.accent, strokeWidth = 2.dp)
            }
            error?.let { Text(it, color = Color(0xFFFFB4C8), fontSize = 13.sp, modifier = Modifier.padding(18.dp)) }
            if (!loading && error == null && list.isEmpty()) Text("No encontré estaciones.", color = RC.sub, fontSize = 13.sp, modifier = Modifier.padding(18.dp))
        }
        items(list, key = { "l" + it.url }) { s ->
            StationRow(s, s.url == nowUrl, playing, onClick = { onPlay(s) }, onInfo = { detail = s })
        }
        item {
            Text("Estaciones de radio-browser.info · mantén presionada una tarjeta para ver su información",
                color = RC.sub.copy(alpha = 0.6f), fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(16.dp))
        }
    }
    detail?.let { s -> StationSheet(s, s.url == nowUrl, onPlay = { onPlay(s); detail = null }, onDismiss = { detail = null }) }
}

@Composable
private fun RadioSection(t: String) = Text(t, color = RC.text, fontWeight = FontWeight.Bold, fontSize = 18.sp,
    modifier = Modifier.padding(start = 18.dp, top = 18.dp, bottom = 10.dp))

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StationCard(s: RadioStation, now: Boolean, playing: Boolean, small: Boolean = false, onClick: () -> Unit, onLong: () -> Unit) {
    val w = if (small) 104.dp else 132.dp
    Column(Modifier.width(w).clip(RoundedCornerShape(18.dp)).combinedClickable(onClick = onClick, onLongClick = onLong)) {
        Box {
            StationLogo(s, Modifier.width(w).aspectRatio(1f)
                .then(if (now) Modifier.border(2.5.dp, Brush.linearGradient(listOf(RC.accent, RC.accent2)), RoundedCornerShape(18.dp)) else Modifier), corner = 18)
            if (now) Box(Modifier.align(Alignment.BottomEnd).padding(6.dp).clip(CircleShape).background(Color(0xCC140F2B)).padding(7.dp)) {
                if (playing) LiveBars(RC.accent) else Icon(Icons.Filled.Pause, null, tint = RC.accent, modifier = Modifier.size(16.dp))
            }
        }
        Text(s.name, color = RC.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp))
        Text((s.genres.firstOrNull() ?: s.place).ifBlank { " " }, color = RC.sub, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 2.dp, end = 2.dp))
    }
}

@Composable
private fun StationRow(s: RadioStation, now: Boolean, playing: Boolean, onClick: () -> Unit, onInfo: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clip(RoundedCornerShape(18.dp))
        .then(if (now) Modifier.glass(RoundedCornerShape(18.dp), strong = true) else Modifier)
        .clickable(onClick = onClick).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        StationLogo(s, Modifier.size(58.dp), corner = 14)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (now && playing) { LiveBars(RC.accent); Spacer(Modifier.width(6.dp)) }
                Text(s.name, color = if (now) RC.accent else RC.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(listOf(s.flag + " " + s.place, s.genres.joinToString(" · ")).map { it.trim() }.filter { it.isNotBlank() }.joinToString("  ·  "),
                color = RC.sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (s.quality.isNotBlank()) Text(s.quality, color = RC.sub.copy(alpha = 0.7f), fontSize = 11.sp)
        }
        val fav = RadioStore.isFav(s)
        Icon(if (fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "Favorita", tint = if (fav) Color(0xFFFF5A8A) else RC.sub,
            modifier = Modifier.size(40.dp).clip(CircleShape).clickable { RadioStore.toggleFav(s) }.padding(9.dp))
        Icon(Icons.Filled.Info, "Información", tint = RC.sub, modifier = Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onInfo).padding(9.dp))
    }
}

/** Ficha de la estación: logo grande, de dónde es, géneros, calidad y su página. */
@Composable
private fun StationSheet(s: RadioStation, now: Boolean, onPlay: () -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxWidth().padding(18.dp).clip(RoundedCornerShape(28.dp)).background(Brush.verticalGradient(listOf(RC.bg2, RC.bg1)))) {
            // el logo borroso de fondo
            StationLogo(s, Modifier.matchParentSize().blur(60.dp), corner = 0)
            Box(Modifier.matchParentSize().background(Color(0xCC140F2B)))
            Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                StationLogo(s, Modifier.size(170.dp), corner = 30, big = true)
                Spacer(Modifier.height(14.dp))
                Text(s.name, color = RC.text, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (s.place.isNotBlank()) Text("${s.flag} ${s.place}".trim(), color = RC.sub, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp))
                if (s.genres.isNotEmpty()) Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) { s.genres.forEach { Chip(it) } }
                Column(Modifier.fillMaxWidth().padding(top = 16.dp).glass(RoundedCornerShape(18.dp)).padding(14.dp)) {
                    InfoLine("Calidad", s.quality.ifBlank { "—" })
                    InfoLine("Idioma", s.language.split(',').firstOrNull()?.trim()?.lowercase()?.let { l ->
                        mapOf("spanish" to "Español", "español" to "Español", "english" to "Inglés", "portuguese" to "Portugués",
                            "french" to "Francés", "italian" to "Italiano", "german" to "Alemán", "japanese" to "Japonés", "korean" to "Coreano")[l]
                            ?: l.replaceFirstChar { it.uppercase() } }?.ifBlank { null } ?: "—")
                    InfoLine("Sitio", s.homepage.removePrefix("https://").removePrefix("http://").removeSuffix("/").ifBlank { "—" })
                }
                Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(26.dp)).background(Brush.linearGradient(listOf(RC.accent, RC.accent2)))
                        .clickable(onClick = onPlay), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.PlayArrow, null, tint = Color.White)
                        Spacer(Modifier.width(6.dp))
                        Text(if (now) "Sonando" else "Escuchar", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    val fav = RadioStore.isFav(s)
                    Icon(if (fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "Favorita", tint = if (fav) Color(0xFFFF5A8A) else RC.text,
                        modifier = Modifier.size(52.dp).glass(CircleShape).clickable { RadioStore.toggleFav(s) }.padding(13.dp))
                    if (s.homepage.startsWith("http")) Icon(Icons.Filled.Language, "Página de la estación", tint = RC.text,
                        modifier = Modifier.size(52.dp).glass(CircleShape).clickable {
                            runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(s.homepage))) }
                        }.padding(13.dp))
                }
            }
        }
    }
}


@Composable
private fun InfoLine(k: String, v: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(k, color = RC.sub, fontSize = 13.sp, modifier = Modifier.width(72.dp))
        Text(v, color = RC.text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    }
}

/** «Sonando» cuando lo que suena es una estación de radio. */
@Composable
internal fun RadioNowPlaying(
    station: RadioStation, songTitle: String, playing: Boolean, volume: Int, speakers: String, host: String?,
    onToggle: () -> Unit, onVolume: (Int) -> Unit, onSwitch: (RadioStation) -> Unit,
) {
    var info by remember { mutableStateOf(false) }
    // anterior / siguiente: entre tus favoritas (o las recientes si esta no es favorita)
    val ring = (if (RadioStore.isFav(station)) RadioStore.favorites else RadioStore.recent).toList()
    val i = ring.indexOfFirst { it.url == station.url }
    // la canción que manda la estación suele venir como «Artista - Canción»
    val song = songTitle.trim().takeIf { it.isNotBlank() && !it.equals(station.name, true) && !it.startsWith("http") }
    val (songName, songArtist) = song?.split(" - ", limit = 2)?.let { if (it.size == 2) it[1].trim() to it[0].trim() else it[0] to "" } ?: ("" to "")
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.weight(1f, fill = false).fillMaxWidth(0.78f).aspectRatio(1f, matchHeightConstraintsFirst = true)) {
            StationLogo(station, Modifier.fillMaxSize().clickable { info = true }, corner = 30, big = true)
            LiveBadge(Modifier.align(Alignment.TopStart).padding(12.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xAA140F2B)))
        }
        Spacer(Modifier.height(18.dp))
        Text(station.name, color = RC.text, fontWeight = FontWeight.Bold, fontSize = 22.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        Text(listOf("${station.flag} ${station.place}".trim(), station.genres.take(2).joinToString(" · ")).filter { it.isNotBlank() }.joinToString("  ·  ").ifBlank { "Radio en vivo" },
            color = RC.sub, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        // lo que está sonando en la estación
        Row(Modifier.fillMaxWidth().padding(top = 14.dp).glass(RoundedCornerShape(18.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            if (playing) LiveBars(RC.accent) else Icon(Icons.Filled.Pause, null, tint = RC.sub, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(if (songName.isNotBlank()) "AHORA SUENA" else "TRANSMITIENDO", color = RC.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                Text(songName.ifBlank { if (playing) "Señal en vivo" else "En pausa" }, color = RC.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (songArtist.isNotBlank()) Text(songArtist, color = RC.sub, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (station.quality.isNotBlank()) Text(station.quality, color = RC.sub, fontSize = 11.sp)
        }
        if (speakers.isNotBlank()) Text("🔊 $speakers", color = RC.accent, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
        host?.let { SleepTimerChip(it, Modifier.padding(top = 6.dp)) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(vertical = 12.dp)) {
            val fav = RadioStore.isFav(station)
            Icon(if (fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "Favorita", tint = if (fav) Color(0xFFFF5A8A) else RC.sub,
                modifier = Modifier.size(46.dp).clip(CircleShape).clickable { RadioStore.toggleFav(station) }.padding(10.dp))
            Icon(Icons.Filled.SkipPrevious, "Estación anterior", tint = if (ring.size > 1) RC.text else RC.sub.copy(alpha = 0.4f),
                modifier = Modifier.size(52.dp).clip(CircleShape).clickable(enabled = ring.size > 1) { onSwitch(ring[(i - 1 + ring.size) % ring.size]) }.padding(8.dp))
            Box(Modifier.size(78.dp).glass(CircleShape, strong = true).background(Brush.linearGradient(listOf(RC.accent.copy(alpha = 0.75f), RC.accent2.copy(alpha = 0.75f))))
                .clickable(onClick = onToggle), contentAlignment = Alignment.Center) {
                Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(40.dp))
            }
            Icon(Icons.Filled.SkipNext, "Estación siguiente", tint = if (ring.size > 1) RC.text else RC.sub.copy(alpha = 0.4f),
                modifier = Modifier.size(52.dp).clip(CircleShape).clickable(enabled = ring.size > 1) { onSwitch(ring[(i + 1) % ring.size]) }.padding(8.dp))
            Icon(Icons.Filled.Info, "Información", tint = RC.sub, modifier = Modifier.size(46.dp).clip(CircleShape).clickable { info = true }.padding(10.dp))
        }
        VolumeControl(volume, Modifier.fillMaxWidth().glass(RoundedCornerShape(26.dp)).padding(horizontal = 8.dp, vertical = 4.dp), onSet = onVolume)
    }
    if (info) StationSheet(station, true, onPlay = { info = false }, onDismiss = { info = false })
}
