package com.tony.explorador

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Abre «Casa» (Home Assistant en el NAS) desde cualquier lado. */
object HomeUi { var open by mutableStateOf(false) }

/** Conexión con Home Assistant: dirección y llave de acceso (token), guardadas solo en el teléfono. */
object HomeAssistant {
    private fun prefs(c: Context) = c.getSharedPreferences("casa", Context.MODE_PRIVATE)
    fun url(c: Context) = prefs(c).getString("url", "http://192.168.3.74:8123")!!
    fun token(c: Context) = prefs(c).getString("token", "").orEmpty()
    fun save(c: Context, url: String, token: String) { prefs(c).edit().putString("url", url.trim().trimEnd('/')).putString("token", token.trim()).apply() }

    fun call(c: Context, method: String, path: String, body: String? = null): String {
        val conn = URL(url(c) + path).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 4000; conn.readTimeout = 8000
        conn.setRequestProperty("Authorization", "Bearer " + token(c))
        conn.setRequestProperty("Content-Type", "application/json")
        if (body != null) { conn.doOutput = true; conn.outputStream.use { it.write(body.toByteArray()) } }
        val code = conn.responseCode
        val text = (if (code < 400) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        conn.disconnect()
        if (code == 401) throw Exception("La llave de acceso no es válida")
        if (code >= 400) throw Exception("Home Assistant respondió $code")
        return text
    }

    fun service(c: Context, domain: String, svc: String, data: JSONObject) { call(c, "POST", "/api/services/$domain/$svc", data.toString()) }
}

private object HC {
    val bg1 = Color(0xFF0B0820); val bg2 = Color(0xFF1C0F3D); val card = Color(0xFF1A1440)
    val text = Color(0xFFF4EEFF); val sub = Color(0xFFA99FCF); val accent = Color(0xFFB98CFF); val warm = Color(0xFFFFC94D); val cold = Color(0xFF7FD3FF)
}

private data class Ent(val id: String, val domain: String, val name: String, val state: String, val attrs: JSONObject)

private val SHOWN = listOf("scene", "light", "climate", "media_player", "vacuum", "switch", "fan")
private val SECTION = mapOf("scene" to "Escenas", "light" to "Luces", "climate" to "Aires", "media_player" to "Teles y bocinas",
    "vacuum" to "Aspiradora", "switch" to "Enchufes", "fan" to "Ventiladores")

@Composable
fun HomeControlScreen(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var settings by remember { mutableStateOf(HomeAssistant.token(ctx).isBlank()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var tick by remember { mutableStateOf(0) }
    val ents = remember { mutableStateListOf<Ent>() }
    BackHandler { onClose() }

    LaunchedEffect(tick, settings) {
        if (settings) return@LaunchedEffect
        while (isActive) {
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    val a = JSONArray(HomeAssistant.call(ctx, "GET", "/api/states"))
                    (0 until a.length()).map { a.getJSONObject(it) }.mapNotNull { o ->
                        val id = o.optString("entity_id"); val d = id.substringBefore('.')
                        if (d !in SHOWN) return@mapNotNull null
                        val at = o.optJSONObject("attributes") ?: JSONObject()
                        if (o.optString("state") == "unavailable" && d != "scene") return@mapNotNull null
                        Ent(id, d, at.optString("friendly_name", id), o.optString("state"), at)
                    }.sortedWith(compareBy({ SHOWN.indexOf(it.domain) }, { it.name.lowercase() }))
                }
            }
            r.onSuccess { l -> ents.clear(); ents.addAll(l); error = null }.onFailure { error = it.message ?: "No se pudo conectar con Home Assistant" }
            loading = false
            delay(3000)
        }
    }

    fun svc(domain: String, s: String, data: JSONObject) {
        scope.launch(Dispatchers.IO) { runCatching { HomeAssistant.service(ctx, domain, s, data) }.onFailure { error = it.message }; delay(400); tick++ }
    }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(HC.bg2, HC.bg1)))) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.ArrowBack, "Cerrar", tint = HC.text, modifier = Modifier.size(44.dp).clip(CircleShape).clickable { onClose() }.padding(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Casa", color = HC.text, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                    Text("Home Assistant · ${HomeAssistant.url(ctx).removePrefix("http://")}", color = HC.sub, fontSize = 12.sp)
                }
                if (loading && !settings) CircularProgressIndicator(color = HC.accent, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                Icon(Icons.Filled.Settings, "Conexión", tint = HC.sub, modifier = Modifier.size(44.dp).clip(CircleShape).clickable { settings = true }.padding(10.dp))
            }
            error?.let {
                Text("⚠ $it", color = Color(0xFFFFB4C8), fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp)).background(Color(0x33FF4F7A)).clickable { settings = true }.padding(10.dp))
            }
            if (!settings && !loading && ents.isEmpty() && error == null)
                Text("No hay aparatos todavía. Agrégalos en Home Assistant (Ajustes → Dispositivos y servicios) y aparecerán aquí.",
                    color = HC.sub, modifier = Modifier.padding(20.dp))
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
                SHOWN.forEach { d ->
                    val group = ents.filter { it.domain == d }
                    if (group.isEmpty()) return@forEach
                    item(key = "h_$d") { Text(SECTION[d] ?: d, color = HC.accent, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp)) }
                    if (d == "scene") item(key = "scenes") {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            group.forEach { e ->
                                Row(Modifier.clip(CircleShape).background(Brush.linearGradient(listOf(Color(0x55B98CFF), Color(0x33FF7AC6))))
                                    .clickable { svc("scene", "turn_on", JSONObject().put("entity_id", e.id)) }.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Movie, null, tint = HC.text, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp))
                                    Text(e.name, color = HC.text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                }
                            }
                        }
                    } else items(group, key = { it.id }) { e -> EntityCard(e, ::svc) }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    if (settings) HomeSettingsDialog(onDone = { settings = false; loading = true; tick++ }, onDismiss = { if (HomeAssistant.token(ctx).isNotBlank()) settings = false else onClose() })
}

