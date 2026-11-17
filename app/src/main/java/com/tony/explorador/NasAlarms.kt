package com.tony.explorador

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Calendar

/** Una alarma de música: a tal hora, tales días, suena tal lista o carpeta en el NAS. */
data class NasAlarm(
    val id: Int,
    val hour: Int,
    val minute: Int,
    val days: Set<Int>,            // Calendar.SUNDAY..SATURDAY; vacío = una sola vez
    val enabled: Boolean = true,
    val kind: String = "playlist", // playlist · folder
    val source: String = "",       // id de lista o ruta de carpeta
    val sourceName: String = "",
    val shuffle: Boolean = true,
    val profile: String? = null,   // perfil de bocinas (null = como estén)
    val volume: Int? = null,
) {
    fun toJson() = JSONObject().put("id", id).put("h", hour).put("m", minute).put("d", JSONArray(days.toList())).put("on", enabled)
        .put("k", kind).put("s", source).put("n", sourceName).put("sh", shuffle).put("p", profile ?: "").put("v", volume ?: -1)

    companion object {
        fun from(o: JSONObject) = NasAlarm(o.getInt("id"), o.getInt("h"), o.getInt("m"),
            o.optJSONArray("d")?.let { a -> (0 until a.length()).map { a.getInt(it) }.toSet() } ?: emptySet(),
            o.optBoolean("on", true), o.optString("k", "playlist"), o.optString("s"), o.optString("n"), o.optBoolean("sh", true),
            o.optString("p").ifBlank { null }, o.optInt("v", -1).takeIf { it >= 0 })
    }
}

object NasAlarms {
    val list = mutableStateListOf<NasAlarm>()
    private var loaded = false
    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("remote_alarms", Context.MODE_PRIVATE)

    fun load(c: Context) {
        if (loaded) return
        loaded = true
        runCatching {
            val a = JSONArray(prefs(c).getString("list", "[]"))
            list.clear(); for (i in 0 until a.length()) list += NasAlarm.from(a.getJSONObject(i))
        }
    }
    private fun save(c: Context) { prefs(c).edit().putString("list", JSONArray(list.map { it.toJson() }).toString()).apply() }

    fun put(c: Context, a: NasAlarm) {
        load(c)
        val i = list.indexOfFirst { it.id == a.id }
        if (i >= 0) list[i] = a else list += a
        save(c); schedule(c, a)
    }
    fun remove(c: Context, a: NasAlarm) { list.removeAll { it.id == a.id }; save(c); cancel(c, a.id) }
    fun newId() = (list.maxOfOrNull { it.id } ?: 0) + 1

