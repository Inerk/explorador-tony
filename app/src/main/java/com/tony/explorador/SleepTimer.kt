package com.tony.explorador

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.Calendar

/*
 * Temporizador para dormir: lo lleva el NAS (Nebula Keeper), así funciona aunque cierres la app o
 * apagues el teléfono. Baja el volumen poco a poco y pausa; o para al terminar la canción.
 */

private object SleepTimerState {
    var active by mutableStateOf(false)
    var song by mutableStateOf(false)
    var endsAt by mutableLongStateOf(0L)     // milisegundos del teléfono
}

private suspend fun refresh(host: String) {
    val t = withContext(Dispatchers.IO) { Keeper.timer(host) } ?: return
    apply(t)
}

private fun apply(t: JSONObject) {
    SleepTimerState.active = t.optBoolean("activo")
    SleepTimerState.song = t.optString("kind") == "song"
    SleepTimerState.endsAt = if (t.has("restante_s")) System.currentTimeMillis() + t.optLong("restante_s") * 1000 else 0L
}

@Composable
private fun rememberTimerLabel(host: String): String {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(host) {
        var n = 0
        while (true) {
            if (n % 10 == 0) runCatching { refresh(host) }
            now = System.currentTimeMillis(); n++
            delay(1000)
        }
    }
    if (!SleepTimerState.active) return ""
    if (SleepTimerState.song) return "al terminar la canción"
    val left = ((SleepTimerState.endsAt - now) / 1000).coerceAtLeast(0)
    return if (left >= 3600) "en %d:%02d:%02d".format(left / 3600, left % 3600 / 60, left % 60) else "en %d:%02d".format(left / 60, left % 60)
}

/** Botoncito en «Sonando»: muestra cuánto falta y abre las opciones. */
@Composable
fun SleepTimerChip(host: String, modifier: Modifier = Modifier) {
    val label = rememberTimerLabel(host)
    var open by remember { mutableStateOf(false) }
    Row(modifier.clip(CircleShape).background(if (label.isNotEmpty()) RC.accent.copy(alpha = 0.30f) else Color(0x22FFFFFF))
        .clickable { open = true }.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Bedtime, null, tint = if (label.isNotEmpty()) Color.White else RC.sub, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(if (label.isEmpty()) "Temporizador" else "Se apaga $label", color = if (label.isNotEmpty()) Color.White else RC.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
    if (open) SleepTimerDialog(host) { open = false }
}

/** Tarjeta en «Alarmas». */
@Composable
fun SleepTimerCard(host: String, modifier: Modifier = Modifier) {
    val label = rememberTimerLabel(host)
    var open by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth().glass(RoundedCornerShape(20.dp)).clickable { open = true }.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Bedtime, null, tint = RC.accent, modifier = Modifier.size(22.dp)); Spacer(Modifier.width(8.dp))
            Text("Temporizador para dormir", color = RC.text, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Text(if (label.isEmpty()) "Poner ›" else "Cambiar ›", color = RC.accent, fontSize = 13.sp)
        }
        Text(if (label.isEmpty()) "Apaga la música sola: en unos minutos, a una hora o al terminar la canción."
             else "La música se apaga $label.", color = if (label.isEmpty()) RC.sub else Color.White, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
    }
    if (open) SleepTimerDialog(host) { open = false }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SleepTimerDialog(host: String, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var fade by remember { mutableStateOf(true) }
    var atTime by remember { mutableStateOf(false) }
    val now = remember { Calendar.getInstance() }
    val time = rememberTimePickerState((now.get(Calendar.HOUR_OF_DAY) + 1) % 24, 0, is24Hour = true)
    var err by remember { mutableStateOf<String?>(null) }

    fun send(body: JSONObject) = scope.launch {
        val r = withContext(Dispatchers.IO) { Keeper.setTimer(host, body.put("fade", fade)) }
        if (r == null) err = "No pude hablar con el NAS." else { apply(r); onDismiss() }
    }

    AlertDialog(onDismissRequest = onDismiss, containerColor = Color(0xFF221A3D),
        title = { Text("Temporizador para dormir", color = Color.White) },
        text = {
            Column {
                if (!atTime) {
                    Text("Apagar la música en…", color = RC.sub, fontSize = 13.sp)
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(15, 30, 45, 60, 90, 120).forEach { m ->
                            Text(if (m < 60) "$m min" else if (m % 60 == 0) "${m / 60} h" else "1 h 30", color = Color.White, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clip(CircleShape).background(Brush.linearGradient(listOf(RC.accent, RC.accent2)))
                                    .clickable { send(JSONObject().put("minutes", m)) }.padding(horizontal = 14.dp, vertical = 9.dp))
                        }
                    }
                    Text("Al terminar la canción", color = Color.White, fontSize = 15.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp).clip(RoundedCornerShape(14.dp)).background(Color(0x22FFFFFF))
                            .clickable { send(JSONObject().put("song", true)) }.padding(12.dp))
                    Text("A una hora…", color = Color.White, fontSize = 15.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(14.dp)).background(Color(0x22FFFFFF))
                            .clickable { atTime = true }.padding(12.dp))
                } else {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        TimeInput(time, colors = TimePickerDefaults.colors(
                            timeSelectorSelectedContainerColor = RC.accent.copy(alpha = 0.35f), timeSelectorUnselectedContainerColor = Color(0x22FFFFFF),
                            timeSelectorSelectedContentColor = Color.White, timeSelectorUnselectedContentColor = RC.text))
                    }
                    Text("Apagar a esta hora", color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(RC.accent, RC.accent2)))
                            .clickable {
                                val c = Calendar.getInstance().apply {
                                    set(Calendar.HOUR_OF_DAY, time.hour); set(Calendar.MINUTE, time.minute); set(Calendar.SECOND, 0)
                                    if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
                                }
                                // el NAS cuenta en segundos desde ahora (así no importa si su reloj difiere un poco)
                                send(JSONObject().put("minutes", (c.timeInMillis - System.currentTimeMillis()) / 60000.0))
                            }.padding(12.dp))
                }
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Bajar el volumen poco a poco", color = RC.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Switch(fade, { fade = it }, colors = SwitchDefaults.colors(checkedTrackColor = RC.accent, uncheckedTrackColor = Color(0x33FFFFFF)))
                }
                err?.let { Text(it, color = Color(0xFFFFB4C8), fontSize = 13.sp) }
            }
        },
        confirmButton = {
            if (SleepTimerState.active) TextButton(onClick = {
                scope.launch { withContext(Dispatchers.IO) { Keeper.cancelTimer(host) }; SleepTimerState.active = false; onDismiss() }
            }) { Text("Quitar temporizador", color = Color(0xFFFF8FA8)) }
        },
        dismissButton = { TextButton(onClick = { if (atTime) atTime = false else onDismiss() }) { Text(if (atTime) "Atrás" else "Cerrar", color = RC.accent) } })
}