@Composable
private fun EntityCard(e: Ent, svc: (String, String, JSONObject) -> Unit) {
    val on = e.state == "on" || e.state == "playing" || e.state == "paused" || e.state == "cleaning" ||
        (e.domain == "climate" && e.state != "off") || (e.domain == "media_player" && e.state !in setOf("off", "standby", "unavailable"))
    val icon: ImageVector = when (e.domain) {
        "light" -> Icons.Filled.Lightbulb; "climate" -> Icons.Filled.AcUnit; "media_player" -> Icons.Filled.Tv
        "vacuum" -> Icons.Filled.CleaningServices; else -> Icons.Filled.PowerSettingsNew
    }
    val tint = if (!on) HC.sub else when (e.domain) { "light" -> HC.warm; "climate" -> HC.cold; else -> HC.accent }
    val id = JSONObject().put("entity_id", e.id)
    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(18.dp))
        .background(if (on) Color(0x332E2370) else HC.card).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(tint.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp)) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(e.name, color = HC.text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(stateText(e), color = HC.sub, fontSize = 12.sp, maxLines = 1)
            }
            // prender / apagar
            val (dom, s) = when (e.domain) {
                "vacuum" -> "vacuum" to (if (e.state == "cleaning") "return_to_base" else "start")
                "climate" -> "climate" to (if (on) "turn_off" else "turn_on")
                "media_player" -> "media_player" to (if (on) "turn_off" else "turn_on")
                else -> e.domain to "toggle"
            }
            Box(Modifier.size(46.dp).clip(CircleShape).background(if (on) Brush.linearGradient(listOf(HC.accent, Color(0xFFFF7AC6))) else Brush.linearGradient(listOf(Color(0x33FFFFFF), Color(0x22FFFFFF))))
                .clickable { svc(dom, s, id) }, contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.PowerSettingsNew, "Prender o apagar", tint = Color.White, modifier = Modifier.size(22.dp))
            }
        }
        when (e.domain) {
            "light" -> if (on && e.attrs.has("brightness")) {
                var v by remember(e.id) { mutableStateOf<Float?>(null) }
                Slider(v ?: (e.attrs.optInt("brightness") / 255f), { v = it },
                    onValueChangeFinished = { v?.let { svc("light", "turn_on", JSONObject().put("entity_id", e.id).put("brightness_pct", (it * 100).toInt().coerceAtLeast(1))) }; v = null },
                    colors = SliderDefaults.colors(thumbColor = HC.warm, activeTrackColor = HC.warm, inactiveTrackColor = Color(0x33FFFFFF)))
            }
            "climate" -> if (on) {
                val t = e.attrs.optDouble("temperature", Double.NaN)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Pill("−") { if (!t.isNaN()) svc("climate", "set_temperature", JSONObject().put("entity_id", e.id).put("temperature", t - 1)) }
                    Text(if (t.isNaN()) "—" else "${t.toInt()}°", color = HC.text, fontWeight = FontWeight.Bold, fontSize = 26.sp, modifier = Modifier.padding(horizontal = 16.dp))
                    Pill("+") { if (!t.isNaN()) svc("climate", "set_temperature", JSONObject().put("entity_id", e.id).put("temperature", t + 1)) }
                    Spacer(Modifier.weight(1f))
                }
                val modes = e.attrs.optJSONArray("hvac_modes")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty().filter { it != "off" }
                if (modes.isNotEmpty()) Row(Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    modes.forEach { m ->
                        val sel = e.state == m
                        Text(modeName(m), color = if (sel) Color.White else HC.sub, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clip(CircleShape).background(if (sel) HC.cold.copy(alpha = 0.5f) else Color(0x22FFFFFF))
                                .clickable { svc("climate", "set_hvac_mode", JSONObject().put("entity_id", e.id).put("hvac_mode", m)) }.padding(horizontal = 12.dp, vertical = 6.dp))
                    }
                }
            }
            "media_player" -> if (on) Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(icon = Icons.Filled.VolumeDown) { svc("media_player", "volume_down", id) }
                Pill(icon = Icons.Filled.VolumeOff) { svc("media_player", "volume_mute", JSONObject().put("entity_id", e.id).put("is_volume_muted", !e.attrs.optBoolean("is_volume_muted"))) }
                Pill(icon = Icons.Filled.VolumeUp) { svc("media_player", "volume_up", id) }
            }
            "vacuum" -> Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Limpiar") { svc("vacuum", "start", id) }
                Pill("Pausa") { svc("vacuum", "pause", id) }
                Pill("A su base") { svc("vacuum", "return_to_base", id) }
            }
        }
    }
}