    /** Próxima vez que toca (en milisegundos), o null si está apagada. */
    fun nextTime(a: NasAlarm, from: Long = System.currentTimeMillis()): Long? {
        if (!a.enabled) return null
        val c = Calendar.getInstance().apply { timeInMillis = from; set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        c.set(Calendar.HOUR_OF_DAY, a.hour); c.set(Calendar.MINUTE, a.minute)
        if (c.timeInMillis <= from) c.add(Calendar.DAY_OF_YEAR, 1)
        if (a.days.isNotEmpty()) repeat(7) { if (c.get(Calendar.DAY_OF_WEEK) !in a.days) c.add(Calendar.DAY_OF_YEAR, 1) }
        return c.timeInMillis
    }

    private fun pending(c: Context, id: Int) = PendingIntent.getBroadcast(c, 7000 + id,
        Intent(c, NasAlarmReceiver::class.java).putExtra("id", id), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun schedule(c: Context, a: NasAlarm) {
        val am = c.getSystemService(AlarmManager::class.java) ?: return
        val t = nextTime(a) ?: run { cancel(c, a.id); return }
        val pi = pending(c, a.id)
        val exact = android.os.Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        runCatching {
            if (exact) {
                val show = PendingIntent.getActivity(c, 6999, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
                am.setAlarmClock(AlarmManager.AlarmClockInfo(t, show), pi)
            } else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi)
        }
    }
    fun cancel(c: Context, id: Int) { c.getSystemService(AlarmManager::class.java)?.cancel(pending(c, id)) }
    fun rescheduleAll(c: Context) { load(c); list.forEach { schedule(c, it) } }

    fun describe(a: NasAlarm): String {
        val names = listOf("", "D", "L", "M", "M", "J", "V", "S")
        val d = when {
            a.days.isEmpty() -> "Una vez"
            a.days.size == 7 -> "Todos los días"
            a.days == setOf(2, 3, 4, 5, 6) -> "Entre semana"
            a.days == setOf(1, 7) -> "Fines de semana"
            else -> listOf(2, 3, 4, 5, 6, 7, 1).filter { it in a.days }.joinToString(" ") { names[it] }
        }
        return d + " · " + a.sourceName.ifBlank { "sin música elegida" } + (if (a.shuffle) " · aleatorio" else "")
    }

    /** Lo que hace la alarma en el NAS: bocinas, volumen y música. */
    fun fire(c: Context, a: NasAlarm) {
        RemotePrefs.init(c)
        val host = runBlocking { runCatching { AirPlayBridge.findHost(c) }.getOrNull() } ?: throw Exception("no encontré el NAS")
        fun api(m: String, p: String, b: String? = null) = AirPlayBridge.http(m, host, p, b)
        a.profile?.let { RemotePrefs.profiles[it] }?.let { prof ->
            val o = JSONObject(api("GET", "/api/outputs")).optJSONArray("outputs") ?: JSONArray()
            for (i in 0 until o.length()) {
                val out = o.getJSONObject(i); val id = out.optString("id")
                if (out.optString("name").endsWith("+") || out.optString("type") == "ALSA") continue
                val sv = prof[id] ?: Pair(false, out.optInt("volume"))
                api("PUT", "/api/outputs/$id", JSONObject().put("selected", sv.first).apply { if (sv.first) put("volume", sv.second) }.toString())
            }
        }
        a.profile?.let { RemotePrefs.profiles[it] }?.let { prof -> Keeper.push(host, prof.filter { it.value.first }.map { it.key to it.value.second }) }
        a.volume?.let { api("PUT", "/api/player/volume?volume=$it") }
        api("PUT", "/api/player/shuffle?state=${a.shuffle}")
        val what = if (a.kind == "folder") "expression=" + URLEncoder.encode("media_kind is music and path starts with \"${a.source.replace("\"", "")}\"", "UTF-8")
            else "uris=" + URLEncoder.encode("library:playlist:${a.source}", "UTF-8")
        api("POST", "/api/queue/items/add?clear=true&playback=start&$what")
    }
}

class NasAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val app = c.applicationContext
        if (i.action == Intent.ACTION_BOOT_COMPLETED || i.action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            i.action == "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED") { NasAlarms.rescheduleAll(app); return }
        val id = i.getIntExtra("id", -1)
        NasAlarms.load(app)
        val a = NasAlarms.list.firstOrNull { it.id == id } ?: return
        val done = goAsync()
        Thread {
            // el wifi puede tardar en despertar: varios intentos
            var ok = false
            for (t in 0 until 5) {
                if (runCatching { NasAlarms.fire(app, a) }.isSuccess) { ok = true; break }
                Thread.sleep(8000)
            }
            if (!ok) notifyFail(app, a)
            // siguiente vez (o apagarla si era de una vez)
            if (a.days.isEmpty()) NasAlarms.put(app, a.copy(enabled = false)) else NasAlarms.schedule(app, a)
            done.finish()
        }.start()
    }

    private fun notifyFail(c: Context, a: NasAlarm) = runCatching {
        val nm = c.getSystemService(android.app.NotificationManager::class.java)
        nm.createNotificationChannel(android.app.NotificationChannel("alarmas", "Alarmas de música", android.app.NotificationManager.IMPORTANCE_DEFAULT))
        nm.notify(8000 + a.id, androidx.core.app.NotificationCompat.Builder(c, "alarmas")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("La alarma de las %d:%02d no sonó".format(a.hour, a.minute))
            .setContentText("No pude hablar con el NAS. ¿El teléfono estaba en el wifi de la casa?")
            .setAutoCancel(true).build())
    }
}

// ───────────────────────── pantalla ─────────────────────────

@Composable
internal fun AlarmsTab(host: String) {
    val ctx = LocalContext.current
    remember { NasAlarms.load(ctx); 0 }
    var editing by remember { mutableStateOf<NasAlarm?>(null) }
    val am = remember { ctx.getSystemService(AlarmManager::class.java) }
    val exactOk = android.os.Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()

    if (editing != null) { AlarmEditor(host, editing!!, onDone = { a -> if (a != null) NasAlarms.put(ctx, a); editing = null }); return }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        item {
            Text("Alarmas", color = RC.text, fontWeight = FontWeight.Black, fontSize = 32.sp, modifier = Modifier.padding(start = 2.dp, top = 4.dp))
            Text("A la hora que elijas, el NAS pone tu lista o carpeta en las bocinas. El teléfono debe estar en el wifi de la casa.",
                color = RC.sub, fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp))
            if (!exactOk) Text("Para que suene a la hora exacta, activa «Alarmas y recordatorios» para DriveNebula. Toca aquí.",
                color = Color(0xFFFFD27A), fontSize = 13.sp, modifier = Modifier.padding(vertical = 6.dp).glass(RoundedCornerShape(14.dp)).clickable {
                    runCatching { ctx.startActivity(Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, android.net.Uri.parse("package:" + ctx.packageName))) }
                }.padding(12.dp))
        }
        items(NasAlarms.list.sortedBy { it.hour * 60 + it.minute }, key = { it.id }) { a ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp).glass(RoundedCornerShape(20.dp)).clickable { editing = a }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("%d:%02d".format(a.hour, a.minute), color = if (a.enabled) RC.text else RC.sub, fontSize = 40.sp, fontWeight = FontWeight.Light)
                    Text(NasAlarms.describe(a), color = RC.sub, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    a.profile?.let { Text("🔊 $it" + (a.volume?.let { v -> " · vol $v" } ?: ""), color = RC.accent, fontSize = 12.sp) }
                }
                Switch(a.enabled, { NasAlarms.put(ctx, a.copy(enabled = it)) },
                    colors = SwitchDefaults.colors(checkedTrackColor = RC.accent, checkedThumbColor = Color.White, uncheckedTrackColor = Color(0x33FFFFFF)))
            }
        }
        item {
            Text("+ Nueva alarma", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                modifier = Modifier.padding(vertical = 12.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp))
                    .background(Brush.linearGradient(listOf(RC.accent, RC.accent2))).clickable {
                        editing = NasAlarm(NasAlarms.newId(), 7, 0, setOf(2, 3, 4, 5, 6))
                    }.padding(16.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.height(20.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlarmEditor(host: String, start: NasAlarm, onDone: (NasAlarm?) -> Unit) {
    val ctx = LocalContext.current
    var a by remember { mutableStateOf(start) }
    val time = rememberTimePickerState(start.hour, start.minute, is24Hour = true)
    var picking by remember { mutableStateOf<String?>(null) }   // playlist · folder
    var confirmDelete by remember { mutableStateOf(false) }
    androidx.activity.compose.BackHandler { if (picking != null) picking = null else onDone(null) }

    if (picking != null) {
        SourcePicker(host, picking!!, onPick = { kind, id, name -> a = a.copy(kind = kind, source = id, sourceName = name); picking = null }, onBack = { picking = null })
        return
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Cancelar", color = RC.accent, fontSize = 16.sp, modifier = Modifier.clip(CircleShape).clickable { onDone(null) }.padding(10.dp))
            Spacer(Modifier.weight(1f))
            Text(if (NasAlarms.list.any { it.id == a.id }) "Editar alarma" else "Nueva alarma", color = RC.text, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Spacer(Modifier.weight(1f))
            Text("Guardar", color = RC.accent, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.clip(CircleShape).clickable {
                if (a.source.isBlank()) { picking = "playlist"; return@clickable }
                onDone(a.copy(hour = time.hour, minute = time.minute, enabled = true))
            }.padding(10.dp))
        }
        Box(Modifier.fillMaxWidth().padding(vertical = 10.dp).glass(RoundedCornerShape(24.dp)).padding(16.dp), contentAlignment = Alignment.Center) {
            TimeInput(time, colors = TimePickerDefaults.colors(
                timeSelectorSelectedContainerColor = RC.accent.copy(alpha = 0.35f), timeSelectorUnselectedContainerColor = Color(0x22FFFFFF),
                timeSelectorSelectedContentColor = Color.White, timeSelectorUnselectedContentColor = RC.text))
        }
        GlassLabel("Repetir")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf(2 to "L", 3 to "M", 4 to "M", 5 to "J", 6 to "V", 7 to "S", 1 to "D").forEach { (d, l) ->
                val on = d in a.days
                Box(Modifier.size(42.dp).clip(CircleShape).background(if (on) RC.accent else Color(0x22FFFFFF))
                    .clickable { a = a.copy(days = if (on) a.days - d else a.days + d) }, contentAlignment = Alignment.Center) {
                    Text(l, color = if (on) Color.White else RC.sub, fontWeight = FontWeight.Bold)
                }
            }
        }
        Text(if (a.days.isEmpty()) "Sin días marcados: suena una sola vez." else NasAlarms.describe(a.copy(sourceName = "")).substringBefore(" ·"),
            color = RC.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))

        GlassLabel("Qué suena")
        Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(18.dp))) {
            OptionRow(Icons.Filled.QueueMusic, "Una lista", if (a.kind == "playlist") a.sourceName else "") { picking = "playlist" }
            Box(Modifier.padding(start = 52.dp).fillMaxWidth().height(1.dp).background(Color(0x1AFFFFFF)))
            OptionRow(Icons.Filled.Folder, "Una carpeta", if (a.kind == "folder") a.sourceName else "") { picking = "folder" }
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp).glass(RoundedCornerShape(18.dp)).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Aleatorio", color = RC.text, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Switch(a.shuffle, { a = a.copy(shuffle = it) }, colors = SwitchDefaults.colors(checkedTrackColor = RC.accent, uncheckedTrackColor = Color(0x33FFFFFF)))
        }

        GlassLabel("Bocinas")
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (listOf<String?>(null) + RemotePrefs.profiles.keys.sorted()).forEach { p ->
                val on = a.profile == p
                Text(p ?: "Como estén", color = if (on) Color.White else RC.text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                    modifier = Modifier.clip(CircleShape).background(if (on) RC.accent else Color(0x22FFFFFF)).clickable { a = a.copy(profile = p) }
                        .padding(horizontal = 14.dp, vertical = 9.dp))
            }
        }
        if (RemotePrefs.profiles.isEmpty()) Text("Guarda un perfil en «Bocinas» para elegir cuáles suenan.", color = RC.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))

        GlassLabel("Volumen")
        Row(Modifier.fillMaxWidth().glass(RoundedCornerShape(18.dp)).padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (a.volume == null) "No cambiar" else "${a.volume}", color = RC.text, fontSize = 15.sp, modifier = Modifier.width(92.dp).clip(RoundedCornerShape(8.dp))
                .clickable { a = a.copy(volume = if (a.volume == null) 30 else null) }.padding(6.dp))
            if (a.volume != null) VolumeControl(a.volume!!, Modifier.weight(1f)) { a = a.copy(volume = it) }
            else Text("Toca para fijar un volumen", color = RC.sub, fontSize = 12.sp)
        }

        if (NasAlarms.list.any { it.id == a.id }) Row(Modifier.padding(top = 18.dp).fillMaxWidth().glass(RoundedCornerShape(18.dp)).clickable { confirmDelete = true }.padding(14.dp),
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Delete, null, tint = Color(0xFFFF6B8B)); Spacer(Modifier.width(8.dp)); Text("Borrar alarma", color = Color(0xFFFF6B8B), fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(30.dp))
    }
    if (confirmDelete) androidx.compose.material3.AlertDialog(onDismissRequest = { confirmDelete = false },
        title = { Text("¿Borrar esta alarma?") },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { NasAlarms.remove(ctx, a); confirmDelete = false; onDone(null) }) { Text("Borrar") } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } })
}