@Composable
private fun Pill(text: String? = null, icon: ImageVector? = null, onClick: () -> Unit) {
    Box(Modifier.clip(CircleShape).background(Color(0x22FFFFFF)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
        if (icon != null) Icon(icon, null, tint = HC.text, modifier = Modifier.size(20.dp)) else Text(text.orEmpty(), color = HC.text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

private fun modeName(m: String) = when (m) {
    "cool" -> "Frío"; "heat" -> "Calor"; "dry" -> "Seco"; "fan_only" -> "Ventilador"; "auto" -> "Auto"; "heat_cool" -> "Auto"; else -> m
}

private fun stateText(e: Ent): String = when (e.domain) {
    "light" -> if (e.state == "on") "Prendida" + (e.attrs.optInt("brightness", -1).takeIf { it >= 0 }?.let { " · ${it * 100 / 255}%" } ?: "") else "Apagada"
    "climate" -> if (e.state == "off") "Apagado" else modeName(e.state) + (e.attrs.optDouble("current_temperature", Double.NaN).takeIf { !it.isNaN() }?.let { " · cuarto ${it.toInt()}°" } ?: "")
    "media_player" -> when (e.state) { "off", "standby" -> "Apagada"; "playing" -> "Reproduciendo" + (e.attrs.optString("media_title").takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""); "paused" -> "En pausa"; else -> "Prendida" }
    "vacuum" -> when (e.state) { "cleaning" -> "Limpiando"; "docked" -> "En su base"; "returning" -> "Regresando"; "paused" -> "En pausa"; "idle" -> "Esperando"; else -> e.state }
    else -> if (e.state == "on") "Prendido" else "Apagado"
}

@Composable
private fun HomeSettingsDialog(onDone: () -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var url by remember { mutableStateOf(HomeAssistant.url(ctx)) }
    var token by remember { mutableStateOf(HomeAssistant.token(ctx)) }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text("Conectar con Home Assistant") },
        text = {
            Column {
                Text("1. En Home Assistant toca tu nombre (abajo a la izquierda) → pestaña «Seguridad».\n" +
                    "2. Hasta abajo, en «Tokens de acceso de larga duración», toca «Crear token», ponle «DriveNebula» y copia la llave.\n" +
                    "3. Pégala aquí. Se guarda solo en este teléfono.", fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(url, { url = it }, label = { Text("Dirección") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(token, { token = it }, label = { Text("Llave de acceso (token)") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation())
            }
        },
        confirmButton = { TextButton(onClick = { HomeAssistant.save(ctx, url, token); onDone() }, enabled = token.isNotBlank()) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}