@Composable private fun GlassLabel(t: String) = Text(t.uppercase(), color = RC.sub, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
    modifier = Modifier.padding(start = 6.dp, top = 18.dp, bottom = 6.dp))

@Composable
private fun OptionRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = RC.accent, modifier = Modifier.size(24.dp)); Spacer(Modifier.width(14.dp))
        Text(label, color = RC.text, fontSize = 16.sp)
        Spacer(Modifier.width(10.dp))
        Text(value.ifBlank { "Elegir ›" }, color = if (value.isBlank()) RC.sub else RC.accent, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}

/** Elegir una lista o una carpeta del NAS. */
@Composable
private fun SourcePicker(host: String, kind: String, onPick: (String, String, String) -> Unit, onBack: () -> Unit) {
    var dir by remember { mutableStateOf<String?>(null) }   // null = raíz
    val rows = remember { mutableStateListOf<Pair<String, String>>() }  // id/ruta → nombre
    var loading by remember { mutableStateOf(true) }
    var err by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(kind, dir) {
        loading = true; err = null
        val r = withContext(Dispatchers.IO) {
            runCatching {
                if (kind == "playlist") {
                    val ls = JSONObject(AirPlayBridge.http("GET", host, "/api/library/playlists")).optJSONArray("items") ?: JSONArray()
                    (0 until ls.length()).map { ls.getJSONObject(it) }.filter { it.optString("type") != "folder" }
                        .map { it.optString("id") to it.optString("name") }.sortedBy { it.second.lowercase() }
                } else {
                    val q = dir?.let { "?directory=" + URLEncoder.encode(it, "UTF-8") } ?: ""
                    val ds = JSONObject(AirPlayBridge.http("GET", host, "/api/library/files$q")).optJSONArray("directories") ?: JSONArray()
                    (0 until ds.length()).map { ds.getJSONObject(it).optString("path") }.map { it to it.trimEnd('/').substringAfterLast('/') }
                        .sortedBy { it.second.lowercase() }
                }
            }
        }
        r.onSuccess { rows.clear(); rows.addAll(it) }.onFailure { err = "No pude leer el NAS: ${it.message}" }
        loading = false
    }
    androidx.activity.compose.BackHandler { if (kind == "folder" && dir != null) dir = dir!!.trimEnd('/').substringBeforeLast('/').takeIf { it.count { c -> c == '/' } > 1 } else onBack() }
    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.ArrowBack, "Atrás", tint = RC.text, modifier = Modifier.size(42.dp).clip(CircleShape).clickable { onBack() }.padding(9.dp))
            Column(Modifier.weight(1f)) {
                Text(if (kind == "playlist") "Elige una lista" else "Elige una carpeta", color = RC.text, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                if (kind == "folder") Text(dir ?: "Música del NAS", color = RC.sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (kind == "folder" && dir != null) Text("✓ Usar esta carpeta (${dir!!.trimEnd('/').substringAfterLast('/')})", color = Color.White, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(listOf(RC.accent, RC.accent2)))
                .clickable { onPick("folder", dir!!, dir!!.trimEnd('/').substringAfterLast('/')) }.padding(14.dp))
        if (loading) CircularProgressIndicator(color = RC.accent, strokeWidth = 2.dp, modifier = Modifier.padding(20.dp).size(24.dp))
        err?.let { Text(it, color = Color(0xFFFFB4C8), modifier = Modifier.padding(12.dp)) }
        if (!loading && rows.isEmpty() && err == null) Text(if (kind == "playlist") "No hay listas (.m3u) en el NAS." else "Esta carpeta no tiene subcarpetas.", color = RC.sub, modifier = Modifier.padding(12.dp))
        LazyColumn(Modifier.fillMaxSize().padding(top = 6.dp).glass(RoundedCornerShape(18.dp)).heightIn(min = 10.dp)) {
            items(rows, key = { it.first }) { (id, name) ->
                Row(Modifier.fillMaxWidth().clickable { if (kind == "playlist") onPick("playlist", id, name) else dir = id }.padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (kind == "playlist") Icons.Filled.QueueMusic else Icons.Filled.Folder, null, tint = RC.accent, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(14.dp))
                    Text(name, color = RC.text, fontSize = 16.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (kind == "folder") Text("›", color = RC.sub, fontSize = 20.sp)
                }
                Box(Modifier.padding(start = 52.dp).fillMaxWidth().height(1.dp).background(Color(0x14FFFFFF)))
            }
        }
    }
}
